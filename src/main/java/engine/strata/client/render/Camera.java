package engine.strata.client.render;

import engine.strata.entity.Entity;
import engine.strata.entity.PlayerEntity;
import org.joml.*;
import engine.strata.client.window.Window;

import java.lang.Math;

public class Camera {
    private final Vector3d pos = new Vector3d();
    private final Quaternionf rotation = new Quaternionf();
    private float fov;
    private float Z_NEAR;
    private float Z_FAR;
    private float pitch;
    private float yaw;
    private final Vector3d prevPos = new Vector3d();
    private float prevPitch;
    private float prevYaw;
    private Entity focusedEntity;
    private float cameraY;
    private float lastCameraY;

    //Planes for ClipSpace
    private final Vector3f horizontalPlane = new Vector3f(0, 0, -1);
    private final Vector3f verticalPlane = new Vector3f(0, 1, 0);

    private final Matrix4f viewMatrix = new Matrix4f();
    private final Matrix4f projectionMatrix = new Matrix4f();

    public Camera(){
        this.fov = 90;
        this.Z_NEAR = 0.01f;
        this.Z_FAR = 1000.0f;
    }

    /**
     * Updates the camera based on an entity (the Player).
     * @param partialTick The progress between game ticks (0.0 to 1.0)
     */
    public void update(Entity focusedEntity, Window window, float partialTick, boolean thirdPerson) {
        this.focusedEntity = focusedEntity;

        double x = lerp(partialTick, focusedEntity.prevX, focusedEntity.getX());
        double y = lerp(partialTick, focusedEntity.prevY, focusedEntity.getY()) + focusedEntity.getEyeHeight();
        double z = lerp(partialTick, focusedEntity.prevZ, focusedEntity.getZ());

        this.setRotation(focusedEntity.getYaw(), focusedEntity.getPitch());
        this.setPos((float)x, (float)y, (float)z);

        if (thirdPerson) {
            // Move camera backwards, but check for wall collisions
            float distance = 4.0f;
            float clippedDist = clipToSpace(distance);
            this.moveBy(-clippedDist, 0, 0);
        }

        updateMatrices(window);

        // Store current as previous for next frame
        this.prevYaw = focusedEntity.getYaw();
        this.prevPitch = focusedEntity.getPitch();
        this.prevPos.set(this.pos);
        ((PlayerEntity)focusedEntity).handleMouse();
    }

    public void updateEyeHeight() {
        if (this.focusedEntity != null) {
            this.lastCameraY = this.cameraY;
            this.cameraY = this.cameraY + (this.focusedEntity.getEyeHeight() - this.cameraY) * 0.5F;
        }
    }

    private void setRotation(float yaw, float pitch) {
        this.yaw = yaw;
        this.pitch = pitch;

        // Re-calculate the planes for clipping and movement
        new Vector3f(0, 0, -1).rotate(this.rotation, horizontalPlane);
        new Vector3f(0, 1, 0).rotate(this.rotation, verticalPlane);
    }

    private float clipToSpace(float distance) {

        // Calculate cam collition
        float f = distance;

        // Example logic:
        // RayResult hit = world.raycast(this.pos, horizontalPlane.negate(), distance);
        // if (hit.type != MISS) return hit.distance;

        return f;
    }

    protected void moveBy(float x, float y, float z) {
        Vector3d offset = new Vector3d(z, y, -x).rotate((Quaterniondc) this.rotation);
        this.pos.add(offset);
    }

    private void updateMatrices(Window window) {
        // 1. Projection Matrix (Perspective)
        float fov = (float) Math.toRadians(this.fov);
        float aspectRatio = (float) window.getWidth() / window.getHeight();
        projectionMatrix.setPerspective(fov, aspectRatio, Z_NEAR, Z_FAR);

        // View Matrix
        viewMatrix.identity()
                .rotate((float) Math.toRadians(pitch), 1, 0, 0)
                .rotate((float) Math.toRadians(yaw + 90), 0, 1, 0)
                .translate((float) -pos.x(), (float) -pos.y(), (float) -pos.z());
    }

    private double lerp(double pct, double start, double end) {
        return start + pct * (end - start);
    }

    public void setPos(float x, float y, float z) { this.pos.set(x, y, z); }
    public Vector3d getPos() {return this.pos;}
    public Matrix4f getViewMatrix() { return viewMatrix; }
    public Matrix4f getProjectionMatrix() { return projectionMatrix; }
}