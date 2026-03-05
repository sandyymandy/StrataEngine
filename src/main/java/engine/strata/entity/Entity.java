package engine.strata.entity;

import engine.helios.physics.RigidBody;
import engine.strata.client.frontend.render.renderer.entity.EntityRenderContext;
import engine.strata.entity.util.EntityKey;
import engine.strata.util.Identifier;
import engine.strata.util.Transform;
import engine.strata.util.math.Math;
import engine.strata.util.math.Random;
import engine.strata.util.Vec3d;
import engine.strata.util.Vec3f;
import engine.strata.world.World;
import org.joml.Quaternionf;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Enhanced Entity class with integrated rendering data.
 *
 * <h3>Rendering Integration:</h3>
 * Entities now provide their own rendering configuration through:
 * <ul>
 *   <li>{@link #getModelId()} - The model to render</li>
 *   <li>{@link #getRenderContext()} - Customization (tints, visibility, etc.)</li>
 * </ul>
 *
 * This eliminates the need to create a separate renderer class for each entity type.
 *
 * <h3>Example Usage:</h3>
 * <pre>
 * public class FireEntity extends Entity {
 *     {@code @Override}
 *     public Identifier getModelId() {
 *         return Identifier.ofEngine("fire_elemental");
 *     }
 *
 *     {@code @Override}
 *     public EntityRenderContext getRenderContext() {
 *         EntityRenderContext ctx = new EntityRenderContext();
 *         // Make it glow orange
 *         ctx.setGlobalTint(1.0f, 0.5f, 0.0f, 1.0f);
 *         ctx.setGlobalEmissive(0.8f);
 *         return ctx;
 *     }
 * }
 *
 * // Registration is now just one line:
 * EntityRendererRegistry.register(EntityRegistry.FIRE, GenericEntityRenderer::new);
 * </pre>
 */
public abstract class Entity extends RigidBody {
    private static final AtomicInteger CURRENT_ID = new AtomicInteger();

    protected final EntityKey<?> key;
    protected final World world;

    // Head rotation (separate from body rotation)
    private float headYaw;
    private float headPitch;
    private float bodyYaw; // Body rotation that follows head with delay
    private float lastTickHeadYaw; // Track head yaw from last tick for fast movement detection

    // Rotation settings - smooth interpolation (no timer-based snapping)
    private static final float MAX_HEAD_ROTATION = 75.0f; // Maximum angle difference between head and body
    private static final float BODY_ROTATION_SPEED = 0.15f; // How fast body catches up (lower = smoother)
    private static final float FAST_HEAD_MOVEMENT_THRESHOLD = 60.0f; // If head moves this fast, snap body immediately

    // Model rendering offset (in blocks, relative to entity position)
    protected final Transform modelTransform = new Transform();

    // Previous state for interpolation
    public double prevX;
    public double prevY;
    public double prevZ;
    public float prevHeadYaw;
    public float prevHeadPitch;
    public float prevBodyYaw;
    public Quaternionf prevRotationQuat = new Quaternionf(); // For smooth quaternion interpolation

    // Entity properties
    public float eyeHeight = 1.62f;
    protected final Random random = new Random(System.currentTimeMillis());
    protected UUID uuid = Math.randomUuid(this.random);
    private final int id = CURRENT_ID.incrementAndGet();
    private final Identifier entityId;

    // Rendering cache - created lazily
    private EntityRenderContext cachedRenderContext = null;
    private boolean renderContextDirty = true;

    public Entity(EntityKey<?> key, World world){
        this.key = key;
        this.world = world;
        this.transform.getPosition().set(Vec3d.ZERO);
        this.entityId = key.getEntityId();
        this.lastTickHeadYaw = 0.0f;
    }

    public void tick(){
        this.prevX = this.getPosition().getX();
        this.prevY = this.getPosition().getY();
        this.prevZ = this.getPosition().getZ();
        this.prevHeadYaw = this.headYaw;
        this.prevHeadPitch = this.headPitch;
        this.prevBodyYaw = this.bodyYaw;
        this.prevRotationQuat.set(this.getRotation()); // Store previous quaternion for interpolation

        // Update body rotation (smooth interpolation with fast movement detection)
        updateBodyRotation();

        // Store head yaw for next tick's fast movement detection
        this.lastTickHeadYaw = this.headYaw;

        // Convert bodyYaw to quaternion and set rotation
        // Negative bodyYaw for coordinate system (Y rotation around up axis)
        this.getRotation().rotationY((float) Math.toRadians(-this.bodyYaw));

        // Mark render context as dirty if entity state changed
        if (hasRenderStateChanged()) {
            markRenderContextDirty();
        }

        super.tick();
    }

    /**
     * Updates body rotation to smoothly follow head rotation.
     * Detects fast head movement and snaps body to prevent jitter.
     */
    private void updateBodyRotation() {
        // Detect how fast the head moved this tick
        float headMovement = Math.abs(normalizeAngle(headYaw - lastTickHeadYaw));

        // Calculate shortest angle difference between head and body
        float angleDiff = normalizeAngle(headYaw - bodyYaw);

        // If head is moving very fast, snap body closer to prevent jitter
        if (headMovement > FAST_HEAD_MOVEMENT_THRESHOLD) {
            // Fast movement detected - snap body to reduce lag
            // Keep body within MAX_HEAD_ROTATION but move it aggressively
            if (Math.abs(angleDiff) > MAX_HEAD_ROTATION) {
                // Exceed threshold - snap body to boundary
                bodyYaw = normalizeAngle(headYaw - Math.signum(angleDiff) * MAX_HEAD_ROTATION);
            } else {
                // Within threshold - snap body much closer to head (80% of the way)
                bodyYaw = normalizeAngle(bodyYaw + angleDiff * 0.8f);
            }
        } else {
            // Normal movement - use smooth interpolation
            final float MAX_ROTATION_PER_TICK = 10.0f; // degrees per tick

            if (Math.abs(angleDiff) > MAX_HEAD_ROTATION) {
                // Head exceeded max rotation - move body to keep head within range
                float targetBodyYaw = normalizeAngle(headYaw - Math.signum(angleDiff) * MAX_HEAD_ROTATION);
                float targetDiff = normalizeAngle(targetBodyYaw - bodyYaw);

                // Clamp rotation speed
                float clampedDiff = (float) Math.max(-MAX_ROTATION_PER_TICK, Math.min(MAX_ROTATION_PER_TICK, targetDiff));
                bodyYaw = normalizeAngle(bodyYaw + clampedDiff);
            } else {
                // Smoothly interpolate body towards head
                float rotationAmount = angleDiff * BODY_ROTATION_SPEED;

                // Clamp rotation speed to prevent jerkiness
                rotationAmount = (float) Math.max(-MAX_ROTATION_PER_TICK, Math.min(MAX_ROTATION_PER_TICK, rotationAmount));

                bodyYaw = normalizeAngle(bodyYaw + rotationAmount);
            }
        }
    }

    /**
     * Normalizes an angle to the range [-180, 180].
     * This ensures we always rotate the shortest way.
     */
    private float normalizeAngle(float angle) {
        angle = angle % 360.0f;
        if (angle > 180.0f) {
            angle -= 360.0f;
        } else if (angle < -180.0f) {
            angle += 360.0f;
        }
        return angle;
    }

    /**
     * Returns the model identifier for this entity.
     * Override to provide a different model or implement dynamic model switching.
     *
     * @return The identifier of the model to render
     */
    public Identifier getModelId(){
        return this.entityId;
    }

    /**
     * Returns the render context for this entity.
     * Override to customize appearance (tints, visibility, emissiveness, etc.)
     *
     * <p>The context is cached and only regenerated when {@link #markRenderContextDirty()}
     * is called. This improves performance for entities with complex customization.
     *
     * @return The render context with all customizations
     */
    public EntityRenderContext getRenderContext() {
        if (renderContextDirty || cachedRenderContext == null) {
            cachedRenderContext = createRenderContext();
            renderContextDirty = false;
        }
        return cachedRenderContext;
    }

    /**
     * Creates a new render context with this entity's customizations.
     * Override this method to provide custom rendering behavior.
     *
     * <p>Default implementation returns an empty context (no customization).
     *
     * @return A new render context
     */
    protected EntityRenderContext createRenderContext() {
        return new EntityRenderContext();
    }

    /**
     * Marks the render context as dirty, forcing it to be regenerated next frame.
     * Call this when entity state changes that affects rendering.
     *
     * <p>Examples: health changed (affects tint), equipped item changed (affects texture),
     * status effect applied (affects glow), etc.
     */
    protected void markRenderContextDirty() {
        this.renderContextDirty = true;
    }

    /**
     * Checks if render-affecting state has changed this tick.
     * Override to implement custom dirty checking logic.
     *
     * <p>Default implementation returns false (no automatic dirty marking).
     *
     * @return true if render context should be regenerated
     */
    protected boolean hasRenderStateChanged() {
        return false;
    }

    /**
     * Makes the entity jump if it's on the ground.
     * @param jumpVelocity The upward velocity to apply (typically 8-12)
     */
    public void jump(double jumpVelocity) {
        if (isOnGround() && !noClip) {
            velocity = new Vec3d(velocity.getX(), jumpVelocity, velocity.getZ());
            setOnGround(false);
        }
    }

    /**
     * Improved movement logic using acceleration and target velocities.
     */
    public void moveRelative(float forward, float strafe, float speed) {
        double yawRad = Math.toRadians(this.getHeadYaw());
        double forwardX = Math.sin(-yawRad);
        double forwardZ = Math.cos(-yawRad);
        double rightX = Math.cos(yawRad);
        double rightZ = Math.sin(yawRad);

        // Calculate desired direction
        double targetX = (forwardX * forward) + (rightX * strafe);
        double targetZ = (forwardZ * forward) + (rightZ * strafe);

        // Normalize input
        double len = Math.sqrt(targetX * targetX + targetZ * targetZ);
        if (len > 0.01) {
            targetX = (targetX / len) * speed;
            targetZ = (targetZ / len) * speed;
        }

        // Smoothness factors
        double accel = isOnGround() ? 0.25 : 0.1; // How fast we reach max speed
        double inertia = isOnGround() ? 0.1 : 0.01; // How much we "coast"

        // Apply Lerp-like acceleration: v = v + (target - v) * accel
        double newX = velocity.getX() + (targetX - velocity.getX()) * accel;
        double newZ = velocity.getZ() + (targetZ - velocity.getZ()) * accel;

        this.velocity = new Vec3d(newX, velocity.getY(), newZ);
    }

    public void setHeadYaw(float yaw) {
        this.headYaw = yaw;
    }

    public void setHeadPitch(float pitch) {
        this.headPitch = pitch;
    }

    public float getHeadYaw() {
        return this.headYaw;
    }

    public float getHeadPitch() {
        return this.headPitch;
    }

    public float getBodyYaw() {
        return this.bodyYaw;
    }

    public void setBodyYaw(float yaw) {
        this.bodyYaw = yaw;
        // Convert to quaternion (negative for coordinate system)
        this.getRotation().rotationY((float) Math.toRadians(-yaw));
    }

    /**
     * Gets the maximum head rotation from body in degrees.
     * Override in subclasses for different behavior.
     */
    public float getMaxHeadRotation() {
        return MAX_HEAD_ROTATION;
    }

    /**
     * Gets the model rendering transform.
     * This transform is applied relative to the entity's position and rotation,
     * allowing independent control of model position, rotation, and scale.
     *
     * @return The model transform
     */
    public Transform getModelTransform() {
        return modelTransform;
    }

    /**
     * Sets the model position offset (in blocks).
     * Positive X moves right, positive Y moves up, positive Z moves forward.
     *
     * Example: setModelPosition(0, 0, -0.5f) moves model 0.5 blocks back
     */
    public void setModelPosition(float x, float y, float z) {
        this.modelTransform.getPosition().set(x, y, z);
    }

    /**
     * Sets the model position offset from a vector.
     */
    public void setModelPosition(Vec3d position) {
        this.modelTransform.getPosition().set(position.getX(), position.getY(), position.getZ());
    }

    /**
     * Sets the model rotation (in degrees).
     * This rotation is applied after the entity's rotation.
     */
    public void setModelRotationDegrees(float yaw, float pitch, float roll) {
        this.modelTransform.getRotation().rotationZYX(
                Math.toRadians(roll),
                Math.toRadians(pitch),
                Math.toRadians(yaw)
                );
    }

    /**
     * Sets the model rotation (in radians).
     */
    public void setModelRotation(float pitch, float yaw, float roll) {
        this.modelTransform.getRotation().rotationZYX(roll, pitch, yaw);
    }

    /**
     * Sets the model rotation from a quaternion.
     */
    public void setModelRotation(Quaternionf rotation) {
        this.modelTransform.getRotation().set(rotation);
    }

    /**
     * Sets the model scale.
     * This scale is applied in addition to the entity's scale.
     * Default is (1, 1, 1).
     */
    public void setModelScale(float scale) {
        this.modelTransform.getScale().set(scale, scale, scale);
    }

    /**
     * Sets the model scale with separate X, Y, Z values.
     */
    public void setModelScale(float x, float y, float z) {
        this.modelTransform.getScale().set(x, y, z);
    }

    public float getEyeHeight() {
        return eyeHeight;
    }

    public void setEyeHeight(float eyeHeight) {
        this.eyeHeight = eyeHeight;
    }

    public EntityKey<?> getKey() {
        return this.key;
    }

    public World getWorld() {
        return this.world;
    }

    public int getId() {
        return this.id;
    }

    public UUID getUuid() {
        return this.uuid;
    }

    public Identifier getEntityId() {
        return entityId;
    }
}