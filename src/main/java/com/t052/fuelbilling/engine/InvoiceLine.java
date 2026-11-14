package com.t052.fuelbilling.engine;

import java.math.BigDecimal;
import java.time.LocalDate;

/** A printable transaction-level invoice line. Credit amounts are negative. */
public class InvoiceLine {

    private final String invoiceId;
    private final int lineNumber;
    private final String lineType;
    private final String transactionId;
    private final LocalDate transactionDate;
    private final String product;
    private final BigDecimal litres;
    private final BigDecimal rate;
    private final BigDecimal amount;

    public InvoiceLine(
            String invoiceId, int lineNumber, String lineType,
            String transactionId, LocalDate transactionDate,
            String product, BigDecimal litres, BigDecimal rate,
            BigDecimal amount) {
        this.invoiceId = invoiceId;
        this.lineNumber = lineNumber;
        this.lineType = lineType;
        this.transactionId = transactionId;
        this.transactionDate = transactionDate;
        this.product = product;
        this.litres = litres;
        this.rate = rate;
        this.amount = amount;
    }

    public String getInvoiceId() { return invoiceId; }
    public int getLineNumber() { return lineNumber; }
    public String getLineType() { return lineType; }
    public String getTransactionId() { return transactionId; }
    public LocalDate getTransactionDate() { return transactionDate; }
    public String getProduct() { return product; }
    public BigDecimal getLitres() { return litres; }
    public BigDecimal getRate() { return rate; }
    public BigDecimal getAmount() { return amount; }
}
