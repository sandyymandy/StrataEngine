package engine.strata.client.frontend.ui;

import engine.helios.rendering.RenderSystem;
import engine.helios.rendering.shader.ShaderManager;
import engine.helios.rendering.shader.ShaderStack;
import engine.strata.client.StrataClient;
import engine.strata.client.frontend.render.renderer.GuiRenderer;
import engine.strata.client.frontend.render.renderer.font.TextRenderer;
import engine.strata.util.Identifier;
import org.joml.Matrix4f;
import org.lwjgl.BufferUtils;

import java.nio.ByteBuffer;

import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL12.GL_CLAMP_TO_EDGE;

public class UiManager {
    private static final Matrix4f IDENTITY = new Matrix4f().identity();

    private final StrataClient client;
    private final FrameNode root = new FrameNode();

    private final UiBatch batch = new UiBatch();
    private final UiImmediateRenderer renderer = new UiImmediateRenderer();

    private UiNode hovered;
    private UiNode pressed;
    private boolean prevMouseDown = false;

    private boolean showFps = true;
    private String labelText = "";
    private String imageName = "kiss";
    private float labelTimer = 0;
    private float fpsSmoothed = 0;

    private int whiteTextureId = 0;
    private ImageNode icon;

    public UiManager(StrataClient client) {
        this.client = client;
        root.size().set(1, 1);
        root.sizePx().set(0, 0);
        root.anchor().set(0, 0);
        root.pivot().set(0, 0);
        FPSOverlay();
    }

    public FrameNode root() { return root; }

    private void FPSOverlay() {
        ButtonNode toggle = new ButtonNode();
        toggle.anchor().set(0, 0);
        toggle.pivot().set(0, 0);
        toggle.offsetPx().set(10, 10);
        toggle.sizePx().set(170, 30);
        toggle.onClick(() -> showFps = !showFps);

        TextNode btnText = new TextNode()
                .text(() -> showFps ? "Show Memory" : "Show FPS")
                .scale(1.0f);
        btnText.anchor().set(0.5f, 0.5f);
        btnText.pivot().set(0.5f, 0.5f);
        toggle.addChild(btnText);

        TextNode label = new TextNode()
                .text(() -> labelText)
                .scale(1.0f);
        label.anchor().set(0, 0);
        label.pivot().set(0, 0);
        label.offsetPx().set(12, 50);

        icon = new ImageNode()
                .texture(Identifier.ofEngine(imageName))
                .color(1, 1, 1, .5f);
        
        icon.anchor().set(0.0f, 0.0f);
        icon.pivot().set(0.0f, 0.0f);
        icon.offsetPx().set(10, 90);
        icon.sizePx().set(502, 502);

        root.addChild(icon);
        root.addChild(toggle);
        root.addChild(label);
    }

    public void updateAndRender(GuiRenderer gui, float deltaTime) {
        updateLabel(deltaTime);

        float mouseX = (float) client.getWindow().getMouseX();
        float mouseY = (float) client.getWindow().getMouseY();

        boolean mouseDown = glfwGetMouseButton(client.getWindow().getHandle(), GLFW_MOUSE_BUTTON_LEFT) == GLFW_PRESS;

        root.layout(new UiRect(0, 0, client.getWindow().getWidth(), client.getWindow().getHeight()));
    
        UiNode hit = root.hitTest(mouseX, mouseY);
        if (hit != hovered) {
            if (hovered != null) hovered.dispatchMouseExit();
            hovered = hit;
            if (hovered != null) hovered.dispatchMouseEnter();
        }

        if (!prevMouseDown && mouseDown) {
            pressed = hovered;
            if (pressed != null) pressed.dispatchMouseDown();
        } else if (prevMouseDown && !mouseDown) {
            if (pressed != null) {
                boolean clicked = pressed == hovered;
                pressed.dispatchMouseUp(clicked);
            }
            pressed = null;
        }
        prevMouseDown = mouseDown;

        root.update(mouseX, mouseY, mouseDown, deltaTime);

        render(gui.textRenderer);
    }

    private void updateLabel(float deltaTime) {
        if (deltaTime > 0) {
            float inst = 1.0f / deltaTime;
            fpsSmoothed = fpsSmoothed == 0 ? inst : (fpsSmoothed * 0.9f + inst * 0.1f);
        }

        labelTimer += deltaTime;
        if (labelTimer < 0.2f) return;
        labelTimer = 0;

        if (showFps) {
            labelText = String.format("FPS: %.0f", fpsSmoothed);
            imageName = "teto";
        } else {
            Runtime rt = Runtime.getRuntime();
            long used = rt.totalMemory() - rt.freeMemory();
            labelText = String.format("Memory: %.1f MB", used / (1024.0 * 1024.0));
            imageName = "kiss";
        }

        if (icon != null) {
            icon.texture(Identifier.ofEngine(imageName));
        }
    }

    private void render(TextRenderer textRenderer) {
        RenderSystem.assertOnRenderThread();
        ensureWhiteTexture();

        RenderSystem.disableDepthTest();
        RenderSystem.disableBackFaceCull();
        RenderSystem.enableBlend();
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);

        int w = client.getWindow().getWidth();
        int h = client.getWindow().getHeight();

        Matrix4f proj = new Matrix4f().ortho(0, w, h, 0, -1, 1);

        ShaderStack shader = ShaderManager.use(Identifier.ofEngine("generic_3d"));
        if (shader == null) return;
        shader.use();
        shader.setUniform("u_Projection", proj);
        shader.setUniform("u_View", IDENTITY);
        shader.setUniform("u_Model", IDENTITY);

        // Bind white texture (so fragment outputs v_Color)
        RenderSystem.bindTexture(whiteTextureId);
        shader.setUniform("u_Texture", 0);

        batch.clear();
        UiRenderContext ctx = new UiRenderContext(batch, textRenderer, whiteTextureId);
        root.render(ctx);

        // So, nodes may create/bind textures while building the batch (ImageNode).
        // Reset tracking so the draw loop always binds the intended textures.
        // Let me know if this breaks Sandy
        RenderSystem.unbindTextures();

        var commands = batch.commands();
        var buf = batch.toFloatBuffer();
        renderer.begin(buf, batch.vertexCount());
        for (UiBatch.Command cmd : commands) {
            RenderSystem.bindTexture(cmd.textureId());
            renderer.draw(cmd.firstVertex(), cmd.vertexCount());
        }
        renderer.end();

        RenderSystem.bindTexture(0);
        RenderSystem.disableBlend();
    }

    private void ensureWhiteTexture() {
        if (whiteTextureId != 0) return;

        // Create a 1x1 white texture. Must be called on render thread after GL is ready.
        whiteTextureId = glGenTextures();
        RenderSystem.bindTexture(whiteTextureId);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);

        ByteBuffer pixel = BufferUtils.createByteBuffer(4);
        pixel.put((byte) 0xFF).put((byte) 0xFF).put((byte) 0xFF).put((byte) 0xFF);
        pixel.flip();
        glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA8, 1, 1, 0, GL_RGBA, GL_UNSIGNED_BYTE, pixel);

        RenderSystem.bindTexture(0);
    }
}
