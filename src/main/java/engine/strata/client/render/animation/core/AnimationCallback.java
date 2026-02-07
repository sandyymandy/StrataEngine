package engine.strata.client.render.animation.core;

import engine.strata.client.render.animation.StrataAnimation;

/**
 * Callback interface for animation lifecycle events.
 *
 * <p>Allows entities and other systems to react to animation state changes:
 * <ul>
 *   <li>onAnimationStart - When an animation begins playing</li>
 *   <li>onAnimationLoop - Each time a looping animation repeats</li>
 *   <li>onAnimationEnd - When a non-looping animation completes</li>
 *   <li>onAnimationTransitionStart - When blending between animations begins</li>
 *   <li>onAnimationTransitionEnd - When blending completes</li>
 * </ul>
 *
 * <p><b>Usage Example:</b>
 * <pre>{@code
 * controller.addCallback(new AnimationCallback() {
 *     @Override
 *     public void onAnimationStart(String layerName, AnimationData animation) {
 *         if (animation.name().equals("attack")) {
 *             entity.playSound("sword_swing");
 *         }
 *     }
 *
 *     @Override
 *     public void onAnimationEnd(String layerName, AnimationData animation) {
 *         if (animation.name().equals("death")) {
 *             entity.remove();
 *         }
 *     }
 * });
 * }</pre>
 */
public interface AnimationCallback {

    /**
     * Called when an animation starts playing on a layer.
     *
     * @param layerName The layer the animation is playing on
     * @param animation The animation that started
     */
    default void onAnimationStart(String layerName, StrataAnimation.AnimationData animation) {
        // Default: do nothing
    }

    /**
     * Called each time a looping animation completes a loop.
     *
     * @param layerName The layer the animation is playing on
     * @param animation The animation that looped
     * @param loopCount The number of times the animation has looped
     */
    default void onAnimationLoop(String layerName, StrataAnimation.AnimationData animation, int loopCount) {
        // Default: do nothing
    }

    /**
     * Called when a non-looping animation finishes playing.
     *
     * @param layerName The layer the animation was playing on
     * @param animation The animation that ended
     */
    default void onAnimationEnd(String layerName, StrataAnimation.AnimationData animation) {
        // Default: do nothing
    }

    /**
     * Called when a transition between animations begins.
     *
     * @param layerName The layer where transition is happening
     * @param fromAnimation The animation being transitioned from (may be null)
     * @param toAnimation The animation being transitioned to
     * @param blendDuration The duration of the blend in seconds
     */
    default void onAnimationTransitionStart(String layerName,
                                            StrataAnimation.AnimationData fromAnimation,
                                            StrataAnimation.AnimationData toAnimation,
                                            float blendDuration) {
        // Default: do nothing
    }

    /**
     * Called when a transition between animations completes.
     *
     * @param layerName The layer where transition completed
     * @param animation The animation now playing
     */
    default void onAnimationTransitionEnd(String layerName, StrataAnimation.AnimationData animation) {
        // Default: do nothing
    }

    /**
     * Simple adapter class for when you only need some callbacks.
     * Extend this instead of implementing the full interface.
     */
    abstract class Adapter implements AnimationCallback {
        // All methods have default implementations in the interface
    }
}