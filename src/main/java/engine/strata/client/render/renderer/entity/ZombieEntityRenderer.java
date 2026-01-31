package engine.strata.client.render.renderer.entity;

import engine.helios.BufferBuilder;
import engine.helios.MatrixStack;
import engine.helios.RenderLayer;
import engine.helios.VertexFormat;
import engine.strata.client.render.RenderLayers;
import engine.strata.client.render.model.ModelManager;
import engine.strata.client.render.model.StrataModel;
import engine.strata.client.render.renderer.entity.util.EntityRenderer;
import engine.strata.client.render.renderer.entity.util.EntityRendererFactory;
import engine.strata.entity.ZombieEntity;
import engine.strata.util.Identifier;

import java.util.Map;

public class ZombieEntityRenderer extends EntityRenderer<ZombieEntity> {
    private StrataModel model;
    private Map<String, Identifier> skin;

    public ZombieEntityRenderer(EntityRendererFactory.Context ctx) {
        super(ctx);
    }

    @Override
    public Identifier getModelId() {
        return Identifier.ofEngine("zombie");
    }

}