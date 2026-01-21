package engine.strata.client.input;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.lwjgl.glfw.GLFW.GLFW_PRESS;
import static org.lwjgl.glfw.GLFW.GLFW_RELEASE;

public final class InputSystem {

    private static final Map<Integer, List<Keybind>> KEYBINDS = new HashMap<>();

    public static void register(Keybind bind) {
        KEYBINDS
                .computeIfAbsent(bind.key, k -> new ArrayList<>())
                .add(bind);
    }

    static void handleKeyEvent(int key, int action) {
        List<Keybind> binds = KEYBINDS.get(key);
        if (binds == null) return;

        if (action == GLFW_PRESS) {
            binds.forEach(Keybind::onPress);
        } else if (action == GLFW_RELEASE) {
            binds.forEach(Keybind::onRelease);
        }
    }

    public static void update() {
        for (List<Keybind> binds : KEYBINDS.values()) {
            for (Keybind bind : binds) {
                bind.update();
            }
        }
    }
}
