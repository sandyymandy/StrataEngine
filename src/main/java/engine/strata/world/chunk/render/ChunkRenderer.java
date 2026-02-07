package engine.strata.world.chunk.render;

import engine.strata.client.render.Camera;
import engine.strata.world.chunk.Chunk;
import engine.strata.world.chunk.ChunkManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Renders chunks with frustum culling and distance-based LOD.
 * Manages chunk mesh generation and GPU uploads.
 */
public class ChunkRenderer {
    private static final Logger LOGGER = LoggerFactory.getLogger("ChunkRenderer");

    // Render distance in chunks
    private static final int RENDER_DISTANCE = 16;
    private static final float MAX_RENDER_DISTANCE_BLOCKS = RENDER_DISTANCE * Chunk.SIZE;

    private final ChunkManager chunkManager;
    private final ChunkMeshBuilder meshBuilder;

    // Cache of chunk meshes (ChunkPos -> ChunkMesh)
    private final Map<ChunkPos, ChunkMesh> meshCache = new ConcurrentHashMap<>();

    // Statistics
    private int chunksRendered = 0;
    private int chunksCulled = 0;
    private int chunksOutOfRange = 0;

    public ChunkRenderer(ChunkManager chunkManager) {
        this.chunkManager = chunkManager;
        this.meshBuilder = new ChunkMeshBuilder(chunkManager);
        LOGGER.info("ChunkRenderer initialized");
    }

    /**
     * Renders all visible chunks with frustum culling.
     * Call this in your main render loop.
     */
    public void render(Camera camera, float partialTicks) {
        // Reset stats
        chunksRendered = 0;
        chunksCulled = 0;
        chunksOutOfRange = 0;

        float camX = (float) camera.getPos().x;
        float camY = (float) camera.getPos().y;
        float camZ = (float) camera.getPos().z;

        // Sort chunks by distance for proper transparency rendering
        List<Chunk> sortedChunks = getSortedChunks(camX, camY, camZ);

        for (Chunk chunk : sortedChunks) {
            // Skip if chunk hasn't been generated yet
            if (!chunk.isGenerated()) {
                continue;
            }

            // Skip empty chunks
            if (chunk.isEmpty()) {
                continue;
            }

            // Calculate chunk center in world coordinates
            float chunkCenterX = chunk.getChunkX() * Chunk.SIZE + Chunk.SIZE / 2.0f;
            float chunkCenterY = chunk.getChunkY() * Chunk.SIZE + Chunk.SIZE / 2.0f;
            float chunkCenterZ = chunk.getChunkZ() * Chunk.SIZE + Chunk.SIZE / 2.0f;

            // Distance culling
            float dx = chunkCenterX - camX;
            float dy = chunkCenterY - camY;
            float dz = chunkCenterZ - camZ;
            float distSq = dx * dx + dy * dy + dz * dz;

            if (distSq > MAX_RENDER_DISTANCE_BLOCKS * MAX_RENDER_DISTANCE_BLOCKS) {
                chunksOutOfRange++;
                continue;
            }

            // Frustum culling - test if chunk AABB is visible
            if (!isChunkVisible(camera, chunk)) {
                chunksCulled++;
                continue;
            }

            // Chunk is visible, render it
            renderChunk(chunk);
            chunksRendered++;
        }
    }

    /**
     * Tests if a chunk is visible using frustum culling.
     */
    private boolean isChunkVisible(Camera camera, Chunk chunk) {
        // Calculate AABB bounds for the chunk
        float minX = chunk.getChunkX() * Chunk.SIZE;
        float minY = chunk.getChunkY() * Chunk.SIZE;
        float minZ = chunk.getChunkZ() * Chunk.SIZE;

        float maxX = minX + Chunk.SIZE;
        float maxY = minY + Chunk.SIZE;
        float maxZ = minZ + Chunk.SIZE;

        // Test against frustum
        return camera.isAabbVisible(minX, minY, minZ, maxX, maxY, maxZ);
    }

    /**
     * Sorts chunks by distance from camera for proper rendering order.
     */
    private List<Chunk> getSortedChunks(float camX, float camY, float camZ) {
        List<Chunk> chunks = new ArrayList<>(chunkManager.getLoadedChunks());

        chunks.sort((a, b) -> {
            float distA = getChunkDistanceSq(a, camX, camY, camZ);
            float distB = getChunkDistanceSq(b, camX, camY, camZ);
            return Float.compare(distA, distB);
        });

        return chunks;
    }

    /**
     * Calculates squared distance from camera to chunk center.
     */
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
     * Renders a single chunk.
     */
    private void renderChunk(Chunk chunk) {
        ChunkPos pos = new ChunkPos(chunk.getChunkX(), chunk.getChunkY(), chunk.getChunkZ());

        // Check if we need to rebuild the mesh
        if (chunk.isDirty() || !meshCache.containsKey(pos)) {
            // Remove old mesh if it exists
            ChunkMesh oldMesh = meshCache.remove(pos);
            if (oldMesh != null) {
                oldMesh.delete();
            }

            // Build new mesh
            ChunkMeshBuilder.ChunkMesh meshData = meshBuilder.buildMesh(chunk);
            ChunkMesh mesh = new ChunkMesh(meshData);
            meshCache.put(pos, mesh);
            chunk.markClean();
        }

        // Get the mesh and render it
        ChunkMesh mesh = meshCache.get(pos);
        if (mesh != null && !mesh.isEmpty()) {
            mesh.uploadIfNeeded();
            mesh.render();
        }
    }

    /**
     * Clears all cached meshes.
     * Call this when reloading resources or unloading the world.
     */
    public void clearCache() {
        for (ChunkMesh mesh : meshCache.values()) {
            mesh.delete();
        }
        meshCache.clear();
        LOGGER.info("Cleared chunk mesh cache");
    }

    /**
     * Removes a chunk mesh from the cache.
     */
    public void removeChunk(int chunkX, int chunkY, int chunkZ) {
        ChunkPos pos = new ChunkPos(chunkX, chunkY, chunkZ);
        ChunkMesh mesh = meshCache.remove(pos);
        if (mesh != null) {
            mesh.delete();
        }
    }

    /**
     * Gets rendering statistics.
     */
    public RenderStats getStats() {
        return new RenderStats(
                chunksRendered,
                chunksCulled,
                chunksOutOfRange,
                meshCache.size()
        );
    }

    /**
     * Position key for chunk mesh cache.
     */
    private record ChunkPos(int x, int y, int z) {}

    /**
     * Rendering statistics.
     */
    public record RenderStats(
            int chunksRendered,
            int chunksCulled,
            int chunksOutOfRange,
            int totalCachedMeshes
    ) {
        @Override
        public String toString() {
            return String.format(
                    "Rendered: %d | Culled: %d | Out of Range: %d | Cached: %d",
                    chunksRendered, chunksCulled, chunksOutOfRange, totalCachedMeshes
            );
        }
    }
}