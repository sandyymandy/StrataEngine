package engine.strata.world.block;

import engine.strata.core.io.ResourceManager;
import engine.strata.util.Identifier;
import org.lwjgl.BufferUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.*;
import java.util.List;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL30.glGenerateMipmap;

/**
 * Automatically builds texture atlases from individual texture files.
 *
 * Process:
 * 1. Scans all registered blocks and collects their texture identifiers
 * 2. Loads textures using ResourceManager (supports mods)
 * 3. Combines them into a single atlas texture
 * 4. Creates a mapping from Identifier -> atlas index
 * 5. Uploads to GPU and generates mipmaps
 *
 * Texture Resolution:
 * - "strata:stone" → /assets/strata/textures/blocks/stone.png
 * - "mymod:bouncy" → /assets/mymod/textures/blocks/bouncy.png
 */
public class TextureAtlasBuilder {
    private static final Logger LOGGER = LoggerFactory.getLogger("AtlasBuilder");

    private static final int DEFAULT_TILE_SIZE = 16; // 16x16 pixel textures
    private static final String MISSING_TEXTURE_ID = "missing";

    /**
     * Builds a texture atlas by scanning all registered blocks.
     * Automatically collects all texture identifiers from block definitions.
     *
     * @param blocks Collection of all registered blocks
     * @param tileSize Size of each tile in pixels (typically 16)
     * @return Built atlas ready for use
     */
    public static DynamicTextureAtlas buildAtlasFromBlocks(Collection<Block> blocks, int tileSize) {
        LOGGER.info("Building texture atlas from {} blocks", blocks.size());

        // Collect all unique texture identifiers from blocks
        Set<Identifier> textureIds = collectTextureIds(blocks);

        // Add missing texture as fallback
        textureIds.add(Identifier.ofEngine(MISSING_TEXTURE_ID));

        LOGGER.info("Collected {} unique texture identifiers", textureIds.size());

        // Build the atlas
        return buildAtlas(textureIds, tileSize);
    }

    /**
     * Collects all unique texture identifiers from a collection of blocks.
     * Handles both index-based and identifier-based textures.
     */
    private static Set<Identifier> collectTextureIds(Collection<Block> blocks) {
        Set<Identifier> textureIds = new HashSet<>();

        for (Block block : blocks) {
            if (block.isAir()) {
                continue; // Skip air blocks
            }

            BlockTexture texture = block.getTexture();
            if (texture == null) {
                continue;
            }

            // Collect texture identifiers from all faces
            for (BlockTexture.Face face : BlockTexture.Face.values()) {
                Identifier texId = texture.getTextureIdForFace(face);
                if (texId != null) {
                    textureIds.add(texId);
                    LOGGER.debug("Block {} uses texture: {}", block.getId(), texId);
                }
            }
        }

        return textureIds;
    }

    /**
     * Builds a texture atlas from a set of texture identifiers.
     *
     * @param textureIds Set of texture identifiers to include
     * @param tileSize Size of each tile in pixels (typically 16)
     * @return Built atlas with texture mappings
     */
    public static DynamicTextureAtlas buildAtlas(Set<Identifier> textureIds, int tileSize) {
        LOGGER.info("Building texture atlas with {} textures ({}x{} tiles)",
                textureIds.size(), tileSize, tileSize);

        // Calculate atlas dimensions (power of 2, square-ish)
        int textureCount = textureIds.size();
        int tilesPerSide = (int) Math.ceil(Math.sqrt(textureCount));

        // Round up to next power of 2 for better GPU performance
        tilesPerSide = nextPowerOf2(tilesPerSide);

        int atlasWidth = tilesPerSide;
        int atlasHeight = tilesPerSide;
        int atlasPixelWidth = atlasWidth * tileSize;
        int atlasPixelHeight = atlasHeight * tileSize;

        LOGGER.info("Atlas dimensions: {}x{} tiles ({}x{} pixels)",
                atlasWidth, atlasHeight, atlasPixelWidth, atlasPixelHeight);

        // Create the atlas image
        BufferedImage atlasImage = new BufferedImage(
                atlasPixelWidth, atlasPixelHeight, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = atlasImage.createGraphics();

        // Set background to magenta for missing textures
        g.setColor(Color.MAGENTA);
        g.fillRect(0, 0, atlasPixelWidth, atlasPixelHeight);

        // Enable high-quality rendering
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        g.setRenderingHint(RenderingHints.KEY_RENDERING,
                RenderingHints.VALUE_RENDER_QUALITY);

        // Map texture identifier -> atlas index
        Map<Identifier, Integer> textureIndexMap = new HashMap<>();

        // Sort texture IDs for consistent atlas layout
        List<Identifier> sortedTextureIds = new ArrayList<>(textureIds);
        sortedTextureIds.sort(Comparator.comparing(Identifier::toString));

        // Load and place textures
        int currentIndex = 0;
        for (Identifier textureId : sortedTextureIds) {
            int tileX = currentIndex % atlasWidth;
            int tileY = currentIndex / atlasWidth;

            int pixelX = tileX * tileSize;
            int pixelY = tileY * tileSize;

            // Load the texture using ResourceManager
            BufferedImage texture = loadTexture(textureId, tileSize);

            // Draw it onto the atlas
            g.drawImage(texture, pixelX, pixelY, tileSize, tileSize, null);

            // Store mapping
            textureIndexMap.put(textureId, currentIndex);

            LOGGER.debug("Placed texture '{}' at index {} (tile {}, {})",
                    textureId, currentIndex, tileX, tileY);

            currentIndex++;
        }

        g.dispose();

        // Upload to GPU
        int textureGpuId = uploadToGPU(atlasImage, tileSize);

        LOGGER.info("Texture atlas built successfully with {} textures", textureCount);

        return new DynamicTextureAtlas(
                textureGpuId,
                atlasWidth,
                atlasHeight,
                tileSize,
                textureIndexMap
        );
    }

    /**
     * Loads a texture from resources using ResourceManager.
     * Returns a magenta/black checkerboard placeholder if not found.
     *
     * @param textureId Identifier like "strata:stone" or "mymod:bouncy"
     * @param size Target size (will resize if needed)
     */
    private static BufferedImage loadTexture(Identifier textureId, int size) {
        // Special handling for missing texture
        if (textureId.path.equals(MISSING_TEXTURE_ID)) {
            return createMissingTexture(size);
        }

        // Try to load using ResourceManager
        // Looks in: /assets/{namespace}/textures/blocks/{path}.png
        try (InputStream stream = ResourceManager.getResourceStream(
                textureId, "textures/blocks", "png")) {

            if (stream != null) {
                BufferedImage image = ImageIO.read(stream);
                if (image != null) {
                    // Resize if needed
                    if (image.getWidth() != size || image.getHeight() != size) {
                        LOGGER.debug("Resizing texture {} from {}x{} to {}x{}",
                                textureId, image.getWidth(), image.getHeight(), size, size);
                        return resizeImage(image, size, size);
                    }
                    return image;
                }
            }
        } catch (Exception e) {
            LOGGER.warn("Failed to load texture {}: {}", textureId, e.getMessage());
        }

        // Create missing texture (magenta/black checkerboard)
        LOGGER.warn("Texture not found: {} - using missing texture", textureId);
        return createMissingTexture(size);
    }

    /**
     * Creates a missing texture (magenta/black checkerboard).
     */
    private static BufferedImage createMissingTexture(int size) {
        BufferedImage image = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();

        int checkSize = Math.max(1, size / 2);

        // Top-left: Magenta
        g.setColor(Color.MAGENTA);
        g.fillRect(0, 0, checkSize, checkSize);

        // Top-right: Black
        g.setColor(Color.BLACK);
        g.fillRect(checkSize, 0, checkSize, checkSize);

        // Bottom-left: Black
        g.fillRect(0, checkSize, checkSize, checkSize);

        // Bottom-right: Magenta
        g.setColor(Color.MAGENTA);
        g.fillRect(checkSize, checkSize, checkSize, checkSize);

        g.dispose();
        return image;
    }

    /**
     * Resizes an image to the target size using nearest-neighbor interpolation.
     */
    private static BufferedImage resizeImage(BufferedImage original, int width, int height) {
        BufferedImage resized = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = resized.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        g.drawImage(original, 0, 0, width, height, null);
        g.dispose();
        return resized;
    }

    /**
     * Uploads the atlas image to GPU with optimized settings for pixel art.
     */
    private static int uploadToGPU(BufferedImage image, int tileSize) {
        int width = image.getWidth();
        int height = image.getHeight();

        LOGGER.debug("Uploading {}x{} atlas to GPU", width, height);

        // Convert to byte buffer
        int[] pixels = new int[width * height];
        image.getRGB(0, 0, width, height, pixels, 0, width);

        ByteBuffer buffer = BufferUtils.createByteBuffer(width * height * 4);

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int pixel = pixels[y * width + x];

                buffer.put((byte) ((pixel >> 16) & 0xFF)); // Red
                buffer.put((byte) ((pixel >> 8) & 0xFF));  // Green
                buffer.put((byte) (pixel & 0xFF));         // Blue
                buffer.put((byte) ((pixel >> 24) & 0xFF)); // Alpha
            }
        }

        buffer.flip();

        // Create OpenGL texture
        int textureId = glGenTextures();
        glBindTexture(GL_TEXTURE_2D, textureId);

        // Upload to GPU
        glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA8, width, height, 0,
                GL_RGBA, GL_UNSIGNED_BYTE, buffer);

        // Generate mipmaps for better quality at distance
        glGenerateMipmap(GL_TEXTURE_2D);

        // Set texture parameters for pixel-perfect rendering
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST_MIPMAP_LINEAR);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST); // Pixelated look
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_REPEAT);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_REPEAT);

        // Unbind
        glBindTexture(GL_TEXTURE_2D, 0);

        LOGGER.info("Uploaded atlas to GPU (texture ID: {}, size: {}x{})", textureId, width, height);

        return textureId;
    }

    /**
     * Returns the next power of 2 >= n.
     */
    private static int nextPowerOf2(int n) {
        if (n <= 0) return 1;
        n--;
        n |= n >> 1;
        n |= n >> 2;
        n |= n >> 4;
        n |= n >> 8;
        n |= n >> 16;
        return n + 1;
    }

    /**
     * Builder class for creating atlases with custom settings.
     */
    public static class Builder {
        private final Set<Identifier> textureIds = new HashSet<>();
        private int tileSize = DEFAULT_TILE_SIZE;
        private boolean includeDebugTextures = false;

        /**
         * Adds a texture identifier to the atlas.
         */
        public Builder addTexture(Identifier textureId) {
            textureIds.add(textureId);
            return this;
        }

        /**
         * Adds multiple texture identifiers to the atlas.
         */
        public Builder addTextures(Collection<Identifier> textureIds) {
            this.textureIds.addAll(textureIds);
            return this;
        }

        /**
         * Collects textures from all registered blocks.
         */
        public Builder addTexturesFromBlocks(Collection<Block> blocks) {
            textureIds.addAll(collectTextureIds(blocks));
            return this;
        }

        /**
         * Sets the tile size for the atlas.
         */
        public Builder tileSize(int tileSize) {
            if (tileSize <= 0 || (tileSize & (tileSize - 1)) != 0) {
                throw new IllegalArgumentException("Tile size must be a power of 2");
            }
            this.tileSize = tileSize;
            return this;
        }

        /**
         * Includes debug textures (grid, test patterns).
         */
        public Builder includeDebugTextures(boolean include) {
            this.includeDebugTextures = include;
            return this;
        }

        /**
         * Builds the texture atlas.
         */
        public DynamicTextureAtlas build() {
            if (textureIds.isEmpty()) {
                throw new IllegalStateException("Cannot build atlas with no textures");
            }

            // Add missing texture
            textureIds.add(Identifier.ofEngine(MISSING_TEXTURE_ID));

            // Add debug textures if requested
            if (includeDebugTextures) {
                textureIds.add(Identifier.ofEngine("debug_grid"));
                textureIds.add(Identifier.ofEngine("debug_uv"));
            }

            return buildAtlas(textureIds, tileSize);
        }
    }

    /**
     * Creates a new builder instance.
     */
    public static Builder builder() {
        return new Builder();
    }
}