package engine.strata.entity;

import engine.helios.physics.AABB;
import engine.helios.physics.RigidBody;
import engine.strata.client.frontend.render.animation.AnimationProcessor;
import engine.strata.client.frontend.render.model.StrataModel;
import engine.strata.client.frontend.render.model.io.ModelManager;
import engine.strata.entity.util.EntityKey;
import engine.strata.util.Gender;
import engine.strata.util.Identifier;
import engine.strata.util.Transform;
import engine.strata.util.Vec3d;
import engine.strata.util.math.Math;
import engine.strata.util.math.Random;
import engine.strata.world.World;
import org.joml.Quaternionf;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static engine.strata.util.math.Math.lerp;

/**
 * Base entity class for the Strata engine.
 *
 * <h3>Rendering:</h3>
 * <p>Entities no longer handle their own rendering. Instead, entity-specific
 * renderers are registered via {@link engine.strata.client.frontend.render.renderer.entity.EntityRendererRegistry}.
 * Each entity type has its own renderer that decides how to render it.
 *
 * <h3>Animation:</h3>
 * <p>Entities provide an {@link AnimationProcessor} for bone animations.
 * The {@link #updateAnimations(float)} method is called by renderers to
 * update animation state each frame.
 *
 * <h3>Gender System:</h3>
 * <p>Entities with gendered models can toggle between male/female/unknown.
 * The {@link #gender} field controls which mesh groups are visible.
 */
public abstract class Entity extends RigidBody {

    private static final AtomicInteger CURRENT_ID = new AtomicInteger();

    protected final EntityKey<?> key;
    protected final World world;

    // ══════════════════════════════════════════════════════════════════════════
    // HEAD & BODY ROTATION
    // ══════════════════════════════════════════════════════════════════════════

    private float headYaw;
    private float headPitch;
    private float bodyYaw;
    private float lastTickHeadYaw;

    // Rotation settings - smooth interpolation
    private static final float MAX_HEAD_ROTATION = 75.0f; // Max angle difference between head and body
    private static final float BODY_ROTATION_SPEED = 0.4f; // How fast body catches up (lower = smoother)
    private static final float FAST_HEAD_MOVEMENT_THRESHOLD = 10.0f; // If head moves this fast, snap body

    // ══════════════════════════════════════════════════════════════════════════
    // MODEL TRANSFORM
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * Transform applied to the model (position/rotation/scale offset from entity origin).
     * Used for model adjustments, offsets, or special effects.
     */
    protected final Transform modelTransform = new Transform();

    // ══════════════════════════════════════════════════════════════════════════
    // INTERPOLATION STATE
    // ══════════════════════════════════════════════════════════════════════════

    // Previous state for smooth rendering
    public double prevX, prevY, prevZ;
    public float prevHeadYaw, prevHeadPitch, prevBodyYaw;
    public Quaternionf prevRotationQuat = new Quaternionf();

    // ══════════════════════════════════════════════════════════════════════════
    // ENTITY PROPERTIES
    // ══════════════════════════════════════════════════════════════════════════

    public float eyeHeight = 1.62f;
    protected UUID uuid = UUID.randomUUID();
    private final int id = CURRENT_ID.incrementAndGet();
    protected final Random random = new Random(System.currentTimeMillis() + id);
    private final Identifier entityId;

    // ══════════════════════════════════════════════════════════════════════════
    // GENDER & NSFW SYSTEM
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * The entity's gender. Controls which mesh groups are visible.
     * Defaults to UNKNOWN (uses model file defaults).
     */
    private Gender gender = Gender.UNKNOWN;

    /**
     * Whether this model HAS meshMale/meshFemale bones.
     * Set from EntityKey - indicates model capability.
     */
    private final boolean supportsGenders;

    /**
     * Whether this model HAS genital bones.
     * Set from EntityKey - indicates model capability.
     */
    private final boolean supportsNSFW;

    /**
     * User preference: whether to show NSFW content (genitals).
     * Only has effect if {@link #supportsNSFW} is true.
     */
    private boolean nudeMode = true;

    // ══════════════════════════════════════════════════════════════════════════
    // ANIMATION SYSTEM
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * Per-entity animation processor. Lazily created on first access.
     * Bound to the model by the renderer when first rendered.
     */
    private AnimationProcessor animationProcessor;

    // ══════════════════════════════════════════════════════════════════════════
    // CONSTRUCTOR
    // ══════════════════════════════════════════════════════════════════════════

    public Entity(EntityKey<?> key, World world) {
        this.key = key;
        this.world = world;
        this.transform.getPosition().set(Vec3d.ZERO);
        this.entityId = key.getEntityId();
        this.lastTickHeadYaw = 0.0f;

        // Read model capabilities from EntityKey
        this.supportsGenders = key.supportsGenders();
        this.supportsNSFW = key.supportsNSFW();
    }

    // ══════════════════════════════════════════════════════════════════════════
    // TICK / UPDATE
    // ══════════════════════════════════════════════════════════════════════════

    @Override
    public void tick() {
        // Store previous state for interpolation
        this.prevX = this.getPosition().getX();
        this.prevY = this.getPosition().getY();
        this.prevZ = this.getPosition().getZ();
        this.prevHeadYaw = this.headYaw;
        this.prevHeadPitch = this.headPitch;
        this.prevBodyYaw = this.bodyYaw;
        this.prevRotationQuat.set(this.getRotation());

        // Update body rotation to follow head
        updateBodyRotation();
        this.lastTickHeadYaw = this.headYaw;

        // Update quaternion from body yaw
        this.getRotation().rotationY((float) Math.toRadians(-this.bodyYaw));

        super.tick();
    }

    // ══════════════════════════════════════════════════════════════════════════
    // ANIMATION SYSTEM
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * Returns this entity's {@link AnimationProcessor}, creating it on first call.
     * The processor is bound to the model by the renderer on first render.
     *
     * @return The animation processor for this entity
     */
    public AnimationProcessor getAnimationProcessor() {
        if (animationProcessor == null) {
            animationProcessor = new AnimationProcessor();
        }
        return animationProcessor;
    }

    /**
     * Called by renderers each frame to prepare animations.
     * Resets all bones to defaults then calls {@link #updateAnimations(float)}.
     *
     * <p><strong>DO NOT OVERRIDE THIS METHOD</strong> - override {@link #updateAnimations(float)} instead.
     *
     * @param partialTicks Frame interpolation factor (0.0 - 1.0)
     */
    public final void prepareAnimations(float partialTicks) {
        getAnimationProcessor().resetAllBones();
        updateAnimations(partialTicks);
    }

    /**
     * Override to implement custom bone animations.
     * Called each frame after bones have been reset to defaults.
     *
     * <h3>Example:</h3>
     * <pre>{@code
     * @Override
     * protected void updateAnimations(float partialTicks) {
     *     AnimationProcessor anim = getAnimationProcessor();
     *     anim.applyHeadRotation(getHeadYaw(), getHeadPitch());
     *     anim.applyWalkingAnimation(walkDistance, walkSpeed);
     * }
     * }</pre>
     *
     * @param partialTicks Frame interpolation factor (0.0 - 1.0)
     */
    protected void updateAnimations(float partialTicks) {
        // Default: no animations. Override in subclasses.
    }

    // ══════════════════════════════════════════════════════════════════════════
    // GENDER SYSTEM
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * Gets the entity's gender.
     *
     * @return Current gender (MALE, FEMALE, or UNKNOWN)
     */
    public Gender getGender() {
        return gender;
    }

    /**
     * Sets the entity's gender.
     * Only has effect if the model supports genders.
     *
     * @param gender New gender value
     */
    public void setGender(Gender gender) {
        if (gender != null && this.gender != gender) {
            this.gender = gender;
        }
    }

    /**
     * Checks if this model HAS meshMale/meshFemale bones.
     * This is a model capability flag, not a toggle.
     *
     * @return true if model supports gender-specific meshes
     */
    public boolean supportsGenders() {
        return supportsGenders;
    }

    /**
     * Checks if this model HAS genital bones.
     * This is a model capability flag, not a toggle.
     *
     * @return true if model has NSFW content
     */
    public boolean supportsNSFW() {
        return supportsNSFW;
    }

    /**
     * Gets the nude mode setting (whether to show genitals).
     * Only has effect if the model supports NSFW content.
     *
     * @return true if nude mode is enabled
     */
    public boolean isNudeMode() {
        return nudeMode;
    }

    /**
     * Sets the nude mode (whether to show genitals).
     * Only has effect if the model supports NSFW content.
     *
     * @param nudeMode true to show genitals, false to hide them
     */
    public void setNudeMode(boolean nudeMode) {
        this.nudeMode = nudeMode;
    }

    // ══════════════════════════════════════════════════════════════════════════
    // BODY ROTATION (HEAD TRACKING)
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * Updates body rotation to smoothly follow head rotation.
     * Called automatically during {@link #tick()}.
     */
    private void updateBodyRotation() {
        float headMovement = Math.abs(normalizeAngle(headYaw - lastTickHeadYaw));
        float angleDiff = normalizeAngle(headYaw - bodyYaw);

        if (headMovement > FAST_HEAD_MOVEMENT_THRESHOLD) {
            // Fast head movement - snap body closer immediately
            if (Math.abs(angleDiff) > MAX_HEAD_ROTATION) {
                bodyYaw = normalizeAngle(headYaw - Math.signum(angleDiff) * MAX_HEAD_ROTATION);
            } else {
                bodyYaw = normalizeAngle(bodyYaw + angleDiff * 0.8f);
            }
        } else {
            // Slow head movement - smooth body rotation
            final float MAX_ROTATION_PER_TICK = 10.0f;

            if (Math.abs(angleDiff) > MAX_HEAD_ROTATION) {
                // Body is too far behind - catch up
                float targetBodyYaw = normalizeAngle(headYaw - Math.signum(angleDiff) * MAX_HEAD_ROTATION);
                float targetDiff = normalizeAngle(targetBodyYaw - bodyYaw);
                float clampedDiff = (float) Math.max(-MAX_ROTATION_PER_TICK,
                        Math.min(MAX_ROTATION_PER_TICK, targetDiff));
                bodyYaw = normalizeAngle(bodyYaw + clampedDiff);
            } else {
                // Body is within range - smooth follow
                float rotationAmount = (float) Math.max(-MAX_ROTATION_PER_TICK,
                        Math.min(MAX_ROTATION_PER_TICK,
                                angleDiff * BODY_ROTATION_SPEED));
                bodyYaw = normalizeAngle(bodyYaw + rotationAmount);
            }
        }
    }

    /**
     * Normalizes an angle to [-180, 180] range.
     */
    private float normalizeAngle(float angle) {
        angle = angle % 360.0f;
        if (angle > 180.0f) angle -= 360.0f;
        else if (angle < -180.0f) angle += 360.0f;
        return angle;
    }

    // ══════════════════════════════════════════════════════════════════════════
    // MOVEMENT
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * Makes the entity jump with the given velocity.
     * Only works if entity is on the ground.
     *
     * @param jumpVelocity Upward velocity to apply
     */
    public void jump(double jumpVelocity) {
        if (isOnGround() && !noClip) {
            velocity = new Vec3d(velocity.getX(), jumpVelocity, velocity.getZ());
            setOnGround(false);
        }
    }

    /**
     * Moves entity relative to its current facing direction.
     *
     * @param forward Forward/backward movement (-1 to 1)
     * @param strafe Left/right movement (-1 to 1)
     * @param speed Movement speed multiplier
     */
    public void moveRelative(float forward, float strafe, float speed) {
        double yawRad = Math.toRadians(this.getHeadYaw());
        double forwardX = Math.sin(-yawRad);
        double forwardZ = Math.cos(-yawRad);
        double rightX = Math.cos(yawRad);
        double rightZ = Math.sin(yawRad);

        double targetX = (forwardX * forward) + (rightX * strafe);
        double targetZ = (forwardZ * forward) + (rightZ * strafe);

        // Normalize movement vector
        double len = Math.sqrt(targetX * targetX + targetZ * targetZ);
        if (len > 0.01) {
            targetX = (targetX / len) * speed;
            targetZ = (targetZ / len) * speed;
        }

        // Apply acceleration (smooth movement)
        double accel = isOnGround() ? 0.25 : 0.1;
        double newX = velocity.getX() + (targetX - velocity.getX()) * accel;
        double newZ = velocity.getZ() + (targetZ - velocity.getZ()) * accel;
        this.velocity = new Vec3d(newX, velocity.getY(), newZ);
    }

    // ══════════════════════════════════════════════════════════════════════════
    // BOUNDING BOX
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * Gets the entity's model bounding box in world space.
     * Used for frustum culling and collision detection.
     *
     * @param partialTicks Frame interpolation factor
     * @return World-space AABB
     */
    public AABB getModelBoundingBox(float partialTicks) {
        Identifier modelId = getModelId();
        if (modelId == null) {
            return new AABB(-0.5, 0, -0.5, 0.5, 1, 0.5).offset(getPosition());
        }

        StrataModel model = ModelManager.getModel(modelId);
        if (model == null) {
            return new AABB(-0.5, 0, -0.5, 0.5, 1, 0.5).offset(getPosition());
        }

        AABB modelAABB = model.getBoundingBox();

        // Interpolate position
        Vec3d interpolatedPos = new Vec3d(
                lerp(prevX, getPosition().getX(), partialTicks),
                lerp(prevY, getPosition().getY(), partialTicks),
                lerp(prevZ, getPosition().getZ(), partialTicks)
        );

        // Convert from model space to world space
        AABB worldAABB = new AABB(
                modelAABB.getMinX() / 16.0, modelAABB.getMinY() / 16.0, modelAABB.getMinZ() / 16.0,
                modelAABB.getMaxX() / 16.0, modelAABB.getMaxY() / 16.0, modelAABB.getMaxZ() / 16.0
        );

        // Apply entity scale
        double scaleX = getScale().getX();
        double scaleY = getScale().getY();
        double scaleZ = getScale().getZ();
        if (scaleX != 1.0 || scaleY != 1.0 || scaleZ != 1.0) {
            Vec3d center = worldAABB.getCenter();
            double halfWidth = worldAABB.getWidth() * scaleX / 2.0;
            double halfHeight = worldAABB.getHeight() * scaleY / 2.0;
            double halfDepth = worldAABB.getDepth() * scaleZ / 2.0;
            worldAABB = new AABB(
                    center.getX() - halfWidth, center.getY() - halfHeight, center.getZ() - halfDepth,
                    center.getX() + halfWidth, center.getY() + halfHeight, center.getZ() + halfDepth
            );
        }

        // Apply model transform and entity position
        worldAABB = worldAABB.offset(getModelTransform().getPosition());
        worldAABB = worldAABB.offset(interpolatedPos);
        return worldAABB;
    }

    // ══════════════════════════════════════════════════════════════════════════
    // MODEL INFORMATION
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * Gets the model ID for this entity.
     * Used by renderers to load the appropriate model.
     *
     * @return Model identifier
     */
    public Identifier getModelId() {
        return this.entityId;
    }

    /**
     * Gets the model transform (position/rotation/scale offset).
     *
     * @return Model transform
     */
    public Transform getModelTransform() {
        return modelTransform;
    }

    // ══════════════════════════════════════════════════════════════════════════
    // MODEL TRANSFORM SETTERS
    // ══════════════════════════════════════════════════════════════════════════

    public void setModelPosition(float x, float y, float z) {
        modelTransform.getPosition().set(x, y, z);
    }

    public void setModelPosition(Vec3d pos) {
        modelTransform.getPosition().set(pos.getX(), pos.getY(), pos.getZ());
    }

    public void setModelRotationDegrees(float yaw, float pitch, float roll) {
        modelTransform.getRotation().rotationZYX(
                Math.toRadians(roll),
                Math.toRadians(pitch),
                Math.toRadians(yaw)
        );
    }

    public void setModelRotation(float pitch, float yaw, float roll) {
        modelTransform.getRotation().rotationZYX(roll, pitch, yaw);
    }

    public void setModelRotation(Quaternionf rotation) {
        modelTransform.getRotation().set(rotation);
    }

    public void setModelScale(float scale) {
        modelTransform.getScale().set(scale, scale, scale);
    }

    public void setModelScale(float x, float y, float z) {
        modelTransform.getScale().set(x, y, z);
    }

    // ══════════════════════════════════════════════════════════════════════════
    // HEAD / BODY ROTATION ACCESSORS
    // ══════════════════════════════════════════════════════════════════════════

    public void setHeadYaw(float yaw) {
        this.headYaw = yaw;
    }

    public void setHeadPitch(float pitch) {
        this.headPitch = pitch;
    }

    public float getHeadYaw() {
        return headYaw;
    }

    public float getHeadPitch() {
        return headPitch;
    }

    public float getBodyYaw() {
        return bodyYaw;
    }

    public float getMaxHeadRotation() {
        return MAX_HEAD_ROTATION;
    }

    public void setBodyYaw(float yaw) {
        this.bodyYaw = yaw;
        this.getRotation().rotationY((float) Math.toRadians(-yaw));
    }

    // ══════════════════════════════════════════════════════════════════════════
    // GENERAL ACCESSORS
    // ══════════════════════════════════════════════════════════════════════════

    public float getEyeHeight() {
        return eyeHeight;
    }

    public void setEyeHeight(float h) {
        this.eyeHeight = h;
    }

    public EntityKey<?> getKey() {
        return key;
    }

    public EntityKey<?> getEntityKey() {
        return key;
    }

    public World getWorld() {
        return world;
    }

    public int getId() {
        return id;
    }

    public UUID getUuid() {
        return uuid;
    }

    public Identifier getEntityId() {
        return entityId;
    }
}