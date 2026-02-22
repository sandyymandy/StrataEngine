package engine.strata.core.io;

import engine.strata.core.resource.ResourcePackManager;
import engine.strata.util.Identifier;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.stream.Collectors;

/**
 * Central resource manager that integrates with the resource pack system.
 * Now supports resource pack overrides and priority loading.
 */
public class ResourceManager {

    private static ResourcePackManager packManager;

    /**
     * Initializes the resource manager with a pack manager.
     * Call this during engine initialization.
     */
    public static void initialize(ResourcePackManager manager) {
        packManager = manager;
    }

    /**
     * Gets a resource pack manager.
     */
    public static ResourcePackManager getPackManager() {
        return packManager;
    }

    /**
     * Finds a file using an Identifier.
     * Now respects resource pack priorities.
     *
     * Conversion: strata:shaders/vertex -> /assets/strata/shaders/vertex.glsl
     */
    public static InputStream getResourceStream(Identifier id, String folder, String extension) {
        if (packManager != null) {
            // Try pack manager first (respects priorities)
            InputStream is = packManager.getResource(id, folder, extension);
            if (is != null) {
                return is;
            }
        }

        // Fallback to old behavior
        return getResourceStreamLegacy(id, folder, extension);
    }

    /**
     * Legacy resource loading (direct from classpath).
     * Used as fallback when pack manager is not initialized.
     */
    private static InputStream getResourceStreamLegacy(Identifier id, String folder, String extension) {
        String fullPath = id.toAssetPath(folder, extension);

        // Try engine resources
        InputStream is = ResourceManager.class.getResourceAsStream("/" + fullPath);
        if (is != null) {
            return is;
        }

        // Try system classloader
        return ClassLoader.getSystemResourceAsStream(fullPath);
    }

    /**
     * Loads a resource as a string.
     */
    public static String loadAsString(Identifier id, String folder, String extension) {
        try (InputStream is = getResourceStream(id, folder, extension)) {
            if (is == null) return "";
            return new BufferedReader(new InputStreamReader(is))
                    .lines().collect(Collectors.joining("\n"));
        } catch (Exception e) {
            return "";
        }
    }

    /**
     * Checks if a resource exists.
     */
    public static boolean resourceExists(Identifier id, String folder, String extension) {
        try (InputStream is = getResourceStream(id, folder, extension)) {
            return is != null;
        } catch (Exception e) {
            return false;
        }
    }
}