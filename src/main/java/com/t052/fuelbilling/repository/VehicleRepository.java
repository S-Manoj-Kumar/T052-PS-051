
package com.t052.fuelbilling.repository;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.csv.CSVRecord;

import com.t052.fuelbilling.model.Vehicle;

public class VehicleRepository {

    private final CsvReader csvReader;

    public VehicleRepository() {
        this.csvReader = new CsvReader();
    }

    public List<Vehicle> loadVehicles(Path filePath)
            throws IOException {

        List<CSVRecord> records = csvReader.readCsv(filePath);

        List<Vehicle> vehicles = new ArrayList<>();

        for (CSVRecord record : records) {

            String vehicleId = record.get("vehicle_id");
            String accountId = record.get("account_id");
            String registrationNumber = record.get("registration_no");
            String vehicleClass = record.get("vehicle_class");

            BigDecimal tankCapacity = new BigDecimal(
                    record.get("tank_capacity_litres"));

            Vehicle vehicle = new Vehicle(
                    vehicleId,
                    accountId,
                    registrationNumber,
                    vehicleClass,
                    tankCapacity
            );

            vehicles.add(vehicle);
        }

        return vehicles;
    }
}
