package engine.strata.world.block;

import engine.strata.util.Identifier;

/**
 * Represents a single block type in the world.
 * Blocks are immutable definitions registered in the block registry.
 *
 * <h3>Texture / geometry</h3>
 * All visual information (geometry, UVs, textures) is defined in the block's
 * associated {@link engine.strata.world.block.model.BlockModel}, loaded via
 * {@link engine.strata.world.block.model.BlockModelLoader} using the identifier
 * returned by {@link #getModelId()}.
 *
 * By default the model identifier equals the block identifier, so registering
 * {@code strata:stone} will automatically load
 * {@code assets/strata/models/block/stone.json}.
 * Pass an explicit {@code modelId} to the constructor to share a model between
 * blocks or to use a non-standard asset path.
 */
public class Block {

    private final Identifier id;
    private final BlockProperties properties;

    /**
     * Optional override for the model asset path.
     * When {@code null} the block's own {@link #id} is used as the model ID.
     */
    private final Identifier modelId;

    // Internal numeric ID for chunk storage (assigned by registry)
    private short numericId = -1;

    // ── Constructors ──────────────────────────────────────────────────────────

    /** Standard constructor — model ID defaults to the block ID. */
    public Block(Identifier id, BlockProperties properties) {
        this(id, properties, null);
    }

    /**
     * Constructor with an explicit model ID override.
     * Use when multiple blocks share one model, or the model lives at a
     * non-default asset path.
     */
    public Block(Identifier id, BlockProperties properties, Identifier modelId) {
        this.id = id;
        this.properties = properties;
        this.modelId = modelId;
    }

    // ── Identifiers ───────────────────────────────────────────────────────────

    public Identifier getId() {
        return id;
    }

    /**
     * Returns the model identifier used to look up this block's
     * {@link engine.strata.world.block.model.BlockModel}.
     *
     * Falls back to the block's own identifier when no override was set,
     * so {@code strata:stone} automatically loads {@code stone.json}.
     */
    public Identifier getModelId() {
        return modelId != null ? modelId : id;
    }

    // ── Registry ID ───────────────────────────────────────────────────────────

    /**
     * Gets the numeric ID used for efficient chunk storage.
     * Assigned by the block registry during registration; -1 until set.
     */
    public short getNumericId() {
        return numericId;
    }

    /** Called by the registry only — throws if already set. */
    void setNumericId(short id) {
        if (this.numericId != -1) {
            throw new IllegalStateException("Block numeric ID already set for " + this.id);
        }
        this.numericId = id;
    }

    // ── Properties ────────────────────────────────────────────────────────────

    public BlockProperties getProperties() { return properties; }

    public boolean isTransparent() { return properties.transparent; }
    public boolean isFullBlock() { return properties.fullBlock; }
    public int getLightEmission() { return properties.lightEmission; }
    public float getHardness() { return properties.hardness; }
    public boolean isCollidable() { return properties.collidable; }

    public boolean isAir() { return this == Blocks.AIR; }

    // ── Object ────────────────────────────────────────────────────────────────

    @Override
    public String toString() {
        return "Block{" + id + ", numericId=" + numericId + ", model=" + getModelId() + "}";
    }

    // ═════════════════════════════════════════════════════════════════════════
    // BlockProperties
    // ═════════════════════════════════════════════════════════════════════════

    /**
     * Immutable value type describing a block's physical behaviour.
     * Construct via {@link Builder}.
     */
    public static class BlockProperties {
        private final boolean transparent;
        private final boolean fullBlock;
        private final int lightEmission;
        private final float hardness;
        private final boolean collidable;

        private BlockProperties(Builder b) {
            this.transparent = b.solid;
            this.fullBlock = b.fullBlock;
            this.lightEmission = b.lightEmission;
            this.hardness = b.hardness;
            this.collidable = b.collidable;
        }

        public static Builder builder() { return new Builder(); }

        public boolean isTransparent() { return transparent; }
        public boolean isFullBlock() { return fullBlock; }
        public int getLightEmission() { return lightEmission; }
        public float getHardness() { return hardness; }
        public boolean isCollidable() { return collidable; }

        public static class Builder {
            private boolean solid = true;
            private boolean fullBlock = true;
            private int lightEmission = 0;
            private float hardness = 1.0f;
            private boolean collidable = true;

            public Builder transparent(boolean v) { solid = v; return this; }
            public Builder fullBlock(boolean v) { fullBlock = v; return this; }
            public Builder lightEmission(int v) { lightEmission = Math.max(0, Math.min(15, v));   return this; }
            public Builder hardness(float v) { hardness = v; return this; }
            public Builder collidable(boolean v) { collidable = v; return this; }

            public BlockProperties build() { return new BlockProperties(this); }
        }
    }
}