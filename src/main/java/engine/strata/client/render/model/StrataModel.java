package engine.strata.client.render.model;

import engine.strata.util.Identifier;

import java.util.*;

/**
 * Represents a hierarchical 3D model loaded from .strmodel files.
 * Models use BlockBench-style format with bones, meshes, and multiple textures.
 */
public class StrataModel {
    private final Identifier id;
    private final StrataBone root;
    private final int textureUVWidth;
    private final int textureUVHeight;
    private final List<String> textureSlots;
    private final Map<String, StrataMeshData> meshes;

    public StrataModel(Identifier id, StrataBone root, int textureUVWidth, int textureUVHeight, List<String> textureSlots, Map<String, StrataMeshData> meshes) {
        this.id = id;
        this.root = root;
        this.textureUVWidth = textureUVWidth;
        this.textureUVHeight = textureUVHeight;
        this.textureSlots = textureSlots;
        this.meshes = meshes;
    }

    public StrataBone getRoot() {
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
         * Mesh data loaded from the JSON file.
         */


    /**
     * Get mesh data by ID.
     */
    public StrataMeshData getMesh(String meshId) {
        return meshes.get(meshId);
    }

    /**
     * Get all bones in the model (for animation purposes).
     */
    public Map<String, StrataBone> getAllBones() {
        Map<String, StrataBone> boneMap = new HashMap<>();
        collectBones(root, boneMap);
        return boneMap;
    }

    private void collectBones(StrataBone bone, Map<String, StrataBone> map) {
        map.put(bone.getName(), bone);
        for (StrataBone child : bone.getChildren()) {
            collectBones(child, map);
        }
    }
}