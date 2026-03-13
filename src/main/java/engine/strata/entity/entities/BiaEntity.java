package engine.strata.entity.entities;

import engine.strata.entity.Entity;
import engine.strata.entity.util.EntityKey;
import engine.strata.util.Identifier;
import engine.strata.world.World;

public class BiaEntity extends Entity {
    public BiaEntity(EntityKey<?> key, World world) {
        super(key, world);
    }

    @Override
    public Identifier getModelId() {
        return Identifier.ofEngine("core");
    }
}
