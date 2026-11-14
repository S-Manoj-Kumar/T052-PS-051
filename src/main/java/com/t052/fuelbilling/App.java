
package com.t052.fuelbilling;

import com.t052.fuelbilling.command.CommandFileParser;
import com.t052.fuelbilling.command.ProjectCommand;
import com.t052.fuelbilling.engine.CommandExecutor;
import com.t052.fuelbilling.engine.EngineContext;
import com.t052.fuelbilling.engine.EngineState;
import com.t052.fuelbilling.repository.DataLoader;
import com.t052.fuelbilling.repository.LoadedData;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public class App {

    public static void main(String[] args) {

        // The application needs two input paths.
        if (args.length != 2) {
            System.out.println("Usage:");
            System.out.println(
                    "java -jar fuel-billing-engine.jar "
                    + "<data-directory> <commands-file>"
            );
            return;
        }

        try {
            // 1. Read the input paths.
            Path dataDirectory = Path.of(args[0]);
            Path commandFile = Path.of(args[1]);

            // 2. Check whether the required paths exist.
            if (!Files.isDirectory(dataDirectory)) {
                throw new IllegalArgumentException(
                        "Data directory not found: " + dataDirectory
                );
            }

            if (!Files.isRegularFile(commandFile)) {
                throw new IllegalArgumentException(
                        "Command file not found: " + commandFile
                );
            }

            System.out.println("======================================");
            System.out.println(" FUEL STATION FLEET CARD BILLING ENGINE");
            System.out.println("======================================");

            // 3. Load accounts, vehicles, cards and outlets.
            DataLoader dataLoader = new DataLoader();

            LoadedData loadedData =
                    dataLoader.loadMasterData(dataDirectory);

            System.out.println(
                    "Accounts loaded: " + loadedData.getAccounts().size()
            );

            System.out.println(
                    "Vehicles loaded: " + loadedData.getVehicles().size()
            );

            System.out.println(
                    "Cards loaded: " + loadedData.getFuelCards().size()
            );

            System.out.println(
                    "Outlets loaded: " + loadedData.getOutlets().size()
            );

            // 4. Create the shared application context and state.
            EngineContext context = new EngineContext(loadedData);
            EngineState state = new EngineState();

            // 5. Read the command file.
            CommandFileParser parser = new CommandFileParser();

            List<ProjectCommand> commands =
                    parser.parse(commandFile);

            System.out.println(
                    "Commands loaded: " + commands.size()
            );

            // 6. Execute the commands in file order.
            CommandExecutor executor = new CommandExecutor(
                    context,
                    state,
                    dataDirectory
            );

            System.out.println();
            System.out.println("Starting command execution...");
            System.out.println("--------------------------------------");

            executor.execute(commands);

            System.out.println("--------------------------------------");
            System.out.println("Command execution completed.");

        } catch (Exception exception) {

            System.err.println();
            System.err.println("APPLICATION ERROR:");
            System.err.println(exception.getMessage());

            System.exit(1);
        }
    }
}
