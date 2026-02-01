package engine.helios;

import org.jetbrains.annotations.Nullable;

import static org.lwjgl.opengl.GL11.*;

public class RenderSystem {
    private static int currentTexture = -1;
    private static boolean depthTest = false;
    private static boolean blending = false;
    private static boolean culling = false;

    public static void enableDepthTest() {
        if (!depthTest) {
            glEnable(GL_DEPTH_TEST);
            depthTest = true;
        }
    }

    public static void disableDepthTest() {
        if (depthTest) {
            glDisable(GL_DEPTH_TEST);
            depthTest = false;
        }
    }

    public static void bindTexture(Texture texture) {
        bindTexture(texture.getId());
    }

    public static void bindTexture(int textureId) {
        if (currentTexture != textureId) {
            glBindTexture(GL_TEXTURE_2D, textureId);
            currentTexture = textureId;
        }
    }

    public static void clear(float r, float g, float b, float a) {
        glClearColor(r, g, b, a);
        glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);
    }

    public static void enableBlend() {
        if (!blending) {
            glEnable(GL_BLEND);
            glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
            blending = true;
        }
    }

    public static void disableBlend() {
        if (blending) {
            glDisable(GL_BLEND);
            blending = false;
        }
    }

    public static void enableBackFaceCull() {
        if (!culling) {
            glEnable(GL_CULL_FACE);
            glCullFace(GL_BACK);
            culling = true;
        }
    }

    public static void disableBackFaceCull() {
        if (culling) {
            glDisable(GL_CULL_FACE);
            culling = false;
        }
    }
}