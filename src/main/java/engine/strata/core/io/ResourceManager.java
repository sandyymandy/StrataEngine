package engine.strata.core.io;

import engine.strata.core.resource.ResourcePackManager;
import engine.strata.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.stream.Collectors;

/**
 * Central resource manager that integrates with the resource pack system.
 * Supports resource pack overrides, priority loading, and binary data.
 */
public class ResourceManager {

    public static final Logger LOGGER = LoggerFactory.getLogger("ResourceManager");
    private static ResourcePackManager packManager;

    /**
     * Initializes the resource manager with a pack manager.
     */
    public static void initialize(ResourcePackManager manager) {
        packManager = manager;
    }

    public static ResourcePackManager getPackManager() {
        return packManager;
    }

    /**
     * Finds a file using an Identifier, respecting pack priorities.
     */
    public static InputStream getResourceStream(Identifier id, String folder, String extension) {
        if (packManager != null) {
            InputStream is = packManager.getResource(id, folder, extension);
            if (is != null) {
                return is;
            }
        }
        return getResourceStreamLegacy(id, folder, extension);
    }

    /**
     * Fallback direct classpath loading.
     */
    private static InputStream getResourceStreamLegacy(Identifier id, String folder, String extension) {
        String fullPath = id.toAssetPath(folder, extension);
        InputStream is = ResourceManager.class.getResourceAsStream("/" + fullPath);
        if (is != null) return is;

        return ClassLoader.getSystemResourceAsStream(fullPath);
    }

    /**
     * Loads a resource as a byte array.
     */
    public static byte[] loadAsBytes(Identifier id, String folder, String extension) {
        try (InputStream is = getResourceStream(id, folder, extension)) {
            if (is == null) {
                LOGGER.error("Resource not found: {} (Folder: {}, Extension: {})", id, folder, extension);
                return new byte[0];
            }
            return is.readAllBytes();
        } catch (IOException e) {
            LOGGER.error("Failed to read resource bytes for {}: {}", id, e.getMessage(), e);
            return new byte[0];
        }
    }

    /**
     * Loads a resource as a string.
     */
    public static String loadAsString(Identifier id, String folder, String extension) {
        try (InputStream is = getResourceStream(id, folder, extension)) {
            if (is == null) {
                LOGGER.error("Resource not found: {} (Folder: {}, Extension: {})", id, folder, extension);
                return "";
            }
            return new BufferedReader(new InputStreamReader(is))
                    .lines().collect(Collectors.joining("\n"));
        } catch (IOException e) {
            LOGGER.error("Failed to read resource string for {}: {}", id, e.getMessage(), e);
            return "";
        }
    }

    /**
     * Checks if a resource exists.[cite: 7]
     */
    public static boolean resourceExists(Identifier id, String folder, String extension) {
        try (InputStream is = getResourceStream(id, folder, extension)) {
            return is != null;
        } catch (IOException e) {
            // Existence check failing usually isn't an error, just return false
            return false;
        }
    }
}