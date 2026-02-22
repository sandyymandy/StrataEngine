package engine.strata.client.frontend.render.renderer;

import engine.helios.rendering.GpuModelCache;
import engine.helios.rendering.MeshRenderer;
import engine.helios.rendering.RenderLayer;
import engine.helios.rendering.shader.ShaderStack;
import engine.helios.rendering.vertex.MatrixStack;
import engine.strata.client.StrataClient;
import engine.strata.client.frontend.render.RenderLayers;
import engine.strata.client.frontend.render.model.GpuModelBaker;
import engine.strata.client.frontend.render.model.StrataBone;
import engine.strata.client.frontend.render.model.StrataMeshData;
import engine.strata.client.frontend.render.model.StrataModel;
import engine.strata.client.frontend.render.model.StrataSkin;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

/**
 * GPU-driven model renderer.
 *
 * <h3>How transforms work</h3>
 * The bone hierarchy is walked exactly as before, accumulating transforms in a
 * {@link MatrixStack}.  For each mesh the final matrix is uploaded as the
 * {@code u_Model} shader uniform, then the pre-uploaded VAO is drawn with a
 * single {@code glDrawArrays} call — no per-frame vertex data is touched on the CPU.
 *
 * <h3>How textures / colour work</h3>
 * Static VBOs use {@code POSITION_TEXTURE} format — no per-vertex colour channel.
 * {@link MeshRenderer#render()} calls {@code glVertexAttrib4f(2, 1,1,1,1)} before
 * each draw so that the disabled colour attribute reads white rather than the
 * OpenGL default of black, letting the fragment shader output the texture unchanged.
 *
 * <h3>Shader contract</h3>
 * The active shader (taken from {@link RenderLayer#shaderStack()} <em>after</em>
 * {@link RenderLayer#setup} has been called) must expose:
 * <ul>
 *   <li>{@code uniform mat4 u_Projection} – set by {@link RenderLayer#setup}</li>
 *   <li>{@code uniform mat4 u_View}       – set by {@link RenderLayer#setup}</li>
 *   <li>{@code uniform mat4 u_Model}      – set per mesh draw call here</li>
 * </ul>
 *
 * <h3>Rotation unit contract</h3>
 * <ul>
 *   <li>{@code StrataBone.rotation} — <b>degrees</b> — goes through
 *       {@link MatrixStack#rotateZYX} which calls {@code Math.toRadians} internally.</li>
 *   <li>{@code StrataMeshData.rotation()} — <b>radians</b> — passed directly to
 *       JOML {@link Matrix4f#rotateZYX} which expects radians.
 *       Do NOT wrap these in {@code Math.toRadians}.</li>
 * </ul>
 */
public class ModelRenderer {

    private static final Logger LOGGER = LoggerFactory.getLogger("ModelRenderer");

    private static final Matrix4f IDENTITY = new Matrix4f().identity();

    // ── Public entry points ───────────────────────────────────────────────────

    /**
     * Renders every texture slot of {@code model} using GPU-cached mesh data.
     * The {@code poseStack} should already have the entity world-transform pushed.
     */
    public void render(StrataModel model, StrataSkin skin, MatrixStack poseStack) {
        // Ensure all meshes are on the GPU — no-op after the first frame.
        GpuModelBaker.getInstance().ensureBaked(model);

        for (Map.Entry<String, StrataSkin.TextureData> entry : skin.textures().entrySet()) {
            String                 textureSlot = entry.getKey();
            StrataSkin.TextureData texData     = entry.getValue();

            RenderLayer layer = RenderLayers.getEntityLayer(texData.path(), texData.translucent());


            layer.setup(StrataClient.getInstance().getCamera());
            ShaderStack shader = layer.shaderStack(); // always after setup()

            if (shader == null) {
                LOGGER.error("ModelRenderer: RenderLayer for '{}' has null shader — skipping",
                        texData.path());
                layer.clean();
                continue;
            }

            renderBone(model, textureSlot, model.getRoot(), poseStack, shader,
                    new Matrix4f().identity());

            shader.setUniform("u_Model", IDENTITY);

            layer.clean();
        }
    }

    /** Convenience alias for callers that use the old signature. */
    public void renderModel(StrataModel model, StrataSkin skin, MatrixStack poseStack) {
        render(model, skin, poseStack);
    }

    // ── Bone traversal ────────────────────────────────────────────────────────

    /**
     * Recursively walks the bone tree, accumulating transforms in {@code poseStack}.
     * Structure is identical to the old immediate-mode implementation — only the
     * leaf action changed (uniform upload + glDrawArrays instead of CPU vertex data).
     */
    private void renderBone(StrataModel model,
                            String textureSlotFilter,
                            StrataBone bone,
                            MatrixStack poseStack,
                            ShaderStack shader,
                            Matrix4f parentModelMatrix) {

        if (!bone.shouldRender()) return;

        poseStack.push();

        Vector3f pivot     = bone.getPivot();
        Vector3f staticRot = bone.getRotation(); // DEGREES — MatrixStack.rotateZYX converts internally

        // Identical transform sequence to the original renderBone:
        poseStack.translate(pivot.x, pivot.y, pivot.z);
        poseStack.rotateZYX(staticRot.z, staticRot.y, staticRot.x); // ZYX, degrees
        poseStack.translate(-pivot.x, -pivot.y, -pivot.z);

        if (bone.isTrackingMatrices()) {
            updateBoneMatrices(bone, poseStack.peek(), parentModelMatrix);
        }

        Matrix4f currentModelMatrix = new Matrix4f(parentModelMatrix).mul(poseStack.peek());

        for (String meshId : bone.getMeshIds()) {
            StrataMeshData meshData = model.getMesh(meshId);
            if (meshData == null) continue;
            if (textureSlotFilter != null && !textureSlotFilter.equals(meshData.textureSlot())) continue;

            drawMesh(model, meshData, meshId, poseStack, shader);
        }

        for (StrataBone child : bone.getChildren()) {
            renderBone(model, textureSlotFilter, child, poseStack, shader, currentModelMatrix);
        }

        poseStack.pop();
        bone.resetStateChanges();
    }

    // ── Per-mesh draw ─────────────────────────────────────────────────────────

    /**
     * Computes the full model matrix (bone chain × mesh pivot/rotation),
     * uploads it as {@code u_Model}, and issues one {@code glDrawArrays}.
     *
     * <p>Colour is handled transparently by {@link MeshRenderer#render()} via
     * {@code glVertexAttrib4f} — no per-draw tint upload is needed here.
     */
    private void drawMesh(StrataModel model, StrataMeshData meshData, String meshId,
                          MatrixStack poseStack, ShaderStack shader) {

        String       cacheKey     = GpuModelBaker.getInstance().meshKey(model.getId(), meshId);
        MeshRenderer meshRenderer = GpuModelCache.getInstance().get(cacheKey);
        if (meshRenderer == null || meshRenderer.isEmpty()) return;

        // Copy bone-chain matrix and append the per-mesh pivot/rotation correction.
        Matrix4f meshMatrix = new Matrix4f(poseStack.peek());
        applyMeshLocalTransform(meshMatrix, meshData);

        // u_Model goes to whichever GL program is bound (the RenderLayer's shader,
        // activated just before this via layer.setup()).
        shader.setUniform("u_Model", meshMatrix);

        // MeshRenderer.render() internally calls glVertexAttrib4f(2, 1,1,1,1) when
        // the format has no colour channel, preventing the black-texture bug.
        meshRenderer.render();
    }

    /**
     * Appends the mesh-local origin + rotation pivot to {@code matrix} in-place.
     *
     * <h3>CRITICAL — {@code StrataMeshData.rotation()} is in RADIANS</h3>
     * These values come pre-converted from the model loader and are passed directly
     * to JOML's {@link Matrix4f#rotateZYX} which expects radians.
     * Do NOT call {@code Math.toRadians()} on them — that was the rotation bug.
     */
    private void applyMeshLocalTransform(Matrix4f matrix, StrataMeshData meshData) {
        Vector3f origin = meshData.origin();
        Vector3f rot    = meshData.rotation(); // RADIANS — no toRadians() needed

        if ("blockbench_cuboid".equals(meshData.type())) {
            // Rotate around the mesh origin — mirrors original renderCuboid.
            matrix.translate(origin.x, origin.y, origin.z);
            matrix.rotateZYX(rot.z, rot.y, rot.x); // JOML, radians, ZYX
            matrix.translate(-origin.x, -origin.y, -origin.z);
        } else {
            // Tri-mesh: translate then rotate, no reverse translate — mirrors original renderMesh.
            matrix.translate(origin.x, origin.y, origin.z);
            matrix.rotateZYX(rot.z, rot.y, rot.x); // JOML, radians, ZYX
        }
    }

    // ── Matrix tracking (unchanged from original) ─────────────────────────────

    private void updateBoneMatrices(StrataBone bone, Matrix4f localMatrix, Matrix4f parentModelMatrix) {
        bone.setLocalSpaceMatrix(new Matrix4f(localMatrix));
        Matrix4f modelMatrix = new Matrix4f(parentModelMatrix).mul(localMatrix);
        bone.setModelSpaceMatrix(modelMatrix);
        bone.setWorldSpaceMatrix(new Matrix4f(modelMatrix));
    }

    // ── Data classes ──────────────────────────────────────────────────────────

    public record BoneTransform(Vector3f rotation, Vector3f translation, Vector3f scale) {
        public BoneTransform {
            rotation    = new Vector3f(rotation);
            translation = new Vector3f(translation);
            scale       = new Vector3f(scale);
        }
    }
}