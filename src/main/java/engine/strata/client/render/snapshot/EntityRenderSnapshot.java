package engine.strata.client.render.snapshot;

import engine.strata.entity.util.EntityKey;
import engine.strata.util.math.Vec3d;
import engine.strata.util.math.Vec3f;

import java.util.UUID;

/**
 * A snapshot representing the visual state of an entity at a specific point in time.
 * Includes standard transformation data and the custom DataTicket map.
 */
public class EntityRenderSnapshot extends RenderSnapshot {
    private final int entityId;
    private final UUID entityUuid;
    private final EntityKey<?> entityKey;
    private final float partialTicks;

    private final Vec3d position = new Vec3d();
    private final Vec3d prevPosition = new Vec3d();
    private final Vec3f rotation = new Vec3f();
    private final Vec3f scale = new Vec3f(1, 1, 1);
    private float prevYaw;
    private float prevPitch;

    public EntityRenderSnapshot(int entityId, UUID entityUuid, EntityKey<?> entityKey, float partialTicks) {
        this.entityId = entityId;
        this.entityUuid = entityUuid;
        this.entityKey = entityKey;
        this.partialTicks = partialTicks;
    }

    // Setters used by the Backend during the "Extraction" phase
    public void setPosition(double x, double y, double z) { this.position.set(x, y, z); }
    public void setRotation(float x, float y, float z) { this.rotation.set(x, y, z); }
    public void setScale(float x, float y, float z) { this.scale.set(x, y, z); }
    public void setPrevPosition(double x, double y, double z) { this.prevPosition.set(x, y, z); }
    public void setPrevPitch(float prevPitch) { this.prevPitch = prevPitch; }
    public void setPrevYaw(float prevYaw) { this.prevYaw = prevYaw; }

    // Getters used by the Frontend during the "Render" phase
    public int getEntityId() { return entityId; }
    public UUID getEntityUuid() { return entityUuid; }
    public EntityKey<?> getEntityKey() { return entityKey; }
    public float getPartialTicks() { return partialTicks; }
    public Vec3d getPrevPosition() { return prevPosition; }
    public float getPrevPitch() { return prevPitch; }
    public float getPrevYaw() { return prevYaw; }
    public Vec3d getPosition() { return position; }
    public Vec3f getRotation() { return rotation; }
    public Vec3f getScale() { return scale; }
}