package engine.helios;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL15.*;
import static org.lwjgl.opengl.GL20.*;
import static org.lwjgl.opengl.GL30.*;

public class Tessellator {
    private static final Tessellator INSTANCE = new Tessellator();
    private final BufferBuilder buffer;
    private final int vao, vbo;

    public static Tessellator getInstance() { return INSTANCE; }

    private Tessellator() {
        this.buffer = new BufferBuilder(2097152); // 2MB buffer
        this.vao = glGenVertexArrays();
        this.vbo = glGenBuffers();
    }

    public BufferBuilder getBuffer() { return buffer; }

    public void draw(){
        this.draw(this.buffer);
    }

    public void draw(BufferBuilder builder) {
        if (builder.getVertexCount() == 0) return;

        glBindVertexArray(vao);
        glBindBuffer(GL_ARRAY_BUFFER, vbo);

        // Upload the data from the specific builder passed in
        glBufferData(GL_ARRAY_BUFFER, builder.getBuffer(), GL_STREAM_DRAW);

        setupAttributes(builder.getFormat());

        glDrawArrays(GL_TRIANGLES, 0, builder.getVertexCount());

        // Clean up: Disable attributes so they don't leak into the next draw call
        cleanupAttributes(builder.getFormat());

        glBindBuffer(GL_ARRAY_BUFFER, 0);
        glBindVertexArray(0);
    }

    private void setupAttributes(VertexFormat format) {
        int offset = 0;

        // Position
        glVertexAttribPointer(0, format.posCount, GL_FLOAT, false, format.stride, offset);
        glEnableVertexAttribArray(0);
        offset += format.posCount * Float.BYTES;

        // Texture
        if (format.texCount > 0) {
            glVertexAttribPointer(1, format.texCount, GL_FLOAT, false, format.stride, offset);
            glEnableVertexAttribArray(1);
            offset += format.texCount * Float.BYTES;
        }

        // Color
        glVertexAttribPointer(2, format.colorCount, GL_FLOAT, false, format.stride, offset);
        glEnableVertexAttribArray(2);
    }

    private void cleanupAttributes(VertexFormat format) {
        glDisableVertexAttribArray(0);
        if (format.texCount > 0) glDisableVertexAttribArray(1);
        glDisableVertexAttribArray(2);
    }
}