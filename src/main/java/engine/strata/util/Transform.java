package engine.strata.util;

import org.joml.Quaternionf;

public class Transform {
    protected final Vec3d position;
    protected final Quaternionf rotation;
    protected final Vec3f scale;

    public Transform() {
        this.position = new Vec3d();
        this.rotation = new Quaternionf(); // Identity quaternion
        this.scale = new Vec3f(1, 1, 1);
    }

    public Transform(Vec3d position, Quaternionf rotation, Vec3f scale) {
        this.position = position;
        this.rotation = rotation;
        this.scale = scale;
    }

    public Transform(Vec3d position) {
        this.position = position;
        this.rotation = new Quaternionf(); // Identity quaternion
        this.scale = new Vec3f(1, 1, 1);
    }

    public Vec3d  getPosition() { return position; }
    public Quaternionf getRotation() { return rotation; }
    public Vec3f getScale() { return scale; }
}