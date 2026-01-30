package engine.strata.client.render.renderer.entity;

import engine.helios.BufferBuilder;
import engine.helios.MatrixStack;
import engine.helios.RenderLayer;
import engine.helios.VertexFormat;
import engine.strata.client.render.RenderLayers;
import engine.strata.client.render.model.ModelManager;
import engine.strata.client.render.model.StrataModel;
import engine.strata.client.render.renderer.entity.util.EntityRenderer;
import engine.strata.client.render.renderer.entity.util.EntityRendererFactory;
import engine.strata.entity.ZombieEntity;
import engine.strata.util.Identifier;

import java.util.Map;

public class ZombieEntityRenderer extends EntityRenderer<ZombieEntity> {
    private static final Identifier MODEL_ID = Identifier.ofEngine("zombie");
    private StrataModel model;
    private Map<String, Identifier> skin;

    public ZombieEntityRenderer(EntityRendererFactory.Context ctx) {
        super(ctx);
    }

    @Override
    public void render(ZombieEntity entity, float partialTicks, MatrixStack poseStack) {
        // Lazy load model
        if (model == null) {
            try {
                model = ModelManager.getModel(MODEL_ID);
                skin = ModelManager.getSkin(MODEL_ID);
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

}