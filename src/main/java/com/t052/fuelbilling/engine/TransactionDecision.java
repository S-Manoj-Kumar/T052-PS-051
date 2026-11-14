package com.t052.fuelbilling.engine;

import java.math.BigDecimal;

public class TransactionDecision {

    private final String transactionId;
    private final String cardId;
    private final String accountId;
    private final String outletId;
    private final String timestamp;
    private final String product;
    private final BigDecimal litres;
    private final BigDecimal engineAmount;
    private final String decision;
    private final String reasonCode;
    private final boolean mileageFlag;
    private final boolean varianceFlag;
    private final boolean offlineLate;

    // Kept for compatibility with existing tests or callers.
    public TransactionDecision(
            String transactionId, String cardId, String accountId,
            String timestamp, String product, BigDecimal litres,
            BigDecimal engineAmount, String decision, String reasonCode,
            boolean mileageFlag, boolean varianceFlag, boolean offlineLate) {
        this(transactionId, cardId, accountId, null, timestamp, product,
                litres, engineAmount, decision, reasonCode,
                mileageFlag, varianceFlag, offlineLate);
    }

    public TransactionDecision(
            String transactionId, String cardId, String accountId,
            String outletId, String timestamp, String product,
            BigDecimal litres, BigDecimal engineAmount, String decision,
            String reasonCode, boolean mileageFlag,
            boolean varianceFlag, boolean offlineLate) {
        this.transactionId = transactionId;
        this.cardId = cardId;
        this.accountId = accountId;
        this.outletId = outletId;
        this.timestamp = timestamp;
        this.product = product;
        this.litres = litres;
        this.engineAmount = engineAmount;
        this.decision = decision;
        this.reasonCode = reasonCode;
        this.mileageFlag = mileageFlag;
        this.varianceFlag = varianceFlag;
        this.offlineLate = offlineLate;
    }

    public String getTransactionId() { return transactionId; }
    public String getCardId() { return cardId; }
    public String getAccountId() { return accountId; }
    public String getOutletId() { return outletId; }
    public String getTimestamp() { return timestamp; }
    public String getProduct() { return product; }
    public BigDecimal getLitres() { return litres; }
    public BigDecimal getEngineAmount() { return engineAmount; }
    public String getDecision() { return decision; }
    public String getReasonCode() { return reasonCode; }
    public boolean isMileageFlag() { return mileageFlag; }
    public boolean isVarianceFlag() { return varianceFlag; }
    public boolean isOfflineLate() { return offlineLate; }
}
