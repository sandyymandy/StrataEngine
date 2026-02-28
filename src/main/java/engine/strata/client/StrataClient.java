package engine.strata.client;

import engine.strata.api.ClientInitializer;
import engine.strata.client.backend.ClientBackEnd;
import engine.strata.client.frontend.ClientFrontEnd;
import engine.strata.client.frontend.window.Window;
import engine.strata.client.frontend.window.WindowConfig;
import engine.strata.client.frontend.render.Camera;
import engine.strata.client.frontend.render.renderer.MasterRenderer;
import engine.strata.debug.DisplayDebugInfo;
import engine.strata.entity.Entity;
import engine.strata.entity.entities.PlayerEntity;
import engine.strata.event.EventBus;
import engine.strata.world.World;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static engine.strata.core.StrataCore.TICK_DELTA;

public class StrataClient implements ClientInitializer {
    public static final Logger LOGGER = LoggerFactory.getLogger("Client");
    private static StrataClient instance;
    private final Window window;
    private final ClientBackEnd backEnd;
    private final ClientFrontEnd frontEnd;
    private final EventBus eventBus = new EventBus();



    public StrataClient() {
        instance = this;

        // 1. Initialize Window (Must be on main thread for OpenGL)
        this.window = new Window(new WindowConfig(1920, 1080, "StrataEngine"));

        // 2. Setup Systems
        this.frontEnd = new ClientFrontEnd(window, this);
        this.backEnd = new ClientBackEnd();
    }

    public void run() {
        Thread.currentThread().setName("MainThread");

        long lastTime = System.nanoTime();
        double accumulator = 0.0;
        long lastFrameTime = System.nanoTime();

        // MAIN LOOP (Single Thread - Logic + Render)
        while (!window.shouldClose()) {
            long now = System.nanoTime();
            float deltaTime = (now - lastFrameTime) / 1_000_000_000.0f;
            lastFrameTime = now;

            // Accumulate time for fixed timestep logic
            accumulator += (now - lastTime) / 1_000_000_000.0;
            lastTime = now;

            // Process input
            backEnd.processInput();

            // Fixed timestep logic updates
            while (accumulator >= TICK_DELTA) {
                backEnd.tick();
                accumulator -= TICK_DELTA;
            }

            // Calculate partial ticks for interpolation
            float partialTicks = (float) (accumulator / TICK_DELTA);

            // Render using current entity states with interpolation
            frontEnd.render(backEnd.getWorld().getEntities(), partialTicks, deltaTime);

            window.updateCursorMode();
            window.swapBuffers();
            GLFW.glfwPollEvents();
        }

        // Shutdown
        backEnd.shutdown();
        frontEnd.shutDown();

        LOGGER.info("Client shutdown complete");
    }

    public static StrataClient getInstance() { return instance; }

    public Window getWindow() {
        return window;
    }

    public Entity getCameraEntity() {
        return this.frontEnd.getCamera().getFocusedEntity();
    }

    public Camera getCamera() {
        return this.frontEnd.getCamera();
    }

    public DisplayDebugInfo getDebugInfo() {
        return this.frontEnd.getDebugInfo();
    }

    public MasterRenderer getMasterRenderer() {
        return this.frontEnd.getMasterRenderer();
    }

    public World getWorld() {
        return this.backEnd.getWorld();
    }

    public PlayerEntity getPlayer() {
        return this.backEnd.getPlayer();
    }

    public EventBus getEventBus() { return eventBus; }

    @Override
    public void onClientInitialize() {
        run();
    }
}