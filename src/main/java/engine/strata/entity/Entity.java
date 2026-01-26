package engine.strata.entity;

import engine.strata.util.math.Vec3d;
import engine.strata.world.World;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Entity {
    private static final Logger LOGGER = LoggerFactory.getLogger("Entity");
    protected final EntityKey<?> key;
    protected final World world;
    public double prevX;
    public double prevY;
    public double prevZ;
    private Vec3d pos;
    private Vec3d velocity = Vec3d.ZERO;
    private float yaw;
    private float pitch;
    public float prevYaw;
    public float prevPitch;
    public float eyeHeight = 1.62f;

    public Entity(EntityKey<?> key, World world){
        this.key = key;
        this.world = world;
        this.pos = Vec3d.ZERO;
    }

    public void tick(){
        this.prevX = this.pos.getX();
        this.prevY = this.pos.getY();
        this.prevZ = this.pos.getZ();
        this.prevYaw = this.yaw;
        this.prevPitch = this.pitch;
    }

    public void setPos(Vec3d pos) {
        this.setPos(pos.getX(), pos.getY(), pos.getZ());
    }

    public void setPos(double x, double y, double z) {
        this.pos = new Vec3d(x, y, z);
    }

    public void setPosX(double x) {pos.setX(x);}
    public void setPosY(double y) {pos.setY(y);}
    public void setPosZ(double z) {pos.setZ(z);}
    public void setYaw(float yaw) {this.yaw = yaw;}
    public void setPitch(float pitch) {this.pitch = pitch;}


    public Vec3d getPos() { return pos; }
    public double getX() { return pos.getX(); }
    public double getY() { return pos.getY(); }
    public double getZ() { return pos.getZ(); }
    public float getYaw() { return yaw; }
    public float getPitch() { return pitch; }
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

}
