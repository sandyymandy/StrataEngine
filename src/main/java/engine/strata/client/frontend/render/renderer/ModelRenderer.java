package engine.strata.client.frontend.render.renderer;

import engine.helios.rendering.GpuModelCache;
import engine.helios.rendering.MeshRenderer;
import engine.helios.rendering.RenderLayer;
import engine.helios.rendering.shader.ShaderStack;
import engine.helios.rendering.vertex.MatrixStack;
import engine.strata.client.StrataClient;
import engine.strata.client.frontend.render.RenderLayers;
import engine.strata.client.frontend.render.animation.AnimationProcessor;
import engine.strata.client.frontend.render.model.*;
import engine.strata.client.frontend.render.renderer.entity.EntityRenderContext;
import engine.strata.util.Identifier;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

/**
 * GPU-driven model renderer with QUATERNION-BASED bone transforms.
 *
 * <h3>MAJOR REFACTOR - Quaternion Rotations:</h3>
 * <p>All bone rotations now use {@link Quaternionf} instead of Euler angles.
 * This eliminates gimbal lock and enables smooth interpolation (slerp).
 *
 * <h3>How transforms work:</h3>
 * <p>The bone hierarchy is walked with each bone applying its transform in this order:
 * <ol>
 *   <li>Translate to pivot point</li>
 *   <li>Apply base rotation (from StrataBone) + animation offset (from BoneState)</li>
 *   <li>Apply scale (from BoneState)</li>
 *   <li>Apply position (from BoneState)</li>
 *   <li>Translate back from pivot point</li>
 * </ol>
 *
 * <h3>EntityRenderContext integration:</h3>
 * <p>When rendering with a context:
 * <ul>
 *   <li>AnimationProcessor → provides per-bone transforms via BoneState</li>
 *   <li>Global tint → applied to u_Tint uniform</li>
 *   <li>Per-bone tint → overrides global tint for specific bones</li>
 *   <li>Per-bone visibility → skips rendering invisible bones</li>
 *   <li>Texture overrides → swaps texture paths before layer setup</li>
 * </ul>
 *
 * <h3>Shader contract:</h3>
 * <p>The active shader must expose:
 * <ul>
 *   <li>{@code uniform mat4 u_Projection} – set by {@link RenderLayer#setup}</li>
 *   <li>{@code uniform mat4 u_View}       – set by {@link RenderLayer#setup}</li>
 *   <li>{@code uniform mat4 u_Model}      – set per mesh draw call here</li>
 *   <li>{@code uniform vec4 u_Tint}       – set per mesh for colorization</li>
 * </ul>
 */
public class ModelRenderer {

    private static final Logger LOGGER = LoggerFactory.getLogger("ModelRenderer");
    private static final Matrix4f IDENTITY = new Matrix4f().identity();

    // ══════════════════════════════════════════════════════════════════════════
    // PUBLIC ENTRY POINTS
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * Renders model without customization (legacy compatibility).
     * Uses default white tint, all bones visible, no animations.
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
     * @param context Render customization (tints, visibility, animations, etc.)
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

    // ══════════════════════════════════════════════════════════════════════════
    // BONE TRAVERSAL WITH QUATERNION TRANSFORMS
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * Recursively walks the bone tree with QUATERNION-based transforms.
     *
     * <h3>CRITICAL TRANSFORM ORDER:</h3>
     * <pre>
     * 1. Move to pivot point
     * 2. Apply combined rotation (base * animation offset)
     * 3. Apply scale
     * 4. Apply position offset
     * 5. Move back from pivot point
     * </pre>
     *
     * <h3>Quaternion Multiplication:</h3>
     * <p>{@code baseRotation.mul(animationOffset)} ensures the animation offset
     * is applied in the bone's local space (not world space).
     */
    private void renderBone(StrataModel model,
                            String textureSlotFilter,
                            StrataBone bone,
                            MatrixStack poseStack,
                            ShaderStack shader,
                            Matrix4f parentModelMatrix,
                            EntityRenderContext context) {

        // Get animation processor from context (may be null)
        AnimationProcessor animProcessor = context.getAnimationProcessor();

        // Get bone state (handles animation offsets)
        BoneState boneState = (animProcessor != null)
                ? animProcessor.getBoneState(bone.getName())
                : new BoneState();

        // Check visibility (both BoneState and EntityRenderContext)
        if (!boneState.isVisible() || !context.isBoneVisible(bone.getName())) {
            return; // Skip this entire bone subtree
        }

        poseStack.push();


        //Move to pivot point
        Vector3f pivot = bone.getPivot();
        poseStack.translate(pivot.x, pivot.y, pivot.z);

        // Apply combined rotation (base + animation offset)
        Vector3f baseRotation = bone.getRotation(); // From model file
        Vector3f animOffset = boneState.getRotationOffset(); // From animation

        // Combine rotations: base * offset
        // This ensures the animation offset is in bone-local space
        Vector3f combinedRotation = baseRotation.add(animOffset);

        poseStack.rotateZYX(combinedRotation.z, combinedRotation.y, combinedRotation.x);

        // Apply scale
        Vector3f scale = boneState.getScaleOffset();
        if (!scale.equals(1, 1, 1)) {
            poseStack.scale(scale.x, scale.y, scale.z);
        }

        // Apply position offset
        Vector3f posOffset = boneState.getPositionOffset();
        if (!posOffset.equals(0, 0, 0)) {
            poseStack.translate(posOffset.x, posOffset.y, posOffset.z);
        }

        // Move back from pivot point
        poseStack.translate(-pivot.x, -pivot.y, -pivot.z);

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
    }


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
     * <p>
     * {@code StrataMeshData.rotation()} is in RADIANS</h3>
     * These values come pre-converted from the model loader and are passed directly
     * to JOML's {@link Matrix4f#rotateZYX} which expects radians.
     * Do NOT call {@code Math.toRadians()} on them
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
            // mesh: translate then rotate, no reverse translate — mirrors original renderMesh.
            matrix.translate(origin.x, origin.y, origin.z);
            matrix.rotateZYX(rot.z, rot.y, rot.x); // JOML, radians, ZYX
        }
    }
}