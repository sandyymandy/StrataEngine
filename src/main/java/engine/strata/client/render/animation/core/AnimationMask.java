package engine.strata.client.render.animation.core;

import java.util.*;

/**
 * Defines which bones an animation should affect.
 *
 * <p>Masks allow animations to only affect specific parts of a model:
 * <ul>
 *   <li>Upper body animations (arms, torso, head)</li>
 *   <li>Lower body animations (legs, hips)</li>
 *   <li>Facial animations (head, jaw, eyes)</li>
 *   <li>Weapon animations (weapon bones only)</li>
 * </ul>
 *
 * <p><b>Usage Examples:</b>
 * <pre>{@code
 * // Only affect arms and weapon
 * AnimationMask upperBody = AnimationMask.include("arm_left", "arm_right", "weapon");
 *
 * // Affect everything EXCEPT the legs
 * AnimationMask noLegs = AnimationMask.exclude("leg_left", "leg_right");
 *
 * // Use with controller
 * controller.play(animId, "reload", "upper_body", 0.2f)
 *     .withMask(upperBody);
 * }</pre>
 */
public class AnimationMask {
    private final Set<String> boneNames;
    private final boolean inverted;  // true = exclude these bones, false = include only these

    /**
     * Private constructor. Use static factory methods.
     */
    private AnimationMask(Set<String> boneNames, boolean inverted) {
        this.boneNames = new HashSet<>(boneNames);
        this.inverted = inverted;
    }

    /**
     * Creates a mask that ONLY affects the specified bones.
     *
     * @param boneNames Bones to affect
     * @return New animation mask
     */
    public static AnimationMask include(String... boneNames) {
        return new AnimationMask(Set.of(boneNames), false);
    }

    /**
     * Creates a mask that ONLY affects the specified bones.
     *
     * @param boneNames Bones to affect
     * @return New animation mask
     */
    public static AnimationMask include(Collection<String> boneNames) {
        return new AnimationMask(new HashSet<>(boneNames), false);
    }

    /**
     * Creates a mask that affects ALL bones EXCEPT the specified ones.
     *
     * @param boneNames Bones to exclude
     * @return New animation mask
     */
    public static AnimationMask exclude(String... boneNames) {
        return new AnimationMask(Set.of(boneNames), true);
    }

    /**
     * Creates a mask that affects ALL bones EXCEPT the specified ones.
     *
     * @param boneNames Bones to exclude
     * @return New animation mask
     */
    public static AnimationMask exclude(Collection<String> boneNames) {
        return new AnimationMask(new HashSet<>(boneNames), true);
    }

    /**
     * Creates a mask that affects ALL bones.
     *
     * @return Animation mask affecting everything
     */
    public static AnimationMask all() {
        return new AnimationMask(Collections.emptySet(), true);
    }

    /**
     * Creates a mask that affects NO bones.
     *
     * @return Animation mask affecting nothing
     */
    public static AnimationMask none() {
        return new AnimationMask(Collections.emptySet(), false);
    }

    /**
     * Checks if this mask should affect the given bone.
     *
     * @param boneName The bone name to check
     * @return true if the bone should be animated
     */
    public boolean shouldAffect(String boneName) {
        if (boneName == null) {
            return false;
        }

        boolean contains = boneNames.contains(boneName);
        return inverted ? !contains : contains;
    }

    /**
     * Combines this mask with another using AND logic.
     * The result affects only bones that BOTH masks affect.
     *
     * @param other The other mask
     * @return New combined mask
     */
    public AnimationMask and(AnimationMask other) {
        if (other == null) {
            return this;
        }

        // For simplicity, materialize all affected bones and create new include mask
        Set<String> combined = new HashSet<>();
        Set<String> allPossibleBones = new HashSet<>();
        allPossibleBones.addAll(this.boneNames);
        allPossibleBones.addAll(other.boneNames);

        for (String bone : allPossibleBones) {
            if (this.shouldAffect(bone) && other.shouldAffect(bone)) {
                combined.add(bone);
            }
        }

        return AnimationMask.include(combined);
    }

    /**
     * Combines this mask with another using OR logic.
     * The result affects bones that EITHER mask affects.
     *
     * @param other The other mask
     * @return New combined mask
     */
    public AnimationMask or(AnimationMask other) {
        if (other == null) {
            return this;
        }

        Set<String> combined = new HashSet<>();
        Set<String> allPossibleBones = new HashSet<>();
        allPossibleBones.addAll(this.boneNames);
        allPossibleBones.addAll(other.boneNames);

        for (String bone : allPossibleBones) {
            if (this.shouldAffect(bone) || other.shouldAffect(bone)) {
                combined.add(bone);
            }
        }

        return AnimationMask.include(combined);
    }

    /**
     * Creates an inverted version of this mask.
     *
     * @return New mask with opposite behavior
     */
    public AnimationMask invert() {
        return new AnimationMask(boneNames, !inverted);
    }

    /**
     * Checks if this mask affects all bones.
     *
     * @return true if mask affects everything
     */
    public boolean isAll() {
        return inverted && boneNames.isEmpty();
    }

    /**
     * Checks if this mask affects no bones.
     *
     * @return true if mask affects nothing
     */
    public boolean isNone() {
        return !inverted && boneNames.isEmpty();
    }

    /**
     * Gets the set of bone names in this mask.
     * Note: If inverted, these are the bones NOT affected.
     *
     * @return Unmodifiable set of bone names
     */
    public Set<String> getBoneNames() {
        return Collections.unmodifiableSet(boneNames);
    }

    /**
     * Checks if this mask is inverted (exclude mode).
     *
     * @return true if in exclude mode
     */
    public boolean isInverted() {
        return inverted;
    }

    @Override
    public String toString() {
        if (isAll()) {
            return "AnimationMask[ALL]";
        } else if (isNone()) {
            return "AnimationMask[NONE]";
        } else {
            return String.format("AnimationMask[%s: %s]",
                    inverted ? "EXCLUDE" : "INCLUDE",
                    boneNames);
        }
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof AnimationMask other)) return false;
        return inverted == other.inverted && boneNames.equals(other.boneNames);
    }

    @Override
    public int hashCode() {
        return Objects.hash(boneNames, inverted);
    }

    /**
     * Common preset masks for typical use cases.
     */
    public static class Presets {
        /**
         * Typical upper body bones for humanoid models.
         */
        public static AnimationMask upperBody() {
            return include(
                    "spine", "chest", "neck", "head",
                    "shoulder_left", "arm_left", "hand_left",
                    "shoulder_right", "arm_right", "hand_right"
            );
        }

        /**
         * Typical lower body bones for humanoid models.
         */
        public static AnimationMask lowerBody() {
            return include(
                    "hips", "pelvis",
                    "leg_left", "knee_left", "foot_left",
                    "leg_right", "knee_right", "foot_right"
            );
        }

        /**
         * Typical head and face bones.
         */
        public static AnimationMask head() {
            return include("head", "neck", "jaw", "eye_left", "eye_right");
        }

        /**
         * Left arm bones.
         */
        public static AnimationMask leftArm() {
            return include("shoulder_left", "arm_left", "elbow_left", "hand_left");
        }

        /**
         * Right arm bones.
         */
        public static AnimationMask rightArm() {
            return include("shoulder_right", "arm_right", "elbow_right", "hand_right");
        }
    }
}