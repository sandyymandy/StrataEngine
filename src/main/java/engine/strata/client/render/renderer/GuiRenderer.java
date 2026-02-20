package engine.strata.client.render.renderer;

import engine.strata.client.render.renderer.font.TextRenderer;

public class GuiRenderer {
    public TextRenderer textRenderer;

    public GuiRenderer() {
        this.textRenderer = new TextRenderer();
    }
}
