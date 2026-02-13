package engine.strata.client;

import engine.strata.api.ClientModInitializer;
import engine.strata.client.backend.ClientBackEnd;
import engine.strata.client.frontend.ClientFrontEnd;
import engine.strata.client.frontend.window.Window;
import engine.strata.client.frontend.window.WindowConfig;
import engine.strata.client.render.Camera;
import engine.strata.client.render.renderer.MasterRenderer;
import engine.strata.debug.DisplayDebugInfo;
import engine.strata.entity.Entity;
import engine.strata.entity.entities.PlayerEntity;
import engine.strata.event.EventBus;
import engine.strata.world.World;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class StrataClient implements ClientModInitializer {
    public static final Logger LOGGER = LoggerFactory.getLogger("Client");
    private static StrataClient instance;
    private final Window window;
    private final ClientBackEnd backEnd;
    private final ClientFrontEnd frontEnd;
    private final EventBus eventBus = new EventBus();
    public StrataClient() {
        instance = this;

        // 1. Initialize Window (Must be on main thread for OpenGL)
        this.window = new Window(new WindowConfig(1280, 720, "StrataEngine"));

        // 3. Setup Systems
        this.frontEnd = new ClientFrontEnd(window, this);
        this.backEnd = new ClientBackEnd();
    }

    public void run() {
        // Start Backend Thread
        Thread logicThread = new Thread(backEnd, "LogicThread");
        logicThread.start();

        Thread.currentThread().setName("RenderThread");

        long lastFrameTime = System.nanoTime();

        // MAIN RENDER LOOP (Main Thread)
        while (!window.shouldClose()) {
            long now = System.nanoTime();
            float deltaTime = (now - lastFrameTime) / 1_000_000_000.0f;
            lastFrameTime = now;

            // 1. Get the "Safe" Snapshot from Backend
            var entityStates = backEnd.getLatestEntitySnapshots();
            float partialTicks = backEnd.getLatestPartialTicks();

            // 2. Render using only the Snapshot
            frontEnd.render(entityStates, partialTicks, deltaTime);

            window.updateCursorMode();
            window.swapBuffers();
            GLFW.glfwPollEvents();
        }

        backEnd.stop();
        window.destroy();
        GLFW.glfwTerminate();
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