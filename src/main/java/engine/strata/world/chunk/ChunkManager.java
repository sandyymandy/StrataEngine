package engine.strata.world.chunk;

import engine.strata.client.StrataClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;


public class ChunkManager {
    private static final Logger LOGGER = LoggerFactory.getLogger("ChunkManager");

    private int renderDistance = 16; // ← was 8

    private final ConcurrentHashMap<Long, Region> regions    = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long, Chunk>  chunkCache = new ConcurrentHashMap<>();

    private final AtomicInteger totalChunks  = new AtomicInteger(0);
    private final AtomicInteger totalRegions = new AtomicInteger(0);

    public ChunkManager() {
        LOGGER.info("ChunkManager initialized");
    }

    // ── Chunk access ─────────────────────────────────────────────────────────

    public Chunk getChunk(int chunkX, int chunkZ) {
        return chunkCache.get(packChunkKey(chunkX, chunkZ));
    }

    public Chunk getOrCreateChunk(int chunkX, int chunkZ) {
        long key = packChunkKey(chunkX, chunkZ);
        return chunkCache.computeIfAbsent(key, k -> {
            Chunk chunk = new Chunk(chunkX, chunkZ);
            addChunkToRegion(chunk);
            totalChunks.incrementAndGet();
            return chunk;
        });
    }

    private synchronized void addChunkToRegion(Chunk chunk) {
        int regionX = Math.floorDiv(chunk.getChunkX(), Region.SIZE);
        int regionZ = Math.floorDiv(chunk.getChunkZ(), Region.SIZE);
        int regionY = 0;

        long regionKey = packRegionKey(regionX, regionY, regionZ);
        Region region = regions.computeIfAbsent(regionKey, k -> {
            Region r = new Region(regionX, regionY, regionZ);
            totalRegions.incrementAndGet(); // FIX: was totalRegions++, unsafe
            if (StrataClient.getInstance().getDebugInfo().showWorldDebug()) {
                LOGGER.debug("Created new region: {}", r);
            }
            return r;
        });

        region.addChunk(chunk);
    }

    // ── Chunk removal ─────────────────────────────────────────────────────────

    public synchronized void removeChunk(int chunkX, int chunkZ) {
        long  key   = packChunkKey(chunkX, chunkZ);
        Chunk chunk = chunkCache.remove(key);
        if (chunk == null) return;

        // FIX: sever neighbor back-references FIRST so neighboring chunks
        //      don't hold a dangling pointer to this chunk after clear().
        breakNeighborReferences(chunk);

        int regionX   = Math.floorDiv(chunkX, Region.SIZE);
        int regionZ   = Math.floorDiv(chunkZ, Region.SIZE);
        long regionKey = packRegionKey(regionX, 0, regionZ);
        Region region  = regions.get(regionKey);

        if (region != null) {
            region.removeChunk(chunkX, chunkZ);
            if (region.isEmpty()) {
                regions.remove(regionKey);
                totalRegions.decrementAndGet(); // FIX: was totalRegions--
            }
        }

        chunk.clear();
        totalChunks.decrementAndGet(); // FIX: was totalChunks--
    }

    /**
     * Removes this chunk from all four neighbours' reference slots so
     * the freed Chunk object can be garbage-collected.
     */
    private void breakNeighborReferences(Chunk chunk) {
        int x = chunk.getChunkX();
        int z = chunk.getChunkZ();

        Chunk north = getChunk(x, z - 1);
        if (north != null) north.setNeighbor(Chunk.ChunkDirection.SOUTH, null);

        Chunk south = getChunk(x, z + 1);
        if (south != null) south.setNeighbor(Chunk.ChunkDirection.NORTH, null);

        Chunk east = getChunk(x + 1, z);
        if (east != null) east.setNeighbor(Chunk.ChunkDirection.WEST, null);

        Chunk west = getChunk(x - 1, z);
        if (west != null) west.setNeighbor(Chunk.ChunkDirection.EAST, null);
    }

    // ── Block access ──────────────────────────────────────────────────────────

    public short getBlock(int worldX, int worldY, int worldZ) {
        int   chunkX = Math.floorDiv(worldX, SubChunk.SIZE);
        int   chunkZ = Math.floorDiv(worldZ, SubChunk.SIZE);
        Chunk chunk  = getChunk(chunkX, chunkZ);
        return chunk == null ? 0 : chunk.getBlockWorld(worldX, worldY, worldZ);
    }

    public void setBlock(int worldX, int worldY, int worldZ, short blockId) {
        int   chunkX = Math.floorDiv(worldX, SubChunk.SIZE);
        int   chunkZ = Math.floorDiv(worldZ, SubChunk.SIZE);
        Chunk chunk  = getOrCreateChunk(chunkX, chunkZ);
        chunk.setBlockWorld(worldX, worldY, worldZ, blockId);
    }

    // ── Load / unload helpers ─────────────────────────────────────────────────

    public List<ChunkPos> getChunksToLoad(double centerX, double centerZ) {
        List<ChunkPos> chunks = new ArrayList<>();
        int cx = (int) Math.floor(centerX / SubChunk.SIZE);
        int cz = (int) Math.floor(centerZ / SubChunk.SIZE);

        for (int dx = -renderDistance; dx <= renderDistance; dx++) {
            for (int dz = -renderDistance; dz <= renderDistance; dz++) {
                if (dx * dx + dz * dz <= renderDistance * renderDistance) {
                    chunks.add(new ChunkPos(cx + dx, cz + dz));
                }
            }
        }
        return chunks;
    }

    public List<ChunkPos> getChunksToUnload(double centerX, double centerZ) {
        List<ChunkPos> toUnload = new ArrayList<>();
        int cx = (int) Math.floor(centerX / SubChunk.SIZE);
        int cz = (int) Math.floor(centerZ / SubChunk.SIZE);
        // FIX: unload buffer raised from +2 → +4 to match the larger render distance
        int unloadDist = renderDistance + 4;

        for (Chunk chunk : chunkCache.values()) {
            int dx = chunk.getChunkX() - cx;
            int dz = chunk.getChunkZ() - cz;
            if (dx * dx + dz * dz > unloadDist * unloadDist) {
                toUnload.add(new ChunkPos(chunk.getChunkX(), chunk.getChunkZ()));
            }
        }
        return toUnload;
    }

    // ── Region helpers ────────────────────────────────────────────────────────

    public synchronized List<Region> getVisibleRegions(float camX, float camY, float camZ,
                                                       float maxDistance) {
        List<Region> visible = new ArrayList<>();
        for (Region r : regions.values()) {
            if (r.isVisible(camX, camY, camZ, maxDistance)) visible.add(r);
        }
        return visible;
    }

    // ── Neighbor management ───────────────────────────────────────────────────

    public void updateChunkNeighbors(Chunk chunk) {
        int x = chunk.getChunkX();
        int z = chunk.getChunkZ();

        chunk.setNeighbor(Chunk.ChunkDirection.NORTH, getChunk(x, z - 1));
        chunk.setNeighbor(Chunk.ChunkDirection.SOUTH, getChunk(x, z + 1));
        chunk.setNeighbor(Chunk.ChunkDirection.EAST,  getChunk(x + 1, z));
        chunk.setNeighbor(Chunk.ChunkDirection.WEST,  getChunk(x - 1, z));

        Chunk north = getChunk(x, z - 1);
        if (north != null) north.setNeighbor(Chunk.ChunkDirection.SOUTH, chunk);
        Chunk south = getChunk(x, z + 1);
        if (south != null) south.setNeighbor(Chunk.ChunkDirection.NORTH, chunk);
        Chunk east  = getChunk(x + 1, z);
        if (east  != null) east .setNeighbor(Chunk.ChunkDirection.WEST,  chunk);
        Chunk west  = getChunk(x - 1, z);
        if (west  != null) west .setNeighbor(Chunk.ChunkDirection.EAST,  chunk);
    }

    public void markNeighborsForRemesh(int chunkX, int chunkZ) {
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                Chunk neighbor = getChunk(chunkX + dx, chunkZ + dz);
                if (neighbor != null) neighbor.setNeedsRemesh(true);
            }
        }
    }

    // ── Accessors ─────────────────────────────────────────────────────────────

    public Collection<Chunk>  getAllChunks()  { return chunkCache.values(); }
    public synchronized Collection<Region> getAllRegions() { return new ArrayList<>(regions.values()); }

    public synchronized void clear() {
        for (Chunk chunk : chunkCache.values()) chunk.clear();
        chunkCache.clear();
        regions.clear();
        totalChunks .set(0);
        totalRegions.set(0);
        LOGGER.info("Cleared all chunks and regions");
    }

    // ── Key packing ───────────────────────────────────────────────────────────

    private static long packChunkKey(int chunkX, int chunkZ) {
        return ((long) chunkX << 32) | (chunkZ & 0xFFFFFFFFL);
    }

    private static long packRegionKey(int regionX, int regionY, int regionZ) {
        return ((long) regionX << 42) | ((long) regionY << 21) | (regionZ & 0x1FFFFFL);
    }

    // ── Getters / setters ─────────────────────────────────────────────────────

    public int  getRenderDistance()             { return renderDistance; }
    public void setRenderDistance(int distance) {
        this.renderDistance = Math.max(2, Math.min(200, distance));
        LOGGER.info("Render distance set to {} chunks", this.renderDistance);
    }

    public int   getTotalChunks()   { return totalChunks.get(); }
    public int   getTotalRegions()  { return totalRegions.get(); }

    public float getTotalMemoryUsageMB() {
        long total = 0;
        for (Chunk c : chunkCache.values()) total += c.getMemoryUsage();
        return total / (1024.0f * 1024.0f);
    }

    // ── Inner type ────────────────────────────────────────────────────────────

    public static class ChunkPos {
        public final int x, z;
        public ChunkPos(int x, int z) { this.x = x; this.z = z; }

        @Override public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof ChunkPos)) return false;
            ChunkPos c = (ChunkPos) o;
            return x == c.x && z == c.z;
        }
        @Override public int    hashCode() { return 31 * x + z; }
        @Override public String toString() { return String.format("ChunkPos[%d, %d]", x, z); }
    }
}