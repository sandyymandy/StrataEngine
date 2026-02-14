package engine.strata.world.block;

import engine.strata.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL30.glGenerateMipmap;

/**
 * Dynamically generated texture atlas that maps texture identifiers to atlas indices.
 * Provides UV coordinate calculation and texture binding.
 *
 * This class wraps the GPU texture and provides mapping between:
 * - Texture identifiers (e.g., "strata:stone") -> Atlas indices (0, 1, 2...)
 * - Atlas indices -> UV coordinates for rendering
 */
public class DynamicTextureAtlas {
    private static final Logger LOGGER = LoggerFactory.getLogger("DynamicAtlas");

    private final int textureId;
    private final int atlasWidth;   // Number of tiles horizontally
    private final int atlasHeight;  // Number of tiles vertically
    private final int tileSize;     // Size of each tile in pixels

    // Mapping from texture identifier to atlas index
    private final Map<Identifier, Integer> textureIndexMap;

    /**
     * Creates a dynamic texture atlas.
     *
     * @param textureId OpenGL texture ID
     * @param atlasWidth Number of tiles horizontally
     * @param atlasHeight Number of tiles vertically
     * @param tileSize Size of each tile in pixels
     * @param textureIndexMap Mapping from texture identifiers to atlas indices
     */
    public DynamicTextureAtlas(int textureId, int atlasWidth, int atlasHeight, int tileSize,
                               Map<Identifier, Integer> textureIndexMap) {
        this.textureId = textureId;
        this.atlasWidth = atlasWidth;
        this.atlasHeight = atlasHeight;
        this.tileSize = tileSize;
        this.textureIndexMap = new HashMap<>(textureIndexMap);

        LOGGER.info("Created dynamic texture atlas: {}x{} tiles ({}x{} pixels), {} textures",
                atlasWidth, atlasHeight, atlasWidth * tileSize, atlasHeight * tileSize,
                textureIndexMap.size());
    }

    /**
     * Gets the atlas index for a texture identifier.
     * Returns 0 if the texture is not found (will use first texture as fallback).
     *
     * @param textureId The texture identifier (e.g., "strata:stone")
     * @return The atlas index for this texture
     */
    public int getTextureIndex(Identifier textureId) {
        Integer index = textureIndexMap.get(textureId);
        if (index == null) {
            LOGGER.warn("Texture not found in atlas: {} - using index 0", textureId);
            return 0;
        }
        return index;
    }

    /**
     * Checks if the atlas contains a specific texture.
     *
     * @param textureId The texture identifier to check
     * @return true if the atlas contains this texture
     */
    public boolean hasTexture(Identifier textureId) {
        return textureIndexMap.containsKey(textureId);
    }

    /**
     * Gets all texture identifiers in this atlas.
     *
     * @return Unmodifiable set of texture identifiers
     */
    public Set<Identifier> getTextureIds() {
        return Collections.unmodifiableSet(textureIndexMap.keySet());
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
     * Gets the UV coordinates for a texture identifier.
     *
     * @param textureId The texture identifier
     * @return UV coordinates [minU, minV, maxU, maxV]
     */
    public float[] getUVs(Identifier textureId) {
        int index = getTextureIndex(textureId);
        return getUVs(index);
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

    /**
     * Gets the UV coordinates for a specific corner of a texture by identifier.
     *
     * @param textureId The texture identifier
     * @param u U coordinate (0 = left, 1 = right)
     * @param v V coordinate (0 = top, 1 = bottom)
     * @return [u, v] coordinates
     */
    public float[] getUV(Identifier textureId, float u, float v) {
        int index = getTextureIndex(textureId);
        return getUV(index, u, v);
    }

    /**
     * Binds this atlas texture for rendering.
     */
    public void bind() {
        glBindTexture(GL_TEXTURE_2D, textureId);
    }

    /**
     * Unbinds the texture.
     */
    public void unbind() {
        glBindTexture(GL_TEXTURE_2D, 0);
    }

    /**
     * Sets texture filtering parameters.
     *
     * @param minFilter GL_NEAREST, GL_LINEAR, GL_NEAREST_MIPMAP_LINEAR, etc.
     * @param magFilter GL_NEAREST or GL_LINEAR
     */
    public void setFilterMode(int minFilter, int magFilter) {
        bind();
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, minFilter);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, magFilter);
        unbind();
    }

    /**
     * Sets texture wrapping parameters.
     *
     * @param wrapS GL_REPEAT, GL_CLAMP_TO_EDGE, GL_MIRRORED_REPEAT, etc.
     * @param wrapT GL_REPEAT, GL_CLAMP_TO_EDGE, GL_MIRRORED_REPEAT, etc.
     */
    public void setWrapMode(int wrapS, int wrapT) {
        bind();
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, wrapS);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, wrapT);
        unbind();
    }

    /**
     * Regenerates mipmaps for the atlas.
     */
    public void generateMipmaps() {
        bind();
        glGenerateMipmap(GL_TEXTURE_2D);
        unbind();
        LOGGER.debug("Regenerated mipmaps for texture atlas");
    }

    public int getTextureId() {
        return textureId;
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

    public int getTotalTextures() {
        return textureIndexMap.size();
    }

    public int getMaxCapacity() {
        return atlasWidth * atlasHeight;
    }

    /**
     * Gets the texture index mapping (unmodifiable).
     *
     * @return Map from texture identifiers to atlas indices
     */
    public Map<Identifier, Integer> getTextureIndexMap() {
        return Collections.unmodifiableMap(textureIndexMap);
    }

    /**
     * Deletes the texture from GPU.
     * Should be called when disposing of the atlas.
     */
    public void delete() {
        glDeleteTextures(textureId);
        LOGGER.info("Deleted texture atlas (GPU ID: {})", textureId);
    }

    @Override
    public String toString() {
        return String.format("DynamicTextureAtlas{%dx%d tiles, %d textures, GPU ID: %d}",
                atlasWidth, atlasHeight, textureIndexMap.size(), textureId);
    }
}