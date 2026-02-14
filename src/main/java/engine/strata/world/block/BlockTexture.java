package engine.strata.world.block;

import engine.strata.util.Identifier;

import java.util.HashMap;
import java.util.Map;

/**
 * Defines texture configurations for a block's faces.
 * Supports multiple modes:
 * - Single texture for all faces
 * - Different textures for top/sides/bottom
 * - Individual texture per face
 * - Texture identifiers (for dynamic atlas) or indices (for pre-built atlas)
 */
public class BlockTexture {

    // Texture configuration per face
    private final Map<Face, TextureReference> faceTextures;

    /**
     * Creates a block texture with the same texture on all sides (using atlas index).
     */
    public BlockTexture(int textureIndex) {
        this.faceTextures = new HashMap<>();
        TextureReference ref = new TextureReference(textureIndex);
        for (Face face : Face.values()) {
            faceTextures.put(face, ref);
        }
    }

    /**
     * Creates a block texture with the same texture identifier on all sides.
     */
    public BlockTexture(Identifier textureId) {
        this.faceTextures = new HashMap<>();
        TextureReference ref = new TextureReference(textureId);
        for (Face face : Face.values()) {
            faceTextures.put(face, ref);
        }
    }

    /**
     * Creates a block texture with different textures for top, sides, and bottom (using indices).
     */
    public BlockTexture(int topTexture, int sideTexture, int bottomTexture) {
        this.faceTextures = new HashMap<>();
        faceTextures.put(Face.TOP, new TextureReference(topTexture));
        faceTextures.put(Face.BOTTOM, new TextureReference(bottomTexture));
        faceTextures.put(Face.NORTH, new TextureReference(sideTexture));
        faceTextures.put(Face.SOUTH, new TextureReference(sideTexture));
        faceTextures.put(Face.WEST, new TextureReference(sideTexture));
        faceTextures.put(Face.EAST, new TextureReference(sideTexture));
    }

    /**
     * Creates a block texture with different texture identifiers for top, sides, and bottom.
     */
    public BlockTexture(Identifier topTexture, Identifier sideTexture, Identifier bottomTexture) {
        this.faceTextures = new HashMap<>();
        faceTextures.put(Face.TOP, new TextureReference(topTexture));
        faceTextures.put(Face.BOTTOM, new TextureReference(bottomTexture));
        faceTextures.put(Face.NORTH, new TextureReference(sideTexture));
        faceTextures.put(Face.SOUTH, new TextureReference(sideTexture));
        faceTextures.put(Face.WEST, new TextureReference(sideTexture));
        faceTextures.put(Face.EAST, new TextureReference(sideTexture));
    }

    /**
     * Creates a block texture with different textures for top and sides  (using indices).
     */
    public BlockTexture(int topTexture, int sideTexture) {
        this.faceTextures = new HashMap<>();
        faceTextures.put(Face.TOP, new TextureReference(topTexture));
        faceTextures.put(Face.BOTTOM, new TextureReference(sideTexture));
        faceTextures.put(Face.NORTH, new TextureReference(sideTexture));
        faceTextures.put(Face.SOUTH, new TextureReference(sideTexture));
        faceTextures.put(Face.WEST, new TextureReference(sideTexture));
        faceTextures.put(Face.EAST, new TextureReference(sideTexture));
    }

    /**
     * Creates a block texture with different texture identifiers for top and sides.
     */
    public BlockTexture(Identifier topTexture, Identifier sideTexture) {
        this.faceTextures = new HashMap<>();
        faceTextures.put(Face.TOP, new TextureReference(topTexture));
        faceTextures.put(Face.BOTTOM, new TextureReference(sideTexture));
        faceTextures.put(Face.NORTH, new TextureReference(sideTexture));
        faceTextures.put(Face.SOUTH, new TextureReference(sideTexture));
        faceTextures.put(Face.WEST, new TextureReference(sideTexture));
        faceTextures.put(Face.EAST, new TextureReference(sideTexture));
    }

    /**
     * Creates a block texture with full control over each face (using indices).
     */
    public BlockTexture(int topTexture, int bottomTexture,
                        int northTexture, int southTexture,
                        int westTexture, int eastTexture) {
        this.faceTextures = new HashMap<>();
        faceTextures.put(Face.TOP, new TextureReference(topTexture));
        faceTextures.put(Face.BOTTOM, new TextureReference(bottomTexture));
        faceTextures.put(Face.NORTH, new TextureReference(northTexture));
        faceTextures.put(Face.SOUTH, new TextureReference(southTexture));
        faceTextures.put(Face.WEST, new TextureReference(westTexture));
        faceTextures.put(Face.EAST, new TextureReference(eastTexture));
    }

    /**
     * Creates a block texture with full control over each face (using identifiers).
     */
    public BlockTexture(Identifier topTexture, Identifier bottomTexture,
                        Identifier northTexture, Identifier southTexture,
                        Identifier westTexture, Identifier eastTexture) {
        this.faceTextures = new HashMap<>();
        faceTextures.put(Face.TOP, new TextureReference(topTexture));
        faceTextures.put(Face.BOTTOM, new TextureReference(bottomTexture));
        faceTextures.put(Face.NORTH, new TextureReference(northTexture));
        faceTextures.put(Face.SOUTH, new TextureReference(southTexture));
        faceTextures.put(Face.WEST, new TextureReference(westTexture));
        faceTextures.put(Face.EAST, new TextureReference(eastTexture));
    }

    /**
     * Private constructor for builder pattern.
     */
    private BlockTexture(Map<Face, TextureReference> faceTextures) {
        this.faceTextures = new HashMap<>(faceTextures);
    }

    /**
     * Gets the texture reference for a specific face.
     */
    public TextureReference getTextureForFace(Face face) {
        return faceTextures.get(face);
    }

    /**
     * Gets the texture index for a specific face.
     * Returns -1 if the texture uses an identifier instead.
     */
    public int getTextureIndexForFace(Face face) {
        TextureReference ref = faceTextures.get(face);
        return ref != null && ref.isIndex() ? ref.getIndex() : -1;
    }

    /**
     * Gets the texture identifier for a specific face.
     * Returns null if the texture uses an index instead.
     */
    public Identifier getTextureIdForFace(Face face) {
        TextureReference ref = faceTextures.get(face);
        return ref != null && ref.isIdentifier() ? ref.getIdentifier() : null;
    }

    /**
     * Checks if this texture uses identifiers (dynamic atlas).
     */
    public boolean usesDynamicAtlas() {
        return faceTextures.values().stream().anyMatch(TextureReference::isIdentifier);
    }

    /**
     * Represents a texture reference that can be either an index or an identifier.
     */
    public static class TextureReference {
        private final int index;
        private final Identifier identifier;
        private final boolean isIndex;

        public TextureReference(int index) {
            this.index = index;
            this.identifier = null;
            this.isIndex = true;
        }

        public TextureReference(Identifier identifier) {
            this.index = -1;
            this.identifier = identifier;
            this.isIndex = false;
        }

        public boolean isIndex() {
            return isIndex;
        }

        public boolean isIdentifier() {
            return !isIndex;
        }

        public int getIndex() {
            return index;
        }

        public Identifier getIdentifier() {
            return identifier;
        }
    }

    /**
     * Face enumeration for block sides.
     */
    public enum Face {
        TOP, BOTTOM, NORTH, SOUTH, WEST, EAST
    }

    /**
     * Builder for creating complex block textures.
     */
    public static class Builder {
        private final Map<Face, TextureReference> faceTextures = new HashMap<>();

        /**
         * Sets all faces to the same texture index.
         */
        public Builder allFaces(int textureIndex) {
            TextureReference ref = new TextureReference(textureIndex);
            for (Face face : Face.values()) {
                faceTextures.put(face, ref);
            }
            return this;
        }

        /**
         * Sets all faces to the same texture identifier.
         */
        public Builder allFaces(Identifier textureId) {
            TextureReference ref = new TextureReference(textureId);
            for (Face face : Face.values()) {
                faceTextures.put(face, ref);
            }
            return this;
        }

        /**
         * Sets a specific face to a texture index.
         */
        public Builder face(Face face, int textureIndex) {
            faceTextures.put(face, new TextureReference(textureIndex));
            return this;
        }

        /**
         * Sets a specific face to a texture identifier.
         */
        public Builder face(Face face, Identifier textureId) {
            faceTextures.put(face, new TextureReference(textureId));
            return this;
        }

        /**
         * Sets the top face texture.
         */
        public Builder top(int textureIndex) {
            return face(Face.TOP, textureIndex);
        }

        public Builder top(Identifier textureId) {
            return face(Face.TOP, textureId);
        }

        /**
         * Sets the bottom face texture.
         */
        public Builder bottom(int textureIndex) {
            return face(Face.BOTTOM, textureIndex);
        }

        public Builder bottom(Identifier textureId) {
            return face(Face.BOTTOM, textureId);
        }

        /**
         * Sets all side faces (north, south, west, east) to the same texture.
         */
        public Builder sides(int textureIndex) {
            TextureReference ref = new TextureReference(textureIndex);
            faceTextures.put(Face.NORTH, ref);
            faceTextures.put(Face.SOUTH, ref);
            faceTextures.put(Face.WEST, ref);
            faceTextures.put(Face.EAST, ref);
            return this;
        }

        public Builder sides(Identifier textureId) {
            TextureReference ref = new TextureReference(textureId);
            faceTextures.put(Face.NORTH, ref);
            faceTextures.put(Face.SOUTH, ref);
            faceTextures.put(Face.WEST, ref);
            faceTextures.put(Face.EAST, ref);
            return this;
        }

        /**
         * Sets north face texture.
         */
        public Builder north(int textureIndex) {
            return face(Face.NORTH, textureIndex);
        }

        public Builder north(Identifier textureId) {
            return face(Face.NORTH, textureId);
        }

        /**
         * Sets south face texture.
         */
        public Builder south(int textureIndex) {
            return face(Face.SOUTH, textureIndex);
        }

        public Builder south(Identifier textureId) {
            return face(Face.SOUTH, textureId);
        }

        /**
         * Sets west face texture.
         */
        public Builder west(int textureIndex) {
            return face(Face.WEST, textureIndex);
        }

        public Builder west(Identifier textureId) {
            return face(Face.WEST, textureId);
        }

        /**
         * Sets east face texture.
         */
        public Builder east(int textureIndex) {
            return face(Face.EAST, textureIndex);
        }

        public Builder east(Identifier textureId) {
            return face(Face.EAST, textureId);
        }

        /**
         * Builds the BlockTexture instance.
         */
        public BlockTexture build() {
            // Ensure all faces have a texture
            if (faceTextures.size() != Face.values().length) {
                throw new IllegalStateException("All faces must have a texture assigned");
            }
            return new BlockTexture(faceTextures);
        }
    }

    /**
     * Creates a new builder instance.
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Preset factory methods for common texture patterns.
     */
    public static class Presets {
        /**
         * Single texture on all faces (using index).
         */
        public static BlockTexture single(int textureIndex) {
            return new BlockTexture(textureIndex);
        }

        /**
         * Single texture on all faces (using identifier).
         */
        public static BlockTexture single(Identifier textureId) {
            return new BlockTexture(textureId);
        }

        /**
         * Grass-like blocks: different top, side, and bottom (using indices).
         */
        public static BlockTexture grassLike(int top, int side, int bottom) {
            return new BlockTexture(top, side, bottom);
        }

        /**
         * Grass-like blocks: different top, side, and bottom (using identifiers).
         */
        public static BlockTexture grassLike(Identifier top, Identifier side, Identifier bottom) {
            return new BlockTexture(top, side, bottom);
        }

        /**
         * Log-like blocks: different top/bottom vs sides (using indices).
         */
        public static BlockTexture logLike(int topBottom, int side) {
            return new BlockTexture(topBottom, side, topBottom);
        }

        /**
         * Log-like blocks: different top/bottom vs sides (using identifiers).
         */
        public static BlockTexture logLike(Identifier topBottom, Identifier side) {
            return new BlockTexture(topBottom, side, topBottom);
        }

        /**
         * Column-like blocks: different top, bottom, and sides (using indices).
         */
        public static BlockTexture column(int top, int bottom, int side) {
            return new BlockTexture(top, side, bottom);
        }

        /**
         * Column-like blocks: different top, bottom, and sides (using identifiers).
         */
        public static BlockTexture column(Identifier top, Identifier bottom, Identifier side) {
            return new BlockTexture(top, side, bottom);
        }
    }
}