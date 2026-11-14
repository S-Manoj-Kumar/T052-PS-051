
package com.t052.fuelbilling.repository;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.csv.CSVRecord;

import com.t052.fuelbilling.model.Outlet;

public class OutletRepository {

    private final CsvReader csvReader;

    public OutletRepository() {
        this.csvReader = new CsvReader();
    }

    public List<Outlet> loadOutlets(Path filePath)
            throws IOException {

        List<CSVRecord> records = csvReader.readCsv(filePath);

        List<Outlet> outlets = new ArrayList<>();

        for (CSVRecord record : records) {

            String outletId = record.get("outlet_id");
            String outletName = record.get("outlet_name");
            String city = record.get("city");
            String state = record.get("state");

            Outlet outlet = new Outlet(
                    outletId,
                    outletName,
                    city,
                    state,
                    "ACTIVE"
            );

            outlets.add(outlet);
        }

        return outlets;
    }
}
