package engine.strata.world.lighting;

import engine.strata.client.StrataClient;
import engine.strata.world.block.Block;
import engine.strata.world.chunk.Chunk;
import engine.strata.world.chunk.ChunkManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayDeque;
import java.util.Queue;

/**
 * Dynamic lighting system inspired by Minecraft.
 * Handles both sky light (sunlight) and block light (torches, etc.)
 * with proper light propagation and attenuation.
 * * Thread-safe: Uses local queues for propagation to allow concurrent chunk generation.
 */
public class LightingEngine {
    private static final Logger LOGGER = LoggerFactory.getLogger("Lighting");

    private final ChunkManager chunkManager;

    public LightingEngine(ChunkManager chunkManager) {
        this.chunkManager = chunkManager;
    }

    /**
     * Calculates initial lighting for a newly generated chunk.
     */
    public void calculateInitialLighting(Chunk chunk) {
        long startTime = System.nanoTime();

        // First pass: Sky light (sunlight from above)
        calculateSkyLight(chunk);

        // Second pass: Block light (from light-emitting blocks)
        calculateBlockLight(chunk);

        long endTime = System.nanoTime();
        double timeMs = (endTime - startTime) / 1_000_000.0;

        if(StrataClient.getInstance().getDebugInfo().showLightDebug())
            LOGGER.debug("Calculated lighting for {} in {:.2f}ms", chunk, timeMs);
    }

    /**
     * Calculates sky light for a chunk.
     * Light propagates down from the top of the world.
     */
    private void calculateSkyLight(Chunk chunk) {
        // Create a local queue for this specific calculation to ensure thread safety
        Queue<LightNode> queue = new ArrayDeque<>();

        for (int x = 0; x < Chunk.SIZE; x++) {
            for (int z = 0; z < Chunk.SIZE; z++) {
                // Start from the top of this chunk
                int currentLight = 15; // Full sunlight at top

                // Check if there's a chunk above
                Chunk chunkAbove = chunkManager.getChunkIfLoaded(
                        chunk.getChunkX(),
                        chunk.getChunkY() + 1,
                        chunk.getChunkZ()
                );

                // If there's a chunk above, get light from it
                if (chunkAbove != null) {
                    currentLight = chunkAbove.getSkyLight(x, 0, z);
                }

                // Propagate light downward through this chunk
                for (int y = Chunk.SIZE - 1; y >= 0; y--) {
                    Block block = chunk.getBlock(x, y, z);

                    if (block.isOpaque()) {
                        // Opaque blocks block sky light
                        currentLight = 0;
                    } else if (!block.isAir()) {
                        // Transparent blocks reduce light slightly
                        currentLight = Math.max(0, currentLight - 1);
                    }

                    chunk.setSkyLight(x, y, z, currentLight);

                    // Also propagate to neighbors using the local queue
                    if (currentLight > 1) {
                        propagateSkyLight(chunk, x, y, z, currentLight, queue);
                    }
                }
            }
        }
    }

    /**
     * Propagates sky light to neighboring blocks.
     */
    private void propagateSkyLight(Chunk chunk, int x, int y, int z, int lightLevel, Queue<LightNode> queue) {
        // Queue for BFS light propagation
        queue.clear();
        queue.offer(new LightNode(chunk.getChunkX(), chunk.getChunkY(), chunk.getChunkZ(),
                x, y, z, lightLevel));

        while (!queue.isEmpty() && queue.size() < 1000) { // Limit iterations
            LightNode node = queue.poll();
            if (node == null) continue;

            // Check all 6 neighbors
            propagateSkyLightToNeighbor(node, -1, 0, 0, queue); // West
            propagateSkyLightToNeighbor(node, 1, 0, 0, queue);  // East
            propagateSkyLightToNeighbor(node, 0, -1, 0, queue); // Down
            propagateSkyLightToNeighbor(node, 0, 1, 0, queue);  // Up
            propagateSkyLightToNeighbor(node, 0, 0, -1, queue); // North
            propagateSkyLightToNeighbor(node, 0, 0, 1, queue);  // South
        }
    }

    /**
     * Helper to propagate sky light to a specific neighbor.
     */
    private void propagateSkyLightToNeighbor(LightNode node, int dx, int dy, int dz, Queue<LightNode> queue) {
        int nx = node.localX + dx;
        int ny = node.localY + dy;
        int nz = node.localZ + dz;

        int chunkX = node.chunkX;
        int chunkY = node.chunkY;
        int chunkZ = node.chunkZ;

        // Handle chunk boundaries
        if (nx < 0) { nx += Chunk.SIZE; chunkX--; }
        if (nx >= Chunk.SIZE) { nx -= Chunk.SIZE; chunkX++; }
        if (ny < 0) { ny += Chunk.SIZE; chunkY--; }
        if (ny >= Chunk.SIZE) { ny -= Chunk.SIZE; chunkY++; }
        if (nz < 0) { nz += Chunk.SIZE; chunkZ--; }
        if (nz >= Chunk.SIZE) { nz -= Chunk.SIZE; chunkZ++; }

        Chunk neighborChunk = chunkManager.getChunkIfLoaded(chunkX, chunkY, chunkZ);
        if (neighborChunk == null) {
            return;
        }

        Block neighborBlock = neighborChunk.getBlock(nx, ny, nz);
        if (neighborBlock.isOpaque()) {
            return;
        }

        int newLight = node.lightLevel - 1;
        if (newLight <= 0) {
            return;
        }

        int currentLight = neighborChunk.getSkyLight(nx, ny, nz);
        if (newLight > currentLight) {
            neighborChunk.setSkyLight(nx, ny, nz, newLight);
            queue.offer(new LightNode(chunkX, chunkY, chunkZ, nx, ny, nz, newLight));
        }
    }

    /**
     * Calculates block light (torches, glowstone, etc.)
     */
    private void calculateBlockLight(Chunk chunk) {
        // Create local queue
        Queue<LightNode> queue = new ArrayDeque<>();

        // Find all light-emitting blocks
        for (int x = 0; x < Chunk.SIZE; x++) {
            for (int y = 0; y < Chunk.SIZE; y++) {
                for (int z = 0; z < Chunk.SIZE; z++) {
                    Block block = chunk.getBlock(x, y, z);
                    int emission = block.getLightEmission();

                    if (emission > 0) {
                        chunk.setBlockLight(x, y, z, emission);
                        queue.offer(new LightNode(
                                chunk.getChunkX(), chunk.getChunkY(), chunk.getChunkZ(),
                                x, y, z, emission
                        ));
                    }
                }
            }
        }

        // Propagate block light using BFS
        propagateBlockLight(queue);
    }

    /**
     * Propagates block light from light sources.
     */
    private void propagateBlockLight(Queue<LightNode> queue) {
        int iterations = 0;
        while (!queue.isEmpty() && iterations++ < 10000) { // Safety limit
            LightNode node = queue.poll();
            if (node == null) continue;

            // Propagate to all 6 neighbors
            propagateBlockLightToNeighbor(node, -1, 0, 0, queue);
            propagateBlockLightToNeighbor(node, 1, 0, 0, queue);
            propagateBlockLightToNeighbor(node, 0, -1, 0, queue);
            propagateBlockLightToNeighbor(node, 0, 1, 0, queue);
            propagateBlockLightToNeighbor(node, 0, 0, -1, queue);
            propagateBlockLightToNeighbor(node, 0, 0, 1, queue);
        }
    }

    /**
     * Helper to propagate block light to a specific neighbor.
     */
    private void propagateBlockLightToNeighbor(LightNode node, int dx, int dy, int dz, Queue<LightNode> queue) {
        int nx = node.localX + dx;
        int ny = node.localY + dy;
        int nz = node.localZ + dz;

        int chunkX = node.chunkX;
        int chunkY = node.chunkY;
        int chunkZ = node.chunkZ;

        // Handle chunk boundaries
        if (nx < 0) { nx += Chunk.SIZE; chunkX--; }
        if (nx >= Chunk.SIZE) { nx -= Chunk.SIZE; chunkX++; }
        if (ny < 0) { ny += Chunk.SIZE; chunkY--; }
        if (ny >= Chunk.SIZE) { ny -= Chunk.SIZE; chunkY++; }
        if (nz < 0) { nz += Chunk.SIZE; chunkZ--; }
        if (nz >= Chunk.SIZE) { nz -= Chunk.SIZE; chunkZ++; }

        Chunk neighborChunk = chunkManager.getChunkIfLoaded(chunkX, chunkY, chunkZ);
        if (neighborChunk == null) {
            return;
        }

        Block neighborBlock = neighborChunk.getBlock(nx, ny, nz);
        if (neighborBlock.isOpaque()) {
            return;
        }

        int newLight = node.lightLevel - 1;
        if (newLight <= 0) {
            return;
        }

        int currentLight = neighborChunk.getBlockLight(nx, ny, nz);
        if (newLight > currentLight) {
            neighborChunk.setBlockLight(nx, ny, nz, newLight);
            queue.offer(new LightNode(chunkX, chunkY, chunkZ, nx, ny, nz, newLight));
        }
    }

    /**
     * Updates lighting when a block changes.
     * Call this after placing or breaking blocks.
     */
    public void updateLightingAt(int worldX, int worldY, int worldZ) {
        Chunk chunk = chunkManager.getChunkAtWorldPos(worldX, worldY, worldZ);
        if (chunk == null) {
            return;
        }

        int localX = Math.floorMod(worldX, Chunk.SIZE);
        int localY = Math.floorMod(worldY, Chunk.SIZE);
        int localZ = Math.floorMod(worldZ, Chunk.SIZE);

        Block block = chunk.getBlock(localX, localY, localZ);

        // Recalculate light for this block and propagate
        if (block.getLightEmission() > 0) {
            // Create local queue for this update
            Queue<LightNode> queue = new ArrayDeque<>();

            chunk.setBlockLight(localX, localY, localZ, block.getLightEmission());
            queue.offer(new LightNode(
                    chunk.getChunkX(), chunk.getChunkY(), chunk.getChunkZ(),
                    localX, localY, localZ, block.getLightEmission()
            ));
            propagateBlockLight(queue);
        } else {
            // Block doesn't emit light, recalculate from neighbors
            recalculateLightingAtPosition(chunk, localX, localY, localZ);
        }
    }

    /**
     * Recalculates lighting at a specific position based on neighbors.
     */
    private void recalculateLightingAtPosition(Chunk chunk, int x, int y, int z) {
        // Find max light from neighbors and reduce by 1
        int maxBlockLight = 0;
        int maxSkyLight = 0;

        // Check all 6 neighbors
        int[][] neighbors = {
                {-1, 0, 0}, {1, 0, 0},
                {0, -1, 0}, {0, 1, 0},
                {0, 0, -1}, {0, 0, 1}
        };

        for (int[] offset : neighbors) {
            int worldX = chunk.localToWorldX(x + offset[0]);
            int worldY = chunk.localToWorldY(y + offset[1]);
            int worldZ = chunk.localToWorldZ(z + offset[2]);

            Block neighborBlock = chunkManager.getBlock(worldX, worldY, worldZ);
            if (!neighborBlock.isOpaque()) {
                Chunk neighborChunk = chunkManager.getChunkAtWorldPos(worldX, worldY, worldZ);
                if (neighborChunk != null) {
                    int nx = Math.floorMod(worldX, Chunk.SIZE);
                    int ny = Math.floorMod(worldY, Chunk.SIZE);
                    int nz = Math.floorMod(worldZ, Chunk.SIZE);

                    maxBlockLight = Math.max(maxBlockLight,
                            neighborChunk.getBlockLight(nx, ny, nz) - 1);
                    maxSkyLight = Math.max(maxSkyLight,
                            neighborChunk.getSkyLight(nx, ny, nz) - 1);
                }
            }
        }

        chunk.setBlockLight(x, y, z, Math.max(0, maxBlockLight));
        chunk.setSkyLight(x, y, z, Math.max(0, maxSkyLight));
    }

    /**
     * Node for light propagation queue.
     */
    private record LightNode(
            int chunkX, int chunkY, int chunkZ,
            int localX, int localY, int localZ,
            int lightLevel
    ) {}
}