package engine.helios.rendering;

import engine.helios.rendering.vertex.VertexFormat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.FloatBuffer;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL15.*;
import static org.lwjgl.opengl.GL20.*;
import static org.lwjgl.opengl.GL30.*;

/**
 * Helios abstraction for rendering meshes with VAO/VBO management.
 * Handles vertex format configuration and draw calls.
 */
public class MeshRenderer {
    private static final Logger LOGGER = LoggerFactory.getLogger("MeshRenderer");

    private int vao = 0;
    private int vbo = 0;
    private int vertexCount = 0;
    private int gpuBytes = 0;
    private VertexFormat format;
    private boolean uploaded = false;

    /**
     * Uploads mesh data to the GPU.
     * Must be called on the render thread.
     *
     * @param data Vertex data in the specified format
     * @param vertexCount Number of vertices
     * @param format Vertex format describing the data layout
     */
    public void upload(FloatBuffer data, int vertexCount, VertexFormat format) {
        RenderSystem.assertOnRenderThread();

        // Clean up old resources
        if (vao != 0 || vbo != 0) {
            deleteGLObjects();
        }

        if (vertexCount == 0) {
            this.vertexCount = 0;
            this.gpuBytes = 0;
            this.uploaded = false;
            this.format = format;
            return;
        }

        this.format = format;

        // Create and bind VAO
        vao = glGenVertexArrays();
        glBindVertexArray(vao);

        // Create and bind VBO
        vbo = glGenBuffers();
        glBindBuffer(GL_ARRAY_BUFFER, vbo);
        glBufferData(GL_ARRAY_BUFFER, data, GL_STATIC_DRAW);

        gpuBytes = data.limit() * Float.BYTES;

        // Configure vertex attributes based on format
        configureVertexAttributes(format);

        // Unbind
        glBindBuffer(GL_ARRAY_BUFFER, 0);
        glBindVertexArray(0);

        this.vertexCount = vertexCount;
        this.uploaded = true;
    }

    /**
     * Configures vertex attributes based on the vertex format.
     */
    private void configureVertexAttributes(VertexFormat format) {
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

    /**
     * Renders the mesh. Must be called on the render thread with appropriate shader bound.
     */
    public void render() {
        RenderSystem.assertOnRenderThread();

        if (!uploaded || vertexCount == 0) {
            return;
        }

        glBindVertexArray(vao);
        glDrawArrays(GL_TRIANGLES, 0, vertexCount);
        glBindVertexArray(0);
    }

    /**
     * Frees all GPU resources. Idempotent.
     */
    public void dispose() {
        deleteGLObjects();
        vertexCount = 0;
        gpuBytes = 0;
        uploaded = false;
    }

    private void deleteGLObjects() {
        if (vao != 0) {
            glDeleteVertexArrays(vao);
            vao = 0;
        }
        if (vbo != 0) {
            glDeleteBuffers(vbo);
            vbo = 0;
        }
    }

    // ── Getters ───────────────────────────────────────────────────────────────

    public boolean isUploaded() { return uploaded; }
    public boolean isEmpty() { return vertexCount == 0; }
    public int getVertexCount() { return vertexCount; }
    public int getBufferSize() { return gpuBytes; }
    public VertexFormat getFormat() { return format; }

    @Override
    public String toString() {
        return String.format("MeshRenderer[verts=%d, gpu=%dKB, format=%s]",
                vertexCount, gpuBytes / 1024, format);
    }
}