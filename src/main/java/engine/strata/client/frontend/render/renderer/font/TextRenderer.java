package engine.strata.client.frontend.render.renderer.font;

import engine.strata.client.frontend.ui.UiBatch;
import org.lwjgl.BufferUtils;
import org.lwjgl.stb.STBEasyFont;

import java.nio.ByteBuffer;

public class TextRenderer {
    private ByteBuffer quadBuffer = BufferUtils.createByteBuffer(1024);

    private void ensureQuadCapacity(int textLength) {
        // STBEasyFont worst-case recommendation: ~270 bytes per character.
        int needed = Math.max(1024, textLength * 270);
        if (quadBuffer.capacity() >= needed) {
            quadBuffer.clear();
            return;
        }
        quadBuffer = BufferUtils.createByteBuffer(needed);
    }

    public int measureWidth(String text) {
        if (text == null || text.isEmpty()) return 0;
        try {
            return STBEasyFont.stb_easy_font_width(text);
        } catch (Throwable ignored) {
            // Fallback: rough estimate (STB font is ~8px wide per char).
            return text.length() * 8;
        }
    }

    public int measureHeight(String text) {
        if (text == null || text.isEmpty()) return 0;
        try {
            return STBEasyFont.stb_easy_font_height(text);
        } catch (Throwable ignored) {
            return 12;
        }
    }

    public void append(UiBatch batch, String text, float x, float y, float scale,
                       float r, float g, float b, float a) {
        if (text == null || text.isEmpty()) return;
        ensureQuadCapacity(text.length());

        int quads = STBEasyFont.stb_easy_font_print(0, 0, text, null, quadBuffer);
        if (quads <= 0) return;

        // Each quad has 4 vertices, each vertex is 16 bytes (4 floats), but only (x,y) are used.
        int verts = quads * 4;
        for (int q = 0; q < quads; q++) {
            int baseVertex = q * 4;

            float x0 = getX(baseVertex + 0) * scale + x;
            float y0 = getY(baseVertex + 0) * scale + y;
            float x1 = getX(baseVertex + 1) * scale + x;
            float y1 = getY(baseVertex + 1) * scale + y;
            float x2 = getX(baseVertex + 2) * scale + x;
            float y2 = getY(baseVertex + 2) * scale + y;
            float x3 = getX(baseVertex + 3) * scale + x;
            float y3 = getY(baseVertex + 3) * scale + y;

            // Two triangles: 0-1-2, 0-2-3
            v(batch, x0, y0, r, g, b, a);
            v(batch, x1, y1, r, g, b, a);
            v(batch, x2, y2, r, g, b, a);
            v(batch, x0, y0, r, g, b, a);
            v(batch, x2, y2, r, g, b, a);
            v(batch, x3, y3, r, g, b, a);
        }
    }

    private float getX(int vertexIndex) {
        int offset = vertexIndex * 16;
        return quadBuffer.getFloat(offset);
    }

    private float getY(int vertexIndex) {
        int offset = vertexIndex * 16 + 4;
        return quadBuffer.getFloat(offset);
    }

    private static void v(UiBatch batch, float x, float y, float r, float g, float b, float a) {
        // Text uses the same POSITION_TEXTURE_COLOR format as the rest of UI.
        // UVs are constant and a white texture is bound for UI rendering.
        batch.vertex(x, y, 0, 0, 0, r, g, b, a);
    }
}
