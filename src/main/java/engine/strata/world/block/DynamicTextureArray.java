package engine.strata.world.block;

import engine.strata.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL12.GL_TEXTURE_WRAP_R;
import static org.lwjgl.opengl.GL30.*;

/**
 * GL_TEXTURE_2D_ARRAY that maps texture identifiers to array layer indices.
 *
 * Each texture occupies its own slice of the array — there is no atlas grid,
 * no UV sub-division, and no tile-bleeding at mipmap boundaries.
 *
 * UV coordinates for every face are always the full [0,0] → [1,1] square.
 * The layer index is passed as a separate vertex attribute and used in the
 * fragment shader via sampler2DArray / texture(u_Texture, vec3(u, v, layer)).
 */
public class DynamicTextureArray {
    private static final Logger LOGGER = LoggerFactory.getLogger("DynamicTextureArray");

    private final int textureId;   // OpenGL object name
    private final int tileSize;    // Width & height of every slice in pixels
    private final int layerCount;  // Total slices allocated in the array

    /** identifier → layer index (0-based) */
    private final Map<Identifier, Integer> layerMap;

    public DynamicTextureArray(int textureId, int tileSize, int layerCount,
                               Map<Identifier, Integer> layerMap) {
        this.textureId  = textureId;
        this.tileSize   = tileSize;
        this.layerCount = layerCount;
        this.layerMap   = new HashMap<>(layerMap);

        LOGGER.info("Created DynamicTextureArray: {} layers, {}x{} px each, GL id={}",
                layerCount, tileSize, tileSize, textureId);
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

    // ── Binding ───────────────────────────────────────────────────────────────

    public void bind() {
        glBindTexture(GL_TEXTURE_2D_ARRAY, textureId);
    }

    public void unbind() {
        glBindTexture(GL_TEXTURE_2D_ARRAY, 0);
    }

    public void setFilterMode(int minFilter, int magFilter) {
        bind();
        glTexParameteri(GL_TEXTURE_2D_ARRAY, GL_TEXTURE_MIN_FILTER, minFilter);
        glTexParameteri(GL_TEXTURE_2D_ARRAY, GL_TEXTURE_MAG_FILTER, magFilter);
        unbind();
    }

    /**
     * Sets wrap mode. Always clamps R (the layer axis) to edge regardless of
     * wrapS / wrapT, since interpolating between layers makes no sense.
     */
    public void setWrapMode(int wrapS, int wrapT) {
        bind();
        glTexParameteri(GL_TEXTURE_2D_ARRAY, GL_TEXTURE_WRAP_S, wrapS);
        glTexParameteri(GL_TEXTURE_2D_ARRAY, GL_TEXTURE_WRAP_T, wrapT);
        glTexParameteri(GL_TEXTURE_2D_ARRAY, GL_TEXTURE_WRAP_R, GL_CLAMP_TO_EDGE);
        unbind();
    }

    public void generateMipmaps() {
        bind();
        glGenerateMipmap(GL_TEXTURE_2D_ARRAY);
        unbind();
        LOGGER.debug("Regenerated mipmaps for texture array");
    }

    // ── Disposal ──────────────────────────────────────────────────────────────

    public void delete() {
        glDeleteTextures(textureId);
        LOGGER.info("Deleted texture array (GL id: {})", textureId);
    }

    // ── Getters ───────────────────────────────────────────────────────────────

    public int getTextureId()     { return textureId; }
    public int getTileSize()      { return tileSize; }
    public int getLayerCount()    { return layerCount; }
    public int getTotalTextures() { return layerMap.size(); }

    @Override
    public String toString() {
        return String.format("DynamicTextureArray{layers=%d, size=%dpx, GL id=%d}",
                layerCount, tileSize, textureId);
    }
}