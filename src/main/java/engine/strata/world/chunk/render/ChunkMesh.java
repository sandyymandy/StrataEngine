package engine.strata.world.chunk.render;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.FloatBuffer;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL15.*;
import static org.lwjgl.opengl.GL20.*;
import static org.lwjgl.opengl.GL30.*;

/**
 * Holds the VAO/VBO for one chunk's geometry and issues the draw call.
 *
 * Vertex layout — 6 floats per vertex, matching the chunk shader exactly:
 *
 *   [ x, y, z,   u, v,   brightness ]
 *    ^-pos(3)-^  ^uv(2)^ ^--float--^
 *
 *   stride  = 6 * 4 = 24 bytes
 *   attrib 0 (a_Position)   : offset  0, size 3
 *   attrib 1 (a_TexCoord)   : offset 12, size 2
 *   attrib 2 (a_Brightness) : offset 20, size 1  ← was size 4 (vec4), now size 1 (float)
 *
 * FIX: The old code declared FLOATS_PER_VERTEX = 9 and bound attrib 2 with
 * size=4 (rgba), but the vertex shader has "in float a_Brightness" — a single
 * float.  That mismatch made the GPU read the stride wrong: every vertex after
 * the first had its position, UV, and brightness read from the wrong bytes,
 * producing scrambled geometry and wrong solid colours instead of textures.
 */
public class ChunkMesh {
    private static final Logger LOGGER = LoggerFactory.getLogger("ChunkMesh");

    // Shared with ChunkMesher for vertexCount calculation.
    // FIX: 6 floats per vertex (was 9). Must match ChunkMesher.FLOATS_PER_VERTEX.
    static final int FLOATS_PER_VERTEX = 6;  // pos(3) + uv(2) + brightness(1)

    private static final int STRIDE          = FLOATS_PER_VERTEX * Float.BYTES; // 24
    private static final int OFFSET_POS      = 0;
    private static final int OFFSET_UV       = 3 * Float.BYTES;  // 12
    private static final int OFFSET_BRIGHTNESS = 5 * Float.BYTES; // 20

    private int vao         = 0;
    private int vbo         = 0;
    private int vertexCount = 0;
    private int gpuBytes    = 0;

    final int chunkX;
    final int chunkZ;

    private boolean uploaded = false;

    public ChunkMesh(int chunkX, int chunkZ) {
        this.chunkX = chunkX;
        this.chunkZ = chunkZ;
    }

    /**
     * Upload vertex data to the GPU.
     * Must be called on the render thread.
     */
    public void upload(FloatBuffer data, int vertexCount) {
        // Always free old GL objects first (handles re-upload and zero-vertex case).
        if (vao != 0 || vbo != 0) deleteGLObjects();

        if (vertexCount == 0) {
            this.vertexCount = 0;
            this.gpuBytes    = 0;
            this.uploaded    = false;
            return;
        }

        vao = glGenVertexArrays();
        glBindVertexArray(vao);

        vbo = glGenBuffers();
        glBindBuffer(GL_ARRAY_BUFFER, vbo);
        glBufferData(GL_ARRAY_BUFFER, data, GL_STATIC_DRAW);

        gpuBytes = data.limit() * Float.BYTES;

        // Attribute 0: position (vec3)
        glVertexAttribPointer(0, 3, GL_FLOAT, false, STRIDE, OFFSET_POS);
        glEnableVertexAttribArray(0);

        // Attribute 1: texture UV (vec2)
        glVertexAttribPointer(1, 2, GL_FLOAT, false, STRIDE, OFFSET_UV);
        glEnableVertexAttribArray(1);

        // Attribute 2: brightness (float) — FIX: size 1, not 4.
        // Shader: "layout(location = 2) in float a_Brightness"
        glVertexAttribPointer(2, 1, GL_FLOAT, false, STRIDE, OFFSET_BRIGHTNESS);
        glEnableVertexAttribArray(2);

        glBindBuffer(GL_ARRAY_BUFFER, 0);
        glBindVertexArray(0);

        this.vertexCount = vertexCount;
        this.uploaded    = true;
    }

    /** Draw this mesh. Must be called on the render thread with the chunk shader bound. */
    public void render() {
        if (!uploaded || vertexCount == 0) return;
        glBindVertexArray(vao);
        glDrawArrays(GL_TRIANGLES, 0, vertexCount);
        glBindVertexArray(0);
    }

    /** Free all GPU resources. Idempotent — safe to call multiple times. */
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

    public boolean isUploaded()     { return uploaded; }
    public boolean isEmpty()        { return vertexCount == 0; }
    public int     getVertexCount() { return vertexCount; }
    public int     getBufferSize()  { return gpuBytes; }
    public int     getChunkX()      { return chunkX; }
    public int     getChunkZ()      { return chunkZ; }

    @Override
    public String toString() {
        return String.format("ChunkMesh[%d,%d] verts=%d gpu=%dKB",
                chunkX, chunkZ, vertexCount, gpuBytes / 1024);
    }
}