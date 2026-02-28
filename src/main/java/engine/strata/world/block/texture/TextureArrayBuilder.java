package engine.strata.world.block.texture;

import engine.helios.rendering.texture.TextureArray;
import engine.strata.core.io.ResourceManager;
import engine.strata.util.Identifier;
import engine.strata.world.block.Block;
import engine.strata.world.block.model.BlockModel;
import engine.strata.world.block.model.BlockModelLoader;
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

/**
 * Builds a GL_TEXTURE_2D_ARRAY from texture identifiers using Helios TextureArray.
 * <p>
 * Collects textures from BlockModel definitions, ensuring all textures referenced
 * by models are included in the texture array.
 * <p>
 * Each unique texture becomes one layer of the array. All layers share the
 * same width × height (tileSize × tileSize). Because layers are independent
 * slices there is zero bleed between textures — no border padding is needed
 * and mipmaps are generated per-layer automatically by the driver.
 */
public class TextureArrayBuilder {
    private static final Logger LOGGER = LoggerFactory.getLogger("TextureArrayBuilder");

    private static final int DEFAULT_TILE_SIZE = 32;
    private static final String MISSING_TEXTURE_PATH = "missing";

    // ── Public entry points ───────────────────────────────────────────────────

    /**
     * Scans all registered blocks, loads their models, and builds a texture array
     * containing every unique texture identifier found in the models.
     */
    public static DynamicTextureArray buildArrayFromBlocks(Collection<Block> blocks,
                                                           BlockModelLoader modelLoader,
                                                           int tileSize) {
        LOGGER.info("Building texture array from {} blocks using BlockModel system", blocks.size());

        Set<Identifier> ids = collectTextureIdsFromModels(blocks, modelLoader);
        ids.add(Identifier.ofEngine(MISSING_TEXTURE_PATH)); // layer 0 = missing

        LOGGER.info("Collected {} unique texture identifiers from BlockModels", ids.size());
        return buildArray(ids, tileSize);
    }


    /**
     * Builds a texture array from an explicit set of texture identifiers.
     */
    public static DynamicTextureArray buildArray(Set<Identifier> textureIds, int tileSize) {
        LOGGER.info("Building texture array: {} textures, {}px tiles", textureIds.size(), tileSize);

        // Sort for deterministic layer assignment across runs
        List<Identifier> sorted = new ArrayList<>(textureIds);
        sorted.sort(Comparator.comparing(Identifier::toString));

        // Ensure missing texture is always layer 0
        sorted.remove(Identifier.ofEngine(MISSING_TEXTURE_PATH));
        sorted.add(0, Identifier.ofEngine(MISSING_TEXTURE_PATH));

        int layerCount = sorted.size();
        Map<Identifier, Integer> layerMap = new HashMap<>();

        // Build one ByteBuffer containing all layers back-to-back: RGBA, tileSize×tileSize per layer
        int bytesPerLayer = tileSize * tileSize * 4;
        ByteBuffer allData = BufferUtils.createByteBuffer(bytesPerLayer * layerCount);

        for (int i = 0; i < layerCount; i++) {
            Identifier id = sorted.get(i);
            BufferedImage img = loadTexture(id, tileSize);
            appendLayerToBuffer(img, tileSize, allData);
            layerMap.put(id, i);
            LOGGER.debug("Layer {}: {}", i, id);
        }

        allData.flip();

        TextureArray glTextureArray = uploadToGPU(allData, tileSize, layerCount);

        LOGGER.info("Texture array uploaded: {} layers, GL id={}", layerCount, glTextureArray.getId());
        return new DynamicTextureArray(glTextureArray, tileSize, layerMap);
    }

    // ── Internal helpers ──────────────────────────────────────────────────────

    /**
     * Collects every texture identifier from BlockModels.
     * Loads each block's model and extracts all resolved texture identifiers.
     */
    private static Set<Identifier> collectTextureIdsFromModels(Collection<Block> blocks,
                                                               BlockModelLoader modelLoader) {
        Set<Identifier> ids = new HashSet<>();

        for (Block block : blocks) {
            if (block.isAir()) continue;

            try {
                // Load and resolve the block's model
                Identifier modelId = block.getModelId();
                if (modelId == null) {
                    LOGGER.warn("Block {} has no model ID", block.getId());
                    continue;
                }

                BlockModel model = modelLoader.loadModel(modelId);
                if (model == null) {
                    LOGGER.warn("Failed to load model {} for block {}", modelId, block.getId());
                    continue;
                }

                // Extract all textures from the model
                extractTexturesFromModel(model, ids);

            } catch (Exception e) {
                LOGGER.error("Error collecting textures from block {}", block.getId(), e);
            }
        }

        return ids;
    }

    /**
     * Extracts all texture identifiers from a resolved BlockModel.
     */
    private static void extractTexturesFromModel(BlockModel model, Set<Identifier> ids) {
        // Get textures from the model's texture map
        Map<String, Identifier> textures = model.getTextures();
        if (textures != null) {
            ids.addAll(textures.values());
        }

        // Also extract textures from face data in case some are only in faces
        for (BlockModel.Element element : model.getElements()) {
            for (Map.Entry<BlockModel.Face, BlockModel.FaceData> entry : element.getFaces().entrySet()) {
                BlockModel.FaceData faceData = entry.getValue();
                Identifier texture = faceData.getTexture();
                if (texture != null) {
                    ids.add(texture);
                    LOGGER.debug("Model {} face {} → {}", model.getId(), entry.getKey(), texture);
                }
            }
        }
    }

    /**
     * Writes a tileSize×tileSize RGBA image into the provided ByteBuffer.
     */
    private static void appendLayerToBuffer(BufferedImage img, int tileSize, ByteBuffer buf) {
        int[] pixels = new int[tileSize * tileSize];
        img.getRGB(0, 0, tileSize, tileSize, pixels, 0, tileSize);
        for (int pixel : pixels) {
            buf.put((byte) ((pixel >> 16) & 0xFF)); // R
            buf.put((byte) ((pixel >> 8) & 0xFF));  // G
            buf.put((byte) (pixel & 0xFF));         // B
            buf.put((byte) ((pixel >> 24) & 0xFF)); // A
        }
    }

    /**
     * Creates a texture array through Helios from the prepared data buffer.
     */
    private static TextureArray uploadToGPU(ByteBuffer data, int tileSize, int layerCount) {
        TextureArray textureArray = TextureArray.create(data, tileSize, tileSize, layerCount);

        LOGGER.info("Created texture array through Helios (id={}, {}x{}, {} layers)",
                textureArray.getId(), tileSize, tileSize, layerCount);

        return textureArray;
    }

    // ── Texture loading ───────────────────────────────────────────────────────

    private static BufferedImage loadTexture(Identifier id, int size) {
        if (id.path.equals(MISSING_TEXTURE_PATH)) {
            return createMissingTexture(size);
        }

        // Try "block/" prefix first (standard for block textures)
        try (InputStream stream = ResourceManager.getResourceStream(id, "textures/block", "png")) {
            if (stream != null) {
                BufferedImage img = ImageIO.read(stream);
                if (img != null) {
                    return processLoadedImage(img, size, id);
                }
            }
        } catch (Exception e) {
            LOGGER.debug("Failed to load texture {} from textures/block: {}", id, e.getMessage());
        }

        // Fallback to "blocks/" prefix (legacy)
        try (InputStream stream = ResourceManager.getResourceStream(id, "textures/blocks", "png")) {
            if (stream != null) {
                BufferedImage img = ImageIO.read(stream);
                if (img != null) {
                    return processLoadedImage(img, size, id);
                }
            }
        } catch (Exception e) {
            LOGGER.debug("Failed to load texture {} from textures/blocks: {}", id, e.getMessage());
        }

        LOGGER.warn("Texture not found: {} – using missing texture placeholder", id);
        return createMissingTexture(size);
    }

    /**
     * Processes a loaded image: resize if needed and ensure ARGB format.
     */
    private static BufferedImage processLoadedImage(BufferedImage img, int size, Identifier id) {
        if (img.getWidth() != size || img.getHeight() != size) {
            LOGGER.debug("Resizing {} from {}x{} to {}x{}",
                    id, img.getWidth(), img.getHeight(), size, size);
            return resizeImage(img, size);
        }
        return ensureARGB(img);
    }

    /** Magenta/black checkerboard — immediately recognisable as missing. */
    private static BufferedImage createMissingTexture(int size) {
        BufferedImage img = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        int half = Math.max(1, size / 2);
        g.setColor(Color.MAGENTA);
        g.fillRect(0, 0, half, half);
        g.setColor(Color.BLACK);
        g.fillRect(half, 0, half, half);
        g.setColor(Color.BLACK);
        g.fillRect(0, half, half, half);
        g.setColor(Color.MAGENTA);
        g.fillRect(half, half, half, half);
        g.dispose();
        return img;
    }

    private static BufferedImage resizeImage(BufferedImage src, int size) {
        BufferedImage dst = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = dst.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        g.drawImage(src, 0, 0, size, size, null);
        g.dispose();
        return dst;
    }

    private static BufferedImage ensureARGB(BufferedImage src) {
        if (src.getType() == BufferedImage.TYPE_INT_ARGB) return src;
        BufferedImage dst = new BufferedImage(src.getWidth(), src.getHeight(),
                BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = dst.createGraphics();
        g.drawImage(src, 0, 0, null);
        g.dispose();
        return dst;
    }

    // ── Builder API ───────────────────────────────────────────────────────────

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private final Set<Identifier> ids = new HashSet<>();
        private int tileSize = DEFAULT_TILE_SIZE;
        private BlockModelLoader modelLoader;

        public Builder addTexture(Identifier id) {
            ids.add(id);
            return this;
        }

        public Builder addTextures(Collection<Identifier> texIds) {
            ids.addAll(texIds);
            return this;
        }

        /**
         * Adds textures from blocks using BlockModel system.
         * Requires modelLoader to be set first.
         */
        public Builder addTexturesFromBlocks(Collection<Block> blocks) {
//            if (modelLoader != null) {
                ids.addAll(collectTextureIdsFromModels(blocks, modelLoader));
//            } else {
//                LOGGER.warn("BlockModelLoader not set, using legacy texture collection");
//                ids.addAll(collectTextureIdsLegacy(blocks));
//            }
            return this;
        }

        /**
         * Sets the BlockModelLoader for collecting textures from models.
         */
        public Builder modelLoader(BlockModelLoader loader) {
            this.modelLoader = loader;
            return this;
        }

        public Builder tileSize(int size) {
            if (size <= 0 || (size & (size - 1)) != 0)
                throw new IllegalArgumentException("tileSize must be a power of 2, got " + size);
            this.tileSize = size;
            return this;
        }

        public DynamicTextureArray build() {
            if (ids.isEmpty()) throw new IllegalStateException("No textures added");
            ids.add(Identifier.ofEngine(MISSING_TEXTURE_PATH));
            return buildArray(ids, tileSize);
        }
    }
}