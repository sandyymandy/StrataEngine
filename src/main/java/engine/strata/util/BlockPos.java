package engine.strata.util;

import engine.strata.util.math.Math;
import engine.strata.world.chunk.SubChunk;

/**
 * Represents a block position in world space.
 * Handles proper coordinate conversion from floating-point entity positions
 * to integer block positions, including correct handling of negative coordinates.
 */
public class BlockPos {
    private final int x;
    private final int y;
    private final int z;

    public BlockPos(int x, int y, int z) {
        this.x = x;
        this.y = y;
        this.z = z;
    }

    /**
     * Creates a BlockPos from world coordinates (doubles).
     * Uses floor operation to handle negative coordinates correctly.
     */
    public BlockPos(double x, double y, double z) {
        this.x = (int) engine.strata.util.math.Math.floor(x);
        this.y = (int) engine.strata.util.math.Math.floor(y);
        this.z = (int) engine.strata.util.math.Math.floor(z);
    }

    /**
     * Creates a BlockPos from a Vec3d.
     */
    public BlockPos(Vec3d vec) {
        this(vec.getX(), vec.getY(), vec.getZ());
    }

    /**
     * Creates a BlockPos from a Vec3f.
     */
    public BlockPos(Vec3f vec) {
        this(vec.getX(), vec.getY(), vec.getZ());
    }

    // ==================== Offset Methods ====================

    /**
     * Returns a new BlockPos offset by the given amounts.
     */
    public BlockPos offset(int dx, int dy, int dz) {
        return new BlockPos(x + dx, y + dy, z + dz);
    }

    /**
     * Returns a new BlockPos offset in a direction.
     */
    public BlockPos offset(Direction direction) {
        return offset(direction, 1);
    }

    /**
     * Returns a new BlockPos offset in a direction by distance.
     */
    public BlockPos offset(Direction direction, int distance) {
        return offset(
                direction.getOffsetX() * distance,
                direction.getOffsetY() * distance,
                direction.getOffsetZ() * distance
        );
    }

    /**
     * Returns a new BlockPos above this one.
     */
    public BlockPos up() {
        return offset(0, 1, 0);
    }

    /**
     * Returns a new BlockPos below this one.
     */
    public BlockPos down() {
        return offset(0, -1, 0);
    }

    /**
     * Returns a new BlockPos north of this one.
     */
    public BlockPos north() {
        return offset(0, 0, -1);
    }

    /**
     * Returns a new BlockPos south of this one.
     */
    public BlockPos south() {
        return offset(0, 0, 1);
    }

    /**
     * Returns a new BlockPos east of this one.
     */
    public BlockPos east() {
        return offset(1, 0, 0);
    }

    /**
     * Returns a new BlockPos west of this one.
     */
    public BlockPos west() {
        return offset(-1, 0, 0);
    }

    // ==================== Chunk Coordinate Conversion ====================

    /**
     * Gets the chunk X coordinate this block is in.
     */
    public int getChunkX() {
        return engine.strata.util.math.Math.floorDiv(x, SubChunk.SIZE);
    }

    /**
     * Gets the chunk Z coordinate this block is in.
     */
    public int getChunkZ() {
        return engine.strata.util.math.Math.floorDiv(z, SubChunk.SIZE);
    }

    /**
     * Gets the subchunk Y level this block is in.
     */
    public int getSubChunkY() {
        return engine.strata.util.math.Math.floorDiv(y, SubChunk.SIZE);
    }

    /**
     * Gets the local X coordinate within the chunk (0-31).
     */
    public int getLocalX() {
        return engine.strata.util.math.Math.floorMod(x, SubChunk.SIZE);
    }

    /**
     * Gets the local Y coordinate within the subchunk (0-31).
     */
    public int getLocalY() {
        return engine.strata.util.math.Math.floorMod(y, SubChunk.SIZE);
    }

    /**
     * Gets the local Z coordinate within the chunk (0-31).
     */
    public int getLocalZ() {
        return engine.strata.util.math.Math.floorMod(z, SubChunk.SIZE);
    }

    // ==================== Distance Calculations ====================

    /**
     * Gets the squared distance to another BlockPos.
     */
    public double getSquaredDistance(BlockPos other) {
        double dx = this.x - other.x;
        double dy = this.y - other.y;
        double dz = this.z - other.z;
        return dx * dx + dy * dy + dz * dz;
    }

    /**
     * Gets the distance to another BlockPos.
     */
    public double getDistance(BlockPos other) {
        return engine.strata.util.math.Math.sqrt(getSquaredDistance(other));
    }

    /**
     * Gets the squared distance to world coordinates.
     */
    public double getSquaredDistance(double x, double y, double z) {
        double dx = this.x - x;
        double dy = this.y - y;
        double dz = this.z - z;
        return dx * dx + dy * dy + dz * dz;
    }

    /**
     * Gets the distance to world coordinates.
     */
    public double getDistance(double x, double y, double z) {
        return engine.strata.util.math.Math.sqrt(getSquaredDistance(x, y, z));
    }

    /**
     * Gets the Manhattan distance to another BlockPos.
     */
    public int getManhattanDistance(BlockPos other) {
        return engine.strata.util.math.Math.abs(this.x - other.x) +
                engine.strata.util.math.Math.abs(this.y - other.y) +
                engine.strata.util.math.Math.abs(this.z - other.z);
    }

    // ==================== Conversion Methods ====================

    /**
     * Converts to a Vec3d at the center of the block.
     */
    public Vec3d toCenterVec3d() {
        return new Vec3d(x + 0.5, y + 0.5, z + 0.5);
    }

    /**
     * Converts to a Vec3d at the corner of the block.
     */
    public Vec3d toVec3d() {
        return new Vec3d(x, y, z);
    }

    /**
     * Converts to a Vec3f at the center of the block.
     */
    public Vec3f toCenterVec3f() {
        return new Vec3f(x + 0.5f, y + 0.5f, z + 0.5f);
    }

    /**
     * Converts to a Vec3f at the corner of the block.
     */
    public Vec3f toVec3f() {
        return new Vec3f(x, y, z);
    }

    // ==================== Utility Methods ====================

    /**
     * Checks if this BlockPos is within a cubic region.
     */
    public boolean isWithinDistance(BlockPos center, int distance) {
        return engine.strata.util.math.Math.abs(x - center.x) <= distance &&
                engine.strata.util.math.Math.abs(y - center.y) <= distance &&
                Math.abs(z - center.z) <= distance;
    }

    /**
     * Returns an immutable copy of this BlockPos.
     */
    public BlockPos toImmutable() {
        return this;
    }

    // ==================== Getters ====================

    public int getX() {
        return x;
    }

    public int getY() {
        return y;
    }

    public int getZ() {
        return z;
    }

    // ==================== Object Methods ====================

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof BlockPos)) return false;
        BlockPos other = (BlockPos) obj;
        return x == other.x && y == other.y && z == other.z;
    }

    @Override
    public int hashCode() {
        int result = x;
        result = 31 * result + y;
        result = 31 * result + z;
        return result;
    }

    @Override
    public String toString() {
        return String.format("BlockPos[%d, %d, %d]", x, y, z);
    }

    // ==================== Direction Enum ====================

    public enum Direction {
        DOWN(0, -1, 0),
        UP(0, 1, 0),
        NORTH(0, 0, -1),
        SOUTH(0, 0, 1),
        WEST(-1, 0, 0),
        EAST(1, 0, 0);

        private final int offsetX;
        private final int offsetY;
        private final int offsetZ;

        Direction(int offsetX, int offsetY, int offsetZ) {
            this.offsetX = offsetX;
            this.offsetY = offsetY;
            this.offsetZ = offsetZ;
        }

        public int getOffsetX() {
            return offsetX;
        }

        public int getOffsetY() {
            return offsetY;
        }

        public int getOffsetZ() {
            return offsetZ;
        }

        public Direction getOpposite() {
            switch (this) {
                case DOWN: return UP;
                case UP: return DOWN;
                case NORTH: return SOUTH;
                case SOUTH: return NORTH;
                case WEST: return EAST;
                case EAST: return WEST;
                default: return this;
            }
        }
    }
}