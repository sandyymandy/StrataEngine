package engine.strata;

import engine.strata.client.StrataClientMain;
import engine.strata.server.StrataServerMain;
import java.util.Arrays;

public class StrataLauncher {
    public static void main(String[] args) {
        // Simple argument check, similar to how Minecraft checks for --server
        boolean isServer = false;
        for (String arg : args) {
            if (arg.equalsIgnoreCase("--server")) {
                isServer = true;
                break;
            }
        }

        if (isServer) {
            StrataServerMain.main(args);
        } else {
            StrataClientMain.main(args);
        }
    }
}