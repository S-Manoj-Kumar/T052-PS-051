
package com.t052.fuelbilling.model;

import java.math.BigDecimal;
import java.time.LocalDate;

public class Payment {

    private LocalDate paymentDate;
    private String accountId;
    private BigDecimal amount;
    private boolean fullPayment;
    private String mode;
    private String reference;

    public Payment(
            LocalDate paymentDate,
            String accountId,
            BigDecimal amount,
            boolean fullPayment,
            String mode,
            String reference) {

        this.paymentDate = paymentDate;
        this.accountId = accountId;
        this.amount = amount;
        this.fullPayment = fullPayment;
        this.mode = mode;
        this.reference = reference;
    }

    public LocalDate getPaymentDate() {
        return paymentDate;
    }

    public String getAccountId() {
        return accountId;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public boolean isFullPayment() {
        return fullPayment;
    }

    public String getMode() {
        return mode;
    }

    public String getReference() {
        return reference;
    }
}
