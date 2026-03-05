package engine.strata.world;

import engine.strata.util.Transform;
import engine.strata.util.Vec3d;
import engine.strata.util.Vec3f;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import static org.joml.Math.toRadians;


/**
 * The base class for any physical object in the 3D world.
 * Handles Position, Rotation, Scale, and Matrix calculations.
 * Now uses Quaternion rotation for smooth interpolation and no gimbal lock.
 */
public abstract class SpatialObject {

    // Transform
    protected final Transform transform = new Transform();

    // Caching for performance (Calculate matrix only when needed)
    protected final Matrix4f modelMatrix = new Matrix4f();
    private boolean isDirty = true; // "Dirty" means data changed and matrix needs update

    // Physics / Collision (Simple AABB)
    protected final Vec3f boundingBoxSize = new Vec3f(1, 1, 1);

    public SpatialObject() {
        // Default constructor
    }

    /**
     * Called every game tick. Override for logic.
     */
    public void tick() {
    }

    // ==========================================
    //               TRANSFORMS
    // ==========================================

    public void setPosition(double x, double y, double z) {
        this.getPosition().set(x, y, z);
        this.isDirty = true;
    }

    public void move(float dx, float dy, float dz) {
        this.getPosition().add(dx, dy, dz);
        this.isDirty = true;
    }

    /**
     * Sets rotation using a Quaternion.
     */
    public void setRotation(Quaternionf quaternion) {
        this.getRotation().set(quaternion);
        this.isDirty = true;
    }

    /**
     * Sets rotation from Euler angles in Radians.
     * Uses XYZ rotation order.
     */
    public void setRotation(float x, float y, float z) {
        this.getRotation().rotationXYZ(x, y, z);
        this.isDirty = true;
    }

    /**
     * Sets rotation from Euler angles in Degrees (helper).
     */
    public void setRotationDegrees(float x, float y, float z) {
        this.getRotation().rotationXYZ(toRadians(x), toRadians(y), toRadians(z));
        this.isDirty = true;
    }

    /**
     * Rotates by Euler angles in radians (adds to current rotation).
     */
    public void rotate(float x, float y, float z) {
        this.getRotation().rotateXYZ(x, y, z);
        this.isDirty = true;
    }

    /**
     * Rotates by Euler angles in degrees (adds to current rotation).
     */
    public void rotateDegrees(float x, float y, float z) {
        this.getRotation().rotateXYZ(toRadians(x), toRadians(y), toRadians(z));
        this.isDirty = true;
    }

    /**
     * Gets the rotation as Euler angles (pitch, yaw, roll) in radians.
     * Extracted from the current quaternion.
     */
    public Vec3f getEulerAngles() {
        Vector3f euler = this.getRotation().getEulerAnglesXYZ(new Vector3f());
        return new Vec3f(euler.x, euler.y, euler.z);
    }

    public void setScale(float s) {
        this.getScale().set(s, s, s);
        this.isDirty = true;
    }

    public void setScale(float x, float y, float z) {
        this.getScale().set(x, y, z);
        this.isDirty = true;
    }

    // ==========================================
    //           MATRIX CALCULATION
    // ==========================================

    /**
     * Returns the Model Matrix for rendering.
     * Automatically recalculates if the object has moved/rotated.
     */
    public Matrix4f getModelMatrix() {
        if (isDirty) {
            recalculateMatrix();
        }
        return modelMatrix;
    }

    protected void recalculateMatrix() {
        modelMatrix.identity()
                .translate(this.getPosition().toVector3f())
                .rotate(this.getRotation()) // Quaternion rotation
                .scale(this.getScale().toVector3f());

        isDirty = false;
    }

    // ==========================================
    //            HELPER VECTORS
    // ==========================================

    /**
     * Gets the forward direction vector of this object.
     * Useful for moving "forward" regardless of rotation.
     */
    public Vector3f getForwardVector() {
        // Transform the forward vector (0, 0, -1) by our rotation
        return this.getRotation().transform(new Vector3f(0, 0, -1));
    }

    /**
     * Gets the right direction vector of this object.
     */
    public Vector3f getRightVector() {
        return this.getRotation().transform(new Vector3f(1, 0, 0));
    }

    /**
     * Gets the up direction vector of this object.
     */
    public Vector3f getUpVector() {
        return this.getRotation().transform(new Vector3f(0, 1, 0));
    }

    // ==========================================
    //               GETTERS
    // ==========================================

    public Vec3d getPosition() { return transform.getPosition(); }
    public Quaternionf getRotation() { return transform.getRotation(); }
    public Vec3f getScale() { return transform.getScale(); }
}