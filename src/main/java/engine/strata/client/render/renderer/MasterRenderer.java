package engine.strata.client.render.renderer;

import engine.helios.*;
import engine.strata.client.StrataClient;
import engine.strata.client.render.Camera;
import engine.strata.client.render.RenderLayers;
import engine.strata.client.render.model.io.ModelManager;
import engine.strata.client.render.renderer.entity.util.EntityRenderDispatcher;
import engine.strata.client.render.renderer.entity.util.EntityRenderer;
import engine.strata.client.render.util.BasicRenderer;
import engine.strata.debug.DisplayDebugInfo;
import engine.strata.entity.Entity;
import engine.strata.util.Identifier;
import engine.strata.world.World;
import engine.strata.world.chunk.render.ChunkRenderer;
import org.joml.Matrix4f;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static engine.strata.util.math.Math.lerp;
import static engine.strata.util.math.Math.lerpAngle;

/**
 * Master renderer that coordinates all rendering passes.
 * Manages render layers, batching, and the rendering pipeline.
 */
public class MasterRenderer implements BasicRenderer {
    private static final Logger LOGGER = LoggerFactory.getLogger("MasterRenderer");

    // Optimal buffer size: 4MB per layer (handles ~20-30 entities before flush)
    private static final int BUFFER_SIZE = 4 * 1024 * 1024;

    // Flush when buffer reaches 90% capacity
    private static final float FLUSH_THRESHOLD = 0.90f;

    private final StrataClient client;
    private final Camera camera;
    private final MatrixStack poseStack;
    private final ModelRenderer modelRenderer;
    private final GuiRenderer guiRenderer;
    private final EntityRenderDispatcher entityRenderDispatcher;

    // Entity render distance
    private static final float ENTITY_RENDER_DISTANCE = 120.0f;

    // Chunk rendering
    private ChunkRenderer chunkRenderer;

    // Render layer management
    private final Map<RenderLayer, BufferBuilder> buffers = new HashMap<>();
    private final List<RenderLayer> renderOrder = new ArrayList<>();

    private int flushesThisFrame = 0;

    private boolean isInThirdPerson = false;

    // Culling statistics
    private int entitiesRendered = 0;
    private int entitiesCulled = 0;

    // Debug flags
    private DisplayDebugInfo debug;

    public MasterRenderer(StrataClient client) {
        this.client = client;
        this.camera = client.getCamera();
        this.poseStack = new MatrixStack();
        this.modelRenderer = new ModelRenderer();
        this.guiRenderer = new GuiRenderer();
        this.entityRenderDispatcher = new EntityRenderDispatcher();
        this.debug = client.getDebugInfo();
    }

    /**
     * Initializes chunk renderer after world is created.
     * Call this after the world is set up.
     */
    public void initChunkRenderer(World world) {
        this.chunkRenderer = new ChunkRenderer(world.getChunkManager());
        LOGGER.info("Chunk renderer initialized");
    }


    @Override
    public void render(float partialTicks, float deltaTime) {
        flushesThisFrame = 0;
        entitiesRendered = 0;
        entitiesCulled = 0;

        this.camera.update(client.getPlayer(), client.getWindow(), partialTicks, isInThirdPerson);

        // 2. Clear screen and prepare for rendering
        preRender(partialTicks, deltaTime);

        // 3. Setup shader and uniforms
        if (!setupShaderUniforms()) {
            return; // Shader failed to load
        }

        // 4. Render the world (entities, blocks, etc.)
        renderWorld(partialTicks, deltaTime);

        // 5. Render UI and overlays
        postRender(partialTicks, deltaTime);

    }

    /**
     * Sets up shader uniforms for the current frame.
     * @return true if successful, false if shader loading failed
     */
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

    /**
     * Main world rendering pass.
     */
    private void renderWorld(float partialTicks, float deltaTime) {
        this.client.getWindow().setRenderPhase("Render World");

        clearBuffers();

        if (chunkRenderer != null) {
            renderChunks(partialTicks);
        }

        renderEntities(partialTicks, deltaTime);

        flushBuffers(); // Final flush
    }

    /**
     * Renders chunks with frustum culling.
     */
    private void renderChunks(float partialTicks) {
        if (chunkRenderer == null) {
            return;
        }

        this.client.getWindow().setRenderPhase("Render Chunks");
        chunkRenderer.render(camera, partialTicks);

        if (debug.showRenderCullingDebug()) {
            ChunkRenderer.RenderStats stats = chunkRenderer.getStats();
            LOGGER.debug("Chunk rendering: {}", stats);
        }
    }

    /**
     * Renders all entities in the world.
     */
    private void renderEntities(float partialTicks, float deltaTime) {
        World world = client.getWorld();
        if (world == null) {
            return;
        }

        Entity cameraEntity = client.getCameraEntity();

        float camX = (float) lerp(cameraEntity.prevX, cameraEntity.getPosition().getX(), partialTicks);
        float camY = (float) lerp(cameraEntity.prevY, cameraEntity.getPosition().getY(), partialTicks);
        float camZ = (float) lerp(cameraEntity.prevZ, cameraEntity.getPosition().getZ(), partialTicks);

        for (Entity entity : world.getEntities()) {
            EntityRenderer<Entity> renderer = entityRenderDispatcher.getRenderer(entity);

            if (renderer == null) {
                continue;
            }

            // Interpolate entity position
            double x = lerp(entity.prevX, entity.getPosition().getX(), partialTicks);
            double y = lerp(entity.prevY, entity.getPosition().getY(), partialTicks);
            double z = lerp(entity.prevZ, entity.getPosition().getZ(), partialTicks);

            // Distance culling
            float dx = (float) (x - camX);
            float dy = (float) (y - camY);
            float dz = (float) (z - camZ);
            float distSq = dx * dx + dy * dy + dz * dz;

            if (distSq > ENTITY_RENDER_DISTANCE * ENTITY_RENDER_DISTANCE) {
                entitiesCulled++;
                continue;
            }

            // Frustum culling - test entity bounding sphere
            float entityRadius = entity.getWidth(); // Use entity width as approximate radius
            if (!camera.isSphereVisible((float) x, (float) y, (float) z, entityRadius)) {
                entitiesCulled++;
                continue;
            }

            // Additional renderer-specific culling
            if (!renderer.shouldRender(entity, camX, camY, camZ)) {
                entitiesCulled++;
                continue;
            }

            // Entity is visible, prepare to render
            checkBuffersBeforeRender();

            float yaw = lerpAngle(entity.prevYaw, entity.getYaw(), partialTicks);
            float pitch = lerpAngle(entity.prevPitch, entity.getPitch(), partialTicks);

            MatrixStack entityPoseStack = new MatrixStack();
            entityPoseStack.push();

            entityPoseStack.translate((float) x, (float) y, (float) z);
            entityPoseStack.rotate(yaw, 0, 1, 0);
            entityPoseStack.rotate(pitch, 1, 0, 0);

            // Render the entity
            renderer.render(entity, partialTicks, entityPoseStack);

            entityPoseStack.pop();
            entitiesRendered++;
        }

        if (debug.showRenderCullingDebug()) {
            LOGGER.debug("Entity rendering: {} rendered, {} culled",
                    entitiesRendered, entitiesCulled);
        }
    }

    /**
     * Checks all active buffers and flushes any that are nearly full.
     * This prevents BufferOverflowException while maintaining batching efficiency.
     */
    private void checkBuffersBeforeRender() {
        for (Map.Entry<RenderLayer, BufferBuilder> entry : buffers.entrySet()) {
            BufferBuilder buffer = entry.getValue();

            if (!buffer.isBuilding()) {
                continue;
            }

            float usage = buffer.getUsage();

            if (usage > FLUSH_THRESHOLD) {
                RenderLayer layer = entry.getKey();

                // Flush this buffer
                flushSingleBuffer(layer, buffer);
                flushesThisFrame++;

                // Start fresh
                buffer.begin(VertexFormat.POSITION_TEXTURE_COLOR);
            }
        }
    }

    /**
     * Flushes a single buffer without affecting others.
     */
    private void flushSingleBuffer(RenderLayer layer, BufferBuilder buffer) {
        if (!buffer.isBuilding() || buffer.getVertexCount() == 0) {
            return;
        }

        layer.setup(camera);
        Tessellator.getInstance().draw(buffer);
        layer.clean();
    }

    @Override
    public void preRender(float partialTicks, float deltaTime) {
        this.client.getWindow().setRenderPhase("Pre Render");
        RenderSystem.clear(0.5f, 0.7f, 0.9f, 1.0f);
    }

    @Override
    public void postRender(float partialTicks, float deltaTime) {
        this.client.getWindow().setRenderPhase("Post Render");
    }

    /**
     * Clears all buffers and prepares them for the new frame.
     */
    private void clearBuffers() {
        // Reset buffer state for new frame
        for (BufferBuilder builder : buffers.values()) {
            if (builder.isBuilding()) {
                // If still building from last frame, finish it
                builder.end();
            }
            builder.reset();
        }
    }
    /**
     * Flushes all accumulated geometry to the GPU.
     * This is where batched rendering happens.
     */
    private void flushBuffers() {
        List<Map.Entry<RenderLayer, BufferBuilder>> sortedEntries = new ArrayList<>(buffers.entrySet());
        sortedEntries.sort((a, b) -> {
            boolean aTranslucent = a.getKey().isTranslucent();
            boolean bTranslucent = b.getKey().isTranslucent();
            if (aTranslucent != bTranslucent) {
                return aTranslucent ? 1 : -1;
            }
            return 0;
        });

        for (Map.Entry<RenderLayer, BufferBuilder> entry : sortedEntries) {
            flushSingleBuffer(entry.getKey(), entry.getValue());
        }
    }

    /**
     * Gets or creates a buffer for the specified render layer.
     * Used by entity renderers to accumulate geometry.
     */
    public BufferBuilder getBuffer(RenderLayer layer) {
        return buffers.computeIfAbsent(layer, l -> {
            BufferBuilder buffer = new BufferBuilder(BUFFER_SIZE);
            renderOrder.add(l);
            return buffer;
        });
    }

    // Getters
    public ModelRenderer getModelRenderer() {
        return modelRenderer;
    }

    public EntityRenderDispatcher getEntityRenderDispatcher() {
        return entityRenderDispatcher;
    }

    public Camera getCamera() {
        return camera;
    }

    public void setThirdPerson(boolean thirdPerson) {
        this.isInThirdPerson = thirdPerson;
    }

    public boolean isThirdPerson() {
        return isInThirdPerson;
    }

    public ChunkRenderer getChunkRenderer() {
        return chunkRenderer;
    }

    /**
     * Reloads all rendering resources.
     * Call this when resource packs are changed.
     */
    public void reload() {
        ModelManager.clearCache();
        RenderLayers.clearCache();
        if (chunkRenderer != null) {
            chunkRenderer.clearCache();
        }
        LOGGER.info("Reloaded renderer resources");
    }

}