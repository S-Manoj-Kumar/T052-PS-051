package com.t052.fuelbilling.command;

import org.junit.Test;
import org.junit.Rule;
import org.junit.rules.TemporaryFolder;

import com.t052.fuelbilling.command.CommandFileParser;
import com.t052.fuelbilling.command.CommandParseException;
import com.t052.fuelbilling.command.CommandType;
import com.t052.fuelbilling.command.ProjectCommand;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.Assert.*;

public class CommandFileParserTest {

    @Rule
    public TemporaryFolder temporaryFolder =
        new TemporaryFolder();

    private Path createFile(String content)
            throws Exception {

        Path file = temporaryFolder
            .getRoot()
            .toPath()
            .resolve("commands.txt");

        Files.writeString(file, content);

        return file;
    }

    @Test
    public void parsesValidCommandsInOrder()
            throws Exception {

        Path file = createFile(
            "LOAD_PRICES|prices.csv\n" +
            "RUN_INVOICE|2026-10-16\n" +
            "PRINT_DECISION_LOG\n"
        );

        List<ProjectCommand> commands =
            new CommandFileParser().parse(file);

        assertEquals(3, commands.size());

        assertEquals(
            CommandType.LOAD_PRICES,
            commands.get(0).getType()
        );

        assertEquals(
            CommandType.RUN_INVOICE,
            commands.get(1).getType()
        );

        assertEquals(
            CommandType.PRINT_DECISION_LOG,
            commands.get(2).getType()
        );
    }

    @Test
    public void ignoresCommentsAndBlankLines()
            throws Exception {

        Path file = createFile(
            "# Sample comment\n" +
            "\n" +
            "LOAD_PRICES|prices.csv\n" +
            "   \n" +
            "# Another comment\n"
        );

        List<ProjectCommand> commands =
            new CommandFileParser().parse(file);

        assertEquals(1, commands.size());
        assertEquals(3, commands.get(0).getLineNumber());
    }

    @Test
    public void rejectsUnknownCommand()
            throws Exception {

        Path file = createFile(
            "INVALID_COMMAND|test.csv\n"
        );

        try {
            new CommandFileParser().parse(file);
            fail("Expected parsing exception");

        } catch (CommandParseException exception) {
            assertTrue(exception.getMessage().contains("Line 1"));
        }
    }

    @Test
    public void rejectsIncorrectArgumentCount()
            throws Exception {

        Path file = createFile(
            "LOAD_PRICES\n"
        );

        try {
            new CommandFileParser().parse(file);
            fail("Expected parsing exception");

        } catch (CommandParseException exception) {
            assertTrue(exception.getMessage().contains("Line 1"));
        }
    }

    @Test
    public void rejectsEmptyArguments()
            throws Exception {

        Path file = createFile(
            "LOAD_PRICES|\n"
        );

        try {
            new CommandFileParser().parse(file);
            fail("Expected parsing exception");

        } catch (CommandParseException exception) {
            assertTrue(exception.getMessage().contains("Line 1"));
        }
    }
}