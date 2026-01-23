package engine.helios;

import engine.strata.core.io.ResourceManager;
import engine.strata.util.Identifier;
import org.lwjgl.BufferUtils;
import org.lwjgl.system.MemoryStack;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.stb.STBImage.*;

public class Texture {
    private final int id;

    // Package-private: only TextureManager should create these
    Texture(Identifier identifier) {
        this.id = glGenTextures();
        glBindTexture(GL_TEXTURE_2D, id);

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
                throw new RuntimeException("Failed to load texture: " + stbi_failure_reason());
            }

            glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA, w.get(0), h.get(0), 0, GL_RGBA, GL_UNSIGNED_BYTE, pixels);
            stbi_image_free(pixels);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private ByteBuffer ioResourceToByteBuffer(Identifier id) throws Exception {
        try (InputStream is = ResourceManager.getResourceStream(id, "textures", "png")) {
            if (is == null) throw new RuntimeException("Texture not found: " + id);

            ReadableByteChannel rbc = Channels.newChannel(is);
            ByteBuffer buffer = BufferUtils.createByteBuffer(1024 * 1024); // 1MB temp buffer
            while (rbc.read(buffer) != -1);
            buffer.flip();
            return buffer;
        }
    }

    public void bind() {
        glBindTexture(GL_TEXTURE_2D, id);
    }
}