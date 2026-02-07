package engine.strata.client.render.model.io;

import com.google.gson.*;
import engine.strata.client.render.model.StrataSkin;
import engine.strata.core.io.ResourceManager;
import engine.strata.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

/**
 * Loads .strskin files that map texture slots to actual texture identifiers.
 * Supports additional metadata like transparency and render priority.
 */
public class StrataSkinLoader {
    private static final Logger LOGGER = LoggerFactory.getLogger("SkinLoader");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    /**
     * Loads a skin file from resources.
     */
    public static StrataSkin load(Identifier id) {
        LOGGER.info("Attempting to load skin: {}", id);

        try {
            // Try to load the JSON file
            String json = ResourceManager.loadAsString(id, "skins", "strskin");

            if (json == null || json.isEmpty()) {
                LOGGER.error("Skin file is null or empty for: {}", id);
                LOGGER.error("Expected location: assets/{}/skins/{}.strskin", id.namespace, id.path);
                return createFallbackSkin(id);
            }

            LOGGER.debug("Successfully read skin file, parsing JSON...");
            JsonObject root;

            try {
                root = GSON.fromJson(json, JsonObject.class);
            } catch (JsonSyntaxException e) {
                LOGGER.error("Invalid JSON syntax in skin file: {}", id);
                LOGGER.error("JSON content: {}", json);
                throw e;
            }

            // Parse skin ID
            if (!root.has("id")) {
                LOGGER.error("Skin file missing 'id' field: {}", id);
                return createFallbackSkin(id);
            }
            Identifier skinId = Identifier.of(root.get("id").getAsString());

            // Parse format version (with validation)
            if (!root.has("format_version")) {
                LOGGER.warn("Skin file missing 'format_version', assuming version 1: {}", id);
            }
            int version = root.has("format_version") ? root.get("format_version").getAsInt() : 1;

            if (version > 1) {
                LOGGER.warn("Skin file has format_version {}, but loader only supports version 1. Attempting to load anyway: {}", version, id);
            }

            // Parse textures
            if (!root.has("textures")) {
                LOGGER.error("Skin file missing 'textures' field: {}", id);
                return createFallbackSkin(id);
            }

            Map<String, StrataSkin.TextureData> textures = new HashMap<>();
            JsonObject texObj = root.getAsJsonObject("textures");

            if (texObj.size() == 0) {
                LOGGER.error("Skin file has no texture slots defined: {}", id);
                return createFallbackSkin(id);
            }

            for (Map.Entry<String, JsonElement> entry : texObj.entrySet()) {
                String slot = entry.getKey();

                if (!entry.getValue().isJsonObject()) {
                    LOGGER.error("Texture slot '{}' is not a JSON object in skin: {}", slot, id);
                    continue;
                }

                JsonObject data = entry.getValue().getAsJsonObject();

                // Validate required fields
                if (!data.has("path")) {
                    LOGGER.error("Texture slot '{}' missing 'path' field in skin: {}", slot, id);
                    continue;
                }
                if (!data.has("width")) {
                    LOGGER.error("Texture slot '{}' missing 'width' field in skin: {}", slot, id);
                    continue;
                }
                if (!data.has("height")) {
                    LOGGER.error("Texture slot '{}' missing 'height' field in skin: {}", slot, id);
                    continue;
                }

                Identifier path = Identifier.of(data.get("path").getAsString());
                int width = data.get("width").getAsInt();
                int height = data.get("height").getAsInt();

                // Optional fields with defaults
                boolean translucent = data.has("translucent") && data.get("translucent").getAsBoolean();
                int renderPriority = data.has("render_priority") ? data.get("render_priority").getAsInt() : 0;

                textures.put(slot, new StrataSkin.TextureData(
                        path, width, height, translucent, renderPriority
                ));

                LOGGER.debug("  Loaded texture slot '{}': {} ({}x{}, translucent={}, priority={})",
                        slot, path, width, height, translucent, renderPriority);
            }

            if (textures.isEmpty()) {
                LOGGER.error("No valid texture slots loaded from skin: {}", id);
                return createFallbackSkin(id);
            }

            LOGGER.info("Successfully loaded skin: {} with {} texture slot(s)", id, textures.size());
            return new StrataSkin(skinId, version, textures);

        } catch (Exception e) {
            LOGGER.error("Failed to load skin: {}", id);
            LOGGER.error("Error details: ", e);
            return createFallbackSkin(id);
        }
    }

    /**
     * Creates a fallback skin with a missing texture indicator.
     */
    private static StrataSkin createFallbackSkin(Identifier id) {
        LOGGER.warn("Creating fallback skin for: {}", id);

        Map<String, StrataSkin.TextureData> fallback = new HashMap<>();
        fallback.put("main", new StrataSkin.TextureData(
                Identifier.ofEngine("missing"),
                16, 16, // Standard missing texture size
                false,  // Not translucent
                0       // Default priority
        ));

        return new StrataSkin(id, 1, fallback);
    }
}