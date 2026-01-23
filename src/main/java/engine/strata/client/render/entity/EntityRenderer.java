//package engine.strata.client.render.entity;
//
//import engine.strata.client.render.model.RawModel;
//import engine.strata.client.render.shader.StaticShader;
//import org.joml.Matrix4f;
//import org.lwjgl.opengl.GL11;
//import org.lwjgl.opengl.GL20;
//import org.lwjgl.opengl.GL30;
//
//import java.util.List;
//import java.util.Map;
//
//public class EntityRenderer {
//    private StaticShader shader;
//
//    public EntityRenderer(StaticShader shader, Matrix4f projectionMatrix) {
//        this.shader = shader;
//        // Load projection matrix once on startup (unless window resizes)
//        shader.start();
//        shader.loadProjectionMatrix(projectionMatrix);
//        shader.stop();
//    }
//
//    // Takes a map of Model -> List of Entities (Batch Processing)
//    public void render(Map<RawModel, List<RenderEntity>> entities) {
//        for (RawModel model : entities.keySet()) {
//            prepareRawModel(model);
//            List<RenderEntity> batch = entities.get(model);
//            for (RenderEntity entity : batch) {
//                prepareInstance(entity);
//                GL11.glDrawElements(GL11.GL_TRIANGLES, model.getVertexCount(), GL11.GL_UNSIGNED_INT, 0);
//            }
//            unbindRawModel();
//        }
//    }
//
//    private void prepareRawModel(RawModel model) {
//        GL30.glBindVertexArray(model.getVaoID());
//        GL20.glEnableVertexAttribArray(0); // Position
//        GL20.glEnableVertexAttribArray(1); // Color
//    }
//
//    private void unbindRawModel() {
//        GL20.glDisableVertexAttribArray(0);
//        GL20.glDisableVertexAttribArray(1);
//        GL30.glBindVertexArray(0);
//    }
//
//    private void prepareInstance(RenderEntity entity) {
//        Matrix4f transformationMatrix = Maths.createTransformationMatrix(
//                entity.getPosition(),
//                entity.getRotX(), entity.getRotY(), entity.getRotZ(),
//                entity.getScale()
//        );
//        shader.loadTransformationMatrix(transformationMatrix);
//    }
//}