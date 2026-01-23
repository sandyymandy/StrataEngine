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

    public void draw() {
        if (buffer.getVertexCount() == 0) return;

        glBindVertexArray(vao);
        glBindBuffer(GL_ARRAY_BUFFER, vbo);
        glBufferData(GL_ARRAY_BUFFER, buffer.getBuffer(), GL_STREAM_DRAW);

        setupAttributes();

        glDrawArrays(GL_TRIANGLES, 0, buffer.getVertexCount());

        glBindBuffer(GL_ARRAY_BUFFER, 0);
        glBindVertexArray(0);
    }

    private void setupAttributes() {
        VertexFormat f = buffer.getFormat();
        int offset = 0;

        // Position
        glVertexAttribPointer(0, f.posCount, GL_FLOAT, false, f.stride, offset);
        glEnableVertexAttribArray(0);
        offset += f.posCount * Float.BYTES;

        // Texture
        if (f.texCount > 0) {
            glVertexAttribPointer(1, f.texCount, GL_FLOAT, false, f.stride, offset);
            glEnableVertexAttribArray(1);
            offset += f.texCount * Float.BYTES;
        }

        // Color
        glVertexAttribPointer(2, f.colorCount, GL_FLOAT, false, f.stride, offset);
        glEnableVertexAttribArray(2);
    }
}