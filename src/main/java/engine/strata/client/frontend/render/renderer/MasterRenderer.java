package engine.strata.client.frontend.render.renderer;

import engine.helios.rendering.*;
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
import engine.strata.client.frontend.render.renderer.entity.EntityRenderer;
import engine.strata.debug.DisplayDebugInfo;
import engine.strata.entity.Entity;
import engine.strata.entity.entities.PlayerEntity;
import engine.strata.util.Identifier;
import engine.strata.world.World;
import engine.strata.util.BlockPos;
import engine.strata.util.math.BlockRaycast;
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

import static engine.strata.util.math.Math.fLerp;
import static engine.strata.util.math.Math.lerp;

/**
 * Master renderer that coordinates all rendering passes.
 * Manages render layers, batching, and the rendering pipeline.
 *
 * <h3>GPU model rendering integration</h3>
 * Each frame {@link #setupShaderUniforms()} now also initialises:
 * <ul>
 *   <li>{@code u_Model} → identity (so Tessellator / immediate-mode draws are unaffected)</li>
 *   <li>{@code u_Tint}  → (1, 1, 1, 1) white (no tint)</li>
 * </ul>
 * {@link ModelRenderer} then overrides these uniforms per-mesh when rendering
 * static-VBO entity models.
 *
 * <h3>Entity rendering refactor</h3>
 * Removed EntityRenderDispatcher and per-entity renderer classes.
 * Now uses a single {@link EntityRenderer} instance that reads configuration
 * from {@link Entity#getModelId()} and {@link Entity#getRenderContext()}.
 */
public class MasterRenderer {
    private static final Logger LOGGER = LoggerFactory.getLogger("MasterRenderer");

    private static final int   BUFFER_SIZE     = 4 * 1024 * 1024;
    private static final float FLUSH_THRESHOLD = 0.90f;

    /** Reusable identity matrix for u_Model default. */
    private static final Matrix4f IDENTITY = new Matrix4f().identity();

    private final StrataClient client;
    private final Camera camera;
    private final MatrixStack poseStack;
    private final ModelRenderer modelRenderer;
    private final GuiRenderer guiRenderer;
    private final BlockOutlineRenderer blockOutlineRenderer;
    private final EntityRenderer entityRenderer;
    private final BlockModelLoader blockModelLoader;

    private ChunkRenderer chunkRenderer;

    private static final float ENTITY_RENDER_DISTANCE = 120.0f;

    private boolean chunkRendererInitialized = false;

    private boolean isInThirdPerson  = false;
    private int     entitiesRendered = 0;
    private int     entitiesCulled   = 0;

    private final DisplayDebugInfo debug;

    public MasterRenderer(StrataClient client, Camera camera, DisplayDebugInfo debug) {
        this.client                = client;
        this.camera                = camera;
        this.poseStack             = new MatrixStack();
        this.modelRenderer         = new ModelRenderer();
        this.guiRenderer           = new GuiRenderer();
        this.entityRenderer        = new EntityRenderer();
        this.debug                 = debug;
        this.blockOutlineRenderer  = new BlockOutlineRenderer();
        this.blockModelLoader      = new BlockModelLoader();

        LOGGER.info("MasterRenderer created (ChunkRenderer will initialize when world is ready)");
    }

    // ── Initialization ─────────────────────────────────────────────────────────

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

    // ── Main render entry point ────────────────────────────────────────────────

    public void render(Collection<Entity> entities, float partialTicks, float deltaTime) {
        entitiesRendered = 0;
        entitiesCulled   = 0;

        if (!chunkRendererInitialized && client.getWorld() != null) {
            initChunkRenderer(client.getWorld());
        }

        camera.update(client.getPlayer(), client.getWindow(), partialTicks, isInThirdPerson);

        preRender(partialTicks, deltaTime);

        if (!setupShaderUniforms()) return;

        renderWorld(entities, partialTicks, deltaTime);

        postRender(partialTicks, deltaTime);
    }

    // ── Shader setup ───────────────────────────────────────────────────────────

    /**
     * Activates the primary 3D shader and uploads per-frame uniforms.
     *
     * <p>{@code u_Model} is reset to identity here so that any immediate-mode
     * (Tessellator) draws that follow are not accidentally transformed.
     * {@link ModelRenderer} will override it per-mesh before each static-VBO
     * draw call.
     */
    private boolean setupShaderUniforms() {
        ShaderStack shader = ShaderManager.use(Identifier.ofEngine("generic_3d"));
        if (shader == null) {
            LOGGER.error("Shader not loaded! Check shader files in resources/shaders/");
            return false;
        }

        // Per-frame camera uniforms.
        shader.setUniform("u_Projection", camera.getProjectionMatrix());
        shader.setUniform("u_View",       camera.getViewMatrix());

        // Default model matrix — overridden per-mesh by ModelRenderer.
        shader.setUniform("u_Model", IDENTITY);

        // Default tint — white, no effect on colour.
        shader.setUniform("u_Tint", 1f, 1f, 1f, 1f);

        return true;
    }

    // ── World render pass ──────────────────────────────────────────────────────

    private void renderWorld(Collection<Entity> entities, float partialTicks, float deltaTime) {
        client.getWindow().setRenderPhase("Render World");

        if (chunkRenderer != null) {
            renderChunks(partialTicks);
        } else if (client.getWorld() != null && debug.showRenderDebug()) {
            LOGGER.warn("ChunkRenderer not initialized yet");
        }

        renderBlockOutline();
        renderEntities(entities, partialTicks, deltaTime);

    }

    // ── Block outline ──────────────────────────────────────────────────────────

    private void renderBlockOutline() {
        if (!(client.getPlayer() instanceof PlayerEntity player)) return;

        BlockRaycast.RaycastResult target = player.getTargetedBlock();
        if (target == null || !target.isHit()) return;

        BlockPos   pos   = target.getBlockPos();
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

    // ── Chunk rendering ────────────────────────────────────────────────────────

    private void renderChunks(float partialTicks) {
        client.getWindow().setRenderPhase("Render Chunks");
        chunkRenderer.render();

        if (debug.showRenderDebug()) {
            LOGGER.debug("Chunks: {} rendered, {} culled, {} triangles",
                    chunkRenderer.getChunksRendered(),
                    chunkRenderer.getChunksCulled(),
                    chunkRenderer.getTrianglesRendered());
        }
    }

    // ── Entity rendering ───────────────────────────────────────────────────────

    /**
     * Renders all entities using the universal EntityRenderer.
     *
     * <p>No more per-entity renderer lookup — the EntityRenderer reads
     * model ID and render context directly from each entity.
     */
    private void renderEntities(Collection<Entity> entities, float partialTicks, float deltaTime) {
        client.getWindow().setRenderPhase("Render Entities");

        World world = client.getWorld();
        if (world == null || entities == null) return;

        Entity cameraEntity = client.getCameraEntity();

        float camX = (float) lerp(cameraEntity.prevX, cameraEntity.getPosition().getX(), partialTicks);
        float camY = (float) lerp(cameraEntity.prevY, cameraEntity.getPosition().getY(), partialTicks);
        float camZ = (float) lerp(cameraEntity.prevZ, cameraEntity.getPosition().getZ(), partialTicks);

        for (Entity entity : entities) {
            // Interpolate position for culling checks
            float x = fLerp((float) entity.prevX, (float) entity.getPosition().getX(), partialTicks);
            float y = fLerp((float) entity.prevY, (float) entity.getPosition().getY(), partialTicks);
            float z = fLerp((float) entity.prevZ, (float) entity.getPosition().getZ(), partialTicks);

            // Distance culling
            float dx = x - camX, dy = y - camY, dz = z - camZ;
            if (dx*dx + dy*dy + dz*dz > ENTITY_RENDER_DISTANCE * ENTITY_RENDER_DISTANCE) {
                entitiesCulled++;
                continue;
            }

            // Frustum culling
            if (!camera.isSphereVisible(x, y, z, entity.getKey().getWidth())) {
                entitiesCulled++;
                continue;
            }

            // Render entity using universal renderer
            MatrixStack entityPoseStack = new MatrixStack();
            entityPoseStack.push();
            entityRenderer.render(entity, partialTicks, entityPoseStack);
            entityPoseStack.pop();

            entitiesRendered++;
        }

        if (debug.showRenderCullingDebug()) {
            LOGGER.debug("Entity rendering: {} rendered, {} culled",
                    entitiesRendered, entitiesCulled);
        }
    }

    // ── Pre/post render hooks ──────────────────────────────────────────────────

    public void preRender(float partialTicks, float deltaTime) {
        client.getWindow().setRenderPhase("Pre Render");
        RenderSystem.clear(0.5f, 0.7f, 0.9f, 1.0f);
    }

    public void postRender(float partialTicks, float deltaTime) {
        client.getWindow().setRenderPhase("Post Render");
    }

    // ── Reload / cleanup ───────────────────────────────────────────────────────

    /**
     * Reloads all rendering resources.
     *
     * <p>Importantly, this now also evicts the {@link GpuModelBaker} cache so
     * that all static VBOs are re-uploaded on the next render pass with any
     * newly loaded model data.
     */
    public void reload() {
        ModelManager.clearCache();
        RenderLayers.clearCache();
        blockModelLoader.clearCache();

        // Free all cached static VBOs — they will be re-baked lazily.
        GpuModelBaker.getInstance().evictAll();

        // Clear entity renderer model cache
        entityRenderer.clearCache();

        if (chunkRenderer != null) {
            chunkRenderer.disposeAll();
        }

        LOGGER.info("Reloaded renderer resources (GPU model cache cleared)");
    }

    public void cleanup() {
        GpuModelBaker.getInstance().evictAll();

        if (chunkRenderer != null) {
            chunkRenderer.disposeAll();
        }
        blockOutlineRenderer.dispose();
    }

    // ── Getters ────────────────────────────────────────────────────────────────

    public ModelRenderer getModelRenderer()                         { return modelRenderer; }
    public EntityRenderer getEntityRenderer()                       { return entityRenderer; }
    public Camera getCamera()                                       { return camera; }
    public ChunkRenderer getChunkRenderer()                        { return chunkRenderer; }
    public BlockModelLoader getBlockModelLoader()                   { return blockModelLoader; }
    public void setThirdPerson(boolean v)                           { isInThirdPerson = v; }
    public boolean isThirdPerson()                                  { return isInThirdPerson; }
}