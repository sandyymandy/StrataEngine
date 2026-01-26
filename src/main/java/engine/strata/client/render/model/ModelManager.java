package engine.strata.client.render.model;

import engine.strata.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

/**
 * Manages loading and caching of models and skins.
 */
public class ModelManager {
    private static final Logger LOGGER = LoggerFactory.getLogger("ModelManager");
    private static final Map<Identifier, StrataModel> MODELS = new HashMap<>();
    private static final Map<Identifier, Map<String, Identifier>> SKINS = new HashMap<>();

    /**
     * Gets a model, loading it if necessary.
     */
    public static StrataModel getModel(Identifier id) {
        return MODELS.computeIfAbsent(id, StrataModelLoader::load);
    }

    /**
     * Gets a skin (texture mapping), loading it if necessary.
     */
    public static Map<String, Identifier> getSkin(Identifier id) {
        return SKINS.computeIfAbsent(id, StrataSkinLoader::load);
    }

    /**
     * Gets the texture identifier for a specific texture slot in a skin.
     */
    public static Identifier getTexture(Identifier skinId, String slot) {
        Map<String, Identifier> skin = getSkin(skinId);
        return skin.getOrDefault(slot, Identifier.ofEngine("missing"));
    }

    /**
     * Clears all cached models and skins (useful for resource reloading).
     */
    public static void clearCache() {
        MODELS.clear();
        SKINS.clear();
        LOGGER.info("Cleared model and skin cache");
    }

    /**
     * Preloads a model into the cache.
     */
    public static void preload(Identifier modelId, Identifier skinId) {
        getModel(modelId);
        getSkin(skinId);
        LOGGER.info("Preloaded model: {} with skin: {}", modelId, skinId);
    }
}