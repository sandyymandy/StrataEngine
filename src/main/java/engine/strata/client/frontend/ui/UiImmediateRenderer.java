package engine.strata.client.frontend.ui;

import engine.helios.rendering.RenderSystem;

import java.nio.FloatBuffer;

import static org.lwjgl.opengl.GL11.GL_FLOAT;
import static org.lwjgl.opengl.GL11.GL_TRIANGLES;
import static org.lwjgl.opengl.GL11.glDrawArrays;
import static org.lwjgl.opengl.GL15.*;
import static org.lwjgl.opengl.GL20.glEnableVertexAttribArray;
import static org.lwjgl.opengl.GL20.glVertexAttribPointer;
import static org.lwjgl.opengl.GL30.*;

public class UiImmediateRenderer {
    private int vao;
    private int vbo;
    private int bytesCapacity;

    public void initIfNeeded() {
        RenderSystem.assertOnRenderThread();
        if (vao != 0) return;

        vao = glGenVertexArrays();
        vbo = glGenBuffers();

        glBindVertexArray(vao);
        glBindBuffer(GL_ARRAY_BUFFER, vbo);

        // POSITION_TEXTURE_COLOR (from helios doc)
        // 0: vec3 pos (12 bytes)
        // 1: vec2 uv  (8 bytes)
        // 2: vec4 col? (16 bytes)
        int stride = UiBatch.FLOATS_PER_VERTEX * Float.BYTES;
        int offset = 0;

        glVertexAttribPointer(0, 3, GL_FLOAT, false, stride, offset);
        glEnableVertexAttribArray(0);
        offset += 3 * Float.BYTES;

        glVertexAttribPointer(1, 2, GL_FLOAT, false, stride, offset);
        glEnableVertexAttribArray(1);
        offset += 2 * Float.BYTES;

        glVertexAttribPointer(2, 4, GL_FLOAT, false, stride, offset);
        glEnableVertexAttribArray(2);

        glBindBuffer(GL_ARRAY_BUFFER, 0);
        glBindVertexArray(0);
    }

    public void begin(FloatBuffer data, int vertexCount) {
        RenderSystem.assertOnRenderThread();
        initIfNeeded();
        if (vertexCount <= 0) return;

        int bytesNeeded = data.remaining() * Float.BYTES;
        glBindBuffer(GL_ARRAY_BUFFER, vbo);
        if (bytesNeeded > bytesCapacity) {
            glBufferData(GL_ARRAY_BUFFER, data, GL_STREAM_DRAW);
            bytesCapacity = bytesNeeded;
        } else {
            glBufferSubData(GL_ARRAY_BUFFER, 0, data);
        }
        glBindBuffer(GL_ARRAY_BUFFER, 0);

        glBindVertexArray(vao);
    }

    public void draw(int firstVertex, int vertexCount) {
        RenderSystem.assertOnRenderThread();
        if (vao == 0 || vertexCount <= 0) return;
        glDrawArrays(GL_TRIANGLES, firstVertex, vertexCount);
    }

    public void end() {
        RenderSystem.assertOnRenderThread();
        if (vao != 0) {
            glBindVertexArray(0);
        }
    }

    public void render(FloatBuffer data, int vertexCount) {
        begin(data, vertexCount);
        draw(0, vertexCount);
        end();
    }

    public void dispose() {
        RenderSystem.assertOnRenderThread();
        if (vao != 0) { glDeleteVertexArrays(vao); vao = 0; }
        if (vbo != 0) { glDeleteBuffers(vbo); vbo = 0; }
        bytesCapacity = 0;
    }
}
