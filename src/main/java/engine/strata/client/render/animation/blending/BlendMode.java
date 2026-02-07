package engine.strata.client.render.animation.blending;

/**
 * Defines how multiple animation layers blend together.
 *
 * Different blend modes are useful for different types of animations:
 * - LINEAR: Standard cross-fade between animations
 * - ADDITIVE: Layer small motions on top (breathing, head bobbing)
 * - OVERRIDE: Higher priority completely replaces lower
 */
public enum BlendMode {
    /**
     * Linear interpolation between animations.
     * The most common blend mode for transitions.
     *
     * Formula: result = lerp(base, overlay, weight)
     *
     * Example use cases:
     * - Transitioning from walk to run
     * - Blending idle to action pose
     */
    LINEAR,

    /**
     * Additive blending - adds the overlay motion to the base.
     * Perfect for secondary animations that enhance the base.
     *
     * Formula: result = base + (overlay * weight)
     *
     * Example use cases:
     * - Breathing motion on top of idle
     * - Head tracking added to any animation
     * - Recoil effect during shooting
     * - Facial expressions over body animations
     */
    ADDITIVE,

    /**
     * Override mode - highest weight wins completely.
     * Useful for high-priority animations that should interrupt.
     *
     * Formula: result = (weight > threshold) ? overlay : base
     *
     * Example use cases:
     * - Stun/ragdoll overriding everything
     * - Death animation
     * - Forced emotes/gestures
     */
    OVERRIDE;

    /**
     * Blend two values according to this blend mode
     */
    public float blend(float base, float overlay, float weight) {
        return switch (this) {
            case LINEAR -> base * (1.0f - weight) + overlay * weight;
            case ADDITIVE -> base + overlay * weight;
            case OVERRIDE -> weight > 0.5f ? overlay : base;
        };
    }
}