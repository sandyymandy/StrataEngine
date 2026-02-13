package engine.strata.client.render.renderer.entity;

import engine.strata.client.render.renderer.entity.util.EntityRenderer;
import engine.strata.client.render.renderer.entity.util.EntityRendererFactory;
import engine.strata.entity.entities.PlayerEntity;
import engine.strata.util.Identifier;

public class PlayerEntityRenderer extends EntityRenderer<PlayerEntity> {

    public PlayerEntityRenderer(EntityRendererFactory.Context ctx) {
        super(ctx);
        // Models will be loaded on first render to avoid GL context issues
    }

    @Override
    public Identifier getModelId() {
        return Identifier.ofEngine("player");
    }
}