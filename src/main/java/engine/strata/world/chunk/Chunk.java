package engine.strata.world.chunk;

import engine.strata.world.block.Block;
import engine.strata.world.block.Blocks;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A 16x16x16 chunk of blocks that can stack vertically.
 *
 * Coordinate system:
 * - X: West (-) to East (+)
 * - Y: Down (-) to Up (+)
 * - Z: North (-) to South (+)
 *
 * Unlike Minecraft, chunks stack vertically, allowing infinite height!
 */
public class Chunk {
    private static final Logger LOGGER = LoggerFactory.getLogger("Chunk");

    public static final int SIZE = 16; // 16x16x16 blocks
    public static final int VOLUME = SIZE * SIZE * SIZE; // 4096 blocks

    // Chunk position in chunk coordinates
    private final int chunkX;
    private final int chunkY; // Vertical chunk position!
    private final int chunkZ;

    // Block storage: [x][y][z]
    // Using short array to store block numeric IDs
    private final short[][][] blocks;

    // Light storage: [x][y][z]
    // 4 bits for sky light, 4 bits for block light
    private final byte[][][] lightData;

    // Dirty flag for rendering
    private boolean dirty = true;

    // Track if chunk has been generated
    private boolean generated = false;

    public Chunk(int chunkX, int chunkY, int chunkZ) {
        this.chunkX = chunkX;
        this.chunkY = chunkY;
        this.chunkZ = chunkZ;

        // Initialize storage
        this.blocks = new short[SIZE][SIZE][SIZE];
        this.lightData = new byte[SIZE][SIZE][SIZE];

        // Fill with air (numeric ID 0)
        fillWithAir();
    }

    /**
     * Fills the entire chunk with air blocks.
     */
    private void fillWithAir() {
        short airId = Blocks.AIR.getNumericId();
        for (int x = 0; x < SIZE; x++) {
            for (int y = 0; y < SIZE; y++) {
                for (int z = 0; z < SIZE; z++) {
                    blocks[x][y][z] = airId;
                }
            }
        }
    }

    /**
     * Gets the block at local coordinates (0-15).
     */
    public Block getBlock(int x, int y, int z) {
        if (!isValidLocalCoord(x, y, z)) {
            return Blocks.AIR;
        }

        short numericId = blocks[x][y][z];
        return Blocks.getByNumericId(numericId);
    }

    /**
     * Sets the block at local coordinates (0-15).
     */
    public void setBlock(int x, int y, int z, Block block) {
        if (!isValidLocalCoord(x, y, z)) {
            LOGGER.warn("Attempted to set block outside chunk bounds: ({}, {}, {})", x, y, z);
            return;
        }

        short numericId = block.getNumericId();
        if (numericId == -1) {
            LOGGER.error("Block {} has not been registered!", block.getId());
            return;
        }

        blocks[x][y][z] = numericId;
        dirty = true;
    }

    /**
     * Gets the sky light level (0-15) at local coordinates.
     */
    public int getSkyLight(int x, int y, int z) {
        if (!isValidLocalCoord(x, y, z)) {
            return 15; // Full light outside chunk
        }

        return (lightData[x][y][z] >> 4) & 0x0F;
    }

    /**
     * Sets the sky light level (0-15) at local coordinates.
     */
    public void setSkyLight(int x, int y, int z, int level) {
        if (!isValidLocalCoord(x, y, z)) {
            return;
        }

        level = Math.max(0, Math.min(15, level));
        lightData[x][y][z] = (byte) ((lightData[x][y][z] & 0x0F) | (level << 4));
        dirty = true;
    }

    /**
     * Gets the block light level (0-15) at local coordinates.
     */
    public int getBlockLight(int x, int y, int z) {
        if (!isValidLocalCoord(x, y, z)) {
            return 0;
        }

        return lightData[x][y][z] & 0x0F;
    }

    /**
     * Sets the block light level (0-15) at local coordinates.
     */
    public void setBlockLight(int x, int y, int z, int level) {
        if (!isValidLocalCoord(x, y, z)) {
            return;
        }

        level = Math.max(0, Math.min(15, level));
        lightData[x][y][z] = (byte) ((lightData[x][y][z] & 0xF0) | level);
        dirty = true;
    }

    /**
     * Gets the combined light level (max of sky and block light).
     */
    public int getLight(int x, int y, int z) {
        return Math.max(getSkyLight(x, y, z), getBlockLight(x, y, z));
    }

    /**
     * Checks if local coordinates are valid (0-15).
     */
    private boolean isValidLocalCoord(int x, int y, int z) {
        return x >= 0 && x < SIZE && y >= 0 && y < SIZE && z >= 0 && z < SIZE;
    }

    /**
     * Checks if the chunk has been modified and needs re-meshing.
     */
    public boolean isDirty() {
        return dirty;
    }

    /**
     * Marks the chunk as clean (after re-meshing).
     */
    public void markClean() {
        this.dirty = false;
    }

    /**
     * Marks the chunk as dirty (needs re-meshing).
     */
    public void markDirty() {
        this.dirty = true;
    }

    /**
     * Checks if the chunk has been generated.
     */
    public boolean isGenerated() {
        return generated;
    }

    /**
     * Marks the chunk as generated.
     */
    public void markGenerated() {
        this.generated = true;
    }

    // Getters for chunk position
    public int getChunkX() {
        return chunkX;
    }

    public int getChunkY() {
        return chunkY;
    }

    public int getChunkZ() {
        return chunkZ;
    }

    /**
     * Converts local coordinates to world coordinates.
     */
    public int localToWorldX(int localX) {
        return chunkX * SIZE + localX;
    }

    public int localToWorldY(int localY) {
        return chunkY * SIZE + localY;
    }

    public int localToWorldZ(int localZ) {
        return chunkZ * SIZE + localZ;
    }

    /**
     * Checks if the chunk is empty (all air).
     */
    public boolean isEmpty() {
        short airId = Blocks.AIR.getNumericId();
        for (int x = 0; x < SIZE; x++) {
            for (int y = 0; y < SIZE; y++) {
                for (int z = 0; z < SIZE; z++) {
                    if (blocks[x][y][z] != airId) {
                        return false;
                    }
                }
            }
        }
        return true;
    }

    @Override
    public String toString() {
        return "Chunk[" + chunkX + ", " + chunkY + ", " + chunkZ + "]";
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof Chunk other)) return false;
        return chunkX == other.chunkX && chunkY == other.chunkY && chunkZ == other.chunkZ;
    }

    @Override
    public int hashCode() {
        int result = chunkX;
        result = 31 * result + chunkY;
        result = 31 * result + chunkZ;
        return result;
    }
}