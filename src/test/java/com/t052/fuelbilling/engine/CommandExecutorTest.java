
package com.t052.fuelbilling.engine;

import com.t052.fuelbilling.command.CommandType;
import com.t052.fuelbilling.command.ProjectCommand;
import com.t052.fuelbilling.model.*;
import com.t052.fuelbilling.model.enums.*;
import com.t052.fuelbilling.repository.LoadedData;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import static org.junit.Assert.*;

public class CommandExecutorTest {

    @Rule
    public TemporaryFolder temporaryFolder =
            new TemporaryFolder();

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
                List.of(card,
                        new FuelCard(
                                "CARD-002",
                                "VEH-001",
                                Set.of(FuelProduct.DIESEL),
                                CardStatus.BLOCKED
                        )),
                List.of(outlet)
        );

        return new EngineContext(data);
    }

    private CommandExecutor createExecutor(EngineContext context) {

        return new CommandExecutor(
                context,
                new EngineState(),
                temporaryFolder.getRoot().toPath()
        );
    }

    private ProjectCommand command(
            CommandType type,
            String... arguments) {

        return new ProjectCommand(
                type,
                List.of(arguments),
                1
        );
    }

    @Test
    public void blocksActiveCard() throws Exception {

        EngineContext context = createContext();
        CommandExecutor executor = createExecutor(context);

        ProjectCommand blockCommand = command(
                CommandType.BLOCK_CARD,
                "2026-11-12 10:00:00",
                "CARD-001",
                "MANAGER"
        );

        executor.execute(List.of(blockCommand));

        assertEquals(
                CardStatus.BLOCKED,
                context.findCard("CARD-001")
                        .orElseThrow()
                        .getStatus()
        );
    }

    @Test
    public void unblocksBlockedCard() throws Exception {

        EngineContext context = createContext();

        context.findCard("CARD-001")
                .orElseThrow()
                .setStatus(CardStatus.BLOCKED);

        CommandExecutor executor = createExecutor(context);

        ProjectCommand unblockCommand = command(
                CommandType.UNBLOCK_CARD,
                "2026-11-12 11:00:00",
                "CARD-001"
        );

        executor.execute(List.of(unblockCommand));

        assertEquals(
                CardStatus.ACTIVE,
                context.findCard("CARD-001")
                        .orElseThrow()
                        .getStatus()
        );
    }

    @Test
    public void marksCardAsLost() throws Exception {

        EngineContext context = createContext();
        CommandExecutor executor = createExecutor(context);

        ProjectCommand lostCommand = command(
                CommandType.REPORT_LOST,
                "2026-11-12 12:00:00",
                "CARD-001"
        );

        executor.execute(List.of(lostCommand));

        assertEquals(
                CardStatus.LOST,
                context.findCard("CARD-001")
                        .orElseThrow()
                        .getStatus()
        );
    }

    @Test
    public void addsTripPermission() throws Exception {

        EngineContext context = createContext();
        CommandExecutor executor = createExecutor(context);

        ProjectCommand tripCommand = command(
                CommandType.TRIP,
                "CARD-001",
                "TAMIL NADU",
                "2026-11-12 10:00:00",
                "2026-11-12 18:00:00"
        );

        executor.execute(List.of(tripCommand));

        assertTrue(
                context.hasTripPermission(
                        "CARD-001",
                        "TAMIL NADU",
                        LocalDateTime.of(2026, 11, 12, 14, 0)
                )
        );
    }

    @Test
    public void loadsFuelPrices() throws Exception {

        EngineContext context = createContext();
        CommandExecutor executor = createExecutor(context);

        Path pricesFile =
                temporaryFolder.getRoot().toPath()
                        .resolve("prices.csv");

        Files.writeString(
                pricesFile,
                "price_date,city,product,price_per_litre\n"
                        + "2026-11-12,Bangalore,DIESEL,92.50\n"
        );

        ProjectCommand loadCommand = command(
                CommandType.LOAD_PRICES,
                "prices.csv"
        );

        executor.execute(List.of(loadCommand));

        FuelPrice loadedPrice = context.findFuelPrice(
                java.time.LocalDate.of(2026, 11, 12),
                "Bangalore",
                FuelProduct.DIESEL
        ).orElseThrow();

        assertEquals(
                0,
                new BigDecimal("92.50")
                        .compareTo(loadedPrice.getPricePerLitre())
        );
    }

    @Test
    public void rejectsUnknownCardWhenBlocking() {

        EngineContext context = createContext();
        CommandExecutor executor = createExecutor(context);

        ProjectCommand blockCommand = command(
                CommandType.BLOCK_CARD,
                "2026-11-12 10:00:00",
                "UNKNOWN-CARD",
                "MANAGER"
        );

        try {
            executor.execute(List.of(blockCommand));
            fail("Expected unknown card error");

        } catch (Exception exception) {
            assertTrue(
                    exception.getMessage()
                            .contains("Unknown card")
            );
        }
    }

    @Test
    public void rejectsLostCardUnblockAndContinuesWithNextCommand()
            throws Exception {

        EngineContext context = createContext();

        context.findCard("CARD-001")
                .orElseThrow()
                .setStatus(CardStatus.LOST);

        CommandExecutor executor = createExecutor(context);

        ProjectCommand unblockLostCard = command(
                CommandType.UNBLOCK_CARD,
                "2026-11-12 11:00:00",
                "CARD-001"
        );

        ProjectCommand blockAnotherCard = command(
                CommandType.BLOCK_CARD,
                "2026-11-12 11:01:00",
                "CARD-002",
                "MANAGER"
        );

        executor.execute(List.of(
                unblockLostCard,
                blockAnotherCard
        ));

        assertEquals(
                CardStatus.LOST,
                context.findCard("CARD-001")
                        .orElseThrow()
                        .getStatus()
        );

        assertEquals(
                CardStatus.BLOCKED,
                context.findCard("CARD-002")
                        .orElseThrow()
                        .getStatus()
        );
    }

    @Test
    public void statementCarryForwardDoesNotEraseNewInterest() throws Exception {
        EngineContext context = createContext();
        EngineState state = new EngineState();

        state.addInvoice(new InvoiceRecord(
                "INV-ACC-001-20261016", "ACC-001",
                LocalDate.of(2026, 10, 16),
                LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 15),
                "GOLD", new BigDecimal("1000.00"), BigDecimal.ZERO,
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                new BigDecimal("1000.00"), LocalDate.of(2026, 10, 23)));

        state.addInvoice(new InvoiceRecord(
                "INV-ACC-001-20261101", "ACC-001",
                LocalDate.of(2026, 11, 1),
                LocalDate.of(2026, 10, 16), LocalDate.of(2026, 10, 31),
                "SILVER", new BigDecimal("1000.00"), BigDecimal.ZERO,
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                new BigDecimal("1000.00"), new BigDecimal("20.00"),
                BigDecimal.ZERO, new BigDecimal("2020.00"),
                LocalDate.of(2026, 11, 8)));

        CommandExecutor executor = new CommandExecutor(
                context, state, temporaryFolder.getRoot().toPath());
        executor.execute(List.of(command(
                CommandType.PRINT_STATEMENT, "ACC-001")));

        String statement = Files.readString(
                temporaryFolder.getRoot().toPath().resolve("statements.csv"));
        assertTrue(statement.contains(
                "\"ACC-001\",2026-11-01,\"CARRY_FORWARD\",\"INV-ACC-001-20261101\",0.00,1000.00,0.00"));
        assertTrue(statement.contains(
                "\"ACC-001\",2026-11-01,\"INVOICE\",\"INV-ACC-001-20261101\",2020.00,0.00,2020.00"));
    }

}
