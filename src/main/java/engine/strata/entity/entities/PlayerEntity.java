package engine.strata.entity.entities;

import engine.strata.entity.Entity;
import engine.strata.entity.util.EntityKey;
import engine.strata.world.World;

public class PlayerEntity extends Entity {


    public PlayerEntity(EntityKey<?> key, World world) {
        super(key, world);
    }

    @Override
    public void tick() {
        super.tick();
    }


}
