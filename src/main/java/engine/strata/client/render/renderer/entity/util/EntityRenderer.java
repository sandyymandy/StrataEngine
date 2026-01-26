package engine.strata.client.render.renderer.entity.util;

import engine.helios.MatrixStack;
import engine.strata.client.render.util.rendercommand.RenderCommandQueue;
import engine.strata.entity.Entity;

public abstract class EntityRenderer<T extends Entity> {
    protected final EntityRenderDispatcher dispatcher;

    // We keep the Context so we can access global tools if needed
    public EntityRenderer(EntityRendererFactory.Context ctx) {
        this.dispatcher = ctx.dispatcher;
    }

    /**
     * The main method called every frame by the Logic Thread.
     * @param entity       The entity instance
     * @param partialTicks The fraction of time between ticks (0.0 to 1.0) for smoothing
     * @param poseStack        The MatrixStack for position/rotation
     * @param queue        The command queue to send data to the Render Thread
     */
    public abstract void render(T entity, float partialTicks, MatrixStack poseStack, RenderCommandQueue queue);

    /**
     * Helper to verify if an entity should be rendered (Culling).
     * You can override this if you have special entities that are always visible.
     */
    public boolean shouldRender(T entity, float camX, float camY, float camZ) {
        return entity.isInRange(camX, camY, camZ); // Simple distance check
    }
}