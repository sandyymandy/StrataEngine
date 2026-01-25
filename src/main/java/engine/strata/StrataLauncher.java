package engine.strata;

import engine.strata.api.ClientModInitializer;
import engine.strata.api.ModInitializer;
import engine.strata.api.mod.ModLoader;
import engine.strata.client.StrataClient;
import engine.strata.core.entrypoint.EntrypointManager;
import engine.strata.core.io.FolderManager;
import engine.strata.server.StrataServer;
import engine.strata.util.StrataPaths;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.nio.file.Paths;

public class StrataLauncher {
    public static Logger LOGGER = LoggerFactory.getLogger("StrataLauncher");

    public static void main(String[] args) {
        boolean isServer = false;
        Path runDir = null;

        for (int i = 0; i < args.length; i++) {
            if (args[i].equalsIgnoreCase("--server")) isServer = true;
            if (args[i].equalsIgnoreCase("--runDir") && i + 1 < args.length) {
                runDir = Paths.get(args[i + 1]);
            }
        }

        // Fallback to AppData if no arg provided
        if (runDir == null) {
            runDir = StrataPaths.getDefaultWorkDir();
        }

        FolderManager.setup(runDir);

        LOGGER.info("Run directory set to: " + FolderManager.getRootDir());

        ModLoader.loadMods();

        EntrypointManager.invoke("common", ModInitializer.class, ModInitializer::onInitialize);

        if (isServer) {
            launchServer();
        } else {
            // 3. Initialize Client Logic
            EntrypointManager.invoke("client", ClientModInitializer.class, ClientModInitializer::onClientInitialize);
            launchClient();
        }
    }

    private static void launchClient() {
        // Here we could use Reflection to load a specific ClientEntrypoint class
        StrataClient client = new StrataClient();
        client.run(); // This starts your game loop
    }

    private static void launchServer() {
        StrataServer server = new StrataServer();
        server.init();
        server.run();
    }
}