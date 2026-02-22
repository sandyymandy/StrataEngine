package engine.strata.core.resource;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import engine.strata.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Represents a resource pack that can provide assets.
 * Uses Identifier for pack identification.
 *
 * Resource packs can be:
 * - Built-in (from engine or mods)
 * - External (from files in resourcepacks/ folder)
 * - Data packs (modify game data like blocks, items)
 *
 * Similar to Minecraft's resource pack system.
 */
public class ResourcePack {
    private static final Logger LOGGER = LoggerFactory.getLogger("ResourcePack");

    private final Identifier id;
    private final String name;
    private final String description;
    private final int formatVersion;
    private final ResourcePackType type;
    private final ResourcePackSource source;

    // For file-based packs
    private final Path rootPath;

    // For classpath-based packs (mods)
    private final ClassLoader classLoader;
    private final String namespace;

    // Configuration
    private boolean enabled;
    private int priority; // Higher = loaded later = takes precedence

    private ResourcePack(Builder builder) {
        this.id = builder.id;
        this.name = builder.name;
        this.description = builder.description;
        this.formatVersion = builder.formatVersion;
        this.type = builder.type;
        this.source = builder.source;
        this.rootPath = builder.rootPath;
        this.classLoader = builder.classLoader;
        this.namespace = builder.namespace;
        this.enabled = builder.enabled;
        this.priority = builder.priority;
    }

    /**
     * Attempts to load a resource from this pack.
     *
     * @param path The resource path (e.g., "assets/strata/blocks/stone.json")
     * @return InputStream if found, null otherwise
     */
    public InputStream getResource(String path) {
        if (!enabled) {
            return null;
        }

        try {
            switch (source) {
                case FILE_SYSTEM:
                    return getResourceFromFile(path);
                case CLASSPATH:
                    return getResourceFromClasspath(path);
                case MOD:
                    return getResourceFromMod(path);
                default:
                    return null;
            }
        } catch (Exception e) {
            LOGGER.debug("Failed to load resource {} from pack {}: {}", path, id, e.getMessage());
            return null;
        }
    }

    /**
     * Gets a resource using an Identifier.
     *
     * @param id The identifier
     * @param folder The folder (e.g., "blocks", "textures/block")
     * @param extension The file extension (e.g., "json", "png")
     * @return InputStream if found, null otherwise
     */
    public InputStream getResource(Identifier id, String folder, String extension) {
        String path = id.toAssetPath(folder, extension);
        return getResource(path);
    }

    private InputStream getResourceFromFile(String path) throws IOException {
        Path filePath = rootPath.resolve(path);
        if (Files.exists(filePath)) {
            return Files.newInputStream(filePath);
        }
        return null;
    }

    private InputStream getResourceFromClasspath(String path) {
        if (classLoader != null) {
            return classLoader.getResourceAsStream(path);
        }
        return ResourcePack.class.getResourceAsStream("/" + path);
    }

    private InputStream getResourceFromMod(String path) {
        // For mod-based packs, we need to ensure we're loading from the right namespace
        if (namespace != null && !path.contains("assets/" + namespace)) {
            return null; // This pack doesn't own this namespace
        }

        if (classLoader != null) {
            return classLoader.getResourceAsStream(path);
        }
        return null;
    }

    /**
     * Checks if this pack can provide resources for a given namespace.
     */
    public boolean ownsNamespace(String namespace) {
        if (this.namespace != null) {
            return this.namespace.equals(namespace);
        }
        // File-based packs can provide any namespace
        return source == ResourcePackSource.FILE_SYSTEM;
    }

    // Getters and setters

    public Identifier getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public int getFormatVersion() {
        return formatVersion;
    }

    public ResourcePackType getType() {
        return type;
    }

    public ResourcePackSource getSource() {
        return source;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
        LOGGER.info("Resource pack {} is now {}", id, enabled ? "enabled" : "disabled");
    }

    public int getPriority() {
        return priority;
    }

    public void setPriority(int priority) {
        this.priority = priority;
    }

    public Path getRootPath() {
        return rootPath;
    }

    @Override
    public String toString() {
        return "ResourcePack{" +
                "id=" + id +
                ", name='" + name + '\'' +
                ", enabled=" + enabled +
                ", priority=" + priority +
                ", source=" + source +
                '}';
    }

    // Builder

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private Identifier id;
        private String name;
        private String description = "";
        private int formatVersion = 1;
        private ResourcePackType type = ResourcePackType.RESOURCES;
        private ResourcePackSource source;
        private Path rootPath;
        private ClassLoader classLoader;
        private String namespace;
        private boolean enabled = true;
        private int priority = 0;

        public Builder id(Identifier id) {
            this.id = id;
            return this;
        }

        public Builder id(String idStr) {
            this.id = Identifier.of(idStr);
            return this;
        }

        public Builder name(String name) {
            this.name = name;
            return this;
        }

        public Builder description(String description) {
            this.description = description;
            return this;
        }

        public Builder formatVersion(int version) {
            this.formatVersion = version;
            return this;
        }

        public Builder type(ResourcePackType type) {
            this.type = type;
            return this;
        }

        public Builder source(ResourcePackSource source) {
            this.source = source;
            return this;
        }

        public Builder rootPath(Path path) {
            this.rootPath = path;
            return this;
        }

        public Builder classLoader(ClassLoader loader) {
            this.classLoader = loader;
            return this;
        }

        public Builder namespace(String namespace) {
            this.namespace = namespace;
            return this;
        }

        public Builder enabled(boolean enabled) {
            this.enabled = enabled;
            return this;
        }

        public Builder priority(int priority) {
            this.priority = priority;
            return this;
        }

        public ResourcePack build() {
            if (id == null || name == null || source == null) {
                throw new IllegalStateException("id, name, and source are required");
            }
            return new ResourcePack(this);
        }
    }

    // Enums

    public enum ResourcePackType {
        /** Standard resource pack (textures, models, etc.) */
        RESOURCES,
        /** Data pack (blocks, items, recipes, etc.) */
        DATA,
        /** Both resources and data */
        BOTH
    }

    public enum ResourcePackSource {
        /** From filesystem (resourcepacks/ folder) */
        FILE_SYSTEM,
        /** From classpath (engine resources) */
        CLASSPATH,
        /** From a mod */
        MOD
    }

    /**
     * Loads a resource pack from a pack.mcmeta or pack.json file.
     */
    public static ResourcePack fromMetadata(Path packRoot) {
        try {
            // Try pack.json first (our format)
            Path packJson = packRoot.resolve("pack.json");
            if (Files.exists(packJson)) {
                return fromPackJson(packRoot, packJson);
            }

            // Try pack.mcmeta (Minecraft format for compatibility)
            Path packMcmeta = packRoot.resolve("pack.mcmeta");
            if (Files.exists(packMcmeta)) {
                return fromPackMcmeta(packRoot, packMcmeta);
            }

            // No metadata, create basic pack
            String packName = packRoot.getFileName().toString();
            Identifier packId = Identifier.of("pack", packName);

            return ResourcePack.builder()
                    .id(packId)
                    .name(packName)
                    .description("Resource pack")
                    .source(ResourcePackSource.FILE_SYSTEM)
                    .rootPath(packRoot)
                    .build();

        } catch (Exception e) {
            LOGGER.error("Failed to load resource pack from: " + packRoot, e);
            return null;
        }
    }

    private static ResourcePack fromPackJson(Path packRoot, Path packJson) throws IOException {
        try (InputStream is = Files.newInputStream(packJson)) {
            JsonObject json = JsonParser.parseReader(new InputStreamReader(is)).getAsJsonObject();

            String idStr = json.has("id") ? json.get("id").getAsString() : packRoot.getFileName().toString();
            Identifier id = Identifier.of(idStr);

            String name = json.has("name") ? json.get("name").getAsString() : id.toString();
            String description = json.has("description") ? json.get("description").getAsString() : "";
            int formatVersion = json.has("format") ? json.get("format").getAsInt() : 1;

            ResourcePackType type = ResourcePackType.BOTH;
            if (json.has("type")) {
                type = ResourcePackType.valueOf(json.get("type").getAsString().toUpperCase());
            }

            return ResourcePack.builder()
                    .id(id)
                    .name(name)
                    .description(description)
                    .formatVersion(formatVersion)
                    .type(type)
                    .source(ResourcePackSource.FILE_SYSTEM)
                    .rootPath(packRoot)
                    .build();
        }
    }

    private static ResourcePack fromPackMcmeta(Path packRoot, Path packMcmeta) throws IOException {
        try (InputStream is = Files.newInputStream(packMcmeta)) {
            JsonObject json = JsonParser.parseReader(new InputStreamReader(is)).getAsJsonObject();
            JsonObject pack = json.getAsJsonObject("pack");

            String packName = packRoot.getFileName().toString();
            Identifier id = Identifier.of("pack", packName);

            String description = pack.has("description") ? pack.get("description").getAsString() : "";
            int formatVersion = pack.has("pack_format") ? pack.get("pack_format").getAsInt() : 1;

            return ResourcePack.builder()
                    .id(id)
                    .name(packName)
                    .description(description)
                    .formatVersion(formatVersion)
                    .type(ResourcePackType.RESOURCES)
                    .source(ResourcePackSource.FILE_SYSTEM)
                    .rootPath(packRoot)
                    .build();
        }
    }
}