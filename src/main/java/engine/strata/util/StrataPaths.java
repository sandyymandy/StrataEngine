package engine.strata.util;

import java.nio.file.Path;
import java.nio.file.Paths;

public class StrataPaths {
    public static Path getDefaultWorkDir() {
        String userHome = System.getProperty("user.home");
        String os = System.getProperty("os.name").toLowerCase();

        if (os.contains("win")) {
            // Windows: %APPDATA%/.strataengine
            String appData = System.getenv("APPDATA");
            return Paths.get(appData, ".strataengine");
        } else if (os.contains("mac")) {
            // macOS: ~/Library/Application Support/strataengine
            return Paths.get(userHome, "Library", "Application Support", "strataengine");
        } else {
            // Linux/Unix: ~/.strataengine
            return Paths.get(userHome, ".strataengine");
        }
    }
}