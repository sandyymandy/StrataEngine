package engine.strata.client.render.renderer.entity.util;

import engine.helios.BufferBuilder;
import engine.helios.MatrixStack;
import engine.helios.RenderLayer;
import engine.helios.VertexFormat;
import engine.strata.client.StrataClient;
import engine.strata.client.render.RenderLayers;
import engine.strata.client.render.model.ModelManager;
import engine.strata.client.render.model.StrataModel;
import engine.strata.client.render.model.StrataSkin;
import engine.strata.entity.Entity;
import engine.strata.util.Identifier;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * Base class for rendering entities using the StrataModel system.
 * Handles texture layers, batching, and render order.
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
     * @param entity       The entity instance
     * @param partialTicks The fraction of time between ticks (0.0 to 1.0) for smoothing
     * @param poseStack    The MatrixStack for position/rotation
     */
    public void render(T entity, float partialTicks, MatrixStack poseStack) {
        // Lazy load model and skin on first render
        if (!modelLoaded) {
            loadModel();
            if (model == null || skin == null) {
                return; // Failed to load
            }
        }

        poseStack.push();

        // Apply entity-specific transformations
        applyTransformations(entity, partialTicks, poseStack);

        // Scale from model space (16x16x16) to world space (1x1x1)
        poseStack.scale(1.0f / 16.0f, 1.0f / 16.0f, 1.0f / 16.0f);

        // Render all texture layers in priority order
        renderTextureLayers(entity, partialTicks, poseStack);

        poseStack.pop();
    }

    /**
     * Loads the model and skin. Override to customize loading behavior.
     */
    protected void loadModel() {
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
     * Renders all texture layers defined in the skin, sorted by priority.
     */
    private void renderTextureLayers(T entity, float partialTicks, MatrixStack poseStack) {
        // Sort texture slots by render priority (lower priority renders first)
        List<Map.Entry<String, StrataSkin.TextureData>> sortedTextures = new ArrayList<>(skin.textures().entrySet());
        sortedTextures.sort(Comparator.comparingInt(e -> e.getValue().renderPriority()));

        // Render each texture layer
        for (Map.Entry<String, StrataSkin.TextureData> entry : sortedTextures) {
            String slot = entry.getKey();
            StrataSkin.TextureData textureData = entry.getValue();

            // Skip if this layer shouldn't be rendered (e.g., conditional overlays)
            if (!shouldRenderLayer(entity, slot)) {
                continue;
            }

            // Get the appropriate render layer
            RenderLayer layer = RenderLayers.getLayerForSlot(
                    textureData.path(),
                    textureData.translucent()
            );

            BufferBuilder buffer = getBuffer(layer);

            // Ensure buffer is ready
            if (!buffer.isBuilding()) {
                buffer.begin(VertexFormat.POSITION_TEXTURE_COLOR);
            }

            // Render the model with this texture
            StrataClient.getInstance().getMasterRenderer()
                    .getModelRenderer().render(model, skin, poseStack, buffer);
        }
    }

    /**
     * Apply transformations specific to this entity type.
     * Override to add custom animations, bobbing, etc.
     */
    protected void applyTransformations(T entity, float partialTicks, MatrixStack poseStack) {
        // Default: no additional transformations
        // Subclasses can override to add entity-specific behavior
    }

    /**
     * Determines if a specific texture layer should be rendered.
     * Override to implement conditional rendering (e.g., damage overlay, power-ups).
     */
    protected boolean shouldRenderLayer(T entity, String textureSlot) {
        return true; // Render all layers by default
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

    /**
     * Checks if an entity should be rendered (frustum culling).
     * Override for entities with special rendering rules.
     */
    public boolean shouldRender(T entity, float camX, float camY, float camZ) {
        return entity.isInRange(camX, camY, camZ);
    }

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
}