package engine.strata.world.chunk.render;

import engine.strata.client.StrataClient;
import engine.strata.world.block.Block;
import engine.strata.world.block.BlockTexture;
import engine.strata.world.block.Blocks;
import engine.strata.world.block.DynamicTextureAtlas;
import engine.strata.world.chunk.Chunk;
import engine.strata.world.chunk.ChunkManager;
import engine.strata.world.chunk.SubChunk;
import org.lwjgl.BufferUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.FloatBuffer;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Builds chunk meshes on a dedicated thread.
 *
 * Vertex layout per vertex: [ x, y, z,  u, v,  brightness ] — 6 floats.
 *
 * This matches the chunk vertex shader exactly:
 *   layout(location = 0) in vec3 a_Position;     // x, y, z
 *   layout(location = 1) in vec2 a_TexCoord;     // u, v
 *   layout(location = 2) in float a_Brightness;  // single float, NOT vec4
 *
 * The old layout wrote 9 floats (pos + uv + rgba) but the shader only
 * declared a_Brightness as a float at location 2 — causing the GPU to
 * misread the stride, garbling positions and UVs for every vertex after
 * the first, and producing wrong colours across the whole mesh.
 */
public class ChunkMeshBuilder implements Runnable {
    private static final Logger LOGGER = LoggerFactory.getLogger("ChunkMesh");

    private static final int INITIAL_FLOAT_CAPACITY = (4 * 1024 * 1024) / Float.BYTES;

    // FIX: 6 floats per vertex to match shader layout (was 9).
    // pos(3) + uv(2) + brightness(1) = 6
    static final int FLOATS_PER_VERTEX = 6;

    private final ChunkManager chunkManager;
    private final DynamicTextureAtlas textureAtlas;

    private final Set<ChunkManager.ChunkPos> meshingQueue = ConcurrentHashMap.newKeySet();
    private final Queue<MeshUploadTask>      uploadQueue  = new ConcurrentLinkedQueue<>();

    private final AtomicBoolean running = new AtomicBoolean(true);
    private final AtomicBoolean paused  = new AtomicBoolean(false);

    private int  chunksMesshed   = 0;
    private long totalMeshTimeNs = 0;

    public ChunkMeshBuilder(ChunkManager chunkManager, DynamicTextureAtlas textureAtlas) {
        this.chunkManager = chunkManager;
        this.textureAtlas = textureAtlas;
        LOGGER.info("ChunkMesher initialized");
    }

    // ── Thread loop ──────────────────────────────────────────────────────────

    @Override
    public void run() {
        Thread.currentThread().setName("ChunkMesh");
        LOGGER.info("Chunk meshing thread started");

        while (running.get()) {
            try {
                if (paused.get()) { Thread.sleep(100); continue; }

                ChunkManager.ChunkPos pos = pollQueue();
                if (pos == null) { Thread.sleep(50); continue; }

                meshChunk(pos.x, pos.z);

            } catch (InterruptedException e) {
                LOGGER.warn("Chunk meshing interrupted", e);
                break;
            } catch (Exception e) {
                LOGGER.error("Error meshing chunk", e);
            }
        }

        LOGGER.info("Chunk meshing thread stopped. Total meshed: {}", chunksMesshed);
    }

    private synchronized ChunkManager.ChunkPos pollQueue() {
        if (meshingQueue.isEmpty()) return null;
        ChunkManager.ChunkPos pos = meshingQueue.iterator().next();
        meshingQueue.remove(pos);
        return pos;
    }

    // ── Mesh building ────────────────────────────────────────────────────────

    private void meshChunk(int chunkX, int chunkZ) {
        long  t0    = System.nanoTime();
        Chunk chunk = chunkManager.getChunk(chunkX, chunkZ);
        if (chunk == null || !chunk.isGenerated()) return;

        FloatList verts = new FloatList(INITIAL_FLOAT_CAPACITY);
        buildChunkMesh(chunk, verts);

        FloatBuffer data        = verts.toFlippedBuffer();
        int         vertexCount = data.limit() / FLOATS_PER_VERTEX;

        uploadQueue.offer(new MeshUploadTask(chunkX, chunkZ, data, vertexCount));
        chunk.setNeedsRemesh(false);

        chunksMesshed++;
        totalMeshTimeNs += System.nanoTime() - t0;

        if (StrataClient.getInstance().getDebugInfo().showWorldDebug() && chunksMesshed % 10 == 0) {
            float avg = (totalMeshTimeNs / chunksMesshed) / 1_000_000.0f;
            LOGGER.debug("Meshed {} chunks, avg {}", chunksMesshed, String.format("%.2f ms", avg));
        }
    }

    private void buildChunkMesh(Chunk chunk, FloatList verts) {
        int[] yRange = chunk.getYRange();
        if (yRange == null) return;

        for (int y = yRange[0]; y <= yRange[1]; y++) {
            for (int z = 0; z < SubChunk.SIZE; z++) {
                for (int x = 0; x < SubChunk.SIZE; x++) {
                    short blockId = chunk.getBlock(x, y, z);
                    if (blockId == Blocks.AIR.getNumericId()) continue;

                    Block block = Blocks.getByNumericId(blockId);
                    buildBlockFaces(chunk, x, y, z, block, verts);
                }
            }
        }
    }

    // ── Face visibility ──────────────────────────────────────────────────────

    private void buildBlockFaces(Chunk chunk, int x, int y, int z, Block block, FloatList v) {
        int wx = chunk.getChunkX() * SubChunk.SIZE + x;
        int wz = chunk.getChunkZ() * SubChunk.SIZE + z;

        if (shouldRenderFace(chunk, x, y + 1, z)) buildTopFace   (wx, y, wz, block, v);
        if (shouldRenderFace(chunk, x, y - 1, z)) buildBottomFace(wx, y, wz, block, v);
        if (shouldRenderFace(chunk, x, y, z - 1)) buildNorthFace (wx, y, wz, block, v);
        if (shouldRenderFace(chunk, x, y, z + 1)) buildSouthFace (wx, y, wz, block, v);
        if (shouldRenderFace(chunk, x - 1, y, z)) buildWestFace  (wx, y, wz, block, v);
        if (shouldRenderFace(chunk, x + 1, y, z)) buildEastFace  (wx, y, wz, block, v);
    }

    private boolean shouldRenderFace(Chunk chunk, int x, int y, int z) {
        if (y < 0) return true;

        if (x < 0 || x >= SubChunk.SIZE || z < 0 || z >= SubChunk.SIZE) {
            return shouldRenderFaceNeighbor(chunk, x, y, z);
        }

        short id = chunk.getBlock(x, y, z);
        if (id == Blocks.AIR.getNumericId()) return true;
        return !Blocks.getByNumericId(id).isOpaque();
    }

    private boolean shouldRenderFaceNeighbor(Chunk chunk, int x, int y, int z) {
        Chunk neighbor;
        int lx = x, lz = z;

        if      (x < 0)              { neighbor = chunk.getNeighbor(Chunk.ChunkDirection.WEST);  lx = SubChunk.SIZE - 1; }
        else if (x >= SubChunk.SIZE) { neighbor = chunk.getNeighbor(Chunk.ChunkDirection.EAST);  lx = 0; }
        else if (z < 0)              { neighbor = chunk.getNeighbor(Chunk.ChunkDirection.NORTH); lz = SubChunk.SIZE - 1; }
        else                         { neighbor = chunk.getNeighbor(Chunk.ChunkDirection.SOUTH); lz = 0; }

        if (neighbor == null) return true;

        short id = neighbor.getBlock(lx, y, lz);
        if (id == Blocks.AIR.getNumericId()) return true;
        return !Blocks.getByNumericId(id).isOpaque();
    }

    // ── UV helper ────────────────────────────────────────────────────────────

    /** Returns [minU, minV, maxU, maxV] for the given face (inner tile region, padding excluded). */
    private float[] uvs(Block block, BlockTexture.Face face) {
        BlockTexture.TextureReference ref = block.getTexture().getTextureForFace(face);
        return ref.isIdentifier()
                ? textureAtlas.getUVs(ref.getIdentifier())
                : textureAtlas.getUVs(ref.getIndex());
    }

    // ── Vertex helper ────────────────────────────────────────────────────────

    /**
     * Write one vertex: pos(3) + uv(2) + brightness(1) = 6 floats.
     *
     * FIX: was writing 9 floats [x,y,z, u,v, r,g,b,a] but the vertex shader
     * declares a_Brightness as a single float at location 2, not a vec4.
     * Writing 4 floats where 1 is expected shifts every subsequent vertex's
     * data by +3 floats, so positions and UVs were read from the wrong bytes.
     */
    private void vert(FloatList v, float x, float y, float z,
                      float u, float tv, float brightness) {
        v.add(x); v.add(y); v.add(z);
        v.add(u); v.add(tv);
        v.add(brightness);   // single float, matches "in float a_Brightness"
    }

    // ── Face builders ────────────────────────────────────────────────────────

    private void buildTopFace(int x, int y, int z, Block block, FloatList v) {
        float[] u = uvs(block, BlockTexture.Face.TOP);
        float s = 1.0f;
        vert(v, x+1, y+1, z+1, u[2],u[3], s);
        vert(v, x+1, y+1, z,   u[2],u[1], s);
        vert(v, x,   y+1, z,   u[0],u[1], s);
        vert(v, x,   y+1, z+1, u[0],u[3], s);
        vert(v, x+1, y+1, z+1, u[2],u[3], s);
        vert(v, x,   y+1, z,   u[0],u[1], s);
    }

    private void buildBottomFace(int x, int y, int z, Block block, FloatList v) {
        float[] u = uvs(block, BlockTexture.Face.BOTTOM);
        float s = 0.7f;
        vert(v, x,   y, z+1, u[0],u[3], s);
        vert(v, x+1, y, z+1, u[2],u[3], s);
        vert(v, x+1, y, z,   u[2],u[1], s);
        vert(v, x,   y, z+1, u[0],u[3], s);
        vert(v, x+1, y, z,   u[2],u[1], s);
        vert(v, x,   y, z,   u[0],u[1], s);
    }

    private void buildNorthFace(int x, int y, int z, Block block, FloatList v) {
        float[] u = uvs(block, BlockTexture.Face.NORTH);
        float s = 0.8f;
        vert(v, x+1, y,   z, u[2],u[3], s);
        vert(v, x,   y,   z, u[0],u[3], s);
        vert(v, x,   y+1, z, u[0],u[1], s);
        vert(v, x+1, y,   z, u[2],u[3], s);
        vert(v, x,   y+1, z, u[0],u[1], s);
        vert(v, x+1, y+1, z, u[2],u[1], s);
    }

    private void buildSouthFace(int x, int y, int z, Block block, FloatList v) {
        float[] u = uvs(block, BlockTexture.Face.SOUTH);
        float s = 0.8f;
        vert(v, x,   y,   z+1, u[0],u[3], s);
        vert(v, x+1, y,   z+1, u[2],u[3], s);
        vert(v, x+1, y+1, z+1, u[2],u[1], s);
        vert(v, x,   y,   z+1, u[0],u[3], s);
        vert(v, x+1, y+1, z+1, u[2],u[1], s);
        vert(v, x,   y+1, z+1, u[0],u[1], s);
    }

    private void buildWestFace(int x, int y, int z, Block block, FloatList v) {
        float[] u = uvs(block, BlockTexture.Face.WEST);
        float s = 0.9f;
        vert(v, x, y+1, z,   u[0],u[1], s);
        vert(v, x, y,   z,   u[0],u[3], s);
        vert(v, x, y,   z+1, u[2],u[3], s);
        vert(v, x, y+1, z+1, u[2],u[1], s);
        vert(v, x, y+1, z,   u[0],u[1], s);
        vert(v, x, y,   z+1, u[2],u[3], s);
    }

    private void buildEastFace(int x, int y, int z, Block block, FloatList v) {
        float[] u = uvs(block, BlockTexture.Face.EAST);
        float s = 0.9f;
        vert(v, x+1, y+1, z+1, u[2],u[1], s);
        vert(v, x+1, y,   z+1, u[2],u[3], s);
        vert(v, x+1, y,   z,   u[0],u[3], s);
        vert(v, x+1, y+1, z,   u[0],u[1], s);
        vert(v, x+1, y+1, z+1, u[2],u[1], s);
        vert(v, x+1, y,   z,   u[0],u[3], s);
    }

    // ── Public API ───────────────────────────────────────────────────────────

    public synchronized void requestMesh(int chunkX, int chunkZ) {
        Chunk chunk = chunkManager.getChunk(chunkX, chunkZ);
        if (chunk == null || !chunk.isGenerated()) return;
        meshingQueue.add(new ChunkManager.ChunkPos(chunkX, chunkZ));
    }

    public MeshUploadTask pollUploadTask()     { return uploadQueue.poll(); }
    public boolean        hasUploadTasks()     { return !uploadQueue.isEmpty(); }
    public int            getQueueSize()       { return meshingQueue.size(); }
    public int            getUploadQueueSize() { return uploadQueue.size(); }

    public void pause()  { paused.set(true);  }
    public void resume() { paused.set(false); }

    public void stop() {
        running.set(false);
        meshingQueue.clear();
        MeshUploadTask task;
        while ((task = uploadQueue.poll()) != null) {
            task.releaseBuffer();
        }
    }

    // ── Inner types ──────────────────────────────────────────────────────────

    public static class MeshUploadTask {
        public final int chunkX;
        public final int chunkZ;
        public final int vertexCount;
        private FloatBuffer data;

        MeshUploadTask(int cx, int cz, FloatBuffer data, int vertexCount) {
            this.chunkX      = cx;
            this.chunkZ      = cz;
            this.data        = data;
            this.vertexCount = vertexCount;
        }

        public FloatBuffer getData()    { return data; }
        public void releaseBuffer()     { data = null; }
    }

    // ── FloatList ─────────────────────────────────────────────────────────────

    private static class FloatList {
        private float[] arr;
        private int     size = 0;

        FloatList(int initialCapacity) { arr = new float[initialCapacity]; }

        void add(float f) {
            if (size == arr.length) {
                float[] bigger = new float[arr.length * 2];
                System.arraycopy(arr, 0, bigger, 0, size);
                arr = bigger;
            }
            arr[size++] = f;
        }

        FloatBuffer toFlippedBuffer() {
            FloatBuffer buf = BufferUtils.createFloatBuffer(size);
            buf.put(arr, 0, size);
            buf.flip();
            return buf;
        }
    }
}