package engine.strata.client.render.renderer.entity.util;

import engine.strata.entity.Entity;

@FunctionalInterface
public interface EntityRendererFactory<T extends Entity> {
    EntityRenderer<T> create(Context ctx);

    // This context holds tools that your renderers might need
    class Context {
        public final EntityRenderDispatcher dispatcher;

        public Context(EntityRenderDispatcher dispatcher) {
            this.dispatcher = dispatcher;
        }
    }
}