package engine.strata.client.frontend.render.renderer.entity.entities;

import engine.strata.client.frontend.render.model.ModelInstance;
import engine.strata.client.frontend.render.model.io.ModelManager;
import engine.strata.client.frontend.render.renderer.context.RenderContext;
import engine.strata.entity.entities.CharacterEntity;
import engine.strata.util.Identifier;

public class CharacterEntityRenderer extends EntityRenderer<CharacterEntity> {
    private static final Identifier MODEL_ID =
            Identifier.ofEngine("character");

    @Override
    public RenderContext createRenderContext(CharacterEntity entity, float partialTicks) {
        return RenderContext.builder()
                .addModel(new ModelInstance(MODEL_ID, ModelManager.getSkin(MODEL_ID)))
                .build();
    }
}