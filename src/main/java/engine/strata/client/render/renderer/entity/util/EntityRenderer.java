package engine.strata.client.render.renderer.entity.util;

import engine.helios.BufferBuilder;
import engine.helios.MatrixStack;
import engine.helios.RenderLayer;
import engine.strata.client.StrataClient;
import engine.strata.client.render.model.io.ModelManager;
import engine.strata.client.render.model.StrataModel;
import engine.strata.client.render.model.StrataSkin;
import engine.strata.client.render.snapshot.EntityRenderSnapshot;
import engine.strata.entity.Entity;
import engine.strata.util.Identifier;

import static engine.strata.util.math.Math.fLerp;

/**
 * Base class for rendering entities using the StrataModel system.
 * Handles texture layers, batching, render order, and animations.
 */
public abstract class EntityRenderer<T extends Entity> {
    protected final EntityRenderDispatcher dispatcher;
    private StrataModel model;
    private StrataSkin skin;
    private boolean modelLoaded = false;

    public EntityRenderer(EntityRendererFactory.Context ctx) {
        this.dispatcher = ctx.dispatcher;
    }

    /**
     * The main method called every frame to render the entity.
     *
     * @param snapshot       A snapshot of the entity that only has data
     * @param partialTicks The fraction of time between ticks (0.0 to 1.0) for smoothing
     * @param poseStack    The MatrixStack for position/rotation
     */
    public void render(EntityRenderSnapshot snapshot, float partialTicks, MatrixStack poseStack) {
        // Lazy load model and skin on first render
        if (!modelLoaded) {
            loadModel(snapshot);
            if (model == null || skin == null) {
                return; // Failed to load
            }
        }

        applyTransforms(snapshot, poseStack);

        poseStack.push();

        // Scale from model space (16x16x16) to world space (1x1x1)
        poseStack.scale(1.0f / 16.0f, 1.0f / 16.0f, 1.0f / 16.0f);

        poseStack.scale(snapshot.getScale().getX(), snapshot.getScale().getY(), snapshot.getScale().getZ());

        // Render all texture layers in priority order
        StrataClient.getInstance().getMasterRenderer()
                .getModelRenderer().render(model, skin, poseStack);

        poseStack.pop();
    }

    private void applyTransforms(EntityRenderSnapshot snapshot, MatrixStack poseStack) {

        float x = fLerp(snapshot.getPrevPosition().getX(), snapshot.getPosition().getX(), snapshot.getPartialTicks());
        float y = fLerp(snapshot.getPrevPosition().getY(), snapshot.getPosition().getY(), snapshot.getPartialTicks());
        float z = fLerp(snapshot.getPrevPosition().getZ(), snapshot.getPosition().getZ(), snapshot.getPartialTicks());

        float xR = fLerp(snapshot.getPrevRotation().getX(), snapshot.getPrevRotation().getX(), snapshot.getPartialTicks());
        float yR = fLerp(snapshot.getPrevRotation().getY(), snapshot.getPrevRotation().getY(), snapshot.getPartialTicks());
        float zR = fLerp(snapshot.getPrevRotation().getZ(), snapshot.getPrevRotation().getZ(), snapshot.getPartialTicks());

        poseStack.translate(x, y, z);
        poseStack.rotateXYZ(xR,yR,zR);
        poseStack.scale(snapshot.getScale().getX(), snapshot.getScale().getY(), snapshot.getScale().getZ());

    }

    /**
     * Loads the model, skin, and initializes the animation controller.
     * Override to customize loading behavior.
     */
    protected void loadModel(EntityRenderSnapshot snapshot) {
        try {
            Identifier modelId = getModelId();
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
     * Helper to get a buffer for a specific render layer.
     */
    protected BufferBuilder getBuffer(RenderLayer layer) {
        return StrataClient.getInstance().getMasterRenderer().getBuffer(layer);
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
    protected StrataModel getModel() {
        return model;
    }

    /**
     * Gets the loaded skin instance.
     */
    protected StrataSkin getSkin() {
        return skin;
    }

    /**
     * Checks if the model and animation controller are loaded.
     */
    protected boolean isModelLoaded() {
        return modelLoaded && model != null ;
    }
}