package engine.strata.client.input.keybind;

import engine.strata.client.input.InputState;
import engine.strata.util.Identifier;

public final class Keybind {

    public final Identifier id;
    public final int key; // GLFW_KEY_*
    private InputState state = InputState.IDLE;
    private boolean isDown = false;

    public Keybind(Identifier id, int key) {
        this.id = id;
        this.key = key;
        Keybinds.registerInternal(this);
    }

    public void setPressed(boolean pressed) {
        this.isDown = pressed;
    }

    public void update() {
        switch (state) {
            case IDLE -> {
                if (isDown) state = InputState.INITIATED;
            }
            case INITIATED -> state = isDown ? InputState.ACTIVE : InputState.CANCELED;
            case ACTIVE -> {
                if (!isDown) state = InputState.CANCELED;
            }
            case CANCELED -> state = isDown ? InputState.INITIATED : InputState.IDLE;
        }
    }

    // Getters
    public boolean isIdle() { return state == InputState.IDLE; }
    public boolean isInitiated() { return state == InputState.INITIATED; }
    public boolean isActive() { return state == InputState.ACTIVE; }
    public boolean isCanceled() { return state == InputState.CANCELED; }
}