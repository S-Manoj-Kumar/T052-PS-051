package com.t052.fuelbilling.engine;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.math.RoundingMode;

/** One generated account invoice and its remaining unpaid balance. */
public class InvoiceRecord {

    private final String invoiceId;
    private final String accountId;
    private final LocalDate invoiceDate;
    private final LocalDate periodFrom;
    private final LocalDate periodTo;
    private final String tier;
    private final BigDecimal fuelAmount;
    private final BigDecimal lubeAmount;
    private final BigDecimal lubeGst;
    private final BigDecimal discountCredit;
    private final BigDecimal reversalCredit;
    private final BigDecimal priorBalance;
    private final BigDecimal interest;
    private final BigDecimal convenienceFee;
    private final BigDecimal totalDue;
    private final LocalDate dueDate;
    private BigDecimal outstandingBalance;
    private LocalDate interestAccruedThrough;

    public InvoiceRecord(
            String invoiceId, String accountId,
            LocalDate invoiceDate, LocalDate periodFrom, LocalDate periodTo,
            String tier, BigDecimal fuelAmount, BigDecimal lubeAmount,
            BigDecimal lubeGst, BigDecimal discountCredit,
            BigDecimal reversalCredit, BigDecimal priorBalance,
            BigDecimal interest, BigDecimal convenienceFee,
            BigDecimal totalDue, LocalDate dueDate) {

        this.invoiceId = invoiceId;
        this.accountId = accountId;
        this.invoiceDate = invoiceDate;
        this.periodFrom = periodFrom;
        this.periodTo = periodTo;
        this.tier = tier;
        this.fuelAmount = fuelAmount;
        this.lubeAmount = lubeAmount;
        this.lubeGst = lubeGst;
        this.discountCredit = discountCredit;
        this.reversalCredit = reversalCredit;
        this.priorBalance = priorBalance;
        this.interest = interest;
        this.convenienceFee = convenienceFee;
        this.totalDue = totalDue;
        this.dueDate = dueDate;
        this.outstandingBalance = totalDue;
        this.interestAccruedThrough = dueDate;
    }

    public String getInvoiceId() { return invoiceId; }
    public String getAccountId() { return accountId; }
    public LocalDate getInvoiceDate() { return invoiceDate; }
    public LocalDate getPeriodFrom() { return periodFrom; }
    public LocalDate getPeriodTo() { return periodTo; }
    public String getTier() { return tier; }
    public BigDecimal getFuelAmount() { return fuelAmount; }
    public BigDecimal getLubeAmount() { return lubeAmount; }
    public BigDecimal getLubeGst() { return lubeGst; }
    public BigDecimal getDiscountCredit() { return discountCredit; }
    public BigDecimal getReversalCredit() { return reversalCredit; }
    public BigDecimal getPriorBalance() { return priorBalance; }
    public BigDecimal getInterest() { return interest; }
    public BigDecimal getConvenienceFee() { return convenienceFee; }
    public BigDecimal getTotalDue() { return totalDue; }
    public LocalDate getDueDate() { return dueDate; }
    public BigDecimal getOutstandingBalance() { return outstandingBalance; }

    /**
     * Applies as much of a payment as this invoice currently owes.
     * Returns the amount actually applied; any excess remains with the caller.
     */
    public BigDecimal applyPayment(BigDecimal amount) {
        if (amount == null || amount.signum() <= 0) {
            throw new IllegalArgumentException("Payment must be positive");
        }

        BigDecimal applied = amount.min(outstandingBalance);
        outstandingBalance = outstandingBalance.subtract(applied)
                .setScale(2, RoundingMode.HALF_UP);
        return applied;
    }

    /**
     * Adds overdue interest for the days since interestAccruedThrough.
     * The caller supplies the monthly rate (normally 2%).
     */
    public BigDecimal accrueInterestUntil(
            LocalDate date, BigDecimal monthlyRate) {
        if (date == null || monthlyRate == null) {
            throw new IllegalArgumentException(
                    "Interest date and rate cannot be null");
        }
        if (!date.isAfter(dueDate) || !date.isAfter(interestAccruedThrough)) {
            return BigDecimal.ZERO.setScale(2);
        }

        long overdueDays = ChronoUnit.DAYS.between(
                interestAccruedThrough, date);

        BigDecimal interest = outstandingBalance
                .multiply(monthlyRate)
                .multiply(BigDecimal.valueOf(overdueDays))
                .divide(new BigDecimal("30"), 2, RoundingMode.HALF_UP);

        interestAccruedThrough = date;

        if (interest.signum() > 0 && outstandingBalance.signum() > 0) {
            outstandingBalance = outstandingBalance.add(interest)
                    .setScale(2, RoundingMode.HALF_UP);
        }
        return interest;
    }

    /** Closes this invoice after its balance has been carried to a new invoice. */
    public void closeAfterCarryForward() {
        outstandingBalance = BigDecimal.ZERO.setScale(2);
    }

    public LocalDate getInterestAccruedThrough() {
        return interestAccruedThrough;
    }
}
