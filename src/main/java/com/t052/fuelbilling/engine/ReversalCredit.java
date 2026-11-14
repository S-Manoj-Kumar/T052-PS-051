package com.t052.fuelbilling.engine;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** Credit created when an approved sale is reversed. */
public class ReversalCredit {

    private final String reversalTransactionId;
    private final String originalTransactionId;
    private final String accountId;
    private final LocalDateTime reversalTimestamp;
    private final String product;
    private final BigDecimal litres;
    private final BigDecimal amount;
    private final boolean timely;

    public ReversalCredit(
            String reversalTransactionId,
            String originalTransactionId,
            String accountId,
            LocalDateTime reversalTimestamp,
            String product,
            BigDecimal litres,
            BigDecimal amount,
            boolean timely) {

        this.reversalTransactionId = reversalTransactionId;
        this.originalTransactionId = originalTransactionId;
        this.accountId = accountId;
        this.reversalTimestamp = reversalTimestamp;
        this.product = product;
        this.litres = litres;
        this.amount = amount;
        this.timely = timely;
    }

    public String getReversalTransactionId() { return reversalTransactionId; }
    public String getOriginalTransactionId() { return originalTransactionId; }
    public String getAccountId() { return accountId; }
    public LocalDateTime getReversalTimestamp() { return reversalTimestamp; }
    public String getProduct() { return product; }
    public BigDecimal getLitres() { return litres; }
    public BigDecimal getAmount() { return amount; }
    public boolean isTimely() { return timely; }
}