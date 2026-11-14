
package com.t052.fuelbilling.repository;

import java.io.IOException;
import java.nio.file.Path;

public class DataLoader {

    private final AccountRepository accountRepository;
    private final VehicleRepository vehicleRepository;
    private final FuelCardRepository fuelCardRepository;
    private final OutletRepository outletRepository;

    public DataLoader() {
        this.accountRepository = new AccountRepository();
        this.vehicleRepository = new VehicleRepository();
        this.fuelCardRepository = new FuelCardRepository();
        this.outletRepository = new OutletRepository();
    }

    public LoadedData loadMasterData(Path dataDirectory)
            throws IOException {

        Path accountsFile = dataDirectory.resolve("accounts.csv");
        Path vehiclesFile = dataDirectory.resolve("vehicles.csv");
        Path outletsFile = dataDirectory.resolve("outlets.csv");

        return new LoadedData(
                accountRepository.loadAccounts(accountsFile),
                vehicleRepository.loadVehicles(vehiclesFile),
                fuelCardRepository.loadFuelCards(vehiclesFile),
                outletRepository.loadOutlets(outletsFile)
        );
    }
}
