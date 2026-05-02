package engine.helios.rendering.texture;

import engine.helios.rendering.RenderSystem;
import java.nio.ByteBuffer;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL12.*;
import static org.lwjgl.opengl.GL30.*;

/**
 * Helios wrapper for OpenGL texture arrays (GL_TEXTURE_2D_ARRAY).
 * Provides proper state management and integration with RenderSystem.
 */
public class TextureArray {
    private final int id;
    private final int width;
    private final int height;
    private final int layers;

    private TextureArray(int id, int width, int height, int layers) {
        this.id = id;
        this.width = width;
        this.height = height;
        this.layers = layers;
    }

    /**
     * Creates a new texture array from raw RGBA data.
     *
     * @param data ByteBuffer containing RGBA data for all layers (back-to-back)
     * @param width Width of each texture layer
     * @param height Height of each texture layer
     * @param layerCount Number of layers in the array
     * @return A new TextureArray instance
     */
    public static TextureArray create(ByteBuffer data, int width, int height, int layerCount) {
        int id = glGenTextures();
        glBindTexture(GL_TEXTURE_2D_ARRAY, id);

        // Allocate storage for all layers
        glTexImage3D(
                GL_TEXTURE_2D_ARRAY,
                0,                  // mip level 0
                GL_RGBA8,           // internal format
                width,              // width
                height,             // height
                layerCount,         // depth = number of layers
                0,                  // border (must be 0)
                GL_RGBA,            // format of supplied data
                GL_UNSIGNED_BYTE,
                data
        );

        // Generate mipmaps
        glGenerateMipmap(GL_TEXTURE_2D_ARRAY);

        // Default filtering for pixel-art (can be changed later)
        glTexParameteri(GL_TEXTURE_2D_ARRAY, GL_TEXTURE_MIN_FILTER, GL_NEAREST_MIPMAP_LINEAR);
        glTexParameteri(GL_TEXTURE_2D_ARRAY, GL_TEXTURE_MAG_FILTER, GL_NEAREST);

        // Clamp all axes
        glTexParameteri(GL_TEXTURE_2D_ARRAY, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
        glTexParameteri(GL_TEXTURE_2D_ARRAY, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
        glTexParameteri(GL_TEXTURE_2D_ARRAY, GL_TEXTURE_WRAP_R, GL_CLAMP_TO_EDGE);

        glBindTexture(GL_TEXTURE_2D_ARRAY, 0);

        return new TextureArray(id, width, height, layerCount);
    }

    /**
     * Creates an empty texture array with no initial data.
     * Data can be uploaded later using updateLayer().
     *
     * @param width Width of each texture layer
     * @param height Height of each texture layer
     * @param layerCount Number of layers in the array
     * @return A new TextureArray instance
     */
    public static TextureArray createEmpty(int width, int height, int layerCount) {
        int id = glGenTextures();
        glBindTexture(GL_TEXTURE_2D_ARRAY, id);

        // Allocate storage with null data
        glTexImage3D(
                GL_TEXTURE_2D_ARRAY,
                0,
                GL_RGBA8,
                width,
                height,
                layerCount,
                0,
                GL_RGBA,
                GL_UNSIGNED_BYTE,
                (ByteBuffer) null
        );

        glTexParameteri(GL_TEXTURE_2D_ARRAY, GL_TEXTURE_MIN_FILTER, GL_NEAREST_MIPMAP_LINEAR);
        glTexParameteri(GL_TEXTURE_2D_ARRAY, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
        glTexParameteri(GL_TEXTURE_2D_ARRAY, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
        glTexParameteri(GL_TEXTURE_2D_ARRAY, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
        glTexParameteri(GL_TEXTURE_2D_ARRAY, GL_TEXTURE_WRAP_R, GL_CLAMP_TO_EDGE);

        glBindTexture(GL_TEXTURE_2D_ARRAY, 0);

        return new TextureArray(id, width, height, layerCount);
    }

    /**
     * Updates a specific layer in the texture array.
     *
     * @param layer Layer index to update
     * @param data RGBA data for the layer
     */
    public void updateLayer(int layer, ByteBuffer data) {
        if (layer < 0 || layer >= layers) {
            throw new IllegalArgumentException("Layer index out of bounds: " + layer);
        }

        bind();
        glTexSubImage3D(
                GL_TEXTURE_2D_ARRAY,
                0,              // mip level
                0, 0, layer,    // x, y, z offset
                width, height, 1, // width, height, depth
                GL_RGBA,
                GL_UNSIGNED_BYTE,
                data
        );
        unbind();
    }

    /**
     * Creates a builder for more control over texture array creation.
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder for creating texture arrays with custom settings.
     */
    public static class Builder {
        private int width;
        private int height;
        private int layerCount;
        private ByteBuffer data;
        private int minFilter = GL_NEAREST_MIPMAP_LINEAR;
        private int magFilter = GL_NEAREST;
        private int wrapS = GL_CLAMP_TO_EDGE;
        private int wrapT = GL_CLAMP_TO_EDGE;
        private boolean generateMipmaps = true;

        public Builder size(int width, int height) {
            this.width = width;
            this.height = height;
            return this;
        }

        public Builder layers(int count) {
            this.layerCount = count;
            return this;
        }

        public Builder data(ByteBuffer data) {
            this.data = data;
            return this;
        }

        public Builder minFilter(int filter) {
            this.minFilter = filter;
            return this;
        }

        public Builder magFilter(int filter) {
            this.magFilter = filter;
            return this;
        }

        public Builder wrapMode(int wrapS, int wrapT) {
            this.wrapS = wrapS;
            this.wrapT = wrapT;
            return this;
        }

        public Builder mipmaps(boolean generate) {
            this.generateMipmaps = generate;
            return this;
        }

        public TextureArray build() {
            if (width <= 0 || height <= 0 || layerCount <= 0) {
                throw new IllegalStateException("Width, height, and layer count must be positive");
            }

            int id = glGenTextures();
            glBindTexture(GL_TEXTURE_2D_ARRAY, id);

            // Allocate storage
            glTexImage3D(
                    GL_TEXTURE_2D_ARRAY,
                    0,
                    GL_RGBA8,
                    width,
                    height,
                    layerCount,
                    0,
                    GL_RGBA,
                    GL_UNSIGNED_BYTE,
                    data
            );

            // Generate mipmaps if requested
            if (generateMipmaps) {
                glGenerateMipmap(GL_TEXTURE_2D_ARRAY);
            }

            // Set filtering
            glTexParameteri(GL_TEXTURE_2D_ARRAY, GL_TEXTURE_MIN_FILTER, minFilter);
            glTexParameteri(GL_TEXTURE_2D_ARRAY, GL_TEXTURE_MAG_FILTER, magFilter);

            // Set wrap modes
            glTexParameteri(GL_TEXTURE_2D_ARRAY, GL_TEXTURE_WRAP_S, wrapS);
            glTexParameteri(GL_TEXTURE_2D_ARRAY, GL_TEXTURE_WRAP_T, wrapT);
            glTexParameteri(GL_TEXTURE_2D_ARRAY, GL_TEXTURE_WRAP_R, GL_CLAMP_TO_EDGE);

            glBindTexture(GL_TEXTURE_2D_ARRAY, 0);

            return new TextureArray(id, width, height, layerCount);
        }
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