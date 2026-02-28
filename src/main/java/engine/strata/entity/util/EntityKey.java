package engine.strata.entity.util;

import engine.strata.entity.Entity;
import engine.strata.util.Identifier;
import engine.strata.world.World;

/**
 * Enhanced EntityKey with integrated model information.
 *
 * <h3>Usage:</h3>
 * <pre>
 * // Simple registration with model:
 * public static final EntityKey&lt;BiaEntity&gt; BIA = EntityKey.Builder
 *     .create(BiaEntity::new)
 *     .dimensions(0.6f, 1.8f)
 *     .model(Identifier.ofEngine("bia"))
 *     .build();
 *
 * // Entity can override model dynamically:
 * {@code @Override}
 * public Identifier getModelId() {
 *     return isCrouching ? Identifier.ofEngine("bia_crouch") : super.getModelId();
 * }
 * </pre>
 */
public class EntityKey<T extends Entity> {
    private final EntityFactory<T> factory;
    private final Identifier entityId; // New: default model for this entity type
    private final float width;
    private final float height;

    // Functional interface to create the entity
    public interface EntityFactory<T extends Entity> {
        T create(EntityKey<T> type, World world);
    }

    public EntityKey(EntityFactory<T> factory, Identifier entityId, float width, float height) {
        this.factory = factory;
        this.entityId = entityId;
        this.width = width;
        this.height = height;
    }

    public T create(World world) {
        return factory.create(this, world);
    }

    public Identifier getEntityId() { return entityId; }

    public float getWidth() { return width; }

    public float getHeight() { return height; }

    // Helper builder for clean registration
    public static class Builder<T extends Entity> {
        private final EntityFactory<T> factory;
        private float width = 0.6f;
        private float height = 1.8f;

        private Builder(EntityFactory<T> factory) {
            this.factory = factory;
        }

        public static <T extends Entity> Builder<T> create(EntityFactory<T> factory) {
            return new Builder<>(factory);
        }

        /**
         * Sets the collision box dimensions.
         */
        public Builder<T> dimensions(float width, float height) {
            this.width = width;
            this.height = height;
            return this;
        }

        public EntityKey<T> build(Identifier entityId) {
            return new EntityKey<>(factory, entityId, width, height);
        }
    }
}