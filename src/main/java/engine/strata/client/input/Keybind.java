package engine.strata.client.input;

import engine.strata.util.Identifier;

public final class Keybind {

    public final Identifier id;
    public final int key; // GLFW_KEY_*
    private InputState state = InputState.IDLE;

    // Internal tracking
    private boolean isDown = false;

    public Keybind(Identifier id, int key) {
        this.id = id;
        this.key = key;
    }

    void onPress() {
        isDown = true;
    }

    void onRelease() {
        isDown = false;
    }

    void update() {
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

    public InputState getState() {
        return state;
    }
}
