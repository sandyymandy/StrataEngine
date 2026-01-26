package engine.strata.client.render.util;

import engine.helios.MatrixStack;
import org.joml.Matrix4f;

public class RenderUtils {
    public static Matrix4f getMatrixSnapshot(MatrixStack matrixStack) {
        return new Matrix4f(matrixStack.peek());
    }
}
