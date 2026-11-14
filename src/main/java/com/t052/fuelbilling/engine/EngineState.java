package com.t052.fuelbilling.engine;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.Comparator;
import com.t052.fuelbilling.model.FleetAccount;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

public class EngineState {

    private final Map<CardDate, DailyUsage> dailyUsage;
    private final Map<VehicleMonth, BigDecimal> monthlyLitres;
    private final Map<AccountMonth, BigDecimal> monthlyLubeSpend;

    // Timestamped usage events let late-posted transactions evaluate limits
    // using only activity that existed at the original pump time.
    private final Map<CardDate, List<UsageEvent>> dailyUsageEvents;
    private final Map<VehicleMonth, List<UsageEvent>> monthlyUsageEvents;
    private final Map<AccountMonth, List<LubeEvent>> monthlyLubeEvents;

    private final Map<String, BigDecimal> lastOdometer;
    private final Map<String, TreeMap<LocalDateTime, BigDecimal>> odometerHistory;
    private final Map<String, BigDecimal> unbilledAmounts;
    private final Map<String, BigDecimal> outletRiskLedger;

    private final Map<CardDate, Integer> dailyDeclines;
    private final Map<CardDate, Integer> blockedAttempts;

    private final Set<String> processedTransactionIds;
    private final List<TransactionDecision> decisions;

    // Reversal support: retain approved sales and credits for invoicing.
    private final Map<String, ApprovedSale> approvedSales;
    private final Set<String> reversedSaleIds;
    private final List<ReversalCredit> reversalCredits;

    // Invoice history and printable invoice lines.
    private final List<InvoiceRecord> invoices;
    private final List<InvoiceLine> invoiceLines;

    // Payment processing and unapplied account credits.
    private final List<PaymentAllocation> paymentAllocations;
    private final Map<String, BigDecimal> accountCredits;
    private final Map<String, BigDecimal> originalCreditLimits;

    public EngineState() {
        dailyUsage = new HashMap<>();
        monthlyLitres = new HashMap<>();
        monthlyLubeSpend = new HashMap<>();
        dailyUsageEvents = new HashMap<>();
        monthlyUsageEvents = new HashMap<>();
        monthlyLubeEvents = new HashMap<>();

        lastOdometer = new HashMap<>();
        odometerHistory = new HashMap<>();
        unbilledAmounts = new HashMap<>();
        outletRiskLedger = new HashMap<>();

        dailyDeclines = new HashMap<>();
        blockedAttempts = new HashMap<>();

        processedTransactionIds = new HashSet<>();
        decisions = new ArrayList<>();

        approvedSales = new HashMap<>();
        reversedSaleIds = new HashSet<>();
        reversalCredits = new ArrayList<>();
        invoices = new ArrayList<>();
        invoiceLines = new ArrayList<>();

        paymentAllocations = new ArrayList<>();
        accountCredits = new HashMap<>();
        originalCreditLimits = new HashMap<>();
    }

    // ---------- Daily usage ----------

    public int getDailyTransactionCount(String cardId, LocalDate date) {
        return getDailyUsage(cardId, date).transactionCount;
    }

    public int getDailyTransactionCount(
            String cardId, LocalDateTime asOfTime) {
        CardDate key = new CardDate(cardId, asOfTime.toLocalDate());
        List<UsageEvent> events = dailyUsageEvents.get(key);
        if (events == null) return getDailyTransactionCount(cardId, asOfTime.toLocalDate());
        return events.stream()
                .filter(event -> !event.timestamp().isAfter(asOfTime))
                .mapToInt(UsageEvent::transactionCountDelta)
                .sum();
    }

    public BigDecimal getDailyLitres(String cardId, LocalDate date) {
        return getDailyUsage(cardId, date).litres;
    }

    public BigDecimal getDailyLitres(String cardId, LocalDateTime asOfTime) {
        CardDate key = new CardDate(cardId, asOfTime.toLocalDate());
        List<UsageEvent> events = dailyUsageEvents.get(key);
        if (events == null) return getDailyLitres(cardId, asOfTime.toLocalDate());
        return events.stream()
                .filter(event -> !event.timestamp().isAfter(asOfTime))
                .map(UsageEvent::litresDelta)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public void recordDailyUsage(
            String cardId, LocalDateTime transactionTime, BigDecimal litres) {
        recordDailyUsage(cardId, transactionTime.toLocalDate(), litres);
        CardDate key = new CardDate(cardId, transactionTime.toLocalDate());
        dailyUsageEvents.computeIfAbsent(key, ignored -> new ArrayList<>())
                .add(new UsageEvent(transactionTime, 1, litres));
    }

    public void reverseDailyUsage(
            String cardId, LocalDate date, BigDecimal litres,
            LocalDateTime reversalTime) {
        reverseDailyUsage(cardId, date, litres);
        CardDate key = new CardDate(cardId, date);
        dailyUsageEvents.computeIfAbsent(key, ignored -> new ArrayList<>())
                .add(new UsageEvent(reversalTime, -1, litres.negate()));
    }

    public void recordDailyUsage(
            String cardId, LocalDate date, BigDecimal litres) {
        CardDate key = new CardDate(cardId, date);
        DailyUsage usage = dailyUsage.computeIfAbsent(
                key, ignored -> new DailyUsage());
        usage.transactionCount++;
        usage.litres = usage.litres.add(litres);
    }

    /** Restores one successful fuelling transaction after a timely reversal. */
    public void reverseDailyUsage(
            String cardId, LocalDate date, BigDecimal litres) {
        CardDate key = new CardDate(cardId, date);
        DailyUsage usage = dailyUsage.get(key);
        if (usage == null) return;

        usage.transactionCount = Math.max(0, usage.transactionCount - 1);
        usage.litres = nonNegative(usage.litres.subtract(litres));

        if (usage.transactionCount == 0
                && usage.litres.compareTo(BigDecimal.ZERO) == 0) {
            dailyUsage.remove(key);
        }
    }

    private DailyUsage getDailyUsage(String cardId, LocalDate date) {
        return dailyUsage.getOrDefault(
                new CardDate(cardId, date), new DailyUsage());
    }

    // ---------- Monthly vehicle usage ----------

    public BigDecimal getMonthlyLitres(String vehicleId, LocalDate date) {
        return monthlyLitres.getOrDefault(
                new VehicleMonth(vehicleId, YearMonth.from(date)),
                BigDecimal.ZERO);
    }

    public BigDecimal getMonthlyLitres(
            String vehicleId, LocalDateTime asOfTime) {
        VehicleMonth key = new VehicleMonth(
                vehicleId, YearMonth.from(asOfTime.toLocalDate()));
        List<UsageEvent> events = monthlyUsageEvents.get(key);
        if (events == null) {
            return getMonthlyLitres(vehicleId, asOfTime.toLocalDate());
        }
        return events.stream()
                .filter(event -> !event.timestamp().isAfter(asOfTime))
                .map(UsageEvent::litresDelta)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public void recordMonthlyLitres(
            String vehicleId, LocalDateTime transactionTime, BigDecimal litres) {
        recordMonthlyLitres(vehicleId, transactionTime.toLocalDate(), litres);
        VehicleMonth key = new VehicleMonth(
                vehicleId, YearMonth.from(transactionTime.toLocalDate()));
        monthlyUsageEvents.computeIfAbsent(key, ignored -> new ArrayList<>())
                .add(new UsageEvent(transactionTime, 0, litres));
    }

    public void reverseMonthlyLitres(
            String vehicleId, LocalDate date, BigDecimal litres,
            LocalDateTime reversalTime) {
        reverseMonthlyLitres(vehicleId, date, litres);
        VehicleMonth key = new VehicleMonth(vehicleId, YearMonth.from(date));
        monthlyUsageEvents.computeIfAbsent(key, ignored -> new ArrayList<>())
                .add(new UsageEvent(reversalTime, 0, litres.negate()));
    }

    public void recordMonthlyLitres(
            String vehicleId, LocalDate date, BigDecimal litres) {
        VehicleMonth key = new VehicleMonth(vehicleId, YearMonth.from(date));
        monthlyLitres.merge(key, litres, BigDecimal::add);
    }

    /** Monthly litres are restored only for reversals within 24 hours. */
    public void reverseMonthlyLitres(
            String vehicleId, LocalDate date, BigDecimal litres) {
        VehicleMonth key = new VehicleMonth(vehicleId, YearMonth.from(date));
        BigDecimal updated = nonNegative(
                monthlyLitres.getOrDefault(key, BigDecimal.ZERO)
                        .subtract(litres));
        if (updated.compareTo(BigDecimal.ZERO) == 0) {
            monthlyLitres.remove(key);
        } else {
            monthlyLitres.put(key, updated);
        }
    }

    // ---------- Monthly LUBE spending ----------

    public BigDecimal getMonthlyLubeSpend(String accountId, LocalDate date) {
        return monthlyLubeSpend.getOrDefault(
                new AccountMonth(accountId, YearMonth.from(date)),
                BigDecimal.ZERO);
    }

    public BigDecimal getMonthlyLubeSpend(
            String accountId, LocalDateTime asOfTime) {
        AccountMonth key = new AccountMonth(
                accountId, YearMonth.from(asOfTime.toLocalDate()));
        List<LubeEvent> events = monthlyLubeEvents.get(key);
        if (events == null) {
            return getMonthlyLubeSpend(accountId, asOfTime.toLocalDate());
        }
        return events.stream()
                .filter(event -> !event.timestamp().isAfter(asOfTime))
                .map(LubeEvent::amountDelta)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public void recordLubeSpend(
            String accountId, LocalDateTime transactionTime, BigDecimal amount) {
        recordLubeSpend(accountId, transactionTime.toLocalDate(), amount);
        AccountMonth key = new AccountMonth(
                accountId, YearMonth.from(transactionTime.toLocalDate()));
        monthlyLubeEvents.computeIfAbsent(key, ignored -> new ArrayList<>())
                .add(new LubeEvent(transactionTime, amount));
    }

    public void reverseLubeSpend(
            String accountId, LocalDate date, BigDecimal amount,
            LocalDateTime reversalTime) {
        reverseLubeSpend(accountId, date, amount);
        AccountMonth key = new AccountMonth(accountId, YearMonth.from(date));
        monthlyLubeEvents.computeIfAbsent(key, ignored -> new ArrayList<>())
                .add(new LubeEvent(reversalTime, amount.negate()));
    }

    public void recordLubeSpend(
            String accountId, LocalDate date, BigDecimal amount) {
        AccountMonth key = new AccountMonth(accountId, YearMonth.from(date));
        monthlyLubeSpend.merge(key, amount, BigDecimal::add);
    }

    public void reverseLubeSpend(
            String accountId, LocalDate date, BigDecimal amount) {
        AccountMonth key = new AccountMonth(accountId, YearMonth.from(date));
        BigDecimal updated = nonNegative(
                monthlyLubeSpend.getOrDefault(key, BigDecimal.ZERO)
                        .subtract(amount));
        if (updated.compareTo(BigDecimal.ZERO) == 0) {
            monthlyLubeSpend.remove(key);
        } else {
            monthlyLubeSpend.put(key, updated);
        }
    }

    // ---------- Odometer ----------

    /**
     * Returns the most recent odometer reading strictly before the supplied
     * transaction time. This keeps late-posted transactions from making an
     * older transaction compare against a newer reading.
     */
    public BigDecimal getPreviousOdometer(
            String vehicleId, LocalDateTime transactionTime) {
        TreeMap<LocalDateTime, BigDecimal> history =
                odometerHistory.get(vehicleId);

        if (history == null || history.isEmpty()) {
            return lastOdometer.get(vehicleId);
        }

        Map.Entry<LocalDateTime, BigDecimal> previous =
                history.lowerEntry(transactionTime);
        return previous == null ? null : previous.getValue();
    }

    /** Records an odometer reading with its actual transaction timestamp. */
    public void updateOdometer(
            String vehicleId,
            LocalDateTime transactionTime,
            BigDecimal odometerKm) {
        if (vehicleId == null || transactionTime == null || odometerKm == null) {
            return;
        }

        odometerHistory.computeIfAbsent(
                vehicleId, ignored -> new TreeMap<>())
                .put(transactionTime, odometerKm);

        // Keep the legacy latest-reading accessor consistent with event time.
        Map.Entry<LocalDateTime, BigDecimal> latest =
                odometerHistory.get(vehicleId).lastEntry();
        lastOdometer.put(vehicleId, latest.getValue());
    }

    /**
     * Legacy helper retained for existing callers and unit tests that seed
     * an initial odometer without a known timestamp.
     */
    public BigDecimal getLastOdometer(String vehicleId) {
        return lastOdometer.get(vehicleId);
    }

    public void updateOdometer(String vehicleId, BigDecimal odometerKm) {
        if (vehicleId == null || odometerKm == null) {
            return;
        }
        odometerHistory.computeIfAbsent(
                vehicleId, ignored -> new TreeMap<>())
                .put(LocalDateTime.MIN, odometerKm);
        lastOdometer.put(vehicleId, odometerKm);
    }

    // ---------- Account balances ----------

    public BigDecimal getUnbilledAmount(String accountId) {
        return unbilledAmounts.getOrDefault(accountId, BigDecimal.ZERO);
    }

    public void addUnbilledAmount(String accountId, BigDecimal amount) {
        unbilledAmounts.merge(accountId, amount, BigDecimal::add);
    }

    public void subtractUnbilledAmount(String accountId, BigDecimal amount) {
        BigDecimal updated = nonNegative(
                getUnbilledAmount(accountId).subtract(amount));
        unbilledAmounts.put(accountId, updated);
    }

    /**
     * Returns the account's complete credit exposure: unbilled transactions
     * plus all currently unpaid invoice balances.
     */
    public BigDecimal getCreditExposure(String accountId) {
        BigDecimal invoiceExposure = invoices.stream()
                .filter(invoice -> invoice.getAccountId().equals(accountId))
                .map(InvoiceRecord::getOutstandingBalance)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return getUnbilledAmount(accountId).add(invoiceExposure);
    }

    /**
     * Applies the 25% credit-limit reduction when the two latest consecutive
     * invoices are both overdue and still have outstanding balances.
     */
    public void updateCreditLimit(FleetAccount account, LocalDate asOfDate) {
        if (account == null || asOfDate == null) {
            throw new IllegalArgumentException(
                    "Account and credit-limit date cannot be null");
        }

        String accountId = account.getAccountId();
        rememberOriginalCreditLimit(accountId, account.getCreditLimit());
        BigDecimal originalLimit = getOriginalCreditLimit(accountId);

        List<InvoiceRecord> accountInvoices = new ArrayList<>(
                getInvoicesForAccount(accountId));
        accountInvoices.sort(Comparator
                .comparing(InvoiceRecord::getInvoiceDate).reversed());

        int consecutiveOverdue = 0;
        for (InvoiceRecord invoice : accountInvoices) {
            boolean overdue = asOfDate.isAfter(invoice.getDueDate())
                    && invoice.getOutstandingBalance().signum() > 0;
            if (!overdue) {
                break;
            }
            consecutiveOverdue++;
        }

        BigDecimal adjustedLimit = consecutiveOverdue >= 2
                ? originalLimit.multiply(new BigDecimal("0.75"))
                    .setScale(2, java.math.RoundingMode.HALF_UP)
                : originalLimit;

        account.setCreditLimit(adjustedLimit);
    }

    // ---------- Outlet risk ledger ----------

    public BigDecimal getOutletRiskAmount(String outletId) {
        return outletRiskLedger.getOrDefault(outletId, BigDecimal.ZERO);
    }

    public void addOutletRiskAmount(String outletId, BigDecimal amount) {
        outletRiskLedger.merge(outletId, amount, BigDecimal::add);
    }

    // ---------- Declines and blocked attempts ----------

    public int recordDecline(String cardId, LocalDate date) {
        return dailyDeclines.merge(new CardDate(cardId, date), 1, Integer::sum);
    }

    public int getDailyDeclines(String cardId, LocalDate date) {
        return dailyDeclines.getOrDefault(new CardDate(cardId, date), 0);
    }

    public int recordBlockedAttempt(String cardId, LocalDate date) {
        return blockedAttempts.merge(new CardDate(cardId, date), 1, Integer::sum);
    }

    public int getBlockedAttempts(String cardId, LocalDate date) {
        return blockedAttempts.getOrDefault(new CardDate(cardId, date), 0);
    }

    // ---------- Duplicate transaction tracking ----------

    public boolean isTransactionProcessed(String transactionId) {
        return processedTransactionIds.contains(transactionId);
    }

    public boolean markTransactionProcessed(String transactionId) {
        return processedTransactionIds.add(transactionId);
    }

    // ---------- Approved sales and reversals ----------

    public void recordApprovedSale(
            com.t052.fuelbilling.model.Transaction transaction,
            String accountId,
            String vehicleId,
            BigDecimal engineAmount) {

        ApprovedSale sale = new ApprovedSale(
                transaction, accountId, vehicleId, engineAmount);
        approvedSales.put(transaction.getTransactionId(), sale);
    }

    public ApprovedSale getApprovedSale(String transactionId) {
        return approvedSales.get(transactionId);
    }

    public boolean isSaleAlreadyReversed(String transactionId) {
        return reversedSaleIds.contains(transactionId);
    }

    public boolean markSaleReversed(String transactionId) {
        return reversedSaleIds.add(transactionId);
    }

    public void addReversalCredit(ReversalCredit credit) {
        if (credit == null) {
            throw new IllegalArgumentException("Reversal credit cannot be null");
        }
        reversalCredits.add(credit);
    }

    public List<ReversalCredit> getReversalCredits() {
        return List.copyOf(reversalCredits);
    }

    public List<ApprovedSale> getApprovedSales() {
        return List.copyOf(approvedSales.values());
    }

    public void addInvoice(InvoiceRecord invoice) {
        if (invoice == null) {
            throw new IllegalArgumentException("Invoice cannot be null");
        }
        boolean duplicate = invoices.stream().anyMatch(existing ->
                existing.getInvoiceId().equals(invoice.getInvoiceId()));
        if (duplicate) {
            throw new IllegalArgumentException(
                    "Duplicate invoice: " + invoice.getInvoiceId());
        }
        invoices.add(invoice);
    }

    public List<InvoiceRecord> getInvoices() {
        return List.copyOf(invoices);
    }

    public List<InvoiceRecord> getInvoicesForAccount(String accountId) {
        return invoices.stream()
                .filter(invoice -> invoice.getAccountId().equals(accountId))
                .toList();
    }

    public void addInvoiceLine(InvoiceLine line) {
        if (line == null) {
            throw new IllegalArgumentException("Invoice line cannot be null");
        }
        invoiceLines.add(line);
    }

    public List<InvoiceLine> getInvoiceLines() {
        return List.copyOf(invoiceLines);
    }

    // ---------- Payments and account credits ----------

    public void addPaymentAllocation(PaymentAllocation allocation) {
        if (allocation == null) {
            throw new IllegalArgumentException(
                    "Payment allocation cannot be null");
        }
        paymentAllocations.add(allocation);
    }

    public List<PaymentAllocation> getPaymentAllocations() {
        return List.copyOf(paymentAllocations);
    }

    public BigDecimal getAccountCredit(String accountId) {
        return accountCredits.getOrDefault(accountId, BigDecimal.ZERO);
    }

    public void addAccountCredit(String accountId, BigDecimal amount) {
        if (accountId == null || accountId.isBlank()
                || amount == null || amount.signum() <= 0) {
            throw new IllegalArgumentException("Invalid account credit");
        }
        accountCredits.merge(accountId, amount, BigDecimal::add);
    }

    public BigDecimal consumeAccountCredit(
            String accountId, BigDecimal requestedAmount) {
        BigDecimal available = getAccountCredit(accountId);
        BigDecimal applied = available.min(requestedAmount);
        BigDecimal remaining = available.subtract(applied);

        if (remaining.signum() == 0) {
            accountCredits.remove(accountId);
        } else {
            accountCredits.put(accountId, remaining);
        }
        return applied;
    }

    public void rememberOriginalCreditLimit(
            String accountId, BigDecimal creditLimit) {
        originalCreditLimits.putIfAbsent(accountId, creditLimit);
    }

    public BigDecimal getOriginalCreditLimit(String accountId) {
        return originalCreditLimits.get(accountId);
    }

    // ---------- Decision log ----------

    public void addDecision(TransactionDecision decision) {
        if (decision == null) {
            throw new IllegalArgumentException(
                    "Transaction decision cannot be null");
        }
        decisions.add(decision);
    }

    public List<TransactionDecision> getDecisions() {
        return List.copyOf(decisions);
    }

    private BigDecimal nonNegative(BigDecimal value) {
        return value.compareTo(BigDecimal.ZERO) < 0
                ? BigDecimal.ZERO : value;
    }

    // ---------- Internal key and value classes ----------

    private record CardDate(String cardId, LocalDate date) {}
    private record VehicleMonth(String vehicleId, YearMonth month) {}
    private record AccountMonth(String accountId, YearMonth month) {}
    private record UsageEvent(
            LocalDateTime timestamp, int transactionCountDelta,
            BigDecimal litresDelta) {}
    private record LubeEvent(LocalDateTime timestamp, BigDecimal amountDelta) {}

    private static class DailyUsage {
        private int transactionCount = 0;
        private BigDecimal litres = BigDecimal.ZERO;
    }
}
