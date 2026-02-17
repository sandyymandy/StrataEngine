package engine.strata.world.chunk;

import org.joml.Vector3f;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ConcurrentHashMap;

/**
 * Represents a region of 16x16x16 chunks (512x512x512 blocks).
 * Used for broad-phase frustum culling - if a region is not visible,
 * all its chunks can be skipped without individual chunk tests.
 *
 * This optimization significantly reduces the number of frustum tests
 * from potentially thousands of chunks to dozens of regions.
 */
public class Region {
    private static final Logger LOGGER = LoggerFactory.getLogger("Region");

    public static final int SIZE = 16; // 16 chunks per dimension
    public static final int BLOCK_SIZE = SIZE * SubChunk.SIZE; // 512 blocks

    // Region position in region coordinates
    private final int regionX;
    private final int regionY;
    private final int regionZ;

    // Chunks within this region, indexed by local coordinates
    private final ConcurrentHashMap<Long, Chunk> chunks = new ConcurrentHashMap<>();

    // Cached bounds for frustum culling
    private final float minX, minY, minZ;
    private final float maxX, maxY, maxZ;
    private final float centerX, centerY, centerZ;
    private final float radius;

    public Region(int regionX, int regionY, int regionZ) {
        this.regionX = regionX;
        this.regionY = regionY;
        this.regionZ = regionZ;

        // Calculate world-space bounds
        this.minX = regionX * BLOCK_SIZE;
        this.minY = regionY * BLOCK_SIZE;
        this.minZ = regionZ * BLOCK_SIZE;

        this.maxX = minX + BLOCK_SIZE;
        this.maxY = minY + BLOCK_SIZE;
        this.maxZ = minZ + BLOCK_SIZE;

        this.centerX = (minX + maxX) / 2.0f;
        this.centerY = (minY + maxY) / 2.0f;
        this.centerZ = (minZ + maxZ) / 2.0f;

        // Calculate bounding sphere radius for distance culling
        float halfSize = BLOCK_SIZE / 2.0f;
        this.radius = (float) Math.sqrt(3 * halfSize * halfSize);
    }

    /**
     * Adds a chunk to this region.
     */
    public void addChunk(Chunk chunk) {
        long key = packChunkKey(chunk.getChunkX(), chunk.getChunkZ());
        chunks.put(key, chunk);
    }

    /**
     * Removes a chunk from this region.
     */
    public void removeChunk(int chunkX, int chunkZ) {
        long key = packChunkKey(chunkX, chunkZ);
        chunks.remove(key);
    }

    /**
     * Gets a chunk from this region.
     */
    public Chunk getChunk(int chunkX, int chunkZ) {
        long key = packChunkKey(chunkX, chunkZ);
        return chunks.get(key);
    }

    /**
     * Checks if this region contains any chunks.
     */
    public boolean isEmpty() {
        return chunks.isEmpty();
    }

    /**
     * Gets the number of chunks in this region.
     */
    public int getChunkCount() {
        return chunks.size();
    }

    /**
     * Gets all chunks in this region.
     */
    public Iterable<Chunk> getChunks() {
        return chunks.values();
    }

    /**
     * Tests if this region's bounding box intersects the frustum.
     * This is a broad-phase test - if false, all chunks in this region can be culled.
     */
    public boolean isVisible(float camX, float camY, float camZ, float maxDistance) {
        // Distance culling first (cheaper than frustum test)
        float dx = centerX - camX;
        float dy = centerY - camY;
        float dz = centerZ - camZ;
        float distSq = dx * dx + dy * dy + dz * dz;
        float maxDistSq = (maxDistance + radius) * (maxDistance + radius);

        if (distSq > maxDistSq) {
            return false; // Too far away
        }

        // Region is close enough, assume visible
        // (Frustum test will be done on individual chunks)
        return true;
    }

    /**
     * Gets the axis-aligned bounding box for frustum testing.
     */
    public float[] getAABB() {
        return new float[] { minX, minY, minZ, maxX, maxY, maxZ };
    }

    /**
     * Gets the bounding sphere for distance culling.
     */
    public float[] getBoundingSphere() {
        return new float[] { centerX, centerY, centerZ, radius };
    }

    /**
     * Packs chunk coordinates into a single long for use as a map key.
     */
    private static long packChunkKey(int chunkX, int chunkZ) {
        return ((long) chunkX << 32) | (chunkZ & 0xFFFFFFFFL);
    }

    /**
     * Clears all chunks from this region.
     */
    public void clear() {
        chunks.clear();
    }

    // Getters

    public int getRegionX() {
        return regionX;
    }

    public int getRegionY() {
        return regionY;
    }

    public int getRegionZ() {
        return regionZ;
    }

    public float getCenterX() {
        return centerX;
    }

    public float getCenterY() {
        return centerY;
    }

    public float getCenterZ() {
        return centerZ;
    }

    @Override
    public String toString() {
        return String.format("Region[%d, %d, %d] (%d chunks)",
                regionX, regionY, regionZ, chunks.size());
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Region region = (Region) o;
        return regionX == region.regionX &&
                regionY == region.regionY &&
                regionZ == region.regionZ;
    }

    @Override
    public int hashCode() {
        int result = regionX;
        result = 31 * result + regionY;
        result = 31 * result + regionZ;
        return result;
    }
}