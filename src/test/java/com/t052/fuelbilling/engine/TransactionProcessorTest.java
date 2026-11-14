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

public class TransactionProcessorTest {

    private EngineContext createContext() {

        FleetAccount account = new FleetAccount(

                "ACC-001",

                "Test Fleet",

                new BigDecimal("100000"),

                "FORTNIGHTLY",

                "GOLD",

                "CHEQUE",

                Set.of("KARNATAKA"),

                5000,

                20000

        );

        Vehicle vehicle = new Vehicle(

                "VEH-001",

                "ACC-001",

                "KA01AB1234",

                "TRUCK",

                new BigDecimal("200")

        );

        FuelCard card = new FuelCard(

                "CARD-001",

                "VEH-001",

                Set.of(FuelProduct.DIESEL, FuelProduct.LUBE),

                CardStatus.ACTIVE

        );

        Outlet outlet = new Outlet(

                "OUT-001",

                "Test Outlet",

                "Bangalore",

                "KARNATAKA",

                "ACTIVE"

        );

        LoadedData data = new LoadedData(

                List.of(account),

                List.of(vehicle),

                List.of(card),

                List.of(outlet)

        );

        EngineContext context = new EngineContext(data);

        context.addFuelPrices(List.of(

                new FuelPrice(

                        LocalDate.of(2026, 11, 12),

                        "Bangalore",

                        FuelProduct.DIESEL,

                        new BigDecimal("100.00")

                ),

                new FuelPrice(

                        LocalDate.of(2026, 11, 12),

                        "Bangalore",

                        FuelProduct.LUBE,

                        new BigDecimal("100.00")

                )

        ));

        return context;

    }

    private Transaction createTransaction(

            String id,

            String product,

            String litres,

            String odometer) {

        LocalDateTime timestamp =

                LocalDateTime.of(2026, 11, 12, 10, 0);

        BigDecimal litreAmount = new BigDecimal(litres);

        BigDecimal pumpAmount = litreAmount.multiply(

                new BigDecimal("100.00"));

        return new Transaction(

                id,

                TransactionType.SALE,

                "",

                timestamp,

                timestamp,

                "CARD-001",

                "OUT-001",

                product,

                litreAmount,

                pumpAmount,

                new BigDecimal(odometer),

                false

        );

    }

    @Test

    public void rejectsLubeWhenOdometerDoesNotIncrease() {

        EngineState state = new EngineState();
        state.updateOdometer("VEH-001", new BigDecimal("1000"));
        TransactionProcessor processor =
                new TransactionProcessor(createContext(), state);

        TransactionDecision decision = processor.process(
                createTransaction("TXN-LUBE-ODO", "LUBE", "5", "1000"));

        assertEquals("DECLINE", decision.getDecision());
        assertEquals("ODOMETER_NOT_INCREASING", decision.getReasonCode());
    }

    @Test
    public void lubeCapTakesPriorityWhenOdometerIsAlsoInvalid() {
        EngineState state = new EngineState();
        state.updateOdometer("VEH-001", new BigDecimal("1000"));
        state.recordLubeSpend(
                "ACC-001",
                LocalDate.of(2026, 11, 12),
                new BigDecimal("1500"));

        TransactionProcessor processor =
                new TransactionProcessor(createContext(), state);

        TransactionDecision decision = processor.process(
                createTransaction("TXN-LUBE-CAP-PRIORITY", "LUBE", "10", "1000"));

        assertEquals("DECLINE", decision.getDecision());
        assertEquals("LUBE_CAP", decision.getReasonCode());
    }

    @Test

    public void flagsLubePumpAmountVarianceAndUsesEngineAmount() {

        EngineState state = new EngineState();
        TransactionProcessor processor =
                new TransactionProcessor(createContext(), state);
        Transaction transaction = new Transaction(
                "TXN-LUBE-VARIANCE", TransactionType.SALE, "",
                LocalDateTime.of(2026, 11, 12, 10, 0),
                LocalDateTime.of(2026, 11, 12, 10, 0),
                "CARD-001", "OUT-001", "LUBE",
                new BigDecimal("10"), new BigDecimal("1005"),
                new BigDecimal("1000"), false);

        TransactionDecision decision = processor.process(transaction);

        assertEquals("APPROVED_FLAGGED", decision.getDecision());
        assertEquals("PUMP_AMOUNT_VARIANCE", decision.getReasonCode());
        assertTrue(decision.isVarianceFlag());
        assertEquals(new BigDecimal("1000.00"), decision.getEngineAmount());
    }

    @Test

    public void approvedLubeUpdatesVehicleOdometerHistory() {

        EngineState state = new EngineState();
        TransactionProcessor processor =
                new TransactionProcessor(createContext(), state);

        TransactionDecision decision = processor.process(
                createTransaction("TXN-LUBE-UPDATE", "LUBE", "5", "1200"));

        assertEquals("APPROVE", decision.getDecision());
        assertEquals(new BigDecimal("1200"),
                state.getPreviousOdometer("VEH-001",
                        LocalDateTime.of(2026, 11, 12, 10, 1)));
    }

    @Test

    public void rejectsProductNotAllowedForCard() {

        TransactionProcessor processor =

                new TransactionProcessor(

                        createContext(), new EngineState());

        TransactionDecision decision = processor.process(

                createTransaction(

                        "TXN-001", "PETROL", "50", "1000"));

        assertEquals("DECLINE", decision.getDecision());

        assertEquals(

                "PRODUCT_NOT_ALLOWED",

                decision.getReasonCode());

    }

    @Test

    public void rejectsTransactionAboveTankCapacity() {

        TransactionProcessor processor =

                new TransactionProcessor(

                        createContext(), new EngineState());

        TransactionDecision decision = processor.process(

                createTransaction(

                        "TXN-002", "DIESEL", "201", "1000"));

        assertEquals("DECLINE", decision.getDecision());

        assertEquals(

                "TANK_CAPACITY_EXCEEDED",

                decision.getReasonCode());

    }

    @Test

    public void rejectsNonIncreasingOdometer() {

        EngineState state = new EngineState();

        state.updateOdometer(

                "VEH-001", new BigDecimal("1000"));

        TransactionProcessor processor =

                new TransactionProcessor(createContext(), state);

        TransactionDecision decision = processor.process(

                createTransaction(

                        "TXN-003", "DIESEL", "50", "1000"));

        assertEquals("DECLINE", decision.getDecision());

        assertEquals(

                "ODOMETER_NOT_INCREASING",

                decision.getReasonCode());

    }

    @Test

    public void approvesAndFlagsMileageOutsideAllowedRange() {

        EngineState state = new EngineState();

        state.updateOdometer(

                "VEH-001", new BigDecimal("1000"));

        TransactionProcessor processor =

                new TransactionProcessor(createContext(), state);

        TransactionDecision decision = processor.process(

                createTransaction(

                        "TXN-004", "DIESEL", "2", "1100"));

        assertEquals(

                "APPROVED_FLAGGED",

                decision.getDecision());

        assertEquals(

                "MILEAGE_OUT_OF_RANGE",

                decision.getReasonCode());

        assertTrue(decision.isMileageFlag());

        assertEquals(

                new BigDecimal("200.00"),

                decision.getEngineAmount());

        assertEquals(

                new BigDecimal("2"),

                state.getMonthlyLitres(

                        "VEH-001",

                        LocalDate.of(2026, 11, 12)));

    }

    @Test

    public void approvesValidTransactionAtEnginePrice() {

        EngineState state = new EngineState();

        TransactionProcessor processor =

                new TransactionProcessor(createContext(), state);

        Transaction transaction = createTransaction(

                "TXN-005", "DIESEL", "50", "1000");

        TransactionDecision decision =

                processor.process(transaction);

        assertEquals("APPROVE", decision.getDecision());

        assertEquals("APPROVED", decision.getReasonCode());

        assertEquals(

                new BigDecimal("5000.00"),

                decision.getEngineAmount());

        assertFalse(decision.isMileageFlag());

        assertFalse(decision.isVarianceFlag());

        assertEquals(

                new BigDecimal("5000.00"),

                state.getUnbilledAmount("ACC-001"));

    }

    @Test

    public void flagsPumpAmountVarianceAboveOneRupee() {

        EngineState state = new EngineState();

        TransactionProcessor processor =

                new TransactionProcessor(createContext(), state);

        Transaction transaction = new Transaction(

                "TXN-006",

                TransactionType.SALE,

                "",

                LocalDateTime.of(2026, 11, 12, 10, 0),

                LocalDateTime.of(2026, 11, 12, 10, 0),

                "CARD-001",

                "OUT-001",

                "DIESEL",

                new BigDecimal("10"),

                new BigDecimal("1005"),

                new BigDecimal("1000"),

                false

        );

        TransactionDecision decision =

                processor.process(transaction);

        assertEquals("APPROVE", decision.getDecision());

        assertTrue(decision.isVarianceFlag());

        assertEquals(

                "PUMP_AMOUNT_VARIANCE",

                decision.getReasonCode());

        assertEquals(

                new BigDecimal("1000.00"),

                decision.getEngineAmount());

    }

    @Test

    public void rejectsTransactionOutsideAllowedState() {

        FleetAccount account = new FleetAccount(

                "ACC-001",

                "Test Fleet",

                new BigDecimal("100000"),

                "FORTNIGHTLY",

                "GOLD",

                "CHEQUE",

                Set.of("KARNATAKA"),

                5000,

                20000

        );

        Vehicle vehicle = new Vehicle(

                "VEH-001",

                "ACC-001",

                "KA01AB1234",

                "TRUCK",

                new BigDecimal("200")

        );

        FuelCard card = new FuelCard(

                "CARD-001",

                "VEH-001",

                Set.of(FuelProduct.DIESEL),

                CardStatus.ACTIVE

        );

        Outlet outlet = new Outlet(

                "OUT-002",

                "Other State Outlet",

                "Chennai",

                "TAMIL NADU",

                "ACTIVE"

        );

        EngineContext context = new EngineContext(

                new LoadedData(

                        List.of(account),

                        List.of(vehicle),

                        List.of(card),

                        List.of(outlet)

                )

        );

        TransactionProcessor processor =

                new TransactionProcessor(context, new EngineState());

        LocalDateTime timestamp =

                LocalDateTime.of(2026, 11, 12, 10, 0);

        Transaction transaction = new Transaction(

                "TXN-007",

                TransactionType.SALE,

                "",

                timestamp,

                timestamp,

                "CARD-001",

                "OUT-002",

                "DIESEL",

                new BigDecimal("50"),

                new BigDecimal("5000"),

                new BigDecimal("1000"),

                false

        );

        TransactionDecision decision =

                processor.process(transaction);

        assertEquals("DECLINE", decision.getDecision());

        assertEquals(

                "OUTSIDE_ALLOWED_STATE",

                decision.getReasonCode());

    }

    @Test

    public void retainsAccountIdWhenBlockedCardTransactionIsDeclined() {

        EngineContext context = createContext();

        context.findCard("CARD-001").orElseThrow()

                .setStatus(CardStatus.BLOCKED);

        TransactionProcessor processor =

                new TransactionProcessor(context, new EngineState());

        TransactionDecision decision = processor.process(

                createTransaction(

                        "TXN-BLOCKED-ACCOUNT", "DIESEL", "10", "1000"));

        assertEquals("DECLINE", decision.getDecision());

        assertEquals("CARD_BLOCKED", decision.getReasonCode());

        assertEquals("ACC-001", decision.getAccountId());

    }

    @Test

    public void retainsAccountIdWhenLostCardTransactionIsDeclined() {

        EngineContext context = createContext();

        context.findCard("CARD-001").orElseThrow()

                .setStatus(CardStatus.LOST);

        TransactionProcessor processor =

                new TransactionProcessor(context, new EngineState());

        TransactionDecision decision = processor.process(

                createTransaction(

                        "TXN-LOST-ACCOUNT", "DIESEL", "10", "1000"));

        assertEquals("DECLINE", decision.getDecision());

        assertEquals("CARD_LOST", decision.getReasonCode());

        assertEquals("ACC-001", decision.getAccountId());

    }

    @Test

    public void offlineTankLimitDoesNotOverrideInvalidOdometer() {

        EngineState state = new EngineState();

        TransactionProcessor processor =

                new TransactionProcessor(createContext(), state);

        LocalDateTime time = LocalDateTime.of(2026, 11, 12, 10, 0);

        Transaction offline = new Transaction(

                "TXN-OFFLINE-INVALID-ODOMETER",

                TransactionType.SALE, "", time, time.plusMinutes(10),

                "CARD-001", "OUT-001", "DIESEL",

                new BigDecimal("201"), new BigDecimal("20100"),

                new BigDecimal("-1"), true);

        TransactionDecision decision = processor.process(offline);

        assertEquals("DECLINE", decision.getDecision());

        assertEquals("INVALID_ODOMETER", decision.getReasonCode());

        assertEquals(BigDecimal.ZERO, state.getOutletRiskAmount("OUT-001"));

        assertEquals(BigDecimal.ZERO, state.getUnbilledAmount("ACC-001"));

    }

    @Test

    public void offlineTankLimitDoesNotOverrideInvalidPumpAmount() {

        EngineState state = new EngineState();

        TransactionProcessor processor =

                new TransactionProcessor(createContext(), state);

        LocalDateTime time = LocalDateTime.of(2026, 11, 12, 10, 0);

        Transaction offline = new Transaction(

                "TXN-OFFLINE-INVALID-PUMP",

                TransactionType.SALE, "", time, time.plusMinutes(10),

                "CARD-001", "OUT-001", "DIESEL",

                new BigDecimal("201"), new BigDecimal("-1"),

                new BigDecimal("1000"), true);

        TransactionDecision decision = processor.process(offline);

        assertEquals("DECLINE", decision.getDecision());

        assertEquals("INVALID_PUMP_AMOUNT", decision.getReasonCode());

        assertEquals(BigDecimal.ZERO, state.getOutletRiskAmount("OUT-001"));

        assertEquals(BigDecimal.ZERO, state.getUnbilledAmount("ACC-001"));

    }

    @Test

    public void offlineTransactionOlderThan48HoursIsStillDeclined() {

        EngineState state = new EngineState();

        TransactionProcessor processor =

                new TransactionProcessor(createContext(), state);

        LocalDateTime time = LocalDateTime.of(2026, 11, 12, 10, 0);

        Transaction offline = new Transaction(

                "TXN-OFFLINE-TOO-LATE",

                TransactionType.SALE, "", time, time.plusHours(48).plusMinutes(1),

                "CARD-001", "OUT-001", "DIESEL",

                new BigDecimal("201"), new BigDecimal("20100"),

                new BigDecimal("1000"), true);

        TransactionDecision decision = processor.process(offline);

        assertEquals("DECLINE", decision.getDecision());

        assertEquals("LATE_OFFLINE", decision.getReasonCode());

        assertEquals(BigDecimal.ZERO, state.getOutletRiskAmount("OUT-001"));

        assertEquals(BigDecimal.ZERO, state.getUnbilledAmount("ACC-001"));

    }

    @Test

    public void rejectsOfflinePostingOneSecondBeyond48Hours() {

        EngineState state = new EngineState();
        TransactionProcessor processor =
                new TransactionProcessor(createContext(), state);
        LocalDateTime timestamp =
                LocalDateTime.of(2026, 11, 12, 10, 0);
        Transaction offline = new Transaction(
                "TXN-OFFLINE-48H-ONE-SECOND", TransactionType.SALE, "",
                timestamp, timestamp.plusHours(48).plusSeconds(1),
                "CARD-001", "OUT-001", "DIESEL",
                new BigDecimal("10"), new BigDecimal("1000"),
                new BigDecimal("1000"), true);

        TransactionDecision decision = processor.process(offline);

        assertEquals("DECLINE", decision.getDecision());
        assertEquals("LATE_OFFLINE", decision.getReasonCode());
    }

    @Test

    public void offlineTankLimitViolationIsPostedToOutletRiskLedger() {

        EngineState state = new EngineState();

        TransactionProcessor processor =

                new TransactionProcessor(createContext(), state);

        LocalDateTime transactionTime =

                LocalDateTime.of(2026, 11, 12, 10, 0);

        Transaction offline = new Transaction(

                "TXN-OFFLINE-TANK",

                TransactionType.SALE,

                "",

                transactionTime,

                transactionTime.plusHours(2),

                "CARD-001",

                "OUT-001",

                "DIESEL",

                new BigDecimal("201"),

                new BigDecimal("20100"),

                new BigDecimal("1000"),

                true);

        TransactionDecision decision = processor.process(offline);

        assertEquals("APPROVED_FLAGGED", decision.getDecision());

        assertEquals("OFFLINE_RISK_POSTED", decision.getReasonCode());

        assertEquals(new BigDecimal("20100.00"), decision.getEngineAmount());

        assertEquals(new BigDecimal("20100.00"),

                state.getOutletRiskAmount("OUT-001"));

        assertEquals(new BigDecimal("20100.00"),

                state.getUnbilledAmount("ACC-001"));

    }

    @Test

    public void onlineTankLimitViolationRemainsDeclined() {

        EngineState state = new EngineState();

        TransactionProcessor processor =

                new TransactionProcessor(createContext(), state);

        TransactionDecision decision = processor.process(

                createTransaction("TXN-ONLINE-TANK", "DIESEL", "201", "1000"));

        assertEquals("DECLINE", decision.getDecision());

        assertEquals("TANK_CAPACITY_EXCEEDED", decision.getReasonCode());

        assertEquals(new BigDecimal("0"),

                state.getOutletRiskAmount("OUT-001"));

        assertEquals(new BigDecimal("0"),

                state.getUnbilledAmount("ACC-001"));

    }

    @Test

    public void offlineCreditLimitViolationIsPostedToOutletRiskLedger() {

        EngineContext context = createContext();

        FleetAccount account = context.findAccount("ACC-001").orElseThrow();

        account.setCreditLimit(new BigDecimal("1000"));

        EngineState state = new EngineState();

        TransactionProcessor processor =

                new TransactionProcessor(context, state);

        LocalDateTime transactionTime =

                LocalDateTime.of(2026, 11, 12, 10, 0);

        Transaction offline = new Transaction(

                "TXN-OFFLINE-CREDIT",

                TransactionType.SALE,

                "",

                transactionTime,

                transactionTime.plusMinutes(30),

                "CARD-001",

                "OUT-001",

                "DIESEL",

                new BigDecimal("50"),

                new BigDecimal("5000"),

                new BigDecimal("1000"),

                true);

        TransactionDecision decision = processor.process(offline);

        assertEquals("APPROVED_FLAGGED", decision.getDecision());

        assertEquals("OFFLINE_RISK_POSTED", decision.getReasonCode());

        assertEquals(new BigDecimal("5000.00"),

                state.getOutletRiskAmount("OUT-001"));

        assertEquals(new BigDecimal("5000.00"),

                state.getUnbilledAmount("ACC-001"));

    }

    @Test

    public void offlineLubeCapViolationIsPostedToOutletRiskLedger() {

        EngineState state = new EngineState();

        TransactionProcessor processor =

                new TransactionProcessor(createContext(), state);

        LocalDateTime transactionTime =

                LocalDateTime.of(2026, 11, 12, 10, 0);

        Transaction offline = new Transaction(

                "TXN-OFFLINE-LUBE",

                TransactionType.SALE,

                "",

                transactionTime,

                transactionTime.plusMinutes(15),

                "CARD-001",

                "OUT-001",

                "LUBE",

                new BigDecimal("21"),

                new BigDecimal("2100"),

                new BigDecimal("1000"),

                true);

        TransactionDecision decision = processor.process(offline);

        assertEquals("APPROVED_FLAGGED", decision.getDecision());

        assertEquals("OFFLINE_RISK_POSTED", decision.getReasonCode());

        assertEquals(new BigDecimal("2100.00"),

                state.getOutletRiskAmount("OUT-001"));

        assertEquals(new BigDecimal("2100.00"),

                state.getUnbilledAmount("ACC-001"));

        assertEquals(new BigDecimal("2100.00"),

                state.getMonthlyLubeSpend(

                        "ACC-001", LocalDate.of(2026, 11, 12)));

    }

}
