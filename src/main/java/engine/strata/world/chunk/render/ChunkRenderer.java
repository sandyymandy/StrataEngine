package engine.strata.world.chunk.render;

import engine.helios.RenderLayer;
import engine.helios.ShaderStack;
import engine.strata.client.StrataClient;
import engine.strata.client.render.Camera;
import engine.strata.client.render.RenderLayers;
import engine.strata.util.Identifier;
import engine.strata.world.block.DynamicTextureAtlas;
import engine.strata.world.block.TextureAtlasManager;
import engine.strata.world.chunk.Chunk;
import engine.strata.world.chunk.ChunkManager;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL30;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.util.*;
import java.util.concurrent.*;

public class ChunkRenderer {
    private static final Logger LOGGER = LoggerFactory.getLogger("ChunkRenderer");

    private int renderDistance = 32;
    private float maxRenderDistanceBlocks = renderDistance * Chunk.SIZE;

    private final ChunkManager chunkManager;
    private final ChunkMeshBuilder meshBuilder;
    private final Identifier textureAtlasId;
    private final DynamicTextureAtlas textureAtlas;

    private final Set<ChunkPos> currentlyBuilding = ConcurrentHashMap.newKeySet();
    private final Queue<MeshJob> uploadQueue = new ConcurrentLinkedQueue<>();

    private final ExecutorService meshExecutor;
    private volatile boolean shuttingDown = false;

    private static final int MAX_UPLOAD_QUEUE_SIZE = 50;

    private record MeshJob(ChunkPos pos, ChunkMeshData data) {}

    private record ChunkMeshData(float[] vertices, int[] indices) {
        boolean isEmpty() {
            return vertices.length == 0;
        }
    }

    private final Map<ChunkPos, ChunkMesh> meshCache = new ConcurrentHashMap<>();

    // Statistics
    private int chunksRendered = 0;
    private int chunksCulled = 0;
    private int chunksOutOfRange = 0;
    private int chunksNotGenerated = 0;
    private int chunksEmpty = 0;

    public ChunkRenderer(ChunkManager chunkManager, Identifier textureAtlasId) {
        this.chunkManager = chunkManager;
        this.textureAtlasId = textureAtlasId;
        this.textureAtlas = TextureAtlasManager.getInstance().getAtlas();

        if (this.textureAtlas == null) {
            throw new IllegalStateException(
                    "Texture atlas not initialized! Call TextureAtlasManager.getInstance().initializeAtlas() before creating ChunkRenderer."
            );
        }

        this.meshBuilder = new ChunkMeshBuilder(chunkManager, textureAtlas);

        int threadCount = Math.max(2, Runtime.getRuntime().availableProcessors() / 2);
        this.meshExecutor = Executors.newFixedThreadPool(threadCount, r -> {
            Thread t = new Thread(r);
            t.setDaemon(true);
            t.setName("ChunkMesh-" + t.getId());
            return t;
        });

        LOGGER.info("ChunkRenderer initialized with {} mesh threads", threadCount);
    }

    public void render(Camera camera, float partialTicks) {
        if (shuttingDown) {
            return;
        }

        chunksRendered = 0;
        chunksCulled = 0;
        chunksOutOfRange = 0;
        chunksNotGenerated = 0;
        chunksEmpty = 0;

        float camX = (float) camera.getPos().x;
        float camY = (float) camera.getPos().y;
        float camZ = (float) camera.getPos().z;

        processUploadQueue();

        List<Chunk> sortedChunks = getSortedChunks(camX, camY, camZ);
        RenderLayer chunkLayer = RenderLayers.getChunkLayer(textureAtlasId);

        chunkLayer.setup(camera);

        for (Chunk chunk : sortedChunks) {
            if (shouldSkipChunk(chunk, camera, camX, camY, camZ)) {
                continue;
            }

            renderChunk(chunk, chunkLayer.shaderStack());
            chunksRendered++;
        }

        chunkLayer.clean();

        if (Math.random() < 0.01) {
            cleanupUnloadedChunks();
        }

        if (StrataClient.getInstance().getDebugInfo().showRenderCullingDebug()) {
            LOGGER.debug("Chunks - Rendered: {}, Culled: {}, OOR: {}, NotGen: {}, Empty: {}, Cached: {}, Queue: {}, Building: {}",
                    chunksRendered, chunksCulled, chunksOutOfRange, chunksNotGenerated, chunksEmpty,
                    meshCache.size(), uploadQueue.size(), currentlyBuilding.size());
        }
    }

    private void processUploadQueue() {
        int uploadsThisFrame = 0;
        int maxUploads = 10;

        while (!uploadQueue.isEmpty() && uploadsThisFrame < maxUploads) {
            MeshJob job = uploadQueue.poll();
            if (job == null) break;

            // Clean up old mesh
            ChunkMesh oldMesh = meshCache.remove(job.pos);
            if (oldMesh != null) {
                oldMesh.delete();
            }

            // Upload new mesh if not empty
            if (!job.data.isEmpty()) {
                ChunkMesh newMesh = new ChunkMesh(job.data);
                newMesh.upload();
                meshCache.put(job.pos, newMesh);
            }

            uploadsThisFrame++;
        }

        // Limit queue size
        while (uploadQueue.size() > MAX_UPLOAD_QUEUE_SIZE) {
            MeshJob removed = uploadQueue.poll();
            if (removed != null) {
                LOGGER.warn("Upload queue overflow, dropping mesh job for {}", removed.pos);
            }
        }
    }

    private void cleanupUnloadedChunks() {
        Iterator<Map.Entry<ChunkPos, ChunkMesh>> iterator = meshCache.entrySet().iterator();
        int removed = 0;

        while (iterator.hasNext()) {
            Map.Entry<ChunkPos, ChunkMesh> entry = iterator.next();
            ChunkPos pos = entry.getKey();

            if (chunkManager.getChunkIfLoaded(pos.x, pos.y, pos.z) == null) {
                entry.getValue().delete();
                iterator.remove();
                removed++;
            }
        }

        if (removed > 0) {
            LOGGER.debug("Cleaned up {} meshes for unloaded chunks", removed);
        }
    }

    private boolean shouldSkipChunk(Chunk chunk, Camera camera, float camX, float camY, float camZ) {
        if (!chunk.isGenerated()) {
            chunksNotGenerated++;
            return true;
        }

        if (chunk.isEmpty()) {
            chunksEmpty++;
            return true;
        }

        float chunkCenterX = chunk.getChunkX() * Chunk.SIZE + Chunk.SIZE / 2.0f;
        float chunkCenterY = chunk.getChunkY() * Chunk.SIZE + Chunk.SIZE / 2.0f;
        float chunkCenterZ = chunk.getChunkZ() * Chunk.SIZE + Chunk.SIZE / 2.0f;

        float dx = chunkCenterX - camX;
        float dy = chunkCenterY - camY;
        float dz = chunkCenterZ - camZ;
        float distSq = dx * dx + dy * dy + dz * dz;

        if (distSq > maxRenderDistanceBlocks * maxRenderDistanceBlocks) {
            chunksOutOfRange++;
            return true;
        }

        if (!isChunkVisible(camera, chunk)) {
            chunksCulled++;
            return true;
        }

        return false;
    }

    private boolean isChunkVisible(Camera camera, Chunk chunk) {
        float minX = chunk.getChunkX() * Chunk.SIZE;
        float minY = chunk.getChunkY() * Chunk.SIZE;
        float minZ = chunk.getChunkZ() * Chunk.SIZE;

        float maxX = minX + Chunk.SIZE;
        float maxY = minY + Chunk.SIZE;
        float maxZ = minZ + Chunk.SIZE;

        return camera.isAabbVisible(minX, minY, minZ, maxX, maxY, maxZ);
    }

    private List<Chunk> getSortedChunks(float camX, float camY, float camZ) {
        List<Chunk> chunks = new ArrayList<>(chunkManager.getLoadedChunks());

        chunks.sort((a, b) -> {
            float distA = getChunkDistanceSq(a, camX, camY, camZ);
            float distB = getChunkDistanceSq(b, camX, camY, camZ);
            return Float.compare(distA, distB);
        });

        return chunks;
    }

    private float getChunkDistanceSq(Chunk chunk, float camX, float camY, float camZ) {
        float chunkCenterX = chunk.getChunkX() * Chunk.SIZE + Chunk.SIZE / 2.0f;
        float chunkCenterY = chunk.getChunkY() * Chunk.SIZE + Chunk.SIZE / 2.0f;
        float chunkCenterZ = chunk.getChunkZ() * Chunk.SIZE + Chunk.SIZE / 2.0f;

        float dx = chunkCenterX - camX;
        float dy = chunkCenterY - camY;
        float dz = chunkCenterZ - camZ;

        return dx * dx + dy * dy + dz * dz;
    }

    /**
     * FIXED: Mark chunk clean IMMEDIATELY when submitting to build queue.
     * This prevents multiple threads from building the same chunk.
     */
    private void renderChunk(Chunk chunk, ShaderStack shader) {
        ChunkPos pos = new ChunkPos(chunk.getChunkX(), chunk.getChunkY(), chunk.getChunkZ());

        // Build mesh if dirty and not already building
        if (chunk.isDirty() && !currentlyBuilding.contains(pos)) {
            if (uploadQueue.size() >= MAX_UPLOAD_QUEUE_SIZE) {
                return;
            }

            // CRITICAL FIX: Mark clean BEFORE submitting to prevent duplicate builds
            chunk.markClean();
            currentlyBuilding.add(pos);

            meshExecutor.submit(() -> {
                try {
                    ChunkMeshBuilder.ChunkMesh meshData = meshBuilder.buildMesh(chunk);
                    float[] vertexData = extractVertexData(meshData);
                    int[] indexData = meshData.indices();

                    ChunkMeshData minimalData = new ChunkMeshData(vertexData, indexData);
                    uploadQueue.add(new MeshJob(pos, minimalData));

                } catch (Exception e) {
                    LOGGER.error("Error building mesh for chunk {}", pos, e);
                    // Re-mark as dirty so it will be retried
                    Chunk errorChunk = chunkManager.getChunkIfLoaded(pos.x, pos.y, pos.z);
                    if (errorChunk != null) {
                        errorChunk.markDirty();
                    }
                } finally {
                    currentlyBuilding.remove(pos);
                }
            });
        }

        // Render existing mesh
        ChunkMesh mesh = meshCache.get(pos);
        if (mesh != null && !mesh.isEmpty()) {
            Matrix4f modelMatrix = new Matrix4f().identity()
                    .translate(
                            chunk.getChunkX() * Chunk.SIZE,
                            chunk.getChunkY() * Chunk.SIZE,
                            chunk.getChunkZ() * Chunk.SIZE
                    );

            shader.setUniform("u_Model", modelMatrix);
            mesh.render();
        }
    }

    private float[] extractVertexData(ChunkMeshBuilder.ChunkMesh meshData) {
        ChunkMeshBuilder.ChunkVertex[] vertices = meshData.vertices();
        float[] data = new float[vertices.length * 6];

        int index = 0;
        for (ChunkMeshBuilder.ChunkVertex vertex : vertices) {
            data[index++] = vertex.x();
            data[index++] = vertex.y();
            data[index++] = vertex.z();
            data[index++] = vertex.u();
            data[index++] = vertex.v();
            data[index++] = vertex.brightness();
        }

        return data;
    }

    public void setRenderDistance(int distance) {
        this.renderDistance = distance;
        this.maxRenderDistanceBlocks = distance * Chunk.SIZE;
        clearCache();
    }

    public void clearCache() {
        LOGGER.info("Clearing chunk mesh cache ({} meshes)...", meshCache.size());

        for (ChunkMesh mesh : meshCache.values()) {
            mesh.delete();
        }
        meshCache.clear();
        uploadQueue.clear();
        currentlyBuilding.clear();

        LOGGER.info("Chunk mesh cache cleared");
    }

    public void removeChunk(int chunkX, int chunkY, int chunkZ) {
        ChunkPos pos = new ChunkPos(chunkX, chunkY, chunkZ);
        ChunkMesh mesh = meshCache.remove(pos);
        if (mesh != null) {
            mesh.delete();
        }
    }

    public void shutdown() {
        LOGGER.info("Shutting down chunk renderer...");

        shuttingDown = true;
        meshExecutor.shutdown();

        try {
            if (!meshExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                LOGGER.warn("Mesh executor did not terminate in time, forcing shutdown...");
                meshExecutor.shutdownNow();

                if (!meshExecutor.awaitTermination(2, TimeUnit.SECONDS)) {
                    LOGGER.error("Mesh executor did not terminate after force shutdown");
                }
            }
        } catch (InterruptedException e) {
            LOGGER.error("Interrupted while shutting down mesh executor", e);
            meshExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }

        clearCache();
        LOGGER.info("Chunk renderer shutdown complete");
    }

    public DynamicTextureAtlas getTextureAtlas() {
        return textureAtlas;
    }

    public RenderStats getStats() {
        return new RenderStats(
                chunksRendered,
                chunksCulled,
                chunksOutOfRange,
                chunksNotGenerated,
                chunksEmpty,
                meshCache.size(),
                uploadQueue.size(),
                currentlyBuilding.size()
        );
    }

    private record ChunkPos(int x, int y, int z) {}

    public record RenderStats(
            int chunksRendered,
            int chunksCulled,
            int chunksOutOfRange,
            int chunksNotGenerated,
            int chunksEmpty,
            int totalCachedMeshes,
            int pendingUploads,
            int meshesBuilding
    ) {
        @Override
        public String toString() {
            return String.format(
                    "Rendered: %d | Culled: %d | OOR: %d | NotGen: %d | Empty: %d | Cached: %d | Pending: %d | Building: %d",
                    chunksRendered, chunksCulled, chunksOutOfRange, chunksNotGenerated,
                    chunksEmpty, totalCachedMeshes, pendingUploads, meshesBuilding
            );
        }
    }

    /**
     * FIXED: Complete OpenGL implementation
     */
    private static class ChunkMesh {
        private final ChunkMeshData meshData;
        private int vaoId = -1;
        private int vboId = -1;
        private int iboId = -1;
        private boolean uploaded = false;

        ChunkMesh(ChunkMeshData meshData) {
            this.meshData = meshData;
        }

        void upload() {
            if (uploaded || meshData.isEmpty()) {
                return;
            }

            // Generate VAO
            vaoId = GL30.glGenVertexArrays();
            GL30.glBindVertexArray(vaoId);

            // Generate and bind VBO
            vboId = GL30.glGenBuffers();
            GL30.glBindBuffer(GL30.GL_ARRAY_BUFFER, vboId);

            // Create vertex buffer
            FloatBuffer vertexBuffer = createVertexBuffer(meshData.vertices);
            GL30.glBufferData(GL30.GL_ARRAY_BUFFER, vertexBuffer, GL30.GL_STATIC_DRAW);

            // Position attribute (location = 0, 3 floats)
            GL30.glVertexAttribPointer(0, 3, GL30.GL_FLOAT, false, 6 * Float.BYTES, 0);
            GL30.glEnableVertexAttribArray(0);

            // UV attribute (location = 1, 2 floats)
            GL30.glVertexAttribPointer(1, 2, GL30.GL_FLOAT, false, 6 * Float.BYTES, 3 * Float.BYTES);
            GL30.glEnableVertexAttribArray(1);

            // Brightness attribute (location = 2, 1 float)
            GL30.glVertexAttribPointer(2, 1, GL30.GL_FLOAT, false, 6 * Float.BYTES, 5 * Float.BYTES);
            GL30.glEnableVertexAttribArray(2);

            // Generate and bind IBO
            iboId = GL30.glGenBuffers();
            GL30.glBindBuffer(GL30.GL_ELEMENT_ARRAY_BUFFER, iboId);

            // Create index buffer
            IntBuffer indexBuffer = createIndexBuffer(meshData.indices);
            GL30.glBufferData(GL30.GL_ELEMENT_ARRAY_BUFFER, indexBuffer, GL30.GL_STATIC_DRAW);

            // Unbind
            GL30.glBindVertexArray(0);
            GL30.glBindBuffer(GL30.GL_ARRAY_BUFFER, 0);
            GL30.glBindBuffer(GL30.GL_ELEMENT_ARRAY_BUFFER, 0);

            uploaded = true;
        }

        private FloatBuffer createVertexBuffer(float[] vertices) {
            FloatBuffer buffer = ByteBuffer.allocateDirect(vertices.length * Float.BYTES)
                    .order(ByteOrder.nativeOrder())
                    .asFloatBuffer();
            buffer.put(vertices);
            buffer.flip();
            return buffer;
        }

        private IntBuffer createIndexBuffer(int[] indices) {
            IntBuffer buffer = ByteBuffer.allocateDirect(indices.length * Integer.BYTES)
                    .order(ByteOrder.nativeOrder())
                    .asIntBuffer();
            buffer.put(indices);
            buffer.flip();
            return buffer;
        }

        void render() {
            if (!uploaded || vaoId == -1 || meshData.isEmpty()) {
                return;
            }

            GL30.glBindVertexArray(vaoId);
            GL30.glDrawElements(GL30.GL_TRIANGLES, meshData.indices.length, GL30.GL_UNSIGNED_INT, 0);
            GL30.glBindVertexArray(0);
        }

        void delete() {
            if (vaoId != -1) {
                GL30.glDeleteVertexArrays(vaoId);
                vaoId = -1;
            }
            if (vboId != -1) {
                GL30.glDeleteBuffers(vboId);
                vboId = -1;
            }
            if (iboId != -1) {
                GL30.glDeleteBuffers(iboId);
                iboId = -1;
            }
            uploaded = false;
        }

        boolean isEmpty() {
            return meshData.isEmpty();
        }
    }
}