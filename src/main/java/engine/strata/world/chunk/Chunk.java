package engine.strata.world.chunk;

import engine.strata.world.block.Block;
import engine.strata.world.block.Blocks;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ConcurrentHashMap;

/**
 * Represents a vertical column of subchunks.
 * Dynamically allocates subchunks only when needed (non-air blocks exist).
 *
 * Architecture:
 * - Chunks are positioned in world space by (chunkX, chunkZ)
 * - Each chunk contains a dynamic number of 32^3 subchunks along the Y axis
 * - SubChunks are only allocated when they contain non-air blocks
 * - This allows for infinite vertical height without memory waste
 */
public class Chunk {
    private static final Logger LOGGER = LoggerFactory.getLogger("Chunk");

    // Chunk position in chunk coordinates
    private final int chunkX;
    private final int chunkZ;

    // SubChunks indexed by Y level (each represents 32 blocks in height)
    // Using ConcurrentHashMap for thread-safe dynamic allocation
    private final ConcurrentHashMap<Integer, SubChunk> subChunks = new ConcurrentHashMap<>();

    // Mesh generation flag
    private volatile boolean needsRemesh = true;
    private volatile boolean isGenerated = false;

    // Neighbor references (set after generation for mesh optimization)
    private volatile Chunk northNeighbor;
    private volatile Chunk southNeighbor;
    private volatile Chunk eastNeighbor;
    private volatile Chunk westNeighbor;

    public Chunk(int chunkX, int chunkZ) {
        this.chunkX = chunkX;
        this.chunkZ = chunkZ;
    }

    /**
     * Gets a block in world coordinates.
     * Automatically converts to local chunk coordinates.
     */
    public short getBlockWorld(int worldX, int worldY, int worldZ) {
        // Convert world coords to local coords
        int localX = worldX - (chunkX * SubChunk.SIZE);
        int localZ = worldZ - (chunkZ * SubChunk.SIZE);

        return getBlock(localX, worldY, localZ);
    }

    /**
     * Gets a block at local coordinates within this chunk.
     * @param x Local X (0-31)
     * @param y World Y (absolute)
     * @param z Local Z (0-31)
     */
    public short getBlock(int x, int y, int z) {
        // Bounds check
        if (x < 0 || x >= SubChunk.SIZE || z < 0 || z >= SubChunk.SIZE || y < 0) {
            return Blocks.AIR.getNumericId();
        }

        // Calculate which subchunk this belongs to
        int subChunkY = y / SubChunk.SIZE;
        SubChunk subChunk = subChunks.get(subChunkY);

        // If no subchunk exists, it's air
        if (subChunk == null) {
            return Blocks.AIR.getNumericId();
        }

        // Get block from subchunk (convert Y to local subchunk coordinate)
        int localY = y % SubChunk.SIZE;
        return subChunk.getBlock(x, localY, z);
    }

    /**
     * Sets a block at local coordinates within this chunk.
     */
    public void setBlock(int x, int y, int z, short blockId) {
        // Bounds check
        if (x < 0 || x >= SubChunk.SIZE || z < 0 || z >= SubChunk.SIZE || y < 0) {
            return;
        }

        // Calculate which subchunk this belongs to
        int subChunkY = y / SubChunk.SIZE;
        int localY = y % SubChunk.SIZE;

        // If setting air and no subchunk exists, nothing to do
        if (blockId == Blocks.AIR.getNumericId() && !subChunks.containsKey(subChunkY)) {
            return;
        }

        // Get or create subchunk
        SubChunk subChunk = subChunks.computeIfAbsent(subChunkY,
                key -> new SubChunk(chunkX, key, chunkZ));

        // Set the block
        subChunk.setBlock(x, localY, z, blockId);

        // If subchunk became empty, remove it to save memory
        if (subChunk.isEmpty()) {
            subChunks.remove(subChunkY);
        }

        // Mark for remesh
        setNeedsRemesh(true);

        // Mark neighbors for remesh if on chunk boundary
        if (x == 0 && westNeighbor != null) westNeighbor.setNeedsRemesh(true);
        if (x == SubChunk.SIZE - 1 && eastNeighbor != null) eastNeighbor.setNeedsRemesh(true);
        if (z == 0 && northNeighbor != null) northNeighbor.setNeedsRemesh(true);
        if (z == SubChunk.SIZE - 1 && southNeighbor != null) southNeighbor.setNeedsRemesh(true);
    }

    /**
     * Sets a block in world coordinates.
     */
    public void setBlockWorld(int worldX, int worldY, int worldZ, short blockId) {
        int localX = worldX - (chunkX * SubChunk.SIZE);
        int localZ = worldZ - (chunkZ * SubChunk.SIZE);
        setBlock(localX, worldY, localZ, blockId);
    }

    /**
     * Gets block state at local coordinates.
     */
    public int getBlockState(int x, int y, int z) {
        int subChunkY = y / SubChunk.SIZE;
        SubChunk subChunk = subChunks.get(subChunkY);

        if (subChunk == null) {
            return 0;
        }

        int localY = y % SubChunk.SIZE;
        return subChunk.getBlockState(x, localY, z);
    }

    /**
     * Sets block state at local coordinates.
     */
    public void setBlockState(int x, int y, int z, int state) {
        int subChunkY = y / SubChunk.SIZE;

        // Get or create subchunk (states require a subchunk to exist)
        SubChunk subChunk = subChunks.computeIfAbsent(subChunkY,
                key -> new SubChunk(chunkX, key, chunkZ));

        int localY = y % SubChunk.SIZE;
        subChunk.setBlockState(x, localY, z, state);
    }

    /**
     * Fills a region with a specific block.
     */
    public void fill(int startX, int startY, int startZ,
                     int endX, int endY, int endZ, short blockId) {
        for (int y = startY; y <= endY; y++) {
            for (int z = startZ; z <= endZ && z < SubChunk.SIZE; z++) {
                for (int x = startX; x <= endX && x < SubChunk.SIZE; x++) {
                    setBlock(x, y, z, blockId);
                }
            }
        }
    }

    /**
     * Gets the Y range of this chunk (min and max Y with blocks).
     * Returns null if chunk is completely empty.
     */
    public int[] getYRange() {
        if (subChunks.isEmpty()) {
            return null;
        }

        int minY = Integer.MAX_VALUE;
        int maxY = Integer.MIN_VALUE;

        for (Integer subChunkY : subChunks.keySet()) {
            int yStart = subChunkY * SubChunk.SIZE;
            int yEnd = yStart + SubChunk.SIZE - 1;

            minY = Math.min(minY, yStart);
            maxY = Math.max(maxY, yEnd);
        }

        return new int[] { minY, maxY };
    }

    /**
     * Checks if this chunk is completely empty.
     */
    public boolean isEmpty() {
        return subChunks.isEmpty();
    }

    /**
     * Gets the number of subchunks in this chunk.
     */
    public int getSubChunkCount() {
        return subChunks.size();
    }

    /**
     * Gets a subchunk at a specific Y level.
     */
    public SubChunk getSubChunk(int subChunkY) {
        return subChunks.get(subChunkY);
    }

    /**
     * Gets all subchunk Y levels in this chunk.
     */
    public Iterable<Integer> getSubChunkLevels() {
        return subChunks.keySet();
    }

    /**
     * Estimates memory usage of this chunk in bytes.
     */
    public int getMemoryUsage() {
        int usage = 0;

        for (SubChunk subChunk : subChunks.values()) {
            usage += subChunk.getMemoryUsage();
        }

        return usage;
    }

    // Getters and setters

    public int getChunkX() {
        return chunkX;
    }

    public int getChunkZ() {
        return chunkZ;
    }

    public boolean needsRemesh() {
        return needsRemesh;
    }

    public void setNeedsRemesh(boolean needsRemesh) {
        this.needsRemesh = needsRemesh;
    }

    public boolean isGenerated() {
        return isGenerated;
    }

    public void setGenerated(boolean generated) {
        this.isGenerated = generated;
    }

    // Neighbor management

    public void setNeighbor(ChunkDirection direction, Chunk neighbor) {
        switch (direction) {
            case NORTH: northNeighbor = neighbor; break;
            case SOUTH: southNeighbor = neighbor; break;
            case EAST: eastNeighbor = neighbor; break;
            case WEST: westNeighbor = neighbor; break;
        }
    }

    public Chunk getNeighbor(ChunkDirection direction) {
        switch (direction) {
            case NORTH: return northNeighbor;
            case SOUTH: return southNeighbor;
            case EAST: return eastNeighbor;
            case WEST: return westNeighbor;
            default: return null;
        }
    }

    public boolean hasAllNeighbors() {
        return northNeighbor != null && southNeighbor != null &&
                eastNeighbor != null && westNeighbor != null;
    }

    /**
     * Clears all chunk data and prepares for unloading.
     */
    public void clear() {
        subChunks.clear();
        northNeighbor = null;
        southNeighbor = null;
        eastNeighbor = null;
        westNeighbor = null;
    }

    @Override
    public String toString() {
        return String.format("Chunk[%d, %d] (subchunks: %d, memory: %d KB)",
                chunkX, chunkZ, subChunks.size(), getMemoryUsage() / 1024);
    }

    public enum ChunkDirection {
        NORTH, SOUTH, EAST, WEST
    }
}