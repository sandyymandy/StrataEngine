package engine.strata;

import engine.strata.api.ClientModInitializer;
import engine.strata.api.ModInitializer;
import engine.strata.client.StrataClient;
import engine.strata.core.entrypoint.EntrypointManager;
import engine.strata.server.StrataServer;
import engine.strata.util.Identifier;

public class StrataLauncher {
    public static void main(String[] args) {
        boolean isServer = false;
        for (String arg : args) if (arg.equalsIgnoreCase("--server")) isServer = true;

        // 1. Discovery (The Engine is its own mod!)
        Identifier engineId = Identifier.ofEngine("engine");

        // Load the Engine's Core Logic
        EntrypointManager.load(engineId, "engine.strata.core.StrataCore", ModInitializer.class);

        if (!isServer) {
            // Load the Engine's Renderer/Window Logic
            EntrypointManager.load(engineId, "engine.strata.client.StrataClient", ClientModInitializer.class);
        }

        // 2. Initialize Common Logic (Both Side)
        EntrypointManager.invoke(ModInitializer.class, ModInitializer::onInitialize);

        if (isServer) {
            launchServer();
        } else {
            // 3. Initialize Client Logic
            EntrypointManager.invoke(ClientModInitializer.class, ClientModInitializer::onClientInitialize);
            launchClient();
        }
    }

    private static void launchClient() {
        // Here we could use Reflection to load a specific ClientEntrypoint class
        StrataClient client = new StrataClient();
        client.init();
        client.run(); // This starts your game loop
    }

    private static void launchServer() {
        StrataServer server = new StrataServer();
        server.init();
        server.run();
    }
}