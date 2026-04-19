package engine.strata.client.frontend.ui;

import org.lwjgl.BufferUtils;

import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class UiBatch {
    // POSITION_TEXTURE_COLOR => 3 + 2 + 4 floats
    public static final int FLOATS_PER_VERTEX = 9;

    private float[] data = new float[4096];
    private int size = 0;

    private FloatBuffer direct = BufferUtils.createFloatBuffer(4096);

    public record Command(int textureId, int firstVertex, int vertexCount) {}

    private final List<Command> commands = new ArrayList<>();
    private boolean hasOpenCommand = false;
    private int openTextureId = -1;
    private int openFirstVertex = 0;

    public void clear() {
        size = 0;
        commands.clear();
        hasOpenCommand = false;
        openTextureId = -1;
        openFirstVertex = 0;
    }

    public int vertexCount() {
        return size / FLOATS_PER_VERTEX;
    }

    public void setTexture(int textureId) {
        if (!hasOpenCommand) {
            hasOpenCommand = true;
            openTextureId = textureId;
            openFirstVertex = vertexCount();
            return;
        }

        if (openTextureId == textureId) return;

        closeOpenCommand();
        openTextureId = textureId;
        openFirstVertex = vertexCount();
        hasOpenCommand = true;
    }

    public void finish() {
        if (!hasOpenCommand) return;
        closeOpenCommand();
        hasOpenCommand = false;
    }

    public List<Command> commands() {
        finish();
        return Collections.unmodifiableList(commands);
    }

    private void closeOpenCommand() {
        int first = openFirstVertex;
        int count = vertexCount() - first;
        if (count > 0) {
            commands.add(new Command(openTextureId, first, count));
        }
    }

    private void ensure(int additionalFloats) {
        int needed = size + additionalFloats;
        if (needed <= data.length) return;
        int newCap = data.length;
        while (newCap < needed) newCap *= 2;
        float[] bigger = new float[newCap];
        System.arraycopy(data, 0, bigger, 0, size);
        data = bigger;
    }

    public void vertex(float x, float y, float z,
                       float u, float v,
                       float r, float g, float b, float a) {
        ensure(FLOATS_PER_VERTEX);
        data[size++] = x;
        data[size++] = y;
        data[size++] = z;
        data[size++] = u;
        data[size++] = v;
        data[size++] = r;
        data[size++] = g;
        data[size++] = b;
        data[size++] = a;
    }

    public void quad(int textureId, float x, float y, float w, float h,
                     float u1, float v1, float u2, float v2,
                     float r, float g, float b, float a) {
        setTexture(textureId);
        // Two triangles: (x,y) top-left, y grows downward
        float x2 = x + w;
        float y2 = y + h;
        vertex(x,  y,  0, u1, v1, r, g, b, a);
        vertex(x2, y,  0, u2, v1, r, g, b, a);
        vertex(x2, y2, 0, u2, v2, r, g, b, a);
        vertex(x,  y,  0, u1, v1, r, g, b, a);
        vertex(x2, y2, 0, u2, v2, r, g, b, a);
        vertex(x,  y2, 0, u1, v2, r, g, b, a);
    }

    public FloatBuffer toFloatBuffer() {
        int floats = size;
        if (direct.capacity() < floats) {
            direct = BufferUtils.createFloatBuffer(floats);
        } else {
            direct.clear();
        }
        direct.put(data, 0, floats);
        direct.flip();
        return direct;
    }
}
