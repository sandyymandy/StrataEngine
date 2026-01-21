package engine.strata.client;

import engine.strata.client.input.InputSystem;
import engine.strata.client.renderer.Camera;
import engine.strata.client.renderer.Loader;
import engine.strata.client.renderer.MasterRenderer;
import engine.strata.client.renderer.entity.RenderEntity;
import engine.strata.client.renderer.model.RawModel;
import engine.strata.client.window.Window;
import engine.strata.client.window.WindowConfig;
import engine.strata.client.window.WindowMode;
import org.joml.Vector3f;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

public class StrataClient {
    private Window window;
    private MasterRenderer renderer;
    private Camera camera;
    private Loader loader;
    private List<RenderEntity> allEntities = new ArrayList<>(); // Add this
    private boolean running = true;
    public static final Logger LOGGER = LoggerFactory.getLogger("Client");
    // Game Loop Constants
    private static final double TICKS_PER_SECOND = 20.0;
    private static final double TIME_PER_TICK = 1.0 / TICKS_PER_SECOND;

    public void init() {
        LOGGER.info("Initializing Client Graphics...");
        window = new Window(new WindowConfig(1280, 720, "StrataEngine"));
        loader = new Loader();
        loader = new Loader();
        renderer = new MasterRenderer(window);
        camera = new Camera();
        window.lockCursor();

        // Assign a different color to each face so we can see the 3D depth
        float[] colors = {
                1,0,0, 1,0,0, 1,0,0, 1,0,0, // Red face
                0,1,0, 0,1,0, 0,1,0, 0,1,0, // Green face
                0,0,1, 0,0,1, 0,0,1, 0,0,1, // Blue face
                1,1,0, 1,1,0, 1,1,0, 1,1,0, // Yellow face
                1,0,1, 1,0,1, 1,0,1, 1,0,1, // Magenta face
                0,1,1, 0,1,1, 0,1,1, 0,1,1  // Cyan face
        };

        // Positions: 8 corners of the cube
        float[] vertices = {
                -0.5f, 0.5f, 0.5f,  // 0: Front-Top-Left
                -0.5f,-0.5f, 0.5f,  // 1: Front-Bottom-Left
                0.5f,-0.5f, 0.5f,  // 2: Front-Bottom-Right
                0.5f, 0.5f, 0.5f,  // 3: Front-Top-Right
                -0.5f, 0.5f,-0.5f,  // 4: Back-Top-Left
                -0.5f,-0.5f,-0.5f,  // 5: Back-Bottom-Left
                0.5f,-0.5f,-0.5f,  // 6: Back-Bottom-Right
                0.5f, 0.5f,-0.5f   // 7: Back-Top-Right
        };

        // Indices: 12 triangles (2 per face), all CCW
        int[] indices = {
                // Front face
                0, 1, 3, 3, 1, 2,
                // Back face
                4, 7, 5, 5, 7, 6,
                // Left face
                4, 5, 0, 0, 5, 1,
                // Right face
                3, 2, 7, 7, 2, 6,
                // Top face
                4, 0, 7, 7, 0, 3,
                // Bottom face
                1, 5, 2, 2, 5, 6
        };

        RawModel cubeModel = loader.loadToVAO(vertices, colors, indices);

        allEntities.add(new RenderEntity(cubeModel, new Vector3f(0, 0, -5), 0, 0, 0, 1));
        allEntities.add(new RenderEntity(cubeModel, new Vector3f(2, 0, -5), 0, 0, 0, 1));
    }

    /**
     * The Main Game Loop (Accumulator Pattern)
     */
    public void run() {
        long lastTime = System.nanoTime();
        double accumulator = 0.0;

        while (running && !window.shouldClose()) {
            long now = System.nanoTime();
            // Convert nanoseconds to seconds
            double deltaTime = (now - lastTime) / 1_000_000_000.0;
            lastTime = now;

            accumulator += deltaTime;

            // 1. Input Processing (Variable)
            window.pollEvents(); // Use your Window class method

            // 2. Fixed Update (Logic) - Catch up if we are lagging
            while (accumulator >= TIME_PER_TICK) {
                tick();
                accumulator -= TIME_PER_TICK;
            }

            // 3. Render (Variable)
            // 'alpha' is how far we are between the last tick and the next tick (0.0 to 1.0)
            // Use this for interpolation!
            double alpha = accumulator / TIME_PER_TICK;
            render((float) alpha);

            // Optional: Cap FPS if needed, otherwise run unlimited
        }

        stop();
    }

    // Fixed Logic: Physics, AI, Network Packets
    private void tick() {
        InputSystem.update();
    }

    // Variable Render: Draw the scene
    private void render(float alpha) {
        // 1. Logic
        camera.move(window);

        // Spin the first entity
        allEntities.get(0).increaseRotation(0.01F, 0.01F, 0);

        // 2. Process
        for (RenderEntity e : allEntities) {
            renderer.processEntity(e);
        }

        // 3. Render
        renderer.render(camera);
        window.swapBuffers();

        if (GLFW.glfwGetKey(window.getHandle(), GLFW.GLFW_KEY_ESCAPE) == GLFW.GLFW_PRESS) window.unlockCursor();
        if (GLFW.glfwGetKey(window.getHandle(), GLFW.GLFW_KEY_F) == GLFW.GLFW_PRESS) window.lockCursor();

    }

    public void stop() {
        running = false;
        renderer.cleanUp();
        loader.cleanUp();
        window.destroy();
    }
}