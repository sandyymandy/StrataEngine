package engine.strata.client.frontend.render.renderer.entity;

import engine.strata.client.frontend.render.renderer.entity.util.EntityRenderer;
import engine.strata.client.frontend.render.renderer.entity.util.EntityRendererFactory;
import engine.strata.client.input.keybind.Keybinds;
import engine.strata.entity.entities.MikaEntity;
import engine.strata.util.Identifier;

public class MikaEntityRenderer extends EntityRenderer<MikaEntity> {
    public MikaEntityRenderer(EntityRendererFactory.Context ctx) {
        super(ctx);
    }

    @Override
    public Identifier getModelId() {
        if(Keybinds.CROUCH.isPressedTick()) return Identifier.ofEngine("bia");
        return Identifier.ofEngine("mika");
    }
}
