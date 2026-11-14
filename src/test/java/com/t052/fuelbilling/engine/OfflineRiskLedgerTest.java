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

public class OfflineRiskLedgerTest {

    private EngineContext createContext() {
        FleetAccount account = new FleetAccount(
                "ACC-001", "Test Fleet", new BigDecimal("100000"),
                "FORTNIGHTLY", "GOLD", "CHEQUE",
                Set.of("KARNATAKA"), 5000, 20000);
        Vehicle vehicle = new Vehicle(
                "VEH-001", "ACC-001", "KA01AB1234",
                "TRUCK", new BigDecimal("200"));
        FuelCard card = new FuelCard(
                "CARD-001", "VEH-001",
                Set.of(FuelProduct.DIESEL, FuelProduct.LUBE),
                CardStatus.ACTIVE);
        Outlet outlet = new Outlet(
                "OUT-001", "Test Outlet", "Bangalore",
                "KARNATAKA", "ACTIVE");

        EngineContext context = new EngineContext(new LoadedData(
                List.of(account), List.of(vehicle), List.of(card), List.of(outlet)));
        context.addFuelPrices(List.of(new FuelPrice(
                LocalDate.of(2026, 11, 12), "Bangalore",
                FuelProduct.DIESEL, new BigDecimal("100.00"))));
        return context;
    }

    private Transaction offlineTransaction(String id, String litres) {
        LocalDateTime timestamp = LocalDateTime.of(2026, 11, 12, 10, 0);
        BigDecimal quantity = new BigDecimal(litres);
        return new Transaction(
                id, TransactionType.SALE, "", timestamp,
                timestamp.plusHours(1), "CARD-001", "OUT-001",
                "DIESEL", quantity, quantity.multiply(new BigDecimal("100.00")),
                new BigDecimal("1000"), true);
    }

    @Test
    public void offlineTankLimitViolationIsPostedAndChargedToOutletRisk() {
        EngineState state = new EngineState();
        TransactionDecision decision = new TransactionProcessor(
                createContext(), state).process(
                offlineTransaction("TXN-OFFLINE-TANK", "201"));

        assertEquals("APPROVED_FLAGGED", decision.getDecision());
        assertEquals("OFFLINE_RISK_POSTED", decision.getReasonCode());
        assertEquals(new BigDecimal("20100.00"),
                state.getOutletRiskAmount("OUT-001"));
        assertEquals(new BigDecimal("20100.00"),
                state.getUnbilledAmount("ACC-001"));
    }

    @Test
    public void offlineCreditLimitViolationIsPostedAndChargedToOutletRisk() {
        EngineState state = new EngineState();
        state.addUnbilledAmount("ACC-001", new BigDecimal("95000.00"));

        TransactionDecision decision = new TransactionProcessor(
                createContext(), state).process(
                offlineTransaction("TXN-OFFLINE-CREDIT", "100"));

        assertEquals("APPROVED_FLAGGED", decision.getDecision());
        assertEquals("OFFLINE_RISK_POSTED", decision.getReasonCode());
        assertEquals(new BigDecimal("10000.00"),
                state.getOutletRiskAmount("OUT-001"));
        assertEquals(new BigDecimal("105000.00"),
                state.getUnbilledAmount("ACC-001"));
    }

    @Test
    public void onlineTankLimitViolationStillDeclines() {
        EngineState state = new EngineState();
        LocalDateTime timestamp = LocalDateTime.of(2026, 11, 12, 10, 0);
        Transaction online = new Transaction(
                "TXN-ONLINE-TANK", TransactionType.SALE, "",
                timestamp, timestamp, "CARD-001", "OUT-001", "DIESEL",
                new BigDecimal("201"), new BigDecimal("20100"),
                new BigDecimal("1000"), false);

        TransactionDecision decision = new TransactionProcessor(
                createContext(), state).process(online);

        assertEquals("DECLINE", decision.getDecision());
        assertEquals("TANK_CAPACITY_EXCEEDED", decision.getReasonCode());
        assertEquals(BigDecimal.ZERO, state.getOutletRiskAmount("OUT-001"));
    }

    @Test
    public void offlineTransactionMoreThan48HoursLateIsRejected() {
        EngineState state = new EngineState();
        LocalDateTime timestamp = LocalDateTime.of(2026, 11, 12, 10, 0);
        Transaction late = new Transaction(
                "TXN-OFFLINE-LATE", TransactionType.SALE, "",
                timestamp, timestamp.plusHours(48).plusMinutes(1),
                "CARD-001", "OUT-001", "DIESEL",
                new BigDecimal("10"), new BigDecimal("1000"),
                new BigDecimal("1000"), true);

        TransactionDecision decision = new TransactionProcessor(
                createContext(), state).process(late);

        assertEquals("DECLINE", decision.getDecision());
        assertEquals("LATE_OFFLINE", decision.getReasonCode());
        assertEquals(BigDecimal.ZERO, state.getOutletRiskAmount("OUT-001"));
    }
}
