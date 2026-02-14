package engine.helios;

import engine.strata.client.render.Camera;
import engine.strata.util.Identifier;

/**
 * Defines a render layer with specific shader, texture, and OpenGL state.
 * Properly manages state setup and cleanup to prevent state bleeding between layers.
 */
public record RenderLayer(
        Identifier texture,
        ShaderStack shaderStack,
        boolean isTranslucent,
        boolean isDepthTestingEnabled,
        boolean isCullingEnabled
) {

    /**
     * Sets up the render state for this layer.
     * Call before rendering objects with this layer.
     */
    public void setup(Camera camera) {
        // Use the shader
        shaderStack.use();

        // Set camera uniforms
        shaderStack.setUniform("u_Projection", camera.getProjectionMatrix());
        shaderStack.setUniform("u_View", camera.getViewMatrix());

        // Bind the texture to texture unit 0
        RenderSystem.bindTexture(TextureManager.get(texture));

        // Without this, the sampler uniform defaults to 0, but it's better to be explicit
        shaderStack.setUniform("u_Texture", 0);

        // Enable blending for translucent objects
        if (isTranslucent()) {
            RenderSystem.enableBlend();
        }

        // Enable depth testing
        if (isDepthTestingEnabled()) {
            RenderSystem.enableDepthTest();
        }

        // Enable back-face culling
        if (isCullingEnabled()) {
            RenderSystem.enableBackFaceCull();
        }
    }

    /**
     * Cleans up the render state after rendering.
     * Call after all objects in this layer have been rendered.
     */
    public void clean() {
        // Disable blending
        if (isTranslucent()) {
            RenderSystem.disableBlend();
        }

        // Disable depth testing
        if (isDepthTestingEnabled()) {
            RenderSystem.disableDepthTest();
        }

        // Disable culling
        if (isCullingEnabled()) {
            RenderSystem.disableBackFaceCull();
        }

        // Unbind texture to prevent state bleeding
        RenderSystem.bindTexture(0);
    }
}