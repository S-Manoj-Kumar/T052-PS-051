package com.t052.fuelbilling.engine;

import com.t052.fuelbilling.model.Transaction;
import com.t052.fuelbilling.model.enums.FuelProduct;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** Validates and applies reversals of previously approved sales. */
public class ReversalProcessor {

    private static final long REVERSAL_WINDOW_HOURS = 24;

    private final EngineContext context;
    private final EngineState state;

    public ReversalProcessor(EngineContext context, EngineState state) {
        if (context == null || state == null) {
            throw new IllegalArgumentException(
                    "Engine context and state cannot be null");
        }
        this.context = context;
        this.state = state;
    }

    public TransactionDecision process(Transaction reversal) {
        if (reversal == null) {
            throw new IllegalArgumentException("Transaction cannot be null");
        }

        String reversalId = reversal.getTransactionId();

        if (reversalId == null || reversalId.isBlank()
                || state.isTransactionProcessed(reversalId)) {
            return decision(reversal, accountIdFor(reversal), null,
                    "DECLINE", "DUPLICATE", false);
        }

        LocalDateTime reversalTime = reversal.getTransactionTimestamp();
        LocalDateTime postedAt = reversal.getPostedAt();

        if (reversalTime == null || postedAt == null) {
            state.markTransactionProcessed(reversalId);
            return decision(reversal, accountIdFor(reversal), null,
                    "DECLINE", "INVALID_TIMESTAMP", false);
        }

        if (postedAt.isBefore(reversalTime)) {
            state.markTransactionProcessed(reversalId);
            return decision(reversal, accountIdFor(reversal), null,
                    "DECLINE", "INVALID_POSTING", false);
        }

        String originalId = reversal.getReferenceTransactionId();
        if (originalId == null || originalId.isBlank()) {
            state.markTransactionProcessed(reversalId);
            return decision(reversal, accountIdFor(reversal), null,
                    "DECLINE", "INVALID_REVERSAL_REFERENCE", false);
        }

        ApprovedSale approvedSale = state.getApprovedSale(originalId);
        if (approvedSale == null) {
            state.markTransactionProcessed(reversalId);
            return decision(reversal, accountIdFor(reversal), null,
                    "DECLINE", "INVALID_REVERSAL_REFERENCE", false);
        }

        if (state.isSaleAlreadyReversed(originalId)) {
            state.markTransactionProcessed(reversalId);
            return decision(reversal, approvedSale.getAccountId(), null,
                    "DECLINE", "ALREADY_REVERSED", false);
        }

        Transaction original = approvedSale.getTransaction();

        if (!matchesOriginal(reversal, original)) {
            state.markTransactionProcessed(reversalId);
            return decision(reversal, approvedSale.getAccountId(), null,
                    "DECLINE", "REVERSAL_DETAILS_MISMATCH", false);
        }

        long seconds = Duration.between(
                original.getTransactionTimestamp(), reversalTime).getSeconds();

        if (seconds < 0) {
            state.markTransactionProcessed(reversalId);
            return decision(reversal, approvedSale.getAccountId(), null,
                    "DECLINE", "INVALID_REVERSAL_TIME", false);
        }

        boolean timely = seconds <= REVERSAL_WINDOW_HOURS * 60L * 60L;
        LocalDate originalDate = original.getTransactionTimestamp().toLocalDate();
        String product = original.getProduct().trim().toUpperCase();
        BigDecimal litres = original.getLitres();
        BigDecimal amount = approvedSale.getEngineAmount();

        // Mark both IDs before applying the credit to prevent repeat reversals.
        state.markTransactionProcessed(reversalId);
        state.markSaleReversed(originalId);

        // Every accepted reversal becomes a credit for the invoice engine.
        state.addReversalCredit(new ReversalCredit(
                reversalId,
                originalId,
                approvedSale.getAccountId(),
                reversalTime,
                product,
                litres,
                amount,
                timely
        ));

        if (timely) {
            // A timely reversal restores the original transaction's limits.
            if (!FuelProduct.LUBE.name().equals(product)) {
                state.reverseDailyUsage(
                        original.getCardId(), originalDate, litres, reversalTime);
                state.reverseMonthlyLitres(
                        approvedSale.getVehicleId(), originalDate, litres, reversalTime);
            } else {
                state.reverseLubeSpend(
                        approvedSale.getAccountId(), originalDate, amount, reversalTime);
            }

            // Restore available credit immediately for timely reversals.
            state.subtractUnbilledAmount(
                    approvedSale.getAccountId(), amount);
        }

        return decision(
                reversal,
                approvedSale.getAccountId(),
                amount,
                "APPROVE",
                timely ? "REVERSAL_APPROVED" : "LATE_REVERSAL_CREDIT",
                !timely
        );
    }

    private boolean matchesOriginal(
            Transaction reversal, Transaction original) {

        if (!safeEquals(reversal.getCardId(), original.getCardId())) {
            return false;
        }

        if (!safeEqualsIgnoreCase(
                reversal.getProduct(), original.getProduct())) {
            return false;
        }

        if (reversal.getLitres() == null || original.getLitres() == null
                || reversal.getLitres().compareTo(original.getLitres()) != 0) {
            return false;
        }

        return reversal.getPumpAmount() != null
                && original.getPumpAmount() != null
                && reversal.getPumpAmount().compareTo(
                        original.getPumpAmount()) == 0;
    }

    private String accountIdFor(Transaction transaction) {
        return context.findAccountForCard(transaction.getCardId())
                .map(account -> account.getAccountId())
                .orElse(null);
    }

    private boolean safeEquals(String first, String second) {
        return first != null && first.equals(second);
    }

    private boolean safeEqualsIgnoreCase(String first, String second) {
        return first != null && second != null
                && first.equalsIgnoreCase(second);
    }

    private TransactionDecision decision(
            Transaction transaction,
            String accountId,
            BigDecimal amount,
            String decision,
            String reason,
            boolean late) {

        String timestamp = transaction.getTransactionTimestamp() == null
                ? "" : transaction.getTransactionTimestamp().toString();

        TransactionDecision result = new TransactionDecision(
                transaction.getTransactionId(),
                transaction.getCardId(),
                accountId,
                transaction.getOutletId(),
                timestamp,
                transaction.getProduct(),
                transaction.getLitres(),
                amount,
                decision,
                reason,
                false,
                false,
                false
        );

        state.addDecision(result);
        return result;
    }
}
