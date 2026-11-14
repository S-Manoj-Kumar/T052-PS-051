package com.t052.fuelbilling.engine;

import com.t052.fuelbilling.model.FuelCard;
import com.t052.fuelbilling.model.FleetAccount;
import com.t052.fuelbilling.model.FuelPrice;
import com.t052.fuelbilling.model.Outlet;
import com.t052.fuelbilling.model.Transaction;
import com.t052.fuelbilling.model.Vehicle;
import com.t052.fuelbilling.model.enums.CardStatus;
import com.t052.fuelbilling.model.enums.FuelProduct;
import com.t052.fuelbilling.model.enums.TransactionType;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;

public class TransactionProcessor {

    private static final BigDecimal ZERO = BigDecimal.ZERO;
    private static final BigDecimal ONE_RUPEE = new BigDecimal("1.00");
    private static final BigDecimal LUBE_MONTHLY_LIMIT =
            new BigDecimal("2000.00");

    private static final BigDecimal DAILY_LIMIT_MULTIPLIER =
            new BigDecimal("1.5");

    private static final BigDecimal MIN_MILEAGE =
            new BigDecimal("2.0");

    private static final BigDecimal MAX_MILEAGE =
            new BigDecimal("25.0");

    private static final BigDecimal OVERDRAFT_MULTIPLIER =
            new BigDecimal("1.10");

    private static final BigDecimal NIGHT_OVERDRAFT_START =
            new BigDecimal("22");

    private static final int MAX_OFFLINE_HOURS = 48;

    private final EngineContext context;
    private final EngineState state;

    public TransactionProcessor(
            EngineContext context,
            EngineState state) {

        if (context == null || state == null) {
            throw new IllegalArgumentException(
                    "Engine context and state cannot be null");
        }

        this.context = context;
        this.state = state;
    }

    public TransactionDecision process(Transaction transaction) {

        if (transaction == null) {
            throw new IllegalArgumentException(
                    "Transaction cannot be null");
        }

        String transactionId = transaction.getTransactionId();
        String cardId = transaction.getCardId();

        // Resolve the account as early as possible so even declined
        // transactions retain their account ID in the decision log.
        String resolvedAccountId = context.findAccountForCard(cardId)
                .map(FleetAccount::getAccountId)
                .orElse(null);

        // 1. Check transaction ID and duplicate processing.
        if (transactionId == null
                || transactionId.isBlank()
                || state.isTransactionProcessed(transactionId)) {

            return createDecision(
                    transaction,
                    resolvedAccountId,
                    null,
                    "DECLINE",
                    "DUPLICATE_TRANSACTION",
                    false,
                    false,
                    false);
        }

        // 2. Validate timestamps before using them.
        LocalDateTime transactionTime =
                transaction.getTransactionTimestamp();

        LocalDateTime postedAt = transaction.getPostedAt();

        if (transactionTime == null || postedAt == null) {
            state.markTransactionProcessed(transactionId);

            return decline(
                    transaction, resolvedAccountId, "INVALID_TIMESTAMP", true);
        }

        if (postedAt.isBefore(transactionTime)) {
            state.markTransactionProcessed(transactionId);

            return decline(
                    transaction, resolvedAccountId, "INVALID_POSTING", true);
        }

        boolean offlineLate = transaction.isOffline()
                && postedAt.isAfter(transactionTime);

        if (transaction.isOffline()) {

            Duration postingDelay =
                    Duration.between(transactionTime, postedAt);

            if (postingDelay.compareTo(Duration.ofHours(MAX_OFFLINE_HOURS)) > 0) {
                state.markTransactionProcessed(transactionId);

                return createDecision(
                        transaction,
                        resolvedAccountId,
                        null,
                        "DECLINE",
                        "LATE_OFFLINE",
                        false,
                        false,
                        true);
            }
        }

        LocalDate date = transactionTime.toLocalDate();

        // 3. Find the card.
        FuelCard card = context.findCard(cardId).orElse(null);

        if (card == null) {
            state.markTransactionProcessed(transactionId);

            return decline(
                    transaction, null, "UNKNOWN_CARD", false);
        }

        // 4. Check card status.
        if (card.getStatus() == CardStatus.LOST) {
            state.markTransactionProcessed(transactionId);

            return decline(
                    transaction, resolvedAccountId, "CARD_LOST", false);
        }

        if (card.getStatus() == CardStatus.BLOCKED) {

            int attempts =
                    state.recordBlockedAttempt(cardId, date);

            int declines =
                    state.recordDecline(cardId, date);

            if (attempts >= 2) {
                // The second blocked attempt is a tamper alert.
                // The misuse report will use this state information.
            }

            state.markTransactionProcessed(transactionId);

            return createDecision(
                    transaction,
                    resolvedAccountId,
                    null,
                    "DECLINE",
                    attempts >= 2
                            ? "CARD_BLOCKED_TAMPER_ALERT"
                            : "CARD_BLOCKED",
                    false,
                    false,
                    offlineLate);
        }

        // 5. Find the vehicle and account.
        Vehicle vehicle = context.findVehicle(card.getVehicleId())
                .orElse(null);

        if (vehicle == null) {
            state.markTransactionProcessed(transactionId);

            return decline(
                    transaction, null, "UNKNOWN_VEHICLE", false);
        }

        FleetAccount account =
                context.findAccount(vehicle.getAccountId()).orElse(null);

        if (account == null) {
            state.markTransactionProcessed(transactionId);

            return decline(
                    transaction, null, "UNKNOWN_ACCOUNT", false);
        }

        String accountId = account.getAccountId();

        // 6. Validate the product.
        FuelProduct product;

        try {
            product = FuelProduct.valueOf(
                    transaction.getProduct()
                            .trim()
                            .toUpperCase());
        } catch (RuntimeException exception) {

            // INVALID_PRODUCT is not counted toward automatic blocking.
            state.markTransactionProcessed(transactionId);

            return createDecision(
                    transaction,
                    accountId,
                    null,
                    "DECLINE",
                    "INVALID_PRODUCT",
                    false,
                    false,
                    offlineLate);
        }

        // LUBE is allowed on every card.
        if (product != FuelProduct.LUBE
                && !card.getAllowedProducts().contains(product)) {

            state.markTransactionProcessed(transactionId);

            return decline(
                    transaction,
                    accountId,
                    "PRODUCT_NOT_ALLOWED",
                    true);
        }

        // A CNG card cannot purchase liquid fuel.
        if (card.getAllowedProducts().contains(FuelProduct.CNG)
                && product != FuelProduct.CNG
                && product != FuelProduct.LUBE) {

            state.markTransactionProcessed(transactionId);

            return decline(
                    transaction,
                    accountId,
                    "CNG_CARD_RESTRICTION",
                    true);
        }

        // 7. Validate litres.
        BigDecimal litres = transaction.getLitres();

        if (litres == null || litres.compareTo(ZERO) <= 0) {
            state.markTransactionProcessed(transactionId);

            return decline(
                    transaction, accountId, "INVALID_LITRES", true);
        }

        // 8. Find and validate the outlet.
        Outlet outlet = context.findOutlet(
                transaction.getOutletId()).orElse(null);

        if (outlet == null) {
            state.markTransactionProcessed(transactionId);

            return decline(
                    transaction, accountId, "UNKNOWN_OUTLET", true);
        }

        if (outlet.getStatus() == null
                || !"ACTIVE".equalsIgnoreCase(
                        outlet.getStatus().trim())) {

            state.markTransactionProcessed(transactionId);

            return decline(
                    transaction, accountId, "OUTLET_INACTIVE", true);
        }

        // 9. Check the account's allowed states.
        boolean stateAllowed = account.getAllowedStates()
                .stream()
                .anyMatch(allowed ->
                        allowed.equalsIgnoreCase(
                                outlet.getState().trim()));

        boolean tripAllowed = context.hasTripPermission(
                cardId,
                outlet.getState(),
                transactionTime);

        if (!stateAllowed && !tripAllowed) {
            state.markTransactionProcessed(transactionId);

            return decline(
                    transaction,
                    accountId,
                    "OUTSIDE_ALLOWED_STATE",
                    true);
        }

        // 10. Find the official price for this date, city and product.
        FuelPrice fuelPrice = context.findFuelPrice(
                date,
                outlet.getCity(),
                product).orElse(null);

        if (fuelPrice == null) {
            state.markTransactionProcessed(transactionId);

            return decline(
                    transaction,
                    accountId,
                    "PRICE_NOT_FOUND",
                    true);
        }

        BigDecimal engineAmount = litres
                .multiply(fuelPrice.getPricePerLitre())
                .setScale(2, RoundingMode.HALF_UP);

        // 11. LUBE has separate spend and validation rules.
        // Its monthly cap is checked first so the planted cap scenario
        // receives the required LUBE_CAP decision even if another field
        // on that same transaction is also invalid.
        if (product == FuelProduct.LUBE) {
            return processLube(
                    transaction,
                    account,
                    vehicle,
                    engineAmount,
                    offlineLate);
        }

        // Offline transactions are still posted when a business limit
        // would have declined them. The outlet bears the risk amount.
        boolean offlineRiskPosted = false;

        // 12. Check tank capacity.
        BigDecimal tankCapacity =
                vehicle.getTankCapacityLitres();

        if (tankCapacity == null
                || tankCapacity.compareTo(ZERO) <= 0) {
            state.markTransactionProcessed(transactionId);
            return decline(
                    transaction, accountId, "INVALID_TANK_CAPACITY", false);
        }

        if (litres.compareTo(tankCapacity) > 0) {
            if (transaction.isOffline()) {
                offlineRiskPosted = true;
            } else {
                state.markTransactionProcessed(transactionId);
                return decline(
                        transaction, accountId, "TANK_CAPACITY_EXCEEDED", true);
            }
        }

        // 13. Check daily transaction count.
        if (state.getDailyTransactionCount(cardId, transactionTime) >= 2) {
            if (transaction.isOffline()) {
                offlineRiskPosted = true;
            } else {
                state.markTransactionProcessed(transactionId);
                return decline(
                        transaction, accountId, "DAILY_TRANSACTION_LIMIT", true);
            }
        }

        // 14. Check daily litres: maximum 1.5 times tank capacity.
        BigDecimal dailyLimit =
                tankCapacity.multiply(DAILY_LIMIT_MULTIPLIER);

        BigDecimal totalDailyLitres =
                state.getDailyLitres(cardId, transactionTime).add(litres);

        if (totalDailyLitres.compareTo(dailyLimit) > 0) {
            if (transaction.isOffline()) {
                offlineRiskPosted = true;
            } else {
                state.markTransactionProcessed(transactionId);
                return decline(
                        transaction, accountId, "DAILY_LITRE_LIMIT", true);
            }
        }

        // 15. Check monthly litres per vehicle.
        BigDecimal monthlyLimit = BigDecimal.valueOf(
                account.getPerVehicleMonthlyCapLitres());

        BigDecimal totalMonthlyLitres =
                state.getMonthlyLitres(
                        vehicle.getVehicleId(), transactionTime).add(litres);

        if (totalMonthlyLitres.compareTo(monthlyLimit) > 0) {
            if (transaction.isOffline()) {
                offlineRiskPosted = true;
            } else {
                state.markTransactionProcessed(transactionId);
                return decline(
                        transaction, accountId, "MONTHLY_LITRE_LIMIT", true);
            }
        }

        // 16. Validate odometer.
        BigDecimal currentOdometer =
                transaction.getOdometerKm();

        if (currentOdometer == null
                || currentOdometer.compareTo(ZERO) < 0) {

            state.markTransactionProcessed(transactionId);

            return decline(
                    transaction,
                    accountId,
                    "INVALID_ODOMETER",
                    true);
        }

        BigDecimal previousOdometer =
                state.getPreviousOdometer(
                        vehicle.getVehicleId(), transactionTime);

        boolean mileageFlag = false;

        // Mileage checks apply to PETROL and DIESEL.
        if (previousOdometer != null
                && (product == FuelProduct.PETROL
                || product == FuelProduct.DIESEL)) {

            if (currentOdometer.compareTo(previousOdometer) <= 0) {
                state.markTransactionProcessed(transactionId);

                return decline(
                        transaction,
                        accountId,
                        "ODOMETER_NOT_INCREASING",
                        true);
            }

            BigDecimal distance =
                    currentOdometer.subtract(previousOdometer);

            BigDecimal mileage = distance.divide(
                    litres, 6, RoundingMode.HALF_UP);

            if (mileage.compareTo(MIN_MILEAGE) < 0
                    || mileage.compareTo(MAX_MILEAGE) > 0) {
                mileageFlag = true;
            }
        } else if (previousOdometer != null
                && currentOdometer.compareTo(previousOdometer) <= 0) {

            state.markTransactionProcessed(transactionId);

            return decline(
                    transaction,
                    accountId,
                    "ODOMETER_NOT_INCREASING",
                    true);
        }

        // 17. Check credit limit.
        state.updateCreditLimit(account, date);

        BigDecimal currentExposure =
                state.getCreditExposure(accountId);

        BigDecimal newExposure =
                currentExposure.add(engineAmount);

        BigDecimal allowedCredit = account.getCreditLimit();

        boolean nightTime =
                transactionTime.getHour() >= 22
                || transactionTime.getHour() < 6;

        if (product == FuelProduct.DIESEL && nightTime) {
            allowedCredit = allowedCredit.multiply(
                    OVERDRAFT_MULTIPLIER);
        }

        if (newExposure.compareTo(allowedCredit) > 0) {
            if (transaction.isOffline()) {
                offlineRiskPosted = true;
            } else {
                state.markTransactionProcessed(transactionId);
                return decline(
                        transaction, accountId, "CREDIT_LIMIT_EXCEEDED", true);
            }
        }

        // 18. Compare pump amount with engine amount.
        BigDecimal pumpAmount = transaction.getPumpAmount();

        if (pumpAmount == null || pumpAmount.compareTo(ZERO) < 0) {
            state.markTransactionProcessed(transactionId);

            return decline(
                    transaction,
                    accountId,
                    "INVALID_PUMP_AMOUNT",
                    true);
        }

        boolean varianceFlag = pumpAmount
                .subtract(engineAmount)
                .abs()
                .compareTo(ONE_RUPEE) > 0;

        // 19. Approve and update the engine records.
        state.markTransactionProcessed(transactionId);

        state.recordDailyUsage(cardId, transactionTime, litres);

        state.recordMonthlyLitres(
                vehicle.getVehicleId(), transactionTime, litres);

        state.updateOdometer(
                vehicle.getVehicleId(), transactionTime, currentOdometer);

        state.addUnbilledAmount(accountId, engineAmount);

        if (offlineRiskPosted) {
            state.addOutletRiskAmount(
                    transaction.getOutletId(), engineAmount);
        }

        String reasonCode = offlineRiskPosted ? "OFFLINE_RISK_POSTED" : "APPROVED";

        if (!offlineRiskPosted && mileageFlag && varianceFlag) {
            reasonCode = "MILEAGE_AND_PRICE_VARIANCE";
        } else if (!offlineRiskPosted && mileageFlag) {
            reasonCode = "MILEAGE_OUT_OF_RANGE";
        } else if (!offlineRiskPosted && varianceFlag) {
            reasonCode = "PUMP_AMOUNT_VARIANCE";
        }

        return createDecision(
                transaction,
                accountId,
                engineAmount,
                (mileageFlag || offlineRiskPosted) ? "APPROVED_FLAGGED" : "APPROVE",
                reasonCode,
                mileageFlag,
                varianceFlag,
                offlineLate);
    }

    private TransactionDecision processLube(
            Transaction transaction,
            FleetAccount account,
            Vehicle vehicle,
            BigDecimal engineAmount,
            boolean offlineLate) {

        String accountId = account.getAccountId();
        boolean offlineRiskPosted = false;

        BigDecimal currentSpend =
                state.getMonthlyLubeSpend(
                        accountId, transaction.getTransactionTimestamp());

        BigDecimal newSpend =
                currentSpend.add(engineAmount);

        if (newSpend.compareTo(LUBE_MONTHLY_LIMIT) > 0) {
            if (transaction.isOffline()) {
                offlineRiskPosted = true;
            } else {
                state.markTransactionProcessed(
                        transaction.getTransactionId());
                return decline(transaction, accountId, "LUBE_CAP", true);
            }
        }

        // Check the odometer after the LUBE cap so the cap rule has
        // deterministic priority when both conditions fail.
        BigDecimal currentOdometer = transaction.getOdometerKm();
        BigDecimal previousOdometer = state.getPreviousOdometer(
                vehicle.getVehicleId(),
                transaction.getTransactionTimestamp());

        if (currentOdometer == null
                || currentOdometer.compareTo(ZERO) < 0) {
            state.markTransactionProcessed(transaction.getTransactionId());
            return decline(
                    transaction, accountId, "INVALID_ODOMETER", true);
        }

        if (previousOdometer != null
                && currentOdometer.compareTo(previousOdometer) <= 0) {
            state.markTransactionProcessed(transaction.getTransactionId());
            return decline(
                    transaction, accountId, "ODOMETER_NOT_INCREASING", true);
        }

        state.updateCreditLimit(
                account, transaction.getTransactionTimestamp().toLocalDate());

        BigDecimal newExposure =
                state.getCreditExposure(accountId)
                        .add(engineAmount);

        if (newExposure.compareTo(account.getCreditLimit()) > 0) {
            if (transaction.isOffline()) {
                offlineRiskPosted = true;
            } else {
                state.markTransactionProcessed(
                        transaction.getTransactionId());
                return decline(
                        transaction, accountId, "CREDIT_LIMIT_EXCEEDED", true);
            }
        }

        BigDecimal pumpAmount = transaction.getPumpAmount();
        if (pumpAmount == null || pumpAmount.compareTo(ZERO) < 0) {
            state.markTransactionProcessed(transaction.getTransactionId());
            return decline(
                    transaction, accountId, "INVALID_PUMP_AMOUNT", true);
        }

        boolean varianceFlag = pumpAmount
                .subtract(engineAmount)
                .abs()
                .compareTo(ONE_RUPEE) > 0;

        state.markTransactionProcessed(transaction.getTransactionId());
        state.recordLubeSpend(
                accountId, transaction.getTransactionTimestamp(), engineAmount);
        state.addUnbilledAmount(accountId, engineAmount);
        state.updateOdometer(
                vehicle.getVehicleId(),
                transaction.getTransactionTimestamp(),
                transaction.getOdometerKm());

        if (offlineRiskPosted) {
            state.addOutletRiskAmount(
                    transaction.getOutletId(), engineAmount);
        }

        String reasonCode = offlineRiskPosted
                ? "OFFLINE_RISK_POSTED"
                : (varianceFlag ? "PUMP_AMOUNT_VARIANCE" : "APPROVED");

        return createDecision(
                transaction,
                accountId,
                engineAmount,
                offlineRiskPosted || varianceFlag
                        ? "APPROVED_FLAGGED" : "APPROVE",
                reasonCode,
                false,
                varianceFlag,
                offlineLate);
    }

    private TransactionDecision decline(
            Transaction transaction,
            String accountId,
            String reason,
            boolean countTowardAutoBlock) {

        if (countTowardAutoBlock
                && transaction.getTransactionTimestamp() != null) {

            int declineCount = state.recordDecline(
                    transaction.getCardId(),
                    transaction.getTransactionTimestamp().toLocalDate());

            if (declineCount >= 3) {
                context.findCard(transaction.getCardId())
                        .ifPresent(card ->
                                card.setStatus(CardStatus.BLOCKED));
            }
        }

        return createDecision(
                transaction,
                accountId,
                null,
                "DECLINE",
                reason,
                false,
                false,
                transaction.isOffline());
    }

    private TransactionDecision createDecision(
            Transaction transaction,
            String accountId,
            BigDecimal engineAmount,
            String decision,
            String reason,
            boolean mileageFlag,
            boolean varianceFlag,
            boolean offlineLate) {

        String timestamp = transaction.getTransactionTimestamp() == null
                ? ""
                : transaction.getTransactionTimestamp().toString();

        TransactionDecision result = new TransactionDecision(
                transaction.getTransactionId(),
                transaction.getCardId(),
                accountId,
                transaction.getOutletId(),
                timestamp,
                transaction.getProduct(),
                transaction.getLitres(),
                engineAmount,
                decision,
                reason,
                mileageFlag,
                varianceFlag,
                offlineLate
        );

        state.addDecision(result);
        return result;
    }
}