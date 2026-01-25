package engine.strata.client.render.model;

import org.joml.Vector3f;
import java.util.ArrayList;
import java.util.List;

public class StrataModel {
    private final Bone root;

    public StrataModel(Bone root) { this.root = root; }
    public Bone getRoot() { return root; }

    public static class Bone {
        private final String name;
        private final Vector3f pivot;
        private final List<Bone> children = new ArrayList<>();
        private final List<Mesh> meshes = new ArrayList<>();

        public Bone(String name, Vector3f pivot) {
            this.name = name;
            this.pivot = pivot;
        }

        public void addChild(Bone child) { children.add(child); }
        public void addMesh(Mesh mesh) { meshes.add(mesh); }

        public List<Bone> getChildren() { return children; }
        public List<Mesh> getMeshes() { return meshes; }
        public Vector3f getPivot() { return pivot; }
    }

    public static record Mesh(int vao, int vertexCount) {}
}