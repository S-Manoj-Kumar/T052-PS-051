
package com.t052.fuelbilling.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.t052.fuelbilling.model.enums.TransactionType;

public class Transaction {

    private String transactionId;
    private TransactionType transactionType;
    private String referenceTransactionId;
    private LocalDateTime transactionTimestamp;
    private LocalDateTime postedAt;
    private String cardId;
    private String outletId;
    private String product;
    private BigDecimal litres;
    private BigDecimal pumpAmount;
    private BigDecimal odometerKm;
    private boolean offline;

    public Transaction(
            String transactionId,
            TransactionType transactionType,
            String referenceTransactionId,
            LocalDateTime transactionTimestamp,
            LocalDateTime postedAt,
            String cardId,
            String outletId,
            String product,
            BigDecimal litres,
            BigDecimal pumpAmount,
            BigDecimal odometerKm,
            boolean offline) {

        this.transactionId = transactionId;
        this.transactionType = transactionType;
        this.referenceTransactionId = referenceTransactionId;
        this.transactionTimestamp = transactionTimestamp;
        this.postedAt = postedAt;
        this.cardId = cardId;
        this.outletId = outletId;
        this.product = product;
        this.litres = litres;
        this.pumpAmount = pumpAmount;
        this.odometerKm = odometerKm;
        this.offline = offline;
    }

    public String getTransactionId() {
        return transactionId;
    }

    public TransactionType getTransactionType() {
        return transactionType;
    }

    public String getReferenceTransactionId() {
        return referenceTransactionId;
    }

    public LocalDateTime getTransactionTimestamp() {
        return transactionTimestamp;
    }

    public LocalDateTime getPostedAt() {
        return postedAt;
    }

    public String getCardId() {
        return cardId;
    }

    public String getOutletId() {
        return outletId;
    }

    public String getProduct() {
    return product;
   }

    public BigDecimal getLitres() {
        return litres;
    }

    public BigDecimal getPumpAmount() {
        return pumpAmount;
    }

    public BigDecimal getOdometerKm() {
        return odometerKm;
    }

    public boolean isOffline() {
        return offline;
    }
}
