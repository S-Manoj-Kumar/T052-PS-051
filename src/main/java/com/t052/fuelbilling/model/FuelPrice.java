
package com.t052.fuelbilling.model;

import java.math.BigDecimal;
import java.time.LocalDate;

import com.t052.fuelbilling.model.enums.FuelProduct;

public class FuelPrice {

    private LocalDate priceDate;
    private String city;
    private FuelProduct product;
    private BigDecimal pricePerLitre;

    public FuelPrice(
            LocalDate priceDate,
            String city,
            FuelProduct product,
            BigDecimal pricePerLitre) {

        this.priceDate = priceDate;
        this.city = city;
        this.product = product;
        this.pricePerLitre = pricePerLitre;
    }

    public LocalDate getPriceDate() {
        return priceDate;
    }

    public String getCity() {
        return city;
    }

    public FuelProduct getProduct() {
        return product;
    }

    public BigDecimal getPricePerLitre() {
        return pricePerLitre;
    }
}
