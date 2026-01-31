package engine.strata.client.render.renderer.entity.util;

import engine.helios.BufferBuilder;
import engine.helios.MatrixStack;
import engine.helios.RenderLayer;
import engine.helios.VertexFormat;
import engine.strata.client.StrataClient;
import engine.strata.client.render.RenderLayers;
import engine.strata.client.render.model.ModelManager;
import engine.strata.client.render.model.StrataModel;
import engine.strata.entity.Entity;
import engine.strata.util.Identifier;

import java.util.Map;

public abstract class EntityRenderer<T extends Entity> {
    protected final EntityRenderDispatcher dispatcher;
    private StrataModel model;
    private Map<String, Identifier> skin;

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
        // Lazy load model
        if (model == null) {
            try {
                model = ModelManager.getModel(getModelId());
                skin = ModelManager.getSkin(getModelId());
            } catch (Exception e) {
                // If model fails to load, just don't render
                return;
            }
        }

        poseStack.push();

        // Scale the model (1/16th scale)
        poseStack.scale(1.0f / 16.0f, 1.0f / 16.0f, 1.0f / 16.0f);

        // Get the texture and buffer
        String textureSlot = "main"; // Default texture slot
        if (!model.getRoot().getMeshIds().isEmpty()) {
            String meshId = model.getRoot().getMeshIds().get(0);
            StrataModel.MeshData meshData = model.getMesh(meshId);
            if (meshData != null) {
                textureSlot = meshData.textureSlot();
            }
        }

        Identifier texture = skin.getOrDefault(textureSlot, Identifier.ofEngine("missing"));
        RenderLayer layer = RenderLayers.getEntityTexture(texture);
        BufferBuilder buffer = getBuffer(layer);

        // Ensure buffer is ready
        if (!buffer.isBuilding()) {
            buffer.begin(VertexFormat.POSITION_TEXTURE_COLOR);
        }

        // Render the model directly to the buffer
        engine.strata.client.StrataClient.getInstance().getMasterRenderer()
                .getModelRenderer().render(model, poseStack, buffer);

        poseStack.pop();
    }

    /**
     * Helper to get a buffer for a specific render layer.
     */
    protected BufferBuilder getBuffer(RenderLayer layer) {
        return StrataClient.getInstance().getMasterRenderer().getBuffer(layer);
    }

    public abstract Identifier getModelId();

    /**
     * Helper to verify if an entity should be rendered (Culling).
     * You can override this if you have special entities that are always visible.
     */
    public boolean shouldRender(T entity, float camX, float camY, float camZ) {
        return entity.isInRange(camX, camY, camZ);
    }
}