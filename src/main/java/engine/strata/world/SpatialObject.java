package engine.strata.world;

import engine.strata.util.math.Vec3d;
import engine.strata.util.math.Vec3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import static org.joml.Math.toRadians;


/**
 * The base class for any physical object in the 3D world.
 * Handles Position, Rotation, Scale, and Matrix calculations.
 */
public abstract class SpatialObject {

    // Transform Data
    protected final Vec3d position = new Vec3d();
    protected final Vec3f rotation = new Vec3f(); // Euler angles in Radians (Pitch, Yaw, Roll)
    protected final Vec3f scale = new Vec3f(1, 1, 1);

    // Caching for performance (Calculate matrix only when needed)
    protected final Matrix4f modelMatrix = new Matrix4f();
    private boolean isDirty = true; // "Dirty" means data changed and matrix needs update

    // Physics / Collision (Simple AABB)
    // You can expand this into a full class later
    protected final Vec3f boundingBoxSize = new Vec3f(1, 1, 1);

    public SpatialObject() {
        // Default constructor
    }

    /**
     * Called every game tick. Override for logic.
     */
    public void tick() {
        // Optional: Physics or logic updates go here
    }

    // ==========================================
    //               TRANSFORMS
    // ==========================================

    public void setPosition(double x, double y, double z) {
        this.position.set(x, y, z);
        this.isDirty = true;
    }

    public void move(float dx, float dy, float dz) {
        this.position.add(dx, dy, dz);
        this.isDirty = true;
    }

    /**
     * Sets rotation in Radians.
     */
    public void setRotation(float x, float y, float z) {
        this.rotation.set(x, y, z);
        this.isDirty = true;
    }

    /**
     * Sets rotation in Degrees (helper).
     */
    public void setRotationDegrees(float x, float y, float z) {
        this.rotation.set(toRadians(x), toRadians(y), toRadians(z));
        this.isDirty = true;
    }

    public void setScale(float s) {
        this.scale.set(s, s, s);
        this.isDirty = true;
    }

    public void setScale(float x, float y, float z) {
        this.scale.set(x, y, z);
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
                .translate(position.toVector3f())
                .rotateXYZ(rotation.getX(), rotation.getY(), rotation.getZ()) // Standard Euler rotation
                .scale(scale.toVector3f());

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
        // Calculate forward based on Yaw (Y) and Pitch (X)
        float x = (float) (Math.sin(rotation.getY()) * Math.cos(rotation.getX()));
        float y = (float) -Math.sin(rotation.getX());
        float z = (float) (Math.cos(rotation.getY()) * Math.cos(rotation.getX()));
        return new Vector3f(x, y, z).normalize();
    }

    // ==========================================
    //               GETTERS
    // ==========================================

    public Vec3d getPosition() { return position; }
    public Vec3f getRotation() { return rotation; }
    public Vec3f getScale() { return scale; }
}