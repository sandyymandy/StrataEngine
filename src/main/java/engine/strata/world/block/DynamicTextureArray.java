package engine.strata.world.block;

import engine.helios.rendering.texture.TextureArray;
import engine.strata.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Dynamic texture array that maps texture identifiers to array layer indices.
 * Wraps Helios TextureArray for proper integration with the rendering engine.
 *
 * Each texture occupies its own slice of the array — there is no atlas grid,
 * no UV sub-division, and no tile-bleeding at mipmap boundaries.
 *
 * UV coordinates for every face are always the full [0,0] → [1,1] square.
 * The layer index is passed as a separate vertex attribute.
 */
public class DynamicTextureArray {
    private static final Logger LOGGER = LoggerFactory.getLogger("DynamicTextureArray");

    private final TextureArray textureArray;
    private final int tileSize;

    /** identifier → layer index (0-based) */
    private final Map<Identifier, Integer> layerMap;

    public DynamicTextureArray(TextureArray textureArray, int tileSize, Map<Identifier, Integer> layerMap) {
        this.textureArray = textureArray;
        this.tileSize = tileSize;
        this.layerMap = new HashMap<>(layerMap);

        LOGGER.info("Created DynamicTextureArray: {} layers, {}x{} px each, GL id={}",
                textureArray.getLayers(), tileSize, tileSize, textureArray.getId());
    }

    // ── Layer lookup ──────────────────────────────────────────────────────────

    /**
     * Returns the layer index for the given texture identifier.
     * Falls back to layer 0 (missing-texture slot) if not found.
     */
    public int getLayer(Identifier id) {
        Integer layer = layerMap.get(id);
        if (layer == null) {
            LOGGER.warn("Texture not found in array: {} – using layer 0", id);
            return 0;
        }
        return layer;
    }

    public boolean hasTexture(Identifier id) {
        return layerMap.containsKey(id);
    }

    public Set<Identifier> getTextureIds() {
        return Collections.unmodifiableSet(layerMap.keySet());
    }

    public Map<Identifier, Integer> getLayerMap() {
        return Collections.unmodifiableMap(layerMap);
    }

    // ── Texture array access ──────────────────────────────────────────────────

    /**
     * Gets the underlying Helios TextureArray.
     */
    public TextureArray getTextureArray() {
        return textureArray;
    }

    /**
     * Binds the texture array through Helios.
     */
    public void bind() {
        textureArray.bind();
    }

    /**
     * Unbinds the texture array.
     */
    public void unbind() {
        TextureArray.unbind();
    }

    /**
     * Sets filter mode through Helios.
     */
    public void setFilterMode(int minFilter, int magFilter) {
        textureArray.setFilterMode(minFilter, magFilter);
    }

    /**
     * Sets wrap mode through Helios.
     */
    public void setWrapMode(int wrapS, int wrapT) {
        textureArray.setWrapMode(wrapS, wrapT);
    }

    /**
     * Generates mipmaps through Helios.
     */
    public void generateMipmaps() {
        textureArray.generateMipmaps();
    }

    // ── Disposal ──────────────────────────────────────────────────────────────

    /**
     * Deletes the texture array through Helios.
     */
    public void delete() {
        textureArray.delete();
        LOGGER.info("Deleted texture array (GL id: {})", textureArray.getId());
    }

    // ── Getters ───────────────────────────────────────────────────────────────

    public int getTextureId() { return textureArray.getId(); }
    public int getTileSize() { return tileSize; }
    public int getLayerCount() { return textureArray.getLayers(); }
    public int getTotalTextures() { return layerMap.size(); }

    @Override
    public String toString() {
        return String.format("DynamicTextureArray{layers=%d, size=%dpx, GL id=%d}",
                getLayerCount(), tileSize, getTextureId());
    }
}