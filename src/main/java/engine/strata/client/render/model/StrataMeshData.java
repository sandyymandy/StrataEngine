package engine.strata.client.render.model;

import org.joml.Vector3f;

import java.util.List;
import java.util.Map;

public record StrataMeshData(
        String type,
        String textureSlot,
        Vector3f origin,
        Vector3f rotation,
        Mesh mesh,      // Var for traditional mesh data
        Cuboid cuboid   // Var for new cuboid data
) {

    /**
     * Represents a cuboid from BlockBench.
     */
    public record Cuboid(Vector3f from, Vector3f to, float inflate, Map<String, CuboidFace> faces) {}

    /**
     * Represents a face in the cuboid.
     */
    public record CuboidFace(float[] uv, int rotation) {}

    /**
     * Represents a mesh from BlockBench.
     */
    public record Mesh(Map<String, Vector3f> vertices, Map<String, Face> faces) {}

    /**
     * Represents a face (triangle or quad) in the mesh.
     */
    public static class Face {
        private final List<String> vertexIds;
        private final Map<String, float[]> uvs;

        public Face(List<String> vertexIds, Map<String, float[]> uvs) {
            this.vertexIds = vertexIds;
            this.uvs = uvs;
        }

        public List<String> getVertexIds() { return vertexIds; }
        public Map<String, float[]> getUvs() { return uvs; }
    }
}
