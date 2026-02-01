package engine.helios;

import engine.strata.client.render.Camera;
import engine.strata.util.Identifier;

public record RenderLayer(Identifier texture, ShaderStack shaderStack, boolean isTranslucent, boolean isDepthTestingEnabled, boolean isCullingEnabled) {

    public void setup(Camera camera) {
        shaderStack.use();
        shaderStack.setUniform("u_Projection", camera.getProjectionMatrix());
        shaderStack.setUniform("u_View", camera.getViewMatrix());
        RenderSystem.bindTexture(TextureManager.get(texture));

        if (isTranslucent()) RenderSystem.enableBlend();

        if(isDepthTestingEnabled()) RenderSystem.enableDepthTest();

        if(isCullingEnabled()) RenderSystem.enableBackFaceCull();
    }

    public void clean() {
        if (isTranslucent()) RenderSystem.disableBlend();

        if(isDepthTestingEnabled()) RenderSystem.disableDepthTest();

        if(isCullingEnabled()) RenderSystem.disableBackFaceCull();
    }
}