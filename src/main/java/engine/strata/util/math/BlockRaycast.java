package engine.strata.util.math;

import engine.strata.world.World;

/**
 * Utility for raycasting to find block positions.
 * Useful for block placement/breaking based on where the player is looking.
 */
public class BlockRaycast {

    /**
     * Result of a block raycast.
     */
    public static class RaycastResult {
        private final BlockPos blockPos;
        private final BlockPos adjacentPos;
        private final BlockPos.Direction hitFace;
        private final Vec3d hitPosition;
        private final double distance;
        private final boolean hit;

        public RaycastResult(BlockPos blockPos, BlockPos adjacentPos,
                             BlockPos.Direction hitFace, Vec3d hitPosition, double distance) {
            this.blockPos = blockPos;
            this.adjacentPos = adjacentPos;
            this.hitFace = hitFace;
            this.hitPosition = hitPosition;
            this.distance = distance;
            this.hit = true;
        }

        private RaycastResult() {
            this.blockPos = null;
            this.adjacentPos = null;
            this.hitFace = null;
            this.hitPosition = null;
            this.distance = 0;
            this.hit = false;
        }

        public static RaycastResult miss() {
            return new RaycastResult();
        }

        public boolean isHit() {
            return hit;
        }

        /**
         * Gets the block that was hit.
         */
        public BlockPos getBlockPos() {
            return blockPos;
        }

        /**
         * Gets the adjacent block position (useful for placement).
         */
        public BlockPos getAdjacentPos() {
            return adjacentPos;
        }

        /**
         * Gets the face that was hit.
         */
        public BlockPos.Direction getHitFace() {
            return hitFace;
        }

        /**
         * Gets the exact hit position in world coordinates.
         */
        public Vec3d getHitPosition() {
            return hitPosition;
        }

        /**
         * Gets the distance to the hit.
         */
        public double getDistance() {
            return distance;
        }
    }

    /**
     * Performs a raycast from a position in a direction to find blocks.
     *
     * @param world The world to raycast in
     * @param origin Starting position
     * @param direction Direction to cast (should be normalized)
     * @param maxDistance Maximum distance to check
     * @return RaycastResult containing hit information
     */
    public static RaycastResult raycast(World world, Vec3d origin, Vec3d direction, double maxDistance) {
        // Normalize direction
        Vec3d dir = direction.normalize();

        // DDA algorithm for voxel traversal
        double x = origin.getX();
        double y = origin.getY();
        double z = origin.getZ();

        double dx = dir.getX();
        double dy = dir.getY();
        double dz = dir.getZ();

        // Calculate step direction
        int stepX = dx > 0 ? 1 : -1;
        int stepY = dy > 0 ? 1 : -1;
        int stepZ = dz > 0 ? 1 : -1;

        // Calculate tMax (distance to next voxel boundary)
        double tMaxX = intbound(x, dx);
        double tMaxY = intbound(y, dy);
        double tMaxZ = intbound(z, dz);

        // Calculate tDelta (distance between voxel boundaries)
        double tDeltaX = stepX / dx;
        double tDeltaY = stepY / dy;
        double tDeltaZ = stepZ / dz;

        // Current block position
        int blockX = (int) Math.floor(x);
        int blockY = (int) Math.floor(y);
        int blockZ = (int) Math.floor(z);

        // Face we're entering from
        BlockPos.Direction face = null;

        // Traverse until we hit a block or reach max distance
        double distance = 0;

        while (distance < maxDistance) {
            // Check current block
            BlockPos currentPos = new BlockPos(blockX, blockY, blockZ);
            short block = world.getBlock(currentPos);

            if (block != 0) { // Hit a non-air block
                // Calculate exact hit position
                Vec3d hitPos = origin.add(
                        dir.getX() * distance,
                        dir.getY() * distance,
                        dir.getZ() * distance
                );

                // Calculate adjacent position for placement
                BlockPos adjacentPos = face != null ? currentPos.offset(face) : currentPos;

                return new RaycastResult(currentPos, adjacentPos, face, hitPos, distance);
            }

            // Move to next voxel
            if (tMaxX < tMaxY) {
                if (tMaxX < tMaxZ) {
                    blockX += stepX;
                    distance = tMaxX;
                    tMaxX += tDeltaX;
                    face = stepX > 0 ? BlockPos.Direction.WEST : BlockPos.Direction.EAST;
                } else {
                    blockZ += stepZ;
                    distance = tMaxZ;
                    tMaxZ += tDeltaZ;
                    face = stepZ > 0 ? BlockPos.Direction.NORTH : BlockPos.Direction.SOUTH;
                }
            } else {
                if (tMaxY < tMaxZ) {
                    blockY += stepY;
                    distance = tMaxY;
                    tMaxY += tDeltaY;
                    face = stepY > 0 ? BlockPos.Direction.DOWN : BlockPos.Direction.UP;
                } else {
                    blockZ += stepZ;
                    distance = tMaxZ;
                    tMaxZ += tDeltaZ;
                    face = stepZ > 0 ? BlockPos.Direction.NORTH : BlockPos.Direction.SOUTH;
                }
            }
        }

        return RaycastResult.miss();
    }

    /**
     * Helper function for DDA algorithm.
     * Finds distance to next voxel boundary.
     */
    private static double intbound(double s, double ds) {
        if (ds < 0) {
            return intbound(-s, -ds);
        } else {
            s = mod(s, 1);
            return (1 - s) / ds;
        }
    }

    /**
     * Proper modulo that handles negatives correctly.
     */
    private static double mod(double value, double modulus) {
        return (value % modulus + modulus) % modulus;
    }

    /**
     * Convenience method to raycast from an entity's eye position and look direction.
     */
    public static RaycastResult raycastFromEntity(World world,
                                                  engine.strata.entity.Entity entity,
                                                  double maxDistance) {
        Vec3d eyePos = entity.getPosition().add(0, entity.getEyeHeight(), 0);

        float yaw = entity.getHeadYaw();
        float pitch = entity.getHeadPitch();

        double yawRad = Math.toRadians(yaw);
        double pitchRad = Math.toRadians(pitch);

        // Fixed direction calculation
        double x = Math.sin(yawRad) * Math.cos(pitchRad);
        double y = -Math.sin(pitchRad);
        double z = -Math.cos(yawRad) * Math.cos(pitchRad);

        Vec3d direction = new Vec3d(x, y, z);

        return raycast(world, eyePos, direction, maxDistance);
    }
}