package engine.strata.client.render.animation.core;

import org.joml.Vector3f;

/**
 * Immutable snapshot of a bone's transformation state at a specific point in time.
 * Used for smooth transitions, blending, and animation resets.
 *
 * Unlike GeckoLib's mutable approach, we use immutable snapshots for thread safety
 * and clearer state management.
 */
public record BoneSnapshot(
        Vector3f rotation,    // Euler angles in radians (x, y, z)
        Vector3f position,    // Translation offset from parent
        Vector3f scale,       // Scale multipliers (x, y, z)
        double timestamp      // When this snapshot was captured (in seconds)
) {

    /**
     * Create a defensive copy with cloned vectors
     */
    public BoneSnapshot {
        rotation = new Vector3f(rotation);
        position = new Vector3f(position);
        scale = new Vector3f(scale);
    }

    @Override
    public Vector3f rotation() { return new Vector3f(rotation); }

    @Override
    public Vector3f position() { return new Vector3f(position); }

    @Override
    public Vector3f scale() { return new Vector3f(scale); }

    /**
     * Create a snapshot representing the default/identity pose
     */
    public static BoneSnapshot identity() {
        return new BoneSnapshot(
                new Vector3f(0, 0, 0),
                new Vector3f(0, 0, 0),
                new Vector3f(1, 1, 1),
                0.0
        );
    }

    /**
     * Linear interpolation between two snapshots.
     * Used for smooth transitions and blending.
     *
     * @param other The target snapshot
     * @param alpha Interpolation factor [0, 1]
     * @return New interpolated snapshot
     */
    public BoneSnapshot lerp(BoneSnapshot other, float alpha) {
        Vector3f lerpedRot = new Vector3f(rotation).lerp(other.rotation, alpha);
        Vector3f lerpedPos = new Vector3f(position).lerp(other.position, alpha);
        Vector3f lerpedScale = new Vector3f(scale).lerp(other.scale, alpha);
        double lerpedTime = timestamp + (other.timestamp - timestamp) * alpha;

        return new BoneSnapshot(lerpedRot, lerpedPos, lerpedScale, lerpedTime);
    }

    /**
     * Spherical linear interpolation for rotations (more accurate for large angles).
     * This is a simplified version - full quaternion SLERP would be better.
     *
     * @param other The target snapshot
     * @param alpha Interpolation factor [0, 1]
     * @return New interpolated snapshot with SLERP rotation
     */
    public BoneSnapshot slerp(BoneSnapshot other, float alpha) {
        // TODO: Convert to quaternions for true SLERP
        // For now, use normalized lerp as approximation
        Vector3f slerpedRot = new Vector3f(rotation).lerp(other.rotation, alpha).normalize();
        Vector3f lerpedPos = new Vector3f(position).lerp(other.position, alpha);
        Vector3f lerpedScale = new Vector3f(scale).lerp(other.scale, alpha);
        double lerpedTime = timestamp + (other.timestamp - timestamp) * alpha;

        return new BoneSnapshot(slerpedRot, lerpedPos, lerpedScale, lerpedTime);
    }

    /**
     * Additive blending - useful for layered animations.
     * Example: Base walk animation + additive breathing motion
     *
     * @param additive The additive snapshot to apply
     * @param weight How much of the additive to apply [0, 1]
     * @return New snapshot with additive applied
     */
    public BoneSnapshot blendAdditive(BoneSnapshot additive, float weight) {
        Vector3f blendedRot = new Vector3f(rotation).add(
                new Vector3f(additive.rotation).mul(weight)
        );
        Vector3f blendedPos = new Vector3f(position).add(
                new Vector3f(additive.position).mul(weight)
        );
        Vector3f blendedScale = new Vector3f(scale).add(
                new Vector3f(additive.scale).sub(1, 1, 1).mul(weight)
        );

        return new BoneSnapshot(blendedRot, blendedPos, blendedScale, timestamp);
    }

    /**
     * Calculate the "distance" between two snapshots.
     * Useful for determining if two poses are similar enough to skip blending.
     *
     * @param other The snapshot to compare against
     * @return Approximate distance (lower = more similar)
     */
    public float distanceTo(BoneSnapshot other) {
        float rotDist = rotation.distance(other.rotation);
        float posDist = position.distance(other.position);
        float scaleDist = scale.distance(other.scale);

        // Weight rotation more heavily as it's usually more noticeable
        return rotDist * 2.0f + posDist + scaleDist * 0.5f;
    }

    /**
     * Check if this snapshot is effectively identical to another
     * (within epsilon for floating point comparison)
     */
    public boolean isEquivalentTo(BoneSnapshot other, float epsilon) {
        return rotation.distance(other.rotation) < epsilon
                && position.distance(other.position) < epsilon
                && scale.distance(other.scale) < epsilon;
    }

    @Override
    public String toString() {
        return String.format("BoneSnapshot[rot=(%.2f,%.2f,%.2f), pos=(%.2f,%.2f,%.2f), scale=(%.2f,%.2f,%.2f), t=%.3f]",
                Math.toDegrees(rotation.x), Math.toDegrees(rotation.y), Math.toDegrees(rotation.z),
                position.x, position.y, position.z,
                scale.x, scale.y, scale.z,
                timestamp
        );
    }
}