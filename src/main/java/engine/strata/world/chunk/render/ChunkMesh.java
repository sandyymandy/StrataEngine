package engine.strata.world.chunk.render;

import org.lwjgl.opengl.GL30;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;

/**
 * Represents a compiled chunk mesh ready for rendering.
 * Manages GPU buffers (VAO, VBO, IBO) for efficient rendering.
 */
public class ChunkMesh {
    private final ChunkMeshBuilder.ChunkMesh meshData;

    // OpenGL buffer IDs
    private int vaoId = -1;
    private int vboId = -1;
    private int iboId = -1;

    private boolean uploaded = false;

    public ChunkMesh(ChunkMeshBuilder.ChunkMesh meshData) {
        this.meshData = meshData;
    }

    /**
     * Uploads the mesh data to GPU if not already uploaded.
     */
    public void uploadIfNeeded() {
        if (!uploaded && !meshData.isEmpty()) {
            upload();
            uploaded = true;
        }
    }

    /**
     * Uploads mesh data to GPU buffers.
     */
    private void upload() {
        // Generate VAO
        vaoId = GL30.glGenVertexArrays();
        GL30.glBindVertexArray(vaoId);

        // Generate and bind VBO
        vboId = GL30.glGenBuffers();
        GL30.glBindBuffer(GL30.GL_ARRAY_BUFFER, vboId);

        // Create vertex buffer
        FloatBuffer vertexBuffer = createVertexBuffer();
        GL30.glBufferData(GL30.GL_ARRAY_BUFFER, vertexBuffer, GL30.GL_STATIC_DRAW);

        // Position attribute (location = 0, 3 floats)
        GL30.glVertexAttribPointer(0, 3, GL30.GL_FLOAT, false, 6 * Float.BYTES, 0);
        GL30.glEnableVertexAttribArray(0);

        // UV attribute (location = 1, 2 floats)
        GL30.glVertexAttribPointer(1, 2, GL30.GL_FLOAT, false, 6 * Float.BYTES, 3 * Float.BYTES);
        GL30.glEnableVertexAttribArray(1);

        // Brightness attribute (location = 2, 1 float)
        GL30.glVertexAttribPointer(2, 1, GL30.GL_FLOAT, false, 6 * Float.BYTES, 5 * Float.BYTES);
        GL30.glEnableVertexAttribArray(2);

        // Generate and bind IBO
        iboId = GL30.glGenBuffers();
        GL30.glBindBuffer(GL30.GL_ELEMENT_ARRAY_BUFFER, iboId);

        // Create index buffer
        IntBuffer indexBuffer = createIndexBuffer();
        GL30.glBufferData(GL30.GL_ELEMENT_ARRAY_BUFFER, indexBuffer, GL30.GL_STATIC_DRAW);

        // Unbind
        GL30.glBindVertexArray(0);
        GL30.glBindBuffer(GL30.GL_ARRAY_BUFFER, 0);
        GL30.glBindBuffer(GL30.GL_ELEMENT_ARRAY_BUFFER, 0);
    }

    /**
     * Creates a float buffer from vertex data.
     */
    private FloatBuffer createVertexBuffer() {
        ChunkMeshBuilder.ChunkVertex[] vertices = meshData.vertices();

        // Each vertex has: x, y, z, u, v, brightness = 6 floats
        FloatBuffer buffer = ByteBuffer.allocateDirect(vertices.length * 6 * Float.BYTES)
                .order(ByteOrder.nativeOrder())
                .asFloatBuffer();

        for (ChunkMeshBuilder.ChunkVertex vertex : vertices) {
            buffer.put(vertex.x());
            buffer.put(vertex.y());
            buffer.put(vertex.z());
            buffer.put(vertex.u());
            buffer.put(vertex.v());
            buffer.put(vertex.brightness());
        }

        buffer.flip();
        return buffer;
    }

    /**
     * Creates an int buffer from index data.
     */
    private IntBuffer createIndexBuffer() {
        int[] indices = meshData.indices();

        IntBuffer buffer = ByteBuffer.allocateDirect(indices.length * Integer.BYTES)
                .order(ByteOrder.nativeOrder())
                .asIntBuffer();

        buffer.put(indices);
        buffer.flip();

        return buffer;
    }

    /**
     * Renders the chunk mesh.
     */
    public void render() {
        if (!uploaded || vaoId == -1 || meshData.isEmpty()) {
            return;
        }

        GL30.glBindVertexArray(vaoId);
        GL30.glDrawElements(GL30.GL_TRIANGLES, meshData.indices().length, GL30.GL_UNSIGNED_INT, 0);
        GL30.glBindVertexArray(0);
    }

    /**
     * Deletes GPU resources.
     */
    public void delete() {
        if (vaoId != -1) {
            GL30.glDeleteVertexArrays(vaoId);
            vaoId = -1;
        }
        if (vboId != -1) {
            GL30.glDeleteBuffers(vboId);
            vboId = -1;
        }
        if (iboId != -1) {
            GL30.glDeleteBuffers(iboId);
            iboId = -1;
        }
        uploaded = false;
    }

    public boolean isEmpty() {
        return meshData.isEmpty();
    }

    public boolean isUploaded() {
        return uploaded;
    }
}