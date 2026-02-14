package engine.strata.entity;

import com.github.stephengold.joltjni.Body;
import com.github.stephengold.joltjni.BodyCreationSettings;
import com.github.stephengold.joltjni.RVec3;
import com.github.stephengold.joltjni.enumerate.EActivation;
import engine.strata.entity.util.EntityKey;
import engine.strata.world.World;

public abstract class PhysicsEntity extends Entity {

    protected Body physicsBody;

    public PhysicsEntity(EntityKey<?> key, World world) {
        super(key, world);
    }

    /**
     * Subclasses must call this in their constructor after setting initial position/rotation
     */
    protected void createPhysicsBody(BodyCreationSettings settings) {
        if (physicsBody != null) {
            throw new IllegalStateException("Physics body already created");
        }

        physicsBody = world.getPhysicsManager()
                .getBodyInterface()
                .createBody(settings);

        world.getPhysicsManager()
                .getBodyInterface()
                .addBody(physicsBody, EActivation.Activate);
    }

    @Override
    public void tick() {
        super.tick();  // saves previous position/rotation

        if (physicsBody != null && physicsBody.isActive()) {
            // Read from physics → override visual position & rotation
            RVec3 pos = physicsBody.getPosition();
            position.set((Double) pos.getX(), (Double) pos.getY(), (Double) pos.getZ());

            // If you want rotation too:
            // Quat rot = physicsBody.getRotation();
            // rotation.set(... convert quat to euler ...);

            // Optional: sync velocity if you need it for other logic
            // velocity = new Vec3d(physicsBody.getLinearVelocity().getX(), ...);
        }
    }

    public void destroy() {
        if (physicsBody != null && physicsBody.isActive()) {
            world.getPhysicsManager().getBodyInterface().destroyBody(physicsBody.getId());
            physicsBody = null;
        }
    }


    @Override
    public void finalize() throws Throwable {
        destroy();  // safety net
        super.finalize();
    }

    public Body getPhysicsBody() {
        return physicsBody;
    }
}