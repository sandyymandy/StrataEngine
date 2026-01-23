package engine.helios;

import static org.lwjgl.opengl.GL11.*;

public class RenderSystem {
    private static int currentTexture = -1;
    private static boolean depthTest = false;

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
}