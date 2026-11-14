package com.t052.fuelbilling.engine;

import com.t052.fuelbilling.model.FleetAccount;
import com.t052.fuelbilling.model.Transaction;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Generates fortnightly invoices and their CSV output files. */
public class InvoiceProcessor {

    private static final BigDecimal ZERO = new BigDecimal("0.00");
    private static final BigDecimal GST_RATE = new BigDecimal("0.18");
    private static final BigDecimal CHEQUE_FEE_RATE = new BigDecimal("0.005");
    private static final BigDecimal MONTHLY_INTEREST_RATE = new BigDecimal("0.02");

    private final EngineContext context;
    private final EngineState state;
    private final Path outputDirectory;

    public InvoiceProcessor(
            EngineContext context, EngineState state, Path outputDirectory) {
        if (context == null || state == null || outputDirectory == null) {
            throw new IllegalArgumentException(
                    "Context, state and output directory cannot be null");
        }
        this.context = context;
        this.state = state;
        this.outputDirectory = outputDirectory;
    }

    public List<InvoiceRecord> generate(LocalDate invoiceDate)
            throws IOException {

        if (invoiceDate == null
                || !(invoiceDate.getDayOfMonth() == 1
                || invoiceDate.getDayOfMonth() == 16)) {
            throw new IllegalArgumentException(
                    "Invoices can only be generated on the 1st or 16th");
        }

        LocalDate periodFrom;
        LocalDate periodTo;

        if (invoiceDate.getDayOfMonth() == 16) {
            periodFrom = invoiceDate.withDayOfMonth(1);
            periodTo = invoiceDate.withDayOfMonth(15);
        } else {
            LocalDate previousMonth = invoiceDate.minusMonths(1);
            periodFrom = previousMonth.withDayOfMonth(16);
            periodTo = previousMonth.withDayOfMonth(
                    previousMonth.lengthOfMonth());
        }

        List<InvoiceRecord> generated = new ArrayList<>();

        for (FleetAccount account : context.getAccounts()) {
            String invoiceId = makeInvoiceId(account.getAccountId(), invoiceDate);

            boolean alreadyExists = state.getInvoices().stream()
                    .anyMatch(invoice -> invoice.getInvoiceId().equals(invoiceId));
            if (alreadyExists) {
                throw new IllegalArgumentException(
                        "Invoice already generated: " + invoiceId);
            }

            generated.add(generateForAccount(
                    account, invoiceId, invoiceDate, periodFrom, periodTo));
        }

        writeInvoiceFiles();
        return List.copyOf(generated);
    }

    private InvoiceRecord generateForAccount(
            FleetAccount account,
            String invoiceId,
            LocalDate invoiceDate,
            LocalDate periodFrom,
            LocalDate periodTo) {

        String accountId = account.getAccountId();
        String tier = determineTier(account, invoiceDate);

        BigDecimal fuelAmount = ZERO;
        BigDecimal lubeAmount = ZERO;
        BigDecimal discountCredit = ZERO;
        BigDecimal reversalCredit = ZERO;

        List<LineEvent> events = new ArrayList<>();
        Set<String> timelyReversedSales = new HashSet<>();

        for (ReversalCredit credit : state.getReversalCredits()) {
            if (credit.getAccountId().equals(accountId)
                    && credit.isTimely()) {
                timelyReversedSales.add(credit.getOriginalTransactionId());
            }
        }

        BigDecimal discountRate = discountRate(tier);

        for (ApprovedSale approvedSale : state.getApprovedSales()) {
            if (!approvedSale.getAccountId().equals(accountId)) {
                continue;
            }

            Transaction transaction = approvedSale.getTransaction();
            LocalDate saleDate = transaction.getTransactionTimestamp().toLocalDate();

            if (saleDate.isBefore(periodFrom) || saleDate.isAfter(periodTo)) {
                continue;
            }

            String product = transaction.getProduct().trim().toUpperCase();
            BigDecimal litres = transaction.getLitres();
            BigDecimal amount = money(approvedSale.getEngineAmount());
            BigDecimal rate = litres.signum() == 0
                    ? ZERO : amount.divide(litres, 4, RoundingMode.HALF_UP);

            if ("LUBE".equals(product)) {
                lubeAmount = lubeAmount.add(amount);
                events.add(new LineEvent(
                        transaction.getTransactionTimestamp(),
                        transaction.getTransactionId(),
                        "LUBE",
                        saleDate,
                        product,
                        litres,
                        rate,
                        amount));
            } else {
                fuelAmount = fuelAmount.add(amount);
                events.add(new LineEvent(
                        transaction.getTransactionTimestamp(),
                        transaction.getTransactionId(),
                        "FUEL",
                        saleDate,
                        product,
                        litres,
                        rate,
                        amount));

                if ("DIESEL".equals(product)
                        && !timelyReversedSales.contains(transaction.getTransactionId())) {
                    BigDecimal discount = money(litres.multiply(discountRate));
                    if (discount.signum() > 0) {
                        discountCredit = discountCredit.add(discount);
                        events.add(new LineEvent(
                                transaction.getTransactionTimestamp(),
                                transaction.getTransactionId(),
                                "DISCOUNT_CREDIT",
                                saleDate,
                                product,
                                litres,
                                discountRate,
                                discount.negate()));
                    }
                }
            }
        }

        for (ReversalCredit credit : state.getReversalCredits()) {
            if (!credit.getAccountId().equals(accountId)) {
                continue;
            }

            LocalDate reversalDate = credit.getReversalTimestamp().toLocalDate();
            if (reversalDate.isBefore(periodFrom)
                    || reversalDate.isAfter(periodTo)) {
                continue;
            }

            BigDecimal amount = money(credit.getAmount());
            BigDecimal creditedAmount = amount;

            // LUBE GST is reversed together with the original LUBE charge.
            if ("LUBE".equalsIgnoreCase(credit.getProduct())) {
                creditedAmount = money(amount.multiply(BigDecimal.ONE.add(GST_RATE)));
            }

            reversalCredit = reversalCredit.add(creditedAmount);

            BigDecimal rate = credit.getLitres() == null
                    || credit.getLitres().signum() == 0
                    ? ZERO
                    : amount.divide(credit.getLitres(), 4, RoundingMode.HALF_UP);

            events.add(new LineEvent(
                    credit.getReversalTimestamp(),
                    credit.getReversalTransactionId(),
                    "REVERSAL_CREDIT",
                    reversalDate,
                    credit.getProduct(),
                    credit.getLitres(),
                    rate,
                    amount.negate()));
        }

        fuelAmount = money(fuelAmount);
        lubeAmount = money(lubeAmount);
        discountCredit = money(discountCredit);
        reversalCredit = money(reversalCredit);

        // GST is shown separately for LUBE; the LUBE reversal credit above
        // also reverses its corresponding GST component.
        BigDecimal lubeGst = money(lubeAmount.multiply(GST_RATE));

        BigDecimal priorBalance = ZERO;
        BigDecimal interest = ZERO;

        for (InvoiceRecord previous : state.getInvoicesForAccount(accountId)) {
            BigDecimal outstandingBeforeInterest =
                    money(previous.getOutstandingBalance());

            priorBalance = priorBalance.add(outstandingBeforeInterest);

            // Interest may already have been accrued when an earlier payment
            // was made after the due date. Accrue only the remaining days.
            BigDecimal newlyAccruedInterest =
                    previous.accrueInterestUntil(
                            invoiceDate, MONTHLY_INTEREST_RATE);

            interest = interest.add(newlyAccruedInterest);

            // The new invoice now carries this balance forward. Close the old
            // balance so it is not counted twice during future payments.
            previous.closeAfterCarryForward();
        }

        priorBalance = money(priorBalance);
        interest = money(interest);

        BigDecimal amountBeforeFee = fuelAmount
                .add(lubeAmount)
                .add(lubeGst)
                .subtract(discountCredit)
                .subtract(reversalCredit)
                .add(priorBalance)
                .add(interest);

        BigDecimal convenienceFee = ZERO;
        if ("CHEQUE".equalsIgnoreCase(account.getPaymentMode())) {
            convenienceFee = money(amountBeforeFee
                    .max(BigDecimal.ZERO)
                    .multiply(CHEQUE_FEE_RATE));
        }

        BigDecimal totalDue = money(amountBeforeFee.add(convenienceFee));
        LocalDate dueDate = invoiceDate.plusDays(7);

        // Java's list sort is stable, so lines belonging to the same
        // transaction retain the order in which they were added.
        events.sort(Comparator
                .comparing(LineEvent::timestamp)
                .thenComparing(LineEvent::transactionId));

        int lineNumber = 1;
        for (LineEvent event : events) {
            state.addInvoiceLine(new InvoiceLine(
                    invoiceId,
                    lineNumber++,
                    event.lineType(),
                    event.transactionId(),
                    event.transactionDate(),
                    event.product(),
                    event.litres(),
                    event.rate(),
                    money(event.amount())));
        }

        InvoiceRecord invoice = new InvoiceRecord(
                invoiceId,
                accountId,
                invoiceDate,
                periodFrom,
                periodTo,
                tier,
                fuelAmount,
                lubeAmount,
                lubeGst,
                discountCredit,
                reversalCredit,
                priorBalance,
                interest,
                convenienceFee,
                totalDue,
                dueDate);

        state.addInvoice(invoice);

        // Move the transactions covered by this invoice out of unbilled
        // exposure. Their unpaid amount is now represented by the invoice.
        state.subtractUnbilledAmount(
                accountId, fuelAmount.add(lubeAmount));

        // Apply any excess payment retained from an earlier payment cycle.
        BigDecimal availableCredit = state.consumeAccountCredit(
                accountId, invoice.getOutstandingBalance());

        if (availableCredit.signum() > 0) {
            BigDecimal appliedCredit = invoice.applyPayment(availableCredit);

            state.addPaymentAllocation(new PaymentAllocation(
                    accountId,
                    invoiceDate,
                    "ACCOUNT_CREDIT",
                    "CREDIT",
                    invoiceId,
                    "CREDIT_APPLIED",
                    appliedCredit));
        }

        return invoice;
    }

    private String determineTier(FleetAccount account, LocalDate invoiceDate) {
        LocalDate previousMonth = invoiceDate.minusMonths(1);
        LocalDate monthStart = previousMonth.withDayOfMonth(1);
        LocalDate monthEnd = previousMonth.withDayOfMonth(
                previousMonth.lengthOfMonth());

        // The first month in the sample has no transaction history.
        // In that case, use the account's supplied previous-month litres.
        boolean previousMonthIsCovered = state.getDecisions().stream()
                .map(TransactionDecision::getTimestamp)
                .filter(timestamp -> timestamp != null && !timestamp.isBlank())
                .map(LocalDateTime::parse)
                .anyMatch(timestamp ->
                        YearMonth.from(timestamp).equals(YearMonth.from(previousMonth)));

        int previousMonthLitres = previousMonthIsCovered
                ? getNetDieselLitres(account.getAccountId(), monthStart, monthEnd)
                : account.getPreviousMonthLitres();

        if (previousMonthLitres < 20000) return "SILVER";
        if (previousMonthLitres <= 60000) return "GOLD";
        return "PLATINUM";
    }

    private int getNetDieselLitres(
            String accountId, LocalDate from, LocalDate to) {
        BigDecimal litres = BigDecimal.ZERO;

        for (ApprovedSale sale : state.getApprovedSales()) {
            Transaction transaction = sale.getTransaction();
            LocalDate date = transaction.getTransactionTimestamp().toLocalDate();
            if (sale.getAccountId().equals(accountId)
                    && "DIESEL".equalsIgnoreCase(transaction.getProduct())
                    && !date.isBefore(from) && !date.isAfter(to)) {
                litres = litres.add(transaction.getLitres());
            }
        }

        for (ReversalCredit credit : state.getReversalCredits()) {
            if (!credit.getAccountId().equals(accountId)
                    || !credit.isTimely()
                    || !"DIESEL".equalsIgnoreCase(credit.getProduct())) {
                continue;
            }
            ApprovedSale original = state.getApprovedSale(
                    credit.getOriginalTransactionId());
            if (original != null) {
                LocalDate originalDate =
                        original.getTransaction().getTransactionTimestamp().toLocalDate();
                if (!originalDate.isBefore(from) && !originalDate.isAfter(to)) {
                    litres = litres.subtract(credit.getLitres());
                }
            }
        }

        return litres.max(BigDecimal.ZERO).setScale(0, RoundingMode.HALF_UP)
                .intValue();
    }

    private BigDecimal discountRate(String tier) {
        return switch (tier) {
            case "GOLD" -> new BigDecimal("0.80");
            case "PLATINUM" -> new BigDecimal("1.20");
            default -> new BigDecimal("0.40");
        };
    }

    private String makeInvoiceId(String accountId, LocalDate date) {
        return "INV-" + accountId + "-"
                + date.toString().replace("-", "");
    }

    private BigDecimal money(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP);
    }

    private void writeInvoiceFiles() throws IOException {
        Files.createDirectories(outputDirectory);

        List<String> invoiceRows = new ArrayList<>();
        invoiceRows.add("invoice_id,account_id,invoice_date,period_from,period_to,tier,"
                + "fuel_amount,lube_amount,lube_gst,discount_credit,reversal_credit,"
                + "prior_balance,interest,convenience_fee,total_due,due_date");

        for (InvoiceRecord invoice : state.getInvoices()) {
            invoiceRows.add(String.join(",",
                    csv(invoice.getInvoiceId()),
                    csv(invoice.getAccountId()),
                    invoice.getInvoiceDate().toString(),
                    invoice.getPeriodFrom().toString(),
                    invoice.getPeriodTo().toString(),
                    invoice.getTier(),
                    money(invoice.getFuelAmount()).toPlainString(),
                    money(invoice.getLubeAmount()).toPlainString(),
                    money(invoice.getLubeGst()).toPlainString(),
                    money(invoice.getDiscountCredit()).toPlainString(),
                    money(invoice.getReversalCredit()).toPlainString(),
                    money(invoice.getPriorBalance()).toPlainString(),
                    money(invoice.getInterest()).toPlainString(),
                    money(invoice.getConvenienceFee()).toPlainString(),
                    money(invoice.getTotalDue()).toPlainString(),
                    invoice.getDueDate().toString()));
        }

        List<String> lineRows = new ArrayList<>();
        lineRows.add("invoice_id,line_no,line_type,txn_id,txn_date,product,litres,rate,amount");

        for (InvoiceLine line : state.getInvoiceLines()) {
            lineRows.add(String.join(",",
                    csv(line.getInvoiceId()),
                    Integer.toString(line.getLineNumber()),
                    csv(line.getLineType()),
                    csv(line.getTransactionId()),
                    line.getTransactionDate() == null ? "" : line.getTransactionDate().toString(),
                    csv(line.getProduct()),
                    line.getLitres() == null ? "" : line.getLitres().toPlainString(),
                    line.getRate() == null ? "" : line.getRate().toPlainString(),
                    money(line.getAmount()).toPlainString()));
        }

        Files.write(outputDirectory.resolve("invoices.csv"),
                invoiceRows, StandardCharsets.UTF_8);
        Files.write(outputDirectory.resolve("invoice_lines.csv"),
                lineRows, StandardCharsets.UTF_8);
    }

    private String csv(String value) {
        if (value == null) return "";
        return "\"" + value.replace("\"", "\"\"") + "\"";
    }

    private record LineEvent(
            LocalDateTime timestamp,
            String transactionId,
            String lineType,
            LocalDate transactionDate,
            String product,
            BigDecimal litres,
            BigDecimal rate,
            BigDecimal amount) {}
}
