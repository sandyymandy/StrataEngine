package engine.strata.client.render.renderer;

import engine.helios.*;
import engine.strata.client.StrataClient;
import engine.strata.client.render.Camera;
import engine.strata.client.render.renderer.entity.util.EntityRenderDispatcher;
import engine.strata.client.render.renderer.entity.util.EntityRenderer;
import engine.strata.client.render.util.BasicRenderer;
import engine.strata.client.render.util.rendercommand.RenderCommandQueue;
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
    private final RenderCommandQueue renderCommandQueue;
    private final EntityRenderDispatcher entityRenderDispatcher;
    private final Map<RenderLayer, BufferBuilder> layers = new HashMap<>();
    private boolean isInThirdPerson = false;

    public MasterRenderer(StrataClient client) {
        this.client = client;
        this.camera = new Camera();
        this.poseStack = new MatrixStack();
        this.modelRenderer = new ModelRenderer();
        this.guiRenderer = new GuiRenderer();
        this.renderCommandQueue = new RenderCommandQueue();
        this.entityRenderDispatcher = new EntityRenderDispatcher();
    }

    public void render(float partialTicks, float deltaTime) {
        // 1. Setup Camera and Global States FIRST
        this.camera.update(client.getCameraEntity(), client.getWindow(), partialTicks, isInThirdPerson);

        // 2. Swap the command queue
        renderCommandQueue.swap();

        // 3. Clear screen
        preRender(partialTicks, deltaTime);

        // 4. 3D World Pass
        renderWorld(partialTicks);

        // 5. UI/Overlay Pass
        postRender(partialTicks, deltaTime);
    }

    /**
     * Called from the Logic Thread to prepare all entity render commands.
     * This runs once per logic tick and submits commands to the queue.
     */
    public void prepareEntityRenders(World world, float partialTicks) {
        Entity cameraEntity = client.getCameraEntity();
        float camX = (float) cameraEntity.getX();
        float camY = (float) cameraEntity.getY();
        float camZ = (float) cameraEntity.getZ();

        // Iterate through all entities in the world
        for (Entity entity : world.getEntities()) {
            EntityRenderer<Entity> renderer = entityRenderDispatcher.getRenderer(entity);

            if (renderer == null) {
                // No renderer registered for this entity type
                continue;
            }

            // Check if entity should be rendered (culling)
            if (!renderer.shouldRender(entity, camX, camY, camZ)) {
                continue;
            }

            // Create a new pose stack for this entity
            MatrixStack entityPoseStack = new MatrixStack();
            entityPoseStack.push();

            // Interpolate position for smooth movement between ticks
            double x = lerp(entity.prevX, entity.getX(), partialTicks);
            double y = lerp(entity.prevY, entity.getY(), partialTicks);
            double z = lerp(entity.prevZ, entity.getZ(), partialTicks);

            // Position the entity in world space
            entityPoseStack.translate((float)x, (float)y, (float)z);

            // Interpolate rotation
            float yaw = lerpAngle(entity.prevYaw, entity.getYaw(), partialTicks);
            float pitch = lerpAngle(entity.prevPitch, entity.getPitch(), partialTicks);

            // Apply yaw rotation (entities face along Z axis in model space)
            entityPoseStack.rotate(yaw, 0, 1, 0);

            // Let the renderer submit its commands
            renderer.render(entity, partialTicks, entityPoseStack, renderCommandQueue);

            entityPoseStack.pop();
        }
    }

    public EntityRenderDispatcher getEntityRenderDispatcher() {
        return entityRenderDispatcher;
    }

    private void renderWorld(float partialTicks) {
        RenderSystem.assertOnRenderThread(); // Safety check
        this.client.getWindow().setRenderPhase("Render");

        ShaderManager.use(Identifier.ofEngine("generic_3d"));
        ShaderStack shaderStack = ShaderManager.getCurrent();
        if (shaderStack == null) return;

        // Load View/Projection matrices to GPU
        shaderStack.setUniform("u_Projection", camera.getProjectionMatrix());
        shaderStack.setUniform("u_View", camera.getViewMatrix());

        // Render test cubes FIRST (so we can see something)
        renderSceneCubes();

        // Begin all layer buffers with the appropriate format
        for (BufferBuilder builder : layers.values()) {
            if (!builder.isBuilding()) {
                builder.begin(VertexFormat.POSITION_TEXTURE_COLOR);
            }
        }

        // Run all entity render commands (they will write to the layer buffers)
        renderCommandQueue.flush();

        // Render all buffered layers
        for (Map.Entry<RenderLayer, BufferBuilder> entry : layers.entrySet()) {
            RenderLayer layer = entry.getKey();
            BufferBuilder bufferBuilder = entry.getValue();

            if (bufferBuilder.getVertexCount() > 0) {
                layer.setup(camera); // Bind texture/shader and set Uniforms
                Tessellator.getInstance().draw(bufferBuilder);
                layer.clean();
            }
        }
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
        RenderSystem.assertOnRenderThread(); // Safety check
        this.client.getWindow().setRenderPhase("Pre Render");

        RenderSystem.clear(0.5f, 0.7f, 0.9f, 1.0f);
    }

    @Override
    public void postRender(float partialTicks, float deltaTime) {
        RenderSystem.assertOnRenderThread(); // Safety check
        this.client.getWindow().setRenderPhase("Post Render");
    }

    public RenderCommandQueue getQueue() { return renderCommandQueue; }
    public ModelRenderer getModelRenderer() { return modelRenderer; }

    public BufferBuilder getBuffer(RenderLayer layer) {
        return layers.computeIfAbsent(layer, l -> new BufferBuilder(2097152));
    }

    private void drawCube(BufferBuilder builder, Matrix4f matrix, float x, float y, float z) {
        // Front face (CCW when viewed from front)
        builder.vertex(matrix, x-0.5f, y-0.5f, z+0.5f).color(1, 0, 0, 1).next();
        builder.vertex(matrix, x+0.5f, y-0.5f, z+0.5f).color(1, 0, 0, 1).next();
        builder.vertex(matrix, x+0.5f, y+0.5f, z+0.5f).color(1, 0, 0, 1).next();

        builder.vertex(matrix, x+0.5f, y+0.5f, z+0.5f).color(1, 0, 0, 1).next();
        builder.vertex(matrix, x-0.5f, y+0.5f, z+0.5f).color(1, 0, 0, 1).next();
        builder.vertex(matrix, x-0.5f, y-0.5f, z+0.5f).color(1, 0, 0, 1).next();

        // Back face (CCW when viewed from front)
        builder.vertex(matrix, x+0.5f, y-0.5f, z-0.5f).color(0, 1, 0, 1).next();
        builder.vertex(matrix, x-0.5f, y-0.5f, z-0.5f).color(0, 1, 0, 1).next();
        builder.vertex(matrix, x-0.5f, y+0.5f, z-0.5f).color(0, 1, 0, 1).next();

        builder.vertex(matrix, x-0.5f, y+0.5f, z-0.5f).color(0, 1, 0, 1).next();
        builder.vertex(matrix, x+0.5f, y+0.5f, z-0.5f).color(0, 1, 0, 1).next();
        builder.vertex(matrix, x+0.5f, y-0.5f, z-0.5f).color(0, 1, 0, 1).next();

        // Top face (CCW when viewed from above)
        builder.vertex(matrix, x-0.5f, y+0.5f, z+0.5f).color(0, 0, 1, 1).next();
        builder.vertex(matrix, x+0.5f, y+0.5f, z+0.5f).color(0, 0, 1, 1).next();
        builder.vertex(matrix, x+0.5f, y+0.5f, z-0.5f).color(0, 0, 1, 1).next();

        builder.vertex(matrix, x+0.5f, y+0.5f, z-0.5f).color(0, 0, 1, 1).next();
        builder.vertex(matrix, x-0.5f, y+0.5f, z-0.5f).color(0, 0, 1, 1).next();
        builder.vertex(matrix, x-0.5f, y+0.5f, z+0.5f).color(0, 0, 1, 1).next();

        // Bottom face (CCW when viewed from below)
        builder.vertex(matrix, x-0.5f, y-0.5f, z-0.5f).color(1, 1, 0, 1).next();
        builder.vertex(matrix, x+0.5f, y-0.5f, z-0.5f).color(1, 1, 0, 1).next();
        builder.vertex(matrix, x+0.5f, y-0.5f, z+0.5f).color(1, 1, 0, 1).next();

        builder.vertex(matrix, x+0.5f, y-0.5f, z+0.5f).color(1, 1, 0, 1).next();
        builder.vertex(matrix, x-0.5f, y-0.5f, z+0.5f).color(1, 1, 0, 1).next();
        builder.vertex(matrix, x-0.5f, y-0.5f, z-0.5f).color(1, 1, 0, 1).next();

        // Right face (CCW when viewed from right)
        builder.vertex(matrix, x+0.5f, y-0.5f, z+0.5f).color(1, 0, 1, 1).next();
        builder.vertex(matrix, x+0.5f, y-0.5f, z-0.5f).color(1, 0, 1, 1).next();
        builder.vertex(matrix, x+0.5f, y+0.5f, z-0.5f).color(1, 0, 1, 1).next();

        builder.vertex(matrix, x+0.5f, y+0.5f, z-0.5f).color(1, 0, 1, 1).next();
        builder.vertex(matrix, x+0.5f, y+0.5f, z+0.5f).color(1, 0, 1, 1).next();
        builder.vertex(matrix, x+0.5f, y-0.5f, z+0.5f).color(1, 0, 1, 1).next();

        // Left face (CCW when viewed from left)
        builder.vertex(matrix, x-0.5f, y-0.5f, z-0.5f).color(0, 1, 1, 1).next();
        builder.vertex(matrix, x-0.5f, y-0.5f, z+0.5f).color(0, 1, 1, 1).next();
        builder.vertex(matrix, x-0.5f, y+0.5f, z+0.5f).color(0, 1, 1, 1).next();

        builder.vertex(matrix, x-0.5f, y+0.5f, z+0.5f).color(0, 1, 1, 1).next();
        builder.vertex(matrix, x-0.5f, y+0.5f, z-0.5f).color(0, 1, 1, 1).next();
        builder.vertex(matrix, x-0.5f, y-0.5f, z-0.5f).color(0, 1, 1, 1).next();
    }

    public Camera getCamera() { return camera; }
}