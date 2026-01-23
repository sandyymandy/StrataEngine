package engine.strata.client.input.keybind;

import engine.strata.util.Identifier;
import org.lwjgl.glfw.GLFW;

public class Keybinds {
    public static final Keybind HIDE_CURSOR = new Keybind(
            Identifier.of("strata", "hide_cursor"),
            GLFW.GLFW_KEY_F
    );

    public static final Keybind FORWARDS = new Keybind(
            Identifier.of("strata", "forwards"),
            GLFW.GLFW_KEY_W
    );

    public static final Keybind BACKWARDS = new Keybind(
            Identifier.of("strata", "backwards"),
            GLFW.GLFW_KEY_S
    );

    public static final Keybind LEFT = new Keybind(
            Identifier.of("strata", "left"),
            GLFW.GLFW_KEY_A
    );

    public static final Keybind RIGHT = new Keybind(
            Identifier.of("strata", "right"),
            GLFW.GLFW_KEY_D
    );

    public static final Keybind UP = new Keybind(
            Identifier.of("strata", "up"),
            GLFW.GLFW_KEY_E
    );

    public static final Keybind DOWN = new Keybind(
            Identifier.of("strata", "down"),
            GLFW.GLFW_KEY_Q
    );
}
