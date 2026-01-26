package engine.strata.client.render.renderer.entity.util;

import engine.strata.entity.Entity;
import engine.strata.entity.EntityKey;

import java.util.HashMap;
import java.util.Map;

public class EntityRenderDispatcher {
    // FIX: Map EntityKey to the Renderer instead of the Class
    private final Map<EntityKey<?>, EntityRenderer<?>> renderers = new HashMap<>();
    private final EntityRendererFactory.Context context;

    public EntityRenderDispatcher() {
        this.context = new EntityRendererFactory.Context(this);
        EntityRendererRegistry.inject(this);
    }

    /**
     * Registers a renderer using an EntityKey.
     */
    public <T extends Entity> void register(EntityKey<T> entityKey, EntityRendererFactory<T> factory) {
        renderers.put(entityKey, factory.create(this.context));
    }

    /**
     * Looks up the renderer based on the entity's key.
     */
    @SuppressWarnings("unchecked")
    public <T extends Entity> EntityRenderer<T> getRenderer(T entity) {
        EntityRenderer<?> renderer = renderers.get(entity.getKey());

        if (renderer == null) {
            // Fallback: Try to find a default renderer if the specific key isn't registered
            return (EntityRenderer<T>) renderers.get(null);
        }

        return (EntityRenderer<T>) renderer;
    }
}