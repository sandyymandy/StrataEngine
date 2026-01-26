package engine.strata.entity;

import engine.strata.util.Identifier;
import engine.strata.world.World;

public class EntityKey<T extends Entity> {
    private final EntityFactory<T> factory;
    private final float width;
    private final float height;

    // Functional interface to create the entity
    public interface EntityFactory<T extends Entity> {
        T create(EntityKey<T> type, World world);
    }

    public EntityKey(EntityFactory<T> factory, float width, float height) {
        this.factory = factory;
        this.width = width;
        this.height = height;
    }

    public T create(World world) {
        return factory.create(this, world);
    }

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

        public Builder<T> dimensions(float width, float height) {
            this.width = width;
            this.height = height;
            return this;
        }

        public EntityKey<T> build() {
            return new EntityKey<>(factory, width, height);
        }
    }
}