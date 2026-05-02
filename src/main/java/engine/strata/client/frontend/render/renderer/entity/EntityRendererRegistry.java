package engine.strata.client.frontend.render.renderer.entity;

import engine.strata.client.frontend.render.renderer.entity.entities.EntityRenderer;
import engine.strata.entity.Entity;
import engine.strata.entity.util.EntityKey;

import java.util.HashMap;
import java.util.Map;

/**
 * Global registry that maps {@link EntityKey} types to renderer factories.
 *
 * <h3>Registration</h3>
 * <p>Done once during game/mod initialisation, before any dispatcher is created:
 * <pre>{@code
 * EntityRendererRegistry.register(EntityRegistry.PLAYER,    PlayerEntityRenderer::new);
 * EntityRendererRegistry.register(EntityRegistry.CHARACTER, CharacterEntityRenderer::new);
 * EntityRendererRegistry.register(EntityRegistry.MIKA,      MikaEntityRenderer::new);
 * }</pre>
 *
 * <p>The {@code ::new} method reference resolves to {@link Factory#create()} because
 * every concrete {@link EntityRenderer} subclass only needs a no-arg constructor.
 * No dispatcher reference or extra context is needed at construction time.
 *
 * <h3>Dispatcher injection</h3>
 * <p>After registration, call {@link #inject(EntityRenderDispatcher)} to push all
 * factories into a live dispatcher. The dispatcher calls this automatically on construction.
 */
public final class EntityRendererRegistry {

    // ══════════════════════════════════════════════════════════════════════════
    // NESTED FACTORY INTERFACE
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * Minimal factory that produces an {@link EntityRenderer} instance.
     *
     * <p>Being a {@code @FunctionalInterface} means concrete renderer classes
     * can be registered via a no-arg constructor reference:
     * <pre>{@code
     * EntityRendererRegistry.register(EntityRegistry.MIKA, MikaEntityRenderer::new);
     * //                                                    ↑ Factory<MikaEntity>
     * }</pre>
     *
     * <p>If a renderer needs constructor arguments, use a lambda:
     * <pre>{@code
     * EntityRendererRegistry.register(EntityRegistry.BOSS,
     *         () -> new BossEntityRenderer(bossConfig));
     * }</pre>
     *
     * @param <T> Entity type the produced renderer handles
     */
    @FunctionalInterface
    public interface Factory<T extends Entity> {
        EntityRenderer<T> create();
    }

    // ══════════════════════════════════════════════════════════════════════════
    // STORAGE
    // ══════════════════════════════════════════════════════════════════════════

    private static final Map<EntityKey<?>, Factory<?>> RENDERERS = new HashMap<>();

    private EntityRendererRegistry() {}

    // ══════════════════════════════════════════════════════════════════════════
    // REGISTRATION
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * Register a renderer factory for an entity type.
     *
     * <p>Last-write-wins if the same key is registered more than once.
     * Should only be called during game initialisation, before any
     * dispatcher is constructed.
     *
     * @param key     Entity type key — must not be {@code null}
     * @param factory Factory that produces renderer instances — must not be {@code null}
     * @param <T>     Entity type
     */
    public static <T extends Entity> void register(EntityKey<T> key, Factory<T> factory) {
        if (key == null)     throw new IllegalArgumentException("EntityKey must not be null");
        if (factory == null) throw new IllegalArgumentException("Factory must not be null");
        RENDERERS.put(key, factory);
    }

    // ══════════════════════════════════════════════════════════════════════════
    // LOOKUP
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * Returns the factory registered for {@code key}, or {@code null} if absent.
     */
    @SuppressWarnings("unchecked")
    public static <T extends Entity> Factory<T> getFactory(EntityKey<T> key) {
        return (Factory<T>) RENDERERS.get(key);
    }

    // ══════════════════════════════════════════════════════════════════════════
    // DISPATCHER INJECTION
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * Push every registered factory into {@code dispatcher}.
     * Called by {@link EntityRenderDispatcher} on construction and on reload.
     */
    public static void inject(EntityRenderDispatcher dispatcher) {
        RENDERERS.forEach((key, factory) -> injectOne(dispatcher, key, factory));
    }

    /**
     * Isolates the unavoidable unchecked cast in a single annotated place.
     * Safe because {@link #register} guarantees key and factory share the same {@code T}.
     */
    @SuppressWarnings("unchecked")
    private static <T extends Entity> void injectOne(EntityRenderDispatcher dispatcher,
                                                     EntityKey<?> key,
                                                     Factory<?> factory) {
        dispatcher.register((EntityKey<T>) key, (Factory<T>) factory);
    }

    // ══════════════════════════════════════════════════════════════════════════
    // UTILITIES
    // ══════════════════════════════════════════════════════════════════════════

    /** @return Number of registered factories. */
    public static int size() {
        return RENDERERS.size();
    }

    /** For tests / full teardown only. */
    static void clear() {
        RENDERERS.clear();
    }
}