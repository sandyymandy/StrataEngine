package engine.strata.client.frontend.render.renderer.entity.entities;

import engine.helios.rendering.GpuModelCache;
import engine.helios.rendering.MeshRenderer;
import engine.helios.rendering.RenderLayer;
import engine.helios.rendering.shader.ShaderStack;
import engine.helios.rendering.vertex.MatrixStack;
import engine.strata.client.StrataClient;
import engine.strata.client.frontend.render.RenderLayers;
import engine.strata.client.frontend.render.animation.AnimationProcessor;
import engine.strata.client.frontend.render.model.*;
import engine.strata.client.frontend.render.model.io.ModelManager;
import engine.strata.client.frontend.render.renderer.context.RenderContext;
import engine.strata.entity.Entity;
import engine.strata.util.Identifier;
import engine.strata.util.Transform;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

import static engine.strata.util.math.Math.fLerp;

/**
 * Base class for entity-specific renderers.
 *
 * <h3>Design:</h3>
 * <p>This class provides common entity rendering functionality:
 * <ul>
 *   <li>Model loading and caching</li>
 *   <li>Transform interpolation</li>
 *   <li>Multi-model rendering support</li>
 *   <li>Bone hierarchy traversal</li>
 * </ul>
 *
 * <h3>Subclass Implementation:</h3>
 * <pre>{@code
 * public class PlayerEntityRenderer extends EntityRenderer<PlayerEntity> {
 *     @Override
 *     protected RenderContext createRenderContext(PlayerEntity entity, float partialTicks) {
 *         return RenderContext.builder()
 *             .addModel(createBodyModel(entity))
 *             .addModel(createArmorModel(entity))
 *             .build();
 *     }
 * }
 * }</pre>
 *
 * @param <T> The specific entity type this renderer handles
 */
public abstract class EntityRenderer<T extends Entity> {

    public static final float MODEL_SPACE_UNIT = 16.0f;
    private static final Matrix4f IDENTITY = new Matrix4f().identity();
    public static final Logger LOGGER = LoggerFactory.getLogger("EntityRenderer");
    private final Map<Identifier, ModelCache> modelCache = new HashMap<>();
    private boolean inBatch = false;

    // ══════════════════════════════════════════════════════════════════════════
    // ABSTRACT METHODS - Subclasses must implement
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * Create the render context for this entity.
     * This is where you define which models to render and how to customize them.
     *
     * @param entity The entity being rendered
     * @param partialTicks Frame interpolation factor
     * @return RenderContext with all models and customization
     */
    public abstract RenderContext createRenderContext(T entity, float partialTicks);

    // ══════════════════════════════════════════════════════════════════════════
    // LIFECYCLE IMPLEMENTATION
    // ══════════════════════════════════════════════════════════════════════════

    protected void preRender(T entity, RenderContext context, float partialTicks, MatrixStack poseStack) {
        // Subclasses can override for custom pre-render logic
    }

    public void render(T entity, RenderContext context, float partialTicks, MatrixStack poseStack) {
        // Apply entity world transforms
        applyEntityTransforms(entity, partialTicks, poseStack);

        poseStack.push();

        // Convert from model units to world units
        poseStack.scale(1.0f / MODEL_SPACE_UNIT, 1.0f / MODEL_SPACE_UNIT, 1.0f / MODEL_SPACE_UNIT);

        // Apply entity scale
        poseStack.scale(entity.getScale().getX(), entity.getScale().getY(), entity.getScale().getZ());

        // Render all model instances
        for (ModelInstance modelInstance : context.getModelInstances()) {
            renderModelInstance(entity, modelInstance, poseStack, context, partialTicks);
        }

        poseStack.pop();
    }

    protected void postRender(T entity, RenderContext context, float partialTicks, MatrixStack poseStack) {
        // Subclasses can override for custom post-render logic
    }

    // ══════════════════════════════════════════════════════════════════════════
    // MODEL RENDERING
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * Renders a single model instance.
     */
    protected void renderModelInstance(T entity, ModelInstance modelInstance,
                                       MatrixStack poseStack, RenderContext context, float partialTicks) {
        ModelCache cache = getOrLoadModel(modelInstance.getModelId());
        if (cache == null || cache.model == null || cache.skin == null) {
            LOGGER.warn("Failed to load model: {}", modelInstance.getModelId());
            return;
        }

        // Bind animation processor to model if present
        AnimationProcessor anim = modelInstance.getAnimationProcessor();
        if (anim != null && anim.getModel() != cache.model) {
            anim.setModel(cache.model);
        }

        // Prepare animations
        if (anim != null) {
            entity.prepareAnimations(partialTicks);
        }

        poseStack.push();

        // Apply local transform from ModelInstance
        applyTransform(poseStack, modelInstance.getLocalTransform());

        // Render the model
        renderModel(cache.model, modelInstance.getSkin(), poseStack, context, anim);

        poseStack.pop();
    }

    /**
     * Renders a single object through the complete pipeline.
     * This method is final to enforce the lifecycle order.
     *
     * @param renderable The object to render
     * @param context Rendering context
     * @param partialTicks Frame interpolation
     * @param poseStack Transform stack
     */
    public final void renderObject(T renderable, RenderContext context,
                                   float partialTicks, MatrixStack poseStack) {
        try {
            preRender(renderable, context, partialTicks, poseStack);
            render(renderable, context, partialTicks, poseStack);
            postRender(renderable, context, partialTicks, poseStack);
        } catch (Exception e) {
            LOGGER.error("Error rendering object of type {}",
                    renderable.getClass().getSimpleName(), e);
        }
    }

    /**
     * Renders a complete model with all its bones and meshes.
     */
    protected void renderModel(StrataModel model, StrataSkin skin, MatrixStack poseStack,
                               RenderContext context, AnimationProcessor animator) {
        // Ensure all meshes are on the GPU
        GpuModelBaker.getInstance().ensureBaked(model);

        // Render each texture layer
        for (Map.Entry<String, StrataSkin.TextureData> entry : skin.textures().entrySet()) {
            String textureSlot = entry.getKey();
            StrataSkin.TextureData texData = entry.getValue();

            // Apply texture override if present
            Identifier texturePath = context.hasTextureOverride(textureSlot) ?
                    context.getTextureOverride(textureSlot) : texData.path();

            RenderLayer layer = RenderLayers.getEntityLayer(texturePath, texData.translucent());
            layer.setup(StrataClient.getInstance().getCamera());
            ShaderStack shader = layer.shaderStack();

            if (shader == null) {
                LOGGER.error("Null shader for texture: {}", texturePath);
                layer.clean();
                continue;
            }

            // Render bone hierarchy
            renderBone(model, textureSlot, model.getRoot(), poseStack, shader,
                    new Matrix4f().identity(), context, animator);

            // Reset uniforms
            shader.setUniform("u_Model", IDENTITY);
            shader.setUniform("u_Tint", 1f, 1f, 1f, 1f);

            layer.clean();
        }
    }

    /**
     * Recursively renders bone hierarchy.
     */
    protected void renderBone(StrataModel model, String textureSlotFilter, StrataBone bone,
                              MatrixStack poseStack, ShaderStack shader, Matrix4f parentModelMatrix,
                              RenderContext context, AnimationProcessor animator) {
        // Get bone state from animator
        BoneState boneState = (animator != null)
                ? animator.getBoneState(bone.getName())
                : new BoneState(!bone.isDefaultHidden());

        // Check visibility
        if (!boneState.isVisible() || !context.isBoneVisible(bone.getName())) {
            return; // Skip entire subtree
        }

        poseStack.push();

        // Move to pivot point
        Vector3f pivot = bone.getPivot();
        poseStack.translate(pivot.x, pivot.y, pivot.z);

        // Apply combined rotation (base + animation offset)
        Vector3f baseRotation = bone.getRotation();
        Vector3f animOffset = boneState.getRotationOffset();
        Vector3f combinedRotation = baseRotation.add(animOffset, new Vector3f());
        poseStack.rotateZYX(combinedRotation.z, combinedRotation.y, combinedRotation.x);

        // Apply scale
        Vector3f scale = boneState.getScaleOffset();
        if (!scale.equals(1, 1, 1)) {
            poseStack.scale(scale.x, scale.y, scale.z);
        }

        // Apply position offset
        Vector3f posOffset = boneState.getPositionOffset();
        if (!posOffset.equals(0, 0, 0)) {
            poseStack.translate(posOffset.x, posOffset.y, posOffset.z);
        }

        // Move back from pivot
        poseStack.translate(-pivot.x, -pivot.y, -pivot.z);

        Matrix4f currentModelMatrix = new Matrix4f(parentModelMatrix).mul(poseStack.peek());

        // Render meshes attached to this bone
        for (String meshId : bone.getMeshIds()) {
            StrataMeshData meshData = model.getMesh(meshId);
            if (meshData == null) continue;
            if (textureSlotFilter != null && !textureSlotFilter.equals(meshData.textureSlot())) continue;

            drawMesh(model, meshData, meshId, poseStack, shader, bone, context);
        }

        // Render children
        for (StrataBone child : bone.getChildren()) {
            renderBone(model, textureSlotFilter, child, poseStack, shader, currentModelMatrix, context, animator);
        }

        poseStack.pop();
    }

    /**
     * Draws a single mesh.
     */
    protected void drawMesh(StrataModel model, StrataMeshData meshData, String meshId,
                            MatrixStack poseStack, ShaderStack shader, StrataBone bone,
                            RenderContext context) {
        String cacheKey = GpuModelBaker.getInstance().meshKey(model.getId(), meshId);
        MeshRenderer meshRenderer = GpuModelCache.getInstance().get(cacheKey);
        if (meshRenderer == null || meshRenderer.isEmpty()) return;

        // Apply mesh local transform
        Matrix4f meshMatrix = new Matrix4f(poseStack.peek());
        applyMeshLocalTransform(meshMatrix, meshData);

        // Upload model matrix
        shader.setUniform("u_Model", meshMatrix);

        // Apply tint
        Vector4f tint = computeFinalTint(bone, context);
        shader.setUniform("u_Tint", tint.x, tint.y, tint.z, tint.w);

        meshRenderer.render();
    }

    /**
     * Computes final tint by combining global and bone-specific tints.
     */
    protected Vector4f computeFinalTint(StrataBone bone, RenderContext context) {
        Vector4f globalTint = context.getGlobalTint();
        Vector4f boneTint = context.getBoneTint(bone.getName());

        return new Vector4f(
                globalTint.x * boneTint.x,
                globalTint.y * boneTint.y,
                globalTint.z * boneTint.z,
                globalTint.w * boneTint.w
        );
    }

    /**
     * Applies mesh-local origin + rotation pivot.
     */
    protected void applyMeshLocalTransform(Matrix4f matrix, StrataMeshData meshData) {
        Vector3f origin = meshData.origin();
        Vector3f rot = meshData.rotation(); // Already in radians

        if ("blockbench_cuboid".equals(meshData.type())) {
            matrix.translate(origin.x, origin.y, origin.z);
            matrix.rotateZYX(rot.z, rot.y, rot.x);
            matrix.translate(-origin.x, -origin.y, -origin.z);
        } else {
            matrix.translate(origin.x, origin.y, origin.z);
            matrix.rotateZYX(rot.z, rot.y, rot.x);
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // TRANSFORM UTILITIES
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * Applies entity-level transforms (position, rotation, model transform).
     */
    protected void applyEntityTransforms(T entity, float partialTicks, MatrixStack poseStack) {
        // Interpolate position
        float x = fLerp((float) entity.prevX, (float) entity.getPosition().getX(), partialTicks);
        float y = fLerp((float) entity.prevY, (float) entity.getPosition().getY(), partialTicks);
        float z = fLerp((float) entity.prevZ, (float) entity.getPosition().getZ(), partialTicks);
        poseStack.translate(x, y, z);

        // Interpolate rotation
        Quaternionf interpolatedQuat = new Quaternionf(entity.prevRotationQuat)
                .slerp(entity.getRotation(), partialTicks);
        poseStack.rotate(interpolatedQuat);

        // Apply model transform
        applyTransform(poseStack, entity.getModelTransform());
    }

    /**
     * Applies a Transform to the pose stack.
     */
    protected void applyTransform(MatrixStack poseStack, Transform transform) {
        // Position
        poseStack.translate(
                (float) transform.getPosition().getX(),
                (float) transform.getPosition().getY(),
                (float) transform.getPosition().getZ()
        );

        // Rotation
        Quaternionf rot = transform.getRotation();
        if (!rot.equals(0, 0, 0, 1)) {
            poseStack.rotate(rot);
        }

        // Scale
        poseStack.scale(
                transform.getScale().getX(),
                transform.getScale().getY(),
                transform.getScale().getZ()
        );
    }

    // ══════════════════════════════════════════════════════════════════════════
    // MODEL CACHE MANAGEMENT
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * Loads or retrieves a cached model.
     */
    protected ModelCache getOrLoadModel(Identifier modelId) {
        ModelCache cache = modelCache.get(modelId);
        if (cache != null) return cache;

        try {
            StrataModel model = ModelManager.getModel(modelId);
            StrataSkin skin = ModelManager.getSkin(modelId);

            if (model == null || skin == null) {
                LOGGER.error("Failed to load model or skin: {}", modelId);
                cache = new ModelCache(null, null);
            } else {
                cache = new ModelCache(model, skin);
            }
        } catch (Exception e) {
            LOGGER.error("Error loading model {}", modelId, e);
            cache = new ModelCache(null, null);
        }

        modelCache.put(modelId, cache);
        return cache;
    }

    public void reload() {
        modelCache.clear();
        LOGGER.info("Cleared model cache for {}", getClass().getSimpleName());
    }

    public void cleanup() {
        modelCache.clear();
    }

    /**
     * Begin a batch rendering session. Setup shared state here (shaders, textures).
     * Called once before rendering multiple objects of the same type.
     */
    public void beginBatch() {
        inBatch = true;
    }

    /**
     * End a batch rendering session. Cleanup shared state here.
     * Called once after rendering all objects in the batch.
     */
    public void endBatch() {
        inBatch = false;
    }

    /**
     * @return true if currently in a batch rendering session
     */
    protected final boolean isInBatch() {
        return inBatch;
    }

    /**
     * Model + skin cache entry.
     */
    protected static class ModelCache {
        final StrataModel model;
        final StrataSkin skin;

        ModelCache(StrataModel model, StrataSkin skin) {
            this.model = model;
            this.skin = skin;
        }
    }
}