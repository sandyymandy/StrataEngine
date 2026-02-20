package engine.helios.rendering.vertex;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL15.*;
import static org.lwjgl.opengl.GL20.*;
import static org.lwjgl.opengl.GL30.*;

/**
 * Enhanced Tessellator with support for texture array rendering.
 */
public class Tessellator {
    private static final Tessellator INSTANCE = new Tessellator();
    private final BufferBuilder buffer;
    private final int vao, vbo;

    public static Tessellator getInstance() {
        return INSTANCE;
    }

    private Tessellator() {
        this.buffer = new BufferBuilder(2097152); // 2MB buffer
        this.vao = glGenVertexArrays();
        this.vbo = glGenBuffers();
    }

    public BufferBuilder getBuffer() {
        return buffer;
    }

    public void draw() {
        this.draw(this.buffer);
    }

    public void draw(BufferBuilder builder) {
        if (builder.getVertexCount() == 0) return;

        glBindVertexArray(vao);
        glBindBuffer(GL_ARRAY_BUFFER, vbo);

        // Upload the data
        glBufferData(GL_ARRAY_BUFFER, builder.getBuffer(), GL_STREAM_DRAW);

        setupAttributes(builder.getFormat());

        glDrawArrays(GL_TRIANGLES, 0, builder.getVertexCount());

        // Clean up: Disable attributes
        cleanupAttributes(builder.getFormat());

        glBindBuffer(GL_ARRAY_BUFFER, 0);
        glBindVertexArray(0);
    }

    private void setupAttributes(VertexFormat format) {
        int offset = 0;
        int attribIndex = 0;

        // Position (always present)
        if (format.posCount > 0) {
            glVertexAttribPointer(attribIndex, format.posCount, GL_FLOAT, false, format.stride, offset);
            glEnableVertexAttribArray(attribIndex);
            offset += format.posCount * Float.BYTES;
            attribIndex++;
        }

        // Texture coordinates
        if (format.texCount > 0) {
            glVertexAttribPointer(attribIndex, format.texCount, GL_FLOAT, false, format.stride, offset);
            glEnableVertexAttribArray(attribIndex);
            offset += format.texCount * Float.BYTES;
            attribIndex++;
        }

        // Color
        if (format.colorCount > 0) {
            glVertexAttribPointer(attribIndex, format.colorCount, GL_FLOAT, false, format.stride, offset);
            glEnableVertexAttribArray(attribIndex);
            offset += format.colorCount * Float.BYTES;
            attribIndex++;
        }

        // Texture array layer
        if (format.layerCount > 0) {
            glVertexAttribPointer(attribIndex, format.layerCount, GL_FLOAT, false, format.stride, offset);
            glEnableVertexAttribArray(attribIndex);
            offset += format.layerCount * Float.BYTES;
            attribIndex++;
        }

        // Brightness
        if (format.brightnessCount > 0) {
            glVertexAttribPointer(attribIndex, format.brightnessCount, GL_FLOAT, false, format.stride, offset);
            glEnableVertexAttribArray(attribIndex);
            offset += format.brightnessCount * Float.BYTES;
            attribIndex++;
        }
    }

    private void cleanupAttributes(VertexFormat format) {
        int attribIndex = 0;

        if (format.posCount > 0) {
            glDisableVertexAttribArray(attribIndex++);
        }
        if (format.texCount > 0) {
            glDisableVertexAttribArray(attribIndex++);
        }
        if (format.colorCount > 0) {
            glDisableVertexAttribArray(attribIndex++);
        }
        if (format.layerCount > 0) {
            glDisableVertexAttribArray(attribIndex++);
        }
        if (format.brightnessCount > 0) {
            glDisableVertexAttribArray(attribIndex++);
        }
    }
}