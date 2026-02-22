package engine.helios.physics;

import engine.strata.util.math.Vec3d;

/**
 * Axis-Aligned Bounding Box for collision detection.
 * Represents a rectangular volume aligned with the world axes.
 */
public class AABB {
    
    // Box dimensions (relative to center/position)
    private double minX, minY, minZ;
    private double maxX, maxY, maxZ;
    
    /**
     * Creates an AABB with specified dimensions relative to origin.
     * @param minX Minimum X offset
     * @param minY Minimum Y offset
     * @param minZ Minimum Z offset
     * @param maxX Maximum X offset
     * @param maxY Maximum Y offset
     * @param maxZ Maximum Z offset
     */
    public AABB(double minX, double minY, double minZ, 
                double maxX, double maxY, double maxZ) {
        this.minX = minX;
        this.minY = minY;
        this.minZ = minZ;
        this.maxX = maxX;
        this.maxY = maxY;
        this.maxZ = maxZ;
    }
    
    /**
     * Creates an AABB centered at origin with given dimensions.
     */
    public static AABB fromDimensions(double width, double height, double depth) {
        double halfWidth = width / 2.0;
        double halfDepth = depth / 2.0;
        return new AABB(-halfWidth, 0, -halfDepth, halfWidth, height, halfDepth);
    }
    
    /**
     * Creates an AABB for a block at the given position.
     */
    public static AABB forBlock(int blockX, int blockY, int blockZ) {
        return new AABB(blockX, blockY, blockZ, blockX + 1, blockY + 1, blockZ + 1);
    }
    
    /**
     * Returns a new AABB offset by the given position.
     */
    public AABB offset(double x, double y, double z) {
        return new AABB(
            minX + x, minY + y, minZ + z,
            maxX + x, maxY + y, maxZ + z
        );
    }
    
    /**
     * Returns a new AABB offset by a Vec3d.
     */
    public AABB offset(Vec3d pos) {
        return offset(pos.getX(), pos.getY(), pos.getZ());
    }
    
    /**
     * Expands the AABB by the given amount in all directions.
     */
    public AABB expand(double x, double y, double z) {
        return new AABB(
            minX - x, minY - y, minZ - z,
            maxX + x, maxY + y, maxZ + z
        );
    }
    
    /**
     * Contracts the AABB by the given amount in all directions.
     */
    public AABB contract(double x, double y, double z) {
        return expand(-x, -y, -z);
    }
    
    /**
     * Checks if this AABB intersects with another AABB.
     */
    public boolean intersects(AABB other) {
        return this.maxX > other.minX && this.minX < other.maxX &&
               this.maxY > other.minY && this.minY < other.maxY &&
               this.maxZ > other.minZ && this.minZ < other.maxZ;
    }
    
    /**
     * Checks if this AABB contains a point.
     */
    public boolean contains(double x, double y, double z) {
        return x >= minX && x <= maxX &&
               y >= minY && y <= maxY &&
               z >= minZ && z <= maxZ;
    }
    
    /**
     * Checks if this AABB contains a Vec3d point.
     */
    public boolean contains(Vec3d point) {
        return contains(point.getX(), point.getY(), point.getZ());
    }
    
    /**
     * Calculates the clipping factor for X axis movement.
     * Returns the adjusted movement to prevent collision.
     */
    public double calculateXOffset(AABB other, double offsetX) {
        if (other.maxY > this.minY && other.minY < this.maxY &&
            other.maxZ > this.minZ && other.minZ < this.maxZ) {
            
            if (offsetX > 0.0 && other.maxX <= this.minX) {
                double maxOffset = this.minX - other.maxX;
                if (maxOffset < offsetX) {
                    offsetX = maxOffset;
                }
            } else if (offsetX < 0.0 && other.minX >= this.maxX) {
                double maxOffset = this.maxX - other.minX;
                if (maxOffset > offsetX) {
                    offsetX = maxOffset;
                }
            }
        }
        return offsetX;
    }
    
    /**
     * Calculates the clipping factor for Y axis movement.
     */
    public double calculateYOffset(AABB other, double offsetY) {
        if (other.maxX > this.minX && other.minX < this.maxX &&
            other.maxZ > this.minZ && other.minZ < this.maxZ) {
            
            if (offsetY > 0.0 && other.maxY <= this.minY) {
                double maxOffset = this.minY - other.maxY;
                if (maxOffset < offsetY) {
                    offsetY = maxOffset;
                }
            } else if (offsetY < 0.0 && other.minY >= this.maxY) {
                double maxOffset = this.maxY - other.minY;
                if (maxOffset > offsetY) {
                    offsetY = maxOffset;
                }
            }
        }
        return offsetY;
    }
    
    /**
     * Calculates the clipping factor for Z axis movement.
     */
    public double calculateZOffset(AABB other, double offsetZ) {
        if (other.maxX > this.minX && other.minX < this.maxX &&
            other.maxY > this.minY && other.minY < this.maxY) {
            
            if (offsetZ > 0.0 && other.maxZ <= this.minZ) {
                double maxOffset = this.minZ - other.maxZ;
                if (maxOffset < offsetZ) {
                    offsetZ = maxOffset;
                }
            } else if (offsetZ < 0.0 && other.minZ >= this.maxZ) {
                double maxOffset = this.maxZ - other.minZ;
                if (maxOffset > offsetZ) {
                    offsetZ = maxOffset;
                }
            }
        }
        return offsetZ;
    }
    
    /**
     * Gets the center point of this AABB.
     */
    public Vec3d getCenter() {
        return new Vec3d(
            (minX + maxX) / 2.0,
            (minY + maxY) / 2.0,
            (minZ + maxZ) / 2.0
        );
    }
    
    /**
     * Gets the width (X dimension) of this AABB.
     */
    public double getWidth() {
        return maxX - minX;
    }
    
    /**
     * Gets the height (Y dimension) of this AABB.
     */
    public double getHeight() {
        return maxY - minY;
    }
    
    /**
     * Gets the depth (Z dimension) of this AABB.
     */
    public double getDepth() {
        return maxZ - minZ;
    }
    
    /**
     * Creates a copy of this AABB.
     */
    public AABB copy() {
        return new AABB(minX, minY, minZ, maxX, maxY, maxZ);
    }
    
    // Getters
    public double getMinX() { return minX; }
    public double getMinY() { return minY; }
    public double getMinZ() { return minZ; }
    public double getMaxX() { return maxX; }
    public double getMaxY() { return maxY; }
    public double getMaxZ() { return maxZ; }
    
    @Override
    public String toString() {
        return String.format("AABB[%.2f, %.2f, %.2f -> %.2f, %.2f, %.2f]",
            minX, minY, minZ, maxX, maxY, maxZ);
    }
}
