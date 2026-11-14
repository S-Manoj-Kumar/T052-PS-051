
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

public class EngineContextTest {

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

        LoadedData data = new LoadedData(
            List.of(account),
            List.of(vehicle),
            List.of(card),
            List.of(outlet)
        );

        return new EngineContext(data);
    }

    @Test
    public void findsAccountVehicleAndCard() {

        EngineContext context = createContext();

        assertTrue(context.findAccount("ACC-001").isPresent());
        assertTrue(context.findVehicle("VEH-001").isPresent());
        assertTrue(context.findCard("CARD-001").isPresent());
        assertTrue(context.findOutlet("OUT-001").isPresent());
    }

    @Test
    public void findsAccountThroughCardRelationship() {

        EngineContext context = createContext();

        assertEquals(
            "ACC-001",
            context.findAccountForCard("CARD-001")
                .orElseThrow()
                .getAccountId()
        );

        assertEquals(
            "VEH-001",
            context.findVehicleForCard("CARD-001")
                .orElseThrow()
                .getVehicleId()
        );
    }

    @Test
    public void returnsEmptyForUnknownIds() {

        EngineContext context = createContext();

        assertFalse(context.findAccount("UNKNOWN").isPresent());
        assertFalse(context.findCard("UNKNOWN").isPresent());
        assertFalse(context.findVehicle("UNKNOWN").isPresent());
        assertFalse(context.findOutlet("UNKNOWN").isPresent());
    }

    @Test
    public void findsFuelPriceByDateCityAndProduct() {

        EngineContext context = createContext();

        FuelPrice price = new FuelPrice(
            LocalDate.of(2026, 11, 12),
            "Bangalore",
            FuelProduct.DIESEL,
            new BigDecimal("92.50")
        );

        context.addFuelPrices(List.of(price));

        FuelPrice result = context.findFuelPrice(
            LocalDate.of(2026, 11, 12),
            "BANGALORE",
            FuelProduct.DIESEL
        ).orElseThrow();

        assertEquals(
            0,
            new BigDecimal("92.50")
                .compareTo(result.getPricePerLitre())
        );
    }

    @Test
    public void rejectsDuplicatePricesWithoutPartialInsertion() {

        EngineContext context = createContext();

        LocalDate date = LocalDate.of(2026, 11, 12);

        FuelPrice first = new FuelPrice(
            date,
            "Bangalore",
            FuelProduct.DIESEL,
            new BigDecimal("92.50")
        );

        FuelPrice duplicate = new FuelPrice(
            date,
            "BANGALORE",
            FuelProduct.DIESEL,
            new BigDecimal("93.00")
        );

        try {
            context.addFuelPrices(List.of(first, duplicate));
            fail("Expected duplicate price exception");

        } catch (IllegalArgumentException exception) {
            assertTrue(
                exception.getMessage().contains("Duplicate")
            );
        }

        assertFalse(
            context.findFuelPrice(
                date,
                "Bangalore",
                FuelProduct.DIESEL
            ).isPresent()
        );
    }

    @Test
    public void tripPermissionIncludesStartAndEndTimes() {

        EngineContext context = createContext();

        LocalDateTime start =
            LocalDateTime.of(2026, 11, 12, 10, 0);

        LocalDateTime end =
            LocalDateTime.of(2026, 11, 12, 18, 0);

        TripPermit permit = new TripPermit(
            "CARD-001",
            "TAMIL NADU",
            start,
            end
        );

        context.addTripPermit(permit);

        assertTrue(
            context.hasTripPermission(
                "CARD-001", "TAMIL NADU", start
            )
        );

        assertTrue(
            context.hasTripPermission(
                "CARD-001", "TAMIL NADU", end
            )
        );

        assertFalse(
            context.hasTripPermission(
                "CARD-001",
                "TAMIL NADU",
                start.minusSeconds(1)
            )
        );

        assertFalse(
            context.hasTripPermission(
                "CARD-001",
                "TAMIL NADU",
                end.plusSeconds(1)
            )
        );
    }
}
