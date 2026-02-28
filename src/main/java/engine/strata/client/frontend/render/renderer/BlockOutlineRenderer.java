package engine.strata.client.frontend.render.renderer;

import engine.helios.rendering.shader.ShaderManager;
import engine.helios.rendering.shader.ShaderStack;
import engine.strata.util.Identifier;
import engine.strata.util.BlockPos;
import engine.strata.util.math.BlockRaycast;
import engine.strata.world.block.model.BlockModel;
import org.joml.Matrix4f;
import org.lwjgl.BufferUtils;

import java.nio.FloatBuffer;
import java.util.Collections;
import java.util.List;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL15.*;
import static org.lwjgl.opengl.GL20.*;
import static org.lwjgl.opengl.GL30.*;

/**
 * Renders a wireframe outline around the block the player is targeting.
 *
 * The outline matches the block's actual {@link BlockModel} geometry — each
 * cuboid element in the model gets its own 12-edge wireframe.  Blocks like
 * slabs, stairs, or fences will therefore show a properly shaped outline
 * rather than a full unit cube.  If the model has no elements (air / missing)
 * the renderer falls back to a plain unit-cube outline.
 *
 * <h3>Shader requirements</h3>
 * Register before first use (e.g. in {@code ClientFrontEnd.initHelios}):
 * <pre>
 * ShaderManager.register(
 *     Identifier.ofEngine("outline"),
 *     Identifier.ofEngine("included/outline_vertex"),
 *     Identifier.ofEngine("included/outline_fragment")
 * );
 * </pre>
 *
 * <b>outline_vertex.vert</b>
 * <pre>
 * #version 330 core
 * layout(location = 0) in vec3 a_Position;
 * uniform mat4 u_Projection, u_View, u_Model;
 * void main() { gl_Position = u_Projection * u_View * u_Model * vec4(a_Position, 1.0); }
 * </pre>
 *
 * <b>outline_fragment.frag</b>
 * <pre>
 * #version 330 core
 * uniform vec4 u_Color;
 * out vec4 FragColor;
 * void main() { FragColor = u_Color; }
 * </pre>
 */
public class BlockOutlineRenderer {

    // ── Constants ─────────────────────────────────────────────────────────────

    private static final float COLOR_R = 0.0f;
    private static final float COLOR_G = 0.0f;
    private static final float COLOR_B = 0.0f;
    private static final float COLOR_A = 0.6f;

    /** Push lines slightly outside block faces to prevent z-fighting. */
    private static final float EXPAND = 0.0005f;

    private static final float LINE_WIDTH = 3f;

    /** 3 floats per vertex position. */
    private static final int FLOATS_PER_VERTEX = 3;

    /**
     * 12 edges × 2 endpoints × 3 floats per element.
     * Pre-computed for convenience.
     */
    private static final int FLOATS_PER_ELEMENT = 12 * 2 * FLOATS_PER_VERTEX; // 72

    /**
     * Maximum number of elements supported in a single outline draw.
     * Covers any realistic block model with plenty of headroom.
     */
    private static final int MAX_ELEMENTS = 128;

    /** Fallback used for air / missing-model blocks. */
    private static final List<BlockModel.Element> UNIT_CUBE_FALLBACK =
            Collections.singletonList(makeFallbackElement());

    // ── GPU objects ───────────────────────────────────────────────────────────

    private int vao = 0;
    private int vbo = 0;

    /** Actual number of GL_LINES vertices in the current VBO, varies per model. */
    private int uploadedVertexCount = 0;

    // ── Upload cache ──────────────────────────────────────────────────────────

    private int        lastX       = Integer.MIN_VALUE;
    private int        lastY       = Integer.MIN_VALUE;
    private int        lastZ       = Integer.MIN_VALUE;
    private Identifier lastModelId = null;

    /** Reused CPU-side buffer — sized for the maximum possible upload. */
    private final FloatBuffer buf =
            BufferUtils.createFloatBuffer(MAX_ELEMENTS * FLOATS_PER_ELEMENT);

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Draws the outline for the targeted block.
     * Must be called on the render thread, after the opaque chunk pass.
     *
     * @param result     raycast result — pass {@code null} or a miss to skip drawing
     * @param model      the resolved {@link BlockModel} for the targeted block;
     *                   pass {@code null} to use the unit-cube fallback
     * @param projection projection matrix
     * @param view       view (camera) matrix
     */
    public void render(BlockRaycast.RaycastResult result,
                       BlockModel model,
                       Matrix4f projection,
                       Matrix4f view) {
        if (result == null || !result.isHit()) return;

        BlockPos pos = result.getBlockPos();
        if (pos == null) return;

        ShaderStack shader = ShaderManager.use(Identifier.ofEngine("outline"));
        if (shader == null) return;

        // If the model is null or has no geometry the block is air / was just broken —
        // skip drawing rather than falling back to a phantom unit-cube outline.
        if (model == null || model.getElements().isEmpty()) return;

        List<BlockModel.Element> elements = resolveElements(model);

        ensureGLObjects();
        uploadIfChanged(pos, elements, model);

        if (uploadedVertexCount == 0) return;

        // ── Shader uniforms ───────────────────────────────────────────────────
        shader.use();
        shader.setUniform("u_Projection", projection);
        shader.setUniform("u_View",       view);
        shader.setUniform("u_Model",      new Matrix4f().identity());

        int colorLoc = glGetUniformLocation(glGetInteger(GL_CURRENT_PROGRAM), "u_Color");
        if (colorLoc != -1) {
            glUniform4f(colorLoc, COLOR_R, COLOR_G, COLOR_B, COLOR_A);
        }

        // ── GL state ──────────────────────────────────────────────────────────
        glEnable(GL_DEPTH_TEST);
        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
        glLineWidth(LINE_WIDTH);
        glEnable(GL_POLYGON_OFFSET_LINE);
        glPolygonOffset(-1.0f, -1.0f);

        // ── Draw ──────────────────────────────────────────────────────────────
        glBindVertexArray(vao);
        glDrawArrays(GL_LINES, 0, uploadedVertexCount);
        glBindVertexArray(0);

        // ── Restore state ─────────────────────────────────────────────────────
        glDisable(GL_POLYGON_OFFSET_LINE);
        glDisable(GL_BLEND);
    }

    /** Free GPU resources. Call on shutdown or GL context loss. */
    public void dispose() {
        if (vao != 0) { glDeleteVertexArrays(vao); vao = 0; }
        if (vbo != 0) { glDeleteBuffers(vbo);       vbo = 0; }
        uploadedVertexCount = 0;
    }

    // ── Private ───────────────────────────────────────────────────────────────

    /**
     * Returns the element list to outline.
     * Falls back to a single full-unit-cube element for air / missing models.
     */
    private static List<BlockModel.Element> resolveElements(BlockModel model) {
        if (model == null || model.getElements().isEmpty()) {
            return UNIT_CUBE_FALLBACK;
        }
        return model.getElements();
    }

    /** Lazily creates the VAO and allocates the VBO at maximum size. */
    private void ensureGLObjects() {
        if (vao != 0) return;

        vao = glGenVertexArrays();
        glBindVertexArray(vao);

        vbo = glGenBuffers();
        glBindBuffer(GL_ARRAY_BUFFER, vbo);
        // Pre-allocate the maximum size; data is written via glBufferSubData.
        glBufferData(GL_ARRAY_BUFFER, (long) MAX_ELEMENTS * FLOATS_PER_ELEMENT * Float.BYTES,
                GL_DYNAMIC_DRAW);

        // Attribute 0: position vec3, tightly packed
        glVertexAttribPointer(0, 3, GL_FLOAT, false, 3 * Float.BYTES, 0L);
        glEnableVertexAttribArray(0);

        glBindBuffer(GL_ARRAY_BUFFER, 0);
        glBindVertexArray(0);
    }

    /**
     * Rebuilds and uploads the wireframe geometry only when the targeted block
     * position or model has changed since the last frame.
     */
    private void uploadIfChanged(BlockPos pos,
                                 List<BlockModel.Element> elements,
                                 BlockModel model) {
        Identifier modelId = (model != null) ? model.getId() : null;

        boolean posChanged   = pos.getX() != lastX || pos.getY() != lastY || pos.getZ() != lastZ;
        boolean modelChanged = !java.util.Objects.equals(modelId, lastModelId);
        if (!posChanged && !modelChanged) return;

        lastX       = pos.getX();
        lastY       = pos.getY();
        lastZ       = pos.getZ();
        lastModelId = modelId;

        buf.clear();

        int elementCount = Math.min(elements.size(), MAX_ELEMENTS);
        for (int i = 0; i < elementCount; i++) {
            appendElementEdges(buf, elements.get(i), pos);
        }

        buf.flip();

        uploadedVertexCount = buf.limit() / FLOATS_PER_VERTEX;

        glBindBuffer(GL_ARRAY_BUFFER, vbo);
        glBufferSubData(GL_ARRAY_BUFFER, 0L, buf);
        glBindBuffer(GL_ARRAY_BUFFER, 0);
    }

    /**
     * Appends 12 edges (24 vertices, 72 floats) to {@code buf} for one element.
     *
     * Element bounds are in 0–16 block space and are normalised to 0–1 before
     * being offset to the block's world position plus the anti-z-fight expand.
     */
    private static void appendElementEdges(FloatBuffer buf,
                                           BlockModel.Element element,
                                           BlockPos pos) {
        float bx = pos.getX();
        float by = pos.getY();
        float bz = pos.getZ();

        float x0 = bx + element.fx() - EXPAND;
        float y0 = by + element.fy() - EXPAND;
        float z0 = bz + element.fz() - EXPAND;
        float x1 = bx + element.tx() + EXPAND;
        float y1 = by + element.ty() + EXPAND;
        float z1 = bz + element.tz() + EXPAND;

        // Bottom face (y = y0)
        edge(buf, x0,y0,z0,  x1,y0,z0);
        edge(buf, x1,y0,z0,  x1,y0,z1);
        edge(buf, x1,y0,z1,  x0,y0,z1);
        edge(buf, x0,y0,z1,  x0,y0,z0);

        // Top face (y = y1)
        edge(buf, x0,y1,z0,  x1,y1,z0);
        edge(buf, x1,y1,z0,  x1,y1,z1);
        edge(buf, x1,y1,z1,  x0,y1,z1);
        edge(buf, x0,y1,z1,  x0,y1,z0);

        // Vertical pillars
        edge(buf, x0,y0,z0,  x0,y1,z0);
        edge(buf, x1,y0,z0,  x1,y1,z0);
        edge(buf, x1,y0,z1,  x1,y1,z1);
        edge(buf, x0,y0,z1,  x0,y1,z1);
    }

    private static void edge(FloatBuffer b,
                             float x0, float y0, float z0,
                             float x1, float y1, float z1) {
        b.put(x0).put(y0).put(z0);
        b.put(x1).put(y1).put(z1);
    }

    /**
     * Builds a synthetic full-unit-cube {@link BlockModel.Element} used as the
     * fallback when the targeted block has no model or no elements.
     */
    private static BlockModel.Element makeFallbackElement() {
        return new BlockModel.Element(
                new org.joml.Vector3f(0, 0, 0),
                new org.joml.Vector3f(16, 16, 16),
                java.util.Collections.emptyMap()
        );
    }
}