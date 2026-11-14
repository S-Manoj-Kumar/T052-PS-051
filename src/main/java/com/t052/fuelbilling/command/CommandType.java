package com.t052.fuelbilling.command;

public enum CommandType {
    LOAD_PRICES(1),
    TRIP(4),
    POST_TRANSACTIONS(3),
    BLOCK_CARD(3),
    UNBLOCK_CARD(2),
    REPORT_LOST(2),
    RUN_INVOICE(1),
    APPLY_PAYMENTS(1),
    PRINT_DECISION_LOG(0),
    PRINT_STATEMENT(1),
    PRINT_MISUSE_REPORT(2);

    private final int expectedArguments;

    CommandType(int expectedArguments) {
        this.expectedArguments = expectedArguments;
    }

    public int getExpectedArguments() {
        return expectedArguments;
    }

    public static CommandType fromString(String value) {
        try {
            return CommandType.valueOf(value.trim());
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException(
                "Unknown command: " + value
            );
        }
    }
}