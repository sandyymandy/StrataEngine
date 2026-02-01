package engine.strata.client.render.model;

import engine.strata.util.Identifier;

import java.util.Map;

/**
 * Represents a skin (texture mapping) for a StrataModel.
 * Maps texture slots to actual texture files with metadata.
 */
public record StrataSkin(
        Identifier id,
        int formatVersion,
        Map<String, TextureData> textures
) {
    /**
     * Texture data including path, dimensions, and rendering hints.
     */
    public record TextureData(
            Identifier path,
            int width,
            int height,
            boolean translucent,  // Whether this texture layer needs transparency
            int renderPriority    // Higher priority renders last (for overlays)
    ) {
        // Constructor with defaults for backwards compatibility
        public TextureData(Identifier path, int width, int height) {
            this(path, width, height, false, 0);
        }
    }

    /**
     * Gets texture data for a specific slot, or null if not found.
     */
    public TextureData getTexture(String slot) {
        return textures.get(slot);
    }

    /**
     * Checks if this skin has a texture for the given slot.
     */
    public boolean hasTexture(String slot) {
        return textures.containsKey(slot);
    }
}