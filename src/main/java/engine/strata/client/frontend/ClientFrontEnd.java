package engine.strata.client.frontend;

import engine.helios.RenderSystem;
import engine.helios.ShaderManager;
import engine.strata.client.StrataClient;
import engine.strata.client.frontend.window.Window;
import engine.strata.client.render.Camera;
import engine.strata.client.render.renderer.MasterRenderer;
import engine.strata.client.render.renderer.entity.BiaEntityRenderer;
import engine.strata.client.render.renderer.entity.PlayerEntityRenderer;
import engine.strata.client.render.renderer.entity.util.EntityRendererRegistry;
import engine.strata.client.render.snapshot.EntityRenderSnapshot;
import engine.strata.debug.DisplayDebugInfo;
import engine.strata.registry.registries.EntityRegistry;
import engine.strata.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

public class ClientFrontEnd {
    public static final Logger LOGGER = LoggerFactory.getLogger("ClientFrontEnd");
    private final MasterRenderer masterRenderer;
    private final Window window;
    private final Camera camera;
    private final DisplayDebugInfo debugInfo = new DisplayDebugInfo(false, false, false, false, false, false);

    public ClientFrontEnd(Window window, StrataClient client) {
        this.window = window;
        this.camera = new Camera();
        this.masterRenderer = new MasterRenderer(client, this.camera, this.debugInfo);
        RenderSystem.initRenderThread();
        initHelios();
        init();
    }

    public void render(Map<Integer, EntityRenderSnapshot> states, float partialTicks, float deltaTime) {
        this.masterRenderer.render(states, partialTicks, deltaTime);

    }

    private void init() {
        EntityRendererRegistry.register(EntityRegistry.PLAYER, PlayerEntityRenderer::new);
        EntityRendererRegistry.register(EntityRegistry.BIA, BiaEntityRenderer::new);
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

    public Camera getCamera() {
        return camera;
    }

    public DisplayDebugInfo getDebugInfo() {
        return debugInfo;
    }

    public MasterRenderer getMasterRenderer() {
        return masterRenderer;
    }
}
