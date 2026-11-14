
package com.t052.fuelbilling.model;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.Set;

public class FleetAccount {

    private String accountId;
    private String accountName;
    private BigDecimal creditLimit;
    private String billingCycle;
    private String initialTier;
    private String paymentMode;
    private Set<String> allowedStates;
    private int perVehicleMonthlyCapLitres;
    private int previousMonthLitres;

    public FleetAccount(
            String accountId,
            String accountName,
            BigDecimal creditLimit,
            String billingCycle,
            String initialTier,
            String paymentMode,
            Set<String> allowedStates,
            int perVehicleMonthlyCapLitres,
            int previousMonthLitres) {

        this.accountId = accountId;
        this.accountName = accountName;
        setCreditLimit(creditLimit);
        this.billingCycle = billingCycle;
        this.initialTier = initialTier;
        this.paymentMode = paymentMode;
        this.allowedStates = new HashSet<>(allowedStates);
        this.perVehicleMonthlyCapLitres = perVehicleMonthlyCapLitres;
        this.previousMonthLitres = previousMonthLitres;
    }

    public String getAccountId() {
        return accountId;
    }

    public String getAccountName() {
        return accountName;
    }

    public BigDecimal getCreditLimit() {
        return creditLimit;
    }

    public String getBillingCycle() {
        return billingCycle;
    }

    public String getInitialTier() {
        return initialTier;
    }

    public String getPaymentMode() {
        return paymentMode;
    }

    public Set<String> getAllowedStates() {
        return Set.copyOf(allowedStates);
    }

    public int getPerVehicleMonthlyCapLitres() {
        return perVehicleMonthlyCapLitres;
    }

    public int getPreviousMonthLitres() {
        return previousMonthLitres;
    }

    public void setCreditLimit(BigDecimal creditLimit) {
        if (creditLimit == null ||
                creditLimit.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException(
                    "Credit limit must be greater than zero");
        }

        this.creditLimit = creditLimit;
    }
}
