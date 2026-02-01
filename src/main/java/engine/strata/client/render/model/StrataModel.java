package engine.strata.client.render.model;

import engine.strata.util.Identifier;
import org.joml.Vector3f;
import org.lwjgl.system.windows.INPUT;

import java.util.*;

/**
 * Represents a hierarchical 3D model loaded from .strmodel files.
 * Models use BlockBench-style format with bones, meshes, and multiple textures.
 */
public class StrataModel {
    private final Identifier id;
    private final Bone root;
    private final int textureUVWidth;
    private final int textureUVHeight;
    private final List<String> textureSlots;
    private final Map<String, MeshData> meshes;

    public StrataModel(Identifier id, Bone root, int textureUVWidth, int textureUVHeight, List<String> textureSlots, Map<String, MeshData> meshes) {
        this.id = id;
        this.root = root;
        this.textureUVWidth = textureUVWidth;
        this.textureUVHeight = textureUVHeight;
        this.textureSlots = textureSlots;
        this.meshes = meshes;
    }

    public Bone getRoot() {
        return root;
    }

    public Identifier getId() {
        return id;
    }

    public float uScale() {
        return 1.0f / textureUVWidth;
    }

    public float vScale() {
        return 1.0f / textureUVHeight;
    }

    /**
     * Represents a single bone/part in the model hierarchy.
     */
    public static class Bone {
        private final String name;
        private final Bone parent;
        private final Vector3f pivot;
        private final Vector3f rotation;
        private final List<String> meshIds;
        private final List<Bone> children;

        // Runtime animation properties
        private Vector3f animRotation = new Vector3f(0, 0, 0);
        private Vector3f animTranslation = new Vector3f(0, 0, 0);
        private Vector3f animScale = new Vector3f(1, 1, 1);

        public Bone(String name, Bone parent, Vector3f pivot, Vector3f rotation, List<String> meshIds) {
            this.name = name;
            this.parent = parent;
            this.pivot = pivot;
            this.rotation = rotation;
            this.meshIds = meshIds;
            this.children = new ArrayList<>();
        }

        public void addChild(Bone child) {
            children.add(child);
        }

        public String getName() { return name; }
        public Bone getParent() { return parent; }
        public Vector3f getPivot() { return pivot; }
        public Vector3f getRotation() { return rotation; }
        public List<String> getMeshIds() { return meshIds; }
        public List<Bone> getChildren() { return children; }

        public Vector3f getAnimRotation() { return animRotation; }
        public Vector3f getAnimTranslation() { return animTranslation; }
        public Vector3f getAnimScale() { return animScale; }

        public void setAnimRotation(float x, float y, float z) {
            animRotation.set(x, y, z);
        }

        public void setAnimTranslation(float x, float y, float z) {
            animTranslation.set(x, y, z);
        }

        public void setAnimScale(float x, float y, float z) {
            animScale.set(x, y, z);
        }

        public void resetAnimation() {
            animRotation.set(0, 0, 0);
            animTranslation.set(0, 0, 0);
            animScale.set(1, 1, 1);
        }
    }

    /**
         * Mesh data loaded from the JSON file.
         */
    public record MeshData(
            String type,
            String textureSlot,
            Vector3f origin,
            Vector3f rotation,
            Mesh mesh,      // Var for traditional mesh data
            Cuboid cuboid   // Var for new cuboid data
    ) {

    }

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

    /**
     * Get mesh data by ID.
     */
    public MeshData getMesh(String meshId) {
        return meshes.get(meshId);
    }

    /**
     * Get all bones in the model (for animation purposes).
     */
    public Map<String, Bone> getAllBones() {
        Map<String, Bone> boneMap = new HashMap<>();
        collectBones(root, boneMap);
        return boneMap;
    }

    private void collectBones(Bone bone, Map<String, Bone> map) {
        map.put(bone.getName(), bone);
        for (Bone child : bone.getChildren()) {
            collectBones(child, map);
        }
    }
}