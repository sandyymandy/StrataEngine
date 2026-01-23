package engine.strata.client.render.entity;

import engine.strata.client.render.model.RawModel;
import org.joml.Vector3f;

public class RenderEntity {
    private RawModel model;
    private Vector3f position;
    private float rotX, rotY, rotZ;
    private float scale;

    public RenderEntity(RawModel model, Vector3f position, float rotX, float rotY, float rotZ, float scale) {
        this.model = model;
        this.position = position;
        this.rotX = rotX;
        this.rotY = rotY;
        this.rotZ = rotZ;
        this.scale = scale;
    }

    // Move helper
    public void increasePosition(float dx, float dy, float dz) {
        this.position.x += dx;
        this.position.y += dy;
        this.position.z += dz;
    }

    public void increaseRotation(float dx, float dy, float dz) {
        this.rotX += dx;
        this.rotY += dy;
        this.rotZ += dz;
    }

    public RawModel getModel() { return model; }
    public Vector3f getPosition() { return position; }
    public float getRotX() { return rotX; }
    public float getRotY() { return rotY; }
    public float getRotZ() { return rotZ; }
    public float getScale() { return scale; }
}