package engine.strata.client.input;

import engine.strata.client.input.keybind.Keybind;
import engine.strata.client.input.keybind.Keybinds;
import engine.strata.event.events.KeyEvent;
import org.lwjgl.glfw.GLFW;
import java.util.HashMap;
import java.util.Map;

public final class InputSystem {
    private final Map<Integer, Keybind> keybinds = new HashMap<>();

    public InputSystem() {
        // Automatically pull every keybind defined in Keybinds.java
        for (Keybind bind : Keybinds.getRegisteredBinds()) {
            this.register(bind);
        }
    }

    public void register(Keybind keybind) {
        keybinds.put(keybind.key, keybind);
    }

    /**
     * Called by the EventBus during the Logic Tick.
     */
    public void handleKeyEvent(KeyEvent event) {
        Keybind bind = keybinds.get(event.key());
        if (bind == null) return;

        if (event.action() == GLFW.GLFW_PRESS) {
            bind.setPressed(true);
        } else if (event.action() == GLFW.GLFW_RELEASE) {
            bind.setPressed(false);
        }
    }

    public void update() {
        for (Keybind bind : keybinds.values()) {
            bind.update();
        }
    }
}