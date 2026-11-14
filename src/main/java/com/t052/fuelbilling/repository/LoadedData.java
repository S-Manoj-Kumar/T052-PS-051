
package com.t052.fuelbilling.repository;

import java.util.List;

import com.t052.fuelbilling.model.FleetAccount;
import com.t052.fuelbilling.model.Vehicle;
import com.t052.fuelbilling.model.FuelCard;
import com.t052.fuelbilling.model.Outlet;

public class LoadedData {

    private final List<FleetAccount> accounts;
    private final List<Vehicle> vehicles;
    private final List<FuelCard> fuelCards;
    private final List<Outlet> outlets;

    public LoadedData(
            List<FleetAccount> accounts,
            List<Vehicle> vehicles,
            List<FuelCard> fuelCards,
            List<Outlet> outlets) {

        this.accounts = List.copyOf(accounts);
        this.vehicles = List.copyOf(vehicles);
        this.fuelCards = List.copyOf(fuelCards);
        this.outlets = List.copyOf(outlets);
    }

    public List<FleetAccount> getAccounts() {
        return accounts;
    }

    public List<Vehicle> getVehicles() {
        return vehicles;
    }

    public List<FuelCard> getFuelCards() {
        return fuelCards;
    }

    public List<Outlet> getOutlets() {
        return outlets;
    }
}
