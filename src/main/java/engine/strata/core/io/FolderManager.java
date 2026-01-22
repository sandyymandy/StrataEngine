package engine.strata.core.io;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class FolderManager {
    private static Path rootDir;

    public static void setup(Path path) {
        rootDir = path.toAbsolutePath().normalize();

        // Define sub-folders
        try {
            ensureDirectory(rootDir);
            ensureDirectory(rootDir.resolve("mods"));
            ensureDirectory(rootDir.resolve("saves"));
            ensureDirectory(rootDir.resolve("logs"));
            ensureDirectory(rootDir.resolve("config"));
        } catch (IOException e) {
            throw new RuntimeException("Failed to initialize engine directories!", e);
        }
    }

    private static void ensureDirectory(Path path) throws IOException {
        if (Files.notExists(path)) {
            Files.createDirectories(path);
        }
    }

    public static Path getRootDir() { return rootDir; }
    public static Path getModsDir() { return rootDir.resolve("mods"); }
    public static Path getSavesDir() { return rootDir.resolve("saves"); }
    public static Path getLogsDir() { return rootDir.resolve("logs"); }
    public static Path getConfigDir() { return rootDir.resolve("config"); }
}