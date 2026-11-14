
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

public class OdometerBoundaryTest {

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
                Set.of(FuelProduct.DIESEL),
                CardStatus.ACTIVE
        );

        Outlet outlet = new Outlet(
                "OUT-001",
                "Test Outlet",
                "Bangalore",
                "KARNATAKA",
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

        context.addFuelPrices(List.of(
                new FuelPrice(
                        LocalDate.of(2026, 11, 12),
                        "Bangalore",
                        FuelProduct.DIESEL,
                        new BigDecimal("100.00")
                )
        ));

        return context;
    }

    private Transaction createTransaction(
            String id,
            String litres,
            String odometer) {

        LocalDateTime timestamp =
                LocalDateTime.of(2026, 11, 12, 10, 0);

        BigDecimal litreAmount = new BigDecimal(litres);

        return new Transaction(
                id,
                TransactionType.SALE,
                "",
                timestamp,
                timestamp,
                "CARD-001",
                "OUT-001",
                "DIESEL",
                litreAmount,
                litreAmount.multiply(new BigDecimal("100.00")),
                new BigDecimal(odometer),
                false
        );
    }

    @Test
    public void approvesMileageExactlyAtMinimumBoundary() {

        EngineState state = new EngineState();

        state.updateOdometer(
                "VEH-001",
                new BigDecimal("1000")
        );

        TransactionProcessor processor =
                new TransactionProcessor(createContext(), state);

        TransactionDecision decision = processor.process(
                createTransaction("TXN-BOUNDARY-2", "50", "1100")
        );

        assertEquals("APPROVE", decision.getDecision());
        assertEquals("APPROVED", decision.getReasonCode());
        assertFalse(decision.isMileageFlag());
    }

    @Test
    public void approvesMileageExactlyAtMaximumBoundary() {

        EngineState state = new EngineState();

        state.updateOdometer(
                "VEH-001",
                new BigDecimal("1000")
        );

        TransactionProcessor processor =
                new TransactionProcessor(createContext(), state);

        TransactionDecision decision = processor.process(
                createTransaction("TXN-BOUNDARY-25", "50", "2250")
        );

        assertEquals("APPROVE", decision.getDecision());
        assertEquals("APPROVED", decision.getReasonCode());
        assertFalse(decision.isMileageFlag());
    }
}
