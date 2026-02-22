package engine.strata.world.chunk.render;

import engine.helios.rendering.MeshRenderer;
import engine.helios.rendering.vertex.VertexFormat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.FloatBuffer;

/**
 * Holds mesh data for a single chunk and handles rendering through Helios.
 *
 * Vertex layout matches POSITION_TEXTURE_LAYER_BRIGHTNESS format:
 *   [ x, y, z,   u, v,   layer,   brightness ]
 *    ^-pos(3)-^  ^uv(2)^ ^-f32-^  ^---f32---^
 */
public class ChunkMesh {
    private static final Logger LOGGER = LoggerFactory.getLogger("ChunkMesh");

    // Vertex format for chunk rendering
    private static final VertexFormat CHUNK_FORMAT = VertexFormat.POSITION_TEXTURE_LAYER_BRIGHTNESS;

    private final MeshRenderer renderer;
    private final int chunkX;
    private final int chunkZ;

    public ChunkMesh(int chunkX, int chunkZ) {
        this.chunkX = chunkX;
        this.chunkZ = chunkZ;
        this.renderer = new MeshRenderer();
    }

    /**
     * Upload vertex data to the GPU through Helios.
     * Must be called on the render thread.
     */
    public void upload(FloatBuffer data, int vertexCount) {
        renderer.upload(data, vertexCount, CHUNK_FORMAT);
    }

    /**
     * Render this chunk mesh through Helios.
     * Must be called on the render thread with the chunk shader bound.
     */
    public void render() {
        renderer.render();
    }

    /**
     * Free all GPU resources through Helios.
     */
    public void dispose() {
        renderer.dispose();
    }

    // ── Getters ───────────────────────────────────────────────────────────────

    public boolean isUploaded() { return renderer.isUploaded(); }
    public boolean isEmpty() { return renderer.isEmpty(); }
    public int getVertexCount() { return renderer.getVertexCount(); }
    public int getBufferSize() { return renderer.getBufferSize(); }
    public int getChunkX() { return chunkX; }
    public int getChunkZ() { return chunkZ; }

    @Override
    public String toString() {
        return String.format("ChunkMesh[%d,%d] %s", chunkX, chunkZ, renderer);
    }
}