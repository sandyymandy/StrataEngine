package engine.strata.world;


import engine.strata.client.StrataClient;
import engine.strata.entity.Entity;
import engine.strata.entity.entities.PlayerEntity;
import engine.strata.physics.PhysicsManager;
import engine.strata.world.block.Block;
import engine.strata.world.chunk.Chunk;
import engine.strata.world.chunk.ChunkManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class World {
    private static final Logger LOGGER = LoggerFactory.getLogger("World");

    // Use ConcurrentHashMap for thread-safe access
    private final ConcurrentHashMap<UUID, Entity> entities = new ConcurrentHashMap<>();
    private volatile List<Entity> entityList = new ArrayList<>();

    // management
    private final ChunkManager chunkManager;
    private final PhysicsManager physicsManager;

    // World properties
    private final long seed;
    private final String worldName;

    // Player position tracking for chunk loading
    private int lastPlayerChunkX = 0;
    private int lastPlayerChunkY = 0;
    private int lastPlayerChunkZ = 0;
    private static final int CHUNK_LOAD_RADIUS = 32;

    public World(String worldName, long seed) {
        this.worldName = worldName;
        this.seed = seed;
        this.chunkManager = new ChunkManager(seed);
        this.physicsManager = new PhysicsManager();
        this.physicsManager.init();

        if(StrataClient.getInstance().getDebugInfo().showWorldDebug()) LOGGER.info("Creating new world '{}' with seed {}", worldName, seed);
    }

    public World() {
        this("World", System.currentTimeMillis());
    }


    /**
     * Ticks all entities and updates chunks around players.
     */
    public void tick() {
        if (physicsManager != null) {
            physicsManager.update(1.0f / 60.0f);
        }

        // Tick all entities
        for (Entity entity : entityList) {
            entity.tick();

            // Track player position for chunk loading
            if (entity instanceof PlayerEntity) {
                updateChunksAroundPlayer(entity);
            }
        }
    }

    /**
     * Updates chunks around a player entity.
     * NOW ONLY LOADS ONE VERTICAL LAYER (Y=4, which is world Y=64-79)
     */
    private void updateChunksAroundPlayer(Entity player) {
        int playerChunkX = (int) Math.floor(player.getPosition().getX() / Chunk.SIZE);
        int playerChunkZ = (int) Math.floor(player.getPosition().getZ() / Chunk.SIZE);

        // SNAP TO Y=4 (world height 64-79)
        int playerChunkY = 4;

        // Only update if player moved to a different chunk
        if (playerChunkX != lastPlayerChunkX ||
                playerChunkZ != lastPlayerChunkZ) {

            // Load new chunks - ONLY HORIZONTAL, Y is fixed at 4
            chunkManager.loadChunksAroundFlat(playerChunkX, playerChunkY, playerChunkZ, CHUNK_LOAD_RADIUS);

            // Unload distant chunks
            chunkManager.unloadChunksOutsideFlat(playerChunkX, playerChunkY, playerChunkZ, CHUNK_LOAD_RADIUS + 1);

            lastPlayerChunkX = playerChunkX;
            lastPlayerChunkY = playerChunkY;
            lastPlayerChunkZ = playerChunkZ;

            if(StrataClient.getInstance().getDebugInfo().showWorldDebug()) LOGGER.debug("Player moved to chunk [{}, {}, {}], loaded chunks: {}",
                    playerChunkX, playerChunkY, playerChunkZ,
                    chunkManager.getLoadedChunkCount());
        }
    }

    /**
     * Pre-loads chunks in a radius around a position.
     * NOW ONLY LOADS ONE VERTICAL LAYER (Y=4)
     */
    public void preloadChunks(double x, double y, double z) {
        int centerChunkX = (int) Math.floor(x / Chunk.SIZE);
        int centerChunkZ = (int) Math.floor(z / Chunk.SIZE);

        // SNAP TO Y=4 (world height 64-79)
        int centerChunkY = 4;

        chunkManager.loadChunksAroundFlat(centerChunkX, centerChunkY, centerChunkZ, CHUNK_LOAD_RADIUS);
    }

    /**
     * Adds an entity to the world.
     * Thread-safe for adding entities from any thread.
     */
    public void addEntity(Entity entity) {
        if (entity == null) {
            LOGGER.warn("Attempted to add null entity to world");
            return;
        }

        UUID id = UUID.randomUUID();
        entities.put(id, entity);
        updateEntityList();

        if(StrataClient.getInstance().getDebugInfo().showWorldDebug()) LOGGER.debug("Added entity {} with ID {}", entity.getClass().getSimpleName(), id);
    }

    /**
     * Removes an entity from the world.
     */
    public void removeEntity(UUID id) {
        Entity removed = entities.remove(id);
        if (removed != null) {
            updateEntityList();
            if(StrataClient.getInstance().getDebugInfo().showWorldDebug()) LOGGER.debug("Removed entity {} with ID {}", removed.getClass().getSimpleName(), id);
        }
    }

    /**
     * Removes an entity by reference (slower, requires iteration).
     */
    public void removeEntity(Entity entity) {
        entities.entrySet().removeIf(entry -> entry.getValue() == entity);
        updateEntityList();
    }

    /**
     * Gets all entities in the world.
     * Returns a snapshot that's safe to iterate.
     */
    public List<Entity> getEntities() {
        return entityList;
    }

    /**
     * Gets the number of entities in the world.
     */
    public int getEntityCount() {
        return entities.size();
    }

    /**
     * Updates the cached entity list after modifications.
     */
    private void updateEntityList() {
        entityList = new ArrayList<>(entities.values());
    }

    /**
     * Gets a block at world coordinates.
     */
    public Block getBlock(int x, int y, int z) {
        return chunkManager.getBlock(x, y, z);
    }

    /**
     * Sets a block at world coordinates.
     */
    public void setBlock(int x, int y, int z, Block block) {
        chunkManager.setBlock(x, y, z, block);
    }

    /**
     * Gets the light level at world coordinates (0-15).
     */
    public int getLightLevel(int x, int y, int z) {
        return chunkManager.getLightLevel(x, y, z);
    }

    /**
     * Gets a chunk at chunk coordinates.
     */
    public Chunk getChunk(int chunkX, int chunkY, int chunkZ) {
        return chunkManager.getChunk(chunkX, chunkY, chunkZ);
    }

    /**
     * Gets all loaded chunks.
     */
    public Collection<Chunk> getLoadedChunks() {
        return chunkManager.getLoadedChunks();
    }

    /**
     * Gets the chunk manager for direct access.
     */
    public ChunkManager getChunkManager() {
        return chunkManager;
    }

    public PhysicsManager getPhysicsManager() {
        return physicsManager;
    }

    // ==================== World Properties ====================

    /**
     * Gets the world seed.
     */
    public long getSeed() {
        return seed;
    }

    /**
     * Gets the world name.
     */
    public String getWorldName() {
        return worldName;
    }

    // ==================== Cleanup ====================

    /**
     * Clears all entities from the world.
     */
    public void clearEntities() {
        entities.clear();
        updateEntityList();
        LOGGER.info("Cleared all entities from world");
    }

    /**
     * Clears all chunks from the world.
     */
    public void clearChunks() {
        chunkManager.clear();
        LOGGER.info("Cleared all chunks from world");
    }

    /**
     * Clears everything (entities and chunks).
     */
    public void clear() {
        clearEntities();
        clearChunks();
        LOGGER.info("Cleared world '{}'", worldName);
    }

    /**
     * Shuts down the world and all systems.
     */
    public void shutdown() {
        LOGGER.info("Shutting down world '{}'...", worldName);
        clearEntities();
        chunkManager.shutdown();

        if (physicsManager != null) {
            physicsManager.shutdown();
        }

        LOGGER.info("World '{}' shut down successfully", worldName);
    }

    // ==================== Debug Info ====================

    /**
     * Gets debug information about the world state.
     */
    public String getDebugInfo() {
        return String.format(
                "World '%s' | Seed: %d | Entities: %d | Loaded Chunks: %d | %s",
                worldName,
                seed,
                entities.size(),
                chunkManager.getLoadedChunkCount(),
                chunkManager.getGenerationStats()
        );
    }

    @Override
    public String toString() {
        return String.format("World{name='%s', seed=%d, entities=%d, chunks=%d}",
                worldName, seed, entities.size(), chunkManager.getLoadedChunkCount());
    }
}