package engine.strata.client.frontend;

import engine.helios.rendering.RenderSystem;
import engine.helios.rendering.shader.ShaderManager;
import engine.strata.api.ClientFrontEndInitializer;
import engine.strata.client.StrataClient;
import engine.strata.client.frontend.window.Window;
import engine.strata.client.frontend.render.Camera;
import engine.strata.client.frontend.render.renderer.MasterRenderer;
import engine.strata.core.entrypoint.EntrypointManager;
import engine.strata.debug.DisplayDebugInfo;
import engine.strata.entity.Entity;
import engine.strata.util.Identifier;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;

/**
 * Client frontend that manages rendering and window.
 *
 * <h3>Entity Rendering Simplification:</h3>
 * The old system required registering a renderer for each entity type:
 * <pre>
 * EntityRendererRegistry.register(EntityRegistry.PLAYER, PlayerEntityRenderer::new);
 * EntityRendererRegistry.register(EntityRegistry.BIA, BiaEntityRenderer::new);
 * EntityRendererRegistry.register(EntityRegistry.MIKA, MikaEntityRenderer::new);
 * </pre>
 *
 * The new system requires NO registration at all. Entities provide their own
 * rendering configuration through Entity.getModelId() and Entity.getRenderContext().
 * The universal EntityRenderer handles everything automatically.
 */
public class ClientFrontEnd {
    public static final Logger LOGGER = LoggerFactory.getLogger("ClientFrontEnd");
    private final MasterRenderer masterRenderer;
    private final Window window;
    private final Camera camera;
    private final DisplayDebugInfo debugInfo = new DisplayDebugInfo(false, false, false, false, false, false);

    private FramerateCap framerateCap = FramerateCap.UNCAPPED;
    private long lastFrameTime = System.nanoTime();
    private long targetFrameTimeNanos = 0;

    public ClientFrontEnd(Window window, StrataClient client) {
        this.window = window;
        this.camera = new Camera();
        this.masterRenderer = new MasterRenderer(client, this.camera, this.debugInfo);
        RenderSystem.initRenderThread();
        initHelios();
        init();
        EntrypointManager.invoke("client_front_end", ClientFrontEndInitializer.class,
                ClientFrontEndInitializer::onFrontEndInitialize);
    }

    public void render(Collection<Entity> entities, float partialTicks, float deltaTime) {
        limitFramerate();
        this.masterRenderer.render(entities, partialTicks, deltaTime);
    }

    private void limitFramerate() {
        if (framerateCap == FramerateCap.UNCAPPED || framerateCap == FramerateCap.VSYNC) {
            return; // No limiting needed
        }

        long currentTime = System.nanoTime();
        long elapsedTime = currentTime - lastFrameTime;

        // If we rendered too fast, sleep for the remaining time
        if (elapsedTime < targetFrameTimeNanos) {
            long sleepTime = targetFrameTimeNanos - elapsedTime;

            // Convert to milliseconds and nanoseconds for Thread.sleep
            long sleepMillis = sleepTime / 1_000_000;
            int sleepNanos = (int)(sleepTime % 1_000_000);

            try {
                if (sleepMillis > 0 || sleepNanos > 0) {
                    Thread.sleep(sleepMillis, sleepNanos);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        lastFrameTime = System.nanoTime();
    }

    /**
     * Sets the framerate cap.
     * @param cap The desired framerate cap
     */
    public void setFramerateCap(FramerateCap cap) {
        this.framerateCap = cap;

        if (cap == FramerateCap.VSYNC) {
            // Enable VSync through GLFW
            GLFW.glfwSwapInterval(1);
            targetFrameTimeNanos = 0;
            LOGGER.info("Framerate cap set to: VSync");
        } else if (cap == FramerateCap.UNCAPPED) {
            // Disable VSync
            GLFW.glfwSwapInterval(0);
            targetFrameTimeNanos = 0;
            LOGGER.info("Framerate cap set to: Uncapped");
        } else {
            // Disable VSync for manual limiting
            GLFW.glfwSwapInterval(0);
            // Calculate target frame time in nanoseconds
            targetFrameTimeNanos = 1_000_000_000L / cap.getFps();
            LOGGER.info("Framerate cap set to: {} FPS ({}ms per frame)",
                    cap.getFps(),
                    String.format("%.2f", targetFrameTimeNanos / 1_000_000.0));
        }

        lastFrameTime = System.nanoTime();
    }

    /**
     * Gets the current framerate cap setting.
     */
    public FramerateCap getFramerateCap() {
        return framerateCap;
    }

    /**
     * Cycles to the next framerate cap option.
     * Useful for keybind toggling.
     */
    public void cycleFramerateCap() {
        FramerateCap[] values = FramerateCap.values();
        int currentIndex = framerateCap.ordinal();
        int nextIndex = (currentIndex + 1) % values.length;
        setFramerateCap(values[nextIndex]);
    }

    private void init() {
        setFramerateCap(FramerateCap.FPS_180);
        LOGGER.info("Client frontend initialized with universal entity rendering");
    }

    private void initHelios() {
        LOGGER.info("Initializing Helios rendering system...");

        ShaderManager.register(Identifier.ofEngine("generic_3d"),
                Identifier.ofEngine("core/vertex"),
                Identifier.ofEngine("core/fragment")
        );

        ShaderManager.register(Identifier.ofEngine("entity_cutout"),
                Identifier.ofEngine("core/vertex"),
                Identifier.ofEngine("core/entity_cutout")
        );

        ShaderManager.register(Identifier.ofEngine("chunk"),
                Identifier.ofEngine("core/chunk_vertex"),
                Identifier.ofEngine("core/chunk_fragment")
        );

        ShaderManager.register(Identifier.ofEngine("outline"),
                Identifier.ofEngine("included/outline_vertex"),
                Identifier.ofEngine("included/outline_fragment")
        );

        LOGGER.info("Helios initialization complete");
    }

    public void shutDown() {
        this.masterRenderer.cleanup();
        window.destroy();
        GLFW.glfwTerminate();
    }

    public Camera getCamera() {
        return camera;
    }

    public DisplayDebugInfo getDebugInfo() {
        return debugInfo;
    }

    public MasterRenderer getMasterRenderer() {
        return masterRenderer;
    }

    /**
     * Enum defining available framerate cap options.
     */
    public enum FramerateCap {
        UNCAPPED(0, "Uncapped"),
        FPS_30(30, "30 FPS"),
        FPS_60(60, "60 FPS"),
        FPS_120(120, "120 FPS"),
        FPS_144(144, "144 FPS"),
        FPS_180(180, "180 FPS"),
        FPS_240(240, "240 FPS"),
        VSYNC(0, "VSync");

        private final int fps;
        private final String displayName;

        FramerateCap(int fps, String displayName) {
            this.fps = fps;
            this.displayName = displayName;
        }

        public int getFps() {
            return fps;
        }

        public String getDisplayName() {
            return displayName;
        }

        @Override
        public String toString() {
            return displayName;
        }
    }
}