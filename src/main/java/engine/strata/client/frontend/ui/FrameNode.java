package engine.strata.client.frontend.ui;

public class FrameNode extends UiNode {
    private float r = 0, g = 0, b = 0, a = 0; // transparent by default

    public FrameNode background(float r, float g, float b, float a) {
        this.r = r;
        this.g = g;
        this.b = b;
        this.a = a;
        return this;
    }

    @Override
    public void render(UiRenderContext ctx) {
        if (a > 0.0f) {
            UiRect rect = rect();
            ctx.batch().quad(
                    ctx.whiteTextureId(),
                    rect.x(), rect.y(), rect.width(), rect.height(),
                    0, 0, 1, 1,
                    r, g, b, a
            );
        }
        super.render(ctx);
    }
}
