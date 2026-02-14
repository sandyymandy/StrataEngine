package engine.strata.client.render;

import engine.helios.RenderLayer;
import engine.helios.ShaderManager;
import engine.helios.ShaderStack;
import engine.strata.util.Identifier;

import java.util.HashMap;
import java.util.Map;

/**
 * Manages render layers for different types of rendering.
 * Provides caching to avoid creating duplicate layers for the same texture.
 */
public class RenderLayers {
    // Cache layers to avoid recreating them every frame
    private static final Map<String, RenderLayer> LAYERS = new HashMap<>();



    /**
     * Gets or creates an entity render layer.
     */
    public static RenderLayer getEntityLayer(Identifier texture, boolean translucent) {
        String key = "entity_" + texture.toString() + "_" + translucent;
        return LAYERS.computeIfAbsent(key, k -> {
            ShaderStack shader = ShaderManager.use(Identifier.ofEngine("generic_3d"));
            ShaderStack shaderCutout = ShaderManager.use(Identifier.ofEngine("entity_cutout"));
            return new RenderLayer(
                    texture,
                    translucent ? shaderCutout : shader,
                    translucent,
                    true,   // Enable depth testing
                    false   // Entities typically don't use culling
            );
        });
    }

    /**
     * Gets or creates the chunk render layer.
     * Uses chunk shader with texture atlas, depth testing, and culling.
     */
    public static RenderLayer getChunkLayer(Identifier textureAtlasId) {
        String key = "chunk_" + textureAtlasId.toString();
        return LAYERS.computeIfAbsent(key, k -> new RenderLayer(
                textureAtlasId,
                ShaderManager.use(Identifier.ofEngine("chunk")),
                false,  // Not translucent
                true,   // Enable depth testing
                true    // Enable back-face culling
        ));
    }

    /**
     * Gets or creates the chunk translucent render layer.
     * For water, glass, and other transparent blocks.
     */
    public static RenderLayer getChunkTranslucentLayer(Identifier textureAtlasId) {
        String key = "chunk_translucent_" + textureAtlasId.toString();
        return LAYERS.computeIfAbsent(key, k -> new RenderLayer(
                textureAtlasId,
                ShaderManager.use(Identifier.ofEngine("chunk")),
                true,   // Translucent - enables blending
                true,   // Enable depth testing
                false   // Disable culling for see-through blocks
        ));
    }

    /**
     * Clears all cached render layers.
     * Call this when reloading resources or changing render settings.
     */
    public static void clearCache() {
        LAYERS.clear();
    }
}