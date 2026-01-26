package engine.helios;

import org.joml.Matrix4f;
import org.joml.Vector4f;
import org.lwjgl.BufferUtils;
import java.nio.FloatBuffer;

public class BufferBuilder {
    private final FloatBuffer buffer;
    private int vertexCount = 0;
    private VertexFormat format;
    private boolean building = false;
    // Pre-allocate to avoid GC pressure during rendering
    private final Vector4f tempPos = new Vector4f();

    public BufferBuilder(int capacityInBytes) {
        this.buffer = BufferUtils.createFloatBuffer(capacityInBytes / 4);
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

    public void next() {
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
}