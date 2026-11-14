
package com.t052.fuelbilling.repository;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.commons.csv.CSVRecord;

import com.t052.fuelbilling.model.FleetAccount;

public class AccountRepository {

    private final CsvReader csvReader;

    public AccountRepository() {
        this.csvReader = new CsvReader();
    }

    public List<FleetAccount> loadAccounts(Path filePath)
            throws IOException {

        List<CSVRecord> records = csvReader.readCsv(filePath);

        List<FleetAccount> accounts = new ArrayList<>();

        for (CSVRecord record : records) {

            String accountId = record.get("account_id");
            String accountName = record.get("account_name");

            BigDecimal creditLimit =
                    new BigDecimal(record.get("credit_limit"));

            String billingCycle = record.get("billing_cycle");
            String initialTier = record.get("initial_tier");
            String paymentMode = record.get("payment_mode");

            Set<String> allowedStates =
                    Arrays.stream(record.get("allowed_states").split(";"))
                            .map(String::trim)
                            .collect(Collectors.toSet());

            int monthlyCap = Integer.parseInt(
                    record.get("per_vehicle_monthly_cap_litres"));

            int previousMonthLitres = Integer.parseInt(
                    record.get("prev_month_litres"));

            FleetAccount account = new FleetAccount(
                    accountId,
                    accountName,
                    creditLimit,
                    billingCycle,
                    initialTier,
                    paymentMode,
                    allowedStates,
                    monthlyCap,
                    previousMonthLitres
            );

            accounts.add(account);
        }

        return accounts;
    }
}
