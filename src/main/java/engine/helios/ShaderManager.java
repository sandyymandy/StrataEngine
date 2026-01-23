package engine.helios;

import engine.strata.util.Identifier;
import java.util.HashMap;
import java.util.Map;

public class ShaderManager {
    private static final Map<String, Shader> SHADERS = new HashMap<>();
    private static Shader currentShader;

    public static void register(String name, Identifier vert, Identifier frag) {
        SHADERS.put(name, new Shader(vert, frag));
    }

    public static void use(String name) {
        Shader s = SHADERS.get(name);
        if (s != null && s != currentShader) {
            s.use();
            currentShader = s;
        }
    }

    public static Shader getCurrent() {
        return currentShader;
    }
}