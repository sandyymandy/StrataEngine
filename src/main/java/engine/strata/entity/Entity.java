package engine.strata.entity;

import engine.strata.entity.util.EntityKey;
import engine.strata.util.math.Math;
import engine.strata.util.math.Random;
import engine.strata.util.math.Vec3d;
import engine.strata.util.math.Vec3f;
import engine.strata.world.SpatialObject;
import engine.strata.world.World;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

public class Entity extends SpatialObject {
    private static final AtomicInteger CURRENT_ID = new AtomicInteger();
    protected final EntityKey<?> key;
    protected final World world;
    private float headYaw;
    private float headPitch;
    public double prevX;
    public double prevY;
    public double prevZ;
    public float prevRotationX;
    public float prevRotationY;
    public float prevRotationZ;
    public float prevHeadYaw;
    public float prevHeadPitch;
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
        this.prevRotationX = this.rotation.getX();
        this.prevRotationY = this.rotation.getY();
        this.prevRotationZ = this.rotation.getZ();
        this.prevHeadYaw = this.headYaw;
        this.prevHeadPitch = this.headPitch;
    }

    public void setHeadYaw(float yaw) {this.headYaw = yaw;}
    public void setHeadPitch(float pitch) {this.headPitch = pitch;}


    public float getHeadYaw() { return this.headYaw; }
    public float getHeadPitch() { return this.headPitch; }
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
