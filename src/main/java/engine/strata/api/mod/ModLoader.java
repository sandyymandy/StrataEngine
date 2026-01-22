package engine.strata.api.mod;

import com.google.gson.Gson;
import engine.strata.core.StrataVersion;
import engine.strata.core.entrypoint.EntrypointManager;
import engine.strata.core.io.FolderManager;
import engine.strata.util.Identifier;

import java.io.InputStreamReader;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Enumeration;
import java.util.List;
import java.util.stream.Stream;

public class ModLoader {
    private static final Gson GSON = new Gson();

    public static void loadMods() {
        // 1. Scan Classpath (Built-in mods/Engine)
        discoverInClasspath();

        // 2. Scan /mods folder (External JARs)
        discoverInModsFolder();
    }

    private static void discoverInClasspath() {
        try {
            Enumeration<URL> resources = ModLoader.class.getClassLoader().getResources("strata.mod.json");
            while (resources.hasMoreElements()) {
                parseAndRegister(resources.nextElement());
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static void discoverInModsFolder() {
        Path modsDir = FolderManager.getModsDir();
        if (Files.notExists(modsDir)) return;

        try (Stream<Path> files = Files.list(modsDir)) {
            files.filter(p -> p.toString().endsWith(".jar")).forEach(jarPath -> {
                try {
                    // Create a classloader for this specific JAR
                    URL jarUrl = jarPath.toUri().toURL();
                    URLClassLoader classLoader = new URLClassLoader(new URL[]{jarUrl}, ModLoader.class.getClassLoader());

                    URL jsonUrl = classLoader.findResource("strata.mod.json");
                    if (jsonUrl != null) {
                        parseAndRegister(jsonUrl, classLoader);
                    }
                } catch (Exception e) {
                    System.err.println("Failed to load mod jar: " + jarPath);
                }
            });
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static void parseAndRegister(URL url) {
        parseAndRegister(url, ModLoader.class.getClassLoader());
    }

    private static void parseAndRegister(URL url, ClassLoader classLoader) {
        try (InputStreamReader reader = new InputStreamReader(url.openStream())) {
            ModMetadata meta = GSON.fromJson(reader, ModMetadata.class);

            // 1. Version Compatibility Check
            int modReqApi = Integer.parseInt(meta.api_version());
            if (!StrataVersion.isCompatible(modReqApi)) {
                System.err.println("Skipping mod [" + meta.id() + "]: Requires API " + modReqApi +
                        " but engine is " + StrataVersion.API_VERSION);
                return;
            }

            System.out.println("Loading Mod: " + meta.name() + " v" + meta.version());

            // ... proceed to register entrypoints ...
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}