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
import engine.strata.client.frontend.render.renderer.entity.EntityRenderContext;
import engine.strata.util.Identifier;
import org.joml.Matrix4f;
import org.joml.Vector2f;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

/**
 * GPU-driven model renderer with EntityRenderContext support.
 *
 * <h3>How transforms work</h3>
 * The bone hierarchy is walked exactly as before, accumulating transforms in a
 * {@link MatrixStack}.  For each mesh the final matrix is uploaded as the
 * {@code u_Model} shader uniform, then the pre-uploaded VAO is drawn with a
 * single {@code glDrawArrays} call — no per-frame vertex data is touched on the CPU.
 *
 * <h3>EntityRenderContext integration</h3>
 * When rendering with a context:
 * <ul>
 *   <li>Global tint → applied to u_Tint uniform</li>
 *   <li>Global emissive → (future: needs shader support)</li>
 *   <li>Per-bone tint → overrides global tint for specific bones</li>
 *   <li>Per-bone visibility → skips rendering invisible bones</li>
 *   <li>Texture overrides → swaps texture paths before layer setup</li>
 * </ul>
 *
 * <h3>Shader contract</h3>
 * The active shader must expose:
 * <ul>
 *   <li>{@code uniform mat4 u_Projection} – set by {@link RenderLayer#setup}</li>
 *   <li>{@code uniform mat4 u_View}       – set by {@link RenderLayer#setup}</li>
 *   <li>{@code uniform mat4 u_Model}      – set per mesh draw call here</li>
 *   <li>{@code uniform vec4 u_Tint}       – set per mesh for colorization</li>
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
     * Renders model without customization (legacy compatibility).
     * Uses default white tint, all bones visible.
     */
    public void render(StrataModel model, StrataSkin skin, MatrixStack poseStack) {
        render(model, skin, poseStack, new EntityRenderContext());
    }

    /**
     * Renders model with EntityRenderContext customization.
     * This is the main entry point for context-aware rendering.
     *
     * @param model The model to render
     * @param skin The skin (textures) to apply
     * @param poseStack Transform stack with entity position/rotation
     * @param context Render customization (tints, visibility, etc.)
     */
    public void render(StrataModel model, StrataSkin skin, MatrixStack poseStack, EntityRenderContext context) {
        // Ensure all meshes are on the GPU — no-op after the first frame.
        GpuModelBaker.getInstance().ensureBaked(model);

        for (Map.Entry<String, StrataSkin.TextureData> entry : skin.textures().entrySet()) {
            String                 textureSlot = entry.getKey();
            StrataSkin.TextureData texData     = entry.getValue();

            // Apply texture override from context if present
            Identifier texturePath = context.hasTextureOverride(textureSlot) ?
                    context.getTextureOverride(textureSlot) : texData.path();

            RenderLayer layer = RenderLayers.getEntityLayer(texturePath, texData.translucent());

            layer.setup(StrataClient.getInstance().getCamera());
            ShaderStack shader = layer.shaderStack(); // always after setup()

            if (shader == null) {
                LOGGER.error("ModelRenderer: RenderLayer for '{}' has null shader — skipping",
                        texturePath);
                layer.clean();
                continue;
            }

            // Render bone hierarchy with context
            renderBone(model, textureSlot, model.getRoot(), poseStack, shader,
                    new Matrix4f().identity(), context);

            // Reset uniforms to defaults
            shader.setUniform("u_Model", IDENTITY);
            shader.setUniform("u_Tint", 1f, 1f, 1f, 1f);

            layer.clean();
        }
    }

    /** Convenience alias for callers that use the old signature. */
    public void renderModel(StrataModel model, StrataSkin skin, MatrixStack poseStack) {
        render(model, skin, poseStack);
    }

    // ── Bone traversal ────────────────────────────────────────────────────────

    /**
     * Recursively walks the bone tree with EntityRenderContext support.
     * Applies per-bone visibility and customization.
     */
    private void renderBone(StrataModel model,
                            String textureSlotFilter,
                            StrataBone bone,
                            MatrixStack poseStack,
                            ShaderStack shader,
                            Matrix4f parentModelMatrix,
                            EntityRenderContext context) {

        if (!bone.shouldRender()) return;

        // Check context visibility override
        if (!context.isBoneVisible(bone.getName())) {
            return;
        }

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

            drawMesh(model, meshData, meshId, poseStack, shader, bone, context);
        }

        for (StrataBone child : bone.getChildren()) {
            renderBone(model, textureSlotFilter, child, poseStack, shader, currentModelMatrix, context);
        }

        poseStack.pop();
        bone.resetStateChanges();
    }

    // ── Per-mesh draw ─────────────────────────────────────────────────────────

    /**
     * Draws a single mesh with EntityRenderContext customization.
     * Applies per-bone tint, emissive, and UV offsets.
     */
    private void drawMesh(StrataModel model, StrataMeshData meshData, String meshId,
                          MatrixStack poseStack, ShaderStack shader,
                          StrataBone bone, EntityRenderContext context) {

        String       cacheKey     = GpuModelBaker.getInstance().meshKey(model.getId(), meshId);
        MeshRenderer meshRenderer = GpuModelCache.getInstance().get(cacheKey);
        if (meshRenderer == null || meshRenderer.isEmpty()) return;

        // Copy bone-chain matrix and append the per-mesh pivot/rotation correction.
        Matrix4f meshMatrix = new Matrix4f(poseStack.peek());
        applyMeshLocalTransform(meshMatrix, meshData);

        // Upload model matrix
        shader.setUniform("u_Model", meshMatrix);

        // Apply tint from context
        Vector4f tint = computeFinalTint(bone, context);
        shader.setUniform("u_Tint", tint.x, tint.y, tint.z, tint.w);

        // TODO: Apply emissive (requires shader support)
        // float emissive = context.getBoneEmissive(bone.getName());
        // if (emissive > 0) {
        //     shader.setUniform("u_Emissive", emissive);
        // }

        // TODO: Apply UV offset (requires shader support)
        // Vector2f uvOffset = context.getBoneUVOffset(bone.getName());
        // shader.setUniform("u_UVOffset", uvOffset.x, uvOffset.y);

        // MeshRenderer.render() internally calls glVertexAttrib4f(2, 1,1,1,1) when
        // the format has no colour channel, preventing the black-texture bug.
        meshRenderer.render();
    }

    /**
     * Computes the final tint color for a bone, combining global and per-bone tints.
     */
    private Vector4f computeFinalTint(StrataBone bone, EntityRenderContext context) {
        Vector4f globalTint = context.getGlobalTint();
        Vector4f boneTint = context.getBoneTint(bone.getName());

        // Multiply global and bone tints
        return new Vector4f(
                globalTint.x * boneTint.x,
                globalTint.y * boneTint.y,
                globalTint.z * boneTint.z,
                globalTint.w * boneTint.w
        );
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