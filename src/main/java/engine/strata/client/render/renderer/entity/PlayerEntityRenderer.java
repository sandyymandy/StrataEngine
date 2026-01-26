package engine.strata.client.render.renderer.entity;

import engine.helios.MatrixStack;
import engine.helios.RenderLayer;
import engine.strata.client.render.RenderLayers;
import engine.strata.client.render.model.ModelManager;
import engine.strata.client.render.model.StrataModel;
import engine.strata.client.render.renderer.entity.util.EntityRenderer;
import engine.strata.client.render.renderer.entity.util.EntityRendererFactory;
import engine.strata.client.render.util.rendercommand.RenderCommandQueue;
import engine.strata.entity.PlayerEntity;
import engine.strata.util.Identifier;

import java.util.Map;

public class PlayerEntityRenderer extends EntityRenderer<PlayerEntity> {
    private static final Identifier MODEL_ID = Identifier.ofEngine("player");
    private static final Identifier SKIN_ID = Identifier.ofEngine("player");
    private final StrataModel model;
    private final Map<String, Identifier> skin;

    public PlayerEntityRenderer(EntityRendererFactory.Context ctx) {
        super(ctx);
        // Load model and skin
        this.model = ModelManager.getModel(MODEL_ID);
        this.skin = ModelManager.getSkin(SKIN_ID);
    }

    @Override
    public void render(PlayerEntity entity, float partialTicks, MatrixStack poseStack, RenderCommandQueue queue) {
        poseStack.push();

        // Scale the model (1/16th scale for Minecraft-style models)
        poseStack.scale(1.0f / 16.0f, 1.0f / 16.0f, 1.0f / 16.0f);

        // Apply animations
        applyAnimations(entity, model, partialTicks);

        // Submit model render for each texture slot
        for (String meshId : model.getRoot().getMeshIds()) {
            StrataModel.MeshData meshData = model.getMesh(meshId);
            if (meshData != null) {
                String textureSlot = meshData.getTextureSlot();
                Identifier texture = skin.getOrDefault(textureSlot, Identifier.ofEngine("missing"));
                RenderLayer layer = RenderLayers.getEntityTexture(texture);

                queue.submitModel(model, poseStack, layer, 0);
                break; // Submit once for whole model
            }
        }

        poseStack.pop();
    }

    /**
     * Apply animations to the model bones.
     */
    private void applyAnimations(PlayerEntity entity, StrataModel model, float partialTicks) {
        Map<String, StrataModel.Bone> bones = model.getAllBones();

        // Reset all animations
        for (StrataModel.Bone bone : bones.values()) {
            bone.resetAnimation();
        }

        // Apply animations (walking, arm swinging, etc.)
        // This is where you'd implement player animations
    }
}