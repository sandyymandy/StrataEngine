package engine.strata.client.render;

import engine.strata.client.StrataClient;
import engine.strata.client.input.keybind.Keybinds;
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
    private float speed;
    private float normalSpeed = 0.25f;
    private float slowSpeed = 0.05f;
    private boolean firstMouse = true;
    private double lastMouseX, lastMouseY;
    private float sensitivity = 0.15f;
    //Planes for ClipSpace
    private final Vector3f horizontalPlane = new Vector3f(0, 0, -1);
    private final Vector3f verticalPlane = new Vector3f(0, 1, 0);

    private final Matrix4f viewMatrix = new Matrix4f();
    private final Matrix4f projectionMatrix = new Matrix4f();

    private final Frustum frustum;

    public Camera(){
        this.fov = 90;
        this.Z_NEAR = 0.01f;
        this.Z_FAR = 1000.0f;
        this.frustum = new Frustum();
    }

    /**
     * Updates the camera based on an entity (the Player).
     * @param partialTick The progress between game ticks (0.0 to 1.0)
     */
    public void update(Entity focusedEntity, Window window, float partialTick, boolean thirdPerson) {
        this.focusedEntity = focusedEntity;

        double x = lerp(partialTick, focusedEntity.prevX, focusedEntity.getPosition().getX());
        double y = lerp(partialTick, focusedEntity.prevY, focusedEntity.getPosition().getY()) + focusedEntity.getEyeHeight();
        double z = lerp(partialTick, focusedEntity.prevZ, focusedEntity.getPosition().getZ());

        this.setRotation(focusedEntity.getYaw(), focusedEntity.getPitch());
        this.setPos((float)x, (float)y, (float)z);

        if (thirdPerson) {
            // Move camera backwards, but check for wall collisions
            float distance = 4.0f;
            float clippedDist = clipToSpace(distance);
            this.moveBy(-clippedDist, 0, 0);
        }

        updateMatrices(window);
        frustum.update(projectionMatrix, viewMatrix);

        // Store current as previous for next frame
        this.prevYaw = focusedEntity.getYaw();
        this.prevPitch = focusedEntity.getPitch();
        this.prevPos.set(this.pos);
        handleMouse(focusedEntity);
    }

    public void tick() {
        updateMovement(focusedEntity);
    }

    public void handleMouse(Entity entity) {
        if(entity == null) return;
        Window window = StrataClient.getInstance().getWindow();
        double x = window.getMouseX();
        double y = window.getMouseY();

        if (firstMouse) {
            lastMouseX = x;
            lastMouseY = y;
            firstMouse = false;
        }

        float offsetX = (float) (x - lastMouseX);
        float offsetY = (float) (lastMouseY - y);
        lastMouseX = x;
        lastMouseY = y;

        entity.setYaw(entity.getYaw() + offsetX * sensitivity);
        entity.setPitch(entity.getPitch() - offsetY * sensitivity); // Adjusted sign for standard feel

        if (entity.getPitch() > 89.0f) entity.setPitch(89.0f);
        if (entity.getPitch() < -89.0f) entity.setPitch(-89.0f);
    }

    public void updateMovement(Entity entity) {
        if(entity == null) return;
        // Calculate direction based on YAW
        float radYaw = (float) Math.toRadians(entity.getYaw());
        float sin = (float) Math.sin(radYaw);
        float cos = (float) Math.cos(radYaw);

        speed = normalSpeed;

        if(Keybinds.SLOW.isActive()) {
            speed = slowSpeed;
        }

        if (Keybinds.RIGHT.isActive()) {
            entity.getPosition().setX(entity.getPosition().getX() - sin * speed);
            entity.getPosition().setZ(entity.getPosition().getZ() + cos * speed);
        }
        if (Keybinds.LEFT.isActive()) {
            entity.getPosition().setX(entity.getPosition().getX() + sin * speed);
            entity.getPosition().setZ(entity.getPosition().getZ() - cos * speed);
        }
        if (Keybinds.FORWARDS.isActive()) {
            entity.getPosition().setX(entity.getPosition().getX() + cos * speed);
            entity.getPosition().setZ(entity.getPosition().getZ() + sin * speed);
        }
        if (Keybinds.BACKWARDS.isActive()) {
            entity.getPosition().setX(entity.getPosition().getX() - cos * speed);
            entity.getPosition().setZ(entity.getPosition().getZ() - sin * speed);
        }
        if (Keybinds.UP.isActive()) {
            entity.getPosition().setY(entity.getPosition().getY() + speed);
        }
        if (Keybinds.DOWN.isActive()) {
            entity.getPosition().setY(entity.getPosition().getY() - speed);
        }
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

    // ==================== Frustum Culling Methods ====================

    /**
     * Tests if a point is visible in the camera's frustum.
     */
    public boolean isPointVisible(float x, float y, float z) {
        return frustum.testPoint(x, y, z);
    }

    /**
     * Tests if a sphere is visible in the camera's frustum.
     */
    public boolean isSphereVisible(float x, float y, float z, float radius) {
        return frustum.testSphere(x, y, z, radius);
    }

    /**
     * Tests if an AABB is visible in the camera's frustum.
     */
    public boolean isAabbVisible(float minX, float minY, float minZ,
                                 float maxX, float maxY, float maxZ) {
        return frustum.testAabb(minX, minY, minZ, maxX, maxY, maxZ);
    }

    /**
     * Tests if a cube is visible.
     * Useful for chunks (16x16x16 cubes).
     */
    public boolean isCubeVisible(float centerX, float centerY, float centerZ, float size) {
        return frustum.testCube(centerX, centerY, centerZ, size);
    }

    /**
     * Tests visibility with distance culling.
     * Combines frustum culling with max render distance.
     */
    public boolean isSphereVisibleWithDistance(float x, float y, float z, float radius,
                                               float maxDistance) {
        return frustum.testSphereWithDistance(
                x, y, z, radius,
                (float) pos.x, (float) pos.y, (float) pos.z,
                maxDistance
        );
    }

    /**
     * Tests if an AABB is visible with distance culling.
     */
    public boolean isAabbVisibleWithDistance(float minX, float minY, float minZ,
                                             float maxX, float maxY, float maxZ,
                                             float maxDistance) {
        return frustum.testAabbWithDistance(
                minX, minY, minZ, maxX, maxY, maxZ,
                (float) pos.x, (float) pos.y, (float) pos.z,
                maxDistance
        );
    }

    public void setPos(float x, float y, float z) {
        this.pos.set(x, y, z);
    }

    public Vector3d getPos() {
        return this.pos;
    }

    public Matrix4f getViewMatrix() {
        return viewMatrix;
    }

    public Matrix4f getProjectionMatrix() {
        return projectionMatrix;
    }

    public Frustum getFrustum() {
        return frustum;
    }

    public float getFov() {
        return fov;
    }

    public void setFov(float fov) {
        this.fov = fov;
    }

    public float getZNear() {
        return Z_NEAR;
    }

    public float getZFar() {
        return Z_FAR;
    }

    public float getPitch() {
        return pitch;
    }

    public float getYaw() {
        return yaw;
    }

    public Entity getFocusedEntity() {
        return focusedEntity;
    }
}