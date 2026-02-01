package engine.strata.client.render;

import engine.helios.RenderLayer;
import engine.helios.ShaderManager;
import engine.strata.util.Identifier;

import java.util.HashMap;
import java.util.Map;

/**
 * Manages render layers for different types of rendering.
 * Provides caching to avoid creating duplicate layers for the same texture.
 */
public class RenderLayers {
    // Cache layers to avoid recreating them every frame
    private static final Map<Identifier, RenderLayer> ENTITY_LAYERS = new HashMap<>();
    private static final Map<Identifier, RenderLayer> TRANSLUCENT_LAYERS = new HashMap<>();

    /**
     * Gets or creates a standard entity render layer for a specific texture.
     * Cached for performance.
     */
    public static RenderLayer getEntityTexture(Identifier texture) {
        return ENTITY_LAYERS.computeIfAbsent(texture, RenderLayers::createEntityLayer);
    }

    /**
     * Gets or creates a translucent entity render layer.
     * Used for entities with transparency (ghosts, particles, etc.)
     */
    public static RenderLayer getEntityTranslucent(Identifier texture) {
        return TRANSLUCENT_LAYERS.computeIfAbsent(texture, RenderLayers::createTranslucentLayer);
    }

    /**
     * Creates a standard entity render layer.
     */
    private static RenderLayer createEntityLayer(Identifier texture) {
        return new RenderLayer(
                texture,
                ShaderManager.use(Identifier.ofEngine("generic_3d")),
                false, // No transparency
                true,  // Enable depth testing
                true

        );
    }

    /**
     * Creates a translucent entity render layer.
     */
    private static RenderLayer createTranslucentLayer(Identifier texture) {
        return new RenderLayer(
                texture,
                ShaderManager.use(Identifier.ofEngine("entity_cutout")),
                true,  // Enable transparency
                true,  // Enable depth testing
                false
        );
    }

    /**
     * Gets a render layer based on texture slot metadata.
     * Useful when the skin file specifies render properties.
     */
    public static RenderLayer getLayerForSlot(Identifier texture, boolean translucent) {
        return translucent ? getEntityTranslucent(texture) : getEntityTexture(texture);
    }

    /**
     * Clears all cached render layers.
     * Call this when reloading resources or changing render settings.
     */
    public static void clearCache() {
        ENTITY_LAYERS.clear();
        TRANSLUCENT_LAYERS.clear();
    }

    /**
     * Pre-warms the cache with commonly used textures.
     * Call during initialization to avoid hitches during gameplay.
     */
    public static void prewarm(Identifier... textures) {
        for (Identifier texture : textures) {
            getEntityTexture(texture);
        }
    }
}