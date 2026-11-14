package com.t052.fuelbilling.engine;

import com.t052.fuelbilling.model.*;
import com.t052.fuelbilling.model.enums.*;
import com.t052.fuelbilling.repository.LoadedData;

import org.junit.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import static org.junit.Assert.*;

public class ReversalProcessorTest {

    private EngineContext createContext() {
        FleetAccount account = new FleetAccount(
                "ACC-001", "Test Fleet", new BigDecimal("100000"),
                "FORTNIGHTLY", "GOLD", "CHEQUE",
                Set.of("KARNATAKA"), 5000, 20000);

        Vehicle vehicle = new Vehicle(
                "VEH-001", "ACC-001", "KA01AB1234", "DIESEL_TRUCK",
                new BigDecimal("200"));

        FuelCard card = new FuelCard(
                "CARD-001", "VEH-001",
                Set.of(FuelProduct.DIESEL), CardStatus.ACTIVE);

        Outlet outlet = new Outlet(
                "OUT-001", "Test Outlet", "Bangalore", "KARNATAKA", "ACTIVE");

        return new EngineContext(new LoadedData(
                List.of(account), List.of(vehicle), List.of(card), List.of(outlet)));
    }

    private Transaction sale(String id, LocalDateTime time) {
        return new Transaction(
                id, TransactionType.SALE, "", time, time.plusMinutes(2),
                "CARD-001", "OUT-001", "DIESEL",
                new BigDecimal("50"), new BigDecimal("5000.00"),
                new BigDecimal("10000"), false);
    }

    private Transaction reversal(
            String id, String reference, LocalDateTime time) {
        return new Transaction(
                id, TransactionType.REVERSAL, reference, time, time,
                "CARD-001", "OUT-001", "DIESEL",
                new BigDecimal("50"), new BigDecimal("5000.00"),
                new BigDecimal("10000"), false);
    }

    private EngineState stateWithApprovedSale(
            EngineState state, Transaction original) {
        state.recordDailyUsage(
                "CARD-001", original.getTransactionTimestamp().toLocalDate(),
                original.getLitres());
        state.recordMonthlyLitres(
                "VEH-001", original.getTransactionTimestamp().toLocalDate(),
                original.getLitres());
        state.addUnbilledAmount("ACC-001", new BigDecimal("5000.00"));
        state.recordApprovedSale(
                original, "ACC-001", "VEH-001", new BigDecimal("5000.00"));
        return state;
    }

    @Test
    public void timelyReversalRestoresDailyMonthlyAndCreditLimits() {
        EngineState state = new EngineState();
        LocalDateTime saleTime = LocalDateTime.of(2026, 11, 12, 10, 0);
        Transaction original = sale("TXN-001", saleTime);
        stateWithApprovedSale(state, original);

        ReversalProcessor processor = new ReversalProcessor(createContext(), state);
        TransactionDecision result = processor.process(
                reversal("TXN-002", "TXN-001", saleTime.plusHours(24)));

        assertEquals("APPROVE", result.getDecision());
        assertEquals("REVERSAL_APPROVED", result.getReasonCode());
        assertEquals(0, state.getDailyTransactionCount(
                "CARD-001", saleTime.toLocalDate()));
        assertEquals(0, state.getMonthlyLitres(
                "VEH-001", saleTime.toLocalDate()).compareTo(BigDecimal.ZERO));
        assertEquals(0, state.getUnbilledAmount("ACC-001")
                .compareTo(BigDecimal.ZERO));
        assertTrue(state.getReversalCredits().get(0).isTimely());
    }

    @Test
    public void reversalOneSecondAfter24HoursDoesNotRestoreUsage() {
        EngineState state = new EngineState();
        LocalDateTime saleTime = LocalDateTime.of(2026, 11, 12, 10, 0);
        Transaction original = sale("TXN-011", saleTime);
        stateWithApprovedSale(state, original);

        ReversalProcessor processor = new ReversalProcessor(createContext(), state);
        TransactionDecision result = processor.process(
                reversal("TXN-012", "TXN-011", saleTime.plusHours(24).plusSeconds(1)));

        assertEquals("APPROVE", result.getDecision());
        assertEquals("LATE_REVERSAL_CREDIT", result.getReasonCode());
        assertEquals(1, state.getDailyTransactionCount(
                "CARD-001", saleTime.toLocalDate()));
        assertEquals(0, state.getMonthlyLitres(
                "VEH-001", saleTime.toLocalDate())
                .compareTo(new BigDecimal("50")));
        assertEquals(0, state.getUnbilledAmount("ACC-001")
                .compareTo(new BigDecimal("5000.00")));
        assertFalse(state.getReversalCredits().get(0).isTimely());
    }

    @Test
    public void rejectsReversalWhenOriginalSaleWasNotApproved() {
        EngineState state = new EngineState();
        ReversalProcessor processor = new ReversalProcessor(createContext(), state);

        TransactionDecision result = processor.process(
                reversal("TXN-020", "TXN-NOT-APPROVED",
                        LocalDateTime.of(2026, 11, 12, 12, 0)));

        assertEquals("DECLINE", result.getDecision());
        assertEquals("INVALID_REVERSAL_REFERENCE", result.getReasonCode());
        assertTrue(state.getReversalCredits().isEmpty());
    }

    @Test
    public void preventsOriginalSaleFromBeingReversedTwice() {
        EngineState state = new EngineState();
        LocalDateTime saleTime = LocalDateTime.of(2026, 11, 12, 10, 0);
        Transaction original = sale("TXN-030", saleTime);
        stateWithApprovedSale(state, original);

        ReversalProcessor processor = new ReversalProcessor(createContext(), state);
        processor.process(reversal("TXN-031", "TXN-030", saleTime.plusHours(1)));
        TransactionDecision second = processor.process(
                reversal("TXN-032", "TXN-030", saleTime.plusHours(2)));

        assertEquals("DECLINE", second.getDecision());
        assertEquals("ALREADY_REVERSED", second.getReasonCode());
        assertEquals(1, state.getReversalCredits().size());
    }

    @Test
    public void rejectsReversalWhoseDetailsDoNotMatchOriginalSale() {
        EngineState state = new EngineState();
        LocalDateTime saleTime = LocalDateTime.of(2026, 11, 12, 10, 0);
        Transaction original = sale("TXN-040", saleTime);
        stateWithApprovedSale(state, original);

        Transaction mismatched = new Transaction(
                "TXN-041", TransactionType.REVERSAL, "TXN-040",
                saleTime.plusHours(1), saleTime.plusHours(1),
                "CARD-001", "OUT-001", "DIESEL",
                new BigDecimal("40"), new BigDecimal("4000.00"),
                new BigDecimal("10000"), false);

        TransactionDecision result = new ReversalProcessor(
                createContext(), state).process(mismatched);

        assertEquals("DECLINE", result.getDecision());
        assertEquals("REVERSAL_DETAILS_MISMATCH", result.getReasonCode());
    }
}
