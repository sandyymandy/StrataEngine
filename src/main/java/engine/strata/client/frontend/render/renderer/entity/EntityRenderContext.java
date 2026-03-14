package engine.strata.client.frontend.render.renderer.entity;

import engine.strata.client.frontend.render.animation.AnimationProcessor;
import engine.strata.util.Identifier;
import org.joml.Vector2f;
import org.joml.Vector4f;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Per-entity render customisation context.
 *
 * <p>Holds per-bone and global overrides for tint, UV offset, emissiveness,
 * visibility, and texture swaps. Also carries the live {@link AnimationProcessor}
 * that drives bone transforms each frame.
 */
public class EntityRenderContext {

    private AnimationProcessor animationProcessor;

    private final Map<String, Vector4f> boneTints = new HashMap<>();
    private final Map<String, Vector2f> boneUVOffsets = new HashMap<>();
    private final Map<String, Float> boneEmissiveness = new HashMap<>();
    private final Map<String, Boolean> boneVisibility = new HashMap<>();
    private final Map<String, Identifier> textureOverrides = new HashMap<>();

    private Vector4f globalTint = new Vector4f(1, 1, 1, 1);
    private float globalEmissive = 0.0f;
    private Vector2f globalUVOffset = new Vector2f(0, 0);

    // Animation processor

    public void setAnimationProcessor(AnimationProcessor processor) { this.animationProcessor = processor; }
    public AnimationProcessor getAnimationProcessor() { return animationProcessor; }

    // Bone tinting

    public void setBoneTint(String boneName, float r, float g, float b, float a) {
        boneTints.put(boneName, new Vector4f(r, g, b, a));
    }

    public void setBoneTint(String boneName, Vector4f tint) {
        boneTints.put(boneName, new Vector4f(tint));
    }

    public Vector4f getBoneTint(String boneName) {
        return boneTints.getOrDefault(boneName, new Vector4f(1, 1, 1, 1));
    }

    public void clearBoneTint(String boneName) { boneTints.remove(boneName); }

    // UV offset

    public void setBoneUVOffset(String boneName, float u, float v) {
        boneUVOffsets.put(boneName, new Vector2f(u, v));
    }

    public Vector2f getBoneUVOffset(String boneName) {
        return boneUVOffsets.getOrDefault(boneName, new Vector2f(0, 0));
    }

    public void clearBoneUVOffset(String boneName) { boneUVOffsets.remove(boneName); }

    // Emissiveness

    public void setBoneEmissive(String boneName, float intensity) {
        boneEmissiveness.put(boneName, Math.max(0.0f, Math.min(1.0f, intensity)));
    }

    public float getBoneEmissive(String boneName) {
        return boneEmissiveness.getOrDefault(boneName, 0.0f);
    }

    public void clearBoneEmissive(String boneName) { boneEmissiveness.remove(boneName); }

    // Bone visibility

    public void setBoneVisible(String boneName, boolean visible) { boneVisibility.put(boneName, visible); }
    public boolean isBoneVisible(String boneName) { return boneVisibility.getOrDefault(boneName, true); }

    // Texture overrides

    public void setTextureOverride(String textureSlot, Identifier textureId) {
        textureOverrides.put(textureSlot, textureId);
    }

    public Identifier getTextureOverride(String textureSlot) { return textureOverrides.get(textureSlot); }
    public void clearTextureOverride(String textureSlot) { textureOverrides.remove(textureSlot); }
    public boolean hasTextureOverride(String textureSlot) { return textureOverrides.containsKey(textureSlot); }

    // Global effects

    public void setGlobalTint(float r, float g, float b, float a) { globalTint.set(r, g, b, a); }
    public Vector4f getGlobalTint() { return new Vector4f(globalTint); }

    public void setGlobalEmissive(float intensity) { globalEmissive = Math.max(0.0f, Math.min(1.0f, intensity)); }
    public float getGlobalEmissive() { return globalEmissive; }

    public void setGlobalUVOffset(float u, float v) { globalUVOffset.set(u, v); }
    public Vector2f getGlobalUVOffset() { return new Vector2f(globalUVOffset); }

    // Utility

    public void reset() {
        boneTints.clear();
        boneUVOffsets.clear();
        boneEmissiveness.clear();
        boneVisibility.clear();
        textureOverrides.clear();
        globalTint.set(1, 1, 1, 1);
        globalEmissive = 0.0f;
        globalUVOffset.set(0, 0);
    }

    public boolean hasCustomization() {
        return !boneTints.isEmpty()
                || !boneUVOffsets.isEmpty()
                || !boneEmissiveness.isEmpty()
                || !boneVisibility.isEmpty()
                || !textureOverrides.isEmpty()
                || !globalTint.equals(1, 1, 1, 1)
                || globalEmissive != 0.0f
                || !globalUVOffset.equals(0, 0);
    }

    public EntityRenderContext copy() {
        EntityRenderContext copy = new EntityRenderContext();
        boneTints.forEach((k, v) -> copy.boneTints.put(k, new Vector4f(v)));
        boneUVOffsets.forEach((k, v) -> copy.boneUVOffsets.put(k, new Vector2f(v)));
        copy.boneEmissiveness.putAll(boneEmissiveness);
        copy.boneVisibility.putAll(boneVisibility);
        copy.textureOverrides.putAll(textureOverrides);
        copy.globalTint = new Vector4f(globalTint);
        copy.globalEmissive = globalEmissive;
        copy.globalUVOffset = new Vector2f(globalUVOffset);
        return copy;
    }

    // Convenience getters

    public Map<String, Vector4f> getAllBoneTints() { return new HashMap<>(boneTints); }
    public Map<String, Vector2f> getAllBoneUVOffsets() { return new HashMap<>(boneUVOffsets); }
    public Map<String, Float> getAllBoneEmissiveness() { return new HashMap<>(boneEmissiveness); }
    public Map<String, Boolean> getAllBoneVisibility() { return new HashMap<>(boneVisibility); }
    public Map<String, Identifier> getAllTextureOverrides() { return new HashMap<>(textureOverrides); }

    @Override
    public int hashCode() {
        return Objects.hash(boneTints, boneUVOffsets, boneEmissiveness, boneVisibility,
                textureOverrides, globalTint, globalEmissive, globalUVOffset);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof EntityRenderContext other)) return false;
        return Objects.equals(boneTints, other.boneTints)
                && Objects.equals(boneUVOffsets, other.boneUVOffsets)
                && Objects.equals(boneEmissiveness, other.boneEmissiveness)
                && Objects.equals(boneVisibility, other.boneVisibility)
                && Objects.equals(textureOverrides, other.textureOverrides)
                && Objects.equals(globalTint, other.globalTint)
                && Objects.equals(globalEmissive, other.globalEmissive)
                && Objects.equals(globalUVOffset, other.globalUVOffset);
    }
}