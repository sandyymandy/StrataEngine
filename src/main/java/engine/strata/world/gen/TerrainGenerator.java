package engine.strata.world.gen;

import engine.strata.client.StrataClient;
import engine.strata.world.block.Blocks;
import engine.strata.world.chunk.Chunk;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Random;

/**
 * Generates terrain for chunks.
 * Simple flat world with some hills for now.
 */
public class TerrainGenerator {
    private static final Logger LOGGER = LoggerFactory.getLogger("TerrainGenerator");

    private final long seed;
    private final SimplexNoise noise;

    public TerrainGenerator(long seed) {
        this.seed = seed;
        this.noise = new SimplexNoise(seed);
    }

    /**
     * Generates terrain for a chunk.
     */
    public void generateChunk(Chunk chunk) {
        int chunkX = chunk.getChunkX();
        int chunkY = chunk.getChunkY();
        int chunkZ = chunk.getChunkZ();

        // Generate blocks
        for (int x = 0; x < Chunk.SIZE; x++) {
            for (int z = 0; z < Chunk.SIZE; z++) {
                int worldX = chunk.localToWorldX(x);
                int worldZ = chunk.localToWorldZ(z);

                // Calculate terrain height using noise
                int height = getTerrainHeight(worldX, worldZ);

                for (int y = 0; y < Chunk.SIZE; y++) {
                    int worldY = chunk.localToWorldY(y);

                    if (worldY < height - 3) {
                        // Deep underground = stone
                        chunk.setBlock(x, y, z, Blocks.STONE);
                    } else if (worldY < height) {
                        // Near surface = dirt
                        chunk.setBlock(x, y, z, Blocks.DIRT);
                    } else if (worldY == height) {
                        // Surface = grass
                        chunk.setBlock(x, y, z, Blocks.GRASS);
                    }
                    // else: air (default)
                }
            }
        }

        chunk.markGenerated();
        if(StrataClient.getInstance().getDebugInfo().showWorldDebug()) LOGGER.debug("Generated terrain for {}", chunk);
    }

    /**
     * Calculates terrain height at world coordinates.
     */
    private int getTerrainHeight(int worldX, int worldZ) {
        // Multi-octave noise for varied terrain
        double scale1 = 0.01;  // Large features
        double scale2 = 0.05;  // Medium features
        double scale3 = 0.1;   // Small features

        double n1 = noise.noise(worldX * scale1, worldZ * scale1) * 20;
        double n2 = noise.noise(worldX * scale2, worldZ * scale2) * 10;
        double n3 = noise.noise(worldX * scale3, worldZ * scale3) * 5;

        double totalNoise = n1 + n2 + n3;

        // Base height + noise
        int baseHeight = 64; // Sea level
        return baseHeight + (int) totalNoise;
    }

    /**
     * Simple Simplex Noise implementation.
     * (This is a simplified version - use a proper library for production)
     */
    private static class SimplexNoise {
        private final Random random;

        public SimplexNoise(long seed) {
            this.random = new Random(seed);
        }

        public double noise(double x, double z) {
            // Simplified 2D noise - replace with proper simplex noise
            int xi = (int) Math.floor(x);
            int zi = (int) Math.floor(z);

            double xf = x - xi;
            double zf = z - zi;

            // Generate pseudo-random values at grid corners
            double n00 = hash(xi, zi);
            double n10 = hash(xi + 1, zi);
            double n01 = hash(xi, zi + 1);
            double n11 = hash(xi + 1, zi + 1);

            // Smooth interpolation
            double nx0 = lerp(n00, n10, smooth(xf));
            double nx1 = lerp(n01, n11, smooth(xf));

            return lerp(nx0, nx1, smooth(zf));
        }

        private double hash(int x, int z) {
            random.setSeed(x * 374761393L + z * 668265263L);
            return random.nextDouble() * 2.0 - 1.0;
        }

        private double lerp(double a, double b, double t) {
            return a + t * (b - a);
        }

        private double smooth(double t) {
            return t * t * (3.0 - 2.0 * t); // Smoothstep
        }
    }
}