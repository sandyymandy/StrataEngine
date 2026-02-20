package engine.strata.client.input.keybind;

import engine.strata.client.input.InputState;
import engine.strata.util.Identifier;

public final class Keybind {
    public final Identifier id;
    public final int key;

    private boolean currentlyPressed = false;

    // Frame-based tracking
    private boolean wasPressedFrame = false;
    private InputState frameState = InputState.IDLE;

    // Tick-based tracking
    private InputState tickState = InputState.IDLE;
    private boolean wasPressedTick = false;
    private boolean pressedSinceLastTick = false;
    private boolean releasedSinceLastTick = false;


    public Keybind(Identifier id, int key) {
        this.id = id;
        this.key = key;
        Keybinds.registerInternal(this);
    }

    public void setPressed(boolean pressed) {
        if (pressed && !this.currentlyPressed) {
            this.pressedSinceLastTick = true;
        } else if (!pressed && this.currentlyPressed) {
            this.releasedSinceLastTick = true;
        }
        this.currentlyPressed = pressed;
    }

    /**
     * PER FRAME: Keeps latency at 0ms for visuals.
     */
    public void update() {
        if (currentlyPressed) {
            frameState = wasPressedFrame ? InputState.HELD : InputState.JUST_PRESSED;
        } else {
            frameState = wasPressedFrame ? InputState.JUST_RELEASED : InputState.IDLE;
        }
        wasPressedFrame = currentlyPressed;
    }

    /**
     * PER TICK: Guaranteed state for physics/logic.
     */
    public void tick() {
        if (pressedSinceLastTick) {
            tickState = InputState.JUST_PRESSED;
            wasPressedTick = true;
        } else if (releasedSinceLastTick) {
            tickState = InputState.JUST_RELEASED;
            wasPressedTick = false;
        } else if (currentlyPressed) {
            // FIX: If the hardware says it's down, it's HELD,
            // regardless of whether an event fired this exact tick.
            tickState = wasPressedTick ? InputState.HELD : InputState.JUST_PRESSED;
            wasPressedTick = true;
        } else {
            tickState = wasPressedTick ? InputState.JUST_RELEASED : InputState.IDLE;
            wasPressedTick = false;
        }

        // Reset buffers
        pressedSinceLastTick = false;
        releasedSinceLastTick = false;
    }

    // --- Frame Queries ---
    public boolean isPressedFrame() { return frameState == InputState.JUST_PRESSED || frameState == InputState.HELD; }
    public boolean isJustPressedFrame() { return frameState == InputState.JUST_PRESSED; }
    public boolean isJustReleasedFrame() { return frameState == InputState.JUST_RELEASED; }

    // --- Tick Queries ---
    public boolean isPressedTick() { return tickState == InputState.JUST_PRESSED || tickState == InputState.HELD; }
    public boolean isJustPressedTick() { return tickState == InputState.JUST_PRESSED; }
    public boolean isJustReleasedTick() { return tickState == InputState.JUST_RELEASED; }
    public boolean isHeldTick() { return tickState == InputState.HELD; }
}