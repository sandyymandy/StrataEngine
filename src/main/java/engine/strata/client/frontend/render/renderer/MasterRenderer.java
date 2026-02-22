package engine.strata.client.frontend.render.renderer;

import engine.helios.rendering.*;
import engine.helios.rendering.shader.ShaderManager;
import engine.helios.rendering.shader.ShaderStack;
import engine.helios.rendering.texture.Texture;
import engine.helios.rendering.texture.TextureManager;
import engine.helios.rendering.vertex.BufferBuilder;
import engine.helios.rendering.vertex.MatrixStack;
import engine.helios.rendering.vertex.Tessellator;
import engine.helios.rendering.vertex.VertexFormat;
import engine.strata.client.StrataClient;
import engine.strata.client.frontend.render.Camera;
import engine.strata.client.frontend.render.RenderLayers;
import engine.strata.client.frontend.render.model.io.ModelManager;
import engine.strata.client.frontend.render.renderer.entity.util.EntityRenderDispatcher;
import engine.strata.client.frontend.render.renderer.entity.util.EntityRenderer;
import engine.strata.debug.DisplayDebugInfo;
import engine.strata.entity.Entity;
import engine.strata.entity.entities.PlayerEntity;
import engine.strata.entity.util.EntityKey;
import engine.strata.util.Identifier;
import engine.strata.world.World;
import engine.strata.util.math.BlockPos;
import engine.strata.util.math.BlockRaycast;
import engine.strata.world.block.Block;
import engine.strata.world.block.Blocks;
import engine.strata.world.block.model.BlockModel;
import engine.strata.world.block.model.BlockModelLoader;
import engine.strata.world.block.texture.DynamicTextureArray;
import engine.strata.world.block.texture.TextureArrayManager;
import engine.strata.world.chunk.render.ChunkMeshBuilder;
import engine.strata.world.chunk.render.ChunkRenderer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static engine.strata.util.math.Math.fLerp;
import static engine.strata.util.math.Math.lerp;

/**
 * Master renderer that coordinates all rendering passes.
 * Manages render layers, batching, and the rendering pipeline.
 */
public class MasterRenderer {
    private static final Logger LOGGER = LoggerFactory.getLogger("MasterRenderer");

    private static final int BUFFER_SIZE = 4 * 1024 * 1024;
    private static final float FLUSH_THRESHOLD = 0.90f;

    private final StrataClient client;
    private final Camera camera;
    private final MatrixStack poseStack;
    private final ModelRenderer modelRenderer;
    private final GuiRenderer guiRenderer;
    private final BlockOutlineRenderer blockOutlineRenderer;
    private final EntityRenderDispatcher entityRenderDispatcher;

    /** Shared model loader — used for block outline lookups and anything else that needs models. */
    private final BlockModelLoader blockModelLoader;

    // Chunk rendering
    private ChunkRenderer chunkRenderer;

    private static final float ENTITY_RENDER_DISTANCE = 120.0f;

    private boolean chunkRendererInitialized = false;

    // Render layer management
    private final Map<RenderLayer, BufferBuilder> buffers = new HashMap<>();
    private final List<RenderLayer> renderOrder = new ArrayList<>();

    private int flushesThisFrame = 0;

    private boolean isInThirdPerson = false;

    // Culling statistics
    private int entitiesRendered = 0;
    private int entitiesCulled = 0;

    private final DisplayDebugInfo debug;

    public MasterRenderer(StrataClient client, Camera camera, DisplayDebugInfo debug) {
        this.client = client;
        this.camera = camera;
        this.poseStack = new MatrixStack();
        this.modelRenderer = new ModelRenderer();
        this.guiRenderer = new GuiRenderer();
        this.entityRenderDispatcher = new EntityRenderDispatcher();
        this.debug = debug;
        this.blockOutlineRenderer = new BlockOutlineRenderer();
        this.blockModelLoader = new BlockModelLoader();

        LOGGER.info("MasterRenderer created (ChunkRenderer will initialize when world is ready)");
    }

    // ── Initialization ────────────────────────────────────────────────────────

    /**
     * Initializes the chunk renderer once the world is available.
     * Safe to call multiple times — subsequent calls are no-ops.
     */
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
                world.getChunkManager(),
                chunkMeshBuilder,
                camera,
                atlasId
        );

        chunkRendererInitialized = true;
        LOGGER.info("ChunkRenderer initialized");
    }

    // ── Main render entry point ───────────────────────────────────────────────

    public void render(Collection<Entity> entities, float partialTicks, float deltaTime) {
        flushesThisFrame = 0;
        entitiesRendered = 0;
        entitiesCulled = 0;

        if (!chunkRendererInitialized && client.getWorld() != null) {
            initChunkRenderer(client.getWorld());
        }

        camera.update(client.getPlayer(), client.getWindow(), partialTicks, isInThirdPerson);

        preRender(partialTicks, deltaTime);

        if (!setupShaderUniforms()) return;

        renderWorld(entities, partialTicks, deltaTime);

        postRender(partialTicks, deltaTime);
    }

    // ── Shader setup ──────────────────────────────────────────────────────────

    private boolean setupShaderUniforms() {
        ShaderStack shaderStack = ShaderManager.use(Identifier.ofEngine("generic_3d"));
        if (shaderStack == null) {
            LOGGER.error("Shader not loaded! Check shader files in resources/shaders/");
            return false;
        }
        shaderStack.setUniform("u_Projection", camera.getProjectionMatrix());
        shaderStack.setUniform("u_View", camera.getViewMatrix());
        return true;
    }

    // ── World render pass ─────────────────────────────────────────────────────

    private void renderWorld(Collection<Entity> entities, float partialTicks, float deltaTime) {
        client.getWindow().setRenderPhase("Render World");

        clearBuffers();

        if (chunkRenderer != null) {
            renderChunks(partialTicks);
        } else if (client.getWorld() != null && debug.showRenderDebug()) {
            LOGGER.warn("ChunkRenderer not initialized yet");
        }

        renderBlockOutline();
        renderEntities(entities, partialTicks, deltaTime);

        flushBuffers();
    }

    // ── Block outline ─────────────────────────────────────────────────────────

    /**
     * Resolves the targeted block's model and passes it to the outline renderer
     * so the wireframe matches the block's actual geometry.
     */
    private void renderBlockOutline() {
        if (!(client.getPlayer() instanceof PlayerEntity player)) return;

        BlockRaycast.RaycastResult target = player.getTargetedBlock();
        if (target == null || !target.isHit()) return;

        // Look up the block at the targeted position and resolve its model.
        BlockPos pos = target.getBlockPos();
        BlockModel model = null;
        if (pos != null && client.getWorld() != null) {
            Block block = client.getWorld().getBlock(pos);
            if (block != null && !block.isAir()) {
                model = blockModelLoader.loadModel(block.getModelId());
            }
        }

        blockOutlineRenderer.render(
                target,
                model,
                camera.getProjectionMatrix(),
                camera.getViewMatrix()
        );
    }

    // ── Chunk rendering ───────────────────────────────────────────────────────

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

    // ── Entity rendering ──────────────────────────────────────────────────────

    private void renderEntities(Collection<Entity> entities, float partialTicks, float deltaTime) {
        client.getWindow().setRenderPhase("Render Entities");

        World world = client.getWorld();
        if (world == null || entities == null) return;

        Entity cameraEntity = client.getCameraEntity();

        float camX = (float) lerp(cameraEntity.prevX, cameraEntity.getPosition().getX(), partialTicks);
        float camY = (float) lerp(cameraEntity.prevY, cameraEntity.getPosition().getY(), partialTicks);
        float camZ = (float) lerp(cameraEntity.prevZ, cameraEntity.getPosition().getZ(), partialTicks);

        for (Entity entity : entities) {
            EntityKey<?> renderKey = entity.getKey();

            // Morph support: if the player is morphed, use the morph target's renderer.
            if (client.getPlayer() instanceof PlayerEntity playerEntity
                    && entity.getKey() == client.getPlayer().getKey()
                    && playerEntity.isMorphed()) {
                renderKey = playerEntity.getMorphTarget();
            }

            EntityRenderer<Entity> renderer = entityRenderDispatcher.getRenderer(renderKey);
            if (renderer == null) continue;

            // Interpolate position for smooth rendering
            float x = fLerp((float) entity.prevX, (float) entity.getPosition().getX(), partialTicks);
            float y = fLerp((float) entity.prevY, (float) entity.getPosition().getY(), partialTicks);
            float z = fLerp((float) entity.prevZ, (float) entity.getPosition().getZ(), partialTicks);

            // Distance culling
            float dx = x - camX, dy = y - camY, dz = z - camZ;
            if (dx * dx + dy * dy + dz * dz > ENTITY_RENDER_DISTANCE * ENTITY_RENDER_DISTANCE) {
                entitiesCulled++;
                continue;
            }

            // Frustum culling
            if (!camera.isSphereVisible(x, y, z, entity.getKey().getWidth())) {
                entitiesCulled++;
                continue;
            }

            checkBuffersBeforeRender();

            MatrixStack entityPoseStack = new MatrixStack();
            entityPoseStack.push();
            renderer.render(entity, partialTicks, entityPoseStack);
            entityPoseStack.pop();

            entitiesRendered++;
        }

        if (debug.showRenderCullingDebug()) {
            LOGGER.debug("Entity rendering: {} rendered, {} culled", entitiesRendered, entitiesCulled);
        }
    }

    // ── Buffer management ─────────────────────────────────────────────────────

    private void checkBuffersBeforeRender() {
        for (Map.Entry<RenderLayer, BufferBuilder> entry : buffers.entrySet()) {
            BufferBuilder buffer = entry.getValue();
            if (!buffer.isBuilding()) continue;

            if (buffer.getUsage() > FLUSH_THRESHOLD) {
                flushSingleBuffer(entry.getKey(), buffer);
                flushesThisFrame++;
                buffer.begin(VertexFormat.POSITION_TEXTURE_COLOR);
            }
        }
    }

    private void flushSingleBuffer(RenderLayer layer, BufferBuilder buffer) {
        if (!buffer.isBuilding() || buffer.getVertexCount() == 0) return;
        layer.setup(camera);
        Tessellator.getInstance().draw(buffer);
        layer.clean();
    }

    private void clearBuffers() {
        for (BufferBuilder builder : buffers.values()) {
            if (builder.isBuilding()) builder.end();
            builder.reset();
        }
    }

    private void flushBuffers() {
        List<Map.Entry<RenderLayer, BufferBuilder>> sorted = new ArrayList<>(buffers.entrySet());
        sorted.sort((a, b) -> {
            boolean aT = a.getKey().isTranslucent();
            boolean bT = b.getKey().isTranslucent();
            if( aT != bT) return aT ? 1 : -1;
            return 0;
        });
        for (Map.Entry<RenderLayer, BufferBuilder> entry : sorted) {
            flushSingleBuffer(entry.getKey(), entry.getValue());
        }
    }

    public BufferBuilder getBuffer(RenderLayer layer) {
        return buffers.computeIfAbsent(layer, l -> {
            renderOrder.add(l);
            return new BufferBuilder(BUFFER_SIZE);
        });
    }

    // ── Pre/post render hooks ─────────────────────────────────────────────────

    public void preRender(float partialTicks, float deltaTime) {
        client.getWindow().setRenderPhase("Pre Render");
        RenderSystem.clear(0.5f, 0.7f, 0.9f, 1.0f);
    }

    public void postRender(float partialTicks, float deltaTime) {
        client.getWindow().setRenderPhase("Post Render");
    }

    // ── Reload / cleanup ──────────────────────────────────────────────────────

    /**
     * Reloads all rendering resources (e.g. after a resource-pack change).
     * Also clears the block model cache so JSON files are re-read from disk.
     */
    public void reload() {
        ModelManager.clearCache();
        RenderLayers.clearCache();
        blockModelLoader.clearCache();

        if (chunkRenderer != null) {
            chunkRenderer.disposeAll();
        }

        LOGGER.info("Reloaded renderer resources");
    }

    public void cleanup() {
        if (chunkRenderer != null) {
            chunkRenderer.disposeAll();
        }
        blockOutlineRenderer.dispose();
    }

    // ── Getters ───────────────────────────────────────────────────────────────

    public ModelRenderer getModelRenderer() { return modelRenderer; }
    public EntityRenderDispatcher getEntityRenderDispatcher() { return entityRenderDispatcher; }
    public Camera getCamera() { return camera; }
    public ChunkRenderer getChunkRenderer() { return chunkRenderer; }
    public BlockModelLoader getBlockModelLoader() { return blockModelLoader; }

    public void setThirdPerson(boolean v) { isInThirdPerson = v; }
    public boolean isThirdPerson() { return isInThirdPerson; }
}