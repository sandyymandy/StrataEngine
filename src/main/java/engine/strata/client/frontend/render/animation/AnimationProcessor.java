package engine.strata.client.frontend.render.animation;

import engine.strata.client.frontend.render.model.BoneState;
import engine.strata.client.frontend.render.model.StrataBone;
import engine.strata.client.frontend.render.model.StrataModel;
import org.joml.Vector3f;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

/**
 * Per-entity animation processor that manages bone states and applies animations.
 *
 * <p>Each entity owns one AnimationProcessor which maintains a {@link BoneState}
 * reads them during rendering.
 *
 * <p>All rotation values are Euler angles in radians (x=pitch, y=yaw, z=roll).
 * Degree helpers are provided where source data is in degrees.
 *
 * <h3>Visibility cascading:</h3>
 * <p>Calling {@link #setBoneVisible(String, boolean)} propagates the visibility
 * to all descendant bones that have not been explicitly overridden this frame.
 * This means you only need to call {@code setBoneVisible("penis", true)} and
 * {@code shaft}, {@code tip}, {@code ball}, {@code texturing} will all update
 * automatically. To override a specific child, call {@code setBoneVisible} on it
 * directly after setting the parent — it will be marked explicit and the parent
 * cascade won't touch it again.
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

    public BoneState getBoneState(String boneName) {
        return boneStates.computeIfAbsent(boneName, k -> new BoneState());
    }

    public boolean hasBoneState(String boneName) { return boneStates.containsKey(boneName); }
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

    /**
     * Sets the visibility of a bone and cascades the change to all its descendants.
     *
     * <p>Descendants that have already been explicitly set this frame (via a direct
     * call to this method) are not overridden. This means call order matters:
     * set the parent first, then override specific children afterwards.
     *
     * <pre>
     * // Show the whole penis group in one call:
     * anim.setBoneVisible("penis", true);
     *
     * // Override just one child after the parent cascade:
     * anim.setBoneVisible("tip", false); // only tip stays hidden
     * </pre>
     */
    public void setBoneVisible(String boneName, boolean visible) {
        getBoneState(boneName).setVisible(visible);
        if (model != null) {
            StrataBone bone = model.getBone(boneName);
            if (bone != null) {
                cascadeVisibility(bone, visible);
            }
        }
    }

    /**
     * Recursively propagates {@code visible} to all descendants that have not
     * been explicitly set this frame.
     */
    private void cascadeVisibility(StrataBone bone, boolean visible) {
        for (StrataBone child : bone.getChildren()) {
            BoneState childState = getBoneState(child.getName());
            if (!childState.isExplicitlySet()) {
                childState.setVisibleInherited(visible);
                cascadeVisibility(child, visible);
            }
            // If explicitly set, skip this child but still recurse — a grandchild
            // that is NOT explicitly set should still inherit from this child's
            // own explicit value, not from the grandparent's cascade.
        }
    }

    // Open/closed morph states

    /**
     * Sets the vagina morph state.
     * {@code true} shows {@code vaginaOpen} and hides {@code vaginaClosed}.
     */
    public void setVaginaOpen(boolean open) {
        setMorphPair("vaginaClosed", "vaginaOpen", open);
    }

    /**
     * Sets the anus morph state.
     * {@code true} shows {@code anusOpen} and hides {@code anusClosed}.
     */
    public void setAnusOpen(boolean open) {
        setMorphPair("anusClosed", "anusOpen", open);
    }

    /**
     * Generic morph-pair toggle. Hides one bone and shows the other.
     * Cascades to descendants of each bone automatically.
     *
     * @param closedBone Bone visible in the default/closed state
     * @param openBone   Bone visible in the open state
     * @param open       {@code true} to switch to the open state
     */
    public void setMorphPair(String closedBone, String openBone, boolean open) {
        setBoneVisible(closedBone, !open);
        setBoneVisible(openBone, open);
    }

    // Common animation utilities

    public void applyHeadRotation(float headYaw, float headPitch) {
        applyHeadRotation("head", headYaw, headPitch);
    }

    public void applyHeadRotation(String boneName, float headYaw, float headPitch) {
        getBoneState(boneName).setRotationOffset(
                (float) Math.toRadians(headPitch),
                (float) Math.toRadians(headYaw),
                0
        );
    }

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

    public void applyBreathingAnimation(float time, float intensity) {
        float breathScale = 1.0f + (float) Math.sin(time * 0.1f) * 0.02f * intensity;
        setBoneScale("body", 1.0f, breathScale, 1.0f);
    }

    public void applyHurtAnimation(int hurtTime, int maxHurtTime) {
        if (hurtTime <= 0) return;
        float progress = (float) hurtTime / maxHurtTime;
        float shake = (float) Math.sin(hurtTime * 0.8f) * progress * 0.1f;
        setBoneRotation("body", 0, 0, shake);
    }

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