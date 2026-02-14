package engine.strata.world.block;

import engine.helios.Texture;
import engine.helios.TextureManager;
import engine.strata.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Manages the block texture atlas.
 * A texture atlas combines multiple block textures into a single large texture
 * to avoid binding different textures while rendering chunks.
 */
public class TextureAtlas {
    private static final Logger LOGGER = LoggerFactory.getLogger("TextureAtlas");

    // Atlas configuration
    private final int atlasWidth;   // Number of tiles horizontally
    private final int atlasHeight;  // Number of tiles vertically
    private final int tileSize;     // Size of each tile in pixels

    private final Texture atlasTexture;

    /**
     * Creates a texture atlas.
     *
     * @param atlasId The identifier for the atlas texture (e.g., "blocks/atlas")
     * @param atlasWidth Number of tiles wide (e.g., 16 for a 16x16 grid)
     * @param atlasHeight Number of tiles tall
     * @param tileSize Size of each tile in pixels (e.g., 16 for 16x16 pixel tiles)
     */
    public TextureAtlas(Identifier atlasId, int atlasWidth, int atlasHeight, int tileSize) {
        this.atlasWidth = atlasWidth;
        this.atlasHeight = atlasHeight;
        this.tileSize = tileSize;

        this.atlasTexture = TextureManager.get(atlasId);

        LOGGER.info("Loaded texture atlas: {} ({}x{} tiles, {}px per tile)",
                atlasId, atlasWidth, atlasHeight, tileSize);
    }

    /**
     * Gets the UV coordinates for a texture at a specific atlas index.
     * Atlas indices go left-to-right, top-to-bottom (0, 1, 2... starting at top-left).
     *
     * @param textureIndex The index of the texture in the atlas
     * @return UV coordinates [minU, minV, maxU, maxV]
     */
    public float[] getUVs(int textureIndex) {
        // Calculate tile position in atlas
        int tileX = textureIndex % atlasWidth;
        int tileY = textureIndex / atlasWidth;

        // Calculate UV coordinates (0.0 to 1.0 range)
        float tileWidth = 1.0f / atlasWidth;
        float tileHeight = 1.0f / atlasHeight;

        float minU = tileX * tileWidth;
        float minV = tileY * tileHeight;
        float maxU = minU + tileWidth;
        float maxV = minV + tileHeight;

        return new float[] { minU, minV, maxU, maxV };
    }

    /**
     * Gets the UV coordinates for a specific corner of a texture.
     *
     * @param textureIndex The index of the texture in the atlas
     * @param u U coordinate (0 = left, 1 = right)
     * @param v V coordinate (0 = top, 1 = bottom)
     * @return [u, v] coordinates
     */
    public float[] getUV(int textureIndex, float u, float v) {
        float[] uvs = getUVs(textureIndex);

        // Interpolate between min and max
        float finalU = uvs[0] + (uvs[2] - uvs[0]) * u;
        float finalV = uvs[1] + (uvs[3] - uvs[1]) * v;

        return new float[] { finalU, finalV };
    }

    public Texture getTexture() {
        return atlasTexture;
    }

    public int getAtlasWidth() {
        return atlasWidth;
    }

    public int getAtlasHeight() {
        return atlasHeight;
    }

    public int getTileSize() {
        return tileSize;
    }
}