package engine.helios;

import org.joml.Matrix4f;
import org.joml.Vector4f;
import org.lwjgl.BufferUtils;
import java.nio.FloatBuffer;

public class BufferBuilder {
    private final FloatBuffer buffer;
    private final int capacityInBytes;
    private int vertexCount = 0;
    private VertexFormat format;
    private boolean building = false;

    // Pre-allocate to avoid GC pressure during rendering
    private final Vector4f tempPos = new Vector4f();

    public BufferBuilder(int capacityInBytes) {
        this.capacityInBytes = capacityInBytes;
        this.buffer = BufferUtils.createFloatBuffer(capacityInBytes / 4); // 4 bytes per float
    }

    public void begin(VertexFormat format) {
        if (building) {
            throw new IllegalStateException("Already building!");
        }
        this.format = format;
        this.vertexCount = 0;
        this.buffer.clear();
        this.building = true;
    }

    public BufferBuilder vertex(Matrix4f matrix, float x, float y, float z) {
        tempPos.set(x, y, z, 1.0f).mul(matrix);
        return pos(tempPos.x, tempPos.y, tempPos.z);
    }

    public BufferBuilder pos(float x, float y, float z) {
        if (!building) {
            throw new IllegalStateException("Not building!");
        }
        buffer.put(x).put(y).put(z);
        return this;
    }

    public BufferBuilder tex(float u, float v) {
        if (!building) {
            throw new IllegalStateException("Not building!");
        }
        buffer.put(u).put(v);
        return this;
    }

    public BufferBuilder color(float r, float g, float b, float a) {
        if (!building) {
            throw new IllegalStateException("Not building!");
        }
        buffer.put(r).put(g).put(b).put(a);
        return this;
    }

    public void end() {
        if (!building) {
            throw new IllegalStateException("Not building!");
        }
        this.vertexCount++;
    }

    public FloatBuffer getBuffer() {
        buffer.flip();
        building = false;
        return buffer;
    }

    public int getVertexCount() {
        return vertexCount;
    }

    public VertexFormat getFormat() {
        return format;
    }

    public boolean isBuilding() {
        return building;
    }

    /**
     * Gets the buffer capacity in bytes.
     * @return capacity in bytes
     */
    public int getCapacity() {
        return capacityInBytes;
    }

    /**
     * Gets the current position in the buffer (in bytes).
     * This represents how much data has been written.
     * @return current position in bytes
     */
    public int getPosition() {
        return buffer.position() * 4; // Convert floats to bytes
    }

    /**
     * Gets the remaining capacity in the buffer (in bytes).
     * @return remaining bytes available
     */
    public int getRemaining() {
        return capacityInBytes - getPosition();
    }

    /**
     * Checks if the buffer has enough space for the specified number of bytes.
     * @param bytes number of bytes to check
     * @return true if there's enough space
     */
    public boolean hasSpace(int bytes) {
        return getRemaining() >= bytes;
    }

    /**
     * Gets the buffer usage as a percentage (0.0 to 1.0).
     * @return usage percentage
     */
    public float getUsage() {
        return (float) getPosition() / capacityInBytes;
    }

    /**
     * Resets the buffer for reuse without reallocating.
     * Called after flushing to GPU.
     */
    public void reset() {
        this.buffer.clear();
        this.vertexCount = 0;
        this.building = false;
    }
}