
package com.t052.fuelbilling.repository;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.commons.csv.CSVRecord;

import com.t052.fuelbilling.model.FuelCard;
import com.t052.fuelbilling.model.enums.CardStatus;
import com.t052.fuelbilling.model.enums.FuelProduct;

public class FuelCardRepository {

    private final CsvReader csvReader;

    public FuelCardRepository() {
        this.csvReader = new CsvReader();
    }

    public List<FuelCard> loadFuelCards(Path filePath)
            throws IOException {

        List<CSVRecord> records = csvReader.readCsv(filePath);

        List<FuelCard> cards = new ArrayList<>();

        for (CSVRecord record : records) {

            String cardId = record.get("card_id");
            String vehicleId = record.get("vehicle_id");

            Set<FuelProduct> allowedProducts =
                    Arrays.stream(record.get("allowed_products").split(";"))
                            .map(String::trim)
                            .map(FuelProduct::valueOf)
                            .collect(Collectors.toSet());

            CardStatus status = CardStatus.valueOf(
                    record.get("card_status").trim());

            FuelCard card = new FuelCard(
                    cardId,
                    vehicleId,
                    allowedProducts,
                    status
            );

            cards.add(card);
        }

        return cards;
    }
}
