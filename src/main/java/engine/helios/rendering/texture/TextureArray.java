package engine.helios.rendering.texture;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL12.*;
import static org.lwjgl.opengl.GL30.*;

public class TextureArray {
    private final int id;
    private final int width;
    private final int height;
    private final int layers;

    public TextureArray(int id, int width, int height, int layers) {
        this.id = id;
        this.width = width;
        this.height = height;
        this.layers = layers;
    }

    /**
     * Binds this texture array for rendering.
     * Should be called through RenderSystem for proper state tracking.
     */
    public void bind() {
        glBindTexture(GL_TEXTURE_2D_ARRAY, id);
    }

    /**
     * Unbinds any texture array.
     */
    public static void unbind() {
        glBindTexture(GL_TEXTURE_2D_ARRAY, 0);
    }

    /**
     * Sets filtering modes for the texture array.
     */
    public void setFilterMode(int minFilter, int magFilter) {
        bind();
        glTexParameteri(GL_TEXTURE_2D_ARRAY, GL_TEXTURE_MIN_FILTER, minFilter);
        glTexParameteri(GL_TEXTURE_2D_ARRAY, GL_TEXTURE_MAG_FILTER, magFilter);
        unbind();
    }

    /**
     * Sets wrap modes for the texture array.
     * Always clamps R axis to edge since interpolating between layers is invalid.
     */
    public void setWrapMode(int wrapS, int wrapT) {
        bind();
        glTexParameteri(GL_TEXTURE_2D_ARRAY, GL_TEXTURE_WRAP_S, wrapS);
        glTexParameteri(GL_TEXTURE_2D_ARRAY, GL_TEXTURE_WRAP_T, wrapT);
        glTexParameteri(GL_TEXTURE_2D_ARRAY, GL_TEXTURE_WRAP_R, GL_CLAMP_TO_EDGE);
        unbind();
    }

    /**
     * Generates mipmaps for this texture array.
     */
    public void generateMipmaps() {
        bind();
        glGenerateMipmap(GL_TEXTURE_2D_ARRAY);
        unbind();
    }

    /**
     * Deletes the OpenGL texture array.
     */
    public void delete() {
        glDeleteTextures(id);
    }

    // Getters
    public int getId() { return id; }
    public int getWidth() { return width; }
    public int getHeight() { return height; }
    public int getLayers() { return layers; }

    @Override
    public String toString() {
        return String.format("TextureArray{id=%d, size=%dx%d, layers=%d}",
                id, width, height, layers);
    }
}