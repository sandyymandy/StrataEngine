package engine.helios.rendering.texture;

import engine.strata.core.io.ResourceManager;
import engine.strata.util.Identifier;
import org.lwjgl.BufferUtils;
import org.lwjgl.system.MemoryStack;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.io.InputStream;
import java.util.Objects;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;

import static engine.helios.rendering.RenderSystem.bindTexture;
import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.stb.STBImage.*;

public class Texture {
    private static final Logger LOGGER = LoggerFactory.getLogger("Texture");

    private final int id;

    public Texture(int id) {
        this.id = id;
    }

    Texture(Identifier identifier) {
        this.id = glGenTextures();
        bindTexture(id);

        // Pixel-art settings (Nearest Neighbor)
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_REPEAT);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_REPEAT);

        try (MemoryStack stack = MemoryStack.stackPush()) {
            IntBuffer w = stack.mallocInt(1);
            IntBuffer h = stack.mallocInt(1);
            IntBuffer comp = stack.mallocInt(1);

            ByteBuffer imageBuffer = ioResourceToByteBuffer(identifier);
            ByteBuffer pixels = stbi_load_from_memory(imageBuffer, w, h, comp, 4);

            if (pixels == null) {
                throw new RuntimeException("Failed to decode texture: " + stbi_failure_reason());
            }

            glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA8, w.get(0), h.get(0), 0, GL_RGBA, GL_UNSIGNED_BYTE, pixels);
            stbi_image_free(pixels);
        } catch (Exception e) {
            // Don't silently create an uninitialized texture (renders as a white box on some drivers).
            // Upload a visible fallback pixel so missing/bad assets are obvious at runtime.
            LOGGER.error("Failed to load texture {} (expected at {})",
                    identifier, identifier.toAssetPath("textures", "png"), e);
            uploadFallbackPixel();
        } finally {
            bindTexture(0);
        }
    }

    private ByteBuffer ioResourceToByteBuffer(Identifier id) throws Exception {
        try (InputStream is = ResourceManager.getResourceStream(id, "textures", "png")) {
            if (is == null) {
                throw new RuntimeException("Texture not found: " + id + " (expected " + id.toAssetPath("textures", "png") + ")");
            }
            byte[] bytes = is.readAllBytes();
            ByteBuffer buffer = BufferUtils.createByteBuffer(bytes.length);
            buffer.put(bytes);
            buffer.flip();
            return buffer;
        }
    }

    private void uploadFallbackPixel() {
        // 1x1 magenta (debug missing texture)
        ByteBuffer pixel = BufferUtils.createByteBuffer(4);
        pixel.put((byte) 0xFF).put((byte) 0x00).put((byte) 0xFF).put((byte) 0xFF);
        pixel.flip();
        glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA8, 1, 1, 0, GL_RGBA, GL_UNSIGNED_BYTE, pixel);
    }

    public int getId() {
        return id;
    }
}