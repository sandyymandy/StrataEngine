package engine.helios.physics;

import engine.strata.util.Vec3d;
import engine.strata.world.SpatialObject;

import static engine.helios.physics.PhysicsManager.GRAVITY;
import static engine.strata.core.StrataCore.TICK_DELTA;

/**
 * A physics-enabled SpatialObject with velocity, forces, and collision.
 * All entities that need physics simulation should extend this instead of SpatialObject directly.
 */
public abstract class RigidBody extends SpatialObject {

    // Physics properties
    protected Vec3d velocity = new Vec3d(0, 0, 0);
    protected Vec3d acceleration = new Vec3d(0, 0, 0);
    protected float mass = 1.0f; // kg
    protected float surfaceFriction = 0.91f;
    protected float surfaceRestitution = 0.0f;
    protected float restitution = 0.0f; // Bounciness (0 = no bounce, 1 = perfect bounce)

    // Physics state
    protected boolean onGround = false;
    protected boolean noClip = false; // Debug mode - disable collisions
    protected boolean hasGravity = true;
    protected boolean isCollidable = true;

    // Collision box (relative to position)
    protected AABB collisionBox;

    // Physics constants
     // blocks per second squared
    private static final double TERMINAL_VELOCITY = -78.4; // blocks per second
    private static final double GROUND_FRICTION_MULTIPLIER = 0.91;
    private static final double AIR_FRICTION_MULTIPLIER = 0.98;
    private static final double EPSILON = 0.001; // Small value for collision detection

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
            this.transform.getPosition().add(velocity.multiply(TICK_DELTA));
            return;
        }

        // 1. Apply Gravity
        if (hasGravity && !onGround) {
            acceleration = acceleration.add(0, GRAVITY, 0);
        }

        // 2. Integration: Velocity += Accel * dt
        velocity = velocity.add(acceleration.multiply(TICK_DELTA));

        // 3. Apply Friction (Block Dependent)
        // Air friction is constant, ground friction depends on what you stand on
        double frictionFactor = onGround ? surfaceFriction : AIR_FRICTION_MULTIPLIER;

        double vx = velocity.getX() * frictionFactor;
        double vz = velocity.getZ() * frictionFactor;
        double vy = velocity.getY();

        // Air resistance on Y (Terminal Velocity)
        if (!onGround) {
            vy *= AIR_FRICTION_MULTIPLIER;
            if (vy < TERMINAL_VELOCITY) vy = TERMINAL_VELOCITY;
        }

        velocity = new Vec3d(vx, vy, vz);

        // 4. Reset acceleration for next tick forces
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
        return collisionBox.offset(transform.getPosition());
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

    public void setSurfaceProperties(float friction, float restitution) {
        this.surfaceFriction = friction;
        this.surfaceRestitution = restitution;
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

    public float getWidth() {
        return (float) collisionBox.getWidth();
    }

    public float getHeight() {
        return (float) collisionBox.getHeight();
    }
}