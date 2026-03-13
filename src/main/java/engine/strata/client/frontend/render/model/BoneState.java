package engine.strata.client.frontend.render.model;

import org.joml.Vector3f;

/**
 * Dynamic per-frame pose state for a single bone.
 *
 * <h3>Design Philosophy:</h3>
 * <p>This class stores the runtime animation state applied ON TOP OF the bone's
 * base transform from the model file. Each entity owns an AnimationProcessor
 * which maintains a {@code Map<String, BoneState>} for every bone.
 *
 * <h3>Transform Application Order:</h3>
 * <pre>
 * 1. Move to pivot point
 * 2. Apply base rotation (from StrataBone template, in radians)
 * 3. Add rotationOffset (from this BoneState, in radians)
 * 4. Apply scaleOffset
 * 5. Apply positionOffset
 * 6. Move back from pivot
 * </pre>
 *
 * <h3>Usage Example:</h3>
 * <pre>
 * BoneState headState = animProcessor.getBoneState("head");
 *
 * // Look-at logic — converted from entity yaw/pitch to radians
 * headState.setRotationOffset(pitchRad, yawRad, 0);
 *
 * // Scale effect (e.g. breathing animation)
 * headState.setScaleOffset(1.0f, 1.02f, 1.0f);
 *
 * // Bob effect (walking animation)
 * headState.setPositionOffset(0, bobAmount, 0);
 * </pre>
 */
public class BoneState {

    // ══════════════════════════════════════════════════════════════════════════
    // TRANSFORM OFFSETS
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * Position offset in model-local space.
     * Applied AFTER rotation, so (1,0,0) moves along the bone's local X-axis.
     */
    private final Vector3f positionOffset;

    /**
     * Rotation offset in <em>radians</em>: {@code x=pitch, y=yaw, z=roll}.
     * Added to the bone's base rotation each frame.
     * Use (0,0,0) for no additional rotation.
     */
    private final Vector3f rotationOffset;

    /**
     * Scale multiplier in local space. Applied BEFORE position offset.
     * Use (1,1,1) for no scaling.
     */
    private final Vector3f scaleOffset;

    // ══════════════════════════════════════════════════════════════════════════
    // VISIBILITY
    // ══════════════════════════════════════════════════════════════════════════

    /** Whether this bone should be rendered. Skips the entire subtree when false. */
    private boolean isVisible;

    /** Default visibility from the model file; restored by {@link #reset()}. */
    private final boolean defaultVisibility;

    // ══════════════════════════════════════════════════════════════════════════
    // CONSTRUCTORS
    // ══════════════════════════════════════════════════════════════════════════

    /** Creates a BoneState with identity transforms and visible=true. */
    public BoneState() {
        this(true);
    }

    /**
     * Creates a BoneState with identity transforms.
     *
     * @param defaultVisibility Initial visibility (from model file)
     */
    public BoneState(boolean defaultVisibility) {
        this.positionOffset    = new Vector3f(0, 0, 0);
        this.rotationOffset    = new Vector3f(0, 0, 0);
        this.scaleOffset       = new Vector3f(1, 1, 1);
        this.defaultVisibility = defaultVisibility;
        this.isVisible         = defaultVisibility;
    }

    // ══════════════════════════════════════════════════════════════════════════
    // GETTERS
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * Returns the position offset.
     * <strong>Returns the internal instance — use setters to modify.</strong>
     */
    public Vector3f getPositionOffset() { return positionOffset; }

    /**
     * Returns the rotation offset in radians (x=pitch, y=yaw, z=roll).
     * <strong>Returns the internal instance — use setters to modify.</strong>
     */
    public Vector3f getRotationOffset() { return rotationOffset; }

    /**
     * Returns the scale offset.
     * <strong>Returns the internal instance — use setters to modify.</strong>
     */
    public Vector3f getScaleOffset() { return scaleOffset; }

    /** Returns {@code true} if this bone should be rendered. */
    public boolean isVisible() { return isVisible; }

    /** Returns the default visibility from the model file. */
    public boolean getDefaultVisibility() { return defaultVisibility; }

    // ══════════════════════════════════════════════════════════════════════════
    // SETTERS — POSITION
    // ══════════════════════════════════════════════════════════════════════════

    /** Replaces the position offset. */
    public void setPositionOffset(Vector3f offset) {
        this.positionOffset.set(offset);
    }

    /** Replaces the position offset. */
    public void setPositionOffset(float x, float y, float z) {
        this.positionOffset.set(x, y, z);
    }

    /** Accumulates onto the position offset. */
    public void addPositionOffset(Vector3f delta) {
        this.positionOffset.add(delta);
    }

    /** Accumulates onto the position offset. */
    public void addPositionOffset(float dx, float dy, float dz) {
        this.positionOffset.add(dx, dy, dz);
    }

    // ══════════════════════════════════════════════════════════════════════════
    // SETTERS — ROTATION (all in radians)
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * Replaces the rotation offset.
     *
     * @param rotation Vector3f where x=pitch, y=yaw, z=roll (radians)
     */
    public void setRotationOffset(Vector3f rotation) {
        this.rotationOffset.set(rotation);
    }

    /**
     * Replaces the rotation offset.
     *
     * @param pitch Rotation around X-axis (radians)
     * @param yaw   Rotation around Y-axis (radians)
     * @param roll  Rotation around Z-axis (radians)
     */
    public void setRotationOffset(float pitch, float yaw, float roll) {
        this.rotationOffset.set(pitch, yaw, roll);
    }

    /**
     * Replaces the rotation offset, converting from degrees to radians.
     *
     * @param pitch Rotation around X-axis (degrees)
     * @param yaw   Rotation around Y-axis (degrees)
     * @param roll  Rotation around Z-axis (degrees)
     */
    public void setRotationOffsetDegrees(float pitch, float yaw, float roll) {
        this.rotationOffset.set(
                (float) Math.toRadians(pitch),
                (float) Math.toRadians(yaw),
                (float) Math.toRadians(roll)
        );
    }

    /**
     * Accumulates onto the rotation offset (adds radians component-wise).
     *
     * @param pitch Δ around X-axis (radians)
     * @param yaw   Δ around Y-axis (radians)
     * @param roll  Δ around Z-axis (radians)
     */
    public void addRotationOffset(float pitch, float yaw, float roll) {
        this.rotationOffset.add(pitch, yaw, roll);
    }

    /** Accumulates onto the rotation offset (adds radians component-wise). */
    public void addRotationOffset(Vector3f delta) {
        this.rotationOffset.add(delta);
    }

    // ══════════════════════════════════════════════════════════════════════════
    // SETTERS — SCALE
    // ══════════════════════════════════════════════════════════════════════════

    /** Replaces the scale offset. */
    public void setScaleOffset(Vector3f scale) {
        this.scaleOffset.set(scale);
    }

    /** Replaces the scale offset. */
    public void setScaleOffset(float x, float y, float z) {
        this.scaleOffset.set(x, y, z);
    }

    /** Sets uniform scale on all three axes. */
    public void setScaleOffset(float uniformScale) {
        this.scaleOffset.set(uniformScale, uniformScale, uniformScale);
    }

    /** Multiplies the current scale component-wise. */
    public void multiplyScaleOffset(Vector3f scale) {
        this.scaleOffset.mul(scale);
    }

    /** Multiplies the current scale component-wise. */
    public void multiplyScaleOffset(float x, float y, float z) {
        this.scaleOffset.mul(x, y, z);
    }

    // ══════════════════════════════════════════════════════════════════════════
    // VISIBILITY
    // ══════════════════════════════════════════════════════════════════════════

    /** Sets whether this bone should be rendered. */
    public void setVisible(boolean visible) {
        this.isVisible = visible;
    }

    // ══════════════════════════════════════════════════════════════════════════
    // RESET
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * Resets this bone state to identity transforms.
     * Visibility is restored to the model-file default.
     * Call this at the start of each frame before applying animations.
     */
    public void reset() {
        this.positionOffset.set(0, 0, 0);
        this.rotationOffset.set(0, 0, 0);
        this.scaleOffset.set(1, 1, 1);
        this.isVisible = this.defaultVisibility;
    }

    // ══════════════════════════════════════════════════════════════════════════
    // UTILITY
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * Returns {@code true} if any transform is non-identity.
     * Can be used to skip processing bones with no changes.
     */
    public boolean hasTransforms() {
        return !positionOffset.equals(0, 0, 0)
                || !rotationOffset.equals(0, 0, 0)
                || !scaleOffset.equals(1, 1, 1);
    }

    /** Creates a deep copy of this BoneState. */
    public BoneState copy() {
        BoneState copy = new BoneState(this.defaultVisibility);
        copy.positionOffset.set(this.positionOffset);
        copy.rotationOffset.set(this.rotationOffset);
        copy.scaleOffset.set(this.scaleOffset);
        copy.isVisible = this.isVisible;
        return copy;
    }

    @Override
    public String toString() {
        return String.format("BoneState{pos=%s, rot=%s, scale=%s, visible=%b}",
                positionOffset, rotationOffset, scaleOffset, isVisible);
    }
}