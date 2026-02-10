package engine.strata.client.render.animation.core;

import engine.strata.client.render.animation.AnimationManager;
import engine.strata.client.render.animation.EasingUtil;
import engine.strata.client.render.animation.StrataAnimation;
import engine.strata.client.render.animation.StrataAnimation.*;
import engine.strata.client.render.animation.blending.BlendMode;
import engine.strata.client.render.animation.events.AnimationEventListener;
import engine.strata.client.render.model.StrataModel;
import engine.strata.client.render.renderer.ModelRenderer;
import engine.strata.util.Identifier;
import org.joml.Vector3f;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Manages animation playback state for an entity.
 *
 * <p><b>REDESIGNED ARCHITECTURE:</b>
 * <ul>
 *   <li>NO pre-computation of bone transforms</li>
 *   <li>Computes transforms ON-DEMAND during rendering</li>
 *   <li>Eliminates timing/sync issues between update and render</li>
 *   <li>Cleaner separation: Controller manages TIME, Renderer requests TRANSFORMS</li>
 * </ul>
 */
public class AnimationController {
    private static final Logger LOGGER = LoggerFactory.getLogger("AnimationController");

    private final StrataModel model;
    private final Map<String, AnimationLayer> layers = new LinkedHashMap<>();
    private final List<AnimationEventListener> eventListeners = new CopyOnWriteArrayList<>();
    private final List<AnimationCallback> callbacks = new CopyOnWriteArrayList<>();
    private final Map<String, AnimationMask> layerMasks = new HashMap<>();

    private static final String DEFAULT_LAYER = "base";

    public AnimationController(StrataModel model) {
        this.model = model;
        layers.put(DEFAULT_LAYER, new AnimationLayer(DEFAULT_LAYER, 1.0f));
        LOGGER.debug("Created animation controller for model: {}", model.getId());
    }

    // ========================================================================
    // ANIMATION PLAYBACK CONTROL
    // ========================================================================

    public PlaybackHandle play(Identifier animationId, String animationName) {
        return play(animationId, animationName, DEFAULT_LAYER, 0.0f);
    }

    public PlaybackHandle play(Identifier animationId, String animationName, float blendDuration) {
        return play(animationId, animationName, DEFAULT_LAYER, blendDuration);
    }

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

        AnimationData animation = animFile.getAnimation(animationName);
        if (animation == null) {
            LOGGER.error("Animation '{}' not found in '{}'", animationName, animationId);
            return new PlaybackHandle(layerName, null);
        }

        AnimationLayer layer = layers.computeIfAbsent(layerName,
                name -> new AnimationLayer(name, 1.0f));

        AnimationData previousAnim = layer.getCurrentAnimation();
        layer.playAnimation(animation, blendDuration);

        fireAnimationStartCallbacks(layerName, animation);
        if (previousAnim != null && blendDuration > 0) {
            fireTransitionStartCallbacks(layerName, previousAnim, animation, blendDuration);
        }

        LOGGER.debug("Playing animation '{}' on layer '{}' with {}s blend",
                animationName, layerName, blendDuration);

        return new PlaybackHandle(layerName, animation);
    }

    public void stop(String layerName, float blendOutDuration) {
        AnimationLayer layer = layers.get(layerName);
        if (layer == null) {
            LOGGER.warn("Attempted to stop non-existent layer: {}", layerName);
            return;
        }

        AnimationData currentAnim = layer.getCurrentAnimation();
        layer.stop(blendOutDuration);

        if (currentAnim != null && !layer.isPlaying()) {
            fireAnimationEndCallbacks(layerName, currentAnim);
        }
    }

    public void stopAll(float blendOutDuration) {
        for (Map.Entry<String, AnimationLayer> entry : layers.entrySet()) {
            String layerName = entry.getKey();
            AnimationLayer layer = entry.getValue();

            if (layer != null) {
                AnimationData currentAnim = layer.getCurrentAnimation();
                layer.stop(blendOutDuration);

                if (currentAnim != null && !layer.isPlaying()) {
                    fireAnimationEndCallbacks(layerName, currentAnim);
                }
            }
        }
    }

    // ========================================================================
    // CORE REDESIGN: ON-DEMAND TRANSFORM COMPUTATION
    // ========================================================================

    /**
     * Gets the current animated transform for a specific bone.
     *
     * <p><b>CRITICAL:</b> This is called DURING RENDERING, not before!
     * The renderer calls this for each bone as it renders the hierarchy.
     *
     * @param boneName The bone to get transforms for
     * @return The current transform, or null if no animation affects this bone
     */
    public ModelRenderer.BoneTransform getBoneTransform(String boneName) {
        if (boneName == null) {
            return null;
        }

        Vector3f finalRotation = new Vector3f(0, 0, 0);
        Vector3f finalTranslation = new Vector3f(0, 0, 0);
        Vector3f finalScale = new Vector3f(1, 1, 1);

        float totalWeight = 0.0f;
        boolean hasAnyAnimation = false;

        // Iterate through all active layers
        for (AnimationLayer layer : layers.values()) {
            if (!layer.isActive()) {
                continue;
            }

            AnimationMask mask = layerMasks.get(layer.getName());
            if (mask != null && !mask.shouldAffect(boneName)) {
                continue; // This layer doesn't affect this bone
            }

            // Sample current animation
            if (layer.getCurrentAnimation() != null) {
                float weight = layer.getBlendWeight();
                if (weight > 0.001f) {
                    BoneAnimData animData = sampleAnimation(
                            layer.getCurrentAnimation(),
                            boneName,
                            layer.getAnimationTime()
                    );

                    if (animData != null) {
                        blendInTransform(finalRotation, finalTranslation, finalScale,
                                animData, weight, layer.getBlendMode(), totalWeight);
                        totalWeight += weight;
                        hasAnyAnimation = true;
                    }
                }
            }

            // Sample previous animation if transitioning
            if (layer.isTransitioning() && layer.getPreviousAnimation() != null) {
                float weight = layer.getPreviousBlendWeight();
                if (weight > 0.001f) {
                    BoneAnimData animData = sampleAnimation(
                            layer.getPreviousAnimation(),
                            boneName,
                            layer.getAnimationTime()
                    );

                    if (animData != null) {
                        blendInTransform(finalRotation, finalTranslation, finalScale,
                                animData, weight, layer.getBlendMode(), totalWeight);
                        totalWeight += weight;
                        hasAnyAnimation = true;
                    }
                }
            }
        }

        if (!hasAnyAnimation) {
            return null; // No animation affects this bone
        }

        // Normalize for LINEAR blend mode if needed
        if (totalWeight > 0 && totalWeight != 1.0f) {
            // This happens with LINEAR blending when weights don't sum to 1
            // We don't normalize for ADDITIVE or OVERRIDE modes
        }

        return new ModelRenderer.BoneTransform(finalRotation, finalTranslation, finalScale);
    }

    /**
     * Samples an animation at a specific time for a specific bone.
     * Returns the interpolated transform values.
     */
    private BoneAnimData sampleAnimation(AnimationData animation, String boneName, float time) {
        if (animation == null || animation.boneAnimations() == null) {
            return null;
        }

        BoneAnimation boneAnim = animation.getBoneAnimation(boneName);
        if (boneAnim == null) {
            return null; // This animation doesn't animate this bone
        }

        Vector3f rotation = sampleRotation(boneAnim, time);
        Vector3f translation = sampleTranslation(boneAnim, time);
        Vector3f scale = sampleScale(boneAnim, time);

        return new BoneAnimData(rotation, translation, scale);
    }

    /**
     * Blends animation data into the final transform accumulator.
     */
    private void blendInTransform(Vector3f outRotation, Vector3f outTranslation, Vector3f outScale,
                                  BoneAnimData animData, float weight, BlendMode blendMode,
                                  float currentTotalWeight) {
        switch (blendMode) {
            case LINEAR -> {
                // Linear interpolation
                outRotation.lerp(animData.rotation, weight);
                outTranslation.lerp(animData.translation, weight);
                outScale.lerp(animData.scale, weight);
            }
            case ADDITIVE -> {
                // Additive blending
                outRotation.add(new Vector3f(animData.rotation).mul(weight));
                outTranslation.add(new Vector3f(animData.translation).mul(weight));
                outScale.add(new Vector3f(animData.scale).sub(1, 1, 1).mul(weight));
            }
            case OVERRIDE -> {
                // Override mode - highest weight wins
                if (weight > currentTotalWeight) {
                    outRotation.set(animData.rotation);
                    outTranslation.set(animData.translation);
                    outScale.set(animData.scale);
                }
            }
        }
    }

    // ========================================================================
    // KEYFRAME SAMPLING (unchanged logic)
    // ========================================================================

    private Vector3f sampleRotation(BoneAnimation boneAnim, float time) {
        if (!boneAnim.hasRotation()) {
            return new Vector3f(0, 0, 0);
        }

        List<RotationKeyframe> keyframes = boneAnim.rotation();
        if (keyframes.isEmpty()) {
            return new Vector3f(0, 0, 0);
        }

        RotationKeyframe kf1 = null, kf2 = null;
        for (int i = 0; i < keyframes.size() - 1; i++) {
            if (time >= keyframes.get(i).getTimestamp() &&
                    time <= keyframes.get(i + 1).getTimestamp()) {
                kf1 = keyframes.get(i);
                kf2 = keyframes.get(i + 1);
                break;
            }
        }

        if (kf1 == null) {
            if (time < keyframes.get(0).getTimestamp()) {
                return new Vector3f(keyframes.get(0).getRotation());
            } else {
                return new Vector3f(keyframes.get(keyframes.size() - 1).getRotation());
            }
        }

        float timeDiff = kf2.getTimestamp() - kf1.getTimestamp();
        if (timeDiff <= 0.0001f) {
            return new Vector3f(kf2.getRotation());
        }

        float t = (time - kf1.getTimestamp()) / timeDiff;
        float easedT = EasingUtil.ease(t, kf2.getEasing());

        return new Vector3f(kf1.getRotation()).lerp(kf2.getRotation(), easedT);
    }

    private Vector3f sampleTranslation(BoneAnimation boneAnim, float time) {
        if (!boneAnim.hasTranslation()) {
            return new Vector3f(0, 0, 0);
        }

        List<TranslationKeyframe> keyframes = boneAnim.translation();
        if (keyframes.isEmpty()) {
            return new Vector3f(0, 0, 0);
        }

        TranslationKeyframe kf1 = null, kf2 = null;
        for (int i = 0; i < keyframes.size() - 1; i++) {
            if (time >= keyframes.get(i).getTimestamp() &&
                    time <= keyframes.get(i + 1).getTimestamp()) {
                kf1 = keyframes.get(i);
                kf2 = keyframes.get(i + 1);
                break;
            }
        }

        if (kf1 == null) {
            if (time < keyframes.get(0).getTimestamp()) {
                return new Vector3f(keyframes.get(0).getTranslation());
            } else {
                return new Vector3f(keyframes.get(keyframes.size() - 1).getTranslation());
            }
        }

        float timeDiff = kf2.getTimestamp() - kf1.getTimestamp();
        if (timeDiff <= 0.0001f) {
            return new Vector3f(kf2.getTranslation());
        }

        float t = (time - kf1.getTimestamp()) / timeDiff;
        float easedT = EasingUtil.ease(t, kf2.getEasing());

        return new Vector3f(kf1.getTranslation()).lerp(kf2.getTranslation(), easedT);
    }

    private Vector3f sampleScale(BoneAnimation boneAnim, float time) {
        if (!boneAnim.hasScale()) {
            return new Vector3f(1, 1, 1);
        }

        List<ScaleKeyframe> keyframes = boneAnim.scale();
        if (keyframes.isEmpty()) {
            return new Vector3f(1, 1, 1);
        }

        ScaleKeyframe kf1 = null, kf2 = null;
        for (int i = 0; i < keyframes.size() - 1; i++) {
            if (time >= keyframes.get(i).getTimestamp() &&
                    time <= keyframes.get(i + 1).getTimestamp()) {
                kf1 = keyframes.get(i);
                kf2 = keyframes.get(i + 1);
                break;
            }
        }

        if (kf1 == null) {
            if (time < keyframes.get(0).getTimestamp()) {
                return new Vector3f(keyframes.get(0).getScale());
            } else {
                return new Vector3f(keyframes.get(keyframes.size() - 1).getScale());
            }
        }

        float timeDiff = kf2.getTimestamp() - kf1.getTimestamp();
        if (timeDiff <= 0.0001f) {
            return new Vector3f(kf2.getScale());
        }

        float t = (time - kf1.getTimestamp()) / timeDiff;
        float easedT = EasingUtil.ease(t, kf2.getEasing());

        return new Vector3f(kf1.getScale()).lerp(kf2.getScale(), easedT);
    }

    // ========================================================================
    // TIME UPDATE (called each tick)
    // ========================================================================

    public void tick(float deltaTime) {
        Map<String, Integer> previousLoops = new HashMap<>();
        Map<String, Boolean> wasPlaying = new HashMap<>();

        // Capture state before update
        for (Map.Entry<String, AnimationLayer> entry : layers.entrySet()) {
            AnimationLayer layer = entry.getValue();
            if (layer != null) {
                previousLoops.put(entry.getKey(), layer.getLoopCount());
                wasPlaying.put(entry.getKey(), layer.isPlaying());
            }
        }

        // Update all layers
        for (AnimationLayer layer : layers.values()) {
            if (layer.isActive()) {
                float prevTime = layer.getAnimationTime();
                layer.tick(deltaTime);
                float newTime = layer.getAnimationTime();

                // Process animation events
                if (layer.getCurrentAnimation() != null) {
                    processEventsInRange(layer.getCurrentAnimation(), layer.getName(),
                            prevTime, newTime, layer.getLoopCount());
                }
            }
        }

        // Process lifecycle events
        processLifecycleEvents(previousLoops, wasPlaying);
    }

    private void processEventsInRange(AnimationData animation, String layerName,
                                      float startTime, float endTime, int loopCount) {
        for (AnimationEvent event : animation.events()) {
            float eventTime = event.timestamp();

            if (eventTime > startTime && eventTime <= endTime) {
                fireAnimationEvent(layerName, animation, event, loopCount);
            }
        }
    }

    private void processLifecycleEvents(Map<String, Integer> previousLoops, Map<String, Boolean> wasPlaying) {
        for (Map.Entry<String, AnimationLayer> entry : layers.entrySet()) {
            String layerName = entry.getKey();
            AnimationLayer layer = entry.getValue();

            if (layer == null) continue;

            int prevLoop = previousLoops.getOrDefault(layerName, 0);
            int currLoop = layer.getLoopCount();
            if (currLoop > prevLoop && layer.getCurrentAnimation() != null) {
                fireAnimationLoopCallbacks(layerName, layer.getCurrentAnimation(), currLoop);
            }

            boolean prevPlaying = wasPlaying.getOrDefault(layerName, false);
            if (prevPlaying && !layer.isPlaying() && !layer.isTransitioning() && layer.getCurrentAnimation() != null) {
                fireAnimationEndCallbacks(layerName, layer.getCurrentAnimation());
            }
        }
    }

    // ========================================================================
    // CALLBACKS & EVENTS (unchanged)
    // ========================================================================

    private void fireAnimationStartCallbacks(String layerName, AnimationData animation) {
        for (AnimationCallback callback : callbacks) {
            try {
                callback.onAnimationStart(layerName, animation);
            } catch (Exception e) {
                LOGGER.error("Error in animation start callback", e);
            }
        }
    }

    private void fireAnimationLoopCallbacks(String layerName, AnimationData animation, int loopCount) {
        for (AnimationCallback callback : callbacks) {
            try {
                callback.onAnimationLoop(layerName, animation, loopCount);
            } catch (Exception e) {
                LOGGER.error("Error in animation loop callback", e);
            }
        }
    }

    private void fireAnimationEndCallbacks(String layerName, AnimationData animation) {
        for (AnimationCallback callback : callbacks) {
            try {
                callback.onAnimationEnd(layerName, animation);
            } catch (Exception e) {
                LOGGER.error("Error in animation end callback", e);
            }
        }
    }

    private void fireTransitionStartCallbacks(String layerName, AnimationData from,
                                              AnimationData to, float duration) {
        for (AnimationCallback callback : callbacks) {
            try {
                callback.onAnimationTransitionStart(layerName, from, to, duration);
            } catch (Exception e) {
                LOGGER.error("Error in transition start callback", e);
            }
        }
    }

    private void fireTransitionEndCallbacks(String layerName, AnimationData animation) {
        for (AnimationCallback callback : callbacks) {
            try {
                callback.onAnimationTransitionEnd(layerName, animation);
            } catch (Exception e) {
                LOGGER.error("Error in transition end callback", e);
            }
        }
    }

    private void fireAnimationEvent(String layerName, AnimationData animation,
                                    AnimationEvent event, int loopCount) {
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

    // ========================================================================
    // UTILITY METHODS
    // ========================================================================

    public AnimationLayer getOrCreateLayer(String name, float weight) {
        if (name == null) {
            throw new IllegalArgumentException("Layer name cannot be null");
        }
        return layers.computeIfAbsent(name, n -> new AnimationLayer(n, weight));
    }

    public void setLayerWeight(String layerName, float weight) {
        AnimationLayer layer = layers.get(layerName);
        if (layer != null) {
            layer.setWeight(weight);
        } else {
            LOGGER.warn("Attempted to set weight on non-existent layer: {}", layerName);
        }
    }

    public void setLayerPlaybackSpeed(String layerName, float speed) {
        AnimationLayer layer = layers.get(layerName);
        if (layer != null) {
            layer.setPlaybackSpeed(speed);
        }
    }

    public void setLayerMask(String layerName, AnimationMask mask) {
        if (mask == null) {
            layerMasks.remove(layerName);
        } else {
            layerMasks.put(layerName, mask);
        }
    }

    public AnimationMask getLayerMask(String layerName) {
        return layerMasks.get(layerName);
    }

    public boolean isPlaying() {
        return layers.values().stream().anyMatch(AnimationLayer::isActive);
    }

    public boolean isPlaying(String layerName) {
        AnimationLayer layer = layers.get(layerName);
        return layer != null && layer.isActive();
    }

    public boolean isPlaying(String layerName, String animName) {
        AnimationLayer layer = layers.get(layerName);
        return layer != null && layer.isActive() && layer.getCurrentAnimation().name().equals(animName);
    }

    public float getAnimationTime(String layerName) {
        AnimationLayer layer = layers.get(layerName);
        return layer != null ? layer.getAnimationTime() : 0.0f;
    }

    public AnimationData getCurrentAnimation(String layerName) {
        AnimationLayer layer = layers.get(layerName);
        return layer != null ? layer.getCurrentAnimation() : null;
    }

    public Collection<AnimationLayer> getLayers() {
        return Collections.unmodifiableCollection(layers.values());
    }

    public void reset() {
        stopAll(0.0f);
        layerMasks.clear();
    }

    public void addCallback(AnimationCallback callback) {
        if (callback != null) {
            callbacks.add(callback);
        }
    }

    public void removeCallback(AnimationCallback callback) {
        callbacks.remove(callback);
    }

    public void addEventListener(AnimationEventListener listener) {
        if (listener != null) {
            eventListeners.add(listener);
        }
    }

    public void removeEventListener(AnimationEventListener listener) {
        eventListeners.remove(listener);
    }

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

    public class PlaybackHandle {
        private final String layerName;
        private final AnimationData animation;

        PlaybackHandle(String layerName, AnimationData animation) {
            this.layerName = layerName;
            this.animation = animation;
        }

        public PlaybackHandle withMask(AnimationMask mask) {
            setLayerMask(layerName, mask);
            return this;
        }

        public PlaybackHandle withWeight(float weight) {
            setLayerWeight(layerName, weight);
            return this;
        }

        public String getLayerName() {
            return layerName;
        }

        public AnimationData getAnimation() {
            return animation;
        }
    }

    /**
     * Simple data holder for bone animation data at a specific time.
     */
    private record BoneAnimData(Vector3f rotation, Vector3f translation, Vector3f scale) {
    }
}