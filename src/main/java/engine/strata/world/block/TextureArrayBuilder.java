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
import static org.lwjgl.opengl.GL12.*;
import static org.lwjgl.opengl.GL30.*;

/**
 * Builds a GL_TEXTURE_2D_ARRAY from the texture identifiers declared by
 * registered blocks.
 *
 * Each unique texture becomes one layer of the array.  All layers share the
 * same width × height (tileSize × tileSize).  Because layers are independent
 * slices there is zero bleed between textures — no border padding is needed
 * and mipmaps are generated per-layer automatically by the driver.
 *
 * Texture resolution:
 *   "strata:stone"   →  /assets/strata/textures/blocks/stone.png
 *   "mymod:bouncy"   →  /assets/mymod/textures/blocks/bouncy.png
 */
public class TextureArrayBuilder {
    private static final Logger LOGGER = LoggerFactory.getLogger("TextureArrayBuilder");

    private static final int    DEFAULT_TILE_SIZE    = 16;
    private static final String MISSING_TEXTURE_PATH = "missing";

    // ── Public entry points ───────────────────────────────────────────────────

    /**
     * Scans all registered blocks and builds a texture array containing every
     * unique texture identifier found.
     */
    public static DynamicTextureArray buildArrayFromBlocks(Collection<Block> blocks, int tileSize) {
        LOGGER.info("Building texture array from {} blocks", blocks.size());

        Set<Identifier> ids = collectTextureIds(blocks);
        ids.add(Identifier.ofEngine(MISSING_TEXTURE_PATH)); // layer 0 = missing

        LOGGER.info("Collected {} unique texture identifiers", ids.size());
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

        int glId = uploadToGPU(allData, tileSize, layerCount);

        LOGGER.info("Texture array uploaded: {} layers, GL id={}", layerCount, glId);
        return new DynamicTextureArray(glId, tileSize, layerCount, layerMap);
    }

    // ── Internal helpers ──────────────────────────────────────────────────────

    /** Collects every Identifier-based texture reference from the block registry. */
    private static Set<Identifier> collectTextureIds(Collection<Block> blocks) {
        Set<Identifier> ids = new HashSet<>();
        for (Block block : blocks) {
            if (block.isAir()) continue;
            BlockTexture tex = block.getTexture();
            if (tex == null) continue;
            for (BlockTexture.Face face : BlockTexture.Face.values()) {
                Identifier id = tex.getTextureIdForFace(face);
                if (id != null) {
                    ids.add(id);
                    LOGGER.debug("Block {} face {} → {}", block.getId(), face, id);
                }
            }
        }
        return ids;
    }

    /**
     * Writes a tileSize×tileSize RGBA image into the provided ByteBuffer
     * (which must have enough remaining capacity).
     */
    private static void appendLayerToBuffer(BufferedImage img, int tileSize, ByteBuffer buf) {
        int[] pixels = new int[tileSize * tileSize];
        img.getRGB(0, 0, tileSize, tileSize, pixels, 0, tileSize);
        for (int pixel : pixels) {
            buf.put((byte) ((pixel >> 16) & 0xFF)); // R
            buf.put((byte) ((pixel >>  8) & 0xFF)); // G
            buf.put((byte) ( pixel        & 0xFF)); // B
            buf.put((byte) ((pixel >> 24) & 0xFF)); // A
        }
    }

    /**
     * Uploads the flat buffer of layer data to a GL_TEXTURE_2D_ARRAY and
     * configures filtering suitable for pixel-art block textures.
     */
    private static int uploadToGPU(ByteBuffer data, int tileSize, int layerCount) {
        int id = glGenTextures();
        glBindTexture(GL_TEXTURE_2D_ARRAY, id);

        // Allocate storage for all mip levels and all layers in one call.
        // glTexImage3D with depth = layerCount defines the array.
        glTexImage3D(
                GL_TEXTURE_2D_ARRAY,
                0,              // mip level 0
                GL_RGBA8,       // internal format
                tileSize,       // width
                tileSize,       // height
                layerCount,     // depth = number of layers
                0,              // border (must be 0)
                GL_RGBA,        // format of supplied data
                GL_UNSIGNED_BYTE,
                data
        );

        // Generate full mip chain per layer — driver handles this automatically
        glGenerateMipmap(GL_TEXTURE_2D_ARRAY);

        // Nearest-neighbour mag for the crisp pixel-art look;
        // nearest-mipmap-linear min for clean distance fade without blur.
        glTexParameteri(GL_TEXTURE_2D_ARRAY, GL_TEXTURE_MIN_FILTER, GL_NEAREST_MIPMAP_LINEAR);
        glTexParameteri(GL_TEXTURE_2D_ARRAY, GL_TEXTURE_MAG_FILTER, GL_NEAREST);

        // Clamp all axes — no wrapping between layers (R axis) or within tiles
        glTexParameteri(GL_TEXTURE_2D_ARRAY, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
        glTexParameteri(GL_TEXTURE_2D_ARRAY, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
        glTexParameteri(GL_TEXTURE_2D_ARRAY, GL_TEXTURE_WRAP_R, GL_CLAMP_TO_EDGE);

        glBindTexture(GL_TEXTURE_2D_ARRAY, 0);

        LOGGER.info("Uploaded texture array to GPU (id={}, {}x{}, {} layers)",
                id, tileSize, tileSize, layerCount);
        return id;
    }

    // ── Texture loading ───────────────────────────────────────────────────────

    private static BufferedImage loadTexture(Identifier id, int size) {
        if (id.path.equals(MISSING_TEXTURE_PATH)) {
            return createMissingTexture(size);
        }

        try (InputStream stream = ResourceManager.getResourceStream(id, "textures/blocks", "png")) {
            if (stream != null) {
                BufferedImage img = ImageIO.read(stream);
                if (img != null) {
                    if (img.getWidth() != size || img.getHeight() != size) {
                        LOGGER.debug("Resizing {} from {}x{} to {}x{}",
                                id, img.getWidth(), img.getHeight(), size, size);
                        return resizeImage(img, size);
                    }
                    // Ensure ARGB format so getRGB() works consistently
                    return ensureARGB(img);
                }
            }
        } catch (Exception e) {
            LOGGER.warn("Failed to load texture {}: {}", id, e.getMessage());
        }

        LOGGER.warn("Texture not found: {} – using missing texture placeholder", id);
        return createMissingTexture(size);
    }

    /** Magenta/black checkerboard — immediately recognisable as missing. */
    private static BufferedImage createMissingTexture(int size) {
        BufferedImage img = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        int half = Math.max(1, size / 2);
        g.setColor(Color.MAGENTA); g.fillRect(0,    0,    half, half);
        g.setColor(Color.BLACK);   g.fillRect(half, 0,    half, half);
        g.setColor(Color.BLACK);   g.fillRect(0,    half, half, half);
        g.setColor(Color.MAGENTA); g.fillRect(half, half, half, half);
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

    public static Builder builder() { return new Builder(); }

    public static class Builder {
        private final Set<Identifier> ids = new HashSet<>();
        private int tileSize = DEFAULT_TILE_SIZE;

        public Builder addTexture(Identifier id)                    { ids.add(id); return this; }
        public Builder addTextures(Collection<Identifier> texIds)   { ids.addAll(texIds); return this; }
        public Builder addTexturesFromBlocks(Collection<Block> blks){ ids.addAll(collectTextureIds(blks)); return this; }

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