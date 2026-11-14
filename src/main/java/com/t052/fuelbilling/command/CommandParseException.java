package com.t052.fuelbilling.command;

import java.io.IOException;

public class CommandParseException extends IOException {

    public CommandParseException(
            int lineNumber,
            String message) {

        super("Line " + lineNumber + ": " + message);
    }
}