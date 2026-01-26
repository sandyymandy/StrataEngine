package engine.strata.client;

import engine.helios.RenderSystem;
import engine.helios.ShaderManager;
import engine.strata.client.input.InputSystem;
import engine.strata.client.input.keybind.Keybinds;
import engine.strata.client.render.renderer.MasterRenderer;
import engine.strata.client.render.renderer.entity.ZombieEntityRenderer;
import engine.strata.client.render.renderer.entity.util.EntityRendererRegistry;
import engine.strata.client.window.Window;
import engine.strata.client.window.WindowConfig;
import engine.strata.entity.Entity;
import engine.strata.entity.PlayerEntity;
import engine.strata.entity.ZombieEntity;
import engine.strata.registry.registries.EntityRegistry;
import engine.strata.util.Identifier;
import engine.strata.world.World;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.lwjgl.glfw.GLFW.glfwTerminate;
import static org.lwjgl.opengl.GL11.*;

public class StrataClient {
    static StrataClient instance;
    private final Window window;
    private final World world;
    private final PlayerEntity player;
    private final MasterRenderer masterRenderer;
    private boolean running = true;
    boolean hideCursor = false;
    public static final Logger LOGGER = LoggerFactory.getLogger("Client");

    private static final double TICKS_PER_SECOND = 20.0;
    private static final double TIME_PER_TICK = 1.0 / TICKS_PER_SECOND;
    private volatile float partialTicks = 0.0f;

    public StrataClient() {
        instance = this;
        LOGGER.info("Initializing Client");

        // 1. Setup Window and World
        this.window = new Window(new WindowConfig(1280, 720, "StrataEngine"));
        this.world = new World();
        this.player = EntityRegistry.PLAYER.create(world);
        this.masterRenderer = new MasterRenderer(this);

        // 2. Initialize Client and Helios
        init();
        initHelios();
    }

    private void init() {
        // Register entity renderers
        EntityRendererRegistry.register(EntityRegistry.ZOMBIE, ZombieEntityRenderer::new);

        // Add player to world
        world.addEntity(player);

        // Spawn some test zombies
        for (int i = 0; i < 5; i++) {
            ZombieEntity zombie = EntityRegistry.ZOMBIE.create(world);
            zombie.setPos(i * 2, 0, -5);
            world.addEntity(zombie);
        }
    }

    private void initHelios() {
        // Register core shaders using the Helios ShaderManager
        ShaderManager.register(Identifier.ofEngine("generic_3d"),
                Identifier.ofEngine("vertex"),
                Identifier.ofEngine("fragment")
        );

        glEnable(GL_CULL_FACE);
        glCullFace(GL_BACK);
        glEnable(GL_DEPTH_TEST);
    }

    public void start() {
        // Initialize OpenGL context on Main Thread
        RenderSystem.initRenderThread();

        // Spawn Logic Thread
        Thread logicThread = new Thread(this::runLogic, "Logic-Thread");
        logicThread.start();

        Thread.currentThread().setName("Render-Thread");
        // Run Render Loop on Main Thread
        runRender();
    }

    /**
     * Logic Thread
     */

    public void runLogic() {
        long lastTime = System.nanoTime();
        double accumulator = 0.0;

        while (running && !window.shouldClose()) {
            long now = System.nanoTime();
            double deltaTime = (now - lastTime) / 1_000_000_000.0;
            lastTime = now;

            accumulator += deltaTime;

            processInput();

            while (accumulator >= TIME_PER_TICK) {
                tick();
                accumulator -= TIME_PER_TICK;
            }
            this.partialTicks = (float) (accumulator / TIME_PER_TICK);

            // Prepare render commands AFTER ticking
            prepareRenderCommands(this.partialTicks);


            try { Thread.sleep(1); } catch (InterruptedException ignored) {}
        }
    }

    private void prepareRenderCommands(float partialTicks) {
        // Prepare all entity renders (including player since it's in the world)
        masterRenderer.prepareEntityRenders(world, partialTicks);
    }

    /**
     * Render Thread
     */

    private void runRender() {
        long lastFrameTime = System.nanoTime();

        while (running && !window.shouldClose()) {
            long now = System.nanoTime();
            float renderDeltaTime = (float) ((now - lastFrameTime) / 1_000_000_000.0);
            lastFrameTime = now;

            masterRenderer.render(this.partialTicks, renderDeltaTime);

            window.swapBuffers();
            window.pollEvents();

            if(Keybinds.HIDE_CURSOR.isCanceled()) hideCursor = !hideCursor;
            if(hideCursor) window.lockCursor(); else window.unlockCursor();

        }
        stop();
    }

    private void tick() {
        world.tick();
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

    public MasterRenderer getMasterRenderer(){
        return masterRenderer;
    }

    public World getWorld() {
        return world;
    }
}