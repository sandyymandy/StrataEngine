package engine.strata.client.render.renderer.entity;

import engine.strata.client.render.renderer.entity.util.EntityRenderer;
import engine.strata.client.render.renderer.entity.util.EntityRendererFactory;
import engine.strata.entity.BiaEntity;
import engine.strata.util.Identifier;

public class BiaEntityRenderer extends EntityRenderer<BiaEntity> {
    public BiaEntityRenderer(EntityRendererFactory.Context ctx) {
        super(ctx);
    }

    @Override
    public Identifier getModelId() {
        return Identifier.ofEngine("bia");
    }

}