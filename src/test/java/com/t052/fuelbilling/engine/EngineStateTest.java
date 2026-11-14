
package com.t052.fuelbilling.engine;

import org.junit.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Set;

import com.t052.fuelbilling.model.FleetAccount;

import static org.junit.Assert.*;

public class EngineStateTest {

    @Test
    public void tracksDailyTransactionUsage() {
        EngineState state = new EngineState();
        LocalDate date = LocalDate.of(2026, 11, 12);

        state.recordDailyUsage(
                "CARD-001", date, new BigDecimal("50"));

        state.recordDailyUsage(
                "CARD-001", date, new BigDecimal("70"));

        assertEquals(2,
                state.getDailyTransactionCount("CARD-001", date));

        assertEquals(0,
                new BigDecimal("120").compareTo(
                        state.getDailyLitres("CARD-001", date)));
    }

    @Test
    public void tracksMonthlyVehicleLitres() {
        EngineState state = new EngineState();
        LocalDate date = LocalDate.of(2026, 11, 12);

        state.recordMonthlyLitres(
                "VEH-001", date, new BigDecimal("100"));

        assertEquals(0,
                new BigDecimal("100").compareTo(
                        state.getMonthlyLitres("VEH-001", date)));
    }

    @Test
    public void tracksUnbilledAccountAmount() {
        EngineState state = new EngineState();

        state.addUnbilledAmount(
                "ACC-001", new BigDecimal("5000"));

        state.addUnbilledAmount(
                "ACC-001", new BigDecimal("2000"));

        assertEquals(0,
                new BigDecimal("7000").compareTo(
                        state.getUnbilledAmount("ACC-001")));
    }

    @Test
    public void tracksOdometerReading() {
        EngineState state = new EngineState();

        state.updateOdometer(
                "VEH-001", new BigDecimal("15000"));

        assertEquals(0,
                new BigDecimal("15000").compareTo(
                        state.getLastOdometer("VEH-001")));
    }

    @Test
    public void detectsDuplicateTransactionIds() {
        EngineState state = new EngineState();

        assertTrue(state.markTransactionProcessed("TXN-001"));
        assertFalse(state.markTransactionProcessed("TXN-001"));
    }

    @Test
    public void tracksDailyDeclines() {
        EngineState state = new EngineState();
        LocalDate date = LocalDate.of(2026, 11, 12);

        assertEquals(1, state.recordDecline("CARD-001", date));
        assertEquals(2, state.recordDecline("CARD-001", date));
        assertEquals(2, state.getDailyDeclines("CARD-001", date));
    }
    @Test
    public void findsPreviousOdometerByTransactionTimeNotPostingOrder() {
        EngineState state = new EngineState();
        LocalDateTime first = LocalDateTime.of(2026, 10, 1, 8, 0);
        LocalDateTime second = LocalDateTime.of(2026, 10, 3, 8, 0);
        LocalDateTime latePostedEarlierEvent =
                LocalDateTime.of(2026, 10, 2, 8, 0);

        // The Oct 3 transaction was processed first because it was posted first.
        state.updateOdometer("VEH-001", second, new BigDecimal("1200"));
        state.updateOdometer("VEH-001", first, new BigDecimal("1000"));

        assertEquals(0, new BigDecimal("1000").compareTo(
                state.getPreviousOdometer("VEH-001", latePostedEarlierEvent)));
        assertEquals(0, new BigDecimal("1200").compareTo(
                state.getPreviousOdometer("VEH-001", LocalDateTime.of(2026, 10, 4, 8, 0))));
    }

    @Test
    public void legacyOdometerSeedIsUsedAsInitialReading() {
        EngineState state = new EngineState();
        state.updateOdometer("VEH-001", new BigDecimal("15000"));

        assertEquals(0, new BigDecimal("15000").compareTo(
                state.getPreviousOdometer(
                        "VEH-001", LocalDateTime.of(2026, 11, 12, 10, 0))));
    }


    @Test
    public void timestampedUsageExcludesTransactionsFromTheFuture() {
        EngineState state = new EngineState();
        LocalDateTime morning = LocalDateTime.of(2026, 10, 10, 8, 0);
        LocalDateTime evening = LocalDateTime.of(2026, 10, 10, 18, 0);

        state.recordDailyUsage("CARD-001", morning, new BigDecimal("40"));
        state.recordDailyUsage("CARD-001", evening, new BigDecimal("60"));
        state.recordMonthlyLitres("VEH-001", morning, new BigDecimal("40"));
        state.recordMonthlyLitres("VEH-001", evening, new BigDecimal("60"));

        LocalDateTime beforeEvening = LocalDateTime.of(2026, 10, 10, 12, 0);
        assertEquals(1, state.getDailyTransactionCount("CARD-001", beforeEvening));
        assertEquals(0, new BigDecimal("40").compareTo(
                state.getDailyLitres("CARD-001", beforeEvening)));
        assertEquals(0, new BigDecimal("40").compareTo(
                state.getMonthlyLitres("VEH-001", beforeEvening)));
    }

    @Test
    public void creditExposureIncludesUnpaidInvoicesAndUnbilledUsage() {
        EngineState state = new EngineState();
        state.addUnbilledAmount("ACC-001", new BigDecimal("500.00"));

        InvoiceRecord invoice = new InvoiceRecord(
                "INV-ACC-001-20261016", "ACC-001",
                LocalDate.of(2026, 10, 16),
                LocalDate.of(2026, 10, 1),
                LocalDate.of(2026, 10, 15),
                "SILVER",
                new BigDecimal("1000.00"),
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.ZERO, new BigDecimal("1000.00"),
                LocalDate.of(2026, 10, 23));
        state.addInvoice(invoice);

        assertEquals(0, new BigDecimal("1500.00").compareTo(
                state.getCreditExposure("ACC-001")));

        invoice.applyPayment(new BigDecimal("250.00"));
        assertEquals(0, new BigDecimal("1250.00").compareTo(
                state.getCreditExposure("ACC-001")));
    }

    @Test
    public void creditLimitReducesAfterTwoConsecutiveOverdueInvoices() {
        EngineState state = new EngineState();
        FleetAccount account = new FleetAccount(
                "ACC-001", "Test Account", new BigDecimal("10000.00"),
                "FORTNIGHTLY", "SILVER", "NEFT", Set.of("Karnataka"),
                1000, 0);
        state.rememberOriginalCreditLimit(
                account.getAccountId(), account.getCreditLimit());

        state.addInvoice(testInvoice(
                "INV-1", LocalDate.of(2026, 10, 1),
                LocalDate.of(2026, 10, 8), new BigDecimal("1000.00")));
        state.addInvoice(testInvoice(
                "INV-2", LocalDate.of(2026, 10, 16),
                LocalDate.of(2026, 10, 23), new BigDecimal("1000.00")));

        state.updateCreditLimit(account, LocalDate.of(2026, 10, 24));
        assertEquals(0, new BigDecimal("7500.00").compareTo(
                account.getCreditLimit()));

        state.getInvoicesForAccount("ACC-001").get(0)
                .applyPayment(new BigDecimal("1000.00"));
        state.updateCreditLimit(account, LocalDate.of(2026, 10, 24));
        assertEquals(0, new BigDecimal("10000.00").compareTo(
                account.getCreditLimit()));
    }

    private InvoiceRecord testInvoice(
            String id, LocalDate invoiceDate, LocalDate dueDate,
            BigDecimal totalDue) {
        return new InvoiceRecord(
                id, "ACC-001", invoiceDate,
                invoiceDate.withDayOfMonth(1), invoiceDate,
                "SILVER", totalDue, BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.ZERO, BigDecimal.ZERO, totalDue, dueDate);
    }

    @Test
    public void timestampedLubeSpendExcludesTransactionsFromTheFuture() {
        EngineState state = new EngineState();
        LocalDateTime morning = LocalDateTime.of(2026, 10, 10, 8, 0);
        LocalDateTime evening = LocalDateTime.of(2026, 10, 10, 18, 0);

        state.recordLubeSpend("ACC-001", morning, new BigDecimal("1200.00"));
        state.recordLubeSpend("ACC-001", evening, new BigDecimal("900.00"));

        assertEquals(0, new BigDecimal("1200.00").compareTo(
                state.getMonthlyLubeSpend(
                        "ACC-001", LocalDateTime.of(2026, 10, 10, 12, 0))));
    }
}
