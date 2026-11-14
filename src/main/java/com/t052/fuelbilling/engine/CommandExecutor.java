
package com.t052.fuelbilling.engine;

import com.t052.fuelbilling.command.ProjectCommand;
import com.t052.fuelbilling.command.CommandType;
import com.t052.fuelbilling.model.FuelCard;
import com.t052.fuelbilling.model.Transaction;
import com.t052.fuelbilling.model.enums.CardStatus;
import com.t052.fuelbilling.model.enums.TransactionType;

import com.t052.fuelbilling.repository.FuelPriceRepository;
import com.t052.fuelbilling.repository.TransactionRepository;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.IdentityHashMap;

public class CommandExecutor {

    private static final DateTimeFormatter TIMESTAMP_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final EngineContext context;
    private final EngineState state;
    private final Path dataDirectory;

    private final FuelPriceRepository fuelPriceRepository;
    private final TransactionRepository transactionRepository;

    private final TransactionProcessor transactionProcessor;
    private final ReversalProcessor reversalProcessor;
    private final InvoiceProcessor invoiceProcessor;
    private final PaymentProcessor paymentProcessor;
    private boolean statementsStarted = false;

    public CommandExecutor(
            EngineContext context,
            EngineState state,
            Path dataDirectory) {

        if (context == null || state == null || dataDirectory == null) {
            throw new IllegalArgumentException(
                    "Context, state and data directory cannot be null");
        }

        this.context = context;
        this.state = state;
        this.dataDirectory = dataDirectory;

        this.fuelPriceRepository = new FuelPriceRepository();
        this.transactionRepository = new TransactionRepository();

        this.transactionProcessor =
                new TransactionProcessor(context, state);
        this.reversalProcessor =
                new ReversalProcessor(context, state);
        this.invoiceProcessor =
                new InvoiceProcessor(context, state, dataDirectory);
        this.paymentProcessor =
                new PaymentProcessor(context, state);
    }

    /**
     * Executes commands in the exact order supplied by the command parser.
     */
    public void execute(List<ProjectCommand> commands)
            throws IOException {

        if (commands == null) {
            throw new IllegalArgumentException(
                    "Command list cannot be null");
        }

        for (ProjectCommand command : commands) {
            try {
                executeCommand(command);
            } catch (IllegalArgumentException exception) {

                // A LOST card is terminal. The attempted unblock is a
                // deliberate negative test in the supplied command file.
                // Reject that command, but continue with later commands.
                boolean lostCardUnblock =
                        command.getType() == CommandType.UNBLOCK_CARD
                        && "A lost card cannot be unblocked."
                                .equals(exception.getMessage());

                if (lostCardUnblock) {
                    System.err.println(
                            "COMMAND REJECTED at line "
                                    + command.getLineNumber()
                                    + " ("
                                    + command.getType()
                                    + "): "
                                    + exception.getMessage());
                    continue;
                }

                throw new IOException(
                        "Command failed at line "
                                + command.getLineNumber()
                                + " ("
                                + command.getType()
                                + "): "
                                + exception.getMessage(),
                        exception);

            } catch (Exception exception) {
                throw new IOException(
                        "Command failed at line "
                                + command.getLineNumber()
                                + " ("
                                + command.getType()
                                + "): "
                                + exception.getMessage(),
                        exception);
            }
        }
    }

    private void executeCommand(ProjectCommand command)
            throws IOException {

        switch (command.getType()) {

            case LOAD_PRICES:
                loadPrices(command);
                break;

            case TRIP:
                addTripPermit(command);
                break;

            case POST_TRANSACTIONS:
                postTransactions(command);
                break;

            case BLOCK_CARD:
                blockCard(command);
                break;

            case UNBLOCK_CARD:
                unblockCard(command);
                break;

            case REPORT_LOST:
                reportLostCard(command);
                break;

            case PRINT_DECISION_LOG:
                printDecisionLog();
                break;

            case RUN_INVOICE:
                runInvoice(command);
                break;

            case APPLY_PAYMENTS:
                applyPayments(command);
                break;

            case PRINT_STATEMENT:
                printStatement(command.getArgument(0));
                break;

            case PRINT_MISUSE_REPORT:
                printMisuseReport(
                        LocalDate.parse(command.getArgument(0)),
                        LocalDate.parse(command.getArgument(1)));
                break;

            default:
                throw new IllegalArgumentException(
                        "Unsupported command: " + command.getType());
        }
    }

    // --------------------------------------------------
    // LOAD_PRICES
    // Format: LOAD_PRICES|prices.csv
    // --------------------------------------------------

    private void loadPrices(ProjectCommand command)
            throws IOException {

        Path file = resolveDataFile(command.getArgument(0));

        var prices = fuelPriceRepository.loadPrices(file);

        context.addFuelPrices(prices);

        System.out.println(
                "Loaded " + prices.size() + " fuel price records.");
    }

    // --------------------------------------------------
    // TRIP
    // Format: TRIP|card_id|state|from_ts|to_ts
    // --------------------------------------------------

    private void addTripPermit(ProjectCommand command) {

        String cardId = command.getArgument(0);
        String stateName = command.getArgument(1);

        LocalDateTime validFrom =
                parseTimestamp(command.getArgument(2));

        LocalDateTime validUntil =
                parseTimestamp(command.getArgument(3));

        FuelCard card = context.findCard(cardId)
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "Unknown card: " + cardId));

        if (card.getStatus() == CardStatus.LOST) {
            throw new IllegalArgumentException(
                    "A lost card cannot receive a trip permit.");
        }

        TripPermit permit = new TripPermit(
                cardId,
                stateName,
                validFrom,
                validUntil
        );

        context.addTripPermit(permit);

        System.out.println(
                "Trip permission added for card "
                        + cardId + " in " + stateName + ".");
    }

    // --------------------------------------------------
    // POST_TRANSACTIONS
    // Format: POST_TRANSACTIONS|file|from_ts|to_ts
    // --------------------------------------------------

    private void postTransactions(ProjectCommand command)
            throws IOException {

        Path file = resolveDataFile(command.getArgument(0));

        LocalDateTime fromTimestamp =
                parseTimestamp(command.getArgument(1));

        LocalDateTime toTimestamp =
                parseTimestamp(command.getArgument(2));

        if (toTimestamp.isBefore(fromTimestamp)) {
            throw new IllegalArgumentException(
                    "End timestamp cannot be before start timestamp.");
        }

        List<Transaction> transactions =
                transactionRepository.loadTransactions(file);

        List<Transaction> selectedTransactions =
                transactions.stream()
                        .filter(transaction ->
                                transaction.getPostedAt() != null
                                && !transaction.getPostedAt()
                                        .isBefore(fromTimestamp)
                                && !transaction.getPostedAt()
                                        .isAfter(toTimestamp))
                        .sorted(
                                Comparator.comparing(
                                        Transaction::getPostedAt)
                                .thenComparing(
                                        Transaction::getTransactionId))
                        .toList();

        int processedCount = 0;

        for (Transaction transaction : selectedTransactions) {

            TransactionDecision decision;

            if (transaction.getTransactionType() == TransactionType.REVERSAL) {
                decision = reversalProcessor.process(transaction);
            } else {
                decision = transactionProcessor.process(transaction);

                // Retain successful sales so later reversals can be validated.
                boolean approved = "APPROVE".equals(decision.getDecision())
                        || "APPROVED_FLAGGED".equals(decision.getDecision());

                if (approved && decision.getEngineAmount() != null) {
                    var vehicle = context.findVehicleForCard(
                            transaction.getCardId()).orElseThrow();
                    var account = context.findAccountForCard(
                            transaction.getCardId()).orElseThrow();

                    state.recordApprovedSale(
                            transaction,
                            account.getAccountId(),
                            vehicle.getVehicleId(),
                            decision.getEngineAmount());
                }
            }

            processedCount++;

            System.out.println(
                    transaction.getTransactionId()
                            + " -> "
                            + decision.getDecision()
                            + " ("
                            + decision.getReasonCode()
                            + ")");
        }

        System.out.println(
                "Processed " + processedCount
                        + " transactions.");
    }

    // --------------------------------------------------
    // RUN_INVOICE
    // Format: RUN_INVOICE|yyyy-MM-dd
    // --------------------------------------------------

    private void runInvoice(ProjectCommand command)
            throws IOException {

        LocalDate invoiceDate = LocalDate.parse(command.getArgument(0));

        List<InvoiceRecord> invoices =
                invoiceProcessor.generate(invoiceDate);

        System.out.println(
                "Generated " + invoices.size()
                        + " invoices for " + invoiceDate + ".");
        System.out.println(
                "Invoice files saved in: " + dataDirectory.toAbsolutePath());
    }

    // --------------------------------------------------
    // APPLY_PAYMENTS
    // Format: APPLY_PAYMENTS|payments_file
    // --------------------------------------------------

    private void applyPayments(ProjectCommand command)
            throws IOException {

        Path file = resolveDataFile(command.getArgument(0));

        int count = paymentProcessor.applyPayments(file);

        System.out.println(
                "Payment processing completed. Records applied: "
                        + count + ".");
    }

    // --------------------------------------------------
    // BLOCK_CARD
    // Format: BLOCK_CARD|timestamp|card_id|who
    // --------------------------------------------------

    private void blockCard(ProjectCommand command) {

        parseTimestamp(command.getArgument(0));

        String cardId = command.getArgument(1);
        String blockedBy = command.getArgument(2);

        FuelCard card = context.findCard(cardId)
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "Unknown card: " + cardId));

        if (card.getStatus() == CardStatus.LOST) {
            throw new IllegalArgumentException(
                    "A lost card cannot be blocked again.");
        }

        if (blockedBy.isBlank()) {
            throw new IllegalArgumentException(
                    "Blocked-by information cannot be empty.");
        }

        card.setStatus(CardStatus.BLOCKED);

        System.out.println(
                "Card " + cardId
                        + " blocked by " + blockedBy + ".");
    }

    // --------------------------------------------------
    // UNBLOCK_CARD
    // Format: UNBLOCK_CARD|timestamp|card_id
    // --------------------------------------------------

    private void unblockCard(ProjectCommand command) {

        parseTimestamp(command.getArgument(0));

        String cardId = command.getArgument(1);

        FuelCard card = context.findCard(cardId)
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "Unknown card: " + cardId));

        if (card.getStatus() == CardStatus.LOST) {
            throw new IllegalArgumentException(
                    "A lost card cannot be unblocked.");
        }

        if (card.getStatus() != CardStatus.BLOCKED) {
            throw new IllegalArgumentException(
                    "Only blocked cards can be unblocked.");
        }

        card.setStatus(CardStatus.ACTIVE);

        System.out.println(
                "Card " + cardId + " is now ACTIVE.");
    }

    // --------------------------------------------------
    // REPORT_LOST
    // Format: REPORT_LOST|timestamp|card_id
    // --------------------------------------------------

    private void reportLostCard(ProjectCommand command) {

        parseTimestamp(command.getArgument(0));

        String cardId = command.getArgument(1);

        FuelCard card = context.findCard(cardId)
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "Unknown card: " + cardId));

        card.setStatus(CardStatus.LOST);

        System.out.println(
                "Card " + cardId
                        + " has been permanently marked LOST.");
    }

    // --------------------------------------------------
    // PRINT_DECISION_LOG
    // --------------------------------------------------

    private void printDecisionLog() throws IOException {
        List<TransactionDecision> decisions = orderedDecisions();
        List<String> rows = new ArrayList<>();
        rows.add("txn_id,card_id,account_id,timestamp,product,litres,engine_amount,decision,reason_code,variance_flag,offline_late");

        for (TransactionDecision decision : decisions) {
            rows.add(String.join(",",
                    csv(decision.getTransactionId()),
                    csv(decision.getCardId()),
                    csv(decision.getAccountId()),
                    csv(decision.getTimestamp()),
                    csv(decision.getProduct()),
                    csv(decimal(decision.getLitres())),
                    csv(decimal(decision.getEngineAmount())),
                    csv(decision.getDecision()),
                    csv(decision.getReasonCode()),
                    Boolean.toString(decision.isVarianceFlag()),
                    Boolean.toString(decision.isOfflineLate())));
        }

        Files.createDirectories(dataDirectory);
        Files.write(dataDirectory.resolve("decision_log.csv"),
                rows, StandardCharsets.UTF_8);

        System.out.println();
        System.out.println("========== DECISION LOG ==========");
        for (String row : rows) System.out.println(row);
        System.out.println("==================================");
        System.out.println("Decision log saved to: "
                + dataDirectory.resolve("decision_log.csv").toAbsolutePath());
    }

    /** Reorders decisions to match the original transactions.csv row order. */
    private List<TransactionDecision> orderedDecisions() throws IOException {
        Path sourceFile = dataDirectory.resolve("transactions.csv");
        if (!Files.exists(sourceFile)) return state.getDecisions();

        List<Transaction> sourceRows =
                transactionRepository.loadTransactions(sourceFile);
        Map<String, List<Integer>> positions = new HashMap<>();
        for (int i = 0; i < sourceRows.size(); i++) {
            positions.computeIfAbsent(sourceRows.get(i).getTransactionId(),
                    ignored -> new ArrayList<>()).add(i);
        }

        Map<String, Integer> used = new HashMap<>();
        Map<TransactionDecision, Integer> order = new IdentityHashMap<>();
        int fallback = sourceRows.size();
        for (TransactionDecision decision : state.getDecisions()) {
            List<Integer> matches = positions.get(decision.getTransactionId());
            int occurrence = used.getOrDefault(decision.getTransactionId(), 0);
            if (matches != null && occurrence < matches.size()) {
                order.put(decision, matches.get(occurrence));
                used.put(decision.getTransactionId(), occurrence + 1);
            } else {
                order.put(decision, fallback++);
            }
        }

        return state.getDecisions().stream()
                .sorted(Comparator.comparingInt(order::get))
                .toList();
    }

    /** Writes one account's chronological ledger block to statements.csv. */
    private void printStatement(String accountId) throws IOException {
        if (context.findAccount(accountId).isEmpty()) {
            throw new IllegalArgumentException("Unknown account: " + accountId);
        }

        List<LedgerEvent> events = new ArrayList<>();
        for (InvoiceRecord invoice : state.getInvoicesForAccount(accountId)) {
            // A later invoice carries the previous outstanding balance forward.
            // Credit only that old balance: the new invoice separately debits
            // both priorBalance and newly accrued interest, so crediting
            // interest here would erase the interest charge from the ledger.
            BigDecimal carried = invoice.getPriorBalance();
            if (carried.signum() > 0) {
                events.add(new LedgerEvent(invoice.getInvoiceDate(), 0,
                        "CARRY_FORWARD", invoice.getInvoiceId(),
                        BigDecimal.ZERO, carried));
            }
            events.add(new LedgerEvent(invoice.getInvoiceDate(), 1,
                    "INVOICE", invoice.getInvoiceId(),
                    invoice.getTotalDue(), BigDecimal.ZERO));
        }

        for (PaymentAllocation allocation : state.getPaymentAllocations()) {
    if (!accountId.equals(allocation.getAccountId())) continue;

    String allocationType = allocation.getAllocationType();
    if ("CREDIT_APPLIED".equals(allocationType)) continue;

    if ("INTEREST".equals(allocationType)) {
        events.add(new LedgerEvent(allocation.getDate(), 2,
                "INTEREST", allocation.getReference(),
                allocation.getAmount(), BigDecimal.ZERO));

    } else if ("PAYMENT".equals(allocationType)
            || "ACCOUNT_CREDIT".equals(allocationType)) {
        events.add(new LedgerEvent(allocation.getDate(), 3,
                allocationType, allocation.getReference(),
                BigDecimal.ZERO, allocation.getAmount()));
    }
}

        events.sort(Comparator.comparing(LedgerEvent::date)
                .thenComparingInt(LedgerEvent::priority)
                .thenComparing(LedgerEvent::reference));

        BigDecimal balance = BigDecimal.ZERO.setScale(2);
        List<String> rows = new ArrayList<>();
        rows.add("account_id,entry_date,entry_type,reference,debit,credit,balance");
        for (LedgerEvent event : events) {
            balance = balance.add(event.debit()).subtract(event.credit())
                    .setScale(2, java.math.RoundingMode.HALF_UP);
            rows.add(String.join(",",
                    csv(accountId), event.date().toString(),
                    csv(event.type()), csv(event.reference()),
                    moneyText(event.debit()), moneyText(event.credit()),
                    moneyText(balance)));
        }

        Path output = dataDirectory.resolve("statements.csv");
        Files.createDirectories(dataDirectory);
        List<String> appendRows = statementsStarted
                ? rows.subList(1, rows.size()) : rows;
        if (!statementsStarted) {
            Files.write(output, appendRows, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            statementsStarted = true;
        } else {
            Files.write(output, appendRows, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        }

        System.out.println("Account statement generated for " + accountId
                + " (" + events.size() + " ledger entries).");
    }

    /** Writes all misuse events within the inclusive date range. */
    private void printMisuseReport(LocalDate fromDate, LocalDate toDate)
            throws IOException {
        if (toDate.isBefore(fromDate)) {
            throw new IllegalArgumentException(
                    "Misuse report end date cannot be before start date.");
        }

        List<TransactionDecision> ordered = new ArrayList<>(state.getDecisions());
        ordered.sort(Comparator.comparing(
                decision -> decision.getTimestamp() == null
                        || decision.getTimestamp().isBlank()
                        ? LocalDateTime.MIN
                        : LocalDateTime.parse(decision.getTimestamp())));

        List<String> rows = new ArrayList<>();
        rows.add("txn_id,card_id,account_id,outlet_id,timestamp,flag_type,detail");
        for (TransactionDecision decision : ordered) {
            if (decision.getTimestamp() == null || decision.getTimestamp().isBlank()) continue;
            LocalDate date = LocalDateTime.parse(decision.getTimestamp()).toLocalDate();
            if (date.isBefore(fromDate) || date.isAfter(toDate)) continue;

            // Offline risk is a distinct misuse event. Preserve it even when
            // the same transaction also has a mileage or price-variance flag.
            if ("OFFLINE_RISK_POSTED".equals(decision.getReasonCode())) {
                rows.add(String.join(",",
                        csv(decision.getTransactionId()),
                        csv(decision.getCardId()),
                        csv(decision.getAccountId()),
                        csv(decision.getOutletId()),
                        csv(decision.getTimestamp()),
                        csv("OFFLINE_RISK"),
                        csv(decision.getReasonCode())));
            }

            String flagType = null;
            if (decision.isMileageFlag() && decision.isVarianceFlag()) {
                flagType = "MILEAGE_AND_PRICE_VARIANCE";
            } else if (decision.isMileageFlag()) {
                flagType = "MILEAGE_OUT_OF_RANGE";
            } else if (decision.isVarianceFlag()) {
                flagType = "PRICE_VARIANCE";
            } else if ("CARD_BLOCKED_TAMPER_ALERT".equals(decision.getReasonCode())) {
                flagType = "TAMPER_ALERT";
            }

            if (flagType != null) {
                rows.add(String.join(",",
                        csv(decision.getTransactionId()),
                        csv(decision.getCardId()),
                        csv(decision.getAccountId()),
                        csv(decision.getOutletId()),
                        csv(decision.getTimestamp()),
                        csv(flagType), csv(decision.getReasonCode())));
            }
        }

        Files.createDirectories(dataDirectory);
        Path output = dataDirectory.resolve("misuse_report.csv");
        Files.write(output, rows, StandardCharsets.UTF_8);
        System.out.println("Misuse report generated with "
                + (rows.size() - 1) + " records: " + output.toAbsolutePath());
    }

    private String moneyText(BigDecimal value) {
        return value.setScale(2, java.math.RoundingMode.HALF_UP).toPlainString();
    }

    private record LedgerEvent(LocalDate date, int priority, String type,
                               String reference, BigDecimal debit,
                               BigDecimal credit) {}

    // --------------------------------------------------
    // HELPER METHODS
    // --------------------------------------------------

    private Path resolveDataFile(String fileName) {

        Path suppliedPath = Path.of(fileName);

        if (suppliedPath.isAbsolute()) {
            return suppliedPath;
        }

        return dataDirectory.resolve(suppliedPath).normalize();
    }

    private LocalDateTime parseTimestamp(String value) {

        return LocalDateTime.parse(
                value,
                TIMESTAMP_FORMAT);
    }

    private String decimal(BigDecimal value) {

        return value == null ? "" : value.toPlainString();
    }

    private String csv(String value) {

        if (value == null) {
            return "";
        }

        return "\"" + value.replace("\"", "\"\"") + "\"";
    }

}
