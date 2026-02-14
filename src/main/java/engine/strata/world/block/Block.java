package engine.strata.world.block;

import engine.strata.util.Identifier;

/**
 * Represents a single block type in the world.
 * Blocks are immutable definitions registered in the BlockRegistry.
 */
public class Block {
    private final Identifier id;
    private final BlockProperties properties;
    private final BlockTexture texture;

    // Internal numeric ID for chunk storage (assigned by registry)
    private short numericId = -1;

    public Block(Identifier id, BlockProperties properties, BlockTexture texture) {
        this.id = id;
        this.properties = properties;
        this.texture = texture;
    }

    // Convenience constructor for blocks without texture (like air)
    public Block(Identifier id, BlockProperties properties) {
        this(id, properties, new BlockTexture(0));
    }

    public Identifier getId() {
        return id;
    }

    public BlockProperties getProperties() {
        return properties;
    }

    /**
     * Gets the numeric ID for efficient chunk storage.
     * This is assigned by the registry during registration.
     */
    public short getNumericId() {
        return numericId;
    }

    /**
     * Sets the numeric ID (called by registry only).
     */
    void setNumericId(short id) {
        if (this.numericId != -1) {
            throw new IllegalStateException("Block numeric ID already set!");
        }
        this.numericId = id;
    }

    public boolean isSolid() {
        return properties.solid;
    }

    public boolean isOpaque() {
        return properties.opaque;
    }

    public int getLightEmission() {
        return properties.lightEmission;
    }

    public float getHardness() {
        return properties.hardness;
    }

    public boolean isCollidable() {
        return properties.collidable;
    }

    public boolean isAir() {
        return this == Blocks.AIR;
    }

    public BlockTexture getTexture() {
        return texture;
    }

    @Override
    public String toString() {
        return "Block{" + id + ", numericId=" + numericId + "}";
    }

    /**
     * Block properties that define physical behavior.
     */
    public static class BlockProperties {
        private final boolean solid;
        private final boolean opaque;
        private final int lightEmission;
        private final float hardness;
        private final boolean collidable;

        private BlockProperties(Builder builder) {
            this.solid = builder.solid;
            this.opaque = builder.opaque;
            this.lightEmission = builder.lightEmission;
            this.hardness = builder.hardness;
            this.collidable = builder.collidable;
        }

        public static Builder builder() {
            return new Builder();
        }

        public boolean isSolid() { return solid; }
        public boolean isOpaque() { return opaque; }
        public int getLightEmission() { return lightEmission; }
        public float getHardness() { return hardness; }
        public boolean isCollidable() { return collidable; }

        public static class Builder {
            private boolean solid = true;
            private boolean opaque = true;
            private int lightEmission = 0;
            private float hardness = 1.0f;
            private boolean collidable = true;

            public Builder solid(boolean solid) {
                this.solid = solid;
                return this;
            }

            public Builder opaque(boolean opaque) {
                this.opaque = opaque;
                return this;
            }

            public Builder lightEmission(int level) {
                this.lightEmission = Math.max(0, Math.min(15, level));
                return this;
            }

            public Builder hardness(float hardness) {
                this.hardness = hardness;
                return this;
            }

            public Builder collidable(boolean collidable) {
                this.collidable = collidable;
                return this;
            }

            public BlockProperties build() {
                return new BlockProperties(this);
            }
        }
    }
}