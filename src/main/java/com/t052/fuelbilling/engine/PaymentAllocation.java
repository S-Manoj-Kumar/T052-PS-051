package com.t052.fuelbilling.engine;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Records how a payment or carried account credit was applied. */
public class PaymentAllocation {

    private final String accountId;
    private final LocalDate date;
    private final String reference;
    private final String mode;
    private final String invoiceId;
    private final String allocationType;
    private final BigDecimal amount;

    public PaymentAllocation(
            String accountId,
            LocalDate date,
            String reference,
            String mode,
            String invoiceId,
            String allocationType,
            BigDecimal amount) {
        this.accountId = accountId;
        this.date = date;
        this.reference = reference;
        this.mode = mode;
        this.invoiceId = invoiceId;
        this.allocationType = allocationType;
        this.amount = amount;
    }

    public String getAccountId() { return accountId; }
    public LocalDate getDate() { return date; }
    public String getReference() { return reference; }
    public String getMode() { return mode; }
    public String getInvoiceId() { return invoiceId; }
    public String getAllocationType() { return allocationType; }
    public BigDecimal getAmount() { return amount; }
}
