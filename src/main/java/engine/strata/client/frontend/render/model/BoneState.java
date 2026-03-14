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
 *
 * <p>Visibility cascading: when a parent bone's visibility is set via
 * {@code AnimationProcessor.setBoneVisible()}, the change propagates to all
 * descendants that have not been explicitly overridden this frame.
 * {@link #isExplicitlySet()} tracks whether this bone's visibility was set
 * directly, and is cleared each frame by {@link #reset()}.
 */
public class BoneState {

    private final Vector3f positionOffset;
    private final Vector3f rotationOffset;
    private final Vector3f scaleOffset;

    private boolean isVisible;
    private final boolean defaultVisibility;

    /**
     * True if {@link #setVisible(boolean)} was called directly on this bone this frame.
     * False means the current visibility is either the model default or an inherited value
     * from a parent. Cleared by {@link #reset()}.
     */
    private boolean explicitlySet;

    public BoneState() {
        this(true);
    }

    public BoneState(boolean defaultVisibility) {
        this.positionOffset = new Vector3f(0, 0, 0);
        this.rotationOffset = new Vector3f(0, 0, 0);
        this.scaleOffset = new Vector3f(1, 1, 1);
        this.defaultVisibility = defaultVisibility;
        this.isVisible = defaultVisibility;
        this.explicitlySet = false;
    }

    // Getters

    public Vector3f getPositionOffset() { return positionOffset; }
    public Vector3f getRotationOffset() { return rotationOffset; }
    public Vector3f getScaleOffset() { return scaleOffset; }
    public boolean isVisible() { return isVisible; }
    public boolean getDefaultVisibility() { return defaultVisibility; }

    /**
     * Returns true if visibility was set directly on this bone this frame via
     * {@link #setVisible(boolean)}. Used by the cascade system to avoid overriding
     * an explicit child override when a parent's visibility propagates down.
     */
    public boolean isExplicitlySet() { return explicitlySet; }

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

    /**
     * Sets visibility and marks this bone as explicitly set this frame.
     * Explicit bones are not overridden when a parent's visibility cascades down.
     */
    public void setVisible(boolean visible) {
        this.isVisible = visible;
        this.explicitlySet = true;
    }

    /**
     * Sets visibility without marking this bone as explicitly set.
     * Used internally by the cascade system to propagate a parent's visibility
     * to children that haven't been directly overridden.
     */
    public void setVisibleInherited(boolean visible) {
        this.isVisible = visible;
    }

    // Reset

    /**
     * Resets to identity transforms, restores default visibility, and clears the
     * explicit-set flag. Called at the start of each frame before animations are applied.
     */
    public void reset() {
        this.positionOffset.set(0, 0, 0);
        this.rotationOffset.set(0, 0, 0);
        this.scaleOffset.set(1, 1, 1);
        this.isVisible = this.defaultVisibility;
        this.explicitlySet = false;
    }

    // Utility

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
        copy.explicitlySet = this.explicitlySet;
        return copy;
    }

    @Override
    public String toString() {
        return String.format("BoneState{pos=%s, rot=%s, scale=%s, visible=%b, explicit=%b}",
                positionOffset, rotationOffset, scaleOffset, isVisible, explicitlySet);
    }
}