
package com.t052.fuelbilling.repository;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.csv.CSVRecord;

import com.t052.fuelbilling.model.Payment;

public class PaymentRepository {

    private final CsvReader csvReader;

    public PaymentRepository() {
        this.csvReader = new CsvReader();
    }

    public List<Payment> loadPayments(Path filePath)
            throws IOException {

        List<CSVRecord> records = csvReader.readCsv(filePath);

        List<Payment> payments = new ArrayList<>();

        for (CSVRecord record : records) {

            LocalDate paymentDate = LocalDate.parse(
                    record.get("payment_date"));

            String accountId = record.get("account_id");

            String amountValue = record.get("amount").trim();

            boolean fullPayment = amountValue.equalsIgnoreCase("FULL");

            BigDecimal amount = fullPayment
                    ? null
                    : new BigDecimal(amountValue);

            String mode = record.get("mode");
            String reference = record.get("reference");

            Payment payment = new Payment(
                    paymentDate,
                    accountId,
                    amount,
                    fullPayment,
                    mode,
                    reference
            );

            payments.add(payment);
        }

        return payments;
    }
}
