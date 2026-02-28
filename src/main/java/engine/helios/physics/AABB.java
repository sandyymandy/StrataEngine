package engine.helios.physics;

import engine.strata.util.Vec3d;

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

    /**
     * Performs swept AABB collision detection along a single axis.
     * Returns the time of collision (0.0 to 1.0) or 1.0 if no collision.
     *
     * @param staticBox The static box to check against
     * @param velocityX Velocity in X direction
     * @param velocityY Velocity in Y direction
     * @param velocityZ Velocity in Z direction
     * @return Collision time (0.0 = immediate, 1.0 = no collision this frame)
     */
    public SweptCollisionResult sweptAABB(AABB staticBox, double velocityX, double velocityY, double velocityZ) {
        // Broadphase: check if boxes will ever collide
        AABB broadphaseBox = getBroadphaseBox(velocityX, velocityY, velocityZ);
        if (!broadphaseBox.intersects(staticBox)) {
            return new SweptCollisionResult(1.0, 0, 0, 0);
        }

        // Calculate entry and exit times for each axis
        double entryX, exitX, entryY, exitY, entryZ, exitZ;

        // X axis
        if (velocityX > 0.0) {
            entryX = staticBox.minX - this.maxX;
            exitX = staticBox.maxX - this.minX;
        } else if (velocityX < 0.0) {
            entryX = staticBox.maxX - this.minX;
            exitX = staticBox.minX - this.maxX;
        } else {
            entryX = Double.NEGATIVE_INFINITY;
            exitX = Double.POSITIVE_INFINITY;
        }

        // Y axis
        if (velocityY > 0.0) {
            entryY = staticBox.minY - this.maxY;
            exitY = staticBox.maxY - this.minY;
        } else if (velocityY < 0.0) {
            entryY = staticBox.maxY - this.minY;
            exitY = staticBox.minY - this.maxY;
        } else {
            entryY = Double.NEGATIVE_INFINITY;
            exitY = Double.POSITIVE_INFINITY;
        }

        // Z axis
        if (velocityZ > 0.0) {
            entryZ = staticBox.minZ - this.maxZ;
            exitZ = staticBox.maxZ - this.minZ;
        } else if (velocityZ < 0.0) {
            entryZ = staticBox.maxZ - this.minZ;
            exitZ = staticBox.minZ - this.maxZ;
        } else {
            entryZ = Double.NEGATIVE_INFINITY;
            exitZ = Double.POSITIVE_INFINITY;
        }

        // Convert to time
        double entryTimeX = velocityX == 0.0 ? Double.NEGATIVE_INFINITY : entryX / velocityX;
        double entryTimeY = velocityY == 0.0 ? Double.NEGATIVE_INFINITY : entryY / velocityY;
        double entryTimeZ = velocityZ == 0.0 ? Double.NEGATIVE_INFINITY : entryZ / velocityZ;

        double exitTimeX = velocityX == 0.0 ? Double.POSITIVE_INFINITY : exitX / velocityX;
        double exitTimeY = velocityY == 0.0 ? Double.POSITIVE_INFINITY : exitY / velocityY;
        double exitTimeZ = velocityZ == 0.0 ? Double.POSITIVE_INFINITY : exitZ / velocityZ;

        // Find latest entry time and earliest exit time
        double entryTime = Math.max(Math.max(entryTimeX, entryTimeY), entryTimeZ);
        double exitTime = Math.min(Math.min(exitTimeX, exitTimeY), exitTimeZ);

        // No collision if:
        // - Entry time is after exit time
        // - All axes are negative (box is moving away)
        // - Entry time is greater than 1.0 (collision happens after this frame)
        if (entryTime > exitTime ||
                (entryTimeX < 0.0 && entryTimeY < 0.0 && entryTimeZ < 0.0) ||
                entryTime > 1.0) {
            return new SweptCollisionResult(1.0, 0, 0, 0);
        }

        // Calculate collision normal (which face did we hit?)
        double normalX = 0, normalY = 0, normalZ = 0;

        if (entryTimeX > entryTimeY && entryTimeX > entryTimeZ) {
            // Hit on X axis
            normalX = velocityX > 0 ? -1 : 1;
        } else if (entryTimeY > entryTimeZ) {
            // Hit on Y axis
            normalY = velocityY > 0 ? -1 : 1;
        } else {
            // Hit on Z axis
            normalZ = velocityZ > 0 ? -1 : 1;
        }

        return new SweptCollisionResult(
                Math.max(0.0, entryTime), // Clamp to 0.0 minimum
                normalX, normalY, normalZ
        );
    }

    /**
     * Gets the broadphase bounding box that encompasses the entire swept path.
     */
    public AABB getBroadphaseBox(double velocityX, double velocityY, double velocityZ) {
        return new AABB(
                velocityX > 0 ? minX : minX + velocityX,
                velocityY > 0 ? minY : minY + velocityY,
                velocityZ > 0 ? minZ : minZ + velocityZ,
                velocityX > 0 ? maxX + velocityX : maxX,
                velocityY > 0 ? maxY + velocityY : maxY,
                velocityZ > 0 ? maxZ + velocityZ : maxZ
        );
    }

    /**
     * Result of a swept collision check.
     */
    public static class SweptCollisionResult {
        public final double time; // 0.0 to 1.0, where 1.0 means no collision
        public final double normalX, normalY, normalZ;

        public SweptCollisionResult(double time, double normalX, double normalY, double normalZ) {
            this.time = time;
            this.normalX = normalX;
            this.normalY = normalY;
            this.normalZ = normalZ;
        }

        public boolean hasCollision() {
            return time < 1.0;
        }
    }

    @Override
    public String toString() {
        return String.format("AABB[%.2f, %.2f, %.2f -> %.2f, %.2f, %.2f]",
                minX, minY, minZ, maxX, maxY, maxZ);
    }
}