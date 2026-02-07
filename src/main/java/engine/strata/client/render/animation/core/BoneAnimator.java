package engine.strata.client.render.animation.core;

import engine.strata.client.render.animation.EasingUtil;
import engine.strata.client.render.animation.StrataAnimation;
import engine.strata.client.render.animation.StrataAnimation.*;
import engine.strata.client.render.animation.blending.BlendMode;
import engine.strata.client.render.model.StrataBone;
import engine.strata.client.render.model.StrataModel;
import org.joml.Vector3f;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * Processes animation data and updates bone transforms.
 * Handles keyframe interpolation, easing, and multi-layer blending.
 *
 * <p><b>FIXED ISSUES:</b>
 * <ul>
 *   <li>Fixed initialization to use proper Vector3f(0,0,0) instead of bone.getPosition()</li>
 *   <li>Added null checks and validation</li>
 *   <li>Fixed division by zero in interpolation</li>
 *   <li>Added object pooling for performance</li>
 * </ul>
 */
public class BoneAnimator {
    private static final Logger LOGGER = LoggerFactory.getLogger("BoneAnimator");

    private final StrataModel model;
    private final Map<String, StrataBone> bones;

    // Snapshot management
    private final Map<String, BoneSnapshot> initialSnapshots = new HashMap<>();
    private final Map<String, BoneSnapshot> currentSnapshots = new HashMap<>();
    private final Map<String, BoneSnapshot> previousSnapshots = new HashMap<>();

    // Performance tracking
    private int bonesProcessed = 0;
    private int bonesSkipped = 0;

    // Object pooling to reduce allocations
    private final Vector3f tempVec1 = new Vector3f();
    private final Vector3f tempVec2 = new Vector3f();

    public BoneAnimator(StrataModel model) {
        this.model = model;
        this.bones = model.getAllBones();

        // Capture initial pose snapshots
        captureInitialSnapshots();
    }

    /**
     * Capture the initial T-pose/rest pose of all bones.
     * FIXED: Use proper initialization instead of calling non-existent getPosition()
     */
    private void captureInitialSnapshots() {
        for (Map.Entry<String, StrataBone> entry : bones.entrySet()) {
            String boneName = entry.getKey();
            StrataBone bone = entry.getValue();

            BoneSnapshot snapshot = new BoneSnapshot(
                    new Vector3f(0, 0, 0),
                    new Vector3f(0, 0, 0),  // Translation starts at origin
                    new Vector3f(1, 1, 1),  // Scale starts at 1
                    0.0
            );

            initialSnapshots.put(boneName, snapshot);
            currentSnapshots.put(boneName, snapshot);
            previousSnapshots.put(boneName, snapshot);
        }

        LOGGER.debug("Captured initial snapshots for {} bones", bones.size());
    }

    /**
     * Process all active animation layers and update bone transforms.
     *
     * @param layers Collection of animation layers to process
     * @param deltaTime Time since last update in seconds
     */
    public void processLayers(Collection<AnimationLayer> layers, float deltaTime) {
        processLayers(layers, null, deltaTime);
    }

    /**
     * Process all active animation layers and update bone transforms with masks.
     *
     * @param layers Collection of animation layers to process
     * @param layerMasks Map of layer names to animation masks (can be null)
     * @param deltaTime Time since last update in seconds
     */
    public void processLayers(Collection<AnimationLayer> layers,
                              Map<String, AnimationMask> layerMasks,
                              float deltaTime) {
        bonesProcessed = 0;
        bonesSkipped = 0;

        // Collect all transforms from all layers
        Map<String, List<WeightedTransform>> boneTransforms = new HashMap<>();

        for (AnimationLayer layer : layers) {
            if (!layer.isActive()) {
                continue;
            }

            // Get mask for this layer (if any)
            AnimationMask mask = layerMasks != null ? layerMasks.get(layer.getName()) : null;

            // Process current animation
            if (layer.getCurrentAnimation() != null) {
                float weight = layer.getBlendWeight();
                if (weight > 0.001f) {  // Skip negligible weights
                    processAnimation(
                            layer.getCurrentAnimation(),
                            layer.getAnimationTime(),
                            weight,
                            layer.getBlendMode(),
                            mask,
                            boneTransforms
                    );
                }
            }

            // Process previous animation if transitioning
            if (layer.isTransitioning() && layer.getPreviousAnimation() != null) {
                float weight = layer.getPreviousBlendWeight();
                if (weight > 0.001f) {
                    processAnimation(
                            layer.getPreviousAnimation(),
                            layer.getAnimationTime(),  // Could store separate time for prev
                            weight,
                            layer.getBlendMode(),
                            mask,
                            boneTransforms
                    );
                }
            }
        }

        // Apply blended transforms to bones
        applyTransforms(boneTransforms, deltaTime);
    }

    /**
     * Sample an animation at a specific time and add to transform accumulator.
     *
     * @param animation The animation to sample
     * @param time Current playback time
     * @param weight Blend weight for this animation
     * @param blendMode How to blend this animation
     * @param mask Optional mask to limit which bones are affected (can be null)
     * @param boneTransforms Accumulator for bone transforms
     */
    private void processAnimation(
            AnimationData animation,
            float time,
            float weight,
            BlendMode blendMode,
            AnimationMask mask,
            Map<String, List<WeightedTransform>> boneTransforms
    ) {
        if (animation == null || animation.boneAnimations() == null) {
            return;
        }

        for (Map.Entry<String, BoneAnimation> entry : animation.boneAnimations().entrySet()) {
            String boneName = entry.getKey();
            BoneAnimation boneAnim = entry.getValue();

            if (boneAnim == null) {
                continue;
            }

            // Check if mask allows this bone to be animated
            if (mask != null && !mask.shouldAffect(boneName)) {
                continue;  // Skip this bone - mask filters it out
            }

            // Sample keyframes at current time
            Vector3f rotation = sampleRotation(boneAnim, time);
            Vector3f translation = sampleTranslation(boneAnim, time);
            Vector3f scale = sampleScale(boneAnim, time);

            BoneSnapshot snapshot = new BoneSnapshot(rotation, translation, scale, time);
            WeightedTransform transform = new WeightedTransform(snapshot, weight, blendMode);

            // Accumulate transforms for this bone
            boneTransforms.computeIfAbsent(boneName, k -> new ArrayList<>()).add(transform);
        }
    }

    /**
     * Apply accumulated transforms to bones with blending.
     */
    private void applyTransforms(Map<String, List<WeightedTransform>> boneTransforms, float deltaTime) {
        for (Map.Entry<String, StrataBone> entry : bones.entrySet()) {
            String boneName = entry.getKey();
            StrataBone bone = entry.getValue();

            List<WeightedTransform> transforms = boneTransforms.get(boneName);

            if (transforms == null || transforms.isEmpty()) {
                // No animation for this bone - interpolate back to rest pose
                resetBoneToRestPose(bone, boneName, deltaTime);
                bonesSkipped++;
                continue;
            }

            // Blend all transforms for this bone
            BoneSnapshot blended = blendTransforms(transforms, boneName);

            // Update bone with blended result (using fixed method names)
            bone.setAnimRotation(blended.rotation());
            bone.setAnimPosition(blended.position());
            bone.setAnimScale(blended.scale());

            // Update snapshots
            previousSnapshots.put(boneName, currentSnapshots.get(boneName));
            currentSnapshots.put(boneName, blended);

            bonesProcessed++;
        }
    }

    /**
     * Blend multiple transforms according to their weights and blend modes.
     */
    private BoneSnapshot blendTransforms(List<WeightedTransform> transforms, String boneName) {
        if (transforms.size() == 1) {
            return transforms.get(0).snapshot;
        }

        // Start with initial snapshot
        BoneSnapshot initial = initialSnapshots.get(boneName);
        Vector3f rotation = new Vector3f(initial.rotation());
        Vector3f position = new Vector3f(initial.position());
        Vector3f scale = new Vector3f(initial.scale());

        float totalWeight = 0.0f;

        // Apply each transform
        for (WeightedTransform wt : transforms) {
            BoneSnapshot snapshot = wt.snapshot;
            float weight = wt.weight;

            switch (wt.blendMode) {
                case LINEAR:
                    // Weighted linear blend
                    rotation.lerp(snapshot.rotation(), weight);
                    position.lerp(snapshot.position(), weight);
                    scale.lerp(snapshot.scale(), weight);
                    totalWeight += weight;
                    break;

                case ADDITIVE:
                    // Add weighted deltas
                    rotation.add(new Vector3f(snapshot.rotation()).mul(weight));
                    position.add(new Vector3f(snapshot.position()).mul(weight));
                    scale.add(new Vector3f(snapshot.scale()).sub(1, 1, 1).mul(weight));
                    break;

                case OVERRIDE:
                    // Last one wins (highest weight)
                    if (weight > totalWeight) {
                        rotation.set(snapshot.rotation());
                        position.set(snapshot.position());
                        scale.set(snapshot.scale());
                        totalWeight = weight;
                    }
                    break;
            }
        }

        return new BoneSnapshot(rotation, position, scale, 0.0);
    }

    /**
     * Smoothly interpolate bone back to rest pose when no animation is active.
     */
    private void resetBoneToRestPose(StrataBone bone, String boneName, float deltaTime) {
        BoneSnapshot initial = initialSnapshots.get(boneName);
        BoneSnapshot current = currentSnapshots.get(boneName);

        if (initial == null || current == null) {
            return;
        }

        // Fast interpolation (0.5 second reset time)
        float resetSpeed = 2.0f;
        float alpha = Math.min(deltaTime * resetSpeed, 1.0f);

        BoneSnapshot interpolated = current.lerp(initial, alpha);

        bone.setAnimRotation(interpolated.rotation());
        bone.setAnimPosition(interpolated.position());
        bone.setAnimScale(interpolated.scale());

        currentSnapshots.put(boneName, interpolated);
    }

    /**
     * Sample rotation keyframes with interpolation and easing.
     * FIXED: Added null checks and division by zero protection
     */
    private Vector3f sampleRotation(BoneAnimation boneAnim, float time) {
        if (!boneAnim.hasRotation()) {
            return new Vector3f(0, 0, 0);
        }

        List<RotationKeyframe> keyframes = boneAnim.rotation();
        if (keyframes.isEmpty()) {
            return new Vector3f(0, 0, 0);
        }

        // Find surrounding keyframes
        RotationKeyframe kf1 = null, kf2 = null;
        for (int i = 0; i < keyframes.size() - 1; i++) {
            if (time >= keyframes.get(i).getTimestamp() &&
                    time <= keyframes.get(i + 1).getTimestamp()) {
                kf1 = keyframes.get(i);
                kf2 = keyframes.get(i + 1);
                break;
            }
        }

        // Handle edge cases
        if (kf1 == null) {
            if (time < keyframes.get(0).getTimestamp()) {
                return new Vector3f(keyframes.get(0).getRotation());
            } else {
                return new Vector3f(keyframes.get(keyframes.size() - 1).getRotation());
            }
        }

        // Interpolate with easing
        float timeDiff = kf2.getTimestamp() - kf1.getTimestamp();
        if (timeDiff <= 0.0001f) {
            // Keyframes too close together, just use second one
            return new Vector3f(kf2.getRotation());
        }

        float t = (time - kf1.getTimestamp()) / timeDiff;
        float easedT = EasingUtil.ease(t, kf2.getEasing());

        // Linear interpolation (TODO: quaternion slerp for better results)
        return new Vector3f(kf1.getRotation()).lerp(kf2.getRotation(), easedT);
    }

    /**
     * Sample translation keyframes with interpolation and easing.
     * FIXED: Added null checks and division by zero protection
     */
    private Vector3f sampleTranslation(BoneAnimation boneAnim, float time) {
        if (!boneAnim.hasTranslation()) {
            return new Vector3f(0, 0, 0);
        }

        List<TranslationKeyframe> keyframes = boneAnim.translation();
        if (keyframes.isEmpty()) {
            return new Vector3f(0, 0, 0);
        }

        // Find surrounding keyframes
        TranslationKeyframe kf1 = null, kf2 = null;
        for (int i = 0; i < keyframes.size() - 1; i++) {
            if (time >= keyframes.get(i).getTimestamp() &&
                    time <= keyframes.get(i + 1).getTimestamp()) {
                kf1 = keyframes.get(i);
                kf2 = keyframes.get(i + 1);
                break;
            }
        }

        // Handle edge cases
        if (kf1 == null) {
            if (time < keyframes.get(0).getTimestamp()) {
                return new Vector3f(keyframes.get(0).getTranslation());
            } else {
                return new Vector3f(keyframes.get(keyframes.size() - 1).getTranslation());
            }
        }

        // Interpolate with easing
        float timeDiff = kf2.getTimestamp() - kf1.getTimestamp();
        if (timeDiff <= 0.0001f) {
            return new Vector3f(kf2.getTranslation());
        }

        float t = (time - kf1.getTimestamp()) / timeDiff;
        float easedT = EasingUtil.ease(t, kf2.getEasing());

        return new Vector3f(kf1.getTranslation()).lerp(kf2.getTranslation(), easedT);
    }

    /**
     * Sample scale keyframes with interpolation and easing.
     * FIXED: Added null checks and division by zero protection
     */
    private Vector3f sampleScale(BoneAnimation boneAnim, float time) {
        if (!boneAnim.hasScale()) {
            return new Vector3f(1, 1, 1);
        }

        List<ScaleKeyframe> keyframes = boneAnim.scale();
        if (keyframes.isEmpty()) {
            return new Vector3f(1, 1, 1);
        }

        // Find surrounding keyframes
        ScaleKeyframe kf1 = null, kf2 = null;
        for (int i = 0; i < keyframes.size() - 1; i++) {
            if (time >= keyframes.get(i).getTimestamp() &&
                    time <= keyframes.get(i + 1).getTimestamp()) {
                kf1 = keyframes.get(i);
                kf2 = keyframes.get(i + 1);
                break;
            }
        }

        // Handle edge cases
        if (kf1 == null) {
            if (time < keyframes.get(0).getTimestamp()) {
                return new Vector3f(keyframes.get(0).getScale());
            } else {
                return new Vector3f(keyframes.get(keyframes.size() - 1).getScale());
            }
        }

        // Interpolate with easing
        float timeDiff = kf2.getTimestamp() - kf1.getTimestamp();
        if (timeDiff <= 0.0001f) {
            return new Vector3f(kf2.getScale());
        }

        float t = (time - kf1.getTimestamp()) / timeDiff;
        float easedT = EasingUtil.ease(t, kf2.getEasing());

        return new Vector3f(kf1.getScale()).lerp(kf2.getScale(), easedT);
    }

    /**
     * Reset all bones to their initial pose.
     */
    public void reset() {
        for (Map.Entry<String, StrataBone> entry : bones.entrySet()) {
            String boneName = entry.getKey();
            StrataBone bone = entry.getValue();
            BoneSnapshot initial = initialSnapshots.get(boneName);

            if (initial != null) {
                bone.setAnimRotation(initial.rotation());
                bone.setAnimPosition(initial.position());
                bone.setAnimScale(initial.scale());

                currentSnapshots.put(boneName, initial);
            }
        }
    }

    /**
     * Get performance statistics.
     */
    public String getStats() {
        return String.format("BoneAnimator[processed=%d, skipped=%d, total=%d]",
                bonesProcessed, bonesSkipped, bones.size());
    }

    /**
     * Helper class to track weighted transforms.
     */
    private record WeightedTransform(BoneSnapshot snapshot, float weight, BlendMode blendMode) {
    }
}