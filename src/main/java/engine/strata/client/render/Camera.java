package engine.strata.client.render;

import engine.helios.RenderSystem;
import engine.strata.client.input.keybind.Keybind;
import engine.strata.client.input.keybind.Keybinds;
import engine.strata.client.window.Window;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.lwjgl.glfw.GLFW;

public class Camera {
    private final Vector3f position = new Vector3f(0, 0, 0);
    private float pitch = 0;
    private float yaw = -90f;

    // Mouse tracking variables
    private double lastMouseX, lastMouseY;
    private boolean firstMouse = true;
    private float sensitivity = 0.15f; // Reduced for smoother control

    // Matrices
    private final Matrix4f viewMatrix = new Matrix4f();
    private final Matrix4f projectionMatrix = new Matrix4f();

    public void move(Window window) {
        handleKeyboard(window);
        handleMouse(window);

        // Update matrices every frame
        updateMatrices(window);
    }

    private void handleKeyboard(Window window) {
        long handle = window.getHandle();
        float speed = 0.002f;

        // Calculate forward/right vectors based on yaw so we move where we look
        float dx = (float) (Math.sin(Math.toRadians(yaw)) * speed);
        float dz = (float) (Math.cos(Math.toRadians(yaw)) * speed);

        if (Keybinds.FORWARDS.isActive()) {
            position.x += dz;
            position.z += dx;
        }
        if (Keybinds.BACKWARDS.isActive()) {
            position.x -= dz;
            position.z -= dx;
        }
        if (Keybinds.LEFT.isActive()) {
            position.x += dx;
            position.z -= dz;
        }
        if (Keybinds.RIGHT.isActive()) {
            position.x -= dx;
            position.z += dz;
        }

        // Vertical movement remains global (standard for flight/creative cams)
        if (Keybinds.DOWN.isActive()) position.y -= speed;
        if (Keybinds.UP.isActive()) position.y += speed;
    }

    private void handleMouse(Window window) {
        double x = window.getMouseX();
        double y = window.getMouseY();

        if (firstMouse) {
            lastMouseX = x;
            lastMouseY = y;
            firstMouse = false;
        }

        float offsetX = (float) (x - lastMouseX);
        float offsetY = (float) (lastMouseY - y);
        lastMouseX = x;
        lastMouseY = y;

        yaw += offsetX * sensitivity;
        pitch -= offsetY * sensitivity; // Adjusted sign for standard feel

        if (pitch > 89.0f) pitch = 89.0f;
        if (pitch < -89.0f) pitch = -89.0f;
    }

    private void updateMatrices(Window window) {
        // 1. Projection Matrix (Perspective)
        float fov = (float) Math.toRadians(70.0f);
        float aspectRatio = (float) window.getWidth() / window.getHeight();
        projectionMatrix.setPerspective(fov, aspectRatio, 0.01f, 1000.0f);

        // 2. View Matrix (The "Camera" transform)
        // We rotate the world the opposite way of the camera
        viewMatrix.identity()
                .rotate((float) Math.toRadians(pitch), 1, 0, 0)
                .rotate((float) Math.toRadians(yaw + 90), 0, 1, 0) // +90 to align forward with -Z
                .translate(-position.x, -position.y, -position.z);
    }

    public Matrix4f getViewMatrix() { return viewMatrix; }
    public Matrix4f getProjectionMatrix() { return projectionMatrix; }
    public Vector3f getPosition() { return position; }
}