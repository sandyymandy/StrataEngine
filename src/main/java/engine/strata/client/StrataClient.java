package engine.strata.client;

import engine.helios.*;
import engine.strata.client.input.InputSystem;
import engine.strata.client.input.keybind.Keybinds;
import engine.strata.client.render.Camera;
import engine.strata.client.window.Window;
import engine.strata.client.window.WindowConfig;
import engine.strata.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.lwjgl.glfw.GLFW.glfwTerminate;
import static org.lwjgl.opengl.GL11.*;

public class StrataClient {
    private Window window;
    private Camera camera;
    private MatrixStack matrixStack;

    private boolean running = true;
    boolean hideCursor = false;
    public static final Logger LOGGER = LoggerFactory.getLogger("Client");

    private static final double TICKS_PER_SECOND = 20.0;
    private static final double TIME_PER_TICK = 1.0 / TICKS_PER_SECOND;

    public void init() {
        LOGGER.info("Initializing Client");

        // 1. Setup Window
        this.window = new Window(new WindowConfig(1280, 720, "StrataEngine"));
        this.camera = new Camera();
        this.matrixStack = new MatrixStack();

        // 2. Initialize Helios Shaders
        initHelios();
    }

    private void initHelios() {
        // Register core shaders using the Helios ShaderManager
        ShaderManager.register("generic_3d",
                Identifier.of("strata", "vertex"),
                Identifier.of("strata", "fragment")
        );

        glEnable(GL_CULL_FACE);
        glCullFace(GL_BACK);
        glEnable(GL_DEPTH_TEST);
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

            processInput();

            while (accumulator >= TIME_PER_TICK) {
                tick();
                accumulator -= TIME_PER_TICK;
            }

            // Alpha used for interpolation
            render((float) (accumulator / TIME_PER_TICK));
        }

        stop();
    }

    private void render(float alpha) {
        // 1. Clear Screen (like old prepare() method)
        RenderSystem.clear(0.5f, 0.7f, 0.9f, 1.0f);

        // 2. Update Camera
        camera.move(window);

        // 3. Start Shader and load View/Projection (like old code)
        ShaderManager.use("generic_3d");
        Shader shader = ShaderManager.getCurrent();

        if (shader == null) {
            LOGGER.error("Shader is null!");
            window.swapBuffers();
            return;
        }

        // Load these ONCE per frame (like your old EntityRenderer constructor)
        shader.setUniform("u_Projection", camera.getProjectionMatrix());
        shader.setUniform("u_View", camera.getViewMatrix());

        Tessellator tess = Tessellator.getInstance();
        BufferBuilder builder = tess.getBuffer();

        // Render a grid of cubes - each cube gets its own draw call
        // This matches your old batch rendering approach
        for (int x = -2; x <= 2; x++) {
            for (int z = -5; z >= -10; z--) {
                // Calculate model matrix
                matrixStack.push();
                matrixStack.translate(x * 2, 0, z);
                matrixStack.rotate(System.currentTimeMillis() / 20f, 0, 1, 0);

                // Load transformation matrix (like old prepareInstance)
                shader.setUniform("u_Model", matrixStack.peek());

                // Build cube geometry (like old prepareRawModel + drawElements)
                builder.begin(VertexFormat.POSITION_COLOR);
                drawCube(builder, 0, 0, 0);
                tess.draw();

                matrixStack.pop();
            }
        }

        // Finalize frame
        window.swapBuffers();

        if(Keybinds.HIDE_CURSOR.isCanceled()) hideCursor = !hideCursor;
        if(hideCursor) window.lockCursor(); else window.unlockCursor();
    }

    private void tick() {
        // Game logic updates (physics, etc)
    }

    private void processInput() {
        window.pollEvents();
        InputSystem.update();
    }

    public void stop() {
        running = false;
        window.destroy();
        glfwTerminate();
    }

    /**
     * Draw a cube in LOCAL space (0,0,0 centered)
     * The shader will transform it using u_Model matrix
     * This matches your old vertex array approach
     */
    private void drawCube(BufferBuilder builder, float x, float y, float z) {
        // Front face (CCW when viewed from front)
        builder.pos(x-0.5f, y-0.5f, z+0.5f).color(1, 0, 0, 1).next();
        builder.pos(x+0.5f, y-0.5f, z+0.5f).color(1, 0, 0, 1).next();
        builder.pos(x+0.5f, y+0.5f, z+0.5f).color(1, 0, 0, 1).next();

        builder.pos(x+0.5f, y+0.5f, z+0.5f).color(1, 0, 0, 1).next();
        builder.pos(x-0.5f, y+0.5f, z+0.5f).color(1, 0, 0, 1).next();
        builder.pos(x-0.5f, y-0.5f, z+0.5f).color(1, 0, 0, 1).next();

        // Back face (CCW when viewed from front)
        builder.pos(x+0.5f, y-0.5f, z-0.5f).color(0, 1, 0, 1).next();
        builder.pos(x-0.5f, y-0.5f, z-0.5f).color(0, 1, 0, 1).next();
        builder.pos(x-0.5f, y+0.5f, z-0.5f).color(0, 1, 0, 1).next();

        builder.pos(x-0.5f, y+0.5f, z-0.5f).color(0, 1, 0, 1).next();
        builder.pos(x+0.5f, y+0.5f, z-0.5f).color(0, 1, 0, 1).next();
        builder.pos(x+0.5f, y-0.5f, z-0.5f).color(0, 1, 0, 1).next();

        // Top face (CCW when viewed from above)
        builder.pos(x-0.5f, y+0.5f, z+0.5f).color(0, 0, 1, 1).next();
        builder.pos(x+0.5f, y+0.5f, z+0.5f).color(0, 0, 1, 1).next();
        builder.pos(x+0.5f, y+0.5f, z-0.5f).color(0, 0, 1, 1).next();

        builder.pos(x+0.5f, y+0.5f, z-0.5f).color(0, 0, 1, 1).next();
        builder.pos(x-0.5f, y+0.5f, z-0.5f).color(0, 0, 1, 1).next();
        builder.pos(x-0.5f, y+0.5f, z+0.5f).color(0, 0, 1, 1).next();

        // Bottom face (CCW when viewed from below)
        builder.pos(x-0.5f, y-0.5f, z-0.5f).color(1, 1, 0, 1).next();
        builder.pos(x+0.5f, y-0.5f, z-0.5f).color(1, 1, 0, 1).next();
        builder.pos(x+0.5f, y-0.5f, z+0.5f).color(1, 1, 0, 1).next();

        builder.pos(x+0.5f, y-0.5f, z+0.5f).color(1, 1, 0, 1).next();
        builder.pos(x-0.5f, y-0.5f, z+0.5f).color(1, 1, 0, 1).next();
        builder.pos(x-0.5f, y-0.5f, z-0.5f).color(1, 1, 0, 1).next();

        // Right face (CCW when viewed from right)
        builder.pos(x+0.5f, y-0.5f, z+0.5f).color(1, 0, 1, 1).next();
        builder.pos(x+0.5f, y-0.5f, z-0.5f).color(1, 0, 1, 1).next();
        builder.pos(x+0.5f, y+0.5f, z-0.5f).color(1, 0, 1, 1).next();

        builder.pos(x+0.5f, y+0.5f, z-0.5f).color(1, 0, 1, 1).next();
        builder.pos(x+0.5f, y+0.5f, z+0.5f).color(1, 0, 1, 1).next();
        builder.pos(x+0.5f, y-0.5f, z+0.5f).color(1, 0, 1, 1).next();

        // Left face (CCW when viewed from left)
        builder.pos(x-0.5f, y-0.5f, z-0.5f).color(0, 1, 1, 1).next();
        builder.pos(x-0.5f, y-0.5f, z+0.5f).color(0, 1, 1, 1).next();
        builder.pos(x-0.5f, y+0.5f, z+0.5f).color(0, 1, 1, 1).next();

        builder.pos(x-0.5f, y+0.5f, z+0.5f).color(0, 1, 1, 1).next();
        builder.pos(x-0.5f, y+0.5f, z-0.5f).color(0, 1, 1, 1).next();
        builder.pos(x-0.5f, y-0.5f, z-0.5f).color(0, 1, 1, 1).next();
    }
}