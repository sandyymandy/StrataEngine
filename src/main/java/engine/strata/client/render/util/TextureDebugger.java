package engine.strata.client.render.util;

import engine.helios.RenderLayer;
import engine.helios.ShaderStack;
import engine.strata.util.Identifier;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Utility for debugging texture rendering issues.
 */
public class TextureDebugger {
    private static final Logger LOGGER = LoggerFactory.getLogger("TextureDebugger");
    private static boolean enabled = false;

    public static void setEnabled(boolean enable) {
        enabled = enable;
    }

    /**
     * Logs detailed information about texture binding.
     */
    public static void logTextureBinding(Identifier textureId, int textureHandle) {
        if (!enabled) return;

        LOGGER.info("=== Texture Binding Debug ===");
        LOGGER.info("Texture ID: {}", textureId);
        LOGGER.info("OpenGL Handle: {}", textureHandle);

        // Check if texture is valid
        if (textureHandle == 0) {
            LOGGER.error("INVALID TEXTURE HANDLE! Texture was not loaded.");
            return;
        }

        // Bind and query texture properties
        GL13.glActiveTexture(GL13.GL_TEXTURE0);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, textureHandle);

        int width = GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D, 0, GL11.GL_TEXTURE_WIDTH);
        int height = GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D, 0, GL11.GL_TEXTURE_HEIGHT);

        LOGGER.info("Texture Size: {}x{}", width, height);

        if (width == 0 || height == 0) {
            LOGGER.error("TEXTURE HAS ZERO SIZE! Texture data was not uploaded.");
        }
    }

    /**
     * Logs shader uniform state.
     */
    public static void logShaderUniforms(ShaderStack shader) {
        if (!enabled) return;

        LOGGER.info("=== Shader Uniform Debug ===");
        LOGGER.info("Active Shader: {}", shader);

        // You can query uniform locations here if your ShaderStack supports it
        // LOGGER.info("u_Texture location: {}", shader.getUniformLocation("u_Texture"));
        // LOGGER.info("u_UseTexture location: {}", shader.getUniformLocation("u_UseTexture"));
    }

    /**
     * Verifies that a render layer is properly configured.
     */
    public static void validateRenderLayer(RenderLayer layer) {
        if (!enabled) return;

        LOGGER.info("=== Render Layer Validation ===");
        LOGGER.info("Texture: {}", layer.texture());
        LOGGER.info("Shader: {}", layer.shaderStack());
        LOGGER.info("Translucent: {}", layer.isTranslucent());
    }

    /**
     * Logs UV coordinates for debugging.
     */
    public static void logUVCoordinates(float u, float v, String context) {
        if (!enabled) return;

        LOGGER.debug("[{}] UV: ({}, {})", context, u, v);

        if (u < 0 || u > 1 || v < 0 || v > 1) {
            LOGGER.warn("[{}] UV coordinates out of [0,1] range!", context);
        }
    }
}