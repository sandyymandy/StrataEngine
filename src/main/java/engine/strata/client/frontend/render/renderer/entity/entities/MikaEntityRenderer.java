package engine.strata.client.frontend.render.renderer.entity.entities;

import engine.strata.client.frontend.render.model.ModelInstance;
import engine.strata.client.frontend.render.model.io.ModelManager;
import engine.strata.client.frontend.render.renderer.context.RenderContext;
import engine.strata.entity.entities.MikaEntity;
import engine.strata.entity.entities.PlayerEntity;
import engine.strata.util.Identifier;

public class MikaEntityRenderer extends EntityRenderer<MikaEntity> {

    private static final Identifier MODEL_ID =
            Identifier.ofEngine("mika");

    @Override
    public RenderContext createRenderContext(MikaEntity entity, float partialTicks) {
        return RenderContext.builder()
                .addModel(new ModelInstance(MODEL_ID, ModelManager.getSkin(MODEL_ID)))
                .build();
    }
}
