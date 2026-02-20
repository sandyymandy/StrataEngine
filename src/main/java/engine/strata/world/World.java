package engine.strata.world;

import engine.strata.client.StrataClient;
import engine.strata.client.input.keybind.Keybinds;
import engine.strata.entity.Entity;
import engine.strata.entity.entities.PlayerEntity;
import engine.strata.physics.PhysicsManager;
import engine.strata.util.math.BlockPos;
import engine.strata.util.math.Random;
import engine.strata.util.math.Vec3d;
import engine.strata.world.block.Block;
import engine.strata.world.block.DynamicTextureArray;
import engine.strata.world.chunk.ChunkGenerator;
import engine.strata.world.chunk.ChunkManager;
import engine.strata.world.chunk.SubChunk;
import engine.strata.world.chunk.render.ChunkMeshBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class World {
    private static final Logger LOGGER = LoggerFactory.getLogger("World");

    private final ConcurrentHashMap<UUID, Entity> entities = new ConcurrentHashMap<>();
    private volatile List<Entity> entityList = new ArrayList<>();

    private final PhysicsManager physicsManager;

    // Chunk system
    private final ChunkManager chunkManager;
    private final ChunkGenerator chunkGenerator;
    private ChunkMeshBuilder chunkMeshBuilder; // initialized later with atlas

    private final long seed;
    private final String worldName;
    private final Random random = new Random(System.currentTimeMillis());
    // Player position tracking for chunk loading
    private int lastPlayerChunkX = Integer.MAX_VALUE;
    private int lastPlayerChunkZ = Integer.MAX_VALUE;
    private static final int CHUNK_UPDATE_THRESHOLD = 1; // Update chunks every N chunks moved

    public World(String worldName, long seed) {
        this.worldName = worldName;
        this.seed = seed;
        this.physicsManager = new PhysicsManager();
        this.physicsManager.init();

        // Initialize chunk system
        this.chunkManager = new ChunkManager();
        this.chunkGenerator = new ChunkGenerator(chunkManager, seed);
        this.chunkMeshBuilder = null; // Will be initialized when texture atlas is ready

        if(StrataClient.getInstance().getDebugInfo().showWorldDebug())
            LOGGER.info("Creating world '{}' with seed {}", worldName, seed);
    }

    public World() {
        this("World", System.currentTimeMillis());
    }

    /**
     * Initializes the chunk mesher with the texture array.
     * Should be called after the texture array is built.
     */
    public ChunkMeshBuilder initializeChunkMesher(DynamicTextureArray array) {
        return new ChunkMeshBuilder(chunkManager, array);
    }

    /**
     * Starts the chunk generation and meshing threads.
     * Should be called after world initialization and after mesher is created.
     */
    public void startChunkThreads(ChunkMeshBuilder mesher) {
        this.chunkMeshBuilder = mesher;

        // Both components now own and start their own daemon threads
        chunkGenerator.start();
        chunkMeshBuilder.start();

        LOGGER.info("Chunk generation and meshing threads started");
    }

    /**
     * Ticks entities and manages chunk loading.
     */
    public void tick() {
        if (physicsManager != null) {
            physicsManager.update(1.0f / 60.0f);
        }

        // Tick entities
        for (Entity entity : entityList) {
            entity.tick();

            if (entity instanceof PlayerEntity) {
                updateChunksAroundPlayer(entity);
            }
        }
    }

    /**
     * Updates chunks around player with movement threshold.
     */
    private void updateChunksAroundPlayer(Entity player) {
        int playerChunkX = (int) Math.floor(player.getPosition().getX() / SubChunk.SIZE);
        int playerChunkZ = (int) Math.floor(player.getPosition().getZ() / SubChunk.SIZE);

        // Check if player has moved far enough to update chunks
        int dx = Math.abs(playerChunkX - lastPlayerChunkX);
        int dz = Math.abs(playerChunkZ - lastPlayerChunkZ);

        if (dx >= CHUNK_UPDATE_THRESHOLD || dz >= CHUNK_UPDATE_THRESHOLD) {
            lastPlayerChunkX = playerChunkX;
            lastPlayerChunkZ = playerChunkZ;

            // Request generation for chunks around player
            List<ChunkManager.ChunkPos> chunksToLoad = chunkManager.getChunksToLoad(
                    player.getPosition().getX(),
                    player.getPosition().getZ()
            );

            chunkGenerator.requestGeneration(chunksToLoad);

            // Unload distant chunks
            List<ChunkManager.ChunkPos> chunksToUnload = chunkManager.getChunksToUnload(
                    player.getPosition().getX(),
                    player.getPosition().getZ()
            );

            for (ChunkManager.ChunkPos pos : chunksToUnload) {
                unloadChunk(pos.x, pos.z);
            }

            if (StrataClient.getInstance().getDebugInfo().showWorldDebug() && chunksToLoad.size() > 0) {
                LOGGER.debug("Player moved to chunk [{}, {}] - loading {} chunks, unloading {}",
                        playerChunkX, playerChunkZ, chunksToLoad.size(), chunksToUnload.size());
            }
        }
    }

    /**
     * Pre-loads chunks around spawn with priority loading.
     */
    public void preloadChunks(double x, double y, double z) {
        LOGGER.info("Pre-loading chunks around [{}, {}, {}]", x, y, z);

        List<ChunkManager.ChunkPos> chunks = chunkManager.getChunksToLoad(x, z);
        chunkGenerator.requestGeneration(chunks);

        LOGGER.info("Requested generation of {} spawn chunks", chunks.size());
    }

    /**
     * Unloads a chunk and disposes of its mesh.
     */
    private void unloadChunk(int chunkX, int chunkZ) {
        chunkManager.removeChunk(chunkX, chunkZ);

        // Note: Mesh disposal happens in ChunkRenderer
        // We just need to remove the chunk data here

        if (StrataClient.getInstance().getDebugInfo().showWorldDebug()) {
            LOGGER.debug("Unloaded chunk [{}, {}]", chunkX, chunkZ);
        }
    }

    /**
     * Gets a block at a BlockPos.
     */
    public short getBlock(BlockPos pos) {
        return chunkManager.getBlock(pos.getX(), pos.getY(), pos.getZ());
    }

    /**
     * Sets a block at a BlockPos.
     */
    public void setBlock(BlockPos pos, short blockId) {
        chunkManager.setBlock(pos.getX(), pos.getY(), pos.getZ(), blockId);
    }

    /**
     * Sets a block at a BlockPos.
     */
    public void setBlock(BlockPos pos, Block block) {
        setBlock(pos, block.getNumericId());
    }

    /**
     * Checks if a block position is air.
     */
    public boolean isAir(BlockPos pos) {
        return getBlock(pos) == 0;
    }

    /**
     * Gets the block at entity's feet position.
     */
    public short getBlockAtEntity(Entity entity) {
        return getBlock(new BlockPos(entity.getPosition()));
    }

    /**
     * Gets the block at entity's eye position.
     */
    public short getBlockAtEntityEye(Entity entity) {
        Vec3d eyePos = entity.getPosition().add(0, entity.getEyeHeight(), 0);
        return getBlock(new BlockPos(eyePos));
    }

    public void addEntity(Entity entity) {
        if (entity == null) {
            LOGGER.warn("Attempted to add null entity");
            return;
        }

        UUID id = UUID.randomUUID();
        entities.put(id, entity);
        updateEntityList();

        if(StrataClient.getInstance().getDebugInfo().showWorldDebug())
            LOGGER.debug("Added entity {} with ID {}", entity.getClass().getSimpleName(), id);
    }

    public void removeEntity(UUID id) {
        Entity removed = entities.remove(id);
        if (removed != null) {
            updateEntityList();
            if(StrataClient.getInstance().getDebugInfo().showWorldDebug())
                LOGGER.debug("Removed entity {} with ID {}", removed.getClass().getSimpleName(), id);
        }
    }

    public void removeEntity(Entity entity) {
        entities.entrySet().removeIf(entry -> entry.getValue() == entity);
        updateEntityList();
    }

    public List<Entity> getEntities() {
        return entityList;
    }

    public int getEntityCount() {
        return entities.size();
    }

    private void updateEntityList() {
        entityList = new ArrayList<>(entities.values());
    }

    public PhysicsManager getPhysicsManager() {
        return physicsManager;
    }

    public ChunkManager getChunkManager() {
        return chunkManager;
    }

    public ChunkGenerator getChunkGenerator() {
        return chunkGenerator;
    }

    public long getSeed() {
        return seed;
    }

    public String getWorldName() {
        return worldName;
    }

    public void clearEntities() {
        entities.clear();
        updateEntityList();
        LOGGER.info("Cleared all entities");
    }

    public void clearChunks() {
        chunkManager.clear();
        LOGGER.info("Cleared all chunks");
    }

    public void clear() {
        clearEntities();
        clearChunks();
        LOGGER.info("Cleared world '{}'", worldName);
    }

    public void shutdown() {
        LOGGER.info("Shutting down world '{}'...", worldName);

        // Stop chunk threads
        if (chunkGenerator != null) {
            chunkGenerator.stop();
        }

        if (chunkMeshBuilder != null) {
            chunkMeshBuilder.stop();
        }

        clearEntities();
        clearChunks();

        if (physicsManager != null) {
            physicsManager.shutdown();
        }

        LOGGER.info("World '{}' shut down successfully", worldName);
    }

    /**
     * Gets debug information about the world state.
     */
    public String getDebugInfo() {
        return String.format(
                "World: %s | Chunks: %d (%d regions) | Memory: %.2f MB | Queue: Gen=%d",
                worldName,
                chunkManager.getTotalChunks(),
                chunkManager.getTotalRegions(),
                chunkManager.getTotalMemoryUsageMB(),
                chunkGenerator.getQueueSize()
        );
    }
}