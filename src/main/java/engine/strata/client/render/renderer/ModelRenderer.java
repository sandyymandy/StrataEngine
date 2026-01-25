package engine.strata.client.render.renderer;

import engine.helios.MatrixStack;
import engine.helios.Shader;
import engine.strata.client.render.model.StrataModel;
import org.joml.Vector3f;
import static org.lwjgl.opengl.GL30.*;

public class ModelRenderer {

    public void render(StrataModel model, MatrixStack stack, Shader shader) {
        renderBone(model.getRoot(), stack, shader);
    }

    private void renderBone(StrataModel.Bone bone, MatrixStack stack, Shader shader) {
        stack.push();

        // 1. Pivot Translation
        Vector3f pivot = bone.getPivot();
        stack.translate(pivot.x, pivot.y, pivot.z);

        // (Optional: Apply animations here)

        stack.translate(-pivot.x, -pivot.y, -pivot.z);

        // 2. Upload Model Matrix for this bone
        shader.setUniform("u_Model", stack.peek());

        // 3. Draw Meshes
        for (StrataModel.Mesh mesh : bone.getMeshes()) {
            glBindVertexArray(mesh.vao());
            glDrawArrays(GL_TRIANGLES, 0, mesh.vertexCount());
        }

        // 4. Recursive Children
        for (StrataModel.Bone child : bone.getChildren()) {
            renderBone(child, stack, shader);
        }

        stack.pop();
    }
}