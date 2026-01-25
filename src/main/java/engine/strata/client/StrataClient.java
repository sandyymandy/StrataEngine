package engine.strata.client;

import engine.helios.*;
import engine.strata.client.input.InputSystem;
import engine.strata.client.input.keybind.Keybinds;
import engine.strata.client.render.renderer.MasterRenderer;
import engine.strata.client.window.Window;
import engine.strata.client.window.WindowConfig;
import engine.strata.entity.Entity;
import engine.strata.entity.PlayerEntity;
import engine.strata.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.lwjgl.glfw.GLFW.glfwTerminate;
import static org.lwjgl.opengl.GL11.*;

public class StrataClient {
    static StrataClient instance;
    private Window window;
    private final MatrixStack poseStack;
    private final PlayerEntity player;
    private final MasterRenderer masterRenderer;
    private boolean running = true;
    boolean hideCursor = false;
    public static final Logger LOGGER = LoggerFactory.getLogger("Client");

    private static final double TICKS_PER_SECOND = 20.0;
    private static final double TIME_PER_TICK = 1.0 / TICKS_PER_SECOND;

    public StrataClient() {
        instance = this;
        LOGGER.info("Initializing Client");

        // 1. Setup Window
        this.window = new Window(new WindowConfig(1280, 720, "StrataEngine"));
        this.poseStack = new MatrixStack();
        this.player = new PlayerEntity();
        this.masterRenderer = new MasterRenderer(this);

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

            // Drawing to the back buffer
            render((float) (accumulator / TIME_PER_TICK), (float) deltaTime);

            // Finalize frame By swaping the back buffer and bring it to the front
            window.swapBuffers();
        }

        stop();
    }

    private void render(float partialTicks, float deltaTime) {
        this.masterRenderer.render(partialTicks, deltaTime);

        if(Keybinds.HIDE_CURSOR.isCanceled()) hideCursor = !hideCursor;
        if(hideCursor) window.lockCursor(); else window.unlockCursor();
    }

    private void tick() {
        player.tick();
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

    public Window getWindow() {
        return window;
    }

    public static StrataClient getInstance() {
        return instance;
    }

    public Entity getCameraEntity(){
        return player;
    }
}