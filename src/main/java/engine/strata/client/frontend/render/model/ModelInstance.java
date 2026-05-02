package engine.strata.client.frontend.render.model;

import engine.strata.client.frontend.render.animation.AnimationProcessor;
import engine.strata.util.Identifier;
import engine.strata.util.Transform;

/**
 * Represents a single model instance that can be attached to an entity.
 * Entities can have multiple ModelInstances to support complex rendering
 * (e.g., body + armor + weapons as separate models).
 * 
 * <h3>Usage Example:</h3>
 * <pre>{@code
 * // Player with body and armor
 * new ModelInstance(
 *     Identifier.of("strata:player"),
 *     skinManager.getSkin("player/steve"),
 *     Transform.IDENTITY,
 *     animationProcessor
 * )
 * 
 * new ModelInstance(
 *     Identifier.of("strata:armor/iron_chestplate"),
 *     skinManager.getSkin("armor/iron"),
 *     Transform.offset(0, 0.05, 0), // Slight offset to avoid z-fighting
 *     null // Armor uses parent model's animations
 * )
 * }</pre>
 * 
 * <h3>Bone Attachment:</h3>
 * <p>Models can be attached to specific bones of other models:
 * <pre>{@code
 * // Sword attached to right hand bone
 * new ModelInstance(
 *     Identifier.of("strata:item/sword"),
 *     skinManager.getSkin("items/iron_sword"),
 *     Transform.IDENTITY,
 *     null,
 *     "right_hand" // Attachment bone name
 * )
 * }</pre>
 */
public class ModelInstance {
    
    private final Identifier modelId;
    private final StrataSkin skin;
    private final Transform localTransform;
    private final AnimationProcessor animationProcessor;
    private final String attachmentBone;
    private final int renderPriority;
    
    /**
     * Creates a basic model instance.
     * 
     * @param modelId The model to render
     * @param skin The skin (textures) to apply
     */
    public ModelInstance(Identifier modelId, StrataSkin skin) {
        this(modelId, skin, Transform.IDENTITY, null, null, 0);
    }
    
    /**
     * Creates a model instance with transform and animation.
     * 
     * @param modelId The model to render
     * @param skin The skin (textures) to apply
     * @param localTransform Transform offset from entity origin
     * @param animationProcessor Animation controller (null to use parent's)
     */
    public ModelInstance(Identifier modelId, StrataSkin skin,
                        Transform localTransform, AnimationProcessor animationProcessor) {
        this(modelId, skin, localTransform, animationProcessor, null, 0);
    }
    
    /**
     * Creates a model instance with bone attachment.
     * 
     * @param modelId The model to render
     * @param skin The skin (textures) to apply
     * @param localTransform Transform offset from attachment point
     * @param animationProcessor Animation controller (null to use parent's)
     * @param attachmentBone Bone name to attach to (null for entity origin)
     */
    public ModelInstance(Identifier modelId, StrataSkin skin,
                        Transform localTransform, AnimationProcessor animationProcessor,
                        String attachmentBone) {
        this(modelId, skin, localTransform, animationProcessor, attachmentBone, 0);
    }
    
    /**
     * Full constructor with all options.
     * 
     * @param modelId The model to render
     * @param skin The skin (textures) to apply
     * @param localTransform Transform offset from attachment point
     * @param animationProcessor Animation controller (null to use parent's)
     * @param attachmentBone Bone name to attach to (null for entity origin)
     * @param renderPriority Higher values render later (useful for layering)
     */
    public ModelInstance(Identifier modelId, StrataSkin skin,
                        Transform localTransform, AnimationProcessor animationProcessor,
                        String attachmentBone, int renderPriority) {
        this.modelId = modelId;
        this.skin = skin;
        this.localTransform = localTransform != null ? localTransform : Transform.IDENTITY;
        this.animationProcessor = animationProcessor;
        this.attachmentBone = attachmentBone;
        this.renderPriority = renderPriority;
    }
    
    // ══════════════════════════════════════════════════════════════════════════
    // GETTERS
    // ══════════════════════════════════════════════════════════════════════════
    
    public Identifier getModelId() {
        return modelId;
    }
    
    public StrataSkin getSkin() {
        return skin;
    }
    
    public Transform getLocalTransform() {
        return localTransform;
    }
    
    public AnimationProcessor getAnimationProcessor() {
        return animationProcessor;
    }
    
    public String getAttachmentBone() {
        return attachmentBone;
    }
    
    public int getRenderPriority() {
        return renderPriority;
    }
    
    /**
     * @return true if this model should be attached to a bone
     */
    public boolean hasAttachmentBone() {
        return attachmentBone != null && !attachmentBone.isEmpty();
    }
    
    /**
     * @return true if this model has its own animation processor
     */
    public boolean hasOwnAnimator() {
        return animationProcessor != null;
    }
    
    // ══════════════════════════════════════════════════════════════════════════
    // BUILDER PATTERN (Optional convenience)
    // ══════════════════════════════════════════════════════════════════════════
    
    public static Builder builder(Identifier modelId, StrataSkin skin) {
        return new Builder(modelId, skin);
    }
    
    public static class Builder {
        private final Identifier modelId;
        private final StrataSkin skin;
        private Transform localTransform = Transform.IDENTITY;
        private AnimationProcessor animationProcessor;
        private String attachmentBone;
        private int renderPriority = 0;
        
        private Builder(Identifier modelId, StrataSkin skin) {
            this.modelId = modelId;
            this.skin = skin;
        }
        
        public Builder transform(Transform transform) {
            this.localTransform = transform;
            return this;
        }
        
        public Builder animator(AnimationProcessor processor) {
            this.animationProcessor = processor;
            return this;
        }
        
        public Builder attachTo(String boneName) {
            this.attachmentBone = boneName;
            return this;
        }
        
        public Builder priority(int priority) {
            this.renderPriority = priority;
            return this;
        }
        
        public ModelInstance build() {
            return new ModelInstance(modelId, skin, localTransform, 
                                   animationProcessor, attachmentBone, renderPriority);
        }
    }
}
