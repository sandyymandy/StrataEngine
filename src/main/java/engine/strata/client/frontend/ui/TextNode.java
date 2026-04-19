package engine.strata.client.frontend.ui;

import java.util.Objects;
import java.util.function.Supplier;

public class TextNode extends UiNode {
    private Supplier<String> text = () -> "";
    private float r = 1, g = 1, b = 1, a = 1;
    private float scale = 1.0f;

    public TextNode text(String value) {
        this.text = () -> value;
        return this;
    }

    public TextNode text(Supplier<String> value) {
        this.text = Objects.requireNonNull(value);
        return this;
    }

    public TextNode color(float r, float g, float b, float a) {
        this.r = r;
        this.g = g;
        this.b = b;
        this.a = a;
        return this;
    }

    public TextNode scale(float scale) {
        this.scale = scale;
        return this;
    }

    @Override
    public void render(UiRenderContext ctx) {
        String s = text.get();
        if (s != null && !s.isEmpty()) {
            UiRect rect = rect();

            // For text nodes, rect() is treated as an anchor point. Pivot offsets are
            // applied against measured text size so anchor/pivot behave as expected.
            float x = rect.x();
            float y = rect.y();

            float w = ctx.text().measureWidth(s) * scale;
            float h = ctx.text().measureHeight(s) * scale;

            x -= pivot().x * w;
            y -= pivot().y * h;

            ctx.batch().setTexture(ctx.whiteTextureId());
            ctx.text().append(ctx.batch(), s, x, y, scale, r, g, b, a);
        }
        super.render(ctx);
    }
}
