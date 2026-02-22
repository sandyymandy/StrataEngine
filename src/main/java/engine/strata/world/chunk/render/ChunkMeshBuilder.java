package engine.strata.world.chunk.render;

import engine.strata.client.StrataClient;
import engine.strata.world.block.Block;
import engine.strata.world.block.Blocks;
import engine.strata.world.block.model.BlockModel;
import engine.strata.world.block.model.BlockModelLoader;
import engine.strata.world.block.texture.DynamicTextureArray;
import engine.strata.world.chunk.Chunk;
import engine.strata.world.chunk.ChunkManager;
import engine.strata.world.chunk.SubChunk;
import engine.strata.util.Identifier;
import org.lwjgl.BufferUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.FloatBuffer;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Builds chunk meshes on a dedicated background thread using the BlockModel system.
 *
 * <h3>Vertex layout</h3>
 * Each vertex is 7 floats:
 * <pre>
 *   [ x, y, z,   u, v,   layer,   brightness ]
 *    ^-pos(3)-^  ^uv(2)^ ^-f32-^  ^---f32---^
 * </pre>
 * This matches the chunk vertex shader exactly:
 * <pre>
 *   layout(location = 0) in vec3  a_Position;   // world-space block corner
 *   layout(location = 1) in vec2  a_TexCoord;   // [0,1] within the texture layer
 *   layout(location = 2) in float a_Layer;      // GL_TEXTURE_2D_ARRAY layer index
 *   layout(location = 3) in float a_Brightness; // simple directional AO factor
 * </pre>
 *
 * <h3>Geometry source</h3>
 * Instead of hardcoded full-cube quads, all geometry is now derived from
 * {@link BlockModel} elements loaded via {@link BlockModelLoader}.  Each element
 * is a cuboid in 0–16 block space; coordinates are normalised to 0–1 before
 * being offset to world space.  This means non-full-cube blocks (slabs, stairs,
 * fences, decorative elements) render correctly without any special-casing here.
 *
 * <h3>Culling</h3>
 * Face visibility still uses the neighbour-block opacity check for faces that
 * declare a {@code cullface} in their model.  Faces without a cullface (e.g.
 * interior cross-planes on flowers) are always emitted.
 */
public class ChunkMeshBuilder implements Runnable {
    private static final Logger LOGGER = LoggerFactory.getLogger("ChunkMeshBuilder");

    private static final int INITIAL_FLOAT_CAPACITY = (4 * 1024 * 1024) / Float.BYTES;

    // 7 floats per vertex: pos(3) + uv(2) + layer(1) + brightness(1)
    static final int FLOATS_PER_VERTEX = 7;

    private final ChunkManager      chunkManager;
    private final DynamicTextureArray textureArray;
    private final BlockModelLoader  modelLoader;

    private final Set<ChunkManager.ChunkPos> meshingQueue = ConcurrentHashMap.newKeySet();
    private final Queue<MeshUploadTask>      uploadQueue  = new ConcurrentLinkedQueue<>();

    private final AtomicBoolean running = new AtomicBoolean(false);
    private final AtomicBoolean paused  = new AtomicBoolean(false);

    private Thread thread;

    private int  chunksMesshed   = 0;
    private long totalMeshTimeNs = 0;

    public ChunkMeshBuilder(ChunkManager chunkManager, DynamicTextureArray textureArray) {
        this(chunkManager, textureArray, new BlockModelLoader());
    }

    public ChunkMeshBuilder(ChunkManager chunkManager, DynamicTextureArray textureArray,
                            BlockModelLoader modelLoader) {
        this.chunkManager = chunkManager;
        this.textureArray = textureArray;
        this.modelLoader  = modelLoader;
        LOGGER.info("ChunkMesher initialized (BlockModel + GL_TEXTURE_2D_ARRAY mode)");
    }

    // ── Thread lifecycle ──────────────────────────────────────────────────────

    /** Starts the meshing thread. Call once after construction. */
    public void start() {
        if (running.compareAndSet(false, true)) {
            thread = new Thread(this, "ChunkMesh");
            thread.setDaemon(true);
            thread.start();
        }
    }

    // ── Thread loop ──────────────────────────────────────────────────────────

    @Override
    public void run() {
        LOGGER.info("Chunk meshing thread started");

        while (running.get()) {
            try {
                if (paused.get()) { Thread.sleep(100); continue; }

                ChunkManager.ChunkPos pos = pollQueue();
                if (pos == null) { Thread.sleep(50); continue; }

                meshChunk(pos.x, pos.z);

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
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
                    buildBlockMesh(chunk, x, y, z, block, verts);
                }
            }
        }
    }

    // ── Block mesh building ──────────────────────────────────────────────────

    /**
     * Emits geometry for one block at local chunk coordinates (x, y, z).
     *
     * The block's model is resolved via {@link BlockModelLoader}. For each
     * element in the model, each face is tested for visibility using the
     * face's declared {@code cullface} direction.  Faces that pass the test
     * have their quads appended to {@code v}.
     */
    private void buildBlockMesh(Chunk chunk, int x, int y, int z, Block block, FloatList v) {
        Identifier modelId = block.getModelId();
        if (modelId == null) return;

        BlockModel model = modelLoader.loadModel(modelId);
        if (model == null || model.getElements().isEmpty()) return;

        int wx = chunk.getChunkX() * SubChunk.SIZE + x;
        int wz = chunk.getChunkZ() * SubChunk.SIZE + z;

        for (BlockModel.Element element : model.getElements()) {
            for (Map.Entry<BlockModel.Face, BlockModel.FaceData> entry : element.getFaces().entrySet()) {
                BlockModel.Face     face     = entry.getKey();
                BlockModel.FaceData faceData = entry.getValue();

                // ── Cullface test ─────────────────────────────────────────
                if (faceData.shouldCull()) {
                    int[] offset = faceData.getCullface().offset();
                    if (!shouldRenderFace(chunk, x + offset[0], y + offset[1], z + offset[2])) {
                        continue;
                    }
                }

                // ── Texture lookup ────────────────────────────────────────
                Identifier texId = faceData.getTexture();
                if (texId == null) {
                    // Unresolved variable — use missing-texture fallback (layer 0).
                    LOGGER.warn("Unresolved texture on block {} face {} (var={})",
                            block.getId(), face, faceData.getTextureVariable());
                    texId = Identifier.ofEngine("missing");
                }
                int layer = textureArray.getLayer(texId);

                // ── UV ────────────────────────────────────────────────────
                float[] uv         = faceData.getUvNormalized(element, face);
                float   brightness = getFaceBrightness(face);

                // ── Emit quad ─────────────────────────────────────────────
                buildFaceQuad(wx, y, wz, element, face, uv, layer, brightness, v);
            }
        }
    }

    // ── Face visibility ──────────────────────────────────────────────────────

    private boolean shouldRenderFace(Chunk chunk, int x, int y, int z) {
        if (x < 0 || x >= SubChunk.SIZE || z < 0 || z >= SubChunk.SIZE) {
            return shouldRenderFaceNeighbor(chunk, x, y, z);
        }

        short id = chunk.getBlock(x, y, z);
        if (id == Blocks.AIR.getNumericId()) return true;
        return !Blocks.getByNumericId(id).isFullBlock();
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
        return !Blocks.getByNumericId(id).isFullBlock();
    }

    // ── Quad builder ─────────────────────────────────────────────────────────

    /**
     * Emits 6 vertices (2 CCW triangles) for one face of a model element.
     *
     * <p>Vertex positions are computed from the element's normalised bounds
     * (0–1) offset to world space at (wx, wy, wz). UV corners are mapped as:
     * <pre>
     *   (u1,v1) ─── (u2,v1)
     *      │            │
     *   (u1,v2) ─── (u2,v2)
     * </pre>
     * where the exact corner assignment per face matches the traditional
     * axis-aligned mapping (x→U for top/bottom/N/S, z→U for E/W).
     *
     * <p>For custom UV (declared in the model JSON) the values from
     * {@link BlockModel.FaceData#getUvNormalized} are used verbatim.
     */
    private void buildFaceQuad(int wx, int wy, int wz,
                               BlockModel.Element element,
                               BlockModel.Face face,
                               float[] uv, int layer, float brightness,
                               FloatList v) {
        float fx = element.fx(), fy = element.fy(), fz = element.fz();
        float tx = element.tx(), ty = element.ty(), tz = element.tz();

        float u1 = uv[0], v1 = uv[1], u2 = uv[2], v2 = uv[3];

        float bx = wx, by = wy, bz = wz;

        switch (face) {
            case UP:
                // y = by+ty  |  x→u  |  z→v
                vert(v, bx+tx, by+ty, bz+tz, u2, v2, layer, brightness);
                vert(v, bx+tx, by+ty, bz+fz, u2, v1, layer, brightness);
                vert(v, bx+fx, by+ty, bz+fz, u1, v1, layer, brightness);
                vert(v, bx+tx, by+ty, bz+tz, u2, v2, layer, brightness);
                vert(v, bx+fx, by+ty, bz+fz, u1, v1, layer, brightness);
                vert(v, bx+fx, by+ty, bz+tz, u1, v2, layer, brightness);
                break;

            case DOWN:
                // y = by+fy  |  x→u  |  z→v
                vert(v, bx+tx, by+fy, bz+fz, u2, v1, layer, brightness);
                vert(v, bx+tx, by+fy, bz+tz, u2, v2, layer, brightness);
                vert(v, bx+fx, by+fy, bz+tz, u1, v2, layer, brightness);
                vert(v, bx+tx, by+fy, bz+fz, u2, v1, layer, brightness);
                vert(v, bx+fx, by+fy, bz+tz, u1, v2, layer, brightness);
                vert(v, bx+fx, by+fy, bz+fz, u1, v1, layer, brightness);
                break;

            case NORTH:
                // z = bz+fz  |  x→u  |  y→v (inverted: ty→v1, fy→v2)
                vert(v, bx+tx, by+fy, bz+fz, u2, v2, layer, brightness);
                vert(v, bx+fx, by+fy, bz+fz, u1, v2, layer, brightness);
                vert(v, bx+fx, by+ty, bz+fz, u1, v1, layer, brightness);
                vert(v, bx+tx, by+fy, bz+fz, u2, v2, layer, brightness);
                vert(v, bx+fx, by+ty, bz+fz, u1, v1, layer, brightness);
                vert(v, bx+tx, by+ty, bz+fz, u2, v1, layer, brightness);
                break;

            case SOUTH:
                // z = bz+tz  |  x→u  |  y→v (inverted)
                vert(v, bx+fx, by+fy, bz+tz, u1, v2, layer, brightness);
                vert(v, bx+tx, by+fy, bz+tz, u2, v2, layer, brightness);
                vert(v, bx+tx, by+ty, bz+tz, u2, v1, layer, brightness);
                vert(v, bx+fx, by+fy, bz+tz, u1, v2, layer, brightness);
                vert(v, bx+tx, by+ty, bz+tz, u2, v1, layer, brightness);
                vert(v, bx+fx, by+ty, bz+tz, u1, v1, layer, brightness);
                break;

            case WEST:
                // x = bx+fx  |  z→u  |  y→v (inverted)
                vert(v, bx+fx, by+ty, bz+fz, u1, v1, layer, brightness);
                vert(v, bx+fx, by+fy, bz+fz, u1, v2, layer, brightness);
                vert(v, bx+fx, by+fy, bz+tz, u2, v2, layer, brightness);
                vert(v, bx+fx, by+ty, bz+fz, u1, v1, layer, brightness);
                vert(v, bx+fx, by+fy, bz+tz, u2, v2, layer, brightness);
                vert(v, bx+fx, by+ty, bz+tz, u2, v1, layer, brightness);
                break;

            case EAST:
                // x = bx+tx  |  z→u (inverted: tz→u1, fz→u2... keep symmetric with WEST)
                vert(v, bx+tx, by+ty, bz+tz, u2, v1, layer, brightness);
                vert(v, bx+tx, by+fy, bz+tz, u2, v2, layer, brightness);
                vert(v, bx+tx, by+fy, bz+fz, u1, v2, layer, brightness);
                vert(v, bx+tx, by+ty, bz+fz, u1, v1, layer, brightness);
                vert(v, bx+tx, by+ty, bz+tz, u2, v1, layer, brightness);
                vert(v, bx+tx, by+fy, bz+fz, u1, v2, layer, brightness);
                break;
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /**
     * Simple directional lighting factor per face normal.
     * Top is brightest; bottom is darkest; sides are intermediate.
     */
    private static float getFaceBrightness(BlockModel.Face face) {
        switch (face) {
            case UP:             return 1.0f;
            case DOWN:           return 0.7f;
            case NORTH: case SOUTH: return 0.8f;
            case WEST:  case EAST:  return 0.9f;
            default:             return 1.0f;
        }
    }

    /**
     * Write one vertex: pos(3) + uv(2) + layer(1) + brightness(1) = 7 floats.
     */
    private void vert(FloatList v, float x, float y, float z,
                      float u, float tv, int layer, float brightness) {
        v.add(x); v.add(y); v.add(z);
        v.add(u); v.add(tv);
        v.add((float) layer);
        v.add(brightness);
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

    /**
     * Stops the meshing thread and releases all pending off-heap buffers.
     */
    public void stop() {
        running.set(false);
        meshingQueue.clear();
        if (thread != null) {
            thread.interrupt();
            try {
                thread.join(3000);
                if (thread.isAlive()) {
                    LOGGER.warn("ChunkMesh thread did not stop within 3s");
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
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