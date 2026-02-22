package engine.strata.entity;

import engine.helios.physics.RigidBody;
import engine.strata.entity.util.EntityKey;
import engine.strata.util.math.Math;
import engine.strata.util.math.Random;
import engine.strata.util.math.Vec3d;
import engine.strata.util.math.Vec3f;
import engine.strata.world.SpatialObject;
import engine.strata.world.World;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

public class Entity extends RigidBody {
    private static final AtomicInteger CURRENT_ID = new AtomicInteger();

    protected final EntityKey<?> key;
    protected final World world;

    // Head rotation (separate from body rotation)
    private float headYaw;
    private float headPitch;

    // Previous state for interpolation
    public double prevX;
    public double prevY;
    public double prevZ;
    public float prevRotationX;
    public float prevRotationY;
    public float prevRotationZ;
    public float prevHeadYaw;
    public float prevHeadPitch;

    // Entity properties
    public float eyeHeight = 1.62f;
    protected final Random random = new Random(System.currentTimeMillis());
    protected UUID uuid = Math.randomUuid(this.random);
    private int id = CURRENT_ID.incrementAndGet();

    public Entity(EntityKey<?> key, World world){
        this.key = key;
        this.world = world;
        this.position.set(Vec3d.ZERO);

//        this.setCollisionBox(0.6, 1.8, 0.6);
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

        super.tick();
    }

    /**
     * Makes the entity jump if it's on the ground.
     * @param jumpVelocity The upward velocity to apply (typically 8-12)
     */
    public void jump(double jumpVelocity) {
        if (isOnGround() && !noClip) {
            velocity = new Vec3d(velocity.getX(), jumpVelocity, velocity.getZ());
            setOnGround(false);
        }
    }

    /**
     * Applies horizontal movement in the direction the entity is facing (yaw only).
     * @param forward Forward/backward movement (-1 to 1)
     * @param strafe Left/right movement (-1 to 1)
     * @param speed Movement speed multiplier
     */
    public void moveRelative(float forward, float strafe, float speed) {
        // Calculate movement direction based on yaw
        double yawRad = Math.toRadians(this.getHeadYaw());

        // 3. Calculate forward and right vector components based on yaw
        double forwardX = Math.sin(-yawRad);
        double forwardZ = Math.cos(-yawRad);

        double rightX = Math.cos(yawRad);
        double rightZ = Math.sin(yawRad);

        // 4. Combine inputs with direction vectors
        double moveX = (forwardX * forward) + (rightX * strafe);
        double moveZ = (forwardZ * forward) + (rightZ * strafe);

        // 5. Normalize so diagonal movement isn't faster than walking straight
        double length = Math.sqrt(moveX * moveX + moveZ * moveZ);
        if (length > 0.01) {
            moveX /= length;
            moveZ /= length;
        }

        // Apply movement as a force or direct velocity
        if (isOnGround()) {
            // Ground movement - set velocity directly
            velocity = new Vec3d(moveX * speed, velocity.getY(), moveZ * speed);
        } else {
            // Air movement - limited control
            velocity = velocity.add(moveX * speed * 0.02, 0, moveZ * speed * 0.02);
        }
    }

    /**
     * Applies 3D movement in the direction the entity's head is facing (yaw + pitch).
     * Used for flying/noclip where you move in the exact direction you're looking.
     * @param forward Forward/backward movement (-1 to 1)
     * @param strafe Left/right movement (-1 to 1)
     * @param vertical Up/down movement (-1 to 1)
     * @param speed Movement speed multiplier
     */
    public void moveRelative3D(float forward, float strafe, float vertical, float speed) {
        // Convert angles to radians
        double yawRad = Math.toRadians(headYaw);
        double pitchRad = Math.toRadians(headPitch);

        // Calculate forward direction vector (where camera is looking)
        double forwardX = -Math.sin(yawRad) * Math.cos(pitchRad);
        double forwardY = -Math.sin(pitchRad);
        double forwardZ = Math.cos(yawRad) * Math.cos(pitchRad);

        // Calculate right direction vector (perpendicular to forward, on horizontal plane)
        double rightX = Math.cos(yawRad);
        double rightZ = Math.sin(yawRad);

        // Calculate up direction vector (perpendicular to both forward and right)
        double upX = Math.sin(yawRad) * Math.sin(pitchRad);
        double upY = Math.cos(pitchRad);
        double upZ = -Math.cos(yawRad) * Math.sin(pitchRad);

        // Combine all movement directions
        double moveX = forwardX * forward + rightX * strafe + upX * vertical;
        double moveY = forwardY * forward + upY * vertical;
        double moveZ = forwardZ * forward + rightZ * strafe + upZ * vertical;

        // Normalize if moving
        double length = Math.sqrt(moveX * moveX + moveY * moveY + moveZ * moveZ);
        if (length > 0.01) {
            moveX /= length;
            moveY /= length;
            moveZ /= length;
        }

        // Apply movement - direct velocity for smooth flying
        velocity = new Vec3d(moveX * speed, moveY * speed, moveZ * speed);
    }

    // Head rotation getters and setters

    public void setHeadYaw(float yaw) {
        this.headYaw = yaw;
    }

    public void setHeadPitch(float pitch) {
        this.headPitch = pitch;
    }

    public float getHeadYaw() {
        return this.headYaw;
    }

    public float getHeadPitch() {
        return this.headPitch;
    }

    public float getEyeHeight() {
        return eyeHeight;
    }

    public void setEyeHeight(float eyeHeight) {
        this.eyeHeight = eyeHeight;
    }

    // Entity property getters

    public EntityKey<?> getKey() {
        return this.key;
    }

    public World getWorld() {
        return this.world;
    }

    public float getWidth() {
        return (float) collisionBox.getWidth();
    }

    public float getHeight() {
        return (float) collisionBox.getHeight();
    }

    public int getId() {
        return this.id;
    }

    public UUID getUuid() {
        return this.uuid;
    }
}