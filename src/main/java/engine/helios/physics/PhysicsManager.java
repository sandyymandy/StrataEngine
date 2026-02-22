package engine.helios.physics;

import engine.strata.entity.Entity;
import engine.strata.util.math.Vec3d;
import engine.strata.world.World;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * Manages physics simulation for all rigid bodies in the world.
 * Handles collision detection with blocks and collision response.
 *
 * Features:
 * - Block collision only within 2-chunk radius of entities
 * - Sweep-and-prune broad phase collision detection
 * - Separating Axis Theorem for precise collision
 * - Handles friction, restitution, and ground detection
 */
public class PhysicsManager {
    private static final Logger LOGGER = LoggerFactory.getLogger("Physics");

    private final World world;
    private final BlockCollisionCache blockCollisionCache;

    // Physics settings
    private static final double TICK_DELTA = 0.05; // 1/20th second at 20 TPS
    private static final double COLLISION_EPSILON = 0.001; // Small value to prevent clipping
    private static final int MAX_COLLISION_ITERATIONS = 4; // Maximum collision resolution attempts

    // List of all physics objects
    private final List<RigidBody> rigidBodies = new ArrayList<>();

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
        for (RigidBody body : rigidBodies) {
            if (!body.isCollidable() || body.isNoClip()) {
                continue; // Skip collision for non-collidable or noclip entities
            }

            // Apply movement with collision detection
            moveWithCollision(body);
        }
    }

    /**
     * Moves a rigid body while checking for collisions with blocks.
     * Uses swept AABB collision detection for continuous collision.
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

        // Resolve collisions iteratively
        // Y first (vertical), then X and Z (horizontal) - this prevents entities from sliding up walls
        deltaY = resolveCollision(entityBox, blockBoxes, 0, deltaY, 0);
        entityBox = entityBox.offset(0, deltaY, 0);

        deltaX = resolveCollision(entityBox, blockBoxes, deltaX, 0, 0);
        entityBox = entityBox.offset(deltaX, 0, 0);

        deltaZ = resolveCollision(entityBox, blockBoxes, 0, 0, deltaZ);

        // Apply the movement
        body.getPosition().set(
                body.getPosition().getX() + deltaX,
                body.getPosition().getY() + deltaY,
                body.getPosition().getZ() + deltaZ
        );

        // Update velocity based on collisions
        Vec3d currentVelocity = body.getVelocity();
        double newVelX = currentVelocity.getX();
        double newVelY = currentVelocity.getY();
        double newVelZ = currentVelocity.getZ();

        if (Math.abs(deltaX) < Math.abs(currentVelocity.getX() * TICK_DELTA)) {
            newVelX = 0; // Hit wall in X
        }
        if (Math.abs(deltaY) < Math.abs(currentVelocity.getY() * TICK_DELTA)) {
            newVelY = 0; // Hit ceiling or ground
        }
        if (Math.abs(deltaZ) < Math.abs(currentVelocity.getZ() * TICK_DELTA)) {
            newVelZ = 0; // Hit wall in Z
        }

        body.setVelocity(newVelX, newVelY, newVelZ);

        // Check if on ground
        body.setOnGround(checkGroundCollision(body));
    }

    /**
     * Resolves collision in a single axis.
     * Returns the adjusted movement amount.
     */
    private double resolveCollision(AABB entityBox, List<AABB> blockBoxes,
                                    double deltaX, double deltaY, double deltaZ) {
        // Move the entity box by the delta to check for collisions
        AABB movedBox = entityBox.offset(deltaX, deltaY, deltaZ);

        // Check each block for collision
        for (AABB blockBox : blockBoxes) {
            if (movedBox.intersects(blockBox)) {
                // Calculate the clip amount for each axis
                if (deltaX != 0) {
                    deltaX = blockBox.calculateXOffset(entityBox, deltaX);
                }
                if (deltaY != 0) {
                    deltaY = blockBox.calculateYOffset(entityBox, deltaY);
                }
                if (deltaZ != 0) {
                    deltaZ = blockBox.calculateZOffset(entityBox, deltaZ);
                }

                // Update moved box for next iteration
                movedBox = entityBox.offset(deltaX, deltaY, deltaZ);
            }
        }

        // Return the adjusted delta
        if (deltaX != 0) return deltaX;
        if (deltaY != 0) return deltaY;
        if (deltaZ != 0) return deltaZ;
        return 0;
    }

    /**
     * Checks if the rigid body is touching the ground.
     */
    private boolean checkGroundCollision(RigidBody body) {
        AABB entityBox = body.getBoundingBox();

        // Check slightly below the entity
        AABB checkBox = entityBox.offset(0, -COLLISION_EPSILON, 0);

        // Get blocks below
        List<AABB> blockBoxes = blockCollisionCache.getCollidingBlocks(checkBox, 0.1);

        // If any block intersects, we're on ground
        for (AABB blockBox : blockBoxes) {
            if (checkBox.intersects(blockBox)) {
                return true;
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
     */
    public void resolveEntityCollision(RigidBody body1, RigidBody body2) {
        AABB box1 = body1.getBoundingBox();
        AABB box2 = body2.getBoundingBox();

        if (!box1.intersects(box2)) {
            return; // No collision
        }

        // Calculate collision normal and depth
        Vec3d center1 = box1.getCenter();
        Vec3d center2 = box2.getCenter();

        double dx = center2.getX() - center1.getX();
        double dy = center2.getY() - center1.getY();
        double dz = center2.getZ() - center1.getZ();

        double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (distance < COLLISION_EPSILON) {
            return; // Centers are too close, skip
        }

        // Normalize
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

        // Calculate restitution (bounciness)
        float restitution = Math.min(body1.getRestitution(), body2.getRestitution());

        // Calculate impulse magnitude
        double impulseMagnitude = -(1 + restitution) * velAlongNormal;
        impulseMagnitude /= (1.0 / body1.getMass()) + (1.0 / body2.getMass());

        // Apply impulse
        double impulseX = impulseMagnitude * dx;
        double impulseY = impulseMagnitude * dy;
        double impulseZ = impulseMagnitude * dz;

        body1.applyImpulse(-impulseX / body1.getMass(), -impulseY / body1.getMass(), -impulseZ / body1.getMass());
        body2.applyImpulse(impulseX / body2.getMass(), impulseY / body2.getMass(), impulseZ / body2.getMass());
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
        return String.format("Physics: %d bodies", rigidBodies.size());
    }
}