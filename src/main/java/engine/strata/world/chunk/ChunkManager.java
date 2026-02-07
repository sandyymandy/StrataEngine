package engine.strata.world.chunk;

import engine.strata.client.StrataClient;
import engine.strata.world.block.Block;
import engine.strata.world.block.Blocks;
import engine.strata.world.lighting.LightingEngine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Enhanced chunk manager with integrated generation and lighting.
 * Manages all chunks in the world with multithreaded generation support.
 */
public class ChunkManager {
    private static final Logger LOGGER = LoggerFactory.getLogger("ChunkManager");

    // All loaded chunks, indexed by ChunkPos
    private final Map<ChunkPos, Chunk> chunks = new ConcurrentHashMap<>();

    // Chunk generation system
    private final ChunkGenerationManager generationManager;

    // Lighting system
    private final LightingEngine lightingEngine;

    // World seed
    private final long seed;

    public ChunkManager(long seed) {
        this.seed = seed;
        this.generationManager = new ChunkGenerationManager(seed);
        this.lightingEngine = new LightingEngine(this);

        LOGGER.info("Enhanced chunk manager initialized with seed {}", seed);
    }

    /**
     * Gets a chunk at the specified chunk coordinates.
     * Creates and generates a new chunk if it doesn't exist.
     */
    public Chunk getChunk(int chunkX, int chunkY, int chunkZ) {
        ChunkPos pos = new ChunkPos(chunkX, chunkY, chunkZ);
        return chunks.computeIfAbsent(pos, p -> {
            Chunk chunk = new Chunk(chunkX, chunkY, chunkZ);
            if(StrataClient.getInstance().getDebugInfo().showChunkDebug()) LOGGER.debug("Created new chunk at {}", pos);

            // Queue for generation
            requestChunkGeneration(chunk);

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
     * Requests a chunk to be generated asynchronously.
     */
    private void requestChunkGeneration(Chunk chunk) {
        generationManager.generateChunkAsync(chunk, generatedChunk -> {
            // After generation, calculate lighting
            lightingEngine.calculateInitialLighting(generatedChunk);

            // Mark neighboring chunks as dirty if they exist
            markNeighborsForUpdate(generatedChunk);

            if(StrataClient.getInstance().getDebugInfo().showChunkDebug()) LOGGER.debug("Chunk {} fully generated and lit", generatedChunk);
        });
    }

    /**
     * Marks neighboring chunks for update after a chunk is generated.
     */
    private void markNeighborsForUpdate(Chunk chunk) {
        int cx = chunk.getChunkX();
        int cy = chunk.getChunkY();
        int cz = chunk.getChunkZ();

        // Mark all 6 neighboring chunks as dirty
        markChunkDirtyIfLoaded(cx - 1, cy, cz);
        markChunkDirtyIfLoaded(cx + 1, cy, cz);
        markChunkDirtyIfLoaded(cx, cy - 1, cz);
        markChunkDirtyIfLoaded(cx, cy + 1, cz);
        markChunkDirtyIfLoaded(cx, cy, cz - 1);
        markChunkDirtyIfLoaded(cx, cy, cz + 1);
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
     * Sets a block at world coordinates and updates lighting.
     */
    public void setBlock(int worldX, int worldY, int worldZ, Block block) {
        Chunk chunk = getChunkAtWorldPos(worldX, worldY, worldZ);

        int localX = Math.floorMod(worldX, Chunk.SIZE);
        int localY = Math.floorMod(worldY, Chunk.SIZE);
        int localZ = Math.floorMod(worldZ, Chunk.SIZE);

        chunk.setBlock(localX, localY, localZ, block);

        // Update lighting after block change
        lightingEngine.updateLightingAt(worldX, worldY, worldZ);

        // Mark neighboring chunks as dirty if we're on a boundary
        if (localX == 0) markChunkDirtyIfLoaded(chunk.getChunkX() - 1, chunk.getChunkY(), chunk.getChunkZ());
        if (localX == Chunk.SIZE - 1) markChunkDirtyIfLoaded(chunk.getChunkX() + 1, chunk.getChunkY(), chunk.getChunkZ());
        if (localY == 0) markChunkDirtyIfLoaded(chunk.getChunkX(), chunk.getChunkY() - 1, chunk.getChunkZ());
        if (localY == Chunk.SIZE - 1) markChunkDirtyIfLoaded(chunk.getChunkX(), chunk.getChunkY() + 1, chunk.getChunkZ());
        if (localZ == 0) markChunkDirtyIfLoaded(chunk.getChunkX(), chunk.getChunkY(), chunk.getChunkZ() - 1);
        if (localZ == Chunk.SIZE - 1) markChunkDirtyIfLoaded(chunk.getChunkX(), chunk.getChunkY(), chunk.getChunkZ() + 1);
    }

    /**
     * Gets the light level at world coordinates.
     */
    public int getLightLevel(int worldX, int worldY, int worldZ) {
        Chunk chunk = getChunkAtWorldPos(worldX, worldY, worldZ);
        if (chunk == null) {
            return 15; // Full light for unloaded chunks
        }

        int localX = Math.floorMod(worldX, Chunk.SIZE);
        int localY = Math.floorMod(worldY, Chunk.SIZE);
        int localZ = Math.floorMod(worldZ, Chunk.SIZE);

        return chunk.getLight(localX, localY, localZ);
    }

    /**
     * Marks a chunk as dirty if it's loaded.
     */
    private void markChunkDirtyIfLoaded(int chunkX, int chunkY, int chunkZ) {
        Chunk chunk = getChunkIfLoaded(chunkX, chunkY, chunkZ);
        if (chunk != null) {
            chunk.markDirty();
        }
    }

    /**
     * Unloads a chunk and cancels any pending generation.
     */
    public void unloadChunk(int chunkX, int chunkY, int chunkZ) {
        ChunkPos pos = new ChunkPos(chunkX, chunkY, chunkZ);
        Chunk removed = chunks.remove(pos);
        if (removed != null) {
            if(StrataClient.getInstance().getDebugInfo().showChunkDebug()) LOGGER.debug("Unloaded chunk at {}", pos);
        }
    }

    /**
     * Loads chunks in a radius around a position (for player movement).
     */
    public void loadChunksAround(int centerChunkX, int centerChunkY, int centerChunkZ, int radius) {
        for (int x = centerChunkX - radius; x <= centerChunkX + radius; x++) {
            for (int y = centerChunkY - radius; y <= centerChunkY + radius; y++) {
                for (int z = centerChunkZ - radius; z <= centerChunkZ + radius; z++) {
                    // Only load chunks within render distance
                    int dx = x - centerChunkX;
                    int dy = y - centerChunkY;
                    int dz = z - centerChunkZ;
                    double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);

                    if (distance <= radius) {
                        getChunk(x, y, z);
                    }
                }
            }
        }
    }

    /**
     * Unloads chunks outside a radius (for memory management).
     */
    public void unloadChunksOutside(int centerChunkX, int centerChunkY, int centerChunkZ, int radius) {
        chunks.entrySet().removeIf(entry -> {
            ChunkPos pos = entry.getKey();
            int dx = pos.x - centerChunkX;
            int dy = pos.y - centerChunkY;
            int dz = pos.z - centerChunkZ;
            double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);

            if (distance > radius + 2) { // Add buffer
                if(StrataClient.getInstance().getDebugInfo().showChunkDebug()) LOGGER.debug("Unloading distant chunk at {}", pos);
                return true;
            }
            return false;
        });
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
     * Gets chunk generation statistics.
     */
    public ChunkGenerationManager.ChunkGenerationStats getGenerationStats() {
        return generationManager.getStats();
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
     * Shuts down the chunk manager and generation threads.
     */
    public void shutdown() {
        generationManager.shutdown();
        clear();
        LOGGER.info("Chunk manager shut down");
    }

    /**
     * Gets the lighting engine for direct access if needed.
     */
    public LightingEngine getLightingEngine() {
        return lightingEngine;
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