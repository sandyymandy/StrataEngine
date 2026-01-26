package engine.strata.client.render.renderer.entity.util;

import engine.strata.entity.Entity;
import engine.strata.entity.EntityKey;

import java.util.HashMap;
import java.util.Map;

public class EntityRendererRegistry {
    private static final Map<EntityKey<?>, EntityRendererFactory<?>> RENDERERS = new HashMap<>();

    public static <T extends Entity> void register(EntityKey<T> key, EntityRendererFactory<T> factory) {
        RENDERERS.put(key, factory);
    }

    // This is called by the EntityRenderDispatcher when IT is initialized
    public static void inject(EntityRenderDispatcher dispatcher) {
        RENDERERS.forEach((key, factory) -> {
            dispatcher.register((EntityKey)key, (EntityRendererFactory)factory);
        });
    }
}
