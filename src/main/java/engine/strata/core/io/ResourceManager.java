package engine.strata.core.io;

import engine.strata.api.mod.ModLoader;
import engine.strata.util.Identifier;
import java.io.InputStream;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.stream.Collectors;

public class ResourceManager {

    /**
     * Finds a file using an Identifier.
     * Conversion: strata:shaders/vertex -> /assets/strata/shaders/vertex.glsl
     */
    public static InputStream getResourceStream(Identifier id, String folder, String extension) {
        String fullPath = id.toAssetPath(folder, extension);

        // Try to get the stream from the specific mod's classloader
        // If the modloader doesn't have a specific loader, it falls back to System
        ClassLoader loader = ModLoader.getClassLoaderFor(id.namespace);
        InputStream is = loader.getResourceAsStream(fullPath);

        if (is == null) {
            // Fallback: Check the Engine's classloader
            // (Allows mods to use engine assets easily)
            is = ResourceManager.class.getResourceAsStream(fullPath);
        }

        return is;
    }

    public static String loadAsString(Identifier id, String folder, String extension) {
        try (InputStream is = getResourceStream(id, folder, extension)) {
            if (is == null) return "";
            return new BufferedReader(new InputStreamReader(is))
                    .lines().collect(Collectors.joining("\n"));
        } catch (Exception e) {
            return "";
        }
    }
}