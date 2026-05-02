package engine.strata.util;

import org.joml.Quaternionf;

public class Transform {
    // Define the IDENTITY constant here
    public static final Transform IDENTITY = new Transform();

    protected final Vec3d position;
    protected final Quaternionf rotation;
    protected final Vec3f scale;

    public Transform() {
        this.position = new Vec3d(0, 0, 0);
        this.rotation = new Quaternionf(); // Identity quaternion (0,0,0,1)
        this.scale = new Vec3f(1, 1, 1);
    }

    public Transform(Vec3d position, Quaternionf rotation, Vec3f scale) {
        this.position = position;
        this.rotation = rotation;
        this.scale = scale;
    }

    public Transform(Vec3d position) {
        this(position, new Quaternionf(), new Vec3f(1, 1, 1));
    }

    /**
     * Helper to create a transform with just a translation offset.
     * Useful for avoiding z-fighting or bone offsets.
     */
    public static Transform offset(double x, double y, double z) {
        return new Transform(new Vec3d(x, y, z));
    }

    public Vec3d getPosition() { return position; }
    public Quaternionf getRotation() { return rotation; }
    public Vec3f getScale() { return scale; }
}