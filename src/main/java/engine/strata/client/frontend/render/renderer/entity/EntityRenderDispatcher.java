package engine.strata.client.frontend.render.renderer.entity;

import engine.helios.rendering.vertex.MatrixStack;
import engine.strata.client.frontend.render.renderer.context.RenderContext;
import engine.strata.client.frontend.render.renderer.entity.entities.EntityRenderer;
import engine.strata.entity.Entity;
import engine.strata.entity.util.EntityKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * Owns the live {@link EntityKey} → {@link EntityRenderer} map and drives
 * both single-entity and batched rendering each frame.
 *
 * <h3>Responsibilities</h3>
 * <ul>
 *   <li>Instantiates renderers from {@link EntityRendererRegistry.Factory} on registration</li>
 *   <li>Lazy-registers any factory added to {@link EntityRendererRegistry} after construction</li>
 *   <li>Groups entities by renderer type during batch rendering to minimise GL state changes</li>
 * </ul>
 *
 * <h3>Usage</h3>
 * <pre>{@code
 * // Single entity (immediate)
 * dispatcher.render(entity, partialTicks, poseStack);
 *
 * // Batched — preferred, shares beginBatch/endBatch state per renderer type
 * dispatcher.renderBatch(visibleEntities, partialTicks);
 * }</pre>
 */
public class EntityRenderDispatcher {

    private static final Logger LOGGER = LoggerFactory.getLogger("EntityRenderDispatcher");

    private final Map<EntityKey<?>, EntityRenderer<?>> renderers = new HashMap<>();

    public EntityRenderDispatcher() {
        syncWithRegistry();
    }

    // ══════════════════════════════════════════════════════════════════════════
    // REGISTRATION
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * Instantiate and register a renderer for an entity type.
     *
     * @param key     Entity type key
     * @param factory Factory that produces the renderer (usually a {@code ::new} ref)
     * @param <T>     Entity type
     */
    public <T extends Entity> void register(EntityKey<T> key,
                                            EntityRendererRegistry.Factory<T> factory) {
        EntityRenderer<T> renderer = factory.create();
        renderers.put(key, renderer);
        LOGGER.debug("Registered renderer {} for entity {}",
                renderer.getClass().getSimpleName(), key);
    }

    /**
     * Pull all entries from the global registry into this dispatcher.
     * Called on construction and whenever the registry is updated.
     */
    public void syncWithRegistry() {
        EntityRendererRegistry.inject(this);
        LOGGER.info("Synced {} entity renderers from registry", renderers.size());
    }

    // ══════════════════════════════════════════════════════════════════════════
    // RENDERER LOOKUP
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * Returns the renderer for an entity type key.
     * Falls back to the global registry for lazy-registered factories.
     *
     * @return Renderer or {@code null} if not registered
     */
    @SuppressWarnings("unchecked")
    public <T extends Entity> EntityRenderer<T> getRenderer(EntityKey<T> key) {
        EntityRenderer<?> renderer = renderers.get(key);
        if (renderer == null) {
            EntityRendererRegistry.Factory<T> factory = EntityRendererRegistry.getFactory(key);
            if (factory != null) {
                register(key, factory);
                renderer = renderers.get(key);
            }
        }
        return (EntityRenderer<T>) renderer;
    }

    /**
     * Convenience overload — resolves the key from the entity instance.
     */
    @SuppressWarnings("unchecked")
    public <T extends Entity> EntityRenderer<T> getRenderer(T entity) {
        return getRenderer((EntityKey<T>) entity.getEntityKey());
    }

    // ══════════════════════════════════════════════════════════════════════════
    // RENDERING — Single entity
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * Render a single entity immediately.
     *
     * @param entity       Entity to render
     * @param partialTicks Frame interpolation factor
     * @param poseStack    Transform stack
     */
    public <T extends Entity> void render(T entity, float partialTicks, MatrixStack poseStack) {
        EntityRenderer<T> renderer = getRenderer(entity);
        if (renderer == null) {
            LOGGER.warn("No renderer for entity type: {}", entity.getClass().getSimpleName());
            return;
        }
        RenderContext context = renderer.createRenderContext(entity, partialTicks);
        renderer.renderObject(entity, context, partialTicks, poseStack);
    }

    // ══════════════════════════════════════════════════════════════════════════
    // RENDERING — Batched
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * Render a collection of entities with batching optimisation.
     *
     * <p>Entities are grouped by renderer type so that
     * {@link EntityRenderer#beginBatch()} / {@link EntityRenderer#endBatch()}
     * are called only once per renderer per frame, minimising redundant
     * GL state changes.
     *
     * @param entities     Entities to render
     * @param partialTicks Frame interpolation factor
     */
    public void renderBatch(Collection<? extends Entity> entities, float partialTicks) {
        Map<EntityRenderer<?>, List<Entity>> batches = new LinkedHashMap<>();

        for (Entity entity : entities) {
            EntityRenderer<?> renderer = getRenderer(entity);
            if (renderer != null) {
                batches.computeIfAbsent(renderer, k -> new ArrayList<>()).add(entity);
            } else {
                LOGGER.warn("Skipping entity with no renderer: {}",
                        entity.getClass().getSimpleName());
            }
        }

        batches.forEach((renderer, group) -> renderBatchInternal(renderer, group, partialTicks));
    }

    @SuppressWarnings("unchecked")
    private <T extends Entity> void renderBatchInternal(EntityRenderer<T> renderer,
                                                        List<Entity> entities,
                                                        float partialTicks) {
        renderer.beginBatch();
        try {
            for (Entity entity : entities) {
                MatrixStack poseStack  = new MatrixStack();
                RenderContext context  = renderer.createRenderContext((T) entity, partialTicks);
                renderer.renderObject((T) entity, context, partialTicks, poseStack);
            }
        } finally {
            renderer.endBatch();
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // IRenderer — Session lifecycle
    // ══════════════════════════════════════════════════════════════════════════

    public void reload() {
        LOGGER.info("Reloading {} entity renderers...", renderers.size());
        renderers.values().forEach(EntityRenderer::reload);
    }

    public void cleanup() {
        LOGGER.info("Cleaning up {} entity renderers...", renderers.size());
        renderers.values().forEach(EntityRenderer::cleanup);
        renderers.clear();
    }

    // ══════════════════════════════════════════════════════════════════════════
    // DIAGNOSTICS
    // ══════════════════════════════════════════════════════════════════════════

    /** @return Number of live renderer instances. */
    public int getRendererCount() {
        return renderers.size();
    }

    /** @return Unmodifiable view of all registered entity keys. */
    public Set<EntityKey<?>> getRegisteredEntityKeys() {
        return Collections.unmodifiableSet(renderers.keySet());
    }
}