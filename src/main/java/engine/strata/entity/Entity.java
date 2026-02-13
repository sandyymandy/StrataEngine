package engine.strata.entity;

import engine.strata.entity.util.EntityKey;
import engine.strata.util.math.Math;
import engine.strata.util.math.Random;
import engine.strata.util.math.Vec3d;
import engine.strata.world.SpatialObject;
import engine.strata.world.World;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

public class Entity extends SpatialObject {
    private static final AtomicInteger CURRENT_ID = new AtomicInteger();
    protected final EntityKey<?> key;
    protected final World world;
    public double prevX;
    public double prevY;
    public double prevZ;
    public float prevYaw;
    public float prevPitch;
    private Vec3d velocity = Vec3d.ZERO;
    public float eyeHeight = 1.62f;
    protected final Random random = new Random(System.currentTimeMillis());
    protected UUID uuid = Math.randomUuid(this.random);
    private int id = CURRENT_ID.incrementAndGet();

    public Entity(EntityKey<?> key, World world){
        this.key = key;
        this.world = world;
        this.position.set(Vec3d.ZERO);
    }

    public void tick(){
        this.prevX = this.position.getX();
        this.prevY = this.position.getY();
        this.prevZ = this.position.getZ();
        this.prevYaw = this.rotation.getY();
        this.prevPitch = this.rotation.getX();
    }

    public void setYaw(float yaw) {this.rotation.setY(yaw);}
    public void setPitch(float pitch) {this.rotation.setX(pitch);}


    public float getYaw() { return this.rotation.getY(); }
    public float getPitch() { return this.rotation.getX(); }
    public float getEyeHeight() { return eyeHeight; }

    public EntityKey<?> getKey() {
        return this.key;
    }

    public World getWorld() {
        return this.world;
    }

    public float getWidth() {
        return 1f;
    }

    public int getId() {return this.id;}

    public UUID getUuid() {return this.uuid;}
}
