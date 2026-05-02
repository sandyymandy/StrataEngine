package engine.strata.client.frontend.render.renderer;

import engine.helios.physics.AABB;
import engine.helios.rendering.RenderSystem;
import engine.helios.rendering.pipeline.RenderPipeline;
import engine.helios.rendering.pipeline.RenderPipeline.RenderStage;
import engine.helios.rendering.shader.ShaderManager;
import engine.helios.rendering.shader.ShaderStack;
import engine.helios.rendering.texture.Texture;
import engine.helios.rendering.texture.TextureManager;
import engine.helios.rendering.vertex.MatrixStack;
import engine.strata.client.StrataClient;
import engine.strata.client.frontend.render.Camera;
import engine.strata.client.frontend.render.RenderLayers;
import engine.strata.client.frontend.render.model.GpuModelBaker;
import engine.strata.client.frontend.render.model.io.ModelManager;
import engine.strata.client.frontend.render.renderer.entity.EntityRenderDispatcher;
import engine.strata.entity.Entity;
import engine.strata.entity.entities.PlayerEntity;
import engine.strata.util.BlockPos;
import engine.strata.util.Identifier;
import engine.strata.util.debug.DisplayDebugInfo;
import engine.strata.util.math.BlockRaycast;
import engine.strata.world.World;
import engine.strata.world.block.Block;
import engine.strata.world.block.Blocks;
import engine.strata.world.block.model.BlockModel;
import engine.strata.world.block.model.BlockModelLoader;
import engine.strata.world.block.texture.DynamicTextureArray;
import engine.strata.world.block.texture.TextureArrayManager;
import engine.strata.world.chunk.render.ChunkMeshBuilder;
import engine.strata.world.chunk.render.ChunkRenderer;
import org.joml.Matrix4f;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;

/**
 * Master renderer that orchestrates the entire rendering pipeline.
 *
 * <h3>Architecture Improvements:</h3>
 * <ul>
 *   <li>Formal {@link RenderPipeline} with defined stages</li>
 *   <li>{@link EntityRenderDispatcher} with per-entity-type renderers</li>
 *   <li>Batched entity rendering for performance</li>
 *   <li>Clean separation between Strata (engine) and Helios (graphics API)</li>
 * </ul>
 *
 * <h3>Pipeline Stages:</h3>
 * <ol>
 *   <li>PRE_RENDER - Clear buffers, setup uniforms</li>
 *   <li>RENDER_WORLD_OPAQUE - Chunks</li>
 *   <li>RENDER_ENTITIES_OPAQUE - Solid entities</li>
 *   <li>RENDER_DEBUG - Block outline, debug boxes</li>
 *   <li>POST_RENDER - Cleanup</li>
 * </ol>
 */
public class MasterRenderer {

    private static final Logger LOGGER = LoggerFactory.getLogger("MasterRenderer");
    private static final Matrix4f IDENTITY = new Matrix4f().identity();
    private static final float ENTITY_RENDER_DISTANCE = 120.0f;

    private final StrataClient client;
    private final Camera camera;
    private final RenderPipeline pipeline;
    private final EntityRenderDispatcher entityDispatcher;
    private final GuiRenderer guiRenderer;
    private final BlockOutlineRenderer blockOutlineRenderer;
    private final BoundingBoxDebugRenderer debugBoxRenderer;
    private final BlockModelLoader blockModelLoader;
    private final DisplayDebugInfo debug;

    private ChunkRenderer chunkRenderer;
    private boolean chunkRendererInitialized = false;
    private boolean isInThirdPerson = false;

    // Statistics
    private int entitiesRendered = 0;
    private int entitiesCulled = 0;

    public MasterRenderer(StrataClient client, Camera camera, DisplayDebugInfo debug) {
        this.client = client;
        this.camera = camera;
        this.debug = debug;
        this.pipeline = new RenderPipeline();
        this.entityDispatcher = new EntityRenderDispatcher();
        this.guiRenderer = new GuiRenderer();
        this.blockOutlineRenderer = new BlockOutlineRenderer();
        this.debugBoxRenderer = new BoundingBoxDebugRenderer();
        this.blockModelLoader = new BlockModelLoader();

        setupPipeline();
        LOGGER.info("MasterRenderer created with formal pipeline and entity dispatcher");
    }

    // ══════════════════════════════════════════════════════════════════════════
    // PIPELINE SETUP
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * Setup the rendering pipeline with all stages.
     */
    private void setupPipeline() {
        // Pre-render: Clear and setup
        pipeline.registerStage(RenderStage.PRE_RENDER, "Clear & Setup",
                this::preRenderStage);

        // World rendering: Chunks
        pipeline.registerStage(RenderStage.RENDER_WORLD_OPAQUE, "Chunks",
                this::renderChunksStage);

        // Entity rendering: Batched
        pipeline.registerStage(RenderStage.RENDER_ENTITIES_OPAQUE, "Entities",
                this::renderEntitiesStage);

        // Debug rendering: Outlines, boxes
        pipeline.registerStage(RenderStage.RENDER_DEBUG, "Debug Overlays",
                this::renderDebugStage);

        // Post-render: Cleanup
        pipeline.registerStage(RenderStage.POST_RENDER, "Cleanup",
                this::postRenderStage);

        LOGGER.info("Render pipeline configured with {} stages", pipeline.getStageCount());
    }

    // ══════════════════════════════════════════════════════════════════════════
    // MAIN RENDER ENTRY POINT
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * Main render call. Executes the entire pipeline.
     */
    public void render(Collection<Entity> entities, float partialTicks, float deltaTime) {
        // Initialize chunk renderer if needed
        if (!chunkRendererInitialized && client.getWorld() != null) {
            initChunkRenderer(client.getWorld());
        }

        // Update camera
        camera.update(client.getPlayer(), client.getWindow(), partialTicks, isInThirdPerson);

        // Store entities for pipeline stages
        this.currentEntities = entities;
        this.currentPartialTicks = partialTicks;
        this.currentDeltaTime = deltaTime;

        // Execute pipeline
        pipeline.execute(partialTicks, deltaTime);
    }

    // Temporary storage for pipeline stages
    private Collection<Entity> currentEntities;
    private float currentPartialTicks;
    private float currentDeltaTime;

    // ══════════════════════════════════════════════════════════════════════════
    // PIPELINE STAGES
    // ══════════════════════════════════════════════════════════════════════════

    private void preRenderStage(float partialTicks, float deltaTime) {
        client.getWindow().setRenderPhase("Pre Render");

        // Clear buffers
        RenderSystem.clear(0.5f, 0.7f, 0.9f, 1.0f);

        // Setup global shader uniforms
        if (!setupShaderUniforms()) {
            LOGGER.error("Failed to setup shader uniforms");
        }
    }

    private void renderChunksStage(float partialTicks, float deltaTime) {
        if (chunkRenderer == null) return;

        client.getWindow().setRenderPhase("Render Chunks");
        chunkRenderer.render();

        if (debug.showRenderDebug()) {
            LOGGER.debug("Chunks: {} rendered, {} culled, {} triangles",
                    chunkRenderer.getChunksRendered(),
                    chunkRenderer.getChunksCulled(),
                    chunkRenderer.getTrianglesRendered());
        }
    }

    private void renderEntitiesStage(float partialTicks, float deltaTime) {
        client.getWindow().setRenderPhase("Render Entities");

        if (currentEntities == null || currentEntities.isEmpty()) return;

        entitiesRendered = 0;
        entitiesCulled = 0;

        // Frustum cull entities
        Collection<Entity> visibleEntities = performFrustumCulling(currentEntities);

        // Batched rendering through dispatcher
        entityDispatcher.renderBatch(visibleEntities, partialTicks);

        if (debug.showRenderCullingDebug()) {
            LOGGER.debug("Entities: {} rendered, {} culled", entitiesRendered, entitiesCulled);
        }
    }

    private void renderDebugStage(float partialTicks, float deltaTime) {
        client.getWindow().setRenderPhase("Render Debug");

        // Block outline
        renderBlockOutline();

        // Entity bounding boxes (if debug enabled)
        if (debug.showRenderDebug() && currentEntities != null) {
            for (Entity entity : currentEntities) {
                AABB box = entity.getModelBoundingBox(partialTicks);
                debugBoxRenderer.render(box, camera.getProjectionMatrix(),
                        camera.getViewMatrix(),
                        BoundingBoxDebugRenderer.Color.YELLOW);
            }
        }
    }

    private void postRenderStage(float partialTicks, float deltaTime) {
        client.getWindow().setRenderPhase("Post Render");
        // Post-processing would go here
    }

    // ══════════════════════════════════════════════════════════════════════════
    // RENDERING UTILITIES
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * Setup global shader uniforms.
     */
    private boolean setupShaderUniforms() {
        ShaderStack shader = ShaderManager.use(Identifier.ofEngine("generic_3d"));
        if (shader == null) {
            LOGGER.error("Shader not loaded!");
            return false;
        }

        shader.setUniform("u_Projection", camera.getProjectionMatrix());
        shader.setUniform("u_View", camera.getViewMatrix());
        shader.setUniform("u_Model", IDENTITY);
        shader.setUniform("u_Tint", 1f, 1f, 1f, 1f);

        return true;
    }

    /**
     * Perform frustum culling on entities.
     */
    private Collection<Entity> performFrustumCulling(Collection<Entity> entities) {
        if (entities == null) return null;

        java.util.List<Entity> visible = new java.util.ArrayList<>();

        for (Entity entity : entities) {
            AABB worldBox = entity.getModelBoundingBox(currentPartialTicks);

            if (camera.isAabbVisibleWithDistance(worldBox, ENTITY_RENDER_DISTANCE)) {
                visible.add(entity);
                entitiesRendered++;
            } else {
                entitiesCulled++;
            }
        }

        return visible;
    }

    /**
     * Render block outline for targeted block.
     */
    private void renderBlockOutline() {
        if (!(client.getPlayer() instanceof PlayerEntity player)) return;

        BlockRaycast.RaycastResult target = player.getTargetedBlock();
        if (target == null || !target.isHit()) return;

        BlockPos pos = target.getBlockPos();
        BlockModel model = null;

        if (pos != null && client.getWorld() != null) {
            Block block = client.getWorld().getBlock(pos);
            if (block != null && !block.isAir()) {
                model = blockModelLoader.loadModel(block.getModelId());
            }
        }

        blockOutlineRenderer.render(target, model,
                camera.getProjectionMatrix(), camera.getViewMatrix());
    }

    // ══════════════════════════════════════════════════════════════════════════
    // INITIALIZATION
    // ══════════════════════════════════════════════════════════════════════════

    public void initChunkRenderer(World world) {
        if (chunkRendererInitialized) return;

        Identifier atlasId = Identifier.ofEngine("blocks/atlas");
        DynamicTextureArray atlas = TextureArrayManager.getInstance()
                .initialize(Blocks.getAllBlocks(), 32);

        Texture atlasTexture = new Texture(atlas.getTextureId());
        TextureManager.register(atlasId, atlasTexture);

        ChunkMeshBuilder chunkMeshBuilder = world.initializeChunkMesher(atlas);
        world.startChunkThreads(chunkMeshBuilder);

        this.chunkRenderer = new ChunkRenderer(
                world.getChunkManager(), chunkMeshBuilder, camera, atlasId);

        chunkRendererInitialized = true;
        LOGGER.info("ChunkRenderer initialized");
    }

    // ══════════════════════════════════════════════════════════════════════════
    // RESOURCE MANAGEMENT
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * Reload all rendering resources.
     */
    public void reload() {
        ModelManager.clearCache();
        RenderLayers.clearCache();
        blockModelLoader.clearCache();
        GpuModelBaker.getInstance().evictAll();
        entityDispatcher.reload();

        if (chunkRenderer != null) {
            chunkRenderer.disposeAll();
        }

        LOGGER.info("Reloaded all renderer resources");
    }

    /**
     * Cleanup all resources.
     */
    public void cleanup() {
        GpuModelBaker.getInstance().evictAll();
        entityDispatcher.cleanup();

        if (chunkRenderer != null) {
            chunkRenderer.disposeAll();
        }

        blockOutlineRenderer.dispose();
        LOGGER.info("MasterRenderer cleanup complete");
    }

    // ══════════════════════════════════════════════════════════════════════════
    // GETTERS
    // ══════════════════════════════════════════════════════════════════════════

    public Camera getCamera() {
        return camera;
    }

    public EntityRenderDispatcher getEntityDispatcher() {
        return entityDispatcher;
    }

    public ChunkRenderer getChunkRenderer() {
        return chunkRenderer;
    }

    public BlockModelLoader getBlockModelLoader() {
        return blockModelLoader;
    }

    public RenderPipeline getPipeline() {
        return pipeline;
    }

    public void setThirdPerson(boolean v) {
        isInThirdPerson = v;
    }

    public boolean isThirdPerson() {
        return isInThirdPerson;
    }
}