package engine.strata.world.chunk;

import engine.strata.world.block.Block;
import engine.strata.world.block.Blocks;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages all chunks in the world.
 * Handles chunk loading, unloading, and access.
 */
public class ChunkManager {
    private static final Logger LOGGER = LoggerFactory.getLogger("ChunkManager");

    // All loaded chunks, indexed by ChunkPos
    private final Map<ChunkPos, Chunk> chunks = new ConcurrentHashMap<>();

    /**
     * Gets a chunk at the specified chunk coordinates.
     * Creates a new chunk if it doesn't exist.
     */
    public Chunk getChunk(int chunkX, int chunkY, int chunkZ) {
        ChunkPos pos = new ChunkPos(chunkX, chunkY, chunkZ);
        return chunks.computeIfAbsent(pos, p -> {
            Chunk chunk = new Chunk(chunkX, chunkY, chunkZ);
            LOGGER.debug("Created new chunk at {}", pos);
            return chunk;
        });
    }

    /**
     * Gets a chunk if it's loaded, null otherwise.
     */
    public Chunk getChunkIfLoaded(int chunkX, int chunkY, int chunkZ) {
        return chunks.get(new ChunkPos(chunkX, chunkY, chunkZ));
    }

    /**
     * Gets the chunk containing a world position.
     */
    public Chunk getChunkAtWorldPos(int worldX, int worldY, int worldZ) {
        int chunkX = Math.floorDiv(worldX, Chunk.SIZE);
        int chunkY = Math.floorDiv(worldY, Chunk.SIZE);
        int chunkZ = Math.floorDiv(worldZ, Chunk.SIZE);
        return getChunk(chunkX, chunkY, chunkZ);
    }

    /**
     * Gets a block at world coordinates.
     */
    public Block getBlock(int worldX, int worldY, int worldZ) {
        // Convert to chunk coordinates
        int chunkX = Math.floorDiv(worldX, Chunk.SIZE);
        int chunkY = Math.floorDiv(worldY, Chunk.SIZE);
        int chunkZ = Math.floorDiv(worldZ, Chunk.SIZE);

        Chunk chunk = getChunkIfLoaded(chunkX, chunkY, chunkZ);
        if (chunk == null) {
            return Blocks.AIR;
        }

        // Convert to local coordinates
        int localX = Math.floorMod(worldX, Chunk.SIZE);
        int localY = Math.floorMod(worldY, Chunk.SIZE);
        int localZ = Math.floorMod(worldZ, Chunk.SIZE);

        return chunk.getBlock(localX, localY, localZ);
    }

    /**
     * Sets a block at world coordinates.
     */
    public void setBlock(int worldX, int worldY, int worldZ, Block block) {
        Chunk chunk = getChunkAtWorldPos(worldX, worldY, worldZ);

        int localX = Math.floorMod(worldX, Chunk.SIZE);
        int localY = Math.floorMod(worldY, Chunk.SIZE);
        int localZ = Math.floorMod(worldZ, Chunk.SIZE);

        chunk.setBlock(localX, localY, localZ, block);

        // Mark neighboring chunks as dirty if we're on a boundary
        if (localX == 0) markChunkDirty(chunk.getChunkX() - 1, chunk.getChunkY(), chunk.getChunkZ());
        if (localX == Chunk.SIZE - 1) markChunkDirty(chunk.getChunkX() + 1, chunk.getChunkY(), chunk.getChunkZ());
        if (localY == 0) markChunkDirty(chunk.getChunkX(), chunk.getChunkY() - 1, chunk.getChunkZ());
        if (localY == Chunk.SIZE - 1) markChunkDirty(chunk.getChunkX(), chunk.getChunkY() + 1, chunk.getChunkZ());
        if (localZ == 0) markChunkDirty(chunk.getChunkX(), chunk.getChunkY(), chunk.getChunkZ() - 1);
        if (localZ == Chunk.SIZE - 1) markChunkDirty(chunk.getChunkX(), chunk.getChunkY(), chunk.getChunkZ() + 1);
    }

    /**
     * Marks a chunk as dirty if it's loaded.
     */
    private void markChunkDirty(int chunkX, int chunkY, int chunkZ) {
        Chunk chunk = getChunkIfLoaded(chunkX, chunkY, chunkZ);
        if (chunk != null) {
            chunk.markDirty();
        }
    }

    /**
     * Unloads a chunk.
     */
    public void unloadChunk(int chunkX, int chunkY, int chunkZ) {
        ChunkPos pos = new ChunkPos(chunkX, chunkY, chunkZ);
        Chunk removed = chunks.remove(pos);
        if (removed != null) {
            LOGGER.debug("Unloaded chunk at {}", pos);
        }
    }

    /**
     * Gets all loaded chunks.
     */
    public Collection<Chunk> getLoadedChunks() {
        return chunks.values();
    }

    /**
     * Gets the number of loaded chunks.
     */
    public int getLoadedChunkCount() {
        return chunks.size();
    }

    /**
     * Clears all chunks (for world reloading).
     */
    public void clear() {
        int count = chunks.size();
        chunks.clear();
        LOGGER.info("Unloaded {} chunks", count);
    }

    /**
     * Immutable chunk position.
     */
    private record ChunkPos(int x, int y, int z) {
        @Override
        public String toString() {
            return "[" + x + ", " + y + ", " + z + "]";
        }
    }
}