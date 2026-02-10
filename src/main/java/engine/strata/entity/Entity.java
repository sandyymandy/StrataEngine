package engine.strata.entity;

import engine.strata.util.math.Vec3d;
import engine.strata.world.SpatialObject;
import engine.strata.world.World;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Entity extends SpatialObject {
    private static final Logger LOGGER = LoggerFactory.getLogger("Entity");
    protected final EntityKey<?> key;
    protected final World world;
    public double prevX;
    public double prevY;
    public double prevZ;
    public float prevYaw;
    public float prevPitch;
    private Vec3d velocity = Vec3d.ZERO;
    public float eyeHeight = 1.62f;

    public Entity(EntityKey<?> key, World world){
        this.key = key;
        this.world = world;
        this.position.set(Vec3d.ZERO);
    }

    public void tick(){
        this.prevX = this.position.getX();
        this.prevY = this.position.getY();
        this.prevZ = this.position.getZ();
        this.prevYaw = this.rotation.getX();
        this.prevPitch = this.rotation.getY();
    }

    public void setYaw(float yaw) {this.rotation.setY(yaw);}
    public void setPitch(float pitch) {this.rotation.setX(pitch);}


    public float getYaw() { return this.rotation.getY(); }
    public float getPitch() { return this.rotation.getX(); }
    public float getEyeHeight() { return eyeHeight; }

    public boolean isInRange(float camX, float camY, float camZ) {
        return true;
    }

    public EntityKey<?> getKey() {
        return this.key;
    }

    public World getWorld() {
        return this.world;
    }

    public float getWidth() {
        return 1f;
    }
}
