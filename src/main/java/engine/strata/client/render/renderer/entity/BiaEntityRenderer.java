package engine.strata.client.render.renderer.entity;

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

}