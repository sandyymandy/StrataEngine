package engine.strata.core.resource;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import engine.strata.core.io.FolderManager;
import engine.strata.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Stream;

/**
 * Manages resource packs with priority ordering and runtime configuration.
 * Uses Identifier objects for pack identification.
 * Similar to Minecraft's resource pack system.
 *
 * Priority system:
 * - Engine resources: priority 0 (lowest)
 * - Mods: priority 100-999 (based on load order)
 * - Resource packs: priority 1000+ (user-configurable)
 * - Higher priority = loaded later = overrides earlier packs
 */
public class ResourcePackManager {
    private static final Logger LOGGER = LoggerFactory.getLogger("ResourcePacks");

    private static final String CONFIG_FILE = "resourcepacks.json";

    // Thread-safe list maintains insertion order
    // Map: Identifier(packid) -> ResourcePack
    private final Map<Identifier, ResourcePack> packs = new LinkedHashMap<>();

    // Configuration
    private final Path resourcePacksDir;
    private final Path configFile;

    // Cached sorted list (updated when priorities change)
    private volatile List<ResourcePack> sortedPacks = new ArrayList<>();

    public ResourcePackManager() {
        Path root = FolderManager.getRootDir();
        this.resourcePacksDir = root.resolve("resourcepacks");
        this.configFile = FolderManager.getConfigDir().resolve(CONFIG_FILE);

        try {
            Files.createDirectories(resourcePacksDir);
        } catch (IOException e) {
            LOGGER.error("Failed to create resourcepacks directory", e);
        }
    }

    /**
     * Initializes the resource pack system.
     * Call this during engine initialization.
     */
    public void initialize() {
        LOGGER.info("Initializing resource pack system");

        // Load configuration (which packs are enabled, their order, etc.)
        loadConfiguration();

        // Discover resource packs from filesystem
        discoverResourcePacks();

        // Sort packs by priority
        updateSortedPacks();

        LOGGER.info("Resource pack system initialized with {} packs", packs.size());
        logPackOrder();
    }

    /**
     * Registers a built-in pack (engine or mod).
     * @param pack The pack to register
     */
    public void registerBuiltInPack(ResourcePack pack) {
        packs.put(pack.getId(), pack);
        updateSortedPacks();
        LOGGER.debug("Registered built-in pack: {}", pack.getId());
    }

    /**
     * Registers the engine's resources as a pack.
     */
    public void registerEnginePack() {
        Identifier engineId = Identifier.of("strata", "engine");
        ResourcePack enginePack = ResourcePack.builder()
                .id(engineId)
                .name("Strata Engine")
                .description("Built-in engine resources")
                .source(ResourcePack.ResourcePackSource.CLASSPATH)
                .namespace("strata")
                .priority(0) // Lowest priority
                .enabled(true)
                .build();

        registerBuiltInPack(enginePack);
    }

    /**
     * Registers a mod's resources as a pack.
     *
     * @param modIdStr The mod's ID string (will be parsed to Identifier)
     * @param modName The mod's display name
     * @param namespace The mod's namespace
     * @param classLoader The mod's classloader
     * @param priority Priority based on mod load order (100+)
     */
    public void registerModPack(String modIdStr, String modName, String namespace,
                                ClassLoader classLoader, int priority) {
        Identifier modId = Identifier.of(modIdStr);
        registerModPack(modId, modName, namespace, classLoader, priority);
    }

    /**
     * Registers a mod's resources as a pack.
     *
     * @param modId The mod's identifier
     * @param modName The mod's display name
     * @param namespace The mod's namespace
     * @param classLoader The mod's classloader
     * @param priority Priority based on mod load order (100+)
     */
    public void registerModPack(Identifier modId, String modName, String namespace,
                                ClassLoader classLoader, int priority) {
        ResourcePack modPack = ResourcePack.builder()
                .id(modId)
                .name(modName)
                .description("Resources from " + modName)
                .source(ResourcePack.ResourcePackSource.MOD)
                .namespace(namespace)
                .classLoader(classLoader)
                .priority(priority)
                .enabled(true)
                .build();

        registerBuiltInPack(modPack);
    }

    /**
     * Discovers resource packs in the resourcepacks/ folder.
     */
    private void discoverResourcePacks() {
        if (!Files.exists(resourcePacksDir)) {
            return;
        }

        try (Stream<Path> paths = Files.list(resourcePacksDir)) {
            paths.filter(Files::isDirectory)
                    .forEach(this::loadResourcePack);
        } catch (IOException e) {
            LOGGER.error("Failed to discover resource packs", e);
        }
    }

    /**
     * Loads a resource pack from a directory.
     */
    private void loadResourcePack(Path packDir) {
        ResourcePack pack = ResourcePack.fromMetadata(packDir);
        if (pack != null) {
            // Set priority from config or default to 1000+
            applyConfigToPack(pack);
            packs.put(pack.getId(), pack);
            LOGGER.info("Loaded resource pack: {} from {}", pack.getName(), packDir);
        }
    }

    /**
     * Gets a resource from the highest priority pack that has it.
     *
     * @param path The resource path (e.g., "assets/strata/blocks/stone.json")
     * @return InputStream if found, null otherwise
     */
    public java.io.InputStream getResource(String path) {
        // Search packs in reverse order (highest priority first)
        for (int i = sortedPacks.size() - 1; i >= 0; i--) {
            ResourcePack pack = sortedPacks.get(i);
            java.io.InputStream stream = pack.getResource(path);
            if (stream != null) {
                return stream;
            }
        }
        return null;
    }

    /**
     * Gets a resource using an Identifier.
     */
    public java.io.InputStream getResource(Identifier id, String folder, String extension) {
        String path = id.toAssetPath(folder, extension);
        return getResource(path);
    }

    /**
     * Gets all resources matching a pattern from all enabled packs.
     * Useful for discovering all blocks, items, etc.
     *
     * @param pattern The pattern to match (e.g., "assets/blocks/.json")
     * @return List of found resource paths
     */
    public List<String> listResources(String pattern) {
        Set<String> resources = new LinkedHashSet<>();

        // Collect from all packs (duplicates will be deduplicated by Set)
        for (ResourcePack pack : sortedPacks) {
            if (pack.getSource() == ResourcePack.ResourcePackSource.FILE_SYSTEM &&
                    pack.getRootPath() != null) {
                listResourcesFromPath(pack.getRootPath(), pattern, resources);
            }
        }

        return new ArrayList<>(resources);
    }

    private void listResourcesFromPath(Path root, String pattern, Set<String> results) {
        // Convert pattern to regex
        // "assets/*/blocks/*.json" -> assets/.+/blocks/.+\.json
        String regex = pattern
                .replace(".", "\\.")
                .replace("*", ".+");

        try (Stream<Path> paths = Files.walk(root)) {
            paths.filter(Files::isRegularFile)
                    .forEach(path -> {
                        String relativePath = root.relativize(path).toString().replace('\\', '/');
                        if (relativePath.matches(regex)) {
                            results.add(relativePath);
                        }
                    });
        } catch (IOException e) {
            LOGGER.warn("Failed to list resources in: " + root, e);
        }
    }

    /**
     * Gets all registered packs (in priority order).
     */
    public List<ResourcePack> getAllPacks() {
        return new ArrayList<>(sortedPacks);
    }

    /**
     * Gets a pack by ID string.
     * @param idStr The pack ID string (will be parsed to Identifier)
     */
    public ResourcePack getPack(String idStr) {
        Identifier id = Identifier.of(idStr);
        return getPack(id);
    }

    /**
     * Gets a pack by Identifier.
     * @param id The pack identifier
     */
    public ResourcePack getPack(Identifier id) {
        return packs.get(id);
    }

    /**
     * Enables a pack by ID string.
     */
    public void enablePack(String idStr) {
        enablePack(Identifier.of(idStr));
    }

    /**
     * Enables a pack by Identifier.
     */
    public void enablePack(Identifier id) {
        ResourcePack pack = getPack(id);
        if (pack != null) {
            pack.setEnabled(true);
            updateSortedPacks();
            saveConfiguration();
        }
    }

    /**
     * Disables a pack by ID string.
     */
    public void disablePack(String idStr) {
        disablePack(Identifier.of(idStr));
    }

    /**
     * Disables a pack by Identifier.
     */
    public void disablePack(Identifier id) {
        ResourcePack pack = getPack(id);
        if (pack != null) {
            pack.setEnabled(false);
            updateSortedPacks();
            saveConfiguration();
        }
    }

    /**
     * Sets the priority of a pack.
     * Higher priority = loaded later = overrides more.
     */
    public void setPackPriority(String idStr, int priority) {
        setPackPriority(Identifier.of(idStr), priority);
    }

    /**
     * Sets the priority of a pack.
     */
    public void setPackPriority(Identifier id, int priority) {
        ResourcePack pack = getPack(id);
        if (pack != null) {
            pack.setPriority(priority);
            updateSortedPacks();
            saveConfiguration();
            logPackOrder();
        }
    }

    /**
     * Moves a pack up in priority (loaded later).
     */
    public void movePackUp(String idStr) {
        movePackUp(Identifier.of(idStr));
    }

    /**
     * Moves a pack up in priority (loaded later).
     */
    public void movePackUp(Identifier id) {
        ResourcePack pack = getPack(id);
        if (pack != null) {
            pack.setPriority(pack.getPriority() + 1);
            updateSortedPacks();
            saveConfiguration();
            logPackOrder();
        }
    }

    /**
     * Moves a pack down in priority (loaded earlier).
     */
    public void movePackDown(String idStr) {
        movePackDown(Identifier.of(idStr));
    }

    /**
     * Moves a pack down in priority (loaded earlier).
     */
    public void movePackDown(Identifier id) {
        ResourcePack pack = getPack(id);
        if (pack != null) {
            pack.setPriority(Math.max(0, pack.getPriority() - 1));
            updateSortedPacks();
            saveConfiguration();
            logPackOrder();
        }
    }

    /**
     * Updates the sorted pack list based on priorities.
     */
    private void updateSortedPacks() {
        sortedPacks = new ArrayList<>(packs.values());
        sortedPacks.sort(Comparator.comparingInt(ResourcePack::getPriority));
    }

    /**
     * Logs the current pack order.
     */
    private void logPackOrder() {
        LOGGER.info("Resource pack load order:");
        for (ResourcePack pack : sortedPacks) {
            if (pack.isEnabled()) {
                LOGGER.info("  [{}] {} (priority: {})",
                        pack.getSource(), pack.getName(), pack.getPriority());
            }
        }
    }

    // ── Configuration ─────────────────────────────────────────────────────────

    private void loadConfiguration() {
        if (!Files.exists(configFile)) {
            return;
        }

        try {
            String json = Files.readString(configFile);
            Gson gson = new Gson();
            PackConfiguration config = gson.fromJson(json, PackConfiguration.class);

            if (config != null && config.packs != null) {
                LOGGER.info("Loaded resource pack configuration");
            }
        } catch (Exception e) {
            LOGGER.warn("Failed to load resource pack configuration", e);
        }
    }

    private void applyConfigToPack(ResourcePack pack) {
        // Set default priority for user packs
        pack.setPriority(1000 + packs.size());
    }

    private void saveConfiguration() {
        try {
            PackConfiguration config = new PackConfiguration();
            config.packs = new ArrayList<>();

            for (ResourcePack pack : packs.values()) {
                if (pack.getSource() == ResourcePack.ResourcePackSource.FILE_SYSTEM) {
                    PackConfig packConfig = new PackConfig();
                    packConfig.id = pack.getId().toString();
                    packConfig.enabled = pack.isEnabled();
                    packConfig.priority = pack.getPriority();
                    config.packs.add(packConfig);
                }
            }

            Gson gson = new GsonBuilder().setPrettyPrinting().create();
            String json = gson.toJson(config);
            Files.writeString(configFile, json);

            LOGGER.debug("Saved resource pack configuration");
        } catch (Exception e) {
            LOGGER.warn("Failed to save resource pack configuration", e);
        }
    }

    // Configuration classes
    private static class PackConfiguration {
        List<PackConfig> packs;
    }

    private static class PackConfig {
        String id; // Stored as string, parsed to Identifier when loaded
        boolean enabled;
        int priority;
    }
}