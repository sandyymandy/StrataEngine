package engine.strata.client.render;

import engine.helios.RenderLayer;
import engine.helios.ShaderManager;
import engine.strata.util.Identifier;

/**
 * Predefined render layers for different types of rendering.
 */
public class RenderLayers {

    /**
     * Creates an entity render layer for a specific texture.
     */
    public static RenderLayer entity(Identifier texture) {
        return new RenderLayer(
                texture,
                ShaderManager.getCurrent(), // Use the currently active shader
                false // No transparency by default
        );
    }

    /**
     * Creates an entity render layer with transparency.
     */
    public static RenderLayer entityTranslucent(Identifier texture) {
        return new RenderLayer(
                texture,
                ShaderManager.getCurrent(),
                true // Enable transparency
        );
    }

    /**
     * Creates a render layer for the specified entity texture identifier.
     */
    public static RenderLayer getEntityTexture(Identifier texture) {
        return entity(texture);
    }
}