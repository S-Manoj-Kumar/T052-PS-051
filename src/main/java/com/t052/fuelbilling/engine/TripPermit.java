
package com.t052.fuelbilling.engine;

import java.time.LocalDateTime;

public class TripPermit {

    private final String cardId;
    private final String state;
    private final LocalDateTime validFrom;
    private final LocalDateTime validUntil;

    public TripPermit(
            String cardId,
            String state,
            LocalDateTime validFrom,
            LocalDateTime validUntil) {

        if (cardId == null || cardId.isBlank()) {
            throw new IllegalArgumentException(
                "Card ID cannot be empty"
            );
        }

        if (state == null || state.isBlank()) {
            throw new IllegalArgumentException(
                "State cannot be empty"
            );
        }

        if (validFrom == null || validUntil == null
                || validUntil.isBefore(validFrom)) {
            throw new IllegalArgumentException(
                "Invalid trip permission dates"
            );
        }

        this.cardId = cardId;
        this.state = state.trim();
        this.validFrom = validFrom;
        this.validUntil = validUntil;
    }

    public String getCardId() {
        return cardId;
    }

    public String getState() {
        return state;
    }

    public LocalDateTime getValidFrom() {
        return validFrom;
    }

    public LocalDateTime getValidUntil() {
        return validUntil;
    }

    public boolean covers(
        String requestedState,
        LocalDateTime timestamp) {

    if (requestedState == null || timestamp == null) {
        return false;
    }

    return state.equalsIgnoreCase(requestedState.trim())
        && !timestamp.isBefore(validFrom)
        && !timestamp.isAfter(validUntil);
    }
}
