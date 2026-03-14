package engine.strata.entity.util;

import engine.strata.entity.Entity;
import engine.strata.util.Identifier;
import engine.strata.world.World;

/**
 * Enhanced EntityKey with model capability flags.
 *
 * <h3>Usage:</h3>
 * <pre>
 * // Character model with gender variants and NSFW bones:
 * public static final EntityKey&lt;CharacterEntity&gt; CHARACTER = EntityKey.Builder
 *     .create(CharacterEntity::new)
 *     .dimensions(0.6f, 1.8f)
 *     .supportsGenders(true)   // Model has meshMale/meshFemale bones
 *     .supportsNSFW(true)      // Model has genital bones
 *     .build(Identifier.ofEngine("core"));
*
* // Simple model without variants:
* public static final EntityKey&lt;ZombieEntity&gt; ZOMBIE = EntityKey.Builder
*     .create(ZombieEntity::new)
*     .dimensions(0.6f, 1.8f)
*     .build(Identifier.ofEngine("zombie"));
* </pre>
*/
public class EntityKey<T extends Entity> {
    private final EntityFactory<T> factory;
    private final Identifier entityId;
    private final float width;
    private final float height;

    // Model capability flags
    private final boolean supportsGenders;
    private final boolean supportsNSFW;

    // Functional interface to create the entity
    public interface EntityFactory<T extends Entity> {
        T create(EntityKey<T> type, World world);
    }

    public EntityKey(EntityFactory<T> factory, Identifier entityId, float width, float height,
                     boolean supportsGenders, boolean supportsNSFW) {
        this.factory = factory;
        this.entityId = entityId;
        this.width = width;
        this.height = height;
        this.supportsGenders = supportsGenders;
        this.supportsNSFW = supportsNSFW;
    }

    public T create(World world) {
        return factory.create(this, world);
    }

    public Identifier getEntityId() { return entityId; }
    public float getWidth() { return width; }
    public float getHeight() { return height; }

    /**
     * Returns true if this model has meshMale/meshFemale bones.
    * Indicates the model supports gender variants.
    */
    public boolean supportsGenders() { return supportsGenders; }

    /**
     * Returns true if this model has genital bones.
     * Indicates the model has NSFW content that can be toggled.
     */
    public boolean supportsNSFW() { return supportsNSFW; }

    // Helper builder for clean registration
    public static class Builder<T extends Entity> {
        private final EntityFactory<T> factory;
        private float width = 0.6f;
        private float height = 1.8f;
        private boolean supportsGenders = false;
        private boolean supportsNSFW = false;

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

        /**
         * Sets whether this model has meshMale/meshFemale bones.
         * When true, the gender system will automatically manage bone visibility.
         */
        public Builder<T> supportsGenders(boolean supports) {
            this.supportsGenders = supports;
            return this;
        }

        /**
         * Sets whether this model has genital bones.
         * When true, nude mode can toggle genital bone visibility.
         */
        public Builder<T> supportsNSFW(boolean supports) {
            this.supportsNSFW = supports;
            return this;
        }

        public EntityKey<T> build(Identifier entityId) {
            return new EntityKey<>(factory, entityId, width, height, supportsGenders, supportsNSFW);
        }
    }
}