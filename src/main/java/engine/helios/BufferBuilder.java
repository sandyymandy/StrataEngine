package engine.helios;

import org.lwjgl.BufferUtils;
import java.nio.FloatBuffer;

public class BufferBuilder {
    private final FloatBuffer buffer;
    private int vertexCount = 0;
    private VertexFormat format;

    public BufferBuilder(int capacityInBytes) {
        this.buffer = BufferUtils.createFloatBuffer(capacityInBytes / 4);
    }

    public void begin(VertexFormat format) {
        this.format = format;
        this.vertexCount = 0;
        this.buffer.clear();
    }

    public BufferBuilder pos(float x, float y, float z) {
        buffer.put(x).put(y).put(z);
        return this;
    }

    public BufferBuilder tex(float u, float v) {
        buffer.put(u).put(v);
        return this;
    }

    public BufferBuilder color(float r, float g, float b, float a) {
        buffer.put(r).put(g).put(b).put(a);
        return this;
    }

    public void next() {
        this.vertexCount++;
    }

    public FloatBuffer getBuffer() {
        buffer.flip();
        return buffer;
    }

    public int getVertexCount() { return vertexCount; }
    public VertexFormat getFormat() { return format; }
}