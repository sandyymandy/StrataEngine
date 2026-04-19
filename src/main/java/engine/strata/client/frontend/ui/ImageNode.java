package engine.strata.client.frontend.ui;

import engine.helios.rendering.texture.Texture;
import engine.helios.rendering.texture.TextureManager;
import engine.strata.util.Identifier;

public class ImageNode extends UiNode {
    private Identifier textureId = Identifier.ofEngine("missing");
    private Texture texture;
    private float r = 1, g = 1, b = 1, a = 1;

    public ImageNode texture(Identifier id) {
        if (id != null && id.equals(this.textureId)) {
            return this;
        }
        this.textureId = id;
        this.texture = null;
        return this;
    }

    public ImageNode color(float r, float g, float b, float a) {
        this.r = r;
        this.g = g;
        this.b = b;
        this.a = a;
        return this;
    }

    @Override
    public void render(UiRenderContext ctx) {
        if (texture == null) {
            // Texture creation happens on the render thread (we render UI on render thread).
            texture = TextureManager.get(textureId);
        }

        if (a > 0.0f && texture != null) {
            UiRect rect = rect();
            ctx.batch().quad(
                    texture.getId(),
                    rect.x(), rect.y(), rect.width(), rect.height(),
                    0, 0, 1, 1,
                    r, g, b, a
            );
        }
        super.render(ctx);
    }
}
