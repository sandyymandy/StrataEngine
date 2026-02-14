package engine.strata.world.chunk.render;

import engine.strata.world.World;
import engine.strata.world.block.Block;
import engine.strata.world.chunk.Chunk;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Diagnostic utility to help debug chunk rendering issues.
 * Call this to check if your chunks are being generated correctly.
 */
public class ChunkRenderingDebugger {
    private static final Logger LOGGER = LoggerFactory.getLogger("ChunkDebug");

    /**
     * Runs comprehensive diagnostics on the world's chunk system.
     */
    public static void diagnose(World world) {
        LOGGER.info("===== CHUNK RENDERING DIAGNOSTICS =====");

        int totalChunks = world.getLoadedChunks().size();
        int generatedChunks = 0;
        int emptyChunks = 0;
        int nonEmptyChunks = 0;
        int totalBlocks = 0;
        int nonAirBlocks = 0;

        LOGGER.info("Total loaded chunks: {}", totalChunks);

        for (Chunk chunk : world.getLoadedChunks()) {
            if (chunk.isGenerated()) {
                generatedChunks++;

                if (chunk.isEmpty()) {
                    emptyChunks++;
                } else {
                    nonEmptyChunks++;

                    // Count blocks in this chunk
                    for (int x = 0; x < Chunk.SIZE; x++) {
                        for (int y = 0; y < Chunk.SIZE; y++) {
                            for (int z = 0; z < Chunk.SIZE; z++) {
                                totalBlocks++;
                                Block block = chunk.getBlock(x, y, z);
                                if (!block.isAir()) {
                                    nonAirBlocks++;
                                }
                            }
                        }
                    }

                    // Log first non-empty chunk details
                    if (nonEmptyChunks == 1) {
                        LOGGER.info("First non-empty chunk: {} at [{}, {}, {}]",
                                chunk, chunk.getChunkX(), chunk.getChunkY(), chunk.getChunkZ());
                        logSampleBlocks(chunk);
                    }
                }
            }
        }

        LOGGER.info("Generated chunks: {}/{}", generatedChunks, totalChunks);
        LOGGER.info("Empty chunks: {}", emptyChunks);
        LOGGER.info("Non-empty chunks: {}", nonEmptyChunks);
        LOGGER.info("Total blocks checked: {}", totalBlocks);
        LOGGER.info("Non-air blocks: {}", nonAirBlocks);

        if (nonEmptyChunks == 0) {
            LOGGER.warn("WARNING: No non-empty chunks found! Check terrain generation.");
        } else if (nonAirBlocks == 0) {
            LOGGER.warn("WARNING: Chunks exist but contain no non-air blocks!");
        } else {
            LOGGER.info("✓ Chunks are being generated with blocks");
        }

        LOGGER.info("=======================================");
    }

    /**
     * Logs sample blocks from a chunk for debugging.
     */
    private static void logSampleBlocks(Chunk chunk) {
        LOGGER.info("Sample blocks from chunk:");

        // Check bottom layer
        for (int x = 0; x < Chunk.SIZE; x += 4) {
            for (int z = 0; z < Chunk.SIZE; z += 4) {
                Block block = chunk.getBlock(x, 0, z);
                if (!block.isAir()) {
                    LOGGER.info("  [{}, 0, {}] = {} ({})",
                            x, z, block.getId(), block.isOpaque() ? "opaque" : "transparent");
                }
            }
        }
    }

    /**
     * Quick check to see if rendering should work.
     */
    public static boolean quickCheck(World world) {
        int generated = 0;
        int nonEmpty = 0;

        for (Chunk chunk : world.getLoadedChunks()) {
            if (chunk.isGenerated()) {
                generated++;
                if (!chunk.isEmpty()) {
                    nonEmpty++;
                }
            }
        }

        boolean ok = generated > 0 && nonEmpty > 0;

        if (!ok) {
            LOGGER.error("RENDERING WILL FAIL: generated={}, nonEmpty={}", generated, nonEmpty);
        }

        return ok;
    }
}