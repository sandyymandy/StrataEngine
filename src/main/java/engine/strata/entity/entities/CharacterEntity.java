package engine.strata.entity.entities;

import engine.strata.client.input.keybind.Keybinds;
import engine.strata.entity.Entity;
import engine.strata.entity.util.EntityKey;
import engine.strata.util.Gender;
import engine.strata.util.Identifier;
import engine.strata.world.World;

import java.util.Random;

public class CharacterEntity extends Entity {
    public CharacterEntity(EntityKey<?> key, World world) {
        super(key, world);

        setGender(Gender.MALE);
    }

    @Override
    public void tick() {
        super.tick();
        if (Keybinds.CROUCH.isHeldTick()) setGender(Gender.FUTANARI);
        else if(Keybinds.CRAWL.isHeldTick()) setGender(Gender.FEMALE);
        else setGender(Gender.MALE);
    }

}
