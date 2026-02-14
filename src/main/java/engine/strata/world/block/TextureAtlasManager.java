package engine.strata.world.block;

import engine.strata.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;

/**
 * Manages texture atlases for the game.
 * Handles initialization, building, and cleanup of texture atlases.
 *
 * USAGE:
 * 1. After registering all blocks, call initializeAtlas()
 * 2. Use getAtlas() to retrieve the atlas for rendering
 * 3. Call cleanup() when shutting down or reloading resources
 *
 * EXAMPLE:
 * <pre>
 * // During game initialization (after blocks are registered):
 * TextureAtlasManager.getInstance().initializeAtlas(Blocks.getAllBlocks());
 *
 * // During rendering setup:
 * DynamicTextureAtlas atlas = TextureAtlasManager.getInstance().getAtlas();
 * ChunkRenderer renderer = new ChunkRenderer(chunkManager, atlas, atlasId);
 *
 * // During shutdown:
 * TextureAtlasManager.getInstance().cleanup();
 * </pre>
 */
public class TextureAtlasManager {
    private static final Logger LOGGER = LoggerFactory.getLogger("AtlasManager");
    private static final TextureAtlasManager INSTANCE = new TextureAtlasManager();

    private DynamicTextureAtlas blockAtlas;
    private boolean initialized = false;

    private TextureAtlasManager() {
        // Private constructor for singleton
    }

    public static TextureAtlasManager getInstance() {
        return INSTANCE;
    }

    /**
     * Initializes the block texture atlas by scanning all registered blocks.
     * This should be called after all blocks have been registered but before rendering begins.
     *
     * @param blocks Collection of all registered blocks
     * @return The built texture atlas
     */
    public DynamicTextureAtlas initializeAtlas(Collection<Block> blocks) {
        return initializeAtlas(blocks, 16);
    }

    /**
     * Initializes the block texture atlas with a custom tile size.
     *
     * @param blocks Collection of all registered blocks
     * @param tileSize Size of each texture tile in pixels (must be power of 2)
     * @return The built texture atlas
     */
    public DynamicTextureAtlas initializeAtlas(Collection<Block> blocks, int tileSize) {
        if (initialized) {
            LOGGER.warn("Texture atlas already initialized, cleaning up old atlas");
            cleanup();
        }

        LOGGER.info("Initializing block texture atlas from {} blocks", blocks.size());

        try {
            blockAtlas = TextureAtlasBuilder.buildAtlasFromBlocks(blocks, tileSize);
            initialized = true;

            LOGGER.info("Texture atlas initialized successfully: {}x{} tiles, {} textures",
                    blockAtlas.getAtlasWidth(), blockAtlas.getAtlasHeight(),
                    blockAtlas.getTotalTextures());

            return blockAtlas;
        } catch (Exception e) {
            LOGGER.error("Failed to initialize texture atlas", e);
            throw new RuntimeException("Failed to initialize texture atlas", e);
        }
    }

    /**
     * Gets the current block texture atlas.
     *
     * @return The block texture atlas, or null if not initialized
     */
    public DynamicTextureAtlas getAtlas() {
        if (!initialized) {
            LOGGER.warn("Texture atlas not initialized! Call initializeAtlas() first.");
        }
        return blockAtlas;
    }

    /**
     * Checks if the atlas has been initialized.
     *
     * @return true if the atlas is ready to use
     */
    public boolean isInitialized() {
        return initialized;
    }

    /**
     * Reloads the texture atlas.
     * Useful for resource pack changes or texture updates.
     *
     * @param blocks Collection of all registered blocks
     */
    public void reloadAtlas(Collection<Block> blocks) {
        LOGGER.info("Reloading texture atlas");
        cleanup();
        initializeAtlas(blocks);
    }

    /**
     * Cleans up GPU resources.
     * Should be called when shutting down or before reloading.
     */
    public void cleanup() {
        if (blockAtlas != null) {
            LOGGER.info("Cleaning up texture atlas");
            blockAtlas.delete();
            blockAtlas = null;
        }
        initialized = false;
    }

    /**
     * Builder for creating custom atlases with advanced options.
     */
    public static class CustomAtlasBuilder {
        private final TextureAtlasBuilder.Builder builder = TextureAtlasBuilder.builder();

        /**
         * Adds textures from all registered blocks.
         */
        public CustomAtlasBuilder fromBlocks(Collection<Block> blocks) {
            builder.addTexturesFromBlocks(blocks);
            return this;
        }

        /**
         * Adds a specific texture identifier.
         */
        public CustomAtlasBuilder addTexture(Identifier textureId) {
            builder.addTexture(textureId);
            return this;
        }

        /**
         * Adds multiple texture identifiers.
         */
        public CustomAtlasBuilder addTextures(Collection<Identifier> textureIds) {
            builder.addTextures(textureIds);
            return this;
        }

        /**
         * Sets the tile size (must be power of 2).
         */
        public CustomAtlasBuilder tileSize(int tileSize) {
            builder.tileSize(tileSize);
            return this;
        }

        /**
         * Includes debug textures for testing.
         */
        public CustomAtlasBuilder withDebugTextures() {
            builder.includeDebugTextures(true);
            return this;
        }

        /**
         * Builds the custom atlas.
         */
        public DynamicTextureAtlas build() {
            return builder.build();
        }
    }

    /**
     * Creates a builder for custom texture atlases.
     */
    public static CustomAtlasBuilder customAtlas() {
        return new CustomAtlasBuilder();
    }
}