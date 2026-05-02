package engine.strata.client.frontend.render.renderer.context;

import engine.strata.client.frontend.render.animation.AnimationProcessor;
import engine.strata.client.frontend.render.model.ModelInstance;
import engine.strata.util.Identifier;
import org.joml.Vector2f;
import org.joml.Vector4f;

import java.util.*;

/**
 * Enhanced rendering context that holds all customization options for rendering.
 * 
 * <h3>Features:</h3>
 * <ul>
 *   <li>Multi-model support via {@link ModelInstance}</li>
 *   <li>Global and per-bone tinting</li>
 *   <li>Per-bone visibility control</li>
 *   <li>Texture overrides</li>
 *   <li>UV offsets and emissive properties</li>
 * </ul>
 * 
 * <h3>Usage Example:</h3>
 * <pre>{@code
 * RenderContext context = RenderContext.builder()
 *     .addModel(bodyModel)
 *     .addModel(armorModel)
 *     .globalTint(1.0f, 0.8f, 0.8f, 1.0f) // Slight red tint
 *     .setBoneVisible("left_arm", false)  // Hide left arm
 *     .build();
 * }</pre>
 */
public class RenderContext {
    
    private final List<ModelInstance> modelInstances;
    private final Vector4f globalTint;
    private final Map<String, Vector4f> boneTints;
    private final Map<String, Boolean> boneVisibility;
    private final Map<String, Identifier> textureOverrides;
    private final Map<String, Float> boneEmissive;
    private final Map<String, Vector2f> boneUVOffsets;
    
    private RenderContext(Builder builder) {
        this.modelInstances = Collections.unmodifiableList(new ArrayList<>(builder.modelInstances));
        this.globalTint = new Vector4f(builder.globalTint);
        this.boneTints = new HashMap<>(builder.boneTints);
        this.boneVisibility = new HashMap<>(builder.boneVisibility);
        this.textureOverrides = new HashMap<>(builder.textureOverrides);
        this.boneEmissive = new HashMap<>(builder.boneEmissive);
        this.boneUVOffsets = new HashMap<>(builder.boneUVOffsets);
    }
    
    /**
     * Creates an empty context with default values.
     */
    public RenderContext() {
        this.modelInstances = Collections.emptyList();
        this.globalTint = new Vector4f(1, 1, 1, 1);
        this.boneTints = new HashMap<>();
        this.boneVisibility = new HashMap<>();
        this.textureOverrides = new HashMap<>();
        this.boneEmissive = new HashMap<>();
        this.boneUVOffsets = new HashMap<>();
    }
    
    // ══════════════════════════════════════════════════════════════════════════
    // MODEL INSTANCES
    // ══════════════════════════════════════════════════════════════════════════
    
    /**
     * @return Unmodifiable list of model instances to render
     */
    public List<ModelInstance> getModelInstances() {
        return modelInstances;
    }
    
    /**
     * @return true if this context has any models to render
     */
    public boolean hasModels() {
        return !modelInstances.isEmpty();
    }
    
    // ══════════════════════════════════════════════════════════════════════════
    // TINTING
    // ══════════════════════════════════════════════════════════════════════════
    
    /**
     * @return Global tint color (default white)
     */
    public Vector4f getGlobalTint() {
        return new Vector4f(globalTint);
    }
    
    /**
     * Gets tint for a specific bone.
     * @param boneName Bone identifier
     * @return Bone-specific tint, or white if not set
     */
    public Vector4f getBoneTint(String boneName) {
        return boneTints.getOrDefault(boneName, new Vector4f(1, 1, 1, 1));
    }
    
    /**
     * @param boneName Bone identifier
     * @return true if this bone has a custom tint
     */
    public boolean hasBoneTint(String boneName) {
        return boneTints.containsKey(boneName);
    }
    
    // ══════════════════════════════════════════════════════════════════════════
    // VISIBILITY
    // ══════════════════════════════════════════════════════════════════════════
    
    /**
     * Checks if a bone should be rendered.
     * @param boneName Bone identifier
     * @return true if bone is visible (default true)
     */
    public boolean isBoneVisible(String boneName) {
        return boneVisibility.getOrDefault(boneName, true);
    }
    
    // ══════════════════════════════════════════════════════════════════════════
    // TEXTURE OVERRIDES
    // ══════════════════════════════════════════════════════════════════════════
    
    /**
     * Gets texture override for a texture slot.
     * @param textureSlot Texture slot name
     * @return Override texture path, or null if not set
     */
    public Identifier getTextureOverride(String textureSlot) {
        return textureOverrides.get(textureSlot);
    }
    
    /**
     * @param textureSlot Texture slot name
     * @return true if this slot has a texture override
     */
    public boolean hasTextureOverride(String textureSlot) {
        return textureOverrides.containsKey(textureSlot);
    }
    
    // ══════════════════════════════════════════════════════════════════════════
    // EMISSIVE & UV OFFSETS
    // ══════════════════════════════════════════════════════════════════════════
    
    /**
     * Gets emissive value for a bone.
     * @param boneName Bone identifier
     * @return Emissive factor (0-1), default 0
     */
    public float getBoneEmissive(String boneName) {
        return boneEmissive.getOrDefault(boneName, 0.0f);
    }
    
    /**
     * Gets UV offset for a bone (for texture scrolling effects).
     * @param boneName Bone identifier
     * @return UV offset, default (0, 0)
     */
    public Vector2f getBoneUVOffset(String boneName) {
        return boneUVOffsets.getOrDefault(boneName, new Vector2f(0, 0));
    }
    
    // ══════════════════════════════════════════════════════════════════════════
    // ANIMATION PROCESSOR (LEGACY SUPPORT)
    // ══════════════════════════════════════════════════════════════════════════
    
    /**
     * Gets the animation processor from the first model instance.
     * @return AnimationProcessor or null
     * @deprecated Use {@link ModelInstance#getAnimationProcessor()} directly
     */
    @Deprecated
    public AnimationProcessor getAnimationProcessor() {
        if (modelInstances.isEmpty()) return null;
        return modelInstances.get(0).getAnimationProcessor();
    }
    
    // ══════════════════════════════════════════════════════════════════════════
    // BUILDER
    // ══════════════════════════════════════════════════════════════════════════
    
    public static Builder builder() {
        return new Builder();
    }
    
    public static class Builder {
        private final List<ModelInstance> modelInstances = new ArrayList<>();
        private final Vector4f globalTint = new Vector4f(1, 1, 1, 1);
        private final Map<String, Vector4f> boneTints = new HashMap<>();
        private final Map<String, Boolean> boneVisibility = new HashMap<>();
        private final Map<String, Identifier> textureOverrides = new HashMap<>();
        private final Map<String, Float> boneEmissive = new HashMap<>();
        private final Map<String, Vector2f> boneUVOffsets = new HashMap<>();
        
        /**
         * Add a model instance to render.
         */
        public Builder addModel(ModelInstance instance) {
            this.modelInstances.add(instance);
            return this;
        }
        
        /**
         * Add multiple model instances.
         */
        public Builder addModels(Collection<ModelInstance> instances) {
            this.modelInstances.addAll(instances);
            return this;
        }
        
        /**
         * Set global tint color.
         */
        public Builder globalTint(float r, float g, float b, float a) {
            this.globalTint.set(r, g, b, a);
            return this;
        }
        
        /**
         * Set global tint color.
         */
        public Builder globalTint(Vector4f tint) {
            this.globalTint.set(tint);
            return this;
        }
        
        /**
         * Set tint for a specific bone.
         */
        public Builder boneTint(String boneName, float r, float g, float b, float a) {
            this.boneTints.put(boneName, new Vector4f(r, g, b, a));
            return this;
        }
        
        /**
         * Set bone visibility.
         */
        public Builder setBoneVisible(String boneName, boolean visible) {
            this.boneVisibility.put(boneName, visible);
            return this;
        }
        
        /**
         * Override texture for a specific slot.
         */
        public Builder textureOverride(String textureSlot, Identifier texturePath) {
            this.textureOverrides.put(textureSlot, texturePath);
            return this;
        }
        
        /**
         * Set emissive value for a bone.
         */
        public Builder boneEmissive(String boneName, float emissive) {
            this.boneEmissive.put(boneName, Math.max(0, Math.min(1, emissive)));
            return this;
        }
        
        /**
         * Set UV offset for a bone.
         */
        public Builder boneUVOffset(String boneName, float u, float v) {
            this.boneUVOffsets.put(boneName, new Vector2f(u, v));
            return this;
        }
        
        public RenderContext build() {
            // Sort models by render priority
            modelInstances.sort(Comparator.comparingInt(ModelInstance::getRenderPriority));
            return new RenderContext(this);
        }
    }
}
