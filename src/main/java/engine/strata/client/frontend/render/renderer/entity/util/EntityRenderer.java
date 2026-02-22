package engine.strata.client.frontend.render.renderer.entity.util;

import engine.helios.rendering.vertex.MatrixStack;
import engine.strata.client.StrataClient;
import engine.strata.client.frontend.render.model.io.ModelManager;
import engine.strata.client.frontend.render.model.StrataModel;
import engine.strata.client.frontend.render.model.StrataSkin;
import engine.strata.entity.Entity;
import engine.strata.util.Identifier;

import static engine.strata.util.math.Math.fLerp;

/**
 * Base class for rendering entities using the StrataModel system.
 * Handles texture layers, batching, render order, and animations.
 */
public abstract class EntityRenderer<T extends Entity> {
    protected final EntityRenderDispatcher dispatcher;
    private Identifier modelId;
    private StrataModel model;
    private StrataSkin skin;
    private boolean modelLoaded = false;

    public EntityRenderer(EntityRendererFactory.Context ctx) {
        this.dispatcher = ctx.dispatcher;
    }

    /**
     * The main method called every frame to render the entity.
     *
     * @param entity       The entity to render
     * @param partialTicks The fraction of time between ticks (0.0 to 1.0) for smoothing
     * @param poseStack    The MatrixStack for position/rotation
     */
    public void render(T entity, float partialTicks, MatrixStack poseStack) {
        // Lazy load model and skin on first render
        if (!modelLoaded) {
            loadModel(entity);
            if (model == null || skin == null) {
                return; // Failed to load
            }
        }

        if (modelId != getModelId()) loadModel(entity);

        applyTransforms(entity, partialTicks, poseStack);

        poseStack.push();

        // Scale from model space (16x16x16) to world space (1x1x1)
        poseStack.scale(1.0f / 16.0f, 1.0f / 16.0f, 1.0f / 16.0f);

        poseStack.scale(entity.getScale().getX(), entity.getScale().getY(), entity.getScale().getZ());

        // Render all texture layers in priority order
        StrataClient.getInstance().getMasterRenderer()
                .getModelRenderer().render(model, skin, poseStack);

        poseStack.pop();
    }

    /**
     * Applies transformations to the entity with interpolation for smooth movement.
     */
    private void applyTransforms(T entity, float partialTicks, MatrixStack poseStack) {
        // Interpolate position between previous and current
        float x = fLerp((float) entity.prevX, (float) entity.getPosition().getX(), partialTicks);
        float y = fLerp((float) entity.prevY, (float) entity.getPosition().getY(), partialTicks);
        float z = fLerp((float) entity.prevZ, (float) entity.getPosition().getZ(), partialTicks);

        // Interpolate rotation between previous and current
        float xR = fLerp(entity.prevRotationX, entity.getRotation().getX(), partialTicks);
        float yR = fLerp(entity.prevRotationY, entity.getRotation().getY(), partialTicks);
        float zR = fLerp(entity.prevRotationZ, entity.getRotation().getZ(), partialTicks);

        poseStack.translate(x, y, z);
        poseStack.rotateXYZ(xR, yR, zR);
        poseStack.scale(entity.getScale().getX(), entity.getScale().getY(), entity.getScale().getZ());
    }

    /**
     * Loads the model, skin, and initializes the animation controller.
     * Override to customize loading behavior.
     */
    protected void loadModel(T entity) {
        try {
            modelId = getModelId();
            model = ModelManager.getModel(modelId);
            skin = ModelManager.getSkin(modelId);

            modelLoaded = true;

            if (model == null || skin == null) {
                throw new RuntimeException("Failed to load model or skin: " + modelId);
            }
        } catch (Exception e) {
            // Mark as loaded to prevent retry spam
            modelLoaded = true;
        }
    }


    /**
     * Returns the model identifier for this entity.
     * Must be implemented by subclasses.
     */
    public abstract Identifier getModelId();

    // ============================================================================
    // Accessors
    // ============================================================================

    /**
     * Gets the loaded model instance.
     */
    public StrataModel getModel() {
        return model;
    }

    /**
     * Gets the loaded skin instance.
     */
    public StrataSkin getSkin() {
        return skin;
    }

    /**
     * Checks if the model and animation controller are loaded.
     */
    protected boolean isModelLoaded() {
        return modelLoaded && model != null;
    }
}