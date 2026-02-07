package engine.strata.client.window;

import engine.strata.client.StrataClient;
import engine.strata.client.input.InputSystem;
import org.lwjgl.glfw.GLFWVidMode;
import org.lwjgl.opengl.GL;

import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL20C.GL_SHADING_LANGUAGE_VERSION;

public class Window {

    private final long handle;
    private final WindowConfig config;
    private int width, height;
    private String renderPhase = "";

    public Window(WindowConfig config) {
        this.config = config;

        if (!glfwInit()) {
            throw new IllegalStateException("Unable to initialize GLFW");
        }

        glfwDefaultWindowHints();
        glfwWindowHint(GLFW_VISIBLE, GLFW_FALSE);
        glfwWindowHint(GLFW_RESIZABLE, GLFW_TRUE    );

        glfwWindowHint(GLFW_CONTEXT_VERSION_MAJOR, 3);
        glfwWindowHint(GLFW_CONTEXT_VERSION_MINOR, 3);
        glfwWindowHint(GLFW_OPENGL_PROFILE, GLFW_OPENGL_CORE_PROFILE);
        glfwWindowHint(GLFW_OPENGL_FORWARD_COMPAT, GLFW_TRUE);

        this.handle = glfwCreateWindow(
                config.width,
                config.height,
                config.title,
                0,
                0
        );

        if (handle == 0) {
            throw new IllegalStateException("Failed to create GLFW window");
        }

        this.width = config.width;
        this.height = config.height;

        glfwSetFramebufferSizeCallback(handle, (window, width, height) -> {
            glViewport(0, 0, width, height);
            this.width = width;
            this.height = height;
        });

        glfwSetKeyCallback(handle, ((window, key, scancode, action, mods) -> {
            InputSystem.handleKeyEvent(key, action);
        }));

        glfwSetWindowPos(handle, config.x, config.y);
        glfwMakeContextCurrent(handle);
        glfwShowWindow(handle);
        GL.createCapabilities();
        StrataClient.LOGGER.info("OpenGL: " + glGetString(GL_VERSION));
        StrataClient.LOGGER.info("GLSL: " + glGetString(GL_SHADING_LANGUAGE_VERSION));

        applyMode(config.mode);
    }

    public void setTitle(String title) {
        glfwSetWindowTitle(handle, title);
    }

    public long getHandle() {
        return handle;
    }

    public WindowConfig getConfig() {
        return config;
    }

    public int getHeight() {
        return height;
    }

    public int getWidth() {
        return width;
    }

    /* =========================
       Window Mode Switching
       ========================= */

    public void setMode(WindowMode mode) {
        if (this.config.mode == mode) return;
        this.config.mode = mode;
        applyMode(mode);
    }

    private void applyMode(WindowMode mode) {
        switch (mode) {
            case WINDOWED -> applyWindowed();
            case BORDERLESS_FULLSCREEN -> applyBorderless();
            case EXCLUSIVE_FULLSCREEN -> applyExclusive();
        }
    }

    /* =========================
       Mode Implementations
       ========================= */

    private void applyWindowed() {
        glfwSetWindowAttrib(handle, GLFW_DECORATED, GLFW_TRUE);
        glfwSetWindowAttrib(handle, GLFW_RESIZABLE, GLFW_TRUE);

        glfwSetWindowMonitor(
                handle,
                0,
                config.x,
                config.y,
                config.width,
                config.height,
                GLFW_DONT_CARE
        );
    }

    private void applyBorderless() {
        long monitor = glfwGetPrimaryMonitor();
        GLFWVidMode mode = glfwGetVideoMode(monitor);

        glfwSetWindowAttrib(handle, GLFW_DECORATED, GLFW_FALSE);
        glfwSetWindowAttrib(handle, GLFW_RESIZABLE, GLFW_FALSE);

        glfwSetWindowMonitor(
                handle,
                0,
                0,
                0,
                mode.width(),
                mode.height(),
                GLFW_DONT_CARE
        );
    }

    private void applyExclusive() {
        long monitor = glfwGetPrimaryMonitor();
        GLFWVidMode mode = glfwGetVideoMode(monitor);

        glfwSetWindowAttrib(handle, GLFW_DECORATED, GLFW_FALSE);
        glfwSetWindowAttrib(handle, GLFW_RESIZABLE, GLFW_FALSE);

        glfwSetWindowMonitor(
                handle,
                monitor,
                0,
                0,
                mode.width(),
                mode.height(),
                mode.refreshRate()
        );
    }

    /* =========================
       Resize Handling
       ========================= */

    public void pollResize() {
        if (config.mode != WindowMode.WINDOWED) return;

        int[] w = new int[1];
        int[] h = new int[1];
        glfwGetWindowSize(handle, w, h);

        if (w[0] != config.width || h[0] != config.height) {
            config.width = w[0];
            config.height = h[0];
        }

        int[] x = new int[1];
        int[] y = new int[1];
        glfwGetWindowPos(handle, x, y);
        config.x = x[0];
        config.y = y[0];
    }

    public void pollEvents(){
        glfwPollEvents();
    }

    public boolean shouldClose() {
        return glfwWindowShouldClose(handle);
    }

    public void destroy() {
        glfwDestroyWindow(handle);
    }

    public void swapBuffers() {
        glfwSwapBuffers(handle);
    }

    public void lockCursor() {
        glfwSetInputMode(handle, GLFW_CURSOR, GLFW_CURSOR_DISABLED);
    }

    public void unlockCursor() {
        glfwSetInputMode(handle, GLFW_CURSOR, GLFW_CURSOR_NORMAL);
    }

    public double getMouseX() {
        double[] x = new double[1];
        double[] y = new double[1];
        org.lwjgl.glfw.GLFW.glfwGetCursorPos(handle, x, y);
        return x[0];
    }

    public double getMouseY() {
        double[] x = new double[1];
        double[] y = new double[1];
        org.lwjgl.glfw.GLFW.glfwGetCursorPos(handle, x, y);
        return y[0];
    }

    public void setRenderPhase(String renderPhase) {
        this.renderPhase = renderPhase;
    }
}
