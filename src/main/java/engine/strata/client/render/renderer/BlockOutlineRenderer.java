package engine.strata.client.render.renderer;

import engine.helios.rendering.shader.ShaderManager;
import engine.helios.rendering.shader.ShaderStack;
import engine.strata.util.Identifier;
import engine.strata.util.math.BlockPos;
import engine.strata.util.math.BlockRaycast;
import org.joml.Matrix4f;
import org.lwjgl.BufferUtils;

import java.nio.FloatBuffer;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL15.*;
import static org.lwjgl.opengl.GL20.*;
import static org.lwjgl.opengl.GL30.*;

/**
 * Renders a wireframe cube outline around the block the player is targeting.
 *
 * Core-profile safe: uses a VAO/VBO + GL_LINES, no deprecated immediate-mode calls.
 *
 * Requires these shader files on the classpath (register in ClientFrontEnd.initHelios):
 *   ShaderManager.register(
 *       Identifier.ofEngine("outline"),
 *       Identifier.ofEngine("included/outline_vertex"),   // outline_vertex.vert
 *       Identifier.ofEngine("included/outline_fragment")  // outline_fragment.frag
 *   );
 *
 * outline_vertex.vert:
 *   #version 330 core
 *   layout(location = 0) in vec3 a_Position;
 *   uniform mat4 u_Projection, u_View, u_Model;
 *   void main() { gl_Position = u_Projection * u_View * u_Model * vec4(a_Position, 1.0); }
 *
 * outline_fragment.frag:
 *   #version 330 core
 *   uniform vec4 u_Color;
 *   out vec4 FragColor;
 *   void main() { FragColor = u_Color; }
 */
public class BlockOutlineRenderer {

    // Outline color (black, 80% opaque)
    private static final float COLOR_R = 0.0f;
    private static final float COLOR_G = 0.0f;
    private static final float COLOR_B = 0.0f;
    private static final float COLOR_A = 0.6f;

    // Push lines just in front of block faces to prevent z-fighting
    private static final float EXPAND = 0.0005f;


    private static final float LINE_WIDTH = 3f;

    // 12 edges × 2 endpoints × 3 floats (xyz) = 72 floats, 24 vertices
    private static final int VERTEX_COUNT = 24;
    private static final int FLOAT_COUNT  = VERTEX_COUNT * 3;

    private int vao = 0;
    private int vbo = 0;

    // Only re-upload geometry when the targeted block actually changes
    private int lastX = Integer.MIN_VALUE;
    private int lastY = Integer.MIN_VALUE;
    private int lastZ = Integer.MIN_VALUE;

    private final FloatBuffer buf = BufferUtils.createFloatBuffer(FLOAT_COUNT);

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Draws the 12-edge wireframe around the targeted block.
     * Call after the opaque chunk pass, on the render thread.
     */
    public void render(BlockRaycast.RaycastResult result,
                       Matrix4f projection,
                       Matrix4f view) {
        if (result == null || !result.isHit()) return;

        BlockPos pos = result.getBlockPos();
        if (pos == null) return;

        ShaderStack shader = ShaderManager.use(Identifier.ofEngine("outline"));
        if (shader == null) return; // not registered yet — skip silently

        ensureGLObjects();
        uploadIfChanged(pos);

        // Bind shader and set uniforms
        shader.use();
        shader.setUniform("u_Projection", projection);
        shader.setUniform("u_View",       view);
        shader.setUniform("u_Model",      new Matrix4f().identity());

        // Set u_Color via raw GL (ShaderStack may not expose a vec4 overload)
        int colorLoc = glGetUniformLocation(getCurrentProgram(), "u_Color");
        if (colorLoc != -1) {
            glUniform4f(colorLoc, COLOR_R, COLOR_G, COLOR_B, COLOR_A);
        }

        // GL state
        glEnable(GL_DEPTH_TEST);
        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
        glLineWidth(LINE_WIDTH);
        glEnable(GL_POLYGON_OFFSET_LINE);
        glPolygonOffset(-1.0f, -1.0f);

        // Draw
        glBindVertexArray(vao);
        glDrawArrays(GL_LINES, 0, VERTEX_COUNT);
        glBindVertexArray(0);

        // Restore state
        glDisable(GL_POLYGON_OFFSET_LINE);
        glDisable(GL_BLEND);
    }

    /** Free GPU resources. Call on shutdown. */
    public void dispose() {
        if (vao != 0) { glDeleteVertexArrays(vao); vao = 0; }
        if (vbo != 0) { glDeleteBuffers(vbo);       vbo = 0; }
    }

    // ── Private ───────────────────────────────────────────────────────────────

    private void ensureGLObjects() {
        if (vao != 0) return;

        vao = glGenVertexArrays();
        glBindVertexArray(vao);

        vbo = glGenBuffers();
        glBindBuffer(GL_ARRAY_BUFFER, vbo);
        // Allocate max size up front; data filled via glBufferSubData later
        glBufferData(GL_ARRAY_BUFFER, (long) FLOAT_COUNT * Float.BYTES, GL_DYNAMIC_DRAW);

        // Attribute 0: position vec3, tightly packed
        glVertexAttribPointer(0, 3, GL_FLOAT, false, 3 * Float.BYTES, 0L);
        glEnableVertexAttribArray(0);

        glBindBuffer(GL_ARRAY_BUFFER, 0);
        glBindVertexArray(0);
    }

    private void uploadIfChanged(BlockPos pos) {
        if (pos.getX() == lastX && pos.getY() == lastY && pos.getZ() == lastZ) return;

        lastX = pos.getX();
        lastY = pos.getY();
        lastZ = pos.getZ();

        float x0 = pos.getX()     - EXPAND;
        float y0 = pos.getY()     - EXPAND;
        float z0 = pos.getZ()     - EXPAND;
        float x1 = pos.getX() + 1 + EXPAND;
        float y1 = pos.getY() + 1 + EXPAND;
        float z1 = pos.getZ() + 1 + EXPAND;

        buf.clear();

        // Bottom face
        edge(buf, x0,y0,z0, x1,y0,z0);
        edge(buf, x1,y0,z0, x1,y0,z1);
        edge(buf, x1,y0,z1, x0,y0,z1);
        edge(buf, x0,y0,z1, x0,y0,z0);

        // Top face
        edge(buf, x0,y1,z0, x1,y1,z0);
        edge(buf, x1,y1,z0, x1,y1,z1);
        edge(buf, x1,y1,z1, x0,y1,z1);
        edge(buf, x0,y1,z1, x0,y1,z0);

        // Verticals
        edge(buf, x0,y0,z0, x0,y1,z0);
        edge(buf, x1,y0,z0, x1,y1,z0);
        edge(buf, x1,y0,z1, x1,y1,z1);
        edge(buf, x0,y0,z1, x0,y1,z1);

        buf.flip();

        glBindBuffer(GL_ARRAY_BUFFER, vbo);
        glBufferSubData(GL_ARRAY_BUFFER, 0L, buf);
        glBindBuffer(GL_ARRAY_BUFFER, 0);
    }

    private static void edge(FloatBuffer b,
                             float x0, float y0, float z0,
                             float x1, float y1, float z1) {
        b.put(x0).put(y0).put(z0);
        b.put(x1).put(y1).put(z1);
    }

    /** Returns the currently bound GL program (set by shader.use() above). */
    private static int getCurrentProgram() {
        return glGetInteger(GL_CURRENT_PROGRAM);
    }
}