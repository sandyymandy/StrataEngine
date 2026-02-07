package engine.strata.client.render.animation.core;

import engine.strata.client.render.animation.AnimationManager;
import engine.strata.client.render.animation.StrataAnimation;
import engine.strata.client.render.animation.events.AnimationEventListener;
import engine.strata.client.render.model.StrataModel;
import engine.strata.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Manages animation playback state for an entity.
 * Handles animation layers, transitions, and state management.
 *
 * Key improvements over current system:
 * - Multiple concurrent animation layers (e.g., upper body + lower body)
 * - Smooth transition blending between animations
 * - State machine support for complex animation logic
 * - Decoupled from rendering (updates independently)
 */
public class AnimationController {
    private static final Logger LOGGER = LoggerFactory.getLogger("AnimationController");

    private final StrataModel model;
    private final BoneAnimator animator;
    private final Map<String, AnimationLayer> layers = new LinkedHashMap<>();
    private final List<AnimationEventListener> eventListeners = new CopyOnWriteArrayList<>();
    private final List<AnimationCallback> callbacks = new CopyOnWriteArrayList<>();

    // Masks per layer
    private final Map<String, AnimationMask> layerMasks = new HashMap<>();

    // Default layer that always exists
    private static final String DEFAULT_LAYER = "base";

    public AnimationController(StrataModel model) {
        this.model = model;
        this.animator = new BoneAnimator(model);

        // Create default base layer
        layers.put(DEFAULT_LAYER, new AnimationLayer(DEFAULT_LAYER, 1.0f));

        LOGGER.debug("Created animation controller for model: {}", model.getId());
    }

    /**
     * Play an animation on the default layer
     */
    public PlaybackHandle play(Identifier animationId, String animationName) {
        return play(animationId, animationName, DEFAULT_LAYER, 0.0f);
    }
    /**
     * Play an animation with a blend duration
     */
    public PlaybackHandle play(Identifier animationId, String animationName, float blendDuration) {
        return play(animationId, animationName, DEFAULT_LAYER, blendDuration);
    }

    /**
     * Play an animation on a specific layer
     *
     * @param animationId The animation file identifier
     * @param animationName The specific animation within the file
     * @param layerName The layer to play on (created if doesn't exist)
     * @param blendDuration How long to blend from current animation (seconds)
     */
    public PlaybackHandle play(Identifier animationId, String animationName, String layerName, float blendDuration) {
        if (animationId == null || animationName == null || layerName == null) {
            LOGGER.error("Cannot play animation with null parameters");
            return new PlaybackHandle(layerName, null);
        }

        StrataAnimation animFile = AnimationManager.getAnimation(animationId);
        if (animFile == null) {
            LOGGER.error("Animation file not found: {}", animationId);
            return new PlaybackHandle(layerName, null);
        }

        StrataAnimation.AnimationData animation = animFile.getAnimation(animationName);
        if (animation == null) {
            LOGGER.error("Animation '{}' not found in '{}'", animationName, animationId);
            return new PlaybackHandle(layerName, null);
        }

        AnimationLayer layer = layers.computeIfAbsent(layerName,
                name -> new AnimationLayer(name, 1.0f));

        // Store previous animation for callbacks
        StrataAnimation.AnimationData previousAnim = layer.getCurrentAnimation();

        // Start playing
        layer.playAnimation(animation, blendDuration);

        // Fire callbacks
        fireAnimationStartCallbacks(layerName, animation);
        if (previousAnim != null && blendDuration > 0) {
            fireTransitionStartCallbacks(layerName, previousAnim, animation, blendDuration);
        }

        LOGGER.debug("Playing animation '{}' on layer '{}' with {}s blend",
                animationName, layerName, blendDuration);

        return new PlaybackHandle(layerName, animation);
    }



    /**
     * Stop animation on a specific layer
     */
    public void stop(String layerName, float blendOutDuration) {
        AnimationLayer layer = layers.get(layerName);
        if (layer == null) {
            LOGGER.warn("Attempted to stop non-existent layer: {}", layerName);
            return;
        }

        StrataAnimation.AnimationData currentAnim = layer.getCurrentAnimation();
        layer.stop(blendOutDuration);

        // Fire callbacks
        if (currentAnim != null && !layer.isPlaying()) {
            fireAnimationEndCallbacks(layerName, currentAnim);
        }
    }

    /**
     * Stop all animations
     */
    public void stopAll(float blendOutDuration) {
        for (Map.Entry<String, AnimationLayer> entry : layers.entrySet()) {
            String layerName = entry.getKey();
            AnimationLayer layer = entry.getValue();

            if (layer != null) {
                StrataAnimation.AnimationData currentAnim = layer.getCurrentAnimation();
                layer.stop(blendOutDuration);

                if (currentAnim != null && !layer.isPlaying()) {
                    fireAnimationEndCallbacks(layerName, currentAnim);
                }
            }
        }
    }


    /**
     * Create or get an animation layer
     *
     * @param name Layer name
     * @param weight Layer blend weight (0-1)
     * @return The animation layer
     */
    public AnimationLayer getOrCreateLayer(String name, float weight) {
        if (name == null) {
            throw new IllegalArgumentException("Layer name cannot be null");
        }
        return layers.computeIfAbsent(name, n -> new AnimationLayer(n, weight));
    }


    /**
     * Set the weight of a layer (affects how much it influences final pose)
     */
    public void setLayerWeight(String layerName, float weight) {
        AnimationLayer layer = layers.get(layerName);
        if (layer != null) {
            layer.setWeight(weight);
        } else {
            LOGGER.warn("Attempted to set weight on non-existent layer: {}", layerName);
        }
    }

    /**
     * Set an animation mask for a specific layer.
     * The mask determines which bones the layer's animation affects.
     *
     * @param layerName The layer name
     * @param mask The animation mask
     */
    public void setLayerMask(String layerName, AnimationMask mask) {
        if (mask == null) {
            layerMasks.remove(layerName);
        } else {
            layerMasks.put(layerName, mask);
        }
    }

    /**
     * Get the animation mask for a layer.
     *
     * @param layerName The layer name
     * @return The mask, or null if no mask is set
     */
    public AnimationMask getLayerMask(String layerName) {
        return layerMasks.get(layerName);
    }

    /**
     * Check if any layer is currently playing an animation
     */
    public boolean isPlaying() {
        return layers.values().stream().anyMatch(AnimationLayer::isActive);
    }

    /**
     * Check if a specific layer is playing
     */
    public boolean isPlaying(String layerName) {
        AnimationLayer layer = layers.get(layerName);
        return layer != null && layer.isActive();
    }

    /**
     * Check if a specific layer is playing
     */
    public boolean isPlaying(String layerName, String animName) {
        AnimationLayer layer = layers.get(layerName);
        return layer != null && layer.isActive() && layer.getCurrentAnimation().name().equals(animName);
    }


    /**
     * Add an animation callback for lifecycle events.
     *
     * @param callback The callback to add
     */
    public void addCallback(AnimationCallback callback) {
        if (callback != null && !callbacks.contains(callback)) {
            callbacks.add(callback);
        }
    }

    /**
     * Remove an animation callback.
     *
     * @param callback The callback to remove
     */
    public void removeCallback(AnimationCallback callback) {
        callbacks.remove(callback);
    }

    /**
     * Add an animation event listener.
     *
     * @param listener The listener to add
     */
    public void addEventListener(AnimationEventListener listener) {
        if (listener != null && !eventListeners.contains(listener)) {
            eventListeners.add(listener);
        }
    }

    /**
     * Remove an animation event listener.
     *
     * @param listener The listener to remove
     */
    public void removeEventListener(AnimationEventListener listener) {
        eventListeners.remove(listener);
    }

    /**
     * Clear all callbacks and event listeners.
     */
    public void clearCallbacksAndListeners() {
        callbacks.clear();
        eventListeners.clear();
    }

    /**
     * Update all animation layers (called every game tick)
     *
     * @param deltaTime Time since last update in seconds
     */
    public void tick(float deltaTime) {
        if (Float.isNaN(deltaTime) || Float.isInfinite(deltaTime) || deltaTime < 0) {
            LOGGER.warn("Invalid deltaTime: {}, skipping tick", deltaTime);
            return;
        }

        // Track previous state for event detection
        Map<String, Float> previousTimes = new HashMap<>();
        Map<String, Integer> previousLoops = new HashMap<>();
        Map<String, Boolean> wasPlaying = new HashMap<>();

        for (Map.Entry<String, AnimationLayer> entry : layers.entrySet()) {
            AnimationLayer layer = entry.getValue();
            if (layer != null && layer.isActive()) {
                previousTimes.put(entry.getKey(), layer.getAnimationTime());
                previousLoops.put(entry.getKey(), layer.getLoopCount());
                wasPlaying.put(entry.getKey(), layer.isPlaying());
            }
        }

        // Update each layer's playback state
        for (Map.Entry<String, AnimationLayer> entry : layers.entrySet()) {
            String layerName = entry.getKey();
            AnimationLayer layer = entry.getValue();

            if (layer != null) {
                boolean wasTransitioning = layer.isTransitioning();

                layer.tick(deltaTime);

                // Check for transition end
                if (wasTransitioning && !layer.isTransitioning()) {
                    fireTransitionEndCallbacks(layerName, layer.getCurrentAnimation());
                }
            }
        }

        // Detect and fire animation events
        processAnimationEvents(previousTimes, previousLoops);

        // Detect animation loops and ends
        processLifecycleEvents(previousLoops, wasPlaying);

        // Process all layers and update bone transforms (with masks)
        animator.processLayers(layers.values(), layerMasks, deltaTime);
    }

    /**
     * Process animation events that occurred during this tick.
     */
    private void processAnimationEvents(Map<String, Float> previousTimes, Map<String, Integer> previousLoops) {
        for (Map.Entry<String, AnimationLayer> entry : layers.entrySet()) {
            String layerName = entry.getKey();
            AnimationLayer layer = entry.getValue();

            if (layer == null || !layer.isActive()) {
                continue;
            }

            StrataAnimation.AnimationData animation = layer.getCurrentAnimation();
            if (animation == null || animation.events() == null || animation.events().isEmpty()) {
                continue;
            }

            float prevTime = previousTimes.getOrDefault(layerName, 0.0f);
            float currTime = layer.getAnimationTime();
            int currentLoop = layer.getLoopCount();
            int prevLoop = previousLoops.getOrDefault(layerName, 0);

            // Check if we looped - if so, process events from prevTime to duration and 0 to currTime
            if (currentLoop > prevLoop) {
                // Process events from prevTime to end of animation
                processEventsInRange(animation, layerName, prevTime, animation.duration(), currentLoop);
                // Process events from start to current time
                processEventsInRange(animation, layerName, 0.0f, currTime, currentLoop);
            } else {
                // Normal case - process events in range
                processEventsInRange(animation, layerName, prevTime, currTime, currentLoop);
            }
        }
    }

    /**
     * Process events within a time range.
     */
    private void processEventsInRange(StrataAnimation.AnimationData animation, String layerName,
                                      float startTime, float endTime, int loopCount) {
        for (StrataAnimation.AnimationEvent event : animation.events()) {
            float eventTime = event.timestamp();

            // Check if event falls within the time range
            if (eventTime > startTime && eventTime <= endTime) {
                fireAnimationEvent(layerName, animation, event, loopCount);
            }
        }
    }

    /**
     * Process animation lifecycle events (loops, ends).
     */
    private void processLifecycleEvents(Map<String, Integer> previousLoops, Map<String, Boolean> wasPlaying) {
        for (Map.Entry<String, AnimationLayer> entry : layers.entrySet()) {
            String layerName = entry.getKey();
            AnimationLayer layer = entry.getValue();

            if (layer == null) {
                continue;
            }

            // Check for loop
            int prevLoop = previousLoops.getOrDefault(layerName, 0);
            int currLoop = layer.getLoopCount();
            if (currLoop > prevLoop && layer.getCurrentAnimation() != null) {
                fireAnimationLoopCallbacks(layerName, layer.getCurrentAnimation(), currLoop);
            }

            // Check for end
            boolean prevPlaying = wasPlaying.getOrDefault(layerName, false);
            if (prevPlaying && !layer.isPlaying() && !layer.isTransitioning() && layer.getCurrentAnimation() != null) {
                fireAnimationEndCallbacks(layerName, layer.getCurrentAnimation());
            }
        }
    }

    private void fireAnimationStartCallbacks(String layerName, StrataAnimation.AnimationData animation) {
        for (AnimationCallback callback : callbacks) {
            try {
                callback.onAnimationStart(layerName, animation);
            } catch (Exception e) {
                LOGGER.error("Error in animation start callback", e);
            }
        }
    }

    private void fireAnimationLoopCallbacks(String layerName, StrataAnimation.AnimationData animation, int loopCount) {
        for (AnimationCallback callback : callbacks) {
            try {
                callback.onAnimationLoop(layerName, animation, loopCount);
            } catch (Exception e) {
                LOGGER.error("Error in animation loop callback", e);
            }
        }
    }

    private void fireAnimationEndCallbacks(String layerName, StrataAnimation.AnimationData animation) {
        for (AnimationCallback callback : callbacks) {
            try {
                callback.onAnimationEnd(layerName, animation);
            } catch (Exception e) {
                LOGGER.error("Error in animation end callback", e);
            }
        }
    }

    private void fireTransitionStartCallbacks(String layerName, StrataAnimation.AnimationData from,
                                              StrataAnimation.AnimationData to, float duration) {
        for (AnimationCallback callback : callbacks) {
            try {
                callback.onAnimationTransitionStart(layerName, from, to, duration);
            } catch (Exception e) {
                LOGGER.error("Error in transition start callback", e);
            }
        }
    }

    private void fireTransitionEndCallbacks(String layerName, StrataAnimation.AnimationData animation) {
        for (AnimationCallback callback : callbacks) {
            try {
                callback.onAnimationTransitionEnd(layerName, animation);
            } catch (Exception e) {
                LOGGER.error("Error in transition end callback", e);
            }
        }
    }

    private void fireAnimationEvent(String layerName, StrataAnimation.AnimationData animation,
                                    StrataAnimation.AnimationEvent event, int loopCount) {
        AnimationLayer layer = layers.get(layerName);
        float animTime = layer != null ? layer.getAnimationTime() : 0.0f;

        AnimationEventListener.AnimationEventContext context =
                new AnimationEventListener.AnimationEventContext(event, animation, layerName, animTime, loopCount);

        for (AnimationEventListener listener : eventListeners) {
            try {
                listener.onAnimationEvent(context);
            } catch (Exception e) {
                LOGGER.error("Error in animation event listener", e);
            }
        }
    }

    /**
     * Get the current animation time for a layer
     */
    public float getAnimationTime(String layerName) {
        AnimationLayer layer = layers.get(layerName);
        return layer != null ? layer.getAnimationTime() : 0.0f;
    }

    /**
     * Get the current animation being played on a layer
     */
    public StrataAnimation.AnimationData getCurrentAnimation(String layerName) {
        AnimationLayer layer = layers.get(layerName);
        return layer != null ? layer.getCurrentAnimation() : null;
    }

    /**
     * Get all registered layers
     */
    public Collection<AnimationLayer> getLayers() {
        return Collections.unmodifiableCollection(layers.values());
    }

    /**
     * Get the bone animator (for advanced use cases)
     */
    public BoneAnimator getAnimator() {
        return animator;
    }

    /**
     * Reset all animations to their default state.
     */
    public void reset() {
        stopAll(0.0f);
        animator.reset();
        layerMasks.clear();
    }


    /**
     * Get debug information about current state
     */
    public String getDebugInfo() {
        StringBuilder sb = new StringBuilder();
        sb.append("AnimationController[");
        for (Map.Entry<String, AnimationLayer> entry : layers.entrySet()) {
            AnimationLayer layer = entry.getValue();
            if (layer.isActive()) {
                sb.append(String.format("%s: %s (%.2fs, weight=%.2f), ",
                        entry.getKey(),
                        layer.getCurrentAnimation() != null ? layer.getCurrentAnimation().name() : "none",
                        layer.getAnimationTime(),
                        layer.getWeight()
                ));
            }
        }
        sb.append("]");
        return sb.toString();
    }

    /**
     * Handle returned from play() methods for chaining operations.
     * Allows fluent API like: controller.play(...).withMask(...).withWeight(...)
     */
    public class PlaybackHandle {
        private final String layerName;
        private final StrataAnimation.AnimationData animation;

        PlaybackHandle(String layerName, StrataAnimation.AnimationData animation) {
            this.layerName = layerName;
            this.animation = animation;
        }

        /**
         * Set an animation mask for this playback.
         *
         * @param mask The mask to apply
         * @return This handle for chaining
         */
        public PlaybackHandle withMask(AnimationMask mask) {
            setLayerMask(layerName, mask);
            return this;
        }

        /**
         * Set the layer weight.
         *
         * @param weight The weight (0-1)
         * @return This handle for chaining
         */
        public PlaybackHandle withWeight(float weight) {
            setLayerWeight(layerName, weight);
            return this;
        }

        /**
         * Get the layer name this animation is playing on.
         */
        public String getLayerName() {
            return layerName;
        }

        /**
         * Get the animation data.
         */
        public StrataAnimation.AnimationData getAnimation() {
            return animation;
        }
    }

}