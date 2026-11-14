
package com.t052.fuelbilling.model;

import java.math.BigDecimal;

public class Vehicle {

    private String vehicleId;
    private String accountId;
    private String registrationNumber;
    private String vehicleClass;
    private BigDecimal tankCapacityLitres;

    public Vehicle(
            String vehicleId,
            String accountId,
            String registrationNumber,
            String vehicleClass,
            BigDecimal tankCapacityLitres) {

        this.vehicleId = vehicleId;
        this.accountId = accountId;
        this.registrationNumber = registrationNumber;
        this.vehicleClass = vehicleClass;
        this.tankCapacityLitres = tankCapacityLitres;
    }

    public String getVehicleId() {
        return vehicleId;
    }

    public String getAccountId() {
        return accountId;
    }

    public String getRegistrationNumber() {
        return registrationNumber;
    }

    public String getVehicleClass() {
        return vehicleClass;
    }

    public BigDecimal getTankCapacityLitres() {
        return tankCapacityLitres;
    }
}
