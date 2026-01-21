package engine.strata.client;

import engine.strata.client.renderer.Loader;
import engine.strata.client.renderer.StrataRenderer;
import engine.strata.client.renderer.model.RawModel;
import engine.strata.client.renderer.shader.StaticShader;
import engine.strata.client.window.Window;
import engine.strata.client.window.WindowConfig;
import engine.strata.client.window.WindowMode;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class StrataClient {
    private Window window;
    private StrataRenderer renderer; // Add this
    private boolean running = true;
    public static final Logger LOGGER = LoggerFactory.getLogger("Client");
    private Loader loader;
    private StaticShader shader;
    private RawModel model;
    // Game Loop Constants
    private static final double TICKS_PER_SECOND = 20.0;
    private static final double TIME_PER_TICK = 1.0 / TICKS_PER_SECOND;

    public void init() {
        LOGGER.info("Initializing Client Graphics...");
        window = new Window(new WindowConfig(1280, 720, "StrataEngine", WindowMode.BORDERLESS_FULLSCREEN));
        loader = new Loader();
        shader = new StaticShader();

        // 1. Define vertices (x, y, z)
        // A simple quad (rectangle) made of two triangles
        float[] vertices = {
                -0.5f, 0.5f, 0f,  // V0: Top Left
                -0.5f, -0.5f, 0f, // V1: Bottom Left
                0.5f, -0.5f, 0f, // V2: Bottom Right
                0.5f, 0.5f, 0f   // V3: Top Right
        };

        // 2. Define indices (connecting the dots)
        int[] indices = {
                0, 1, 3, // Top Left Triangle
                3, 1, 2  // Bottom Right Triangle
        };

        // 3. Load to GPU
        model = loader.loadToVAO(vertices, indices);
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

            // 2. Render (Variable)
            // 'alpha' is how far we are between the last tick and the next tick (0.0 to 1.0)
            // Use this for interpolation!
            double alpha = accumulator / TIME_PER_TICK;
            render((float) alpha);

            // Optional: Cap FPS if needed, otherwise run unlimited
        }

        stop();
    }

    // Variable Render: Draw the scene
    private void render(float alpha) {
        // 1. Prepare
        GL11.glEnable(GL11.GL_DEPTH_TEST);
        GL11.glClear(GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT);

        // 2. Start Shader
        shader.start();

        // 3. Bind VAO
        GL30.glBindVertexArray(model.getVaoID());
        GL20.glEnableVertexAttribArray(0); // Enable position attribute

        // 4. Draw
        GL11.glDrawElements(GL11.GL_TRIANGLES, model.getVertexCount(), GL11.GL_UNSIGNED_INT, 0);

        // 5. Cleanup for this frame
        GL20.glDisableVertexAttribArray(0);
        GL30.glBindVertexArray(0);
        shader.stop();

        // 6. Swap
        window.swapBuffers();
    }

    public void stop() {
        running = false;
        shader.cleanUp();
        loader.cleanUp();
        window.destroy();
    }
}