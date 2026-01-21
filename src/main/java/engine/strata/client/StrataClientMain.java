package engine.strata.client;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class StrataClientMain {
    private static final Logger LOGGER = LoggerFactory.getLogger("ClientMain");

    public static void main(String[] args) {
        LOGGER.info("Bootstrapping Client");

        // 1. You can parse args here (width, height, username, etc.)
        // For now, we just pass them along or ignore them.

        // 2. Initialize the client
        StrataClient client = new StrataClient();

        // 4. Start the Game Loop
        try {
            client.init(); // Initialize Window & bgfx
            client.run();  // Enter the Infinite Loop
        } catch (Exception e) {
            e.printStackTrace();
            System.exit(-1);
        }
    }
}