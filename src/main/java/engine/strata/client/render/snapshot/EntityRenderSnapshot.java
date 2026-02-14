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
    private int entityId;
    private UUID entityUuid;
    private EntityKey<?> entityKey;
    private float partialTicks;

    private final Vec3d position = new Vec3d();
    private final Vec3d prevPosition = new Vec3d();
    private final Vec3f rotation = new Vec3f();
    private final Vec3f prevRotation = new Vec3f();
    private final Vec3f scale = new Vec3f(1, 1, 1);
    private float headYaw;
    private float headPitch;
    private float prevHeadYaw;
    private float prevHeadPitch;

    // Setters used by the Backend during the "Extraction" phase
    public void setEntityId(int entityId) { this.entityId = entityId; }
    public void setKey(EntityKey<?> key) { this.entityKey = key; }
    public void setPartialTicks(float partialTicks) { this.partialTicks = partialTicks; }
    public void setUuid(UUID uuid) { this.entityUuid = uuid; }
    public void setPosition(double x, double y, double z) { this.position.set(x, y, z); }
    public void setRotation(float x, float y, float z) { this.rotation.set(x, y, z); }
    public void setScale(float x, float y, float z) { this.scale.set(x, y, z); }
    public void setPrevPosition(double x, double y, double z) { this.prevPosition.set(x, y, z); }
    public void setPrevRotation(float x, float y, float z) { this.prevRotation.set(x, y, z); }
    public void setPrevHeadPitch(float prevPitch) { this.prevHeadPitch = prevPitch; }
    public void setPrevHeadYaw(float prevYaw) { this.prevHeadYaw = prevYaw; }
    public void setHeadPitch(float pitch) { this.headPitch = pitch; }
    public void setHeadYaw(float yaw) { this.headYaw = yaw; }

    // Getters used by the Frontend during the "Render" phase
    public int getEntityId() { return entityId; }
    public UUID getEntityUuid() { return entityUuid; }
    public EntityKey<?> getEntityKey() { return entityKey; }
    public float getPartialTicks() { return partialTicks; }
    public Vec3d getPrevPosition() { return prevPosition; }
    public Vec3f getPrevRotation() { return prevRotation; }
    public float getPrevHeadPitch() { return prevHeadPitch; }
    public float getHeadYaw() { return headYaw; }
    public float getHeadPitch() { return headPitch; }
    public float getPrevHeadYaw() { return prevHeadYaw; }
    public Vec3d getPosition() { return position; }
    public Vec3f getRotation() { return rotation; }
    public Vec3f getScale() { return scale; }
}