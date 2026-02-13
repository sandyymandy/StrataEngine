package engine.strata.client.render.animation.core;

import engine.strata.client.render.animation.StrataAnimation;
import engine.strata.client.render.animation.blending.BlendMode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Represents a single animation layer that can play animations independently.
 *
 * Layers allow multiple animations to play simultaneously on different parts
 * of the model. For example:
 * - Base layer: Walking/running animations (affects whole body)
 * - Upper body layer: Attack/reload animations (affects arms only)
 * - Face layer: Talking/emoting (affects head only)
 *
 * This is inspired by GeckoLib's multi-controller system but simplified
 * for Strata's architecture.
 */
public class AnimationLayer {
    private static final Logger LOGGER = LoggerFactory.getLogger("AnimationLayer");
    private final String name;
    private float weight;  // Layer influence (0-1)
    private BlendMode blendMode;

    // Current animation state
    private StrataAnimation.AnimationData currentAnimation;
    private StrataAnimation.AnimationData previousAnimation;

    // Playback state
    private float animationTime;     // Current time in animation (seconds)
    private float playbackSpeed;     // Multiplier for animation speed
    private boolean isPlaying;

    // Transition blending
    private float transitionTime;    // Time spent in current transition
    private float transitionDuration;  // How long the transition should take
    private boolean isTransitioning;

    // Loop behavior
    private boolean shouldLoop;
    private int loopCount;  // For play N times behavior

    public AnimationLayer(String name, float weight) {
        this.name = name;
        this.weight = weight;
        this.blendMode = BlendMode.LINEAR;
        this.playbackSpeed = 1.0f;
        this.isPlaying = false;
    }

    /**
     * Start playing an animation on this layer
     */
    public void playAnimation(StrataAnimation.AnimationData animation, float blendDuration) {
        // Save current as previous for blending
        this.previousAnimation = this.currentAnimation;
        this.currentAnimation = animation;

        // Reset playback state
        this.animationTime = 0.0f;
        this.isPlaying = true;
        this.shouldLoop = animation.loop();
        this.loopCount = 0;

        // Setup transition if blending from previous
        if (blendDuration > 0 && previousAnimation != null) {
            this.isTransitioning = true;
            this.transitionTime = 0.0f;
            this.transitionDuration = blendDuration;
        } else {
            this.isTransitioning = false;
        }
    }

    /**
     * Stop the current animation
     */
    public void stop(float blendOutDuration) {
        if (blendOutDuration > 0) {
            // Blend to rest pose
            this.isTransitioning = true;
            this.transitionTime = 0.0f;
            this.transitionDuration = blendOutDuration;
            this.previousAnimation = this.currentAnimation;
            this.currentAnimation = null;
        } else {
            // Immediate stop
            this.isPlaying = false;
            this.currentAnimation = null;
        }
    }

    /**
     * Update animation playback
     */
    public void tick(float deltaTime) {
        if (!isPlaying && !isTransitioning) {
            return;
        }

        // Update transition blending
        if (isTransitioning) {
            transitionTime += deltaTime;

            if (transitionTime >= transitionDuration) {
                // Transition complete
                isTransitioning = false;
                previousAnimation = null;
                transitionTime = 0.0f;
            }
        }

//        // Update animation time
//        if (currentAnimation != null && isPlaying) {
//            animationTime += deltaTime * playbackSpeed;
//
//            // Handle looping
//            if (animationTime >= currentAnimation.duration()) {
//                if (shouldLoop) {
//                    switch (currentAnimation.loopMode()) {
//                        case LOOP:
//                            animationTime = animationTime % currentAnimation.duration();
//                            break;
//                        case PINGPONG:
//                            // Reverse playback direction
//                            playbackSpeed = -playbackSpeed;
//                            animationTime = currentAnimation.duration();
//                            break;
//                    }
//                    loopCount++;
//                } else {
//                    // Animation finished
//                    animationTime = currentAnimation.duration();
//                    isPlaying = false;
//                }
//            } else if (animationTime < 0 && currentAnimation.loopMode() == StrataAnimation.LoopMode.PINGPONG) {
//                // Ping-pong reached start
//                playbackSpeed = -playbackSpeed;
//                animationTime = 0;
//                loopCount++;
//            }
//        }
    }

    /**
     * Get the current blend weight for this layer.
     * Accounts for both layer weight and transition blend.
     */
    public float getBlendWeight() {
        if (!isTransitioning) {
            return weight;
        }

        if (transitionDuration <= 0) {
            return weight;  // Instant transition
        }

        float transitionAlpha = Math.min(transitionTime / transitionDuration, 1.0f);
        return weight * transitionAlpha;
    }

    /**
     * Get blend weight for previous animation during transition
     */
    public float getPreviousBlendWeight() {
        if (!isTransitioning || previousAnimation == null) {
            return 0.0f;
        }

        float transitionAlpha = Math.min(transitionTime / transitionDuration, 1.0f);
        return weight * (1.0f - transitionAlpha);
    }

    // Getters and setters

    public String getName() {
        return name;
    }

    public float getWeight() {
        return weight;
    }

    public void setWeight(float weight) {
        if (Float.isNaN(weight) || Float.isInfinite(weight)) {
            LOGGER.warn("Invalid weight value: {}, defaulting to 1.0", weight);
            this.weight = 1.0f;
            return;
        }
        this.weight = Math.max(0.0f, Math.min(1.0f, weight));
    }

    public BlendMode getBlendMode() {
        return blendMode;
    }

    public void setBlendMode(BlendMode blendMode) {
        this.blendMode = blendMode;
    }

    public StrataAnimation.AnimationData getCurrentAnimation() {
        return currentAnimation;
    }

    public StrataAnimation.AnimationData getPreviousAnimation() {
        return previousAnimation;
    }

    public float getAnimationTime() {
        return animationTime;
    }

    public void setAnimationTime(float time) {
        this.animationTime = time;
    }

    public float getPlaybackSpeed() {
        return playbackSpeed;
    }

    public void setPlaybackSpeed(float speed) {
        this.playbackSpeed = speed;
    }

    public boolean isPlaying() {
        return isPlaying;
    }

    public boolean isTransitioning() {
        return isTransitioning;
    }

    public boolean isActive() {
        return isPlaying || isTransitioning;
    }

    public int getLoopCount() {
        return loopCount;
    }

    public void setShouldLoop(boolean shouldLoop) {
        this.shouldLoop = shouldLoop;
    }

    @Override
    public String toString() {
        return String.format("AnimationLayer[%s, weight=%.2f, anim=%s, time=%.2fs, playing=%b]",
                name, weight,
                currentAnimation != null ? currentAnimation.name() : "none",
                animationTime, isPlaying
        );
    }
}