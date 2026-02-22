package engine.helios.physics;

import engine.strata.util.math.BlockPos;
import engine.strata.world.World;
import engine.strata.world.block.Block;
import engine.strata.world.chunk.SubChunk;
import engine.strata.registry.registries.Registries;

import java.util.ArrayList;
import java.util.List;

/**
 * Caches block collision data within a 2-chunk radius of physics objects.
 * This optimization prevents checking every block in the world for collisions.
 */
public class BlockCollisionCache {
    
    private final World world;
    private static final int COLLISION_RADIUS_CHUNKS = 2;
    private static final int COLLISION_RADIUS_BLOCKS = COLLISION_RADIUS_CHUNKS * SubChunk.SIZE;
    
    public BlockCollisionCache(World world) {
        this.world = world;
    }
    
    /**
     * Gets all block AABBs that could potentially collide with the given AABB.
     * Only checks blocks within a 2-chunk radius.
     * 
     * @param entityBox The entity's bounding box
     * @param expandBy How much to expand the search area (for velocity prediction)
     * @return List of block AABBs to check for collision
     */
    public List<AABB> getCollidingBlocks(AABB entityBox, double expandBy) {
        List<AABB> blockBoxes = new ArrayList<>();
        
        // Expand the search box slightly to account for movement
        AABB searchBox = entityBox.expand(expandBy, expandBy, expandBy);
        
        // Get the block coordinate bounds
        int minX = (int) Math.floor(searchBox.getMinX());
        int minY = (int) Math.floor(searchBox.getMinY());
        int minZ = (int) Math.floor(searchBox.getMinZ());
        int maxX = (int) Math.floor(searchBox.getMaxX() + 1);
        int maxY = (int) Math.floor(searchBox.getMaxY() + 1);
        int maxZ = (int) Math.floor(searchBox.getMaxZ() + 1);
        
        // Clamp to 2-chunk radius for performance
        int centerX = (int) Math.floor((searchBox.getMinX() + searchBox.getMaxX()) / 2.0);
        int centerY = (int) Math.floor((searchBox.getMinY() + searchBox.getMaxY()) / 2.0);
        int centerZ = (int) Math.floor((searchBox.getMinZ() + searchBox.getMaxZ()) / 2.0);
        
        minX = Math.max(minX, centerX - COLLISION_RADIUS_BLOCKS);
        minY = Math.max(minY, centerY - COLLISION_RADIUS_BLOCKS);
        minZ = Math.max(minZ, centerZ - COLLISION_RADIUS_BLOCKS);
        maxX = Math.min(maxX, centerX + COLLISION_RADIUS_BLOCKS);
        maxY = Math.min(maxY, centerY + COLLISION_RADIUS_BLOCKS);
        maxZ = Math.min(maxZ, centerZ + COLLISION_RADIUS_BLOCKS);
        
        // Check each block in the region
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                for (int y = minY; y <= maxY; y++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    short blockId = world.getBlockId(pos);
                    
                    // Skip air blocks
                    if (blockId == 0) {
                        continue;
                    }
                    
                    // Get block from registry
                    Block block = Registries.BLOCK_BY_ID.get(blockId);
                    if (block == null) {
                        continue;
                    }
                    
                    // Check if block is solid/collidable
                    // You may want to add a method to your Block class for this
                    // For now, we assume all non-air blocks are solid
                    
                    // Create AABB for this block
                    AABB blockBox = AABB.forBlock(x, y, z);
                    
                    // Only add if it intersects with the search box
                    if (blockBox.intersects(searchBox)) {
                        blockBoxes.add(blockBox);
                    }
                }
            }
        }
        
        return blockBoxes;
    }
    
    /**
     * Checks if a specific position has a solid block.
     */
    public boolean isSolid(int x, int y, int z) {
        BlockPos pos = new BlockPos(x, y, z);
        short blockId = world.getBlockId(pos);
        return blockId != 0; // 0 = air
    }
    
    /**
     * Checks if a block position is within the collision radius of a point.
     */
    public boolean isWithinRadius(BlockPos blockPos, double centerX, double centerY, double centerZ) {
        double dx = blockPos.getX() - centerX;
        double dy = blockPos.getY() - centerY;
        double dz = blockPos.getZ() - centerZ;
        double distSquared = dx * dx + dy * dy + dz * dz;
        double radiusSquared = COLLISION_RADIUS_BLOCKS * COLLISION_RADIUS_BLOCKS;
        return distSquared <= radiusSquared;
    }
    
    /**
     * Gets the collision radius in blocks.
     */
    public static int getCollisionRadiusBlocks() {
        return COLLISION_RADIUS_BLOCKS;
    }
    
    /**
     * Gets the collision radius in chunks.
     */
    public static int getCollisionRadiusChunks() {
        return COLLISION_RADIUS_CHUNKS;
    }
}
