
package com.t052.fuelbilling.repository;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.csv.CSVRecord;

import com.t052.fuelbilling.model.Transaction;
import com.t052.fuelbilling.model.enums.TransactionType;

public class TransactionRepository {

    private final CsvReader csvReader;

    private static final DateTimeFormatter TIMESTAMP_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public TransactionRepository() {
        this.csvReader = new CsvReader();
    }

    public List<Transaction> loadTransactions(Path filePath)
            throws IOException {

        List<CSVRecord> records = csvReader.readCsv(filePath);

        List<Transaction> transactions = new ArrayList<>();

        for (CSVRecord record : records) {

            String transactionId = record.get("txn_id");

            TransactionType transactionType =
                    TransactionType.valueOf(
                            record.get("txn_type").trim());

            String referenceTransactionId =
                    record.get("ref_txn_id").trim();

            LocalDateTime transactionTimestamp =
                    LocalDateTime.parse(
                            record.get("timestamp"),
                            TIMESTAMP_FORMAT);

            LocalDateTime postedAt =
                    LocalDateTime.parse(
                            record.get("posted_at"),
                            TIMESTAMP_FORMAT);

            String cardId = record.get("card_id");
            String outletId = record.get("outlet_id");

            // Preserve the original product code.
            // Validation happens later in the authorisation engine.
            String product = record.get("product").trim();

            BigDecimal litres =
                    new BigDecimal(record.get("litres"));

            BigDecimal pumpAmount =
                    new BigDecimal(record.get("pump_amount"));

            BigDecimal odometerKm =
                    new BigDecimal(record.get("odometer_km"));

            boolean offline =
                    Boolean.parseBoolean(record.get("offline"));

            Transaction transaction = new Transaction(
                    transactionId,
                    transactionType,
                    referenceTransactionId,
                    transactionTimestamp,
                    postedAt,
                    cardId,
                    outletId,
                    product,
                    litres,
                    pumpAmount,
                    odometerKm,
                    offline
            );

            transactions.add(transaction);
        }

        return transactions;
    }
}
