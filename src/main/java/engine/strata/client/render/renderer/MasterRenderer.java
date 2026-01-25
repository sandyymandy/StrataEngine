package engine.strata.client.render.renderer;

import engine.helios.*;
import engine.strata.client.StrataClient;
import engine.strata.client.render.Camera;
import engine.strata.client.render.util.BasicRenderer;
import org.joml.Matrix4f;

public class MasterRenderer implements BasicRenderer {
    private final StrataClient client;
    private final Camera camera;
    private final MatrixStack poseStack;
    private final ModelRenderer modelRenderer;
    private final GuiRenderer guiRenderer;

    private boolean isInThirdPerson = false;

    public MasterRenderer(StrataClient client) {
        this.client = client;
        this.camera = new Camera();
        this.poseStack = new MatrixStack();
        this.modelRenderer = new ModelRenderer();
        this.guiRenderer = new GuiRenderer();
    }

    public void render(float partialTicks, float deltaTime) {
        // 1. Setup Camera and Global States
        this.camera.update(client.getCameraEntity(), client.getWindow(), partialTicks, isInThirdPerson);
        preRender(partialTicks, deltaTime);

        // 2. 3D World Pass
        renderWorld(partialTicks);

        // 3. UI/Overlay Pass
        postRender(partialTicks, deltaTime);
    }

    private void renderWorld(float partialTicks) {
        ShaderManager.use("generic_3d");
        Shader shader = ShaderManager.getCurrent();
        if (shader == null) return;

        // Load View/Projection matrices to GPU
        shader.setUniform("u_Projection", camera.getProjectionMatrix());
        shader.setUniform("u_View", camera.getViewMatrix());

        // Render test
        renderSceneCubes(shader);

    }

    private void renderSceneCubes(Shader shader) {
        Tessellator tess = Tessellator.getInstance();
        BufferBuilder builder = tess.getBuffer();

        for (int x = -2; x <= 2; x++) {
            for (int z = -5; z >= -10; z--) {
                poseStack.push();
                poseStack.translate(x, 0, z);

                shader.setUniform("u_Model", poseStack.peek());

                builder.begin(VertexFormat.POSITION_COLOR);
                drawCube(builder, (float) x /2, 0, (float) z /2);
                tess.draw();

                poseStack.pop();
            }
        }
    }

    @Override
    public void preRender(float partialTicks, float deltaTime) {
        RenderSystem.clear(0.5f, 0.7f, 0.9f, 1.0f);
    }

    @Override
    public void postRender(float partialTicks, float deltaTime) {
        // Future: Render GUI here using an Orthographic projection
    }

    private void drawCube(BufferBuilder builder, float x, float y, float z) {
        // Front face (CCW when viewed from front)
        builder.pos(x-0.5f, y-0.5f, z+0.5f).color(1, 0, 0, 1).next();
        builder.pos(x+0.5f, y-0.5f, z+0.5f).color(1, 0, 0, 1).next();
        builder.pos(x+0.5f, y+0.5f, z+0.5f).color(1, 0, 0, 1).next();

        builder.pos(x+0.5f, y+0.5f, z+0.5f).color(1, 0, 0, 1).next();
        builder.pos(x-0.5f, y+0.5f, z+0.5f).color(1, 0, 0, 1).next();
        builder.pos(x-0.5f, y-0.5f, z+0.5f).color(1, 0, 0, 1).next();

        // Back face (CCW when viewed from front)
        builder.pos(x+0.5f, y-0.5f, z-0.5f).color(0, 1, 0, 1).next();
        builder.pos(x-0.5f, y-0.5f, z-0.5f).color(0, 1, 0, 1).next();
        builder.pos(x-0.5f, y+0.5f, z-0.5f).color(0, 1, 0, 1).next();

        builder.pos(x-0.5f, y+0.5f, z-0.5f).color(0, 1, 0, 1).next();
        builder.pos(x+0.5f, y+0.5f, z-0.5f).color(0, 1, 0, 1).next();
        builder.pos(x+0.5f, y-0.5f, z-0.5f).color(0, 1, 0, 1).next();

        // Top face (CCW when viewed from above)
        builder.pos(x-0.5f, y+0.5f, z+0.5f).color(0, 0, 1, 1).next();
        builder.pos(x+0.5f, y+0.5f, z+0.5f).color(0, 0, 1, 1).next();
        builder.pos(x+0.5f, y+0.5f, z-0.5f).color(0, 0, 1, 1).next();

        builder.pos(x+0.5f, y+0.5f, z-0.5f).color(0, 0, 1, 1).next();
        builder.pos(x-0.5f, y+0.5f, z-0.5f).color(0, 0, 1, 1).next();
        builder.pos(x-0.5f, y+0.5f, z+0.5f).color(0, 0, 1, 1).next();

        // Bottom face (CCW when viewed from below)
        builder.pos(x-0.5f, y-0.5f, z-0.5f).color(1, 1, 0, 1).next();
        builder.pos(x+0.5f, y-0.5f, z-0.5f).color(1, 1, 0, 1).next();
        builder.pos(x+0.5f, y-0.5f, z+0.5f).color(1, 1, 0, 1).next();

        builder.pos(x+0.5f, y-0.5f, z+0.5f).color(1, 1, 0, 1).next();
        builder.pos(x-0.5f, y-0.5f, z+0.5f).color(1, 1, 0, 1).next();
        builder.pos(x-0.5f, y-0.5f, z-0.5f).color(1, 1, 0, 1).next();

        // Right face (CCW when viewed from right)
        builder.pos(x+0.5f, y-0.5f, z+0.5f).color(1, 0, 1, 1).next();
        builder.pos(x+0.5f, y-0.5f, z-0.5f).color(1, 0, 1, 1).next();
        builder.pos(x+0.5f, y+0.5f, z-0.5f).color(1, 0, 1, 1).next();

        builder.pos(x+0.5f, y+0.5f, z-0.5f).color(1, 0, 1, 1).next();
        builder.pos(x+0.5f, y+0.5f, z+0.5f).color(1, 0, 1, 1).next();
        builder.pos(x+0.5f, y-0.5f, z+0.5f).color(1, 0, 1, 1).next();

        // Left face (CCW when viewed from left)
        builder.pos(x-0.5f, y-0.5f, z-0.5f).color(0, 1, 1, 1).next();
        builder.pos(x-0.5f, y-0.5f, z+0.5f).color(0, 1, 1, 1).next();
        builder.pos(x-0.5f, y+0.5f, z+0.5f).color(0, 1, 1, 1).next();

        builder.pos(x-0.5f, y+0.5f, z+0.5f).color(0, 1, 1, 1).next();
        builder.pos(x-0.5f, y+0.5f, z-0.5f).color(0, 1, 1, 1).next();
        builder.pos(x-0.5f, y-0.5f, z-0.5f).color(0, 1, 1, 1).next();
    }

    public Camera getCamera() { return camera; }
}