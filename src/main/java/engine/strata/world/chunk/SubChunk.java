package engine.strata.world.chunk;

import engine.strata.world.block.Block;
import engine.strata.world.block.Blocks;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;

/**
 * Represents a 32x32x32 volume of blocks within a chunk.
 * Uses layer-based compression to optimize memory:
 * - If all blocks in a layer are the same, store only one block ID (4 bytes instead of 1KB)
 * - Layers are stored from bottom to top (Y=0 to Y=31)
 */
public class SubChunk {
    private static final Logger LOGGER = LoggerFactory.getLogger("SubChunk");

    public static final int SIZE = 32; // 32x32x32 subchunk
    public static final int AREA = SIZE * SIZE; // 1024 blocks per layer
    public static final int VOLUME = SIZE * SIZE * SIZE; // 32768 blocks total

    // Layer storage - each layer can be either uniform (single block) or varied (1024 blocks)
    private final ChunkLayer[] layers = new ChunkLayer[SIZE];

    // Block state storage (optional - only allocated if needed)
    private int[] blockStates = null;

    // Position within the chunk (in subchunk coordinates)
    private final int subChunkX;
    private final int subChunkY;
    private final int subChunkZ;

    // Flags
    private boolean isEmpty = true;
    private boolean isUniform = true; // All blocks are the same
    private short uniformBlockId = 0; // If uniform, this is the block ID

    public SubChunk(int subChunkX, int subChunkY, int subChunkZ) {
        this.subChunkX = subChunkX;
        this.subChunkY = subChunkY;
        this.subChunkZ = subChunkZ;

        // Initialize all layers as air
        for (int y = 0; y < SIZE; y++) {
            layers[y] = new ChunkLayer(Blocks.AIR.getNumericId());
        }
    }

    /**
     * Gets a block at local coordinates within this subchunk.
     * @param x Local X (0-31)
     * @param y Local Y (0-31)
     * @param z Local Z (0-31)
     * @return The block ID
     */
    public short getBlock(int x, int y, int z) {
        if (x < 0 || x >= SIZE || y < 0 || y >= SIZE || z < 0 || z >= SIZE) {
            return Blocks.AIR.getNumericId();
        }

        if (isUniform) {
            return uniformBlockId;
        }

        return layers[y].getBlock(x, z);
    }

    /**
     * Sets a block at local coordinates within this subchunk.
     * @param x Local X (0-31)
     * @param y Local Y (0-31)
     * @param z Local Z (0-31)
     * @param blockId The block ID to set
     */
    public void setBlock(int x, int y, int z, short blockId) {
        if (x < 0 || x >= SIZE || y < 0 || y >= SIZE || z < 0 || z >= SIZE) {
            return;
        }

        // If uniform and we're setting a different block, expand to layers
        if (isUniform && blockId != uniformBlockId) {
            expandToLayers();
        }

        layers[y].setBlock(x, z, blockId);

        // Update flags
        checkEmpty();
        checkUniform();
    }

    /**
     * Expands the uniform representation into individual layers.
     */
    private void expandToLayers() {
        short prevUniform = uniformBlockId;
        isUniform = false;

        // Each layer is already initialized, just make sure they have the right block
        for (int y = 0; y < SIZE; y++) {
            if (layers[y].isUniform() && layers[y].getUniformBlock() != prevUniform) {
                layers[y] = new ChunkLayer(prevUniform);
            }
        }
    }

    /**
     * Checks if the entire subchunk is empty (all air).
     */
    private void checkEmpty() {
        short airId = Blocks.AIR.getNumericId();

        if (isUniform) {
            isEmpty = (uniformBlockId == airId);
            return;
        }

        for (ChunkLayer layer : layers) {
            if (!layer.isUniform() || layer.getUniformBlock() != airId) {
                isEmpty = false;
                return;
            }
        }

        isEmpty = true;
    }

    /**
     * Checks if all blocks in the subchunk are the same.
     */
    private void checkUniform() {
        if (isUniform) {
            return; // Already uniform
        }

        // Get the first block
        short firstBlock = layers[0].isUniform()
                ? layers[0].getUniformBlock()
                : layers[0].getBlock(0, 0);

        // Check all layers
        for (ChunkLayer layer : layers) {
            if (!layer.isUniform()) {
                isUniform = false;
                return;
            }
            if (layer.getUniformBlock() != firstBlock) {
                isUniform = false;
                return;
            }
        }

        // All layers are uniform with the same block
        isUniform = true;
        uniformBlockId = firstBlock;
    }

    /**
     * Gets the block state at local coordinates.
     * @return Block state, or 0 if no states exist
     */
    public int getBlockState(int x, int y, int z) {
        if (blockStates == null) {
            return 0;
        }

        int index = (y * AREA) + (z * SIZE) + x;
        return blockStates[index];
    }

    /**
     * Sets the block state at local coordinates.
     */
    public void setBlockState(int x, int y, int z, int state) {
        if (state == 0 && blockStates == null) {
            return; // No need to allocate for default state
        }

        // Allocate block states if needed
        if (blockStates == null) {
            blockStates = new int[VOLUME];
        }

        int index = (y * AREA) + (z * SIZE) + x;
        blockStates[index] = state;
    }

    /**
     * Fills a region with a specific block.
     * Optimized bulk operation.
     */
    public void fill(int startX, int startY, int startZ,
                     int endX, int endY, int endZ, short blockId) {
        for (int y = startY; y <= endY && y < SIZE; y++) {
            for (int z = startZ; z <= endZ && z < SIZE; z++) {
                for (int x = startX; x <= endX && x < SIZE; x++) {
                    setBlock(x, y, z, blockId);
                }
            }
        }
    }

    // Getters
    public boolean isEmpty() {
        return isEmpty;
    }

    public boolean isUniform() {
        return isUniform;
    }

    public short getUniformBlockId() {
        return isUniform ? uniformBlockId : Blocks.AIR.getNumericId();
    }

    public boolean hasBlockStates() {
        return blockStates != null;
    }

    public int getSubChunkX() {
        return subChunkX;
    }

    public int getSubChunkY() {
        return subChunkY;
    }

    public int getSubChunkZ() {
        return subChunkZ;
    }

    /**
     * Gets memory usage of this subchunk in bytes.
     */
    public int getMemoryUsage() {
        int usage = 0;

        // Layer overhead
        usage += layers.length * 4; // Array references

        // Layer data
        for (ChunkLayer layer : layers) {
            usage += layer.getMemoryUsage();
        }

        // Block states
        if (blockStates != null) {
            usage += blockStates.length * 4; // int array
        }

        return usage;
    }

    /**
     * Represents a single horizontal layer (Y level) within a subchunk.
     * Can be uniform (all same block) or varied (1024 different blocks).
     */
    private static class ChunkLayer {
        private boolean uniform;
        private short uniformBlock;
        private short[] blocks; // Only allocated if not uniform

        public ChunkLayer(short blockId) {
            this.uniform = true;
            this.uniformBlock = blockId;
        }

        public short getBlock(int x, int z) {
            if (uniform) {
                return uniformBlock;
            }
            return blocks[z * SIZE + x];
        }

        public void setBlock(int x, int z, short blockId) {
            if (uniform) {
                if (blockId == uniformBlock) {
                    return; // No change needed
                }

                // Expand to full layer
                blocks = new short[AREA];
                Arrays.fill(blocks, uniformBlock);
                uniform = false;
            }

            blocks[z * SIZE + x] = blockId;

            // Check if layer became uniform again
            checkUniform();
        }

        private void checkUniform() {
            if (uniform || blocks == null) {
                return;
            }

            short first = blocks[0];
            for (short block : blocks) {
                if (block != first) {
                    return; // Not uniform
                }
            }

            // All blocks are the same - compress back to uniform
            uniform = true;
            uniformBlock = first;
            blocks = null;
        }

        public boolean isUniform() {
            return uniform;
        }

        public short getUniformBlock() {
            return uniformBlock;
        }

        public int getMemoryUsage() {
            if (uniform) {
                return 2 + 1; // short + boolean (approximate)
            } else {
                return 2 + 1 + (AREA * 2); // short + boolean + array
            }
        }
    }
}