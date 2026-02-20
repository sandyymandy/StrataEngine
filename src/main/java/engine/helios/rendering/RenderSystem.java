package engine.helios.rendering;

import engine.helios.rendering.texture.Texture;
import engine.helios.rendering.texture.TextureArray;
import org.jetbrains.annotations.Nullable;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL30.*;

/**
 * Enhanced RenderSystem with texture array support and improved state management.
 */
public class RenderSystem {
    @Nullable
    private static Thread renderThread;

    // State tracking
    private static int currentTexture = -1;
    private static int currentTextureArray = -1;
    private static boolean depthTest = false;
    private static boolean blending = false;
    private static boolean culling = false;

    // ── Thread management ─────────────────────────────────────────────────────

    public static void initRenderThread() {
        if (renderThread != null) {
            throw new IllegalStateException("Could not initialize render thread");
        }
        renderThread = Thread.currentThread();
    }

    public static boolean isOnRenderThread() {
        return Thread.currentThread() == renderThread;
    }

    public static void assertOnRenderThread() {
        if (!isOnRenderThread()) {
            throw new IllegalStateException("RenderSystem called from wrong thread");
        }
    }

    // ── Texture binding ───────────────────────────────────────────────────────

    /**
     * Binds a regular 2D texture.
     */
    public static void bindTexture(Texture texture) {
        bindTexture(texture.getId());
    }

    /**
     * Binds a regular 2D texture by ID.
     */
    public static void bindTexture(int textureId) {
        if (currentTexture != textureId) {
            glBindTexture(GL_TEXTURE_2D, textureId);
            currentTexture = textureId;
            currentTextureArray = -1; // Invalidate array binding
        }
    }

    /**
     * Binds a texture array for rendering.
     */
    public static void bindTextureArray(TextureArray textureArray) {
        bindTextureArray(textureArray.getId());
    }

    /**
     * Binds a texture array by ID.
     */
    public static void bindTextureArray(int textureArrayId) {
        if (currentTextureArray != textureArrayId) {
            glBindTexture(GL_TEXTURE_2D_ARRAY, textureArrayId);
            currentTextureArray = textureArrayId;
            currentTexture = -1; // Invalidate 2D texture binding
        }
    }

    /**
     * Unbinds all textures.
     */
    public static void unbindTextures() {
        if (currentTexture != -1) {
            glBindTexture(GL_TEXTURE_2D, 0);
            currentTexture = -1;
        }
        if (currentTextureArray != -1) {
            glBindTexture(GL_TEXTURE_2D_ARRAY, 0);
            currentTextureArray = -1;
        }
    }

    // ── OpenGL state management ───────────────────────────────────────────────

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

    public static void setDepthTest(boolean enabled) {
        if (enabled) enableDepthTest();
        else disableDepthTest();
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

    public static void setBlend(boolean enabled) {
        if (enabled) enableBlend();
        else disableBlend();
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

    public static void setCulling(boolean enabled) {
        if (enabled) enableBackFaceCull();
        else disableBackFaceCull();
    }

    public static void clear(float r, float g, float b, float a) {
        glClearColor(r, g, b, a);
        glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);
    }

    // ── State queries ─────────────────────────────────────────────────────────

    public static boolean isDepthTestEnabled() { return depthTest; }
    public static boolean isBlendEnabled() { return blending; }
    public static boolean isCullingEnabled() { return culling; }
    public static int getCurrentTexture() { return currentTexture; }
    public static int getCurrentTextureArray() { return currentTextureArray; }
}