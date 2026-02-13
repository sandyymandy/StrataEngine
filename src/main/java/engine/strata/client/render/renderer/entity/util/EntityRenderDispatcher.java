package engine.strata.client.render.renderer.entity.util;

import engine.strata.entity.Entity;
import engine.strata.entity.util.EntityKey;

import java.util.HashMap;
import java.util.Map;

public class EntityRenderDispatcher {
    private final Map<EntityKey<?>, EntityRenderer<?>> renderers = new HashMap<>();
    private final EntityRendererFactory.Context context;

    public EntityRenderDispatcher() {
        this.context = new EntityRendererFactory.Context(this);
        // We still call inject to load what's already there
        syncWithRegistry();
    }

    public void syncWithRegistry() {
        EntityRendererRegistry.inject(this);
    }

    public <T extends Entity> void register(EntityKey<T> entityKey, EntityRendererFactory<T> factory) {
        renderers.put(entityKey, factory.create(this.context));
    }

    @SuppressWarnings("unchecked")
    public <T extends Entity> EntityRenderer<T> getRenderer(EntityKey<?> key) {
        EntityRenderer<?> renderer = renderers.get(key);

        // If not found in local cache, try to pull it from the Registry now
        if (renderer == null) {
            EntityRendererFactory<?> factory = EntityRendererRegistry.getFactory(key);
            if (factory != null) {
                register((EntityKey) key, (EntityRendererFactory) factory);
                renderer = renderers.get(key);
            }
        }

        return (EntityRenderer<T>) renderer;
    }
}