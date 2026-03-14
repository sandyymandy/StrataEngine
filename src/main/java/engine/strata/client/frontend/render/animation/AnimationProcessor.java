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
 * <p>Each entity owns one AnimationProcessor which maintains a {@link BoneState}
 * for every bone. Animations modify bone states each frame and {@link ModelRenderer}
 * reads them during rendering.
 *
 * <p>All rotation values are Euler angles in radians (x=pitch, y=yaw, z=roll).
 * Degree helpers are provided where source data is in degrees.
 *
 * <p>Not thread-safe. Access only from the render thread.
 */
public class AnimationProcessor {

    private static final Logger LOGGER = LoggerFactory.getLogger("AnimationProcessor");

    private final Map<String, BoneState> boneStates;
    private StrataModel model;

    public AnimationProcessor() {
        this.boneStates = new HashMap<>();
    }

    public AnimationProcessor(StrataModel model) {
        this.boneStates = new HashMap<>();
        setModel(model);
    }

    // Model binding

    /**
     * Binds this processor to a model and pre-initialises bone states for all bones,
     * seeding each with the correct default visibility from the model file.
     */
    public void setModel(StrataModel model) {
        this.model = model;
        if (model != null) {
            for (Map.Entry<String, StrataBone> entry : model.getAllBones().entrySet()) {
                boolean defaultVisibility = !entry.getValue().isDefaultHidden();
                boneStates.put(entry.getKey(), new BoneState(defaultVisibility));
            }
        }
    }

    public StrataModel getModel() { return model; }

    // Bone state access

    /** Returns the bone state for {@code boneName}, creating one if absent. */
    public BoneState getBoneState(String boneName) {
        return boneStates.computeIfAbsent(boneName, k -> new BoneState());
    }

    public boolean hasBoneState(String boneName) { return boneStates.containsKey(boneName); }

    /** Returns all bone states. Used by ModelRenderer during rendering. */
    public Map<String, BoneState> getAllBoneStates() { return boneStates; }

    // Reset

    /** Resets every bone state to identity transforms. Call at the start of each animation cycle. */
    public void resetAllBones() {
        for (BoneState state : boneStates.values()) {
            state.reset();
        }
    }

    public void resetBone(String boneName) {
        BoneState state = boneStates.get(boneName);
        if (state != null) state.reset();
    }

    public void clearAllStates() { boneStates.clear(); }

    // Convenience setters

    public void setBoneRotation(String boneName, float pitch, float yaw, float roll) {
        getBoneState(boneName).setRotationOffset(pitch, yaw, roll);
    }

    public void setBoneRotation(String boneName, Vector3f rotation) {
        getBoneState(boneName).setRotationOffset(rotation);
    }

    public void setBoneRotationDegrees(String boneName, float pitch, float yaw, float roll) {
        getBoneState(boneName).setRotationOffsetDegrees(pitch, yaw, roll);
    }

    public void setBonePosition(String boneName, Vector3f position) {
        getBoneState(boneName).setPositionOffset(position);
    }

    public void setBonePosition(String boneName, float x, float y, float z) {
        getBoneState(boneName).setPositionOffset(x, y, z);
    }

    public void setBoneScale(String boneName, Vector3f scale) {
        getBoneState(boneName).setScaleOffset(scale);
    }

    public void setBoneScale(String boneName, float x, float y, float z) {
        getBoneState(boneName).setScaleOffset(x, y, z);
    }

    public void setBoneScale(String boneName, float uniformScale) {
        getBoneState(boneName).setScaleOffset(uniformScale);
    }

    public void setBoneVisible(String boneName, boolean visible) {
        getBoneState(boneName).setVisible(visible);
    }

    // Open/closed morph states
    //
    // Each morph pair has a "closed" bone (visible by default) and an "open" bone
    // (hidden by default). Calling setVaginaOpen(true) swaps which one is visible.
    // Wire these up to your animation event system when ready.

    /**
     * Sets the vagina morph state.
     * {@code true} shows {@code vaginaOpen} and hides {@code vaginaClosed}.
     * {@code false} restores the default (closed).
     */
    public void setVaginaOpen(boolean open) {
        setMorphPair("vaginaClosed", "vaginaOpen", open);
    }

    /**
     * Sets the anus morph state.
     * {@code true} shows {@code anusOpen} and hides {@code anusClosed}.
     * {@code false} restores the default (closed).
     */
    public void setAnusOpen(boolean open) {
        setMorphPair("anusClosed", "anusOpen", open);
    }

    /**
     * Generic morph-pair toggle. Hides one bone and shows the other.
     * Use this for any future paired open/closed bones.
     *
     * @param closedBone Bone name that is visible in the closed/default state
     * @param openBone   Bone name that is visible in the open state
     * @param open       {@code true} to switch to the open state
     */
    public void setMorphPair(String closedBone, String openBone, boolean open) {
        getBoneState(closedBone).setVisible(!open);
        getBoneState(openBone).setVisible(open);
    }

    // Common animation utilities

    /** Applies head yaw/pitch (degrees) to the "head" bone. */
    public void applyHeadRotation(float headYaw, float headPitch) {
        applyHeadRotation("head", headYaw, headPitch);
    }

    /** Applies head yaw/pitch (degrees) to a custom bone. */
    public void applyHeadRotation(String boneName, float headYaw, float headPitch) {
        getBoneState(boneName).setRotationOffset(
                (float) Math.toRadians(headPitch),
                (float) Math.toRadians(headYaw),
                0
        );
    }

    /**
     * Applies a simple walking animation to legs and arms using sine waves.
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
     * Applies a breathing scale animation to the "body" bone.
     *
     * @param time      Current world time or entity age
     * @param intensity Breathing intensity (0.0 – 1.0)
     */
    public void applyBreathingAnimation(float time, float intensity) {
        float breathScale = 1.0f + (float) Math.sin(time * 0.1f) * 0.02f * intensity;
        setBoneScale("body", 1.0f, breathScale, 1.0f);
    }

    /**
     * Applies a hurt shake to the "body" bone.
     *
     * @param hurtTime    Time since last hurt (counts down)
     * @param maxHurtTime Maximum hurt time
     */
    public void applyHurtAnimation(int hurtTime, int maxHurtTime) {
        if (hurtTime <= 0) return;
        float progress = (float) hurtTime / maxHurtTime;
        float shake = (float) Math.sin(hurtTime * 0.8f) * progress * 0.1f;
        setBoneRotation("body", 0, 0, shake);
    }

    /**
     * Linearly interpolates the rotation of {@code boneName} between two Euler-radian values.
     *
     * @param from     Starting rotation (radians)
     * @param to       Target rotation (radians)
     * @param progress Interpolation factor (0.0 – 1.0)
     */
    public void lerpBoneRotation(String boneName, Vector3f from, Vector3f to, float progress) {
        getBoneState(boneName).setRotationOffset(new Vector3f(from).lerp(to, progress));
    }

    // Debug

    public void debugPrintStates() {
        LOGGER.info("AnimationProcessor bone states:");
        for (Map.Entry<String, BoneState> entry : boneStates.entrySet()) {
            BoneState state = entry.getValue();
            if (state.hasTransforms() || !state.isVisible()) {
                LOGGER.info("  {}: {}", entry.getKey(), state);
            }
        }
    }

    public String getSummary() {
        long active = boneStates.values().stream()
                .filter(s -> s.hasTransforms() || !s.isVisible())
                .count();
        return String.format("AnimationProcessor{bones=%d, active=%d}", boneStates.size(), active);
    }
}