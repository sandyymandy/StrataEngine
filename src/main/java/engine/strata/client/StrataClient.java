package engine.strata.client;

import engine.helios.RenderSystem;
import engine.helios.ShaderManager;
import engine.strata.api.ClientModInitializer;
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

public class StrataClient implements ClientModInitializer {
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
    private float partialTicks = 0.0f;

    public StrataClient() {
        instance = this;
        LOGGER.info("Initializing Client");

        // 1. Setup Window and World
        this.window = new Window(new WindowConfig(1280, 720, "StrataEngine"));
        this.world = new World();
        this.player = EntityRegistry.PLAYER.create(world);
        this.masterRenderer = new MasterRenderer(this);

        // 2. Initialize Client
        init();
    }

    @Override
    public void onClientInitialize() {
        initHelios();
        run();
    }

    private void init() {
        // Register entity renderers
        EntityRendererRegistry.register(EntityRegistry.PLAYER, engine.strata.client.render.renderer.entity.PlayerEntityRenderer::new);
        EntityRendererRegistry.register(EntityRegistry.ZOMBIE, ZombieEntityRenderer::new);

        // Add player to world
        world.addEntity(player);
//
//        // Spawn some test zombies
//        for (int i = 0; i < 5; i++) {
//            ZombieEntity zombie = EntityRegistry.ZOMBIE.create(world);
//            zombie.setPos(i * 2, 0, -5);
//            world.addEntity(zombie);
//        }
    }

    private void initHelios() {
        LOGGER.info("Initializing Helios");

        ShaderManager.register(Identifier.ofEngine("generic_3d"),
                Identifier.ofEngine("included/vertex"),
                Identifier.ofEngine("included/fragment_debug")
        );
        ShaderManager.register(Identifier.ofEngine("entity_cutout"),
                Identifier.ofEngine("included/vertex"),
                Identifier.ofEngine("core/entity_cutout")
        );




        LOGGER.info("OpenGL state configured");
    }

    /**
     * Main Game Loop
     */
    public void run() {
        long lastTime = System.nanoTime();
        double accumulator = 0.0;
        long lastFrameTime = System.nanoTime();

        while (running && !window.shouldClose()) {
            long now = System.nanoTime();
            double deltaTime = (now - lastTime) / 1_000_000_000.0;
            lastTime = now;

            accumulator += deltaTime;

            // Process input
            processInput();

            // Fixed timestep logic updates
            while (accumulator >= TIME_PER_TICK) {
                tick();
                accumulator -= TIME_PER_TICK;
            }

            // Calculate partial ticks for smooth interpolation
            this.partialTicks = (float) (accumulator / TIME_PER_TICK);

            // Calculate render delta time
            float renderDeltaTime = (float) ((now - lastFrameTime) / 1_000_000_000.0);
            lastFrameTime = now;

            // Render everything
            masterRenderer.render(this.partialTicks, renderDeltaTime);

            // Swap buffers and poll events
            window.swapBuffers();
            window.pollEvents();

            // Handle cursor visibility
            if(Keybinds.HIDE_CURSOR.isCanceled()) hideCursor = !hideCursor;
            if(hideCursor) window.lockCursor(); else window.unlockCursor();

        }

        stop();
    }

    private void tick() {
        // Tick the entire world (which ticks all entities including the player)
        world.tick();
    }

    private void processInput() {
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