
package com.t052.fuelbilling.model;

import java.util.HashSet;
import java.util.Set;

import com.t052.fuelbilling.model.enums.CardStatus;
import com.t052.fuelbilling.model.enums.FuelProduct;

public class FuelCard {

    private String cardId;
    private String vehicleId;
    private Set<FuelProduct> allowedProducts;
    private CardStatus status;

    public FuelCard(
            String cardId,
            String vehicleId,
            Set<FuelProduct> allowedProducts,
            CardStatus status) {

        this.cardId = cardId;
        this.vehicleId = vehicleId;
        this.allowedProducts = new HashSet<>(allowedProducts);
        this.status = status;
    }

    public String getCardId() {
        return cardId;
    }

    public String getVehicleId() {
        return vehicleId;
    }

    public Set<FuelProduct> getAllowedProducts() {
        return Set.copyOf(allowedProducts);
    }

    public CardStatus getStatus() {
        return status;
    }

    public void setStatus(CardStatus status) {
        this.status = status;
    }
}
