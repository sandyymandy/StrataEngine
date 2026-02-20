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
 * Vertex layout — 7 floats per vertex, matching the chunk shader exactly:
 *
 *   [ x, y, z,   u, v,   layer,   brightness ]
 *    ^-pos(3)-^  ^uv(2)^ ^-f32-^  ^---f32---^
 *
 *   stride  = 7 * 4 = 28 bytes
 *   attrib 0 (a_Position)   : offset  0, size 3  (vec3)
 *   attrib 1 (a_TexCoord)   : offset 12, size 2  (vec2)
 *   attrib 2 (a_Layer)      : offset 20, size 1  (float – texture array layer)
 *   attrib 3 (a_Brightness) : offset 24, size 1  (float)
 *
 * u/v are always [0,0]→[1,1] — the layer index selects the correct texture
 * slice in the GL_TEXTURE_2D_ARRAY, so no atlas sub-division is needed.
 */
public class ChunkMesh {
    private static final Logger LOGGER = LoggerFactory.getLogger("ChunkMesh");

    // 7 floats per vertex: pos(3) + uv(2) + layer(1) + brightness(1)
    // Must stay in sync with ChunkMeshBuilder.FLOATS_PER_VERTEX.
    static final int FLOATS_PER_VERTEX = 7;

    private static final int STRIDE             = FLOATS_PER_VERTEX * Float.BYTES; // 28
    private static final int OFFSET_POS         = 0;
    private static final int OFFSET_UV          = 3 * Float.BYTES;  // 12
    private static final int OFFSET_LAYER       = 5 * Float.BYTES;  // 20
    private static final int OFFSET_BRIGHTNESS  = 6 * Float.BYTES;  // 24

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

        // Attribute 1: texture UV (vec2) — always [0,0]→[1,1] per face
        glVertexAttribPointer(1, 2, GL_FLOAT, false, STRIDE, OFFSET_UV);
        glEnableVertexAttribArray(1);

        // Attribute 2: texture array layer index (float)
        // Shader: "layout(location = 2) in float a_Layer"
        glVertexAttribPointer(2, 1, GL_FLOAT, false, STRIDE, OFFSET_LAYER);
        glEnableVertexAttribArray(2);

        // Attribute 3: brightness (float)
        // Shader: "layout(location = 3) in float a_Brightness"
        glVertexAttribPointer(3, 1, GL_FLOAT, false, STRIDE, OFFSET_BRIGHTNESS);
        glEnableVertexAttribArray(3);

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