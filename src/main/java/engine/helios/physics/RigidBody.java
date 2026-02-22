package engine.helios.physics;

import engine.strata.util.math.Vec3d;
import engine.strata.world.SpatialObject;

/**
 * A physics-enabled SpatialObject with velocity, forces, and collision.
 * All entities that need physics simulation should extend this instead of SpatialObject directly.
 */
public abstract class RigidBody extends SpatialObject {

    // Physics properties
    protected Vec3d velocity = new Vec3d(0, 0, 0);
    protected Vec3d acceleration = new Vec3d(0, 0, 0);
    protected float mass = 1.0f; // kg
    protected float friction = 0.6f; // Ground friction coefficient  
    protected float drag = 0.1f; // Air resistance
    protected float restitution = 0.0f; // Bounciness (0 = no bounce, 1 = perfect bounce)

    // Physics state
    protected boolean onGround = false;
    protected boolean noClip = false; // Debug mode - disable collisions
    protected boolean hasGravity = true;
    protected boolean isCollidable = true;

    // Collision box (relative to position)
    protected AABB collisionBox;

    // Physics constants
    private static final double GRAVITY = -17.0; // blocks per second squared
    private static final double TERMINAL_VELOCITY = -78.4; // blocks per second
    private static final double GROUND_FRICTION_MULTIPLIER = 0.91;
    private static final double AIR_FRICTION_MULTIPLIER = 0.98;
    private static final double EPSILON = 0.001; // Small value for collision detection

    // Tick rate constant (20 TPS)
    private static final double TICK_DELTA = 0.05; // 1/20th of a second

    public RigidBody() {
        this.collisionBox = new AABB(-0.3, 0.0, -0.3, 0.3, 1.8, 0.3);
    }

    /**
     * Updates physics state. Should be called every tick (20 times per second).
     * Override this in subclasses but call super.tick() first.
     */
    @Override
    public void tick() {
        super.tick();

        if (noClip) {
            // In noclip mode, just apply velocity directly
            position.add(velocity.getX() * TICK_DELTA, velocity.getY() * TICK_DELTA, velocity.getZ() * TICK_DELTA);
            return;
        }

        // Apply gravity
        if (hasGravity && !onGround) {
            acceleration = acceleration.add(0, GRAVITY, 0);
        }

        // Update velocity from acceleration
        velocity = velocity.add(
                acceleration.getX() * TICK_DELTA,
                acceleration.getY() * TICK_DELTA,
                acceleration.getZ() * TICK_DELTA
        );

        // Apply drag (air resistance)
        velocity = velocity.multiply(AIR_FRICTION_MULTIPLIER, AIR_FRICTION_MULTIPLIER, AIR_FRICTION_MULTIPLIER);

        // Apply ground friction if on ground
        if (onGround) {
            velocity = velocity.multiply(GROUND_FRICTION_MULTIPLIER, 1.0, GROUND_FRICTION_MULTIPLIER);
        }

        // Clamp to terminal velocity
        if (velocity.getY() < TERMINAL_VELOCITY) {
            velocity = new Vec3d(velocity.getX(), TERMINAL_VELOCITY, velocity.getZ());
        }

        // Reset acceleration (forces need to be re-applied each tick)
        acceleration = new Vec3d(0, 0, 0);
    }

    /**
     * Applies a force to this rigid body.
     * Force is accumulated and applied over time via acceleration.
     */
    public void applyForce(double fx, double fy, double fz) {
        // F = ma, so a = F/m
        acceleration = acceleration.add(fx / mass, fy / mass, fz / mass);
    }

    /**
     * Applies a force vector to this rigid body.
     */
    public void applyForce(Vec3d force) {
        applyForce(force.getX(), force.getY(), force.getZ());
    }

    /**
     * Applies an impulse (instant velocity change) to this rigid body.
     * Unlike forces, impulses are applied immediately to velocity.
     */
    public void applyImpulse(double vx, double vy, double vz) {
        velocity = velocity.add(vx, vy, vz);
    }

    /**
     * Applies an impulse vector to this rigid body.
     */
    public void applyImpulse(Vec3d impulse) {
        velocity = velocity.add(impulse.getX(), impulse.getY(), impulse.getZ());
    }

    /**
     * Gets the world-space AABB for this rigid body.
     */
    public AABB getBoundingBox() {
        return collisionBox.offset(position);
    }

    /**
     * Sets the collision box for this rigid body.
     */
    public void setCollisionBox(double minX, double minY, double minZ,
                                double maxX, double maxY, double maxZ) {
        this.collisionBox = new AABB(minX, minY, minZ, maxX, maxY, maxZ);
    }

    /**
     * Sets the collision box from dimensions.
     */
    public void setCollisionBox(double width, double height, double depth) {
        this.collisionBox = AABB.fromDimensions(width, height, depth);
    }

    // Physics property getters and setters

    public Vec3d getVelocity() {
        return velocity;
    }

    public void setVelocity(double x, double y, double z) {
        this.velocity = new Vec3d(x, y, z);
    }

    public void setVelocity(Vec3d velocity) {
        this.velocity = new Vec3d(velocity.getX(), velocity.getY(), velocity.getZ());
    }

    public Vec3d getAcceleration() {
        return acceleration;
    }

    public boolean isOnGround() {
        return onGround;
    }

    public void setOnGround(boolean onGround) {
        this.onGround = onGround;
    }

    public boolean hasGravity() {
        return hasGravity;
    }

    public void setGravity(boolean hasGravity) {
        this.hasGravity = hasGravity;
    }

    public boolean isNoClip() {
        return noClip;
    }

    public void setNoClip(boolean noClip) {
        this.noClip = noClip;
    }

    public boolean isCollidable() {
        return isCollidable;
    }

    public void setCollidable(boolean collidable) {
        this.isCollidable = collidable;
    }

    public float getMass() {
        return mass;
    }

    public void setMass(float mass) {
        this.mass = Math.max(0.1f, mass); // Prevent zero or negative mass
    }

    public float getFriction() {
        return friction;
    }

    public void setFriction(float friction) {
        this.friction = friction;
    }

    public float getRestitution() {
        return restitution;
    }

    public void setRestitution(float restitution) {
        this.restitution = Math.max(0.0f, Math.min(1.0f, restitution));
    }

    public AABB getCollisionBox() {
        return collisionBox;
    }
}