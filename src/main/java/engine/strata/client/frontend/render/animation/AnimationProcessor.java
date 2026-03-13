package engine.strata.client.frontend.render.animation;

import engine.strata.client.frontend.render.model.BoneState;
import engine.strata.client.frontend.render.model.StrataBone;
import engine.strata.client.frontend.render.model.StrataModel;
import engine.strata.client.frontend.render.renderer.ModelRenderer;
import org.joml.Vector3f;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

/**
 * Per-entity animation processor that manages bone states and applies animations.
 *
 * <h3>Architecture:</h3>
 * <ul>
 *   <li>Each {@link engine.strata.entity.Entity} owns one AnimationProcessor.</li>
 *   <li>The processor maintains a {@link BoneState} for every bone in the model.</li>
 *   <li>Animations modify bone states each frame.</li>
 *   <li>{@link ModelRenderer} reads bone states during rendering.</li>
 * </ul>
 *
 * <h3>Rotation convention:</h3>
 * <p>All rotation values are <em>Euler angles in radians</em>: {@code x=pitch, y=yaw, z=roll}.
 * Degree helpers are provided for convenience where the source data is in degrees.
 *
 * <h3>Usage Pattern:</h3>
 * <pre>
 * // In Entity.tick() or custom renderer:
 * animProcessor.resetAllBones();
 * animProcessor.applyHeadRotation(entity.headYaw, entity.headPitch);
 * animProcessor.applyWalkingAnimation(entity.walkDistance, partialTicks);
 * </pre>
 *
 * <h3>Thread Safety:</h3>
 * NOT thread-safe. Access only from the render thread.
 */
public class AnimationProcessor {

    private static final Logger LOGGER = LoggerFactory.getLogger("AnimationProcessor");

    // ══════════════════════════════════════════════════════════════════════════
    // STATE
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * Maps bone name → current bone state.
     * Lazily populated as bones are first accessed.
     */
    private final Map<String, BoneState> boneStates;

    /** The model being animated. Used to validate bone names. */
    private StrataModel model;

    // ══════════════════════════════════════════════════════════════════════════
    // CONSTRUCTORS
    // ══════════════════════════════════════════════════════════════════════════

    /** Creates a new processor; call {@link #setModel(StrataModel)} before use. */
    public AnimationProcessor() {
        this.boneStates = new HashMap<>();
    }

    /**
     * Creates a new processor and immediately binds it to {@code model}.
     *
     * @param model The model to animate
     */
    public AnimationProcessor(StrataModel model) {
        this.boneStates = new HashMap<>();
        setModel(model);
    }

    // ══════════════════════════════════════════════════════════════════════════
    // MODEL BINDING
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * Binds this processor to {@code model} and pre-initialises bone states for
     * all bones, carrying over the per-bone default visibility from the model file.
     */
    public void setModel(StrataModel model) {
        this.model = model;

        if (model != null) {
            for (Map.Entry<String, StrataBone> entry : model.getAllBones().entrySet()) {
                // StrataBone.isDefaultHidden() == true  →  BoneState visible = false
                boolean defaultVisibility = !entry.getValue().isDefaultHidden();
                boneStates.put(entry.getKey(), new BoneState(defaultVisibility));
            }
        }
    }

    /** Returns the currently bound model. */
    public StrataModel getModel() {
        return model;
    }

    // ══════════════════════════════════════════════════════════════════════════
    // BONE STATE ACCESS
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * Returns the bone state for {@code boneName}, creating one if absent.
     *
     * @param boneName e.g. "head", "body", "leftArm"
     * @return The bone state (never null)
     */
    public BoneState getBoneState(String boneName) {
        return boneStates.computeIfAbsent(boneName, k -> new BoneState());
    }

    /** Returns {@code true} if a bone state already exists for {@code boneName}. */
    public boolean hasBoneState(String boneName) {
        return boneStates.containsKey(boneName);
    }

    /** Returns all bone states. Used by ModelRenderer during rendering. */
    public Map<String, BoneState> getAllBoneStates() {
        return boneStates;
    }

    // ══════════════════════════════════════════════════════════════════════════
    // RESET / CLEANUP
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * Resets every bone state to identity transforms.
     * Call this at the start of each animation update cycle.
     */
    public void resetAllBones() {
        for (BoneState state : boneStates.values()) {
            state.reset();
        }
    }

    /** Resets a single bone to identity transforms. */
    public void resetBone(String boneName) {
        BoneState state = boneStates.get(boneName);
        if (state != null) state.reset();
    }

    /** Clears all bone states. Use when changing models or destroying the entity. */
    public void clearAllStates() {
        boneStates.clear();
    }

    // ══════════════════════════════════════════════════════════════════════════
    // CONVENIENCE SETTERS
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * Sets the rotation offset for {@code boneName} in radians.
     *
     * @param pitch X-axis rotation (radians)
     * @param yaw   Y-axis rotation (radians)
     * @param roll  Z-axis rotation (radians)
     */
    public void setBoneRotation(String boneName, float pitch, float yaw, float roll) {
        getBoneState(boneName).setRotationOffset(pitch, yaw, roll);
    }

    /**
     * Sets the rotation offset for {@code boneName} from a Vector3f in radians.
     *
     * @param rotation x=pitch, y=yaw, z=roll (radians)
     */
    public void setBoneRotation(String boneName, Vector3f rotation) {
        getBoneState(boneName).setRotationOffset(rotation);
    }

    /**
     * Sets the rotation offset for {@code boneName} from Euler angles in degrees.
     *
     * @param pitch X-axis rotation (degrees)
     * @param yaw   Y-axis rotation (degrees)
     * @param roll  Z-axis rotation (degrees)
     */
    public void setBoneRotationDegrees(String boneName, float pitch, float yaw, float roll) {
        getBoneState(boneName).setRotationOffsetDegrees(pitch, yaw, roll);
    }

    /** Sets the position offset for {@code boneName}. */
    public void setBonePosition(String boneName, Vector3f position) {
        getBoneState(boneName).setPositionOffset(position);
    }

    /** Sets the position offset for {@code boneName}. */
    public void setBonePosition(String boneName, float x, float y, float z) {
        getBoneState(boneName).setPositionOffset(x, y, z);
    }

    /** Sets the scale offset for {@code boneName}. */
    public void setBoneScale(String boneName, Vector3f scale) {
        getBoneState(boneName).setScaleOffset(scale);
    }

    /** Sets the scale offset for {@code boneName}. */
    public void setBoneScale(String boneName, float x, float y, float z) {
        getBoneState(boneName).setScaleOffset(x, y, z);
    }

    /** Sets uniform scale for {@code boneName}. */
    public void setBoneScale(String boneName, float uniformScale) {
        getBoneState(boneName).setScaleOffset(uniformScale);
    }

    /** Sets the visibility for {@code boneName}. */
    public void setBoneVisible(String boneName, boolean visible) {
        getBoneState(boneName).setVisible(visible);
    }

    // ══════════════════════════════════════════════════════════════════════════
    // COMMON ANIMATION UTILITIES
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * Applies head rotation from entity yaw/pitch (in degrees) to the "head" bone.
     *
     * @param headYaw   Horizontal rotation in degrees (Y-axis)
     * @param headPitch Vertical rotation in degrees (X-axis)
     */
    public void applyHeadRotation(float headYaw, float headPitch) {
        applyHeadRotation("head", headYaw, headPitch);
    }

    /**
     * Applies head rotation (in degrees) to a custom bone.
     *
     * @param boneName  Target bone (e.g. "head", "neck")
     * @param headYaw   Horizontal rotation in degrees (Y-axis)
     * @param headPitch Vertical rotation in degrees (X-axis)
     */
    public void applyHeadRotation(String boneName, float headYaw, float headPitch) {
        float pitchRad = (float) Math.toRadians(headPitch);
        float yawRad   = (float) Math.toRadians(headYaw);
        getBoneState(boneName).setRotationOffset(pitchRad, yawRad, 0);
    }

    /**
     * Applies a simple walking animation to legs and arms using sine waves.
     *
     * <h3>Example:</h3>
     * <pre>
     * float walkDistance = entity.distanceWalkedModified;
     * float walkSpeed    = entity.prevLimbSwingAmount
     *                    + (entity.limbSwingAmount - entity.prevLimbSwingAmount) * partialTicks;
     * animProcessor.applyWalkingAnimation(walkDistance, walkSpeed);
     * </pre>
     *
     * @param walkDistance Total distance walked (accumulates over time)
     * @param walkSpeed    Current walk speed (0.0 = standing, 1.0 = running)
     */
    public void applyWalkingAnimation(float walkDistance, float walkSpeed) {
        float leftLegRot  = (float) Math.sin(walkDistance * 0.6662f) * 1.4f * walkSpeed;
        float rightLegRot = (float) Math.sin(walkDistance * 0.6662f + Math.PI) * 1.4f * walkSpeed;
        float leftArmRot  = (float) Math.sin(walkDistance * 0.6662f + Math.PI) * 1.4f * walkSpeed;
        float rightArmRot = (float) Math.sin(walkDistance * 0.6662f) * 1.4f * walkSpeed;

        setBoneRotation("leftLeg",  leftLegRot,  0, 0);
        setBoneRotation("rightLeg", rightLegRot, 0, 0);
        setBoneRotation("leftArm",  leftArmRot,  0, 0);
        setBoneRotation("rightArm", rightArmRot, 0, 0);
    }

    /**
     * Applies a breathing animation to the "body" bone.
     *
     * @param time      Current world time or entity age
     * @param intensity Breathing intensity (0.0 – 1.0)
     */
    public void applyBreathingAnimation(float time, float intensity) {
        float breathScale = 1.0f + (float) Math.sin(time * 0.1f) * 0.02f * intensity;
        setBoneScale("body", 1.0f, breathScale, 1.0f);
    }

    /**
     * Applies a hurt animation (shake/rotation effect) to the "body" bone.
     *
     * @param hurtTime    Time since last hurt (counts down from maxHurtTime)
     * @param maxHurtTime Maximum hurt time
     */
    public void applyHurtAnimation(int hurtTime, int maxHurtTime) {
        if (hurtTime <= 0) return;
        float progress    = (float) hurtTime / maxHurtTime;
        float shakeAmount = (float) Math.sin(hurtTime * 0.8f) * progress * 0.1f;
        setBoneRotation("body", 0, 0, shakeAmount);
    }

    /**
     * Linearly interpolates the rotation of {@code boneName} between two Euler-radian
     * values and applies the result.
     *
     * @param boneName Target bone
     * @param from     Starting rotation (radians)
     * @param to       Target rotation (radians)
     * @param progress Interpolation factor (0.0 – 1.0)
     */
    public void lerpBoneRotation(String boneName, Vector3f from, Vector3f to, float progress) {
        Vector3f result = new Vector3f(from).lerp(to, progress);
        getBoneState(boneName).setRotationOffset(result);
    }

    // ══════════════════════════════════════════════════════════════════════════
    // DEBUGGING
    // ══════════════════════════════════════════════════════════════════════════

    /** Logs the current state of all non-identity bones. */
    public void debugPrintStates() {
        LOGGER.info("AnimationProcessor bone states:");
        for (Map.Entry<String, BoneState> entry : boneStates.entrySet()) {
            BoneState state = entry.getValue();
            if (state.hasTransforms() || !state.isVisible()) {
                LOGGER.info("  {}: {}", entry.getKey(), state);
            }
        }
    }

    /** Returns a short summary string (total bones and number with active transforms). */
    public String getSummary() {
        long activeCount = boneStates.values().stream()
                .filter(s -> s.hasTransforms() || !s.isVisible())
                .count();
        return String.format("AnimationProcessor{bones=%d, active=%d}",
                boneStates.size(), activeCount);
    }
}