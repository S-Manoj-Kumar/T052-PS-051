package com.t052.fuelbilling.engine;

import com.t052.fuelbilling.model.Transaction;

import java.math.BigDecimal;

/** Stores the details needed to validate a later reversal. */
public class ApprovedSale {

    private final Transaction transaction;
    private final String accountId;
    private final String vehicleId;
    private final BigDecimal engineAmount;

    public ApprovedSale(
            Transaction transaction,
            String accountId,
            String vehicleId,
            BigDecimal engineAmount) {

        if (transaction == null || accountId == null
                || vehicleId == null || engineAmount == null) {
            throw new IllegalArgumentException(
                    "Approved sale details cannot be null");
        }

        this.transaction = transaction;
        this.accountId = accountId;
        this.vehicleId = vehicleId;
        this.engineAmount = engineAmount;
    }

    public Transaction getTransaction() {
        return transaction;
    }

    public String getAccountId() {
        return accountId;
    }

    public String getVehicleId() {
        return vehicleId;
    }

    public BigDecimal getEngineAmount() {
        return engineAmount;
    }
}
