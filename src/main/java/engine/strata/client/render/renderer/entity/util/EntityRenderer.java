package engine.strata.client.render.renderer.entity.util;

import engine.helios.BufferBuilder;
import engine.helios.MatrixStack;
import engine.helios.RenderLayer;
import engine.strata.client.StrataClient;
import engine.strata.entity.Entity;

public abstract class EntityRenderer<T extends Entity> {
    protected final EntityRenderDispatcher dispatcher;

    public EntityRenderer(EntityRendererFactory.Context ctx) {
        this.dispatcher = ctx.dispatcher;
    }

    /**
     * The main method called every frame to render the entity.
     * @param entity       The entity instance
     * @param partialTicks The fraction of time between ticks (0.0 to 1.0) for smoothing
     * @param poseStack    The MatrixStack for position/rotation
     */
    public abstract void render(T entity, float partialTicks, MatrixStack poseStack);

    /**
     * Helper to get a buffer for a specific render layer.
     */
    protected BufferBuilder getBuffer(RenderLayer layer) {
        return StrataClient.getInstance().getMasterRenderer().getBuffer(layer);
    }

    /**
     * Helper to verify if an entity should be rendered (Culling).
     * You can override this if you have special entities that are always visible.
     */
    public boolean shouldRender(T entity, float camX, float camY, float camZ) {
        return entity.isInRange(camX, camY, camZ);
    }
}