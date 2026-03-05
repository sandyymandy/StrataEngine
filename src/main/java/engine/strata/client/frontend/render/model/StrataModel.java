package engine.strata.client.frontend.render.model;

import engine.strata.util.Identifier;

import java.util.*;

/**
 * Represents a hierarchical 3D model loaded from .strmodel files.
 * Models use BlockBench-style format with bones, meshes, and multiple textures.
 */
public class StrataModel {
    private final Identifier id;
    private final StrataBone root;
    private final Map<String, TextureInfo> textures; // Keyed by texture name
    private final Map<String, StrataMeshData> meshes;

    public StrataModel(Identifier id, StrataBone root, Map<String, TextureInfo> textures, Map<String, StrataMeshData> meshes) {
        this.id = id;
        this.root = root;
        this.textures = textures;
        this.meshes = meshes;
    }

    public StrataBone getRoot() {
        return root;
    }

    public Identifier getId() {
        return id;
    }

    /**
     * Get a bone by ID.
     */
    public StrataBone getBone(String boneId) {
        Map<String, StrataBone> boneMap = new HashMap<>();
        collectBones(root, boneMap);
        return boneMap.get(boneId);
    }

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

    public static class TextureInfo {
        private final int uvWidth;
        private final int uvHeight;

        public TextureInfo(int uvWidth, int uvHeight) {
            this.uvWidth = uvWidth;
            this.uvHeight = uvHeight;
        }

        public float uScale() {
            return 1.0f / uvWidth;
        }

        public float vScale() {
            return 1.0f / uvHeight;
        }
    }

    // Remove global uScale/vScale methods and use per-texture lookups
    public TextureInfo getTextureInfo(String name) {
        return textures.getOrDefault(name, new TextureInfo(16, 16));
    }
}