package com.t052.fuelbilling.engine;

import com.t052.fuelbilling.model.FleetAccount;
import com.t052.fuelbilling.model.Payment;
import com.t052.fuelbilling.repository.PaymentRepository;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Applies incoming payments to the oldest unpaid invoices first. */
public class PaymentProcessor {

    private static final BigDecimal MONTHLY_INTEREST_RATE =
            new BigDecimal("0.02");
    private static final BigDecimal CREDIT_REDUCTION =
            new BigDecimal("0.75");

    private final EngineContext context;
    private final EngineState state;
    private final PaymentRepository paymentRepository;

    public PaymentProcessor(EngineContext context, EngineState state) {
        if (context == null || state == null) {
            throw new IllegalArgumentException(
                    "Engine context and state cannot be null");
        }

        this.context = context;
        this.state = state;
        this.paymentRepository = new PaymentRepository();

        for (FleetAccount account : context.getAccounts()) {
            state.rememberOriginalCreditLimit(
                    account.getAccountId(), account.getCreditLimit());
        }
    }

    public int applyPayments(Path paymentFile) throws IOException {
        if (paymentFile == null) {
            throw new IllegalArgumentException(
                    "Payment file path cannot be null");
        }

        List<Payment> payments =
                paymentRepository.loadPayments(paymentFile);

        // Payment files may not be arranged by date. Apply by value date.
        List<Payment> orderedPayments = new ArrayList<>(payments);
        orderedPayments.sort(
                Comparator.comparing(Payment::getPaymentDate)
                        .thenComparing(Payment::getAccountId)
                        .thenComparing(Payment::getReference));

        for (Payment payment : orderedPayments) {
            applyOnePayment(payment);
        }

        System.out.println(
                "Applied " + orderedPayments.size() + " payments from "
                        + paymentFile.getFileName() + ".");

        return orderedPayments.size();
    }

    private void applyOnePayment(Payment payment) {

        FleetAccount account = context.findAccount(payment.getAccountId())
                .orElseThrow(() -> new IllegalArgumentException(
                        "Unknown payment account: " + payment.getAccountId()));

        validatePayment(payment);

        List<InvoiceRecord> invoices = new ArrayList<>(
                state.getInvoicesForAccount(payment.getAccountId()));

        invoices.sort(Comparator
                .comparing(InvoiceRecord::getInvoiceDate)
                .thenComparing(InvoiceRecord::getInvoiceId));

        // Accrue interest through the payment value date before allocating.
        BigDecimal totalOutstanding = BigDecimal.ZERO;
        for (InvoiceRecord invoice : invoices) {
            BigDecimal accruedInterest = invoice.accrueInterestUntil(
                    payment.getPaymentDate(), MONTHLY_INTEREST_RATE);

            // Record interest separately so the account statement shows
            // the charge on the date it was accrued, not only in a later invoice.
            if (accruedInterest.signum() > 0) {
                String interestReference = "INT-" + invoice.getInvoiceId()
                        + "-" + payment.getPaymentDate();
                state.addPaymentAllocation(new PaymentAllocation(
                        payment.getAccountId(),
                        payment.getPaymentDate(),
                        interestReference,
                        "INTEREST",
                        invoice.getInvoiceId(),
                        "INTEREST",
                        accruedInterest));
            }

            totalOutstanding = totalOutstanding.add(
                    invoice.getOutstandingBalance());
        }
        totalOutstanding = money(totalOutstanding);

        BigDecimal paymentAmount;
        if (payment.isFullPayment()) {
            paymentAmount = totalOutstanding;
        } else {
            paymentAmount = money(payment.getAmount());
        }

        if (paymentAmount.signum() <= 0) {
            if (payment.isFullPayment()) {
                System.out.println(
                        "No outstanding balance for "
                                + payment.getAccountId() + ".");
                updateCreditLimit(account, payment.getPaymentDate());
                return;
            }
            throw new IllegalArgumentException(
                    "Payment amount must be greater than zero");
        }

        BigDecimal remaining = paymentAmount;

        for (InvoiceRecord invoice : invoices) {
            if (remaining.signum() <= 0) {
                break;
            }

            if (invoice.getOutstandingBalance().signum() <= 0) {
                continue;
            }

            BigDecimal applied = invoice.applyPayment(remaining);

            if (applied.signum() > 0) {
                state.addPaymentAllocation(new PaymentAllocation(
                        payment.getAccountId(),
                        payment.getPaymentDate(),
                        payment.getReference(),
                        payment.getMode(),
                        invoice.getInvoiceId(),
                        "PAYMENT",
                        applied));

                remaining = remaining.subtract(applied);
            }
        }

        // Preserve overpayments as account credit for the next invoice.
        if (remaining.signum() > 0) {
            remaining = money(remaining);
            state.addAccountCredit(payment.getAccountId(), remaining);

            state.addPaymentAllocation(new PaymentAllocation(
                    payment.getAccountId(),
                    payment.getPaymentDate(),
                    payment.getReference(),
                    payment.getMode(),
                    "",
                    "ACCOUNT_CREDIT",
                    remaining));
        }

        updateCreditLimit(account, payment.getPaymentDate());

        System.out.println(
                "Payment " + payment.getReference()
                        + " for " + payment.getAccountId()
                        + " applied: Rs " + money(paymentAmount)
                        + (remaining.signum() > 0
                        ? " (excess retained as account credit)"
                        : ""));
    }

    private void validatePayment(Payment payment) {
        if (payment.getPaymentDate() == null
                || payment.getAccountId() == null
                || payment.getAccountId().isBlank()
                || payment.getReference() == null
                || payment.getReference().isBlank()) {
            throw new IllegalArgumentException(
                    "Payment is missing a required field");
        }

        String mode = payment.getMode() == null
                ? "" : payment.getMode().trim().toUpperCase();

        if (!mode.equals("NEFT") && !mode.equals("CHEQUE")) {
            throw new IllegalArgumentException(
                    "Unsupported payment mode: " + payment.getMode());
        }

        if (!payment.isFullPayment()
                && (payment.getAmount() == null
                || payment.getAmount().signum() <= 0)) {
            throw new IllegalArgumentException(
                    "Payment amount must be greater than zero");
        }
    }

    /**
     * A 25% reduction applies when the two latest consecutive invoices
     * are both overdue and still have unpaid balances. It is restored
     * when that condition no longer holds.
     */
    private void updateCreditLimit(
            FleetAccount account, LocalDate asOfDate) {
        state.updateCreditLimit(account, asOfDate);
    }

    private BigDecimal money(BigDecimal amount) {
        return amount.setScale(2, RoundingMode.HALF_UP);
    }
}
