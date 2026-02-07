package engine.strata.client.render.model;

import engine.strata.client.render.animation.core.BoneSnapshot;
import org.joml.Matrix4f;
import org.joml.Vector3d;
import org.joml.Vector3f;
import org.joml.Vector4f;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Enhanced bone system inspired by GeckoLib.
 * Supports visibility control, matrix tracking, and advanced animation.
 *
 * <p><b>FIXED ISSUES:</b>
 * <ul>
 *   <li>Removed duplicate setPosition() methods</li>
 *   <li>Renamed animation setters for clarity (setAnimRotation, setAnimPosition, setAnimScale)</li>
 *   <li>Separated position override from animation position</li>
 * </ul>
 */
public class StrataBone {
    // Core properties (immutable)
    private final String name;
    private final StrataBone parent;
    private final Vector3f pivot;
    private final Vector3f rotation;
    private final List<String> meshIds;
    private final List<StrataBone> children;

    // Visibility control
    private boolean hidden = false;
    private boolean childrenHidden = false;

    // Runtime animation properties
    private final Vector3f animRotation = new Vector3f(0, 0, 0);
    private final Vector3f animTranslation = new Vector3f(0, 0, 0);
    private final Vector3f animScale = new Vector3f(1, 1, 1);

    // Position override (for dynamic positioning)
    private final Vector3f positionOverride = new Vector3f(0, 0, 0);
    private boolean hasPositionOverride = false;

    // Change tracking (for optimization)
    private boolean positionChanged = false;
    private boolean rotationChanged = false;
    private boolean scaleChanged = false;

    // Matrix tracking (for advanced features like getting world position)
    private boolean trackingMatrices = false;
    private final Matrix4f modelSpaceMatrix = new Matrix4f().identity();
    private final Matrix4f localSpaceMatrix = new Matrix4f().identity();
    private final Matrix4f worldSpaceMatrix = new Matrix4f().identity();

    // Initial snapshot (for resetting animations)
    private BoneSnapshot initialSnapshot;

    public StrataBone(String name, StrataBone parent, Vector3f pivot, Vector3f rotation, List<String> meshIds) {
        this.name = name;
        this.parent = parent;
        this.pivot = new Vector3f(pivot); // Copy to prevent external modification
        this.rotation = new Vector3f(rotation);
        this.meshIds = new ArrayList<>(meshIds);
        this.children = new ArrayList<>();
    }

    // ============================================================================
    // Core Accessors
    // ============================================================================

    public String getName() {
        return name;
    }

    public StrataBone getParent() {
        return parent;
    }

    public Vector3f getPivot() {
        return pivot;
    }

    public Vector3f getRotation() {
        return rotation;
    }

    public List<String> getMeshIds() {
        return meshIds;
    }

    public List<StrataBone> getChildren() {
        return children;
    }

    public void addChild(StrataBone child) {
        children.add(child);
    }

    // ============================================================================
    // Visibility Control (GeckoLib feature)
    // ============================================================================

    /**
     * Checks if this bone should be rendered.
     */
    public boolean isHidden() {
        return hidden;
    }

    /**
     * Sets whether this bone is hidden.
     * If hidden, the bone and its meshes won't be rendered.
     */
    public void setHidden(boolean hidden) {
        this.hidden = hidden;

        // Optionally hide all children too
        if (hidden) {
            setChildrenHidden(true);
        }
    }

    /**
     * Checks if children should be hidden.
     */
    public boolean isHidingChildren() {
        return childrenHidden;
    }

    /**
     * Sets whether children of this bone should be hidden.
     */
    public void setChildrenHidden(boolean hideChildren) {
        this.childrenHidden = hideChildren;
    }

    /**
     * Checks if this bone should actually render based on parent visibility.
     */
    public boolean shouldRender() {
        if (hidden) {
            return false;
        }

        // Check if any parent is hiding children
        StrataBone current = parent;
        while (current != null) {
            if (current.isHidingChildren()) {
                return false;
            }
            current = current.getParent();
        }

        return true;
    }

    // ============================================================================
    // Animation Properties
    // ============================================================================

    public Vector3f getAnimRotation() {
        return animRotation;
    }

    public Vector3f getAnimTranslation() {
        return animTranslation;
    }

    public Vector3f getAnimScale() {
        return animScale;
    }

    public void setAnimRotation(float x, float y, float z) {
        animRotation.set(x, y, z);
        markRotationAsChanged();
    }

    public void setAnimTranslation(float x, float y, float z) {
        animTranslation.set(x, y, z);
        markPositionAsChanged();
    }

    public void setAnimScale(float x, float y, float z) {
        animScale.set(x, y, z);
        markScaleAsChanged();
    }

    /**
     * Updates rotation incrementally (adds to current rotation).
     */
    public void addRotation(float x, float y, float z) {
        animRotation.add(x, y, z);
        markRotationAsChanged();
    }

    /**
     * Updates position incrementally.
     */
    public void addTranslation(float x, float y, float z) {
        animTranslation.add(x, y, z);
        markPositionAsChanged();
    }

    /**
     * Multiplies scale.
     */
    public void multiplyScale(float x, float y, float z) {
        animScale.mul(x, y, z);
        markScaleAsChanged();
    }

    // ============================================================================
    // Position Override (GeckoLib feature)
    // ============================================================================

    /**
     * Sets an absolute position override for this bone.
     * Useful for IK (inverse kinematics) or precise positioning.
     */
    public void setPositionOverride(float x, float y, float z) {
        positionOverride.set(x, y, z);
        hasPositionOverride = true;
        markPositionAsChanged();
    }

    /**
     * Sets an absolute position override for this bone (convenience overload).
     *
     * @param position The absolute position to set
     */
    public void setPositionOverride(Vector3f position) {
        setPositionOverride(position.x, position.y, position.z);
    }

    /**
     * Gets the position override if set.
     */
    public Vector3f getPositionOverride() {
        return hasPositionOverride ? positionOverride : null;
    }

    /**
     * Clears the position override.
     */
    public void clearPositionOverride() {
        hasPositionOverride = false;
        markPositionAsChanged();
    }

    // ============================================================================
    // Animation Transform Setters (used by AnimationController)
    // ============================================================================

    /**
     * Sets the animation rotation vector.
     * This modifies the animated rotation, not the base model rotation.
     *
     * @param rotation The new rotation vector (in radians)
     */
    public void setAnimRotation(Vector3f rotation) {
        this.animRotation.set(rotation);
        markRotationAsChanged();
    }

    /**
     * Sets the animation translation vector.
     * This modifies the animated position, not the base pivot.
     *
     * @param position The new position vector
     */
    public void setAnimPosition(Vector3f position) {
        this.animTranslation.set(position);
        markPositionAsChanged();
    }

    /**
     * Sets the animation scale vector.
     * This modifies the animated scale multiplier.
     *
     * @param scale The new scale vector
     */
    public void setAnimScale(Vector3f scale) {
        this.animScale.set(scale);
        markScaleAsChanged();
    }

    /**
     * Gets current rotation including both model and animation rotation.
     *
     * @return Combined rotation vector (new instance)
     */
    public Vector3f getCurrentRotation() {
        return new Vector3f(rotation).add(animRotation);
    }

    /**
     * Gets current position including animation translation.
     * Respects position override if set.
     *
     * @return Combined position vector (new instance)
     */
    public Vector3f getCurrentPosition() {
        if (hasPositionOverride) {
            return new Vector3f(positionOverride);
        }
        return new Vector3f(animTranslation);
    }

    /**
     * Gets current scale from animation.
     *
     * @return Scale vector (new instance)
     */
    public Vector3f getCurrentScale() {
        return new Vector3f(animScale);
    }

    // ============================================================================
    // Change Tracking (for optimization)
    // ============================================================================

    public boolean hasPositionChanged() {
        return positionChanged;
    }

    public boolean hasRotationChanged() {
        return rotationChanged;
    }

    public boolean hasScaleChanged() {
        return scaleChanged;
    }

    public void markPositionAsChanged() {
        this.positionChanged = true;
    }

    public void markRotationAsChanged() {
        this.rotationChanged = true;
    }

    public void markScaleAsChanged() {
        this.scaleChanged = true;
    }

    /**
     * Resets all change flags (call after processing animations).
     */
    public void resetStateChanges() {
        this.positionChanged = false;
        this.rotationChanged = false;
        this.scaleChanged = false;
    }

    // ============================================================================
    // Matrix Tracking (GeckoLib feature)
    // ============================================================================

    public boolean isTrackingMatrices() {
        return trackingMatrices;
    }

    public void setTrackingMatrices(boolean tracking) {
        this.trackingMatrices = tracking;
    }

    /**
     * Gets the model-space transformation matrix.
     * Automatically enables matrix tracking.
     */
    public Matrix4f getModelSpaceMatrix() {
        setTrackingMatrices(true);
        return modelSpaceMatrix;
    }

    public void setModelSpaceMatrix(Matrix4f matrix) {
        this.modelSpaceMatrix.set(matrix);
    }

    /**
     * Gets the local-space transformation matrix.
     */
    public Matrix4f getLocalSpaceMatrix() {
        setTrackingMatrices(true);
        return localSpaceMatrix;
    }

    public void setLocalSpaceMatrix(Matrix4f matrix) {
        this.localSpaceMatrix.set(matrix);
    }

    /**
     * Gets the world-space transformation matrix.
     */
    public Matrix4f getWorldSpaceMatrix() {
        setTrackingMatrices(true);
        return worldSpaceMatrix;
    }

    public void setWorldSpaceMatrix(Matrix4f matrix) {
        this.worldSpaceMatrix.set(matrix);
    }

    // ============================================================================
    // Position Utilities (GeckoLib feature)
    // ============================================================================

    /**
     * Gets the position of the bone relative to its parent.
     */
    public Vector3d getLocalPosition() {
        Vector4f vec = getLocalSpaceMatrix().transform(new Vector4f(0, 0, 0, 1));
        return new Vector3d(vec.x(), vec.y(), vec.z());
    }

    /**
     * Gets the position of the bone relative to the model root.
     */
    public Vector3d getModelPosition() {
        Vector4f vec = getModelSpaceMatrix().transform(new Vector4f(0, 0, 0, 1));
        return new Vector3d(-vec.x() * 16f, vec.y() * 16f, vec.z() * 16f);
    }

    /**
     * Gets the position of the bone in world space.
     */
    public Vector3d getWorldPosition() {
        Vector4f vec = getWorldSpaceMatrix().transform(new Vector4f(0, 0, 0, 1));
        return new Vector3d(vec.x(), vec.y(), vec.z());
    }

    /**
     * Sets the model position of this bone.
     * Note: This doesn't work well with bones that have parent transforms.
     */
    public void setModelPosition(Vector3d pos) {
        StrataBone parent = getParent();
        Matrix4f matrix = (parent == null ? new Matrix4f().identity() : new Matrix4f(parent.getModelSpaceMatrix())).invert();
        Vector4f vec = matrix.transform(new Vector4f(-(float) pos.x / 16f, (float) pos.y / 16f, (float) pos.z / 16f, 1));

        setPositionOverride(-vec.x() * 16f, vec.y() * 16f, vec.z() * 16f);
    }

    // ============================================================================
    // Utility Methods
    // ============================================================================

    /**
     * Gets rotation as a vector.
     */
    public Vector3d getRotationVector() {
        return new Vector3d(
                rotation.x + animRotation.x,
                rotation.y + animRotation.y,
                rotation.z + animRotation.z
        );
    }

    /**
     * Gets scale as a vector.
     */
    public Vector3d getScaleVector() {
        return new Vector3d(animScale.x, animScale.y, animScale.z);
    }

    /**
     * Gets position as a vector.
     */
    public Vector3d getPositionVector() {
        if (hasPositionOverride) {
            return new Vector3d(positionOverride.x, positionOverride.y, positionOverride.z);
        }
        return new Vector3d(animTranslation.x, animTranslation.y, animTranslation.z);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof StrataBone other)) {
            return false;
        }
        return Objects.equals(name, other.name) &&
                Objects.equals(parent != null ? parent.name : null, other.parent != null ? other.parent.name : null);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, parent != null ? parent.name : null, meshIds.size(), children.size());
    }

    @Override
    public String toString() {
        return "StrataBone{name='" + name + "', hidden=" + hidden + ", children=" + children.size() + "}";
    }
}