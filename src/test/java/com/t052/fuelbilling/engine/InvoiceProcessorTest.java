package com.t052.fuelbilling.engine;

import com.t052.fuelbilling.model.FleetAccount;
import com.t052.fuelbilling.model.FuelCard;
import com.t052.fuelbilling.model.Outlet;
import com.t052.fuelbilling.model.Transaction;
import com.t052.fuelbilling.model.Vehicle;
import com.t052.fuelbilling.model.enums.CardStatus;
import com.t052.fuelbilling.model.enums.FuelProduct;
import com.t052.fuelbilling.model.enums.TransactionType;
import com.t052.fuelbilling.repository.LoadedData;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import static org.junit.Assert.*;

public class InvoiceProcessorTest {

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    private EngineContext context(int previousMonthLitres, String paymentMode) {
        FleetAccount account = new FleetAccount(
                "ACC-001", "Test Fleet", new BigDecimal("100000"),
                "FORTNIGHTLY", "SILVER", paymentMode,
                Set.of("KARNATAKA"), 50000, previousMonthLitres);

        Vehicle vehicle = new Vehicle(
                "VEH-001", "ACC-001", "KA01AB1234",
                "DIESEL_TRUCK", new BigDecimal("300"));

        FuelCard card = new FuelCard(
                "CARD-001", "VEH-001",
                Set.of(FuelProduct.DIESEL, FuelProduct.LUBE),
                CardStatus.ACTIVE);

        Outlet outlet = new Outlet(
                "OUT-001", "Test Outlet", "Bengaluru",
                "KARNATAKA", "ACTIVE");

        return new EngineContext(new LoadedData(
                List.of(account), List.of(vehicle), List.of(card), List.of(outlet)));
    }

    private Transaction sale(String id, LocalDateTime time,
                             String product, String litres, String pumpAmount) {
        return new Transaction(
                id, TransactionType.SALE, null, time, time,
                "CARD-001", "OUT-001", product,
                new BigDecimal(litres), new BigDecimal(pumpAmount),
                new BigDecimal("1000"), false);
    }

    @Test
    public void createsOctoberFirstFortnightInvoiceWithGoldTier() throws Exception {
        EngineContext context = context(20000, "NEFT");
        EngineState state = new EngineState();

        Transaction diesel = sale(
                "TXN-1", LocalDateTime.of(2026, 10, 2, 10, 0),
                "DIESEL", "10", "1000.00");

        state.recordApprovedSale(
                diesel, "ACC-001", "VEH-001", new BigDecimal("1000.00"));
        state.addUnbilledAmount("ACC-001", new BigDecimal("1000.00"));

        InvoiceProcessor processor = new InvoiceProcessor(
                context, state, temporaryFolder.getRoot().toPath());

        List<InvoiceRecord> invoices =
                processor.generate(LocalDate.of(2026, 10, 16));

        assertEquals(1, invoices.size());
        InvoiceRecord invoice = invoices.get(0);

        assertEquals("GOLD", invoice.getTier());
        assertEquals(LocalDate.of(2026, 10, 1), invoice.getPeriodFrom());
        assertEquals(LocalDate.of(2026, 10, 15), invoice.getPeriodTo());
        assertEquals(LocalDate.of(2026, 10, 23), invoice.getDueDate());
        assertEquals(0, BigDecimal.ZERO.compareTo(
                state.getUnbilledAmount("ACC-001")));
        assertEquals(0, invoice.getOutstandingBalance().compareTo(
                state.getCreditExposure("ACC-001")));
        assertEquals(0, new BigDecimal("8.00")
                .compareTo(invoice.getDiscountCredit()));
        assertTrue(Files.exists(
                temporaryFolder.getRoot().toPath().resolve("invoices.csv")));
        assertTrue(Files.exists(
                temporaryFolder.getRoot().toPath().resolve("invoice_lines.csv")));
    }

    @Test
    public void calculatesLubeGstAndChequeConvenienceFee() throws Exception {
        EngineContext context = context(1000, "CHEQUE");
        EngineState state = new EngineState();

        Transaction lube = sale(
                "TXN-LUBE", LocalDateTime.of(2026, 10, 4, 11, 0),
                "LUBE", "4", "1400.00");

        state.recordApprovedSale(
                lube, "ACC-001", "VEH-001", new BigDecimal("1400.00"));

        InvoiceProcessor processor = new InvoiceProcessor(
                context, state, temporaryFolder.getRoot().toPath());

        InvoiceRecord invoice =
                processor.generate(LocalDate.of(2026, 10, 16)).get(0);

        assertEquals(0, new BigDecimal("1400.00")
                .compareTo(invoice.getLubeAmount()));
        assertEquals(0, new BigDecimal("252.00")
                .compareTo(invoice.getLubeGst()));
        assertEquals(0, new BigDecimal("8.26")
                .compareTo(invoice.getConvenienceFee()));
    }

    @Test
    public void recalculatesNovemberTierFromOctoberDieselLitres() throws Exception {
        EngineContext context = context(20000, "NEFT");
        EngineState state = new EngineState();

        Transaction diesel = sale(
                "TXN-OCT", LocalDateTime.of(2026, 10, 20, 10, 0),
                "DIESEL", "20000", "1800000.00");

        state.recordApprovedSale(
                diesel, "ACC-001", "VEH-001", new BigDecimal("1800000.00"));
        state.addDecision(new TransactionDecision(
                "TXN-OCT", "CARD-001", "ACC-001",
                "2026-10-20T10:00", "DIESEL",
                new BigDecimal("20000"), new BigDecimal("1800000.00"),
                "APPROVE", "APPROVED", false, false, false));

        InvoiceProcessor processor = new InvoiceProcessor(
                context, state, temporaryFolder.getRoot().toPath());

        InvoiceRecord invoice =
                processor.generate(LocalDate.of(2026, 11, 1)).get(0);

        assertEquals("GOLD", invoice.getTier());
        assertEquals(LocalDate.of(2026, 10, 16), invoice.getPeriodFrom());
        assertEquals(LocalDate.of(2026, 10, 31), invoice.getPeriodTo());
    }

    @Test
    public void rejectsNonFortnightlyInvoiceDate() throws Exception {
        InvoiceProcessor processor = new InvoiceProcessor(
                context(0, "NEFT"),
                new EngineState(),
                temporaryFolder.getRoot().toPath());

        try {
            processor.generate(LocalDate.of(2026, 10, 15));
            fail("Expected invalid invoice date");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("1st or 16th"));
        }
    }
}
