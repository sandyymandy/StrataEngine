package engine.strata.client.render.renderer.entity;

import engine.helios.MatrixStack;
import engine.helios.RenderLayer;
import engine.strata.client.render.RenderLayers;
import engine.strata.client.render.model.ModelManager;
import engine.strata.client.render.model.StrataModel;
import engine.strata.client.render.renderer.entity.util.EntityRenderer;
import engine.strata.client.render.renderer.entity.util.EntityRendererFactory;
import engine.strata.client.render.util.rendercommand.RenderCommandQueue;
import engine.strata.entity.ZombieEntity;
import engine.strata.util.Identifier;

import java.util.Map;

public class ZombieEntityRenderer extends EntityRenderer<ZombieEntity> {
    private static final Identifier MODEL_ID = Identifier.ofEngine("zombie");
    private static final Identifier SKIN_ID = Identifier.ofEngine("zombie");
    private final StrataModel model;
    private final Map<String, Identifier> skin;

    public ZombieEntityRenderer(EntityRendererFactory.Context ctx) {
        super(ctx);
        // Load model and skin
        this.model = ModelManager.getModel(MODEL_ID);
        this.skin = ModelManager.getSkin(SKIN_ID);
    }

    @Override
    public void render(ZombieEntity entity, float partialTicks, MatrixStack poseStack, RenderCommandQueue queue) {
        poseStack.push();

        // Scale the model (example: 1/16th scale for Minecraft-style models)
        poseStack.scale(1.0f / 16.0f, 1.0f / 16.0f, 1.0f / 16.0f);

        // Apply animations here (e.g., walking animation)
        applyAnimations(entity, model, partialTicks);

        // Submit model render for each texture slot
        for (String meshId : model.getRoot().getMeshIds()) {
            StrataModel.MeshData meshData = model.getMesh(meshId);
            if (meshData != null) {
                String textureSlot = meshData.getTextureSlot();
                Identifier texture = skin.getOrDefault(textureSlot, Identifier.ofEngine("missing"));
                RenderLayer layer = RenderLayers.getEntityTexture(texture);

                queue.submitModel(model, poseStack, layer, 0);
                break; // For now, just submit once for the whole model
            }
        }

        poseStack.pop();
    }

    /**
     * Apply animations to the model bones.
     */
    private void applyAnimations(ZombieEntity entity, StrataModel model, float partialTicks) {
        // Example: Simple walking animation
        Map<String, StrataModel.Bone> bones = model.getAllBones();

        // Reset all animations first
        for (StrataModel.Bone bone : bones.values()) {
            bone.resetAnimation();
        }

        // Apply walking animation (example)
        // float walkCycle = (entity.ticksExisted + partialTicks) * 0.1f;
        // StrataModel.Bone rightLeg = bones.get("rightLeg");
        // if (rightLeg != null) {
        //     rightLeg.setAnimRotation((float)Math.sin(walkCycle) * 45, 0, 0);
        // }
    }
}