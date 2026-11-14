package com.t052.fuelbilling.command;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class CommandFileParser {

    public List<ProjectCommand> parse(Path commandFile)
            throws IOException {

        List<String> lines = Files.readAllLines(
            commandFile,
            StandardCharsets.UTF_8
        );

        List<ProjectCommand> commands = new ArrayList<>();

        for (int i = 0; i < lines.size(); i++) {

            int lineNumber = i + 1;
            String line = lines.get(i).trim();

            // Ignore blank lines and comments.
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }

            String[] parts = line.split("\\|", -1);

            String commandName = parts[0].trim();

            CommandType commandType;

            try {
                commandType =
                    CommandType.fromString(commandName);

            } catch (IllegalArgumentException exception) {

                throw new CommandParseException(
                    lineNumber,
                    exception.getMessage()
                );
            }

            int actualArguments = parts.length - 1;
            int expectedArguments =
                commandType.getExpectedArguments();

            if (actualArguments != expectedArguments) {

                throw new CommandParseException(
                    lineNumber,
                    "Command " + commandName
                    + " expects " + expectedArguments
                    + " arguments but received "
                    + actualArguments
                );
            }

            List<String> arguments = new ArrayList<>();

            for (int j = 1; j < parts.length; j++) {

                String argument = parts[j].trim();

                if (argument.isEmpty()) {

                    throw new CommandParseException(
                        lineNumber,
                        "Argument " + j
                        + " cannot be empty"
                    );
                }

                arguments.add(argument);
            }

            commands.add(
                new ProjectCommand(
                    commandType,
                    arguments,
                    lineNumber
                )
            );
        }

        return List.copyOf(commands);
    }
}