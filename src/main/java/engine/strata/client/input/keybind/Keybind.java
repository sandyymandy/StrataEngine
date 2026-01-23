package engine.strata.client.input.keybind;

import engine.strata.client.StrataClient;
import engine.strata.client.input.InputState;
import engine.strata.client.input.InputSystem;
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

        InputSystem.register(this);
    }

    public void onPress() {
        isDown = true;
    }

    public void onRelease() {
        isDown = false;
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

    public boolean isIdle(){
        return state.equals(InputState.IDLE);
    }

    public boolean isInitiated(){
        return state.equals(InputState.INITIATED);
    }

    public boolean isActive(){
        return state.equals(InputState.ACTIVE);
    }

    public boolean isCanceled(){
        return state.equals(InputState.CANCELED);
    }
}
