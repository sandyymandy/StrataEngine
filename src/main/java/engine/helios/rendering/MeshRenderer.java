package engine.helios.rendering;

import engine.helios.rendering.vertex.VertexFormat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.FloatBuffer;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL15.*;
import static org.lwjgl.opengl.GL20.*;
import static org.lwjgl.opengl.GL30.*;

public class MeshRenderer {
    private static final Logger LOGGER = LoggerFactory.getLogger("MeshRenderer");

    private static final int COLOR_ATTRIB_INDEX = 2;

    private int vao = 0;
    private int vbo = 0;
    private int vertexCount = 0;
    private int gpuBytes = 0;
    private VertexFormat format;
    private boolean uploaded = false;

    /**
     * Uploads mesh data to the GPU.
     * Must be called on the render thread.
     */
    public void upload(FloatBuffer data, int vertexCount, VertexFormat format) {

        if (vao != 0 || vbo != 0) {
            deleteGLObjects();
        }

        if (vertexCount == 0) {
            this.vertexCount = 0;
            this.gpuBytes    = 0;
            this.uploaded    = false;
            this.format      = format;
            return;
        }

        this.format = format;

        vao = glGenVertexArrays();
        glBindVertexArray(vao);

        vbo = glGenBuffers();
        glBindBuffer(GL_ARRAY_BUFFER, vbo);
        glBufferData(GL_ARRAY_BUFFER, data, GL_STATIC_DRAW);

        gpuBytes = data.limit() * Float.BYTES;

        configureVertexAttributes(format);

        glBindBuffer(GL_ARRAY_BUFFER, 0);
        glBindVertexArray(0);

        this.vertexCount = vertexCount;
        this.uploaded    = true;
    }

    /**
     * Configures vertex attributes based on the vertex format.
     */
    private void configureVertexAttributes(VertexFormat format) {
        int offset     = 0;
        int attribIndex = 0;

        if (format.posCount > 0) {
            glVertexAttribPointer(attribIndex, format.posCount, GL_FLOAT, false, format.stride, offset);
            glEnableVertexAttribArray(attribIndex);
            offset += format.posCount * Float.BYTES;
            attribIndex++;
        }

        if (format.texCount > 0) {
            glVertexAttribPointer(attribIndex, format.texCount, GL_FLOAT, false, format.stride, offset);
            glEnableVertexAttribArray(attribIndex);
            offset += format.texCount * Float.BYTES;
            attribIndex++;
        }

        if (format.colorCount > 0) {
            glVertexAttribPointer(attribIndex, format.colorCount, GL_FLOAT, false, format.stride, offset);
            glEnableVertexAttribArray(attribIndex);
            offset += format.colorCount * Float.BYTES;
            attribIndex++;
        }

        if (format.layerCount > 0) {
            glVertexAttribPointer(attribIndex, format.layerCount, GL_FLOAT, false, format.stride, offset);
            glEnableVertexAttribArray(attribIndex);
            offset += format.layerCount * Float.BYTES;
            attribIndex++;
        }

        if (format.brightnessCount > 0) {
            glVertexAttribPointer(attribIndex, format.brightnessCount, GL_FLOAT, false, format.stride, offset);
            glEnableVertexAttribArray(attribIndex);
            offset += format.brightnessCount * Float.BYTES;
            attribIndex++;
        }
    }

    /**
     * Renders the mesh.
     *
     * <h3>Why glVertexAttrib4f is needed here</h3>
     * The shared {@code generic_3d} shader has {@code layout(location=2) in vec4 a_Color}.
     * When the Tessellator draws with {@code POSITION_TEXTURE_COLOR}, attrib 2 is enabled
     * and fed real per-vertex colour data.
     *
     * <p>For static model VBOs using {@code POSITION_TEXTURE}, attrib 2 is
     * <em>not</em> enabled (there is no colour in the buffer). OpenGL's default
     * "current attribute" value for a disabled array is {@code (0, 0, 0, 1)} —
     * black with full alpha.  The fragment shader then computes:
     * <pre>
     *   texColor × a_Color = texColor × (0, 0, 0, 1) = (0, 0, 0, alpha)
     * </pre>
     * which produces a completely black (invisible-looking) model even though
     * the texture is correctly bound.
     *
     * <p>{@code glVertexAttrib4f(2, 1, 1, 1, 1)} sets the current value of
     * attribute slot 2 to white, so the fragment computes:
     * <pre>
     *   texColor × (1, 1, 1, 1) = texColor
     * </pre>
     * and the texture appears correctly.  This call is cheap (a single driver
     * state write) and only runs for formats without a colour component.
     *
     * <p>Must be called on the render thread with the correct shader active.
     */
    public void render() {

        if (!uploaded || vertexCount == 0) return;

        if (format.colorCount == 0) {
            glVertexAttrib4f(COLOR_ATTRIB_INDEX, 1.0f, 1.0f, 1.0f, 1.0f);
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
        gpuBytes    = 0;
        uploaded    = false;
    }

    private void deleteGLObjects() {
        if (vao != 0) { glDeleteVertexArrays(vao); vao = 0; }
        if (vbo != 0) { glDeleteBuffers(vbo);       vbo = 0; }
    }

    // ── Getters ───────────────────────────────────────────────────────────────

    public boolean isUploaded()   { return uploaded; }
    public boolean isEmpty()      { return vertexCount == 0; }
    public int getVertexCount()   { return vertexCount; }
    public int getBufferSize()    { return gpuBytes; }
    public VertexFormat getFormat() { return format; }

    @Override
    public String toString() {
        return String.format("MeshRenderer[verts=%d, gpu=%dKB, format=%s]",
                vertexCount, gpuBytes / 1024, format);
    }
}