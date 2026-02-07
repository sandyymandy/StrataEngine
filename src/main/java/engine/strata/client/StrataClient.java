package engine.strata.client;

import engine.helios.ShaderManager;
import engine.strata.api.ClientModInitializer;
import engine.strata.client.input.InputSystem;
import engine.strata.client.input.keybind.Keybinds;
import engine.strata.client.render.Camera;
import engine.strata.client.render.renderer.MasterRenderer;
import engine.strata.client.render.renderer.entity.BiaEntityRenderer;
import engine.strata.client.render.renderer.entity.PlayerEntityRenderer;
import engine.strata.client.render.renderer.entity.util.EntityRendererRegistry;
import engine.strata.client.window.Window;
import engine.strata.client.window.WindowConfig;
import engine.strata.debug.DisplayDebugInfo;
import engine.strata.entity.BiaEntity;
import engine.strata.entity.Entity;
import engine.strata.entity.PlayerEntity;
import engine.strata.registry.registries.EntityRegistry;
import engine.strata.util.Identifier;
import engine.strata.world.World;
import engine.strata.world.block.Blocks;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.lwjgl.glfw.GLFW.glfwTerminate;

public class StrataClient implements ClientModInitializer {
    static StrataClient instance;
    private final Window window;
    private final World world;
    private final PlayerEntity player;
    private final Camera camera;
    private final DisplayDebugInfo debugInfo = new DisplayDebugInfo(false, false, false, false, false, true);
    private final MasterRenderer masterRenderer;
    private boolean running = true;
    boolean hideCursor = false;
    public static final Logger LOGGER = LoggerFactory.getLogger("Client");

    private static final double TICKS_PER_SECOND = 20.0;
    private static final double TIME_PER_TICK = 1.0 / TICKS_PER_SECOND;
    private float partialTicks = 0.0f;

    // Debug overlay
    private long lastDebugUpdate = 0;
    private static final long DEBUG_UPDATE_INTERVAL = 1000_000_000; // 1 second

    public StrataClient() {
        instance = this;
        LOGGER.info("Initializing Client");

        // 1. Setup Window and World
        this.window = new Window(new WindowConfig(1280, 720, "StrataEngine"));
        this.camera = new Camera();

        // 2. Create world with seed
        long seed = System.currentTimeMillis();
        this.world = new World("TestWorld", seed);

        this.player = EntityRegistry.PLAYER.create(world);
        player.setPos(0, 70, 0); // Spawn above terrain

        // 4. Create renderer
        this.masterRenderer = new MasterRenderer(this);

        // 5. Initialize Client
        init();
    }

    @Override
    public void onClientInitialize() {
        initHelios();
        run();
    }

    private void init() {
        // Register entity renderers
        EntityRendererRegistry.register(EntityRegistry.PLAYER, PlayerEntityRenderer::new);
        EntityRendererRegistry.register(EntityRegistry.BIA, BiaEntityRenderer::new);

        // Add player to world
        world.addEntity(player);

        // Spawn some test entities
        spawnTestEntities();

        // Pre-load chunks around spawn
        LOGGER.info("Pre-loading spawn chunks...");
        world.preloadChunks(player.getX(), player.getY(), player.getZ(), 5);

        // Place some test blocks
        placeTestBlocks();

        LOGGER.info("Client initialization complete!");
    }

    private void spawnTestEntities() {
        LOGGER.info("Spawning test entities...");

        // Spawn some test zombies
        for (int i = 0; i < 20; i++) {
            BiaEntity bia = EntityRegistry.BIA.create(world);
            bia.setPos(i * 2, 70, -5);
            world.addEntity(bia);
        }
    }

    private void placeTestBlocks() {
        LOGGER.info("Placing test blocks...");

        // Place a small structure near spawn
        int baseX = 5;
        int baseY = 70;
        int baseZ = 5;

        // Build a small house
        for (int x = 0; x < 5; x++) {
            for (int z = 0; z < 5; z++) {
                // Floor
                world.setBlock(baseX + x, baseY, baseZ + z, Blocks.WOOD);

                // Walls
                if (x == 0 || x == 4 || z == 0 || z == 4) {
                    for (int y = 1; y <= 3; y++) {
                        world.setBlock(baseX + x, baseY + y, baseZ + z, Blocks.COBBLESTONE);
                    }
                }

                // Roof
                world.setBlock(baseX + x, baseY + 4, baseZ + z, Blocks.WOOD);
            }
        }

        // Add a torch inside
        world.setBlock(baseX + 2, baseY + 2, baseZ + 2, Blocks.TORCH);

        LOGGER.info("Test structure built at ({}, {}, {})", baseX, baseY, baseZ);
    }

    private void initHelios() {
        LOGGER.info("Initializing Helios rendering system...");

        ShaderManager.register(Identifier.ofEngine("generic_3d"),
                Identifier.ofEngine("included/vertex"),
                Identifier.ofEngine("included/fragment")
        );
        ShaderManager.register(Identifier.ofEngine("entity_cutout"),
                Identifier.ofEngine("included/vertex"),
                Identifier.ofEngine("core/entity_cutout")
        );
        ShaderManager.register(Identifier.ofEngine("chunk"),
                Identifier.ofEngine("included/chunk_vertex"),
                Identifier.ofEngine("included/chunk_fragment")
        );

        LOGGER.info("Helios initialization complete");
    }

    /**
     * Main Game Loop with fixed timestep
     */
    public void run() {
        LOGGER.info("Starting main game loop...");

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
            if (Keybinds.HIDE_CURSOR.isCanceled()) {
                hideCursor = !hideCursor;
            }
            if (hideCursor) {
                window.lockCursor();
            } else {
                window.unlockCursor();
            }

            // Debug output
            if(StrataClient.getInstance().getDebugInfo().showWorldDebug()) updateDebugInfo(now);
        }

        stop();
    }

    private void tick() {
        // Tick the entire world (entities and chunks)
        world.tick();
        camera.tick();
    }

    private void processInput() {
        InputSystem.update();

        // Add debug keybinds
        if (Keybinds.DEBUG_RELOAD_CHUNKS != null && Keybinds.DEBUG_RELOAD_CHUNKS.isCanceled()) {
            LOGGER.info("Reloading chunks...");
            world.preloadChunks(player.getX(), player.getY(), player.getZ(), 5);
        }
    }

    private void updateDebugInfo(long now) {
        if (now - lastDebugUpdate >= DEBUG_UPDATE_INTERVAL) {
            String debugInfo = world.getDebugInfo();
            LOGGER.info(debugInfo);

            // Update window title with FPS and chunk info
            window.setTitle(String.format(
                    "StrataEngine | Chunks: %d | Entities: %d | Pos: (%.1f, %.1f, %.1f)",
                    world.getChunkManager().getLoadedChunkCount(),
                    world.getEntityCount(),
                    player.getX(), player.getY(), player.getZ()
            ));

            lastDebugUpdate = now;
        }
    }

    public void stop() {
        LOGGER.info("Stopping client...");
        running = false;

        // Shutdown world and all systems
        world.shutdown();

        // Cleanup window
        window.destroy();
        glfwTerminate();

        LOGGER.info("Client stopped successfully");
    }

    // ==================== Getters ====================

    public Window getWindow() {
        return window;
    }

    public static StrataClient getInstance() {
        return instance;
    }

    public Entity getCameraEntity() {
        return camera.getFocusedEntity();
    }

    public Camera getCamera() {
        return camera;
    }

    public DisplayDebugInfo getDebugInfo() {
        return this.debugInfo;
    }

    public MasterRenderer getMasterRenderer() {
        return masterRenderer;
    }

    public World getWorld() {
        return world;
    }

    public PlayerEntity getPlayer() {
        return player;
    }
}