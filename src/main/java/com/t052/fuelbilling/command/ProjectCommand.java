package com.t052.fuelbilling.command;

import java.util.List;

public class ProjectCommand {

    private final CommandType type;
    private final List<String> arguments;
    private final int lineNumber;

    public ProjectCommand(
            CommandType type,
            List<String> arguments,
            int lineNumber) {

        this.type = type;
        this.arguments = List.copyOf(arguments);
        this.lineNumber = lineNumber;
    }

    public CommandType getType() {
        return type;
    }

    public List<String> getArguments() {
        return arguments;
    }

    public int getLineNumber() {
        return lineNumber;
    }

    public String getArgument(int index) {
        return arguments.get(index);
    }
}