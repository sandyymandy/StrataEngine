package engine.helios;

import engine.strata.client.render.Camera;
import engine.strata.util.Identifier;

public class RenderLayer {
    private final Identifier texture;
    private final ShaderStack shaderStack;
    private final boolean hasTransparency;

    public RenderLayer(Identifier texture, ShaderStack shaderStack, boolean hasTransparency) {
        this.texture = texture;
        this.shaderStack = shaderStack;
        this.hasTransparency = hasTransparency;
    }

    public void setup(Camera camera) {
        shaderStack.setUniform("u_Projection", camera.getProjectionMatrix());
        shaderStack.setUniform("u_View", camera.getViewMatrix());
        shaderStack.use();
        RenderSystem.bindTexture(TextureManager.get(texture));

        if (hasTransparency) {
            RenderSystem.enableBlend();
        } else {
            RenderSystem.disableBlend();
        }
    }

    public void clean() {
        if (this.hasTransparency) {
            RenderSystem.disableBlend();
        }
    }
}