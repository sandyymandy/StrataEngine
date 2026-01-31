package engine.strata.client.render.renderer;

import engine.helios.*;
import engine.strata.client.StrataClient;
import engine.strata.client.render.Camera;
import engine.strata.client.render.RenderLayers;
import engine.strata.client.render.model.ModelManager;
import engine.strata.client.render.model.StrataModel;
import engine.strata.client.render.renderer.entity.util.EntityRenderDispatcher;
import engine.strata.client.render.renderer.entity.util.EntityRenderer;
import engine.strata.client.render.util.BasicRenderer;
import engine.strata.entity.Entity;
import engine.strata.util.Identifier;
import engine.strata.world.World;
import org.joml.Matrix4f;

import java.util.HashMap;
import java.util.Map;

import static engine.strata.util.math.Math.lerp;
import static engine.strata.util.math.Math.lerpAngle;

public class MasterRenderer implements BasicRenderer {
    private final StrataClient client;
    private final Camera camera;
    private final MatrixStack poseStack;
    private final ModelRenderer modelRenderer;
    private final GuiRenderer guiRenderer;
    private final EntityRenderDispatcher entityRenderDispatcher;
    private final Map<RenderLayer, BufferBuilder> layers = new HashMap<>();
    private boolean isInThirdPerson = false;

    // Test model fields
    private StrataModel testZombieModel;
    private Map<String, Identifier> testZombieSkin;
    private boolean modelLoadAttempted = false;

    public MasterRenderer(StrataClient client) {
        this.client = client;
        this.camera = new Camera();
        this.poseStack = new MatrixStack();
        this.modelRenderer = new ModelRenderer();
        this.guiRenderer = new GuiRenderer();
        this.entityRenderDispatcher = new EntityRenderDispatcher();
    }

    public void render(float partialTicks, float deltaTime) {
        // 1. Setup Camera
        this.camera.update(client.getCameraEntity(), client.getWindow(), partialTicks, isInThirdPerson);

        // 2. Clear screen
        preRender(partialTicks, deltaTime);

        // 3. Setup shader
        ShaderStack shaderStack = ShaderManager.use(Identifier.ofEngine("generic_3d"));
        if (shaderStack == null) {
            LOGGER.error("Shader not loaded! Check shader files in resources/shaders/");
            return;
        }

        // Load View/Projection matrices to GPU
        shaderStack.setUniform("u_Projection", camera.getProjectionMatrix());
        shaderStack.setUniform("u_View", camera.getViewMatrix());

        // 4. Render world
        renderWorld(partialTicks);

        // 5. UI/Overlay Pass
        postRender(partialTicks, deltaTime);
    }

    private void renderWorld(float partialTicks) {
        this.client.getWindow().setRenderPhase("Render");

        // Render test cubes
        renderSceneCubes();

        // NEW: Render test zombie model directly
        renderTestZombieModel();

        // Render entities (commented out for now)
        // renderEntities(partialTicks);
    }

    /**
     * Renders a zombie model directly without using the entity system.
     * Great for testing model loading!
     * Uses vertex colors instead of textures for now.
     */
    private void renderTestZombieModel() {
        // Try to load model once
        if (!modelLoadAttempted) {
            modelLoadAttempted = true;
            try {
                LOGGER.info("Attempting to load model...");
                testZombieModel = ModelManager.getModel(Identifier.ofEngine("bia"));
                LOGGER.info("Successfully loaded model!");
            } catch (Exception e) {
                LOGGER.error("Failed to load zombie model: {}", e.getMessage());
                LOGGER.info("This is normal if you don't have zombie.strmodel and zombie.strskin files yet");
                testZombieModel = null;
                return;
            }
        }

        // If model didn't load, skip rendering
        if (testZombieModel == null) {
            return;
        }

        // Use the Tessellator directly (like cubes) - no textures needed!
        Tessellator tess = Tessellator.getInstance();
        BufferBuilder builder = tess.getBuffer();

        // Render 3 zombie models in different positions
        renderSingleZombie(builder, 0, 0, -3);   // Center
//        renderSingleZombie(builder, -2, 0, -3);  // Left
//        renderSingleZombie(builder, 2, 0, -3);   // Right
        tess.draw();
    }

    /**
     * Renders a single zombie model at the given position.
     */
    private void renderSingleZombie(BufferBuilder buffer, float x, float y, float z) {
        poseStack.push();

        // Position in world
        poseStack.translate(x, y, z);

        // Scale down from 16x16x16 model space to world space
        poseStack.scale(1.0f / 16.0f, 1.0f / 16.0f, 1.0f / 16.0f);

        // Render the model
        modelRenderer.render(testZombieModel, poseStack, buffer);

        poseStack.pop();
    }

    /**
     * Apply a simple test animation to the model.
     */
    private void applyTestAnimation(StrataModel model, float time) {
        Map<String, StrataModel.Bone> bones = model.getAllBones();

        // Reset all animations
        for (StrataModel.Bone bone : bones.values()) {
            bone.resetAnimation();
        }

        // Example: Animate head to look around
        StrataModel.Bone head = bones.get("head");
        if (head != null) {
            float headRotation = (float) Math.sin(time * 2) * 30; // -30 to +30 degrees
            head.setAnimRotation(0, headRotation, 0);
        }

        // Example: Animate arms to swing
        StrataModel.Bone rightArm = bones.get("rightArm");
        if (rightArm != null) {
            float armSwing = (float) Math.sin(time * 3) * 45;
            rightArm.setAnimRotation(armSwing, 0, 0);
        }

        StrataModel.Bone leftArm = bones.get("leftArm");
        if (leftArm != null) {
            float armSwing = (float) Math.sin(time * 3 + Math.PI) * 45; // Opposite phase
            leftArm.setAnimRotation(armSwing, 0, 0);
        }
    }

    private void renderEntities(float partialTicks) {
        World world = client.getWorld();
        Entity cameraEntity = client.getCameraEntity();
        float camX = (float) cameraEntity.getX();
        float camY = (float) cameraEntity.getY();
        float camZ = (float) cameraEntity.getZ();

        // Begin all layer buffers before rendering
        for (BufferBuilder builder : layers.values()) {
            if (!builder.isBuilding()) {
                builder.begin(VertexFormat.POSITION_TEXTURE_COLOR);
            }
        }

        // Render each entity
        for (Entity entity : world.getEntities()) {
            EntityRenderer<Entity> renderer = entityRenderDispatcher.getRenderer(entity);

            if (renderer == null) {
                continue;
            }

            // Check if entity should be rendered (culling)
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

            // Render the entity directly (no command queue)
            MatrixStack entityPoseStack = new MatrixStack();
            entityPoseStack.push();
            entityPoseStack.translate((float)x, (float)y, (float)z);
            entityPoseStack.rotate(yaw, 0, 1, 0);

            // Render directly to buffers
            renderEntityDirect(renderer, entity, partialTicks, entityPoseStack);

            entityPoseStack.pop();
        }

        // Draw all layer buffers
        for (Map.Entry<RenderLayer, BufferBuilder> entry : layers.entrySet()) {
            RenderLayer layer = entry.getKey();
            BufferBuilder bufferBuilder = entry.getValue();

            if (bufferBuilder.getVertexCount() > 0) {
                layer.setup(camera);
                Tessellator.getInstance().draw(bufferBuilder);
                layer.clean();
            }
        }
    }

    /**
     * Render an entity directly without using the command queue
     */
    private void renderEntityDirect(EntityRenderer<Entity> renderer, Entity entity,
                                    float partialTicks, MatrixStack poseStack) {
        renderer.render(entity, partialTicks, poseStack);
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
        RenderSystem.clear(0.5f, 0.7f, 0.9f, 1.0f);
    }

    @Override
    public void postRender(float partialTicks, float deltaTime) {
        this.client.getWindow().setRenderPhase("Post Render");
    }

    public ModelRenderer getModelRenderer() {
        return modelRenderer;
    }

    public BufferBuilder getBuffer(RenderLayer layer) {
        return layers.computeIfAbsent(layer, l -> new BufferBuilder(2097152));
    }

    public EntityRenderDispatcher getEntityRenderDispatcher() {
        return entityRenderDispatcher;
    }

    public Camera getCamera() {
        return camera;
    }

    private void drawCube(BufferBuilder builder, Matrix4f matrix, float x, float y, float z) {
        // Front face
        builder.vertex(matrix, x-0.5f, y-0.5f, z+0.5f).color(1, 0, 0, 1).next();
        builder.vertex(matrix, x+0.5f, y-0.5f, z+0.5f).color(1, 0, 0, 1).next();
        builder.vertex(matrix, x+0.5f, y+0.5f, z+0.5f).color(1, 0, 0, 1).next();

        builder.vertex(matrix, x+0.5f, y+0.5f, z+0.5f).color(1, 0, 0, 1).next();
        builder.vertex(matrix, x-0.5f, y+0.5f, z+0.5f).color(1, 0, 0, 1).next();
        builder.vertex(matrix, x-0.5f, y-0.5f, z+0.5f).color(1, 0, 0, 1).next();

        // Back face
        builder.vertex(matrix, x+0.5f, y-0.5f, z-0.5f).color(0, 1, 0, 1).next();
        builder.vertex(matrix, x-0.5f, y-0.5f, z-0.5f).color(0, 1, 0, 1).next();
        builder.vertex(matrix, x-0.5f, y+0.5f, z-0.5f).color(0, 1, 0, 1).next();

        builder.vertex(matrix, x-0.5f, y+0.5f, z-0.5f).color(0, 1, 0, 1).next();
        builder.vertex(matrix, x+0.5f, y+0.5f, z-0.5f).color(0, 1, 0, 1).next();
        builder.vertex(matrix, x+0.5f, y-0.5f, z-0.5f).color(0, 1, 0, 1).next();

        // Top face
        builder.vertex(matrix, x-0.5f, y+0.5f, z+0.5f).color(0, 0, 1, 1).next();
        builder.vertex(matrix, x+0.5f, y+0.5f, z+0.5f).color(0, 0, 1, 1).next();
        builder.vertex(matrix, x+0.5f, y+0.5f, z-0.5f).color(0, 0, 1, 1).next();

        builder.vertex(matrix, x+0.5f, y+0.5f, z-0.5f).color(0, 0, 1, 1).next();
        builder.vertex(matrix, x-0.5f, y+0.5f, z-0.5f).color(0, 0, 1, 1).next();
        builder.vertex(matrix, x-0.5f, y+0.5f, z+0.5f).color(0, 0, 1, 1).next();

        // Bottom face
        builder.vertex(matrix, x-0.5f, y-0.5f, z-0.5f).color(1, 1, 0, 1).next();
        builder.vertex(matrix, x+0.5f, y-0.5f, z-0.5f).color(1, 1, 0, 1).next();
        builder.vertex(matrix, x+0.5f, y-0.5f, z+0.5f).color(1, 1, 0, 1).next();

        builder.vertex(matrix, x+0.5f, y-0.5f, z+0.5f).color(1, 1, 0, 1).next();
        builder.vertex(matrix, x-0.5f, y-0.5f, z+0.5f).color(1, 1, 0, 1).next();
        builder.vertex(matrix, x-0.5f, y-0.5f, z-0.5f).color(1, 1, 0, 1).next();

        // Right face
        builder.vertex(matrix, x+0.5f, y-0.5f, z+0.5f).color(1, 0, 1, 1).next();
        builder.vertex(matrix, x+0.5f, y-0.5f, z-0.5f).color(1, 0, 1, 1).next();
        builder.vertex(matrix, x+0.5f, y+0.5f, z-0.5f).color(1, 0, 1, 1).next();

        builder.vertex(matrix, x+0.5f, y+0.5f, z-0.5f).color(1, 0, 1, 1).next();
        builder.vertex(matrix, x+0.5f, y+0.5f, z+0.5f).color(1, 0, 1, 1).next();
        builder.vertex(matrix, x+0.5f, y-0.5f, z+0.5f).color(1, 0, 1, 1).next();

        // Left face
        builder.vertex(matrix, x-0.5f, y-0.5f, z-0.5f).color(0, 1, 1, 1).next();
        builder.vertex(matrix, x-0.5f, y-0.5f, z+0.5f).color(0, 1, 1, 1).next();
        builder.vertex(matrix, x-0.5f, y+0.5f, z+0.5f).color(0, 1, 1, 1).next();

        builder.vertex(matrix, x-0.5f, y+0.5f, z+0.5f).color(0, 1, 1, 1).next();
        builder.vertex(matrix, x-0.5f, y+0.5f, z-0.5f).color(0, 1, 1, 1).next();
        builder.vertex(matrix, x-0.5f, y-0.5f, z-0.5f).color(0, 1, 1, 1).next();
    }

    private static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger("MasterRenderer");
}