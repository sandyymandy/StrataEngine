package engine.strata.client.input;

import engine.strata.client.input.keybind.Keybind;
import org.lwjgl.glfw.GLFW;

import java.util.HashMap;
import java.util.Map;

public final class InputSystem {

    private static final Map<Integer, Keybind> KEYBINDS = new HashMap<>();

    public static void register(Keybind keybind) {
        KEYBINDS.put(keybind.key, keybind);
    }

    public static void handleKeyEvent(int key, int action) {
        Keybind bind = KEYBINDS.get(key);
        if (bind == null) return;

        if (action == GLFW.GLFW_PRESS) {
            bind.onPress();
        } else if (action == GLFW.GLFW_RELEASE) {
            bind.onRelease();
        }
    }

    public static void update() {
        for (Keybind bind : KEYBINDS.values()) {
            bind.update();
        }
    }
}

