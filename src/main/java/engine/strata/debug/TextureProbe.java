package engine.strata.debug;

import engine.strata.core.io.ResourceManager;
import engine.strata.util.Identifier;
import org.lwjgl.BufferUtils;
import org.lwjgl.stb.STBImage;
import org.lwjgl.system.MemoryStack;

import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;

public final class TextureProbe {
    public static void main(String[] args) throws Exception {
        Identifier id = args.length > 0 ? Identifier.of(args[0]) : Identifier.ofEngine("missing");
        System.out.println("Probing texture: " + id + " -> " + id.toAssetPath("textures", "png"));

        ByteBuffer image;
        try (InputStream is = ResourceManager.getResourceStream(id, "textures", "png")) {
            if (is == null) {
                System.out.println("NOT FOUND");
                return;
            }
            byte[] bytes = is.readAllBytes();
            image = BufferUtils.createByteBuffer(bytes.length);
            image.put(bytes).flip();
        }

        try (MemoryStack stack = MemoryStack.stackPush()) {
            IntBuffer w = stack.mallocInt(1);
            IntBuffer h = stack.mallocInt(1);
            IntBuffer comp = stack.mallocInt(1);

            ByteBuffer pixels = STBImage.stbi_load_from_memory(image, w, h, comp, 4);
            if (pixels == null) {
                System.out.println("DECODE FAILED: " + STBImage.stbi_failure_reason());
                return;
            }

            int width = w.get(0);
            int height = h.get(0);
            int components = comp.get(0);
            System.out.println("Decoded: " + width + "x" + height + " comp=" + components);

            // Print first few pixels in RGBA.
            int toPrint = Math.min(8, width * height);
            for (int i = 0; i < toPrint; i++) {
                int o = i * 4;
                int r = pixels.get(o) & 0xFF;
                int g = pixels.get(o + 1) & 0xFF;
                int b = pixels.get(o + 2) & 0xFF;
                int a = pixels.get(o + 3) & 0xFF;
                System.out.println("px[" + i + "]=" + r + "," + g + "," + b + "," + a);
            }

            STBImage.stbi_image_free(pixels);
        }
    }
}

