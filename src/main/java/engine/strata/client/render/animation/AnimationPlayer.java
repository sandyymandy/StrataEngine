package engine.strata.client.render.animation;

import engine.strata.client.render.model.StrataModel;
import org.joml.Vector3f;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Manages animation playback for a single model instance.
 * Handles interpolation, blending, and animation events.
 */
public class AnimationPlayer {
    private static final Logger LOGGER = LoggerFactory.getLogger("AnimationPlayer");

    private final StrataModel model;
    private final StrataAnimation animationContainer;

    // Current animation state
    private StrataAnimation.AnimationData currentAnimation;
    private float currentTime;
    private float playbackSpeed;
    private boolean playing;
    private boolean reversed; // For pingpong mode

    // Blending state
    private StrataAnimation.AnimationData previousAnimation;
    private float blendTime;
    private float blendDuration;

    // Event tracking
    private final List<AnimationEventListener> eventListeners;
    private float lastEventCheckTime;

    public AnimationPlayer(StrataModel model, StrataAnimation animationContainer) {
        this.model = model;
        this.animationContainer = animationContainer;
        this.playbackSpeed = 1.0f;
        this.playing = false;
        this.currentTime = 0.0f;
        this.eventListeners = new ArrayList<>();
    }

    /**
     * Updates the animation state and applies transformations to the model.
     *
     * @param deltaTime Time elapsed since last update (in seconds)
     */
    public void update(float deltaTime) {
        if (!playing || currentAnimation == null) {
            return;
        }

        float prevTime = currentTime;

        // Update time based on playback direction
        if (reversed) {
            currentTime -= deltaTime * playbackSpeed;
        } else {
            currentTime += deltaTime * playbackSpeed;
        }

        // Handle looping
        if (currentAnimation.loop()) {
            handleLooping();
        } else {
            // Clamp to animation duration
            currentTime = Math.max(0, Math.min(currentTime, currentAnimation.duration()));

            // Stop at end
            if (currentTime >= currentAnimation.duration() || currentTime <= 0) {
                playing = false;
            }
        }

        // Update blend state
        if (blendDuration > 0) {
            blendTime += deltaTime;
            if (blendTime >= blendDuration) {
                // Blend complete
                previousAnimation = null;
                blendDuration = 0;
                blendTime = 0;
            }
        }

        // Apply animation to model bones
        applyAnimation();

        // Check for animation events
        checkEvents(prevTime, currentTime);
    }

    /**
     * Handles looping logic based on loop mode.
     */
    private void handleLooping() {
        float duration = currentAnimation.duration();

        switch (currentAnimation.loopMode()) {
            case RESTART -> {
                if (currentTime >= duration) {
                    currentTime = currentTime % duration;
                } else if (currentTime < 0) {
                    currentTime = duration + (currentTime % duration);
                }
            }
            case PINGPONG -> {
                if (currentTime >= duration) {
                    currentTime = duration - (currentTime - duration);
                    reversed = true;
                } else if (currentTime < 0) {
                    currentTime = -currentTime;
                    reversed = false;
                }
            }
        }
    }

    /**
     * Applies the current animation state to the model bones.
     */
    private void applyAnimation() {
        Map<String, StrataModel.Bone> bones = model.getAllBones();

        // Calculate blend factor if blending
        float blendFactor = 0.0f;
        if (blendDuration > 0 && previousAnimation != null) {
            blendFactor = Math.min(1.0f, blendTime / blendDuration);
        }

        // Apply animation to each bone
        for (Map.Entry<String, StrataModel.Bone> entry : bones.entrySet()) {
            String boneName = entry.getKey();
            StrataModel.Bone bone = entry.getValue();

            // Get current animation data for this bone
            StrataAnimation.BoneAnimation boneAnim = currentAnimation.getBoneAnimation(boneName);

            if (boneAnim != null) {
                // Apply rotation
                if (boneAnim.hasRotation()) {
                    Vector3f rotation = interpolateRotation(boneAnim.rotation(), currentTime);

                    // Blend with previous animation if needed
                    if (blendFactor > 0 && previousAnimation != null) {
                        StrataAnimation.BoneAnimation prevBoneAnim = previousAnimation.getBoneAnimation(boneName);
                        if (prevBoneAnim != null && prevBoneAnim.hasRotation()) {
                            Vector3f prevRotation = interpolateRotation(prevBoneAnim.rotation(), 0);
                            rotation = lerpVector3f(prevRotation, rotation, blendFactor);
                        }
                    }

                    bone.setAnimRotation(rotation.x, rotation.y, rotation.z);
                }

                // Apply translation
                if (boneAnim.hasTranslation()) {
                    Vector3f translation = interpolateTranslation(boneAnim.translation(), currentTime);

                    // Blend with previous animation if needed
                    if (blendFactor > 0 && previousAnimation != null) {
                        StrataAnimation.BoneAnimation prevBoneAnim = previousAnimation.getBoneAnimation(boneName);
                        if (prevBoneAnim != null && prevBoneAnim.hasTranslation()) {
                            Vector3f prevTranslation = interpolateTranslation(prevBoneAnim.translation(), 0);
                            translation = lerpVector3f(prevTranslation, translation, blendFactor);
                        }
                    }

                    bone.setAnimTranslation(translation.x, translation.y, translation.z);
                }

                // Apply scale
                if (boneAnim.hasScale()) {
                    Vector3f scale = interpolateScale(boneAnim.scale(), currentTime);

                    // Blend with previous animation if needed
                    if (blendFactor > 0 && previousAnimation != null) {
                        StrataAnimation.BoneAnimation prevBoneAnim = previousAnimation.getBoneAnimation(boneName);
                        if (prevBoneAnim != null && prevBoneAnim.hasScale()) {
                            Vector3f prevScale = interpolateScale(prevBoneAnim.scale(), 0);
                            scale = lerpVector3f(prevScale, scale, blendFactor);
                        }
                    }

                    bone.setAnimScale(scale.x, scale.y, scale.z);
                }
            }
        }
    }

    /**
     * Interpolates rotation keyframes at a given time.
     */
    private Vector3f interpolateRotation(List<StrataAnimation.RotationKeyframe> keyframes, float time) {
        if (keyframes.isEmpty()) {
            return new Vector3f(0, 0, 0);
        }

        // Find surrounding keyframes
        KeyframePair<StrataAnimation.RotationKeyframe> pair = findKeyframes(keyframes, time);

        if (pair.next == null) {
            // Past the last keyframe, use its value
            return new Vector3f(pair.current.getRotation());
        }

        // Calculate interpolation factor with easing
        float t = (time - pair.current.getTimestamp()) /
                (pair.next.getTimestamp() - pair.current.getTimestamp());
        t = EasingUtil.ease(t, pair.current.getEasing());

        // Interpolate rotation (using shortest path for angles)
        return lerpAngles(pair.current.getRotation(), pair.next.getRotation(), t);
    }

    /**
     * Interpolates translation keyframes at a given time.
     */
    private Vector3f interpolateTranslation(List<StrataAnimation.TranslationKeyframe> keyframes, float time) {
        if (keyframes.isEmpty()) {
            return new Vector3f(0, 0, 0);
        }

        KeyframePair<StrataAnimation.TranslationKeyframe> pair = findKeyframes(keyframes, time);

        if (pair.next == null) {
            return new Vector3f(pair.current.getTranslation());
        }

        float t = (time - pair.current.getTimestamp()) /
                (pair.next.getTimestamp() - pair.current.getTimestamp());
        t = EasingUtil.ease(t, pair.current.getEasing());

        return lerpVector3f(pair.current.getTranslation(), pair.next.getTranslation(), t);
    }

    /**
     * Interpolates scale keyframes at a given time.
     */
    private Vector3f interpolateScale(List<StrataAnimation.ScaleKeyframe> keyframes, float time) {
        if (keyframes.isEmpty()) {
            return new Vector3f(1, 1, 1);
        }

        KeyframePair<StrataAnimation.ScaleKeyframe> pair = findKeyframes(keyframes, time);

        if (pair.next == null) {
            return new Vector3f(pair.current.getScale());
        }

        float t = (time - pair.current.getTimestamp()) /
                (pair.next.getTimestamp() - pair.current.getTimestamp());
        t = EasingUtil.ease(t, pair.current.getEasing());

        return lerpVector3f(pair.current.getScale(), pair.next.getScale(), t);
    }

    /**
     * Finds the two keyframes surrounding the given time.
     */
    private <T extends StrataAnimation.Keyframe> KeyframePair<T> findKeyframes(List<T> keyframes, float time) {
        // Handle edge cases
        if (keyframes.size() == 1) {
            return new KeyframePair<>(keyframes.get(0), null);
        }

        // Find the keyframes that surround the current time
        for (int i = 0; i < keyframes.size() - 1; i++) {
            T current = keyframes.get(i);
            T next = keyframes.get(i + 1);

            if (time >= current.getTimestamp() && time <= next.getTimestamp()) {
                return new KeyframePair<>(current, next);
            }
        }

        // If we're past all keyframes, return the last one
        return new KeyframePair<>(keyframes.get(keyframes.size() - 1), null);
    }

    /**
     * Linear interpolation for Vector3f.
     */
    private Vector3f lerpVector3f(Vector3f a, Vector3f b, float t) {
        return new Vector3f(
                a.x + (b.x - a.x) * t,
                a.y + (b.y - a.y) * t,
                a.z + (b.z - a.z) * t
        );
    }

    /**
     * Angular interpolation (shortest path) for rotation.
     */
    private Vector3f lerpAngles(Vector3f a, Vector3f b, float t) {
        return new Vector3f(
                lerpAngle(a.x, b.x, t),
                lerpAngle(a.y, b.y, t),
                lerpAngle(a.z, b.z, t)
        );
    }

    /**
     * Interpolates between two angles using the shortest path.
     */
    private float lerpAngle(float a, float b, float t) {
        float delta = ((b - a + (float) Math.PI) % (2 * (float) Math.PI)) - (float) Math.PI;
        return a + delta * t;
    }

    /**
     * Checks for animation events that should fire between prevTime and currentTime.
     */
    private void checkEvents(float prevTime, float currentTime) {
        if (currentAnimation.events().isEmpty()) {
            return;
        }

        for (StrataAnimation.AnimationEvent event : currentAnimation.events()) {
            float eventTime = event.timestamp();

            // Check if event falls between prev and current time
            boolean shouldFire = false;

            if (!reversed && prevTime < eventTime && currentTime >= eventTime) {
                shouldFire = true;
            } else if (reversed && prevTime > eventTime && currentTime <= eventTime) {
                shouldFire = true;
            }

            if (shouldFire) {
                fireEvent(event);
            }
        }
    }

    /**
     * Fires an animation event to all listeners.
     */
    private void fireEvent(StrataAnimation.AnimationEvent event) {
        for (AnimationEventListener listener : eventListeners) {
            listener.onAnimationEvent(event);
        }
    }

    // ===== Public API Methods =====

    /**
     * Plays the specified animation.
     */
    public void play(String animationName) {
        play(animationName, 1.0f);
    }

    /**
     * Plays the specified animation with a custom speed.
     */
    public void play(String animationName, float speed) {
        StrataAnimation.AnimationData newAnim = animationContainer.getAnimation(animationName);

        if (newAnim == null) {
            LOGGER.warn("Animation not found: {}", animationName);
            return;
        }

        // Setup blending if transitioning from another animation
        if (currentAnimation != null && newAnim.blendInDuration() > 0) {
            previousAnimation = currentAnimation;
            blendDuration = newAnim.blendInDuration();
            blendTime = 0;
        }

        currentAnimation = newAnim;
        currentTime = 0;
        playbackSpeed = speed;
        playing = true;
        reversed = false;

        LOGGER.debug("Playing animation: {} (speed: {}x)", animationName, speed);
    }

    /**
     * Stops the current animation.
     */
    public void stop() {
        playing = false;
    }

    /**
     * Pauses the current animation.
     */
    public void pause() {
        playing = false;
    }

    /**
     * Resumes the current animation.
     */
    public void resume() {
        if (currentAnimation != null) {
            playing = true;
        }
    }

    /**
     * Resets the animation to the beginning.
     */
    public void reset() {
        currentTime = 0;
        reversed = false;
    }

    /**
     * Sets the playback speed (1.0 = normal, 2.0 = double speed, 0.5 = half speed).
     */
    public void setSpeed(float speed) {
        this.playbackSpeed = speed;
    }

    /**
     * Adds an event listener for animation events.
     */
    public void addEventListener(AnimationEventListener listener) {
        eventListeners.add(listener);
    }

    /**
     * Removes an event listener.
     */
    public void removeEventListener(AnimationEventListener listener) {
        eventListeners.remove(listener);
    }

    // ===== Getters =====

    public boolean isPlaying() {
        return playing;
    }

    public String getCurrentAnimationName() {
        return currentAnimation != null ? currentAnimation.name() : null;
    }

    public float getCurrentTime() {
        return currentTime;
    }

    public float getPlaybackSpeed() {
        return playbackSpeed;
    }

    /**
     * Helper record to store a pair of keyframes.
     */
    private record KeyframePair<T extends StrataAnimation.Keyframe>(T current, T next) {
    }

    /**
     * Interface for listening to animation events.
     */
    public interface AnimationEventListener {
        void onAnimationEvent(StrataAnimation.AnimationEvent event);
    }
}