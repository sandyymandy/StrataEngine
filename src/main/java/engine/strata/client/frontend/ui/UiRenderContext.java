package engine.strata.client.frontend.ui;

import engine.strata.client.frontend.render.renderer.font.TextRenderer;
import engine.helios.rendering.RenderSystem;

public record UiRenderContext(UiBatch batch, TextRenderer text, int whiteTextureId) {
    public UiRenderContext(UiBatch batch, TextRenderer text) {
        this(batch, text, RenderSystem.getCurrentTexture());
    }
}
