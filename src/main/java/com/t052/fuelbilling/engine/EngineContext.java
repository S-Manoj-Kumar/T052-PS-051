
package com.t052.fuelbilling.engine;

import com.t052.fuelbilling.model.*;
import com.t052.fuelbilling.model.enums.FuelProduct;
import com.t052.fuelbilling.repository.LoadedData;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

public class EngineContext {

    private final Map<String, FleetAccount> accounts;
    private final Map<String, Vehicle> vehicles;
    private final Map<String, FuelCard> cards;
    private final Map<String, Outlet> outlets;

    private final Map<PriceKey, FuelPrice> prices;
    private final Map<String, List<TripPermit>> tripPermits;

    public EngineContext(LoadedData data) {

        accounts = index(
            data.getAccounts(),
            FleetAccount::getAccountId,
            "account"
        );

        vehicles = index(
            data.getVehicles(),
            Vehicle::getVehicleId,
            "vehicle"
        );

        cards = index(
            data.getFuelCards(),
            FuelCard::getCardId,
            "card"
        );

        outlets = index(
            data.getOutlets(),
            Outlet::getOutletId,
            "outlet"
        );

        prices = new HashMap<>();
        tripPermits = new HashMap<>();

        validateRelationships();
    }

    private static <T> Map<String, T> index(
            List<T> items,
            java.util.function.Function<T, String> idExtractor,
            String entityName) {

        Map<String, T> result = new LinkedHashMap<>();

        for (T item : items) {

            String id = idExtractor.apply(item);

            if (id == null || id.isBlank()) {
                throw new IllegalArgumentException(
                    "Invalid " + entityName + " ID"
                );
            }

            if (result.putIfAbsent(id, item) != null) {
                throw new IllegalArgumentException(
                    "Duplicate " + entityName + " ID: " + id
                );
            }
        }

        return result;
    }

    private void validateRelationships() {

        for (Vehicle vehicle : vehicles.values()) {

            if (!accounts.containsKey(vehicle.getAccountId())) {
                throw new IllegalArgumentException(
                    "Vehicle " + vehicle.getVehicleId()
                    + " references unknown account "
                    + vehicle.getAccountId()
                );
            }
        }

        for (FuelCard card : cards.values()) {

            if (!vehicles.containsKey(card.getVehicleId())) {
                throw new IllegalArgumentException(
                    "Card " + card.getCardId()
                    + " references unknown vehicle "
                    + card.getVehicleId()
                );
            }
        }
    }

    public Optional<FleetAccount> findAccount(String id) {
        return Optional.ofNullable(accounts.get(id));
    }

    public Optional<Vehicle> findVehicle(String id) {
        return Optional.ofNullable(vehicles.get(id));
    }

    public Optional<FuelCard> findCard(String id) {
        return Optional.ofNullable(cards.get(id));
    }

    public Optional<Outlet> findOutlet(String id) {
        return Optional.ofNullable(outlets.get(id));
    }

    public Optional<Vehicle> findVehicleForCard(String cardId) {
        return findCard(cardId)
            .flatMap(card -> findVehicle(card.getVehicleId()));
    }

    public Optional<FleetAccount> findAccountForCard(String cardId) {
        return findVehicleForCard(cardId)
            .flatMap(vehicle -> findAccount(vehicle.getAccountId()));
    }

    
public void addFuelPrices(List<FuelPrice> newPrices) {

    if (newPrices == null) {
        throw new IllegalArgumentException(
            "Fuel price list cannot be null"
        );
    }

    Map<PriceKey, FuelPrice> pendingPrices = new HashMap<>();

    for (FuelPrice price : newPrices) {

        if (price == null
                || price.getPriceDate() == null
                || price.getCity() == null
                || price.getCity().isBlank()
                || price.getProduct() == null
                || price.getPricePerLitre() == null
                || price.getPricePerLitre().compareTo(
                    java.math.BigDecimal.ZERO) <= 0) {

            throw new IllegalArgumentException(
                "Invalid fuel price record"
            );
        }

        PriceKey key = new PriceKey(
            price.getPriceDate(),
            price.getCity().trim().toUpperCase(Locale.ROOT),
            price.getProduct()
        );

        if (prices.containsKey(key)
                || pendingPrices.putIfAbsent(key, price) != null) {

            throw new IllegalArgumentException(
                "Duplicate fuel price: " + key
            );
        }
    }

    // Commit only after every record passes validation.
    prices.putAll(pendingPrices);
}


    public Optional<FuelPrice> findFuelPrice(
            LocalDate date,
            String city,
            FuelProduct product) {

        PriceKey key = new PriceKey(
            date,
            city.trim().toUpperCase(Locale.ROOT),
            product
        );

        return Optional.ofNullable(prices.get(key));
    }

    public void addTripPermit(TripPermit permit) {

        tripPermits.computeIfAbsent(
            permit.getCardId(),
            key -> new ArrayList<>()
        ).add(permit);
    }

    public boolean hasTripPermission(
            String cardId,
            String state,
            LocalDateTime timestamp) {

        return tripPermits.getOrDefault(
            cardId,
            Collections.emptyList()
        ).stream().anyMatch(
            permit -> permit.covers(state, timestamp)
        );
    }

    public List<FleetAccount> getAccounts() {
        return accounts.values().stream()
                .sorted(Comparator.comparing(FleetAccount::getAccountId))
                .toList();
    }

    public int getAccountCount() {
        return accounts.size();
    }

    public int getVehicleCount() {
        return vehicles.size();
    }

    public int getCardCount() {
        return cards.size();
    }

    public int getOutletCount() {
        return outlets.size();
    }

    private record PriceKey(
        LocalDate date,
        String city,
        FuelProduct product
    ) {}
}
