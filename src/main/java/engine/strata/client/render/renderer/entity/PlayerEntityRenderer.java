package engine.strata.client.render.renderer.entity;

import engine.helios.BufferBuilder;
import engine.helios.MatrixStack;
import engine.helios.RenderLayer;
import engine.helios.VertexFormat;
import engine.strata.client.StrataClient;
import engine.strata.client.render.RenderLayers;
import engine.strata.client.render.model.ModelManager;
import engine.strata.client.render.model.StrataModel;
import engine.strata.client.render.renderer.entity.util.EntityRenderer;
import engine.strata.client.render.renderer.entity.util.EntityRendererFactory;
import engine.strata.entity.PlayerEntity;
import engine.strata.util.Identifier;

import java.util.Map;

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