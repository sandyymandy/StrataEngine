package engine.strata.client.frontend.render.renderer;

import engine.helios.physics.AABB;
import engine.helios.rendering.shader.ShaderManager;
import engine.helios.rendering.shader.ShaderStack;
import engine.strata.util.Identifier;
import org.joml.Matrix4f;
import org.lwjgl.BufferUtils;

import java.nio.FloatBuffer;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL15.*;
import static org.lwjgl.opengl.GL20.*;
import static org.lwjgl.opengl.GL30.*;

/**
 * Debug renderer for visualizing AABBs (Axis-Aligned Bounding Boxes).
 * Useful for debugging collision detection, frustum culling, and entity bounds.
 *
 * <h3>Usage Example</h3>
 * <pre>
 * // In your entity render loop:
 * AABB worldAABB = entity.getWorldSpaceBoundingBox(partialTicks);
 * boundingBoxDebugRenderer.render(
 *     worldAABB,
 *     projection,
 *     view,
 *     BoundingBoxDebugRenderer.Color.GREEN  // Entity is visible
 * );
 * </pre>
 *
 * <h3>Shader Requirements</h3>
 * Uses the same shader as BlockOutlineRenderer:
 * <pre>
 * ShaderManager.register(
 *     Identifier.ofEngine("outline"),
 *     Identifier.ofEngine("included/outline_vertex"),
 *     Identifier.ofEngine("included/outline_fragment")
 * );
 * </pre>
 */
public class BoundingBoxDebugRenderer {

    // ── Predefined Colors ─────────────────────────────────────────────────────

    public enum Color {
        RED(1.0f, 0.0f, 0.0f, 0.6f),       // Culled/invisible
        GREEN(0.0f, 1.0f, 0.0f, 0.6f),     // Visible
        BLUE(0.0f, 0.5f, 1.0f, 0.6f),      // Entity AABB
        YELLOW(1.0f, 1.0f, 0.0f, 0.6f),    // Warning/special
        CYAN(0.0f, 1.0f, 1.0f, 0.6f),      // Debug info
        MAGENTA(1.0f, 0.0f, 1.0f, 0.6f),   // Collision
        WHITE(1.0f, 1.0f, 1.0f, 0.6f),     // Default
        ORANGE(1.0f, 0.5f, 0.0f, 0.6f);    // Alert

        public final float r, g, b, a;

        Color(float r, float g, float b, float a) {
            this.r = r;
            this.g = g;
            this.b = b;
            this.a = a;
        }
    }

    // ── Constants ─────────────────────────────────────────────────────────────

    private static final float LINE_WIDTH = 2.0f;

    /** 3 floats per vertex position. */
    private static final int FLOATS_PER_VERTEX = 3;

    /** 12 edges × 2 endpoints × 3 floats = 72 floats per box. */
    private static final int FLOATS_PER_BOX = 12 * 2 * FLOATS_PER_VERTEX;

    /** Maximum number of boxes that can be batched in a single draw call. */
    private static final int MAX_BOXES_PER_BATCH = 256;

    // ── GPU Objects ───────────────────────────────────────────────────────────

    private int vao = 0;
    private int vbo = 0;

    /** Reused CPU-side buffer for batching multiple boxes. */
    private final FloatBuffer vertexBuffer =
            BufferUtils.createFloatBuffer(MAX_BOXES_PER_BATCH * FLOATS_PER_BOX);

    /** Track how many vertices are in the current batch. */
    private int batchVertexCount = 0;

    // ── Render State ──────────────────────────────────────────────────────────

    private Matrix4f currentProjection = null;
    private Matrix4f currentView = null;
    private Color currentColor = Color.WHITE;

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Renders a single AABB with the specified color.
     *
     * @param aabb The bounding box to render
     * @param projection Projection matrix
     * @param view View matrix
     * @param color Color preset for the box
     */
    public void render(AABB aabb, Matrix4f projection, Matrix4f view, Color color) {
        if (aabb == null) return;

        ensureGLObjects();

        ShaderStack shader = ShaderManager.use(Identifier.ofEngine("outline"));
        if (shader == null) return;

        // Begin new batch if matrices or color changed
        if (!projection.equals(currentProjection) ||
                !view.equals(currentView) ||
                currentColor != color ||
                batchVertexCount >= MAX_BOXES_PER_BATCH * 24) {

            flush(shader);
            currentProjection = new Matrix4f(projection);
            currentView = new Matrix4f(view);
            currentColor = color;
        }

        // Add box to batch
        appendBoxEdges(vertexBuffer, aabb);
        batchVertexCount += 24; // 12 edges × 2 vertices

        // Auto-flush if batch is full
        if (batchVertexCount >= MAX_BOXES_PER_BATCH * 24) {
            flush(shader);
        }
    }

    /**
     * Renders a single AABB with custom RGBA color.
     */
    public void render(AABB aabb, Matrix4f projection, Matrix4f view,
                       float r, float g, float b, float a) {
        if (aabb == null) return;

        ensureGLObjects();

        ShaderStack shader = ShaderManager.use(Identifier.ofEngine("outline"));
        if (shader == null) return;

        // Flush any existing batch since we're using custom color
        if (batchVertexCount > 0) {
            flush(shader);
        }

        vertexBuffer.clear();
        appendBoxEdges(vertexBuffer, aabb);
        vertexBuffer.flip();

        uploadAndDraw(shader, projection, view, r, g, b, a, 24);
    }

    /**
     * Begins a batch rendering session.
     * Call this before rendering multiple boxes, then call {@link #endBatch()}.
     */
    public void beginBatch(Matrix4f projection, Matrix4f view, Color color) {
        ensureGLObjects();
        currentProjection = new Matrix4f(projection);
        currentView = new Matrix4f(view);
        currentColor = color;
        vertexBuffer.clear();
        batchVertexCount = 0;
    }

    /**
     * Adds an AABB to the current batch.
     * Must be called between {@link #beginBatch} and {@link #endBatch}.
     */
    public void addBox(AABB aabb) {
        if (aabb == null) return;

        // Auto-flush if batch is full
        if (batchVertexCount >= MAX_BOXES_PER_BATCH * 24) {
            ShaderStack shader = ShaderManager.use(Identifier.ofEngine("outline"));
            if (shader != null) {
                flush(shader);
            }
        }

        appendBoxEdges(vertexBuffer, aabb);
        batchVertexCount += 24;
    }

    /**
     * Ends the batch and renders all accumulated boxes.
     */
    public void endBatch() {
        if (batchVertexCount == 0) return;

        ShaderStack shader = ShaderManager.use(Identifier.ofEngine("outline"));
        if (shader != null) {
            flush(shader);
        }
    }

    /**
     * Renders multiple AABBs in a single draw call (most efficient).
     *
     * @param boxes Array of AABBs to render
     * @param projection Projection matrix
     * @param view View matrix
     * @param color Color for all boxes
     */
    public void renderBatch(AABB[] boxes, Matrix4f projection, Matrix4f view, Color color) {
        if (boxes == null || boxes.length == 0) return;

        beginBatch(projection, view, color);
        for (AABB box : boxes) {
            addBox(box);
        }
        endBatch();
    }

    /**
     * Flushes the current batch and renders it.
     */
    private void flush(ShaderStack shader) {
        if (batchVertexCount == 0) return;

        vertexBuffer.flip();

        uploadAndDraw(shader, currentProjection, currentView,
                currentColor.r, currentColor.g, currentColor.b, currentColor.a,
                batchVertexCount);

        vertexBuffer.clear();
        batchVertexCount = 0;
    }

    /**
     * Uploads vertex data and issues the draw call.
     */
    private void uploadAndDraw(ShaderStack shader, Matrix4f projection, Matrix4f view,
                               float r, float g, float b, float a, int vertexCount) {
        // Upload vertices to GPU
        glBindBuffer(GL_ARRAY_BUFFER, vbo);
        glBufferSubData(GL_ARRAY_BUFFER, 0L, vertexBuffer);
        glBindBuffer(GL_ARRAY_BUFFER, 0);

        // Set shader uniforms
        shader.use();
        shader.setUniform("u_Projection", projection);
        shader.setUniform("u_View", view);
        shader.setUniform("u_Model", new Matrix4f().identity());

        int colorLoc = glGetUniformLocation(glGetInteger(GL_CURRENT_PROGRAM), "u_Color");
        if (colorLoc != -1) {
            glUniform4f(colorLoc, r, g, b, a);
        }

        // Set GL state
        glEnable(GL_DEPTH_TEST);
        glDepthFunc(GL_LEQUAL); // Allow drawing on surfaces
        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
        glLineWidth(LINE_WIDTH);

        // Draw
        glBindVertexArray(vao);
        glDrawArrays(GL_LINES, 0, vertexCount);
        glBindVertexArray(0);

        // Restore state
        glDisable(GL_BLEND);
        glDepthFunc(GL_LESS);
    }

    /** Free GPU resources. */
    public void dispose() {
        if (vao != 0) {
            glDeleteVertexArrays(vao);
            vao = 0;
        }
        if (vbo != 0) {
            glDeleteBuffers(vbo);
            vbo = 0;
        }
        batchVertexCount = 0;
    }

    // ── Private ───────────────────────────────────────────────────────────────

    /** Lazily creates VAO and VBO. */
    private void ensureGLObjects() {
        if (vao != 0) return;

        vao = glGenVertexArrays();
        glBindVertexArray(vao);

        vbo = glGenBuffers();
        glBindBuffer(GL_ARRAY_BUFFER, vbo);
        glBufferData(GL_ARRAY_BUFFER,
                (long) MAX_BOXES_PER_BATCH * FLOATS_PER_BOX * Float.BYTES,
                GL_DYNAMIC_DRAW);

        // Attribute 0: position (vec3)
        glVertexAttribPointer(0, 3, GL_FLOAT, false, 3 * Float.BYTES, 0L);
        glEnableVertexAttribArray(0);

        glBindBuffer(GL_ARRAY_BUFFER, 0);
        glBindVertexArray(0);
    }

    /**
     * Appends the 12 edges of an AABB to the vertex buffer.
     * Does NOT flip the buffer - caller must handle that.
     */
    private static void appendBoxEdges(FloatBuffer buf, AABB aabb) {
        float x0 = (float) aabb.getMinX();
        float y0 = (float) aabb.getMinY();
        float z0 = (float) aabb.getMinZ();
        float x1 = (float) aabb.getMaxX();
        float y1 = (float) aabb.getMaxY();
        float z1 = (float) aabb.getMaxZ();

        // Bottom face (y = y0)
        edge(buf, x0, y0, z0, x1, y0, z0);
        edge(buf, x1, y0, z0, x1, y0, z1);
        edge(buf, x1, y0, z1, x0, y0, z1);
        edge(buf, x0, y0, z1, x0, y0, z0);

        // Top face (y = y1)
        edge(buf, x0, y1, z0, x1, y1, z0);
        edge(buf, x1, y1, z0, x1, y1, z1);
        edge(buf, x1, y1, z1, x0, y1, z1);
        edge(buf, x0, y1, z1, x0, y1, z0);

        // Vertical edges
        edge(buf, x0, y0, z0, x0, y1, z0);
        edge(buf, x1, y0, z0, x1, y1, z0);
        edge(buf, x1, y0, z1, x1, y1, z1);
        edge(buf, x0, y0, z1, x0, y1, z1);
    }

    private static void edge(FloatBuffer b,
                             float x0, float y0, float z0,
                             float x1, float y1, float z1) {
        b.put(x0).put(y0).put(z0);
        b.put(x1).put(y1).put(z1);
    }
}