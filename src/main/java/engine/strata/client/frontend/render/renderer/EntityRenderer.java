package engine.strata.client.frontend.render.renderer;

import engine.helios.rendering.vertex.MatrixStack;
import engine.strata.client.StrataClient;
import engine.strata.client.frontend.render.animation.AnimationProcessor;
import engine.strata.client.frontend.render.model.StrataModel;
import engine.strata.client.frontend.render.model.StrataSkin;
import engine.strata.client.frontend.render.model.io.ModelManager;
import engine.strata.client.frontend.render.renderer.entity.EntityRenderContext;
import engine.strata.entity.Entity;
import engine.strata.util.Identifier;
import engine.strata.util.Transform;
import engine.strata.util.Vec3d;
import engine.strata.util.Vec3f;
import org.joml.Quaternionf;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

import static engine.strata.util.math.Math.fLerp;

/**
 * Universal entity renderer that handles all entity types.
 *
 * <h3>Design:</h3>
 * All configuration is read directly from the entity:
 * <ul>
 *   <li>{@link Entity#getModelId()} → which model to render</li>
 *   <li>{@link Entity#getRenderContext()} → appearance customisation</li>
 *   <li>{@link Entity#prepareAnimations(float)} → per-frame bone animation</li>
 * </ul>
 *
 * <h3>Animation lifecycle per frame:</h3>
 * <ol>
 *   <li>Load / retrieve model from cache.</li>
 *   <li>If this is the first render for this entity, bind its {@link AnimationProcessor}
 *       to the loaded model so bone states are pre-seeded with the correct default visibility.</li>
 *   <li>Call {@link Entity#prepareAnimations(float)} — resets all bones then calls
 *       the entity's {@code updateAnimations()} override.</li>
 *   <li>Retrieve the render context (live processor always attached) and pass everything
 *       to {@link ModelRenderer}.</li>
 * </ol>
 */
public class EntityRenderer {
    private static final Logger LOGGER = LoggerFactory.getLogger("EntityRenderer");

    private final Map<Identifier, ModelCache> modelCache = new HashMap<>();

    /**
     * Renders an entity using its configured model and render context.
     *
     * @param entity       The entity to render
     * @param partialTicks Interpolation factor (0.0 – 1.0)
     * @param poseStack    Transform stack for positioning
     */
    public void render(Entity entity, float partialTicks, MatrixStack poseStack) {
        Identifier modelId = entity.getModelId();
        if (modelId == null) {
            LOGGER.warn("Entity {} has no model ID", entity.getClass().getSimpleName());
            return;
        }

        ModelCache cache = getOrLoadModel(modelId);
        if (cache == null || cache.model == null || cache.skin == null) {
            LOGGER.error("Failed to retrieve model/skin for {}", modelId);
            return;
        }

        // Bind the AnimationProcessor to this model on first encounter, or if the
        // model was reloaded. This pre-seeds bone states with correct default visibility.
        AnimationProcessor anim = entity.getAnimationProcessor();
        if (anim.getModel() != cache.model) {
            anim.setModel(cache.model);
        }

        // Reset bones to model-file defaults, then let the entity apply animations.
        entity.prepareAnimations(partialTicks);

        // getRenderContext() always attaches the live AnimationProcessor.
        EntityRenderContext renderContext = entity.getRenderContext();

        applyTransforms(entity, partialTicks, poseStack);

        poseStack.push();
        poseStack.scale(1.0f / 16.0f, 1.0f / 16.0f, 1.0f / 16.0f);
        poseStack.scale(entity.getScale().getX(), entity.getScale().getY(), entity.getScale().getZ());

        StrataClient.getInstance().getMasterRenderer()
                .getModelRenderer().render(cache.model, cache.skin, poseStack, renderContext);

        poseStack.pop();
    }

    private void applyTransforms(Entity entity, float partialTicks, MatrixStack poseStack) {
        float x = fLerp((float) entity.prevX, (float) entity.getPosition().getX(), partialTicks);
        float y = fLerp((float) entity.prevY, (float) entity.getPosition().getY(), partialTicks);
        float z = fLerp((float) entity.prevZ, (float) entity.getPosition().getZ(), partialTicks);
        poseStack.translate(x, y, z);

        Quaternionf interpolatedQuat = new Quaternionf(entity.prevRotationQuat)
                .slerp(entity.getRotation(), partialTicks);
        poseStack.rotate(interpolatedQuat);

        Transform modelTransform = entity.getModelTransform();

        Vec3d modelPos = modelTransform.getPosition();
        if (modelPos.getX() != 0 || modelPos.getY() != 0 || modelPos.getZ() != 0) {
            poseStack.translate((float) modelPos.getX(), (float) modelPos.getY(), (float) modelPos.getZ());
        }

        Quaternionf modelRot = modelTransform.getRotation();
        if (!modelRot.equals(0, 0, 0, 1)) {
            poseStack.rotate(modelRot);
        }

        Vec3f modelScale = modelTransform.getScale();
        if (modelScale.getX() != 1 || modelScale.getY() != 1 || modelScale.getZ() != 1) {
            poseStack.scale(modelScale.getX(), modelScale.getY(), modelScale.getZ());
        }

        poseStack.scale(entity.getScale().getX(), entity.getScale().getY(), entity.getScale().getZ());
    }

    private ModelCache getOrLoadModel(Identifier modelId) {
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

    /**
     * Clears the model cache, forcing a reload on next render.
     * The AnimationProcessor will be rebound to the fresh model automatically.
     */
    public void clearCache() {
        modelCache.clear();
        LOGGER.info("Entity renderer model cache cleared");
    }

    private static class ModelCache {
        final StrataModel model;
        final StrataSkin skin;

        ModelCache(StrataModel model, StrataSkin skin) {
            this.model = model;
            this.skin = skin;
        }
    }
}