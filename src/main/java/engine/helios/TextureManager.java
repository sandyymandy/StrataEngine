package engine.helios;

import engine.strata.util.Identifier;
import java.util.HashMap;
import java.util.Map;

public class TextureManager {
    private static final Map<Identifier, Texture> TEXTURES = new HashMap<>();

    public static Texture get(Identifier id) {
        return TEXTURES.computeIfAbsent(id, Texture::new);
    }
}