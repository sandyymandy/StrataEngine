package engine.strata.client.frontend.render.renderer.entity;

import engine.strata.client.frontend.render.animation.AnimationProcessor;
import engine.strata.util.Identifier;
import org.joml.Vector2f;
import org.joml.Vector4f;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Per-entity render customization context.
 * Allows full artistic control over entity appearance
 *
 * <h3>Design Philosophy:</h3>
 * <p>Entities with identical customization can be instanced together. Different customizations
 * create separate batches. This balances flexibility with performance.
 *
 * <h3>Usage Example:</h3>
 * <pre>
 * EntityRenderContext ctx = new EntityRenderContext();
 *
 * // Tint the "body" bone red for team identification
 * ctx.setBoneTint("body", 1.0f, 0.0f, 0.0f, 1.0f);
 *
 * // Shift UVs for "helmet" bone to show damage
 * ctx.setBoneUVOffset("helmet", 0.5f, 0.0f);
 *
 * // Make "eyes" bone glow
 * ctx.setBoneEmissive("eyes", 1.0f);
 *
 * // Hide "cape" bone
 * ctx.setBoneVisible("cape", false);
 *
 * // Swap texture for "armor" slot (iron -> diamond upgrade)
 * ctx.setTextureOverride("armor", Identifier.of("strata:armor_diamond"));
 * </pre>
 *
 * <h3>Instancing Compatibility:</h3>
 * <p>Entities with identical contexts can be instanced together. The system automatically
 * batches entities by their customization profile.
 */
public class EntityRenderContext {

    // ══════════════════════════════════════════════════════════════════════════
    // ANIMATION PROCESSOR
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * The animation processor that manages bone states for this entity.
     * Can be null if entity doesn't use animations.
     */
    private AnimationProcessor animationProcessor;

    // ══════════════════════════════════════════════════════════════════════════
    // PER-BONE CUSTOMIZATION
    // ══════════════════════════════════════════════════════════════════════════

    private final Map<String, Vector4f> boneTints = new HashMap<>();
    private final Map<String, Vector2f> boneUVOffsets = new HashMap<>();
    private final Map<String, Float> boneEmissiveness = new HashMap<>();
    private final Map<String, Boolean> boneVisibility = new HashMap<>();

    // ══════════════════════════════════════════════════════════════════════════
    // GLOBAL CUSTOMIZATION
    // ══════════════════════════════════════════════════════════════════════════

    private final Map<String, Identifier> textureOverrides = new HashMap<>();
    private Vector4f globalTint = new Vector4f(1, 1, 1, 1);
    private float globalEmissive = 0.0f;
    private Vector2f globalUVOffset = new Vector2f(0, 0);

    // ══════════════════════════════════════════════════════════════════════════
    // ANIMATION PROCESSOR
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * Sets the animation processor for this context.
     */
    public void setAnimationProcessor(AnimationProcessor processor) {
        this.animationProcessor = processor;
    }

    /**
     * Gets the animation processor for this context.
     * May be null if entity doesn't use animations.
     */
    public AnimationProcessor getAnimationProcessor() {
        return animationProcessor;
    }

    // ══════════════════════════════════════════════════════════════════════════
    // BONE TINTING
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * Sets a color tint for a specific bone.
     * Multiplied with texture color in shader.
     *
     * @param boneName Name of the bone (e.g., "head", "body", "leftArm")
     * @param r Red component (0.0 - 1.0)
     * @param g Green component (0.0 - 1.0)
     * @param b Blue component (0.0 - 1.0)
     * @param a Alpha component (0.0 - 1.0)
     */
    public void setBoneTint(String boneName, float r, float g, float b, float a) {
        boneTints.put(boneName, new Vector4f(r, g, b, a));
    }

    public void setBoneTint(String boneName, Vector4f tint) {
        boneTints.put(boneName, new Vector4f(tint));
    }

    public Vector4f getBoneTint(String boneName) {
        return boneTints.getOrDefault(boneName, new Vector4f(1, 1, 1, 1));
    }

    public void clearBoneTint(String boneName) {
        boneTints.remove(boneName);
    }

    // ══════════════════════════════════════════════════════════════════════════
    // UV OFFSET (for texture variation without changing the texture)
    // ══════════════════════════════════════════════════════════════════════════

    public void setBoneUVOffset(String boneName, float u, float v) {
        boneUVOffsets.put(boneName, new Vector2f(u, v));
    }

    public Vector2f getBoneUVOffset(String boneName) {
        return boneUVOffsets.getOrDefault(boneName, new Vector2f(0, 0));
    }

    public void clearBoneUVOffset(String boneName) {
        boneUVOffsets.remove(boneName);
    }

    // ══════════════════════════════════════════════════════════════════════════
    // EMISSIVENESS (glow effect)
    // ══════════════════════════════════════════════════════════════════════════

    public void setBoneEmissive(String boneName, float intensity) {
        boneEmissiveness.put(boneName, Math.max(0.0f, Math.min(1.0f, intensity)));
    }

    public float getBoneEmissive(String boneName) {
        return boneEmissiveness.getOrDefault(boneName, 0.0f);
    }

    public void clearBoneEmissive(String boneName) {
        boneEmissiveness.remove(boneName);
    }

    // ══════════════════════════════════════════════════════════════════════════
    // BONE VISIBILITY
    // ══════════════════════════════════════════════════════════════════════════

    public void setBoneVisible(String boneName, boolean visible) {
        boneVisibility.put(boneName, visible);
    }

    public boolean isBoneVisible(String boneName) {
        return boneVisibility.getOrDefault(boneName, true);
    }

    // ══════════════════════════════════════════════════════════════════════════
    // TEXTURE OVERRIDES (armor upgrades, skins, etc.)
    // ══════════════════════════════════════════════════════════════════════════

    public void setTextureOverride(String textureSlot, Identifier textureId) {
        textureOverrides.put(textureSlot, textureId);
    }

    public Identifier getTextureOverride(String textureSlot) {
        return textureOverrides.get(textureSlot);
    }

    public void clearTextureOverride(String textureSlot) {
        textureOverrides.remove(textureSlot);
    }

    public boolean hasTextureOverride(String textureSlot) {
        return textureOverrides.containsKey(textureSlot);
    }

    // ══════════════════════════════════════════════════════════════════════════
    // GLOBAL EFFECTS
    // ══════════════════════════════════════════════════════════════════════════

    public void setGlobalTint(float r, float g, float b, float a) {
        globalTint.set(r, g, b, a);
    }

    public Vector4f getGlobalTint() {
        return new Vector4f(globalTint);
    }

    public void setGlobalEmissive(float intensity) {
        globalEmissive = Math.max(0.0f, Math.min(1.0f, intensity));
    }

    public float getGlobalEmissive() {
        return globalEmissive;
    }

    public void setGlobalUVOffset(float u, float v) {
        globalUVOffset.set(u, v);
    }

    public Vector2f getGlobalUVOffset() {
        return new Vector2f(globalUVOffset);
    }

    // ══════════════════════════════════════════════════════════════════════════
    // BATCHING SUPPORT
    // ══════════════════════════════════════════════════════════════════════════

    @Override
    public int hashCode() {
        return Objects.hash(
                boneTints,
                boneUVOffsets,
                boneEmissiveness,
                boneVisibility,
                textureOverrides,
                globalTint,
                globalEmissive,
                globalUVOffset
        );
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

    // ══════════════════════════════════════════════════════════════════════════
    // CONVENIENCE GETTERS
    // ══════════════════════════════════════════════════════════════════════════

    public Map<String, Vector4f> getAllBoneTints() {
        return new HashMap<>(boneTints);
    }

    public Map<String, Vector2f> getAllBoneUVOffsets() {
        return new HashMap<>(boneUVOffsets);
    }

    public Map<String, Float> getAllBoneEmissiveness() {
        return new HashMap<>(boneEmissiveness);
    }

    public Map<String, Boolean> getAllBoneVisibility() {
        return new HashMap<>(boneVisibility);
    }

    public Map<String, Identifier> getAllTextureOverrides() {
        return new HashMap<>(textureOverrides);
    }
}