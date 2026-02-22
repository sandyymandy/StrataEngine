package engine.strata.world.block.texture;

import engine.strata.world.block.Block;
import engine.strata.world.block.model.BlockModelLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;

/**
 * Singleton that owns the lifecycle of the block texture array.
 *
 * Usage:
 * <pre>
 * // After all blocks are registered, before rendering:
 * TextureArrayManager.getInstance().initialize(Blocks.getAllBlocks());
 *
 * // In renderer setup:
 * DynamicTextureArray arr = TextureArrayManager.getInstance().getArray();
 *
 * // On shutdown / resource-pack reload:
 * TextureArrayManager.getInstance().cleanup();
 * </pre>
 */
public class TextureArrayManager {
    private static final Logger LOGGER = LoggerFactory.getLogger("TextureArrayManager");
    private static final TextureArrayManager INSTANCE = new TextureArrayManager();

    private DynamicTextureArray blockArray;
    private boolean initialized = false;

    private TextureArrayManager() {}

    public static TextureArrayManager getInstance() { return INSTANCE; }

    // ── Initialization ────────────────────────────────────────────────────────

    /** Initialize with default 16 px tile size. */
    public DynamicTextureArray initialize(Collection<Block> blocks) {
        return initialize(blocks, 16);
    }

    /** Initialize with a custom tile size (must be a power of 2). */
    public DynamicTextureArray initialize(Collection<Block> blocks, int tileSize) {
        if (initialized) {
            LOGGER.warn("Texture array already initialized – cleaning up first");
            cleanup();
        }

        LOGGER.info("Initializing block texture array from {} blocks, tileSize={}",
                blocks.size(), tileSize);
        try {
            blockArray  = TextureArrayBuilder.buildArrayFromBlocks(blocks, new BlockModelLoader(), tileSize);
            initialized = true;
            LOGGER.info("Texture array ready: {} layers", blockArray.getLayerCount());
            return blockArray;
        } catch (Exception e) {
            LOGGER.error("Failed to initialize texture array", e);
            throw new RuntimeException("Failed to initialize texture array", e);
        }
    }

    // ── Accessors ─────────────────────────────────────────────────────────────

    public DynamicTextureArray getArray() {
        if (!initialized) LOGGER.warn("getArray() called before initialize()!");
        return blockArray;
    }

    public boolean isInitialized() { return initialized; }

    // ── Reload / cleanup ──────────────────────────────────────────────────────

    public void reload(Collection<Block> blocks) {
        LOGGER.info("Reloading texture array");
        cleanup();
        initialize(blocks);
    }

    public void cleanup() {
        if (blockArray != null) {
            blockArray.delete();
            blockArray = null;
        }
        initialized = false;
    }

    // ── Custom-atlas builder ──────────────────────────────────────────────────

    public static TextureArrayBuilder.Builder customArray() {
        return TextureArrayBuilder.builder();
    }
}