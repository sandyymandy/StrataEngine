package engine.helios.physics;

import engine.strata.util.Vec3d;
import engine.strata.util.Vec3f;

/**
 * Axis-Aligned Bounding Box for collision detection.
 * Represents a rectangular volume aligned with the world axes.
 */
public class AABB {

    // Box dimensions (relative to center/position)
    private final Vec3d min;
    private final Vec3d max;

    /**
     * Creates an AABB with specified min and max corners.
     */
    public AABB(Vec3d min, Vec3d max) {
        this.min = min;
        this.max = max;
    }

    /**
     * Creates an AABB with specified dimensions.
     * @param minX Minimum X offset
     * @param minY Minimum Y offset
     * @param minZ Minimum Z offset
     * @param maxX Maximum X offset
     * @param maxY Maximum Y offset
     * @param maxZ Maximum Z offset
     */
    public AABB(double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
        this.min = new Vec3d(minX, minY, minZ);
        this.max = new Vec3d(maxX, maxY, maxZ);
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
                min.add(x, y, z),
                max.add(x, y, z)
        );
    }

    /**
     * Returns a new AABB offset by a Vec3d.
     */
    public AABB offset(Vec3d pos) {
        return new AABB(
                min.add(pos),
                max.add(pos)
        );
    }

    /**
     * Expands the AABB by the given amount in all directions.
     */
    public AABB expand(double x, double y, double z) {
        return new AABB(
                min.subtract(x, y, z),
                max.add(x, y, z)
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
        return this.max.getX() > other.min.getX() && this.min.getX() < other.max.getX() &&
                this.max.getY() > other.min.getY() && this.min.getY() < other.max.getY() &&
                this.max.getZ() > other.min.getZ() && this.min.getZ() < other.max.getZ();
    }

    /**
     * Checks if this AABB contains a point.
     */
    public boolean contains(double x, double y, double z) {
        return x >= min.getX() && x <= max.getX() &&
                y >= min.getY() && y <= max.getY() &&
                z >= min.getZ() && z <= max.getZ();
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
        if (other.max.getY() > this.min.getY() && other.min.getY() < this.max.getY() &&
                other.max.getZ() > this.min.getZ() && other.min.getZ() < this.max.getZ()) {

            if (offsetX > 0.0 && other.max.getX() <= this.min.getX()) {
                double maxOffset = this.min.getX() - other.max.getX();
                if (maxOffset < offsetX) {
                    offsetX = maxOffset;
                }
            } else if (offsetX < 0.0 && other.min.getX() >= this.max.getX()) {
                double maxOffset = this.max.getX() - other.min.getX();
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
        if (other.max.getX() > this.min.getX() && other.min.getX() < this.max.getX() &&
                other.max.getZ() > this.min.getZ() && other.min.getZ() < this.max.getZ()) {

            if (offsetY > 0.0 && other.max.getY() <= this.min.getY()) {
                double maxOffset = this.min.getY() - other.max.getY();
                if (maxOffset < offsetY) {
                    offsetY = maxOffset;
                }
            } else if (offsetY < 0.0 && other.min.getY() >= this.max.getY()) {
                double maxOffset = this.max.getY() - other.min.getY();
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
        if (other.max.getX() > this.min.getX() && other.min.getX() < this.max.getX() &&
                other.max.getY() > this.min.getY() && other.min.getY() < this.max.getY()) {

            if (offsetZ > 0.0 && other.max.getZ() <= this.min.getZ()) {
                double maxOffset = this.min.getZ() - other.max.getZ();
                if (maxOffset < offsetZ) {
                    offsetZ = maxOffset;
                }
            } else if (offsetZ < 0.0 && other.min.getZ() >= this.max.getZ()) {
                double maxOffset = this.max.getZ() - other.min.getZ();
                if (maxOffset > offsetZ) {
                    offsetZ = maxOffset;
                }
            }
        }
        return offsetZ;
    }

    /**
     * Checks if this AABB is within a certain distance from a point.
     * Useful for distance-based culling before performing expensive frustum checks.
     * @param pos The position to check distance from (e.g., camera position)
     * @param maxDistance The culling distance
     * @return true if the AABB is within range, false if it should be culled
     */
    public boolean isWithinDistance(Vec3f pos, double maxDistance) {
        Vec3d center = getCenter();
        double radius = getRadius();

        double dx = center.getX() - pos.getX();
        double dy = center.getY() - pos.getY();
        double dz = center.getZ() - pos.getZ();
        double distSq = dx * dx + dy * dy + dz * dz;

        // Add radius to maxDistance so the box doesn't pop out
        // until the entire volume is out of range
        double limit = maxDistance + radius;
        return distSq <= limit * limit;
    }

    /**
     * Gets the center point of this AABB.
     */
    public Vec3d getCenter() {
        return new Vec3d(
                (min.getX() + max.getX()) / 2.0,
                (min.getY() + max.getY()) / 2.0,
                (min.getZ() + max.getZ()) / 2.0
        );
    }

    /**
     * Gets the approximate radius of the AABB (distance from center to a corner).
     */
    public double getRadius() {
        double dx = getWidth() / 2.0;
        double dy = getHeight() / 2.0;
        double dz = getDepth() / 2.0;
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    /**
     * Gets the width (X dimension) of this AABB.
     */
    public double getWidth() {
        return max.getX() - min.getX();
    }

    /**
     * Gets the height (Y dimension) of this AABB.
     */
    public double getHeight() {
        return max.getY() - min.getY();
    }

    /**
     * Gets the depth (Z dimension) of this AABB.
     */
    public double getDepth() {
        return max.getZ() - min.getZ();
    }

    /**
     * Creates a copy of this AABB.
     */
    public AABB copy() {
        return new AABB(new Vec3d(min.getX(), min.getY(), min.getZ()),
                new Vec3d(max.getX(), max.getY(), max.getZ()));
    }

    // Getters
    public double getMinX() { return min.getX(); }
    public double getMinY() { return min.getY(); }
    public double getMinZ() { return min.getZ(); }
    public double getMaxX() { return max.getX(); }
    public double getMaxY() { return max.getY(); }
    public double getMaxZ() { return max.getZ(); }

    public Vec3d getMin() { return min; }
    public Vec3d getMax() { return max; }

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
            entryX = staticBox.min.getX() - this.max.getX();
            exitX = staticBox.max.getX() - this.min.getX();
        } else if (velocityX < 0.0) {
            entryX = staticBox.max.getX() - this.min.getX();
            exitX = staticBox.min.getX() - this.max.getX();
        } else {
            entryX = Double.NEGATIVE_INFINITY;
            exitX = Double.POSITIVE_INFINITY;
        }

        // Y axis
        if (velocityY > 0.0) {
            entryY = staticBox.min.getY() - this.max.getY();
            exitY = staticBox.max.getY() - this.min.getY();
        } else if (velocityY < 0.0) {
            entryY = staticBox.max.getY() - this.min.getY();
            exitY = staticBox.min.getY() - this.max.getY();
        } else {
            entryY = Double.NEGATIVE_INFINITY;
            exitY = Double.POSITIVE_INFINITY;
        }

        // Z axis
        if (velocityZ > 0.0) {
            entryZ = staticBox.min.getZ() - this.max.getZ();
            exitZ = staticBox.max.getZ() - this.min.getZ();
        } else if (velocityZ < 0.0) {
            entryZ = staticBox.max.getZ() - this.min.getZ();
            exitZ = staticBox.min.getZ() - this.max.getZ();
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
                velocityX > 0 ? min.getX() : min.getX() + velocityX,
                velocityY > 0 ? min.getY() : min.getY() + velocityY,
                velocityZ > 0 ? min.getZ() : min.getZ() + velocityZ,
                velocityX > 0 ? max.getX() + velocityX : max.getX(),
                velocityY > 0 ? max.getY() + velocityY : max.getY(),
                velocityZ > 0 ? max.getZ() + velocityZ : max.getZ()
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
                min.getX(), min.getY(), min.getZ(), max.getX(), max.getY(), max.getZ());
    }
}