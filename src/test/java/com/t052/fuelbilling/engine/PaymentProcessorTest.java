package com.t052.fuelbilling.engine;

import com.t052.fuelbilling.model.FleetAccount;
import com.t052.fuelbilling.model.FuelCard;
import com.t052.fuelbilling.model.Outlet;
import com.t052.fuelbilling.model.Vehicle;
import com.t052.fuelbilling.model.enums.CardStatus;
import com.t052.fuelbilling.model.enums.FuelProduct;
import com.t052.fuelbilling.repository.LoadedData;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import static org.junit.Assert.*;

public class PaymentProcessorTest {

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    private EngineContext context() {
        FleetAccount account = new FleetAccount(
                "ACC-001", "Test Fleet", new BigDecimal("100000"),
                "FORTNIGHTLY", "SILVER", "NEFT",
                Set.of("KARNATAKA"), 5000, 1000);

        Vehicle vehicle = new Vehicle(
                "VEH-001", "ACC-001", "KA01AB1234",
                "DIESEL_TRUCK", new BigDecimal("300"));

        FuelCard card = new FuelCard(
                "CARD-001", "VEH-001",
                Set.of(FuelProduct.DIESEL), CardStatus.ACTIVE);

        Outlet outlet = new Outlet(
                "OUT-001", "Test Outlet", "Bengaluru",
                "KARNATAKA", "ACTIVE");

        return new EngineContext(new LoadedData(
                List.of(account), List.of(vehicle), List.of(card), List.of(outlet)));
    }

    private InvoiceRecord invoice(
            String id, LocalDate date, LocalDate dueDate, String amount) {
        return new InvoiceRecord(
                id, "ACC-001", date, date, date,
                "SILVER",
                new BigDecimal(amount), BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.ZERO, BigDecimal.ZERO,
                new BigDecimal(amount), dueDate);
    }

    private Path paymentFile(String rows) throws Exception {
        Path file = temporaryFolder.getRoot().toPath().resolve("payments.csv");
        Files.writeString(file,
                "payment_date,account_id,amount,mode,reference\n" + rows);
        return file;
    }

    @Test
    public void appliesPaymentToOldestInvoiceFirst() throws Exception {
        EngineState state = new EngineState();
        InvoiceRecord first = invoice(
                "INV-1", LocalDate.of(2026, 10, 16),
                LocalDate.of(2026, 10, 23), "100.00");
        InvoiceRecord second = invoice(
                "INV-2", LocalDate.of(2026, 11, 1),
                LocalDate.of(2026, 11, 8), "200.00");
        state.addInvoice(first);
        state.addInvoice(second);

        PaymentProcessor processor = new PaymentProcessor(context(), state);
        processor.applyPayments(paymentFile(
                "2026-11-02,ACC-001,150.00,NEFT,UTR-1\n"));

        assertEquals(0, new BigDecimal("0.00")
                .compareTo(first.getOutstandingBalance()));
        assertEquals(0, new BigDecimal("150.67")
                .compareTo(second.getOutstandingBalance()));
        assertEquals(3, state.getPaymentAllocations().size());
        PaymentAllocation interest = state.getPaymentAllocations().stream()
                .filter(a -> "INTEREST".equals(a.getAllocationType()))
                .findFirst().orElseThrow();
        assertEquals(0, new BigDecimal("0.67").compareTo(interest.getAmount()));
    }

    @Test
    public void preservesOverpaymentAsAccountCredit() throws Exception {
        EngineState state = new EngineState();
        state.addInvoice(invoice(
                "INV-1", LocalDate.of(2026, 10, 16),
                LocalDate.of(2026, 10, 23), "100.00"));

        PaymentProcessor processor = new PaymentProcessor(context(), state);
        processor.applyPayments(paymentFile(
                "2026-10-20,ACC-001,150.00,NEFT,UTR-2\n"));

        // The test invoice is fully paid; the extra Rs 50 is retained.
        assertEquals(0, new BigDecimal("50.00")
                .compareTo(state.getAccountCredit("ACC-001")));
    }

    @Test
    public void chargesOneDayOverdueInterestBeforeApplyingPayment()
            throws Exception {
        EngineState state = new EngineState();
        InvoiceRecord invoice = invoice(
                "INV-1", LocalDate.of(2026, 10, 16),
                LocalDate.of(2026, 10, 23), "100.00");
        state.addInvoice(invoice);

        PaymentProcessor processor = new PaymentProcessor(context(), state);
        processor.applyPayments(paymentFile(
                "2026-10-24,ACC-001,50.00,NEFT,UTR-3\n"));

        // Rs 100 x 2% / 30 = Rs 0.07 for one overdue day.
        assertEquals(0, new BigDecimal("50.07")
                .compareTo(invoice.getOutstandingBalance()));
        assertEquals(2, state.getPaymentAllocations().size());
        PaymentAllocation interest = state.getPaymentAllocations().stream()
                .filter(a -> "INTEREST".equals(a.getAllocationType()))
                .findFirst().orElseThrow();
        assertEquals(0, new BigDecimal("0.07").compareTo(interest.getAmount()));
    }

    @Test
    public void fullPaymentPaysEntireOutstandingBalance() throws Exception {
        EngineState state = new EngineState();
        InvoiceRecord first = invoice(
                "INV-1", LocalDate.of(2026, 10, 16),
                LocalDate.of(2026, 10, 23), "100.00");
        InvoiceRecord second = invoice(
                "INV-2", LocalDate.of(2026, 11, 1),
                LocalDate.of(2026, 11, 8), "200.00");
        state.addInvoice(first);
        state.addInvoice(second);

        PaymentProcessor processor = new PaymentProcessor(context(), state);
        processor.applyPayments(paymentFile(
                "2026-11-02,ACC-001,FULL,NEFT,UTR-FULL\n"));

        assertEquals(0, BigDecimal.ZERO.compareTo(
                first.getOutstandingBalance()));
        assertEquals(0, BigDecimal.ZERO.compareTo(
                second.getOutstandingBalance()));
    }
}
