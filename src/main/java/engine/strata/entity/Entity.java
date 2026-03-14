package engine.strata.entity;

import engine.helios.physics.AABB;
import engine.helios.physics.RigidBody;
import engine.strata.client.frontend.render.animation.AnimationProcessor;
import engine.strata.client.frontend.render.model.GenderManager;
import engine.strata.client.frontend.render.model.StrataModel;
import engine.strata.client.frontend.render.model.io.ModelManager;
import engine.strata.client.frontend.render.renderer.entity.EntityRenderContext;
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


public abstract class Entity extends RigidBody {
    private static final AtomicInteger CURRENT_ID = new AtomicInteger();

    protected final EntityKey<?> key;
    protected final World world;

    // Head rotation (separate from body rotation)
    private float headYaw;
    private float headPitch;
    private float bodyYaw;
    private float lastTickHeadYaw;

    // Rotation settings - smooth interpolation (no timer-based snapping)
    private static final float MAX_HEAD_ROTATION = 75.0f; // Maximum angle difference between head and body
    private static final float BODY_ROTATION_SPEED = 0.4f; // How fast body catches up (lower = smoother)
    private static final float FAST_HEAD_MOVEMENT_THRESHOLD = 10.0f; // If head moves this fast, snap body immediately

    protected final Transform modelTransform = new Transform();

    // Previous state for interpolation
    public double prevX, prevY, prevZ;
    public float prevHeadYaw, prevHeadPitch, prevBodyYaw;
    public Quaternionf prevRotationQuat = new Quaternionf();

    public float eyeHeight = 1.62f;
    protected UUID uuid = UUID.randomUUID();
    private final int id = CURRENT_ID.incrementAndGet();
    protected final Random random = new Random(System.currentTimeMillis()+id);

    // ══════════════════════════════════════════════════════════════════════════
    // GENDER SYSTEM
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * The entity's gender. Can be changed at runtime.
     * Defaults to UNKNOWN (uses model file defaults).
     */
    private Gender gender = Gender.UNKNOWN;

    /**
     * Previous gender value. Used to detect changes and trigger render context refresh.
     */
    private Gender previousGender = Gender.UNKNOWN;

    /**
     * Whether this model HAS meshMale/meshFemale bones.
     * Set from EntityKey - indicates model capability, not user preference.
     */
    private final boolean supportsGenders;

    /**
     * Whether this model HAS genital bones.
     * Set from EntityKey - indicates model capability, not user preference.
     */
    private final boolean supportsNSFW;

    /**
     * User preference: whether to show NSFW content (genitals).
     * Only has effect if {@link #supportsNSFW} is true.
     * Can be toggled at runtime (e.g., from settings menu).
     */
    private boolean nudeMode = true;

    /**
     * Previous nude mode value. Used to detect changes and trigger render context refresh.
     */
    private boolean previousNudeMode = true;

    private final Identifier entityId;

    private EntityRenderContext cachedRenderContext = null;
    private boolean renderContextDirty = true;

    /**
     * Per-entity animation processor. Lazily created on first call to {@link #getAnimationProcessor()}.
     * Bound to the model by {@code EntityRenderer} when the model is first loaded.
     */
    private AnimationProcessor animationProcessor;

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

    @Override
    public void tick() {
        this.prevX = this.getPosition().getX();
        this.prevY = this.getPosition().getY();
        this.prevZ = this.getPosition().getZ();
        this.prevHeadYaw = this.headYaw;
        this.prevHeadPitch = this.headPitch;
        this.prevBodyYaw = this.bodyYaw;
        this.prevRotationQuat.set(this.getRotation());

        updateBodyRotation();
        this.lastTickHeadYaw = this.headYaw;
        this.getRotation().rotationY((float) Math.toRadians(-this.bodyYaw));

        // Check if gender or nude mode changed
        if (previousGender != gender || previousNudeMode != nudeMode) {
            markRenderContextDirty();
            previousGender = gender;
            previousNudeMode = nudeMode;
        }

        if (hasRenderStateChanged()) {
            markRenderContextDirty();
        }

        super.tick();
    }

    // ── Animation ─────────────────────────────────────────────────────────────

    /**
     * Returns this entity's {@link AnimationProcessor}, creating it on first call.
     * The processor is bound to the model by {@code EntityRenderer} on first render.
     */
    public AnimationProcessor getAnimationProcessor() {
        if (animationProcessor == null) {
            animationProcessor = new AnimationProcessor();
        }
        return animationProcessor;
    }

    /**
     * Called by {@code EntityRenderer} each frame before rendering.
     * Resets all bones to model-file defaults then calls {@link #updateAnimations(float)}.
     * Do not override this — override {@link #updateAnimations(float)} instead.
     */
    public final void prepareAnimations(float partialTicks) {
        getAnimationProcessor().resetAllBones();

        // Apply gender visibility BEFORE custom animations
        if (supportsGenders) {
            applyGenderVisibility();
        }

        updateAnimations(partialTicks);
    }

    /**
     * Override to drive per-frame bone animations.
     * Called after all bone states have been reset to model-file defaults
     * and gender visibility has been applied.
     *
     * <pre>
     * {@code @Override}
     * protected void updateAnimations(float partialTicks) {
     *     AnimationProcessor anim = getAnimationProcessor();
     *     anim.applyHeadRotation(getHeadYaw(), getHeadPitch());
     *     anim.applyWalkingAnimation(walkDistance, walkSpeed);
     * }
     * </pre>
     */
    protected void updateAnimations(float partialTicks) {
        // Default: no animations. Override in subclasses.
    }

    /**
     * Applies gender-based bone visibility.
     * Called automatically if model {@link #supportsGenders}.
     * Can be overridden for custom gender logic.
     */
    protected void applyGenderVisibility() {
        Identifier modelId = getModelId();
        if (modelId == null) return;

        StrataModel model = ModelManager.getModel(modelId);
        if (model == null) return;

        AnimationProcessor anim = getAnimationProcessor();

        // Apply gender visibility with nude mode toggle
        GenderManager.applyGenderVisibility(anim, model, gender, nudeMode, supportsNSFW);
    }

    // ── Render context ────────────────────────────────────────────────────────

    /**
     * Returns the render context for this entity.
     * The context is cached and only recreated when {@link #markRenderContextDirty()} is called.
     * The live {@link AnimationProcessor} is always re-attached after (re)creation.
     */
    public EntityRenderContext getRenderContext() {
        if (renderContextDirty || cachedRenderContext == null) {
            cachedRenderContext = createRenderContext();
            renderContextDirty = false;
        }
        cachedRenderContext.setAnimationProcessor(getAnimationProcessor());
        return cachedRenderContext;
    }

    /**
     * Creates a new render context with this entity's static customisations.
     * Do NOT set the AnimationProcessor here — that is handled by {@link #getRenderContext()}.
     * Override to add tints, texture overrides, bone visibility flags, etc.
     */
    protected EntityRenderContext createRenderContext() {
        return new EntityRenderContext();
    }

    /**
     * Marks the render context as dirty, forcing it to regenerate next frame.
     * Call when entity state changes that affects rendering (health, equipment, etc).
     */
    protected void markRenderContextDirty() {
        this.renderContextDirty = true;
    }

    /**
     * Override to implement custom dirty-checking logic.
     * Default returns false (no automatic dirty marking).
     */
    protected boolean hasRenderStateChanged() {
        return false;
    }

    // ── Gender system ─────────────────────────────────────────────────────────

    /**
     * Gets this entity's gender.
     */
    public Gender getGender() {
        return gender;
    }

    /**
     * Sets this entity's gender.
     * Automatically marks render context as dirty if gender changes.
     * Gender visibility is applied automatically on next frame if model supports genders.
     */
    public void setGender(Gender gender) {
        if (this.gender != gender) {
            this.gender = gender;
            markRenderContextDirty();
        }
    }

    /**
     * Checks if this model HAS meshMale/meshFemale bones.
     * This is a model capability flag, not a toggle.
    */
    public boolean supportsGenders() {
        return supportsGenders;
    }

    /**
     * Checks if this model HAS genital bones.
     * This is a model capability flag, not a toggle.
     */
    public boolean supportsNSFW() {
        return supportsNSFW;
    }

    /**
     * Gets the nude mode setting (whether to show genitals).
     * Only has effect if the model supports NSFW content.
     */
    public boolean isNudeMode() {
        return nudeMode;
    }

    /**
     * Sets the nude mode (whether to show genitals).
     * Only has effect if the model supports NSFW content.
     * Automatically marks render context as dirty if changed.
     *
     * @param nudeMode true to show genitals, false to hide them
     */
    public void setNudeMode(boolean nudeMode) {
        if (this.nudeMode != nudeMode) {
            this.nudeMode = nudeMode;
            markRenderContextDirty();
        }
    }

    // ── Body rotation ─────────────────────────────────────────────────────────

    private void updateBodyRotation() {
        float headMovement = Math.abs(normalizeAngle(headYaw - lastTickHeadYaw));
        float angleDiff = normalizeAngle(headYaw - bodyYaw);

        if (headMovement > FAST_HEAD_MOVEMENT_THRESHOLD) {
            if (Math.abs(angleDiff) > MAX_HEAD_ROTATION) {
                bodyYaw = normalizeAngle(headYaw - Math.signum(angleDiff) * MAX_HEAD_ROTATION);
            } else {
                bodyYaw = normalizeAngle(bodyYaw + angleDiff * 0.8f);
            }
        } else {
            final float MAX_ROTATION_PER_TICK = 10.0f;
            if (Math.abs(angleDiff) > MAX_HEAD_ROTATION) {
                float targetBodyYaw = normalizeAngle(headYaw - Math.signum(angleDiff) * MAX_HEAD_ROTATION);
                float targetDiff = normalizeAngle(targetBodyYaw - bodyYaw);
                float clampedDiff = (float) Math.max(-MAX_ROTATION_PER_TICK, Math.min(MAX_ROTATION_PER_TICK, targetDiff));
                bodyYaw = normalizeAngle(bodyYaw + clampedDiff);
            } else {
                float rotationAmount = (float) Math.max(-MAX_ROTATION_PER_TICK, Math.min(MAX_ROTATION_PER_TICK, angleDiff * BODY_ROTATION_SPEED));
                bodyYaw = normalizeAngle(bodyYaw + rotationAmount);
            }
        }
    }

    private float normalizeAngle(float angle) {
        angle = angle % 360.0f;
        if (angle > 180.0f) angle -= 360.0f;
        else if (angle < -180.0f) angle += 360.0f;
        return angle;
    }

    // ── Movement ──────────────────────────────────────────────────────────────

    public void jump(double jumpVelocity) {
        if (isOnGround() && !noClip) {
            velocity = new Vec3d(velocity.getX(), jumpVelocity, velocity.getZ());
            setOnGround(false);
        }
    }

    public void moveRelative(float forward, float strafe, float speed) {
        double yawRad = Math.toRadians(this.getHeadYaw());
        double forwardX = Math.sin(-yawRad);
        double forwardZ = Math.cos(-yawRad);
        double rightX = Math.cos(yawRad);
        double rightZ = Math.sin(yawRad);

        double targetX = (forwardX * forward) + (rightX * strafe);
        double targetZ = (forwardZ * forward) + (rightZ * strafe);

        double len = Math.sqrt(targetX * targetX + targetZ * targetZ);
        if (len > 0.01) {
            targetX = (targetX / len) * speed;
            targetZ = (targetZ / len) * speed;
        }

        double accel = isOnGround() ? 0.25 : 0.1;
        double newX = velocity.getX() + (targetX - velocity.getX()) * accel;
        double newZ = velocity.getZ() + (targetZ - velocity.getZ()) * accel;
        this.velocity = new Vec3d(newX, velocity.getY(), newZ);
    }

    // ── Bounding box ──────────────────────────────────────────────────────────

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

        Vec3d interpolatedPos = new Vec3d(
                lerp(prevX, getPosition().getX(), partialTicks),
                lerp(prevY, getPosition().getY(), partialTicks),
                lerp(prevZ, getPosition().getZ(), partialTicks)
        );

        AABB worldAABB = new AABB(
                modelAABB.getMinX() / 16.0, modelAABB.getMinY() / 16.0, modelAABB.getMinZ() / 16.0,
                modelAABB.getMaxX() / 16.0, modelAABB.getMaxY() / 16.0, modelAABB.getMaxZ() / 16.0
        );

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

        worldAABB = worldAABB.offset(getModelTransform().getPosition());
        worldAABB = worldAABB.offset(interpolatedPos);
        return worldAABB;
    }

    // ── Model transform helpers ───────────────────────────────────────────────

    public Identifier getModelId() {
        return this.entityId;
    }

    public Transform getModelTransform() {
        return modelTransform;
    }

    public void setModelPosition(float x, float y, float z) {
        modelTransform.getPosition().set(x, y, z);
    }

    public void setModelPosition(Vec3d pos) {
        modelTransform.getPosition().set(pos.getX(), pos.getY(), pos.getZ());
    }

    public void setModelRotationDegrees(float yaw, float pitch, float roll) {
        modelTransform.getRotation().rotationZYX(Math.toRadians(roll), Math.toRadians(pitch), Math.toRadians(yaw));
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

    // ── Head / body rotation ──────────────────────────────────────────────────

    public void setHeadYaw(float yaw) { this.headYaw = yaw; }
    public void setHeadPitch(float pitch) { this.headPitch = pitch; }
    public float getHeadYaw() { return headYaw; }
    public float getHeadPitch() { return headPitch; }
    public float getBodyYaw() { return bodyYaw; }
    public float getMaxHeadRotation() { return MAX_HEAD_ROTATION; }

    public void setBodyYaw(float yaw) {
        this.bodyYaw = yaw;
        this.getRotation().rotationY((float) Math.toRadians(-yaw));
    }

    // ── Misc ──────────────────────────────────────────────────────────────────

    public float getEyeHeight() { return eyeHeight; }
    public void setEyeHeight(float h) { this.eyeHeight = h; }
    public EntityKey<?> getKey() { return key; }
    public World getWorld() { return world; }
    public int getId() { return id; }
    public UUID getUuid() { return uuid; }
    public Identifier getEntityId() { return entityId; }
}