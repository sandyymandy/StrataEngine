package engine.helios.physics;

import engine.strata.util.BlockPos;
import engine.strata.util.Vec3d;
import engine.strata.world.World;
import engine.strata.world.block.Block;
import engine.strata.world.block.Blocks;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

import static engine.strata.core.StrataCore.TICK_DELTA;

/**
 * Manages physics simulation for all rigid bodies in the world.
 * Handles collision detection with blocks and collision response.
 *
 * Features:
 * - Block collision only within 2-chunk radius of entities
 * - Swept AABB collision detection to prevent tunneling at high speeds
 * - Iterative collision resolution with up to 4 iterations per frame
 * - Entity-entity collision with depenetration (prevents entities getting stuck inside each other)
 * - Impulse-based collision response for realistic momentum transfer
 * - Mass-based separation (heavier entities push lighter ones more)
 * - Prevents entities from being pushed into blocks during separation
 * - Handles friction, restitution, and ground detection
 * - Pauses physics for entities in unloaded chunks
 */
public class PhysicsManager {
    private static final Logger LOGGER = LoggerFactory.getLogger("Physics");

    private final World world;
    private final BlockCollisionCache blockCollisionCache;

    // Physics settings
    private static final double COLLISION_EPSILON = 0.001; // Small value to prevent clipping
    private static final int MAX_COLLISION_ITERATIONS = 4; // Maximum collision resolution attempts
    private static final int ENTITY_COLLISION_ITERATIONS = 3; // Number of entity depenetration iterations
    private static final double ENTITY_SEPARATION_STRENGTH = 0.1; // How strongly entities push each other (0.0 - 1.0)

    // List of all physics objects
    private final List<RigidBody> rigidBodies = new ArrayList<>();

    // Options
    public static final double GRAVITY = -44;

    // Statistics
    private int entitiesWithPhysics = 0;
    private int entitiesPaused = 0;

    public PhysicsManager(World world) {
        this.world = world;
        this.blockCollisionCache = new BlockCollisionCache(world);
        LOGGER.info("PhysicsManager initialized");
    }

    /**
     * Registers a rigid body with the physics system.
     */
    public void registerRigidBody(RigidBody body) {
        if (!rigidBodies.contains(body)) {
            rigidBodies.add(body);
        }
    }

    /**
     * Unregisters a rigid body from the physics system.
     */
    public void unregisterRigidBody(RigidBody body) {
        rigidBodies.remove(body);
    }

    /**
     * Updates all physics objects.
     * Should be called once per tick (20 times per second).
     */
    public void tick() {
        entitiesWithPhysics = 0;
        entitiesPaused = 0;

        // 1. Resolve Block Collisions & Gravity
        for (RigidBody body : rigidBodies) {
            if (!isChunkLoadedForEntity(body)) {
                pausePhysicsForBody(body);
                entitiesPaused++;
                continue;
            }
            entitiesWithPhysics++;
            if (body.isCollidable() && !body.isNoClip()) {
                moveWithCollision(body);
            }
        }

        // 2. Resolve Entity vs. Entity Collisions
        // Use multiple iterations to handle overlapping entities
        for (int iteration = 0; iteration < ENTITY_COLLISION_ITERATIONS; iteration++) {
            for (int i = 0; i < rigidBodies.size(); i++) {
                for (int j = i + 1; j < rigidBodies.size(); j++) {
                    RigidBody a = rigidBodies.get(i);
                    RigidBody b = rigidBodies.get(j);

                    if (!a.isCollidable() || !b.isCollidable()) {
                        continue;
                    }

                    AABB boxA = a.getBoundingBox();
                    AABB boxB = b.getBoundingBox();

                    if (boxA.intersects(boxB)) {
                        // Entities are overlapping - depenetrate them
                        depenetrateEntities(a, b);
                        // Then resolve velocity collision
                        resolveEntityCollision(a, b);
                    }
                }
            }
        }
    }

    /**
     * Checks if the chunk containing the entity is loaded.
     */
    private boolean isChunkLoadedForEntity(RigidBody body) {
        Vec3d pos = body.getPosition();
        return world.getChunkManager().isChunkLoadedAtWorldPos(pos.getX(), pos.getZ());
    }

    /**
     * Pauses physics for an entity by zeroing out its velocity.
     * This prevents entities from falling into the void when their chunk isn't loaded.
     */
    private void pausePhysicsForBody(RigidBody body) {
        // Zero out velocity to prevent further movement
        body.setVelocity(0, 0, 0);

        // Mark as not on ground since we can't check collision
        body.setOnGround(false);
    }

    /**
     * Moves a rigid body while checking for collisions with blocks using swept AABB.
     * This prevents tunneling through blocks at high speeds or certain angles.
     */
    private void moveWithCollision(RigidBody body) {
        Vec3d velocity = body.getVelocity();

        // Calculate movement delta for this tick
        double deltaX = velocity.getX() * TICK_DELTA;
        double deltaY = velocity.getY() * TICK_DELTA;
        double deltaZ = velocity.getZ() * TICK_DELTA;

        // If not moving, skip collision check
        if (Math.abs(deltaX) < COLLISION_EPSILON &&
                Math.abs(deltaY) < COLLISION_EPSILON &&
                Math.abs(deltaZ) < COLLISION_EPSILON) {
            body.setOnGround(checkGroundCollision(body));
            return;
        }

        // Get current bounding box
        AABB entityBox = body.getBoundingBox();

        // Get all potentially colliding blocks within 2-chunk radius
        double maxMovement = Math.max(Math.abs(deltaX), Math.max(Math.abs(deltaY), Math.abs(deltaZ)));
        List<AABB> blockBoxes = blockCollisionCache.getCollidingBlocks(entityBox, maxMovement);

        // Track actual movement applied
        double actualDeltaX = deltaX;
        double actualDeltaY = deltaY;
        double actualDeltaZ = deltaZ;

        // Use swept AABB collision detection to prevent tunneling
        double remainingTime = 1.0;
        int iterations = 0;

        // Track which axes have collided
        boolean collidedX = false;
        boolean collidedY = false;
        boolean collidedZ = false;

        while (remainingTime > COLLISION_EPSILON && iterations < MAX_COLLISION_ITERATIONS) {
            iterations++;

            // Find the nearest collision along the remaining path
            AABB.SweptCollisionResult nearestCollision = null;
            double nearestTime = 1.0;

            for (AABB blockBox : blockBoxes) {
                // Calculate velocity for this iteration
                double currentVelX = actualDeltaX * remainingTime;
                double currentVelY = actualDeltaY * remainingTime;
                double currentVelZ = actualDeltaZ * remainingTime;

                AABB.SweptCollisionResult result = entityBox.sweptAABB(
                        blockBox,
                        currentVelX,
                        currentVelY,
                        currentVelZ
                );

                if (result.hasCollision() && result.time < nearestTime) {
                    nearestTime = result.time;
                    nearestCollision = result;
                }
            }

            if (nearestCollision != null && nearestCollision.hasCollision()) {
                // Move to collision point (with small epsilon to prevent overlap)
                double moveTime = Math.max(0, nearestTime - COLLISION_EPSILON);

                double moveX = actualDeltaX * remainingTime * moveTime;
                double moveY = actualDeltaY * remainingTime * moveTime;
                double moveZ = actualDeltaZ * remainingTime * moveTime;

                entityBox = entityBox.offset(moveX, moveY, moveZ);

                // Update remaining deltas based on collision normal
                if (Math.abs(nearestCollision.normalX) > 0.5) {
                    actualDeltaX = 0;
                    collidedX = true;
                }
                if (Math.abs(nearestCollision.normalY) > 0.5) {
                    actualDeltaY = 0;
                    collidedY = true;
                }
                if (Math.abs(nearestCollision.normalZ) > 0.5) {
                    actualDeltaZ = 0;
                    collidedZ = true;
                }

                // Reduce remaining time
                remainingTime -= nearestTime;
            } else {
                // No more collisions, move the rest of the way
                double moveX = actualDeltaX * remainingTime;
                double moveY = actualDeltaY * remainingTime;
                double moveZ = actualDeltaZ * remainingTime;

                entityBox = entityBox.offset(moveX, moveY, moveZ);
                break;
            }

            // If all axes have collided, we can't move anymore
            if (collidedX && collidedY && collidedZ) {
                break;
            }
        }

        // Calculate how far we actually moved
        Vec3d finalPos = new Vec3d(
                entityBox.getCenter().getX(),
                entityBox.getMinY(),
                entityBox.getCenter().getZ()
        );

        // Apply the movement
        body.getPosition().set(finalPos.getX(), finalPos.getY(), finalPos.getZ());

        // Update velocity based on collisions
        Vec3d currentVelocity = body.getVelocity();
        double newVelX = collidedX ? 0 : currentVelocity.getX();
        double newVelY = collidedY ? 0 : currentVelocity.getY();
        double newVelZ = collidedZ ? 0 : currentVelocity.getZ();

        body.setVelocity(newVelX, newVelY, newVelZ);

        // Check if on ground
        body.setOnGround(checkGroundCollision(body));
    }

    /**
     * Checks if the rigid body is touching the ground (blocks or other entities).
     */
    private boolean checkGroundCollision(RigidBody body) {
        AABB entityBox = body.getBoundingBox();
        AABB checkBox = entityBox.offset(0, -COLLISION_EPSILON, 0);

        // Check for block collision below
        boolean onBlock = checkBlockGroundCollision(body, checkBox);

        // Check for entity collision below
        boolean onEntity = checkEntityGroundCollision(body, checkBox);

        return onBlock || onEntity;
    }

    /**
     * Checks if the rigid body is standing on a block.
     */
    private boolean checkBlockGroundCollision(RigidBody body, AABB checkBox) {
        // Retrieve block and its properties
        short blockId = world.getBlockId(new BlockPos(body.getPosition().getX(), body.getPosition().getY() - 0.1, body.getPosition().getZ()));
        Block block = Blocks.getByNumericId(blockId);

        // Update the RigidBody with the block's physical properties
        body.setSurfaceProperties(
                block.getProperties().getFriction(),
                block.getProperties().getRestitution()
        );

        // Standard collision check logic
        List<AABB> blockBoxes = blockCollisionCache.getCollidingBlocks(checkBox, 0.1);
        for (AABB blockBox : blockBoxes) {
            if (checkBox.intersects(blockBox)) {
                // Handle bounciness on landing
                if (body.getVelocity().getY() < -2.0 && block.getProperties().getRestitution() > 0) {
                    body.setVelocity(body.getVelocity().getX(),
                            -body.getVelocity().getY() * block.getProperties().getRestitution(),
                            body.getVelocity().getZ());
                }
                return true;
            }
        }
        return false;
    }

    /**
     * Checks if the rigid body is standing on another entity.
     */
    private boolean checkEntityGroundCollision(RigidBody body, AABB checkBox) {
        // Check all other entities
        for (RigidBody other : rigidBodies) {
            // Skip self and non-collidable entities
            if (other == body || !other.isCollidable()) {
                continue;
            }

            AABB otherBox = other.getBoundingBox();

            // Check if we're standing on top of this entity
            if (checkBox.intersects(otherBox)) {
                // Make sure we're actually above the entity (not just touching sides)
                double entityBottom = body.getBoundingBox().getMinY();
                double otherTop = otherBox.getMaxY();

                // If our bottom is close to their top, we're standing on them
                if (Math.abs(entityBottom - otherTop) < COLLISION_EPSILON * 3) {
                    // Use entity's surface properties (or default friction)
                    body.setSurfaceProperties(0.91f, 0.0f);

                    // Match entity's horizontal velocity to move with it (like standing on a moving platform)
                    Vec3d otherVel = other.getVelocity();
                    if (Math.abs(otherVel.getX()) > 0.01 || Math.abs(otherVel.getZ()) > 0.01) {
                        // Apply some of the entity's momentum
                        body.setVelocity(
                                body.getVelocity().getX() + otherVel.getX() * 0.5,
                                body.getVelocity().getY(),
                                body.getVelocity().getZ() + otherVel.getZ() * 0.5
                        );
                    }

                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Performs a raycast from the entity's position to check for block collisions.
     * Useful for ground detection and line-of-sight checks.
     */
    public boolean raycastBlocks(Vec3d start, Vec3d end) {
        // Simple DDA algorithm
        double dx = end.getX() - start.getX();
        double dy = end.getY() - start.getY();
        double dz = end.getZ() - start.getZ();

        double length = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (length < COLLISION_EPSILON) {
            return false;
        }

        // Normalize direction
        dx /= length;
        dy /= length;
        dz /= length;

        // Step along the ray
        double steps = Math.ceil(length);
        for (int i = 0; i <= steps; i++) {
            double t = i / steps;
            int x = (int) Math.floor(start.getX() + dx * length * t);
            int y = (int) Math.floor(start.getY() + dy * length * t);
            int z = (int) Math.floor(start.getZ() + dz * length * t);

            if (blockCollisionCache.isSolid(x, y, z)) {
                return true;
            }
        }

        return false;
    }

    /**
     * Checks if an AABB would collide with any blocks at its current position.
     */
    public boolean checkCollision(AABB box) {
        List<AABB> blockBoxes = blockCollisionCache.getCollidingBlocks(box, 0);

        for (AABB blockBox : blockBoxes) {
            if (box.intersects(blockBox)) {
                return true;
            }
        }

        return false;
    }

    /**
     * Separates two overlapping entities by moving them apart.
     * This is position-based correction that prevents entities from getting stuck inside each other.
     */
    private void depenetrateEntities(RigidBody body1, RigidBody body2) {
        AABB box1 = body1.getBoundingBox();
        AABB box2 = body2.getBoundingBox();

        if (!box1.intersects(box2)) {
            return; // No overlap
        }

        // Calculate overlap on each axis
        double overlapX = Math.min(box1.getMaxX() - box2.getMinX(), box2.getMaxX() - box1.getMinX());
        double overlapY = Math.min(box1.getMaxY() - box2.getMinY(), box2.getMaxY() - box1.getMinY());
        double overlapZ = Math.min(box1.getMaxZ() - box2.getMinZ(), box2.getMaxZ() - box1.getMinZ());

        // Find the axis with minimum overlap (this is the best separation direction)
        double minOverlap = Math.min(Math.min(overlapX, overlapY), overlapZ);

        // Calculate separation vector
        double separationX = 0;
        double separationY = 0;
        double separationZ = 0;

        if (minOverlap == overlapX) {
            // Separate on X axis
            separationX = (box1.getCenter().getX() < box2.getCenter().getX()) ? -overlapX : overlapX;
        } else if (minOverlap == overlapY) {
            // Separate on Y axis
            separationY = (box1.getCenter().getY() < box2.getCenter().getY()) ? -overlapY : overlapY;
        } else {
            // Separate on Z axis
            separationZ = (box1.getCenter().getZ() < box2.getCenter().getZ()) ? -overlapZ : overlapZ;
        }

        // Calculate mass ratio for weighted separation
        float totalMass = body1.getMass() + body2.getMass();
        float mass1Ratio = body2.getMass() / totalMass; // Body1 moves proportional to body2's mass
        float mass2Ratio = body1.getMass() / totalMass; // Body2 moves proportional to body1's mass

        // Add small epsilon to prevent jitter
        double epsilon = COLLISION_EPSILON * 2;

        // Apply separation (heavier objects move less)
        Vec3d pos1 = body1.getPosition();
        Vec3d pos2 = body2.getPosition();

        // Check if separation would push entity into a block
        AABB testBox1 = body1.getCollisionBox().offset(
                pos1.getX() + separationX * mass1Ratio,
                pos1.getY() + separationY * mass1Ratio,
                pos1.getZ() + separationZ * mass1Ratio
        );

        AABB testBox2 = body2.getCollisionBox().offset(
                pos2.getX() - separationX * mass2Ratio,
                pos2.getY() - separationY * mass2Ratio,
                pos2.getZ() - separationZ * mass2Ratio
        );

        boolean body1WouldCollide = checkCollision(testBox1);
        boolean body2WouldCollide = checkCollision(testBox2);

        if (body1WouldCollide && !body2WouldCollide) {
            // Only move body2
            body2.getPosition().set(
                    pos2.getX() - separationX - Math.signum(separationX) * epsilon,
                    pos2.getY() - separationY - Math.signum(separationY) * epsilon,
                    pos2.getZ() - separationZ - Math.signum(separationZ) * epsilon
            );
        } else if (body2WouldCollide && !body1WouldCollide) {
            // Only move body1
            body1.getPosition().set(
                    pos1.getX() + separationX + Math.signum(separationX) * epsilon,
                    pos1.getY() + separationY + Math.signum(separationY) * epsilon,
                    pos1.getZ() + separationZ + Math.signum(separationZ) * epsilon
            );
        } else if (!body1WouldCollide && !body2WouldCollide) {
            // Move both based on mass
            body1.getPosition().set(
                    pos1.getX() + separationX * mass1Ratio + Math.signum(separationX) * epsilon,
                    pos1.getY() + separationY * mass1Ratio + Math.signum(separationY) * epsilon,
                    pos1.getZ() + separationZ * mass1Ratio + Math.signum(separationZ) * epsilon
            );

            body2.getPosition().set(
                    pos2.getX() - separationX * mass2Ratio - Math.signum(separationX) * epsilon,
                    pos2.getY() - separationY * mass2Ratio - Math.signum(separationY) * epsilon,
                    pos2.getZ() - separationZ * mass2Ratio - Math.signum(separationZ) * epsilon
            );
        }
        // If both would collide with blocks, they're stuck - let velocity resolution handle it
    }

    /**
     * Checks collision between two rigid bodies (entity vs entity).
     * This is separate from block collision.
     */
    public boolean checkEntityCollision(RigidBody body1, RigidBody body2) {
        if (!body1.isCollidable() || !body2.isCollidable()) {
            return false;
        }

        AABB box1 = body1.getBoundingBox();
        AABB box2 = body2.getBoundingBox();

        return box1.intersects(box2);
    }

    /**
     * Resolves collision between two rigid bodies with elastic collision response.
     * Uses impulse-based physics for realistic momentum transfer.
     */
    public void resolveEntityCollision(RigidBody body1, RigidBody body2) {
        AABB box1 = body1.getBoundingBox();
        AABB box2 = body2.getBoundingBox();

        if (!box1.intersects(box2)) {
            return; // No collision
        }

        // Calculate collision normal (direction from body1 to body2)
        Vec3d center1 = box1.getCenter();
        Vec3d center2 = box2.getCenter();

        double dx = center2.getX() - center1.getX();
        double dy = center2.getY() - center1.getY();
        double dz = center2.getZ() - center1.getZ();

        double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (distance < COLLISION_EPSILON) {
            // Centers are too close, use arbitrary direction
            dx = 1.0;
            dy = 0.0;
            dz = 0.0;
            distance = 1.0;
        }

        // Normalize collision normal
        dx /= distance;
        dy /= distance;
        dz /= distance;

        // Calculate relative velocity
        Vec3d v1 = body1.getVelocity();
        Vec3d v2 = body2.getVelocity();

        double relVelX = v2.getX() - v1.getX();
        double relVelY = v2.getY() - v1.getY();
        double relVelZ = v2.getZ() - v1.getZ();

        // Relative velocity along collision normal
        double velAlongNormal = relVelX * dx + relVelY * dy + relVelZ * dz;

        // Objects are separating, don't resolve
        if (velAlongNormal > 0) {
            return;
        }

        // Calculate restitution (bounciness) - use minimum of both entities
        float restitution = Math.min(body1.getRestitution(), body2.getRestitution());

        // Calculate impulse magnitude using the formula:
        // j = -(1 + e) * v_rel · n / (1/m1 + 1/m2)
        double impulseMagnitude = -(1.0 + restitution) * velAlongNormal;
        impulseMagnitude /= (1.0 / body1.getMass()) + (1.0 / body2.getMass());

        // Apply impulse to both entities
        double impulseX = impulseMagnitude * dx;
        double impulseY = impulseMagnitude * dy;
        double impulseZ = impulseMagnitude * dz;

        // Apply impulse scaled by mass (Newton's third law)
        double impulseScale1 = 1.0 / body1.getMass();
        double impulseScale2 = 1.0 / body2.getMass();

        body1.applyImpulse(-impulseX * impulseScale1, -impulseY * impulseScale1, -impulseZ * impulseScale1);
        body2.applyImpulse(impulseX * impulseScale2, impulseY * impulseScale2, impulseZ * impulseScale2);

        // Additional push to help separate entities (especially useful when they're stuck)
        // This adds a small "spring-like" force based on overlap
        double pushStrength = ENTITY_SEPARATION_STRENGTH;
        double overlapAmount = Math.max(0, (box1.getWidth() + box2.getWidth()) / 2 - distance);

        if (overlapAmount > COLLISION_EPSILON) {
            double pushX = dx * overlapAmount * pushStrength;
            double pushY = dy * overlapAmount * pushStrength;
            double pushZ = dz * overlapAmount * pushStrength;

            body1.applyImpulse(-pushX * impulseScale1, -pushY * impulseScale1, -pushZ * impulseScale1);
            body2.applyImpulse(pushX * impulseScale2, pushY * impulseScale2, pushZ * impulseScale2);
        }
    }

    /**
     * Gets all registered rigid bodies.
     */
    public List<RigidBody> getRigidBodies() {
        return new ArrayList<>(rigidBodies);
    }

    /**
     * Clears all registered rigid bodies.
     */
    public void clear() {
        rigidBodies.clear();
        LOGGER.info("PhysicsManager cleared");
    }

    /**
     * Gets debug information about the physics system.
     */
    public String getDebugInfo() {
        return String.format("Physics: %d bodies (%d active, %d paused)",
                rigidBodies.size(), entitiesWithPhysics, entitiesPaused);
    }

    /**
     * Gets the number of entities with active physics this tick.
     */
    public int getActivePhysicsCount() {
        return entitiesWithPhysics;
    }

    /**
     * Gets the number of entities with paused physics this tick.
     */
    public int getPausedPhysicsCount() {
        return entitiesPaused;
    }
}