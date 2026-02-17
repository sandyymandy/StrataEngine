package engine.strata.entity.entities;

import engine.strata.client.input.keybind.Keybinds;
import engine.strata.entity.Entity;
import engine.strata.entity.util.EntityKey;
import engine.strata.world.World;
import engine.strata.world.block.Blocks;

public class PlayerEntity extends Entity {


    public PlayerEntity(EntityKey<?> key, World world) {
        super(key, world);
    }

    @Override
    public void tick() {
        super.tick();
        if(Keybinds.PLACE.isInitiated()) {
            world.setBlock((int) this.getPosition().getX(), (int) this.getPosition().getY(), (int) this.getPosition().getZ(), Blocks.GRASS);
        }
    }


}
