package engine.strata.client.renderer;

import engine.strata.client.renderer.entity.RenderEntity;
import engine.strata.client.renderer.entity.EntityRenderer;
import engine.strata.client.renderer.model.RawModel;
import engine.strata.client.renderer.shader.StaticShader;
import engine.strata.util.math.Maths;
import engine.strata.client.window.Window;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MasterRenderer {
    private static final float FOV = 70;
    private static final float NEAR_PLANE = 0.1f;
    private static final float FAR_PLANE = 1000f;

    private final StaticShader shader = new StaticShader();
    private final EntityRenderer entityRenderer;

    // Determine which entities to draw this frame
    private final Map<RawModel, List<RenderEntity>> entities = new HashMap<>();

    public MasterRenderer(Window window) {
        // Create Projection Matrix based on Window size
        float aspectRatio = (float) window.getConfig().width / window.getConfig().height;
        Matrix4f projectionMatrix = new Matrix4f().perspective(
                (float) Math.toRadians(FOV), aspectRatio, NEAR_PLANE, FAR_PLANE);

        this.entityRenderer = new EntityRenderer(shader, projectionMatrix);

        // Setup GL capabilities
        GL11.glEnable(GL11.GL_CULL_FACE);
        GL11.glCullFace(GL11.GL_BACK); // Don't draw inside of cubes
    }

    public void processEntity(RenderEntity entity) {
        RawModel entityModel = entity.getModel();
        List<RenderEntity> batch = entities.get(entityModel);
        if (batch != null) {
            batch.add(entity);
        } else {
            List<RenderEntity> newBatch = new ArrayList<>();
            newBatch.add(entity);
            entities.put(entityModel, newBatch);
        }
    }

    public void render(Camera camera) {
        prepare();

        // 1. Render 3D World
        shader.start();
        shader.loadViewMatrix(Maths.createViewMatrix(camera));
        entityRenderer.render(entities);
        shader.stop();

        // 2. Render UI (Future: guiRenderer.render(guis))

        // Clear lists after rendering
        entities.clear();
    }

    public void prepare() {
        GL11.glEnable(GL11.GL_DEPTH_TEST);
        GL11.glClearColor(0.5f, 0.7f, 0.9f, 1.0f); // Sky Blue
        GL11.glClear(GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT);
    }

    public void cleanUp() {
        shader.cleanUp();
    }
}