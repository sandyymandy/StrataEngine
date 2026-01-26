package engine.helios;

import engine.strata.util.Identifier;
import java.util.HashMap;
import java.util.Map;

public class ShaderManager {
    private static final Map<Identifier, ShaderStack> SHADERS = new HashMap<>();
    private static ShaderStack currentShaderStack;

    public static void register(Identifier id, Identifier vert, Identifier frag) {
        SHADERS.put(id, new ShaderStack(vert, frag));
    }

    public static void use(Identifier id) {
        ShaderStack s = SHADERS.get(id);
        if (s != null && s != currentShaderStack) {
            s.use();
            currentShaderStack = s;
        }
    }

    public static ShaderStack getCurrent() {
        return currentShaderStack;
    }
}