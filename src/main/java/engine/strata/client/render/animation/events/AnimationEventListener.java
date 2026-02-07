package engine.strata.client.render.animation.events;

import engine.strata.client.render.animation.StrataAnimation;

/**
 * Listener interface for animation events.
 *
 * <p>Animation events are defined in .stranim files and triggered at specific
 * timestamps during animation playback. Common use cases:
 * <ul>
 *   <li>Play sound effects (footsteps, attacks, etc.)</li>
 *   <li>Spawn particles (dust, sparks, blood, etc.)</li>
 *   <li>Trigger game logic (apply damage, consume item, etc.)</li>
 *   <li>Camera effects (shake, zoom, etc.)</li>
 * </ul>
 *
 * <p><b>Usage Example:</b>
 * <pre>{@code
 * controller.addEventListener(new AnimationEventListener() {
 *     @Override
 *     public void onAnimationEvent(AnimationEventContext context) {
 *         switch (context.event().type()) {
 *             case "sound":
 *                 String sound = context.event().parameters().get("sound_id");
 *                 entity.playSound(sound);
 *                 break;
 *             case "particle":
 *                 String particle = context.event().parameters().get("particle_id");
 *                 world.spawnParticle(particle, entity.getPosition());
 *                 break;
 *             case "damage":
 *                 entity.attackTarget();
 *                 break;
 *         }
 *     }
 * });
 * }</pre>
 */
@FunctionalInterface
public interface AnimationEventListener {

    /**
     * Called when an animation event is triggered.
     *
     * @param context Context containing event data and playback state
     */
    void onAnimationEvent(AnimationEventContext context);

    /**
     * Context object passed to event listeners containing all relevant information.
     */
    record AnimationEventContext(
            StrataAnimation.AnimationEvent event,  // The event that was triggered
            StrataAnimation.AnimationData animation,  // The animation it came from
            String layerName,  // The layer playing the animation
            float animationTime,  // Current playback time
            int loopCount  // Number of times animation has looped
    ) {
        /**
         * Convenience method to get event type.
         */
        public String getType() {
            return event.type();
        }

        /**
         * Convenience method to get event parameter.
         */
        public String getParameter(String key) {
            return event.parameters().get(key);
        }

        /**
         * Convenience method to get event parameter with default.
         */
        public String getParameter(String key, String defaultValue) {
            return event.parameters().getOrDefault(key, defaultValue);
        }

        /**
         * Checks if event has a specific parameter.
         */
        public boolean hasParameter(String key) {
            return event.parameters().containsKey(key);
        }

        /**
         * Gets timestamp of the event.
         */
        public float getEventTimestamp() {
            return event.timestamp();
        }
    }
}