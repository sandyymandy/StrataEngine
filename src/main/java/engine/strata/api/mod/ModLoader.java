package engine.strata.api.mod;

import com.google.gson.Gson;
import engine.strata.core.StrataVersion;
import engine.strata.core.entrypoint.EntrypointManager;
import engine.strata.core.io.FolderManager;
import engine.strata.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStreamReader;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Stream;

public class ModLoader {
    private static final Gson GSON = new Gson();
    public static final Logger LOGGER = LoggerFactory.getLogger("ModLoader");
    private static final Map<String, ClassLoader> MOD_LOADERS = new HashMap<>();

    public static void loadMods() {
        discoverInClasspath();
        discoverInModsFolder();
    }

    // Allows ResourceManager to find assets within a specific mod's JAR
    public static ClassLoader getClassLoaderFor(String namespace) {
        return MOD_LOADERS.getOrDefault(namespace, ModLoader.class.getClassLoader());
    }

    private static void discoverInClasspath() {
        try {
            Enumeration<URL> resources = ModLoader.class.getClassLoader().getResources("strata.mod.json");
            while (resources.hasMoreElements()) {
                parseAndRegister(resources.nextElement(), ModLoader.class.getClassLoader());
            }
        } catch (Exception e) {
            LOGGER.error("Error scanning classpath for mods", e);
        }
    }

    private static void discoverInModsFolder() {
        Path modsDir = FolderManager.getModsDir();
        if (Files.notExists(modsDir)) return;

        try (Stream<Path> files = Files.list(modsDir)) {
            files.filter(p -> p.toString().endsWith(".jar")).forEach(jarPath -> {
                try {
                    URL jarUrl = jarPath.toUri().toURL();
                    // We parent this to the ModLoader's classloader so mods can see Engine classes
                    URLClassLoader classLoader = new URLClassLoader(new URL[]{jarUrl}, ModLoader.class.getClassLoader());

                    URL jsonUrl = classLoader.findResource("strata.mod.json");
                    if (jsonUrl != null) {
                        parseAndRegister(jsonUrl, classLoader);
                    }
                } catch (Exception e) {
                    LOGGER.error("Failed to load mod jar: " + jarPath);
                }
            });
        } catch (Exception e) {
            LOGGER.error("Error scanning mods folder", e);
        }
    }

    private static void parseAndRegister(URL url, ClassLoader classLoader) {
        try (InputStreamReader reader = new InputStreamReader(url.openStream())) {
            ModMetadata meta = GSON.fromJson(reader, ModMetadata.class);

            // 1. Compatibility Check
            if (!StrataVersion.isCompatible(Integer.parseInt(meta.api_version()))) {
                LOGGER.error("Skipping mod [{}]: Incompatible API version", meta.namespace());
                return;
            }

            // 2. Register ClassLoader for Resource Access
            MOD_LOADERS.put(meta.namespace(), classLoader);

            LOGGER.info("Loaded Mod: {} ({}) v{}", meta.name(), meta.namespace(), meta.version());

            // 3. Register Entrypoints
            if (meta.entrypoints() != null) {
                meta.entrypoints().forEach((type, classes) -> {
                    for (String className : classes) {
                        // Pass the 'type' (e.g., "common") directly
                        EntrypointManager.load(type, className, classLoader);
                    }
                });
            }
        } catch (Exception e) {
            LOGGER.error("Failed to parse strata.mod.json at " + url, e);
        }
    }
}