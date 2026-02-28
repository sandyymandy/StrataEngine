package engine.strata.client.frontend.render.renderer.entity;

import engine.helios.rendering.vertex.MatrixStack;
import engine.strata.client.StrataClient;
import engine.strata.client.frontend.render.model.StrataModel;
import engine.strata.client.frontend.render.model.StrataSkin;
import engine.strata.client.frontend.render.model.io.ModelManager;
import engine.strata.entity.Entity;
import engine.strata.util.Identifier;
import engine.strata.util.Vec3f;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

import static engine.strata.util.math.Math.fLerp;

/**
 * Universal entity renderer that handles all entity types.
 *
 * <h3>Design Philosophy:</h3>
 * Unlike the old system where each entity type needed its own renderer subclass,
 * this renderer reads all configuration directly from the entity:
 * <ul>
 *   <li>{@link Entity#getModelId()} → which model to render</li>
 *   <li>{@link Entity#getRenderContext()} → how to customize appearance</li>
 * </ul>
 *
 * <h3>Usage:</h3>
 * <pre>
 * // In MasterRenderer:
 * EntityRenderer entityRenderer = new EntityRenderer();
 * entityRenderer.render(entity, partialTicks, poseStack);
 *
 * // No per-entity registration needed!
 * </pre>
 *
 * <h3>How it works:</h3>
 * 1. Reads model ID from entity
 * 2. Loads/caches model and skin
 * 3. Reads render context from entity
 * 4. Applies transformations
 * 5. Passes model, skin, and context to ModelRenderer
 */
public class EntityRenderer {
    private static final Logger LOGGER = LoggerFactory.getLogger("EntityRenderer");

    // Cache loaded models per model ID to avoid repeated disk access
    private final Map<Identifier, ModelCache> modelCache = new HashMap<>();

    /**
     * Renders an entity using its configured model and render context.
     *
     * @param entity The entity to render
     * @param partialTicks Interpolation factor (0.0 to 1.0)
     * @param poseStack Transform stack for positioning
     */
    public void render(Entity entity, float partialTicks, MatrixStack poseStack) {
        // Get the model to use (can be dynamic per entity)
        Identifier modelId = entity.getModelId();
        if (modelId == null) {
            LOGGER.warn("Entity {} has no model ID", entity.getClass().getSimpleName());
            return;
        }

        // Load or retrieve cached model
        ModelCache cache = getOrLoadModel(modelId);
        if (cache == null || cache.model == null || cache.skin == null) {
            return; // Failed to load
        }

        // Get entity's render customization
        EntityRenderContext renderContext = entity.getRenderContext();

        // Apply entity transforms
        applyTransforms(entity, partialTicks, poseStack);

        poseStack.push();

        // Scale from model space (16x16x16) to world space (1x1x1)
        poseStack.scale(1.0f / 16.0f, 1.0f / 16.0f, 1.0f / 16.0f);
        poseStack.scale(entity.getScale().getX(), entity.getScale().getY(), entity.getScale().getZ());

        // Render with context-aware model renderer
        StrataClient.getInstance().getMasterRenderer()
                .getModelRenderer().render(cache.model, cache.skin, poseStack, renderContext);

        poseStack.pop();
    }

    /**
     * Applies position and rotation transforms with interpolation for smooth movement.
     * Uses quaternion slerp for rotation and applies model offset.
     */
    private void applyTransforms(Entity entity, float partialTicks, MatrixStack poseStack) {
        // Interpolate position
        float x = fLerp((float) entity.prevX, (float) entity.getPosition().getX(), partialTicks);
        float y = fLerp((float) entity.prevY, (float) entity.getPosition().getY(), partialTicks);
        float z = fLerp((float) entity.prevZ, (float) entity.getPosition().getZ(), partialTicks);

        // Apply base position
        poseStack.translate(x, y, z);

        // Interpolate rotation using quaternion slerp (spherical linear interpolation)
        // This is much smoother than Euler angle interpolation!
        org.joml.Quaternionf currentQuat = new org.joml.Quaternionf(entity.getRotation());
        org.joml.Quaternionf prevQuat = new org.joml.Quaternionf(entity.prevRotationQuat);
        org.joml.Quaternionf interpolatedQuat = prevQuat.slerp(currentQuat, partialTicks);

        // Apply rotation from quaternion
        poseStack.rotate(interpolatedQuat);

        // Apply model offset (in blocks, relative to entity)
        Vec3f offset = entity.getModelOffset();
        if (offset.getX() != 0 || offset.getY() != 0 || offset.getZ() != 0) {
            poseStack.translate(offset.getX(), offset.getY(), offset.getZ());
        }

        // Apply scale
        poseStack.scale(entity.getScale().getX(), entity.getScale().getY(), entity.getScale().getZ());
    }

    /**
     * Gets model from cache or loads it if not present.
     */
    private ModelCache getOrLoadModel(Identifier modelId) {
        ModelCache cache = modelCache.get(modelId);
        if (cache != null) {
            return cache;
        }

        // Load model and skin
        try {
            StrataModel model = ModelManager.getModel(modelId);
            StrataSkin skin = ModelManager.getSkin(modelId);

            if (model == null || skin == null) {
                LOGGER.error("Failed to load model or skin: {}", modelId);
                // Cache null result to prevent repeated load attempts
                cache = new ModelCache(null, null);
            } else {
                cache = new ModelCache(model, skin);
            }

            modelCache.put(modelId, cache);
            return cache;
        } catch (Exception e) {
            LOGGER.error("Error loading model {}", modelId, e);
            // Cache null result
            ModelCache errorCache = new ModelCache(null, null);
            modelCache.put(modelId, errorCache);
            return errorCache;
        }
    }

    /**
     * Clears the model cache, forcing reload on next render.
     * Should be called when models are reloaded.
     */
    public void clearCache() {
        modelCache.clear();
        LOGGER.info("Entity renderer model cache cleared");
    }

    /**
     * Simple cache entry for model and skin.
     */
    private static class ModelCache {
        final StrataModel model;
        final StrataSkin skin;

        ModelCache(StrataModel model, StrataSkin skin) {
            this.model = model;
            this.skin = skin;
        }
    }
}