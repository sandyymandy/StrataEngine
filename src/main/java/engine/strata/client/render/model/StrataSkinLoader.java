package engine.strata.client.render.model;

import com.google.gson.*;
import engine.strata.core.io.ResourceManager;
import engine.strata.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

/**
 * Loads .strskin files that map texture slots to actual texture identifiers.
 */
public class StrataSkinLoader {
    private static final Logger LOGGER = LoggerFactory.getLogger("SkinLoader");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    /**
     * Loads a skin file and returns a map of texture slot names to Identifiers.
     */
    public static Map<String, Identifier> load(Identifier skinId) {
        try {
            String json = ResourceManager.loadAsString(skinId, "models", "strskin");
            if (json == null || json.isEmpty()) {
                LOGGER.error("Failed to load skin: {}", skinId);
                return createFallbackSkin();
            }

            JsonObject root = GSON.fromJson(json, JsonObject.class);
            Map<String, Identifier> textureMap = new HashMap<>();

            JsonObject texturesObj = root.getAsJsonObject("textures");
            if (texturesObj != null) {
                for (Map.Entry<String, JsonElement> entry : texturesObj.entrySet()) {
                    String slot = entry.getKey();
                    String texturePath = entry.getValue().getAsString();
                    textureMap.put(slot, Identifier.of(texturePath));
                }
            }

            return textureMap;

        } catch (Exception e) {
            LOGGER.error("Error loading skin {}: {}", skinId, e.getMessage());
            return createFallbackSkin();
        }
    }

    /**
     * Creates a fallback skin with a missing texture.
     */
    private static Map<String, Identifier> createFallbackSkin() {
        Map<String, Identifier> fallback = new HashMap<>();
        fallback.put("main", Identifier.ofEngine("missing"));
        return fallback;
    }
}