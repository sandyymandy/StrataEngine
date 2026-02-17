package engine.strata.world.chunk;

import engine.strata.client.StrataClient;
import engine.strata.world.block.Blocks;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Generates chunks on a separate thread.
 *
 * CHANGES:
 *  - Debug log line used "{:.2f}" which is a Python/C format — SLF4J uses "{}".
 *    Fixed to String.format() inside the {} placeholder.
 *  - requestGeneration() now skips adding the position if it is already present
 *    in the queue, preventing the queue from growing unboundedly when the same
 *    chunk is repeatedly requested before it gets generated.
 *  - stop() now clears the generation queue so the thread drains cleanly.
 */
public class ChunkGenerator implements Runnable {
    private static final Logger LOGGER = LoggerFactory.getLogger("ChunkGen");

    private final ChunkManager chunkManager;
    private final long         seed;

    private final Queue<ChunkManager.ChunkPos> generationQueue = new ConcurrentLinkedQueue<>();

    private final AtomicBoolean running = new AtomicBoolean(true);
    private final AtomicBoolean paused  = new AtomicBoolean(false);

    private int  chunksGenerated     = 0;
    private long totalGenerationTime = 0;

    private final SimplexNoise noise;

    public ChunkGenerator(ChunkManager chunkManager, long seed) {
        this.chunkManager = chunkManager;
        this.seed         = seed;
        this.noise        = new SimplexNoise(seed);
        LOGGER.info("ChunkGenerator initialized with seed: {}", seed);
    }

    // ── Thread loop ──────────────────────────────────────────────────────────

    @Override
    public void run() {
        Thread.currentThread().setName("ChunkGen");
        LOGGER.info("Chunk generation thread started");

        while (running.get()) {
            try {
                if (paused.get()) { Thread.sleep(100); continue; }

                ChunkManager.ChunkPos pos = generationQueue.poll();
                if (pos == null) { Thread.sleep(50); continue; }

                generateChunk(pos.x, pos.z);

            } catch (InterruptedException e) {
                LOGGER.warn("Chunk generation interrupted", e);
                break;
            } catch (Exception e) {
                LOGGER.error("Error during chunk generation", e);
            }
        }

        LOGGER.info("Chunk generation thread stopped. Generated {} chunks", chunksGenerated);
    }

    // ── Generation ───────────────────────────────────────────────────────────

    private void generateChunk(int chunkX, int chunkZ) {
        long  startTime = System.nanoTime();
        Chunk chunk     = chunkManager.getOrCreateChunk(chunkX, chunkZ);

        if (chunk.isGenerated()) return;

        generateTerrain(chunk);
        chunk.setGenerated(true);
        chunk.setNeedsRemesh(true);

        chunkManager.updateChunkNeighbors(chunk);
        chunkManager.markNeighborsForRemesh(chunkX, chunkZ);

        chunksGenerated++;
        totalGenerationTime += System.nanoTime() - startTime;

        if (StrataClient.getInstance().getDebugInfo().showWorldDebug() && chunksGenerated % 10 == 0) {
            float avgMs = (totalGenerationTime / chunksGenerated) / 1_000_000.0f;
            // FIX: was "{:.2f}" — not a valid SLF4J placeholder. Use String.format inside {}.
            LOGGER.debug("Generated {} chunks (avg: {} ms per chunk)",
                    chunksGenerated, String.format("%.2f", avgMs));
        }
    }

    private void generateTerrain(Chunk chunk) {
        int chunkX = chunk.getChunkX();
        int chunkZ = chunk.getChunkZ();

        for (int x = 0; x < SubChunk.SIZE; x++) {
            for (int z = 0; z < SubChunk.SIZE; z++) {
                int worldX      = chunkX * SubChunk.SIZE + x;
                int worldZ      = chunkZ * SubChunk.SIZE + z;
                int groundLevel = (int) generateHeight(worldX, worldZ);

                for (int y = 0; y <= groundLevel; y++) {
                    short blockId;
                    if      (y == groundLevel)      blockId = Blocks.GRASS.getNumericId();
                    else if (y >= groundLevel - 3)  blockId = Blocks.DIRT.getNumericId();
                    else                            blockId = Blocks.STONE.getNumericId();

                    chunk.setBlock(x, y, z, blockId);
                }
            }
        }
    }

    private float generateHeight(int worldX, int worldZ) {
        float scale     = 0.01f;
        float height    = (float) (noise.noise(worldX * scale, worldZ * scale) * 30);
        float hillScale = 0.005f;
        height += noise.noise(worldX * hillScale, worldZ * hillScale) * 50;
        height += 64;
        return Math.max(0, height);
    }

    // ── Public API ───────────────────────────────────────────────────────────

    /**
     * Requests generation of a chunk.
     * FIX: checks for duplicate entries before adding to avoid unbounded queue growth.
     */
    public void requestGeneration(int chunkX, int chunkZ) {
        Chunk existing = chunkManager.getChunk(chunkX, chunkZ);
        if (existing != null && existing.isGenerated()) return;

        ChunkManager.ChunkPos pos = new ChunkManager.ChunkPos(chunkX, chunkZ);
        // ConcurrentLinkedQueue.contains() is O(n) but called infrequently enough.
        if (!generationQueue.contains(pos)) {
            generationQueue.offer(pos);
        }
    }

    public void requestGeneration(Iterable<ChunkManager.ChunkPos> positions) {
        for (ChunkManager.ChunkPos pos : positions) {
            requestGeneration(pos.x, pos.z);
        }
    }

    /**
     * Stops the generation thread and clears the queue.
     * FIX: was missing the queue clear, leaving stale entries if restarted.
     */
    public void stop() {
        running.set(false);
        generationQueue.clear();
    }

    public void pause()  { paused.set(true);  }
    public void resume() { paused.set(false); }

    public int getQueueSize()       { return generationQueue.size(); }
    public int getChunksGenerated() { return chunksGenerated; }

    // ── SimplexNoise ─────────────────────────────────────────────────────────

    private static class SimplexNoise {
        private final int[] perm = new int[512];

        public SimplexNoise(long seed) {
            java.util.Random random = new java.util.Random(seed);
            int[] p = new int[256];
            for (int i = 0; i < 256; i++) p[i] = i;
            for (int i = 255; i > 0; i--) {
                int j    = random.nextInt(i + 1);
                int temp = p[i]; p[i] = p[j]; p[j] = temp;
            }
            for (int i = 0; i < 512; i++) perm[i] = p[i & 255];
        }

        public double noise(double x, double y) {
            int    X = (int) Math.floor(x) & 255;
            int    Y = (int) Math.floor(y) & 255;
            x -= Math.floor(x);
            y -= Math.floor(y);
            double u = fade(x), v = fade(y);
            int    A = perm[X] + Y, B = perm[X + 1] + Y;
            return lerp(v,
                    lerp(u, grad(perm[A],     x,     y),   grad(perm[B],     x - 1, y)),
                    lerp(u, grad(perm[A + 1], x,     y - 1), grad(perm[B + 1], x - 1, y - 1)));
        }

        private double fade(double t) { return t * t * t * (t * (t * 6 - 15) + 10); }
        private double lerp(double t, double a, double b) { return a + t * (b - a); }
        private double grad(int hash, double x, double y) {
            int    h = hash & 3;
            double u = h < 2 ? x : y;
            double v = h < 2 ? y : x;
            return ((h & 1) == 0 ? u : -u) + ((h & 2) == 0 ? v : -v);
        }
    }
}