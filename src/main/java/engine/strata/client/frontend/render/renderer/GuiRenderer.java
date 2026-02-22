package engine.strata.client.frontend.render.renderer;

import engine.strata.client.frontend.render.renderer.font.TextRenderer;

public class GuiRenderer {
    public TextRenderer textRenderer;

    public GuiRenderer() {
        this.textRenderer = new TextRenderer();
    }
}
