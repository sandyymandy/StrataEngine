package engine.strata.client.render.renderer;

import engine.helios.*;
import engine.strata.client.StrataClient;
import engine.strata.client.render.Camera;
import engine.strata.client.render.RenderLayers;
import engine.strata.client.render.model.ModelManager;
import engine.strata.client.render.model.StrataModel;
import engine.strata.client.render.model.StrataSkin;
import engine.strata.client.render.renderer.entity.util.EntityRenderDispatcher;
import engine.strata.client.render.renderer.entity.util.EntityRenderer;
import engine.strata.client.render.util.BasicRenderer;
import engine.strata.entity.Entity;
import engine.strata.util.Identifier;
import engine.strata.world.World;
import org.joml.Matrix4f;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

import static engine.strata.util.math.Math.lerp;
import static engine.strata.util.math.Math.lerpAngle;

/**
 * Master renderer that coordinates all rendering passes.
 * Manages render layers, batching, and the rendering pipeline.
 */
public class MasterRenderer implements BasicRenderer {
    private static final Logger LOGGER = LoggerFactory.getLogger("MasterRenderer");

    private final StrataClient client;
    private final Camera camera;
    private final MatrixStack poseStack;
    private final ModelRenderer modelRenderer;
    private final GuiRenderer guiRenderer;
    private final EntityRenderDispatcher entityRenderDispatcher;

    // Render layer management
    private final Map<RenderLayer, BufferBuilder> buffers = new HashMap<>();
    private final List<RenderLayer> renderOrder = new ArrayList<>();

    private boolean isInThirdPerson = false;

    // Test model fields
    private StrataModel testModel;
    private StrataSkin testSkin;
    private boolean testModelLoadAttempted = false;

    public MasterRenderer(StrataClient client) {
        this.client = client;
        this.camera = new Camera();
        this.poseStack = new MatrixStack();
        this.modelRenderer = new ModelRenderer();
        this.guiRenderer = new GuiRenderer();
        this.entityRenderDispatcher = new EntityRenderDispatcher();
    }

    @Override
    public void render(float partialTicks, float deltaTime) {
        // 1. Update camera
        this.camera.update(client.getCameraEntity(), client.getWindow(), partialTicks, isInThirdPerson);

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

        // Load view/projection matrices
        shaderStack.setUniform("u_Projection", camera.getProjectionMatrix());
        shaderStack.setUniform("u_View", camera.getViewMatrix());

        return true;
    }

    /**
     * Main world rendering pass.
     */
    private void renderWorld(float partialTicks, float deltaTime) {
        this.client.getWindow().setRenderPhase("Render World");

        // Clear previous frame's buffers
        clearBuffers();

        // Render entities (populates buffers)
        renderEntities(partialTicks, deltaTime);

        // Optional: Render test models for debugging
        renderSceneCubes();
        renderTestModel(); // Now uses proper render layers!

        // Flush all accumulated geometry to GPU
        flushBuffers();
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
        float camX = (float) cameraEntity.getX();
        float camY = (float) cameraEntity.getY();
        float camZ = (float) cameraEntity.getZ();

        // Render each entity
        for (Entity entity : world.getEntities()) {
            EntityRenderer<Entity> renderer = entityRenderDispatcher.getRenderer(entity);

            if (renderer == null) {
                continue; // No renderer registered for this entity type
            }

            // Frustum culling
            if (!renderer.shouldRender(entity, camX, camY, camZ)) {
                continue;
            }

            // Interpolate position for smooth movement
            double x = lerp(entity.prevX, entity.getX(), partialTicks);
            double y = lerp(entity.prevY, entity.getY(), partialTicks);
            double z = lerp(entity.prevZ, entity.getZ(), partialTicks);

            // Interpolate rotation
            float yaw = lerpAngle(entity.prevYaw, entity.getYaw(), partialTicks);
            float pitch = lerpAngle(entity.prevPitch, entity.getPitch(), partialTicks);

            // Create entity pose stack
            MatrixStack entityPoseStack = new MatrixStack();
            entityPoseStack.push();

            // Translate to entity position (relative to camera)
            entityPoseStack.translate(
                    (float) (x - camX),
                    (float) (y - camY),
                    (float) (z - camZ)
            );

            // Apply rotation
            entityPoseStack.rotate(yaw, 0, 1, 0);
            entityPoseStack.rotate(pitch, 1, 0, 0);

            // Render the entity
            renderer.render(entity, partialTicks, entityPoseStack);

            entityPoseStack.pop();
        }
    }

    /**
     * Renders a test model for debugging.
     * FIXED: Now uses the proper render layer system!
     */
    private void renderTestModel() {
        // Try to load model once
        if (!testModelLoadAttempted) {
            testModelLoadAttempted = true;
            try {
                LOGGER.info("Loading test model...");
                testModel = ModelManager.getModel(Identifier.ofEngine("bia"));
                testSkin = ModelManager.getSkin(Identifier.ofEngine("bia"));

                if (testModel != null && testSkin != null) {
                    LOGGER.info("Successfully loaded test model!");
                    LOGGER.info("Skin has {} texture slot(s)", testSkin.textures().size());

                    // Log texture information
                    testSkin.textures().forEach((slot, texData) -> {
                        LOGGER.info("  Texture slot '{}': {} ({}x{})",
                                slot, texData.path(), texData.width(), texData.height());
                    });
                }
            } catch (Exception e) {
                LOGGER.warn("Test model not available: {}", e.getMessage());
                testModel = null;
                testSkin = null;
            }
        }

        if (testModel == null || testSkin == null) {
            return;
        }

        poseStack.push();
        poseStack.translate(0, 0, -5); // Position in front of camera
        poseStack.scale(1.0f / 16.0f, 1.0f / 16.0f, 1.0f / 16.0f);

        // Render each texture layer (important for multi-texture models!)
        for (Map.Entry<String, StrataSkin.TextureData> entry : testSkin.textures().entrySet()) {
            String slot = entry.getKey();
            StrataSkin.TextureData texData = entry.getValue();

            // Get the appropriate render layer
            RenderLayer layer = RenderLayers.getLayerForSlot(
                    texData.path(),
                    texData.translucent()
            );

            // Get the buffer for this layer
            BufferBuilder buffer = getBuffer(layer);

            // Ensure buffer is building
            if (!buffer.isBuilding()) {
                buffer.begin(VertexFormat.POSITION_TEXTURE_COLOR);
            }

            // Render the model into this buffer
            modelRenderer.render(testModel, testSkin, poseStack, buffer);
        }

        poseStack.pop();
    }

    private void renderSceneCubes() {
        Tessellator tess = Tessellator.getInstance();
        BufferBuilder builder = tess.getBuffer();

        for (int x = -2; x <= 2; x++) {
            for (int z = -5; z >= -10; z--) {
                poseStack.push();
                poseStack.translate(x, 0, z);

                builder.begin(VertexFormat.POSITION_COLOR);
                drawCube(builder, poseStack.peek(), (float) x / 2, 0, (float) z / 2);

                poseStack.pop();
                tess.draw();
            }
        }
    }

    @Override
    public void preRender(float partialTicks, float deltaTime) {
        this.client.getWindow().setRenderPhase("Pre Render");
        // Clear with a nice sky blue
        RenderSystem.clear(0.5f, 0.7f, 0.9f, 1.0f);
    }

    @Override
    public void postRender(float partialTicks, float deltaTime) {
        this.client.getWindow().setRenderPhase("Post Render");
        // UI rendering would go here
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
        }
    }

    /**
     * Flushes all accumulated geometry to the GPU.
     * This is where batched rendering happens.
     */
    private void flushBuffers() {
        // Sort layers to ensure correct render order
        // (e.g., opaque first, then translucent)
        List<Map.Entry<RenderLayer, BufferBuilder>> sortedEntries = new ArrayList<>(buffers.entrySet());
        sortedEntries.sort((a, b) -> {
            // Render opaque before translucent
            boolean aTranslucent = a.getKey().isTranslucent();
            boolean bTranslucent = b.getKey().isTranslucent();
            if (aTranslucent != bTranslucent) {
                return aTranslucent ? 1 : -1;
            }
            return 0;
        });

        // Draw each layer
        for (Map.Entry<RenderLayer, BufferBuilder> entry : sortedEntries) {
            RenderLayer layer = entry.getKey();
            BufferBuilder builder = entry.getValue();

            if (!builder.isBuilding() || builder.getVertexCount() == 0) {
                continue; // Nothing to render
            }

            // Setup GL state for this layer (THIS IS WHERE TEXTURE BINDING HAPPENS!)
            layer.setup(camera);

            // Draw the batched geometry
            Tessellator.getInstance().draw(builder);

            // Cleanup GL state
            layer.clean();
        }
    }

    /**
     * Gets or creates a buffer for the specified render layer.
     * Used by entity renderers to accumulate geometry.
     */
    public BufferBuilder getBuffer(RenderLayer layer) {
        return buffers.computeIfAbsent(layer, l -> {
            // 2MB buffer per layer
            BufferBuilder buffer = new BufferBuilder(2097152);
            renderOrder.add(l);
            LOGGER.debug("Created new buffer for layer: {}", layer.texture());
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

    /**
     * Reloads all rendering resources.
     * Call this when resource packs are changed.
     */
    public void reload() {
        ModelManager.clearCache();
        RenderLayers.clearCache();
        testModelLoadAttempted = false;
        LOGGER.info("Reloaded renderer resources");
    }

    private void drawCube(BufferBuilder builder, Matrix4f matrix, float x, float y, float z) {
        // Front face
        builder.vertex(matrix, x-0.5f, y-0.5f, z+0.5f).color(1, 0, 0, 1).end();
        builder.vertex(matrix, x+0.5f, y-0.5f, z+0.5f).color(1, 0, 0, 1).end();
        builder.vertex(matrix, x+0.5f, y+0.5f, z+0.5f).color(1, 0, 0, 1).end();

        builder.vertex(matrix, x+0.5f, y+0.5f, z+0.5f).color(1, 0, 0, 1).end();
        builder.vertex(matrix, x-0.5f, y+0.5f, z+0.5f).color(1, 0, 0, 1).end();
        builder.vertex(matrix, x-0.5f, y-0.5f, z+0.5f).color(1, 0, 0, 1).end();

        // Back face
        builder.vertex(matrix, x+0.5f, y-0.5f, z-0.5f).color(0, 1, 0, 1).end();
        builder.vertex(matrix, x-0.5f, y-0.5f, z-0.5f).color(0, 1, 0, 1).end();
        builder.vertex(matrix, x-0.5f, y+0.5f, z-0.5f).color(0, 1, 0, 1).end();

        builder.vertex(matrix, x-0.5f, y+0.5f, z-0.5f).color(0, 1, 0, 1).end();
        builder.vertex(matrix, x+0.5f, y+0.5f, z-0.5f).color(0, 1, 0, 1).end();
        builder.vertex(matrix, x+0.5f, y-0.5f, z-0.5f).color(0, 1, 0, 1).end();

        // Top face
        builder.vertex(matrix, x-0.5f, y+0.5f, z+0.5f).color(0, 0, 1, 1).end();
        builder.vertex(matrix, x+0.5f, y+0.5f, z+0.5f).color(0, 0, 1, 1).end();
        builder.vertex(matrix, x+0.5f, y+0.5f, z-0.5f).color(0, 0, 1, 1).end();

        builder.vertex(matrix, x+0.5f, y+0.5f, z-0.5f).color(0, 0, 1, 1).end();
        builder.vertex(matrix, x-0.5f, y+0.5f, z-0.5f).color(0, 0, 1, 1).end();
        builder.vertex(matrix, x-0.5f, y+0.5f, z+0.5f).color(0, 0, 1, 1).end();

        // Bottom face
        builder.vertex(matrix, x-0.5f, y-0.5f, z-0.5f).color(1, 1, 0, 1).end();
        builder.vertex(matrix, x+0.5f, y-0.5f, z-0.5f).color(1, 1, 0, 1).end();
        builder.vertex(matrix, x+0.5f, y-0.5f, z+0.5f).color(1, 1, 0, 1).end();

        builder.vertex(matrix, x+0.5f, y-0.5f, z+0.5f).color(1, 1, 0, 1).end();
        builder.vertex(matrix, x-0.5f, y-0.5f, z+0.5f).color(1, 1, 0, 1).end();
        builder.vertex(matrix, x-0.5f, y-0.5f, z-0.5f).color(1, 1, 0, 1).end();

        // Right face
        builder.vertex(matrix, x+0.5f, y-0.5f, z+0.5f).color(1, 0, 1, 1).end();
        builder.vertex(matrix, x+0.5f, y-0.5f, z-0.5f).color(1, 0, 1, 1).end();
        builder.vertex(matrix, x+0.5f, y+0.5f, z-0.5f).color(1, 0, 1, 1).end();

        builder.vertex(matrix, x+0.5f, y+0.5f, z-0.5f).color(1, 0, 1, 1).end();
        builder.vertex(matrix, x+0.5f, y+0.5f, z+0.5f).color(1, 0, 1, 1).end();
        builder.vertex(matrix, x+0.5f, y-0.5f, z+0.5f).color(1, 0, 1, 1).end();

        // Left face
        builder.vertex(matrix, x-0.5f, y-0.5f, z-0.5f).color(0, 1, 1, 1).end();
        builder.vertex(matrix, x-0.5f, y-0.5f, z+0.5f).color(0, 1, 1, 1).end();
        builder.vertex(matrix, x-0.5f, y+0.5f, z+0.5f).color(0, 1, 1, 1).end();

        builder.vertex(matrix, x-0.5f, y+0.5f, z+0.5f).color(0, 1, 1, 1).end();
        builder.vertex(matrix, x-0.5f, y+0.5f, z-0.5f).color(0, 1, 1, 1).end();
        builder.vertex(matrix, x-0.5f, y-0.5f, z-0.5f).color(0, 1, 1, 1).end();
    }

}