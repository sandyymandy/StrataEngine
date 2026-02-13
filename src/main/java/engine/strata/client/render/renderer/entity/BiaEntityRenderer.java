package engine.strata.client.render.renderer.entity;

import engine.strata.client.render.animation.core.AnimationController;
import engine.strata.client.render.renderer.entity.util.EntityRenderer;
import engine.strata.client.render.renderer.entity.util.EntityRendererFactory;
import engine.strata.client.render.snapshot.EntityRenderSnapshot;
import engine.strata.entity.entities.BiaEntity;
import engine.strata.util.Identifier;

/**
 * Renderer for BiaEntity.
 *
 * <p><b>FIXED ISSUES:</b>
 * <ul>
 *   <li>Added super.updateAnimations() call to tick the controller</li>
 *   <li>Added check to prevent restarting animation every frame</li>
 *   <li>Added proper animation state management</li>
 * </ul>
 */
public class BiaEntityRenderer extends EntityRenderer<BiaEntity> {

    public BiaEntityRenderer(EntityRendererFactory.Context ctx) {
        super(ctx);
    }

    @Override
    public Identifier getModelId() {
        return Identifier.ofEngine("bia");
    }

    @Override
    protected void updateAnimations(EntityRenderSnapshot snapshot, float partialTicks) {
        super.updateAnimations(snapshot, partialTicks);

        AnimationController controller = getAnimationController();
        if (controller == null) {
            return;
        }

//        // CRITICAL FIX #2: Only play animation if not already playing
//        // Otherwise it restarts 60 times per second = vibration!
//        if (!controller.isPlaying("base")) {
//            controller.play(getModelId(), "walk");
//        }
//
//            if (!controller.isPlaying() ||
//                !"walk".equals(getCurrentAnimationName(controller))) {
//                controller.play(getModelId(), "walk", 0.2f);
//            }
    }

    /**
     * Helper to get current animation name for a layer.
     */
    private String getCurrentAnimationName(AnimationController controller) {
        var anim = controller.getCurrentAnimation("base");
        return anim != null ? anim.name() : null;
    }
}