package engine.strata.registry.registries;

import engine.strata.entity.Entity;
import engine.strata.entity.EntityKey;
import engine.strata.entity.PlayerEntity;
import engine.strata.entity.ZombieEntity;
import engine.strata.registry.Registry;
import engine.strata.util.Identifier;

import java.util.HashMap;
import java.util.Map;

public class EntityRegistry {

    public static final EntityKey<ZombieEntity> ZOMBIE = register("zombie", EntityKey.Builder.create(ZombieEntity::new));
    public static final EntityKey<PlayerEntity> PLAYER = register("player", EntityKey.Builder.create(PlayerEntity::new));

    /**
     * Internal helper to make the registration not take a Identifier.
     */
    private static <T extends Entity> EntityKey<T> register(String id, EntityKey.Builder<T> builder) {
        Identifier identifier = Identifier.ofEngine(id);
        return Registry.register(
                Registries.ENTITY_KEY,
                identifier,
                builder.build()
        );
    }


}
