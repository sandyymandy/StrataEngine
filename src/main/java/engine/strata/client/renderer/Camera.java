package engine.strata.client.renderer;

import org.joml.Vector3f;
import engine.strata.client.window.Window;
import org.lwjgl.glfw.GLFW;

public class Camera {
    private final Vector3f position = new Vector3f(0, 0, 0);
    private float pitch = 0;
    private float yaw = -90f;
    private float roll;

    // Mouse tracking variables
    private double lastMouseX, lastMouseY;
    private boolean firstMouse = true;
    private float sensitivity = 0.4f;

    public void move(Window window) {
        handleKeyboard(window);
        handleMouse(window);
    }

    private void handleKeyboard(Window window) {
        long handle = window.getHandle();
        float speed = 0.002f;

        // Calculate forward/right vectors based on yaw so we move where we look
        float dx = (float) (Math.sin(Math.toRadians(yaw)) * speed);
        float dz = (float) (Math.cos(Math.toRadians(yaw)) * speed);

        if (GLFW.glfwGetKey(handle, GLFW.GLFW_KEY_W) == GLFW.GLFW_PRESS) {
            position.x += dx;
            position.z -= dz;
        }
        if (GLFW.glfwGetKey(handle, GLFW.GLFW_KEY_S) == GLFW.GLFW_PRESS) {
            position.x -= dx;
            position.z += dz;
        }
        if (GLFW.glfwGetKey(handle, GLFW.GLFW_KEY_D) == GLFW.GLFW_PRESS) {
            position.x += dz;
            position.z += dx;
        }
        if (GLFW.glfwGetKey(handle, GLFW.GLFW_KEY_A) == GLFW.GLFW_PRESS) {
            position.x -= dz;
            position.z -= dx;
        }

        // Vertical movement remains global (standard for flight/creative cams)
        if (GLFW.glfwGetKey(handle, GLFW.GLFW_KEY_Q) == GLFW.GLFW_PRESS) position.y += speed;
        if (GLFW.glfwGetKey(handle, GLFW.GLFW_KEY_E) == GLFW.GLFW_PRESS) position.y -= speed;
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
        float offsetY = (float) (lastMouseY - y); // Y is inverted in window coords
        lastMouseX = x;
        lastMouseY = y;

        yaw += offsetX * sensitivity;
        pitch -= offsetY * sensitivity;

        // Clamp pitch so you can't flip your neck backwards
        if (pitch > 89.0f) pitch = 89.0f;
        if (pitch < -89.0f) pitch = -89.0f;
    }

    public Vector3f getPosition() { return position; }
    public float getPitch() { return pitch; }
    public float getYaw() { return yaw; }
    public float getRoll() { return roll; }
}