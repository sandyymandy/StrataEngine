package engine.strata.client.render.animation;

import engine.strata.util.Identifier;
import org.joml.Vector3f;

import java.util.List;
import java.util.Map;

/**
 * Represents a collection of animations for a StrataModel.
 * Each .stranim file contains multiple named animations that can be played on the model.
 */
public record StrataAnimation(
        Identifier id,
        int formatVersion,
        Map<String, AnimationData> animations
) {
    /**
     * Gets an animation by name, or null if not found.
     */
    public AnimationData getAnimation(String name) {
        return animations.get(name);
    }

    /**
     * Checks if this animation container has a specific animation.
     */
    public boolean hasAnimation(String name) {
        return animations.containsKey(name);
    }

    /**
     * Represents a single animation within the container.
     */
    public record AnimationData(
            String name,
            float duration,              // Total duration in seconds
            boolean loop,                // Whether to loop the animation
            LoopMode loopMode,           // How to loop (restart, pingpong)
            float blendInDuration,       // Time to blend in from previous animation
            float blendOutDuration,      // Time to blend out to next animation
            Map<String, BoneAnimation> boneAnimations,  // Bone name -> animation data
            List<AnimationEvent> events  // Timed events (sound, particles, etc.)
    ) {
        /**
         * Gets the bone animation for a specific bone, or null if not animated.
         */
        public BoneAnimation getBoneAnimation(String boneName) {
            return boneAnimations.get(boneName);
        }

        /**
         * Checks if a specific bone is animated in this animation.
         */
        public boolean animatesBone(String boneName) {
            return boneAnimations.containsKey(boneName);
        }
    }

    /**
     * Animation data for a single bone.
     * Contains keyframe tracks for rotation, translation, and scale.
     */
    public record BoneAnimation(
            List<RotationKeyframe> rotation,
            List<TranslationKeyframe> translation,
            List<ScaleKeyframe> scale
    ) {
        /**
         * Checks if this bone has any rotation keyframes.
         */
        public boolean hasRotation() {
            return rotation != null && !rotation.isEmpty();
        }

        /**
         * Checks if this bone has any translation keyframes.
         */
        public boolean hasTranslation() {
            return translation != null && !translation.isEmpty();
        }

        /**
         * Checks if this bone has any scale keyframes.
         */
        public boolean hasScale() {
            return scale != null && !scale.isEmpty();
        }
    }

    /**
     * Base class for all keyframes.
     */
    public abstract static class Keyframe {
        protected final float timestamp;  // Time in seconds
        protected final EasingFunction easing;

        public Keyframe(float timestamp, EasingFunction easing) {
            this.timestamp = timestamp;
            this.easing = easing;
        }

        public float getTimestamp() {
            return timestamp;
        }

        public EasingFunction getEasing() {
            return easing;
        }
    }

    /**
     * Keyframe for rotation (stored as Euler angles in radians).
     */
    public static class RotationKeyframe extends Keyframe {
        private final Vector3f rotation;  // X, Y, Z rotation in radians

        public RotationKeyframe(float timestamp, Vector3f rotation, EasingFunction easing) {
            super(timestamp, easing);
            this.rotation = rotation;
        }

        public Vector3f getRotation() {
            return rotation;
        }
    }

    /**
     * Keyframe for translation.
     */
    public static class TranslationKeyframe extends Keyframe {
        private final Vector3f translation;  // X, Y, Z offset

        public TranslationKeyframe(float timestamp, Vector3f translation, EasingFunction easing) {
            super(timestamp, easing);
            this.translation = translation;
        }

        public Vector3f getTranslation() {
            return translation;
        }
    }

    /**
     * Keyframe for scale.
     */
    public static class ScaleKeyframe extends Keyframe {
        private final Vector3f scale;  // X, Y, Z scale multipliers

        public ScaleKeyframe(float timestamp, Vector3f scale, EasingFunction easing) {
            super(timestamp, easing);
            this.scale = scale;
        }

        public Vector3f getScale() {
            return scale;
        }
    }

    /**
     * Timed event that can trigger during animation playback.
     * Examples: sound effects, particle spawns, footstep marks, etc.
     */
    public record AnimationEvent(
            float timestamp,
            String type,                    // "sound", "particle", "footstep", etc.
            Map<String, String> parameters  // Event-specific data
    ) {
    }

    /**
     * Loop modes for animations.
     */
    public enum LoopMode {
        RESTART,    // Jump back to start when looping
        PINGPONG    // Reverse direction when looping
    }

    /**
     * Easing functions for smooth interpolation between keyframes.
     * Based on easings.net
     */
    public enum EasingFunction {
        // No easing
        LINEAR,

        // Sine easing
        EASE_IN_SINE,
        EASE_OUT_SINE,
        EASE_IN_OUT_SINE,

        // Quad easing
        EASE_IN_QUAD,
        EASE_OUT_QUAD,
        EASE_IN_OUT_QUAD,

        // Cubic easing
        EASE_IN_CUBIC,
        EASE_OUT_CUBIC,
        EASE_IN_OUT_CUBIC,

        // Quart easing
        EASE_IN_QUART,
        EASE_OUT_QUART,
        EASE_IN_OUT_QUART,

        // Quint easing
        EASE_IN_QUINT,
        EASE_OUT_QUINT,
        EASE_IN_OUT_QUINT,

        // Expo easing
        EASE_IN_EXPO,
        EASE_OUT_EXPO,
        EASE_IN_OUT_EXPO,

        // Circ easing
        EASE_IN_CIRC,
        EASE_OUT_CIRC,
        EASE_IN_OUT_CIRC,

        // Back easing (overshoots)
        EASE_IN_BACK,
        EASE_OUT_BACK,
        EASE_IN_OUT_BACK,

        // Elastic easing (springs)
        EASE_IN_ELASTIC,
        EASE_OUT_ELASTIC,
        EASE_IN_OUT_ELASTIC,

        // Bounce easing
        EASE_IN_BOUNCE,
        EASE_OUT_BOUNCE,
        EASE_IN_OUT_BOUNCE
    }
}