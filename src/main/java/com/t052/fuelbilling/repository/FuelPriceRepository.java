
package com.t052.fuelbilling.repository;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.csv.CSVRecord;

import com.t052.fuelbilling.model.FuelPrice;
import com.t052.fuelbilling.model.enums.FuelProduct;

public class FuelPriceRepository {

    private final CsvReader csvReader;

    public FuelPriceRepository() {
        this.csvReader = new CsvReader();
    }

    public List<FuelPrice> loadPrices(Path filePath)
            throws IOException {

        List<CSVRecord> records = csvReader.readCsv(filePath);

        List<FuelPrice> prices = new ArrayList<>();

        for (CSVRecord record : records) {

            LocalDate priceDate = LocalDate.parse(
                    record.get("price_date"));

            String city = record.get("city");

            FuelProduct product = FuelProduct.valueOf(
                    record.get("product").trim());

            BigDecimal pricePerLitre = new BigDecimal(
                    record.get("price_per_litre"));

            FuelPrice fuelPrice = new FuelPrice(
                    priceDate,
                    city,
                    product,
                    pricePerLitre
            );

            prices.add(fuelPrice);
        }

        return prices;
    }
}
