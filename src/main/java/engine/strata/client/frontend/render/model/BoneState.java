package engine.strata.client.frontend.render.model;

import org.joml.Vector3f;

/**
 * Dynamic per-frame pose state for a single bone.
 * Stores the runtime animation state applied on top of the bone's base transform.
 *
 * <p>Transform application order:
 * <ol>
 *   <li>Move to pivot point</li>
 *   <li>Apply base rotation (from StrataBone, radians) + rotationOffset</li>
 *   <li>Apply scaleOffset</li>
 *   <li>Apply positionOffset</li>
 *   <li>Move back from pivot</li>
 * </ol>
 */
public class BoneState {

    /** Position offset in model-local space, applied after rotation. */
    private final Vector3f positionOffset;

    /** Rotation offset in radians (x=pitch, y=yaw, z=roll), added to the bone's base rotation. */
    private final Vector3f rotationOffset;

    /** Scale multiplier in local space, applied before position offset. */
    private final Vector3f scaleOffset;

    /** Whether this bone should be rendered. Skips the entire subtree when false. */
    private boolean isVisible;

    /** Default visibility from the model file, restored by {@link #reset()}. */
    private final boolean defaultVisibility;

    /** Creates a BoneState with identity transforms and visible=true. */
    public BoneState() {
        this(true);
    }

    /** Creates a BoneState with identity transforms and the given default visibility. */
    public BoneState(boolean defaultVisibility) {
        this.positionOffset = new Vector3f(0, 0, 0);
        this.rotationOffset = new Vector3f(0, 0, 0);
        this.scaleOffset = new Vector3f(1, 1, 1);
        this.defaultVisibility = defaultVisibility;
        this.isVisible = defaultVisibility;
    }

    // Getters

    /** Returns the internal position offset instance — use setters to modify. */
    public Vector3f getPositionOffset() { return positionOffset; }

    /** Returns the internal rotation offset instance in radians — use setters to modify. */
    public Vector3f getRotationOffset() { return rotationOffset; }

    /** Returns the internal scale offset instance — use setters to modify. */
    public Vector3f getScaleOffset() { return scaleOffset; }

    public boolean isVisible() { return isVisible; }
    public boolean getDefaultVisibility() { return defaultVisibility; }

    // Position setters

    public void setPositionOffset(Vector3f offset) { this.positionOffset.set(offset); }
    public void setPositionOffset(float x, float y, float z) { this.positionOffset.set(x, y, z); }
    public void addPositionOffset(Vector3f delta) { this.positionOffset.add(delta); }
    public void addPositionOffset(float dx, float dy, float dz) { this.positionOffset.add(dx, dy, dz); }

    // Rotation setters (all in radians)

    public void setRotationOffset(Vector3f rotation) { this.rotationOffset.set(rotation); }
    public void setRotationOffset(float pitch, float yaw, float roll) { this.rotationOffset.set(pitch, yaw, roll); }

    public void setRotationOffsetDegrees(float pitch, float yaw, float roll) {
        this.rotationOffset.set(
                (float) Math.toRadians(pitch),
                (float) Math.toRadians(yaw),
                (float) Math.toRadians(roll)
        );
    }

    public void addRotationOffset(float pitch, float yaw, float roll) { this.rotationOffset.add(pitch, yaw, roll); }
    public void addRotationOffset(Vector3f delta) { this.rotationOffset.add(delta); }

    // Scale setters

    public void setScaleOffset(Vector3f scale) { this.scaleOffset.set(scale); }
    public void setScaleOffset(float x, float y, float z) { this.scaleOffset.set(x, y, z); }
    public void setScaleOffset(float uniformScale) { this.scaleOffset.set(uniformScale, uniformScale, uniformScale); }
    public void multiplyScaleOffset(Vector3f scale) { this.scaleOffset.mul(scale); }
    public void multiplyScaleOffset(float x, float y, float z) { this.scaleOffset.mul(x, y, z); }

    // Visibility

    public void setVisible(boolean visible) { this.isVisible = visible; }

    // Reset

    /**
     * Resets to identity transforms and restores default visibility.
     * Called at the start of each frame before animations are applied.
     */
    public void reset() {
        this.positionOffset.set(0, 0, 0);
        this.rotationOffset.set(0, 0, 0);
        this.scaleOffset.set(1, 1, 1);
        this.isVisible = this.defaultVisibility;
    }

    // Utility

    /** Returns true if any transform is non-identity. */
    public boolean hasTransforms() {
        return !positionOffset.equals(0, 0, 0)
                || !rotationOffset.equals(0, 0, 0)
                || !scaleOffset.equals(1, 1, 1);
    }

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