package engine.strata.client.input;

import engine.strata.client.input.keybind.Keybind;
import engine.strata.client.input.keybind.Keybinds;
import engine.strata.event.events.KeyEvent;
import engine.strata.event.events.MouseEvent;
import engine.strata.event.events.MouseScrollEvent;
import org.lwjgl.glfw.GLFW;
import java.util.HashMap;
import java.util.Map;

public final class InputSystem {
    public static final int MOUSE_OFFSET = 500;
    public static final int SCROLL_UP = 600;
    public static final int SCROLL_DOWN = 601;

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

    public static int ofMouse(int id) {return MOUSE_OFFSET + id;}

    /**
     * Called by the EventBus during the Logic Tick.
     */
    public void handleKeyEvent(KeyEvent event) {
        processInput(event.key(), event.action());
    }

    public void handleMouseEvent(MouseEvent event) {
        processInput(MOUSE_OFFSET + event.button(), event.action());
    }

    public void handleScrollEvent(MouseScrollEvent event) {
        int scrollKey = event.yOffset() > 0 ? SCROLL_UP : SCROLL_DOWN;
        Keybind bind = keybinds.get(scrollKey);
        if (bind != null) {
            // "Pulse" the input: press and release in the same tick
            bind.setPressed(true);
            bind.setPressed(false);
        }
    }

    private void processInput(int keyId, int action) {
        Keybind bind = keybinds.get(keyId);
        if (bind == null) return;

        if (action == GLFW.GLFW_PRESS) {
            bind.setPressed(true);
        } else if (action == GLFW.GLFW_RELEASE) {
            bind.setPressed(false);
        }
    }

    public void update() {
        for (Keybind bind : keybinds.values()) {
            bind.update();
        }
    }

    public void tick() {
        for (Keybind bind : keybinds.values()) {
            bind.tick();
        }
    }
}