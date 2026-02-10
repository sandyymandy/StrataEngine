package engine.strata.client.render.renderer;

import engine.helios.BufferBuilder;
import engine.helios.MatrixStack;
import engine.helios.RenderLayer;
import engine.helios.VertexFormat;
import engine.strata.client.StrataClient;
import engine.strata.client.render.RenderLayers;
import engine.strata.client.render.animation.core.AnimationController;
import engine.strata.client.render.model.StrataBone;
import engine.strata.client.render.model.StrataMeshData;
import engine.strata.client.render.model.StrataModel;
import engine.strata.client.render.model.StrataSkin;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Renders StrataModel instances with integrated animation support.
 *
 * <p><b>REDESIGNED ARCHITECTURE:</b>
 * <ul>
 *   <li>Animation transforms are computed DURING rendering, not before</li>
 *   <li>No more storing animation state on bones - it's all ephemeral</li>
 *   <li>AnimationController provides current bone transforms on demand</li>
 *   <li>Eliminates timing issues and vibration from pre-computed transforms</li>
 * </ul>
 */
public class ModelRenderer {
    private static final Logger LOGGER = LoggerFactory.getLogger("ModelRenderer");

    /**
     * Renders a model with optional animation support.
     * If animationController is null, renders in bind pose.
     */
    public void render(StrataModel model, StrataSkin skin, MatrixStack poseStack, AnimationController animationController) {
        for (Map.Entry<String, StrataSkin.TextureData> entry : skin.textures().entrySet()) {
            String textureSlot = entry.getKey();
            StrataSkin.TextureData texData = entry.getValue();

            // Get the appropriate render layer
            RenderLayer layer = RenderLayers.getLayerForSlot(
                    texData.path(),
                    texData.translucent()
            );

            // Get the buffer for this layer
            BufferBuilder buffer = StrataClient.getInstance().getMasterRenderer().getBuffer(layer);

            // Ensure buffer is building
            if (!buffer.isBuilding()) {
                buffer.begin(VertexFormat.POSITION_TEXTURE_COLOR);
            }

            renderModel(model, skin, textureSlot, poseStack, buffer, animationController);
        }
    }

    /**
     * Legacy method for backward compatibility (no animation).
     */
    public void render(StrataModel model, StrataSkin skin, MatrixStack poseStack) {
        render(model, skin, poseStack, null);
    }

    /**
     * Renders a model with a MatrixStack.
     * This renders ALL meshes (used when you have a single texture).
     */
    public void renderModel(StrataModel model, StrataSkin skin, MatrixStack poseStack,
                            BufferBuilder builder, AnimationController animationController) {
        if (!builder.isBuilding()) {
            builder.begin(VertexFormat.POSITION_TEXTURE_COLOR);
        }
        renderBone(model, skin, null, model.getRoot(), poseStack, builder,
                new Matrix4f().identity(), animationController);
    }

    /**
     * Renders a model with a specific texture slot filter.
     * Only meshes matching the textureSlot will be rendered.
     *
     * @param textureSlotFilter The texture slot to render (e.g., "bia.png", "steve.png")
     */
    public void renderModel(StrataModel model, StrataSkin skin, String textureSlotFilter,
                            MatrixStack poseStack, BufferBuilder builder, AnimationController animationController) {
        if (!builder.isBuilding()) {
            builder.begin(VertexFormat.POSITION_TEXTURE_COLOR);
        }
        renderBone(model, skin, textureSlotFilter, model.getRoot(), poseStack, builder,
                new Matrix4f().identity(), animationController);
    }

    /**
     * Recursively renders a bone and all its children.
     *
     *
     * @param textureSlotFilter If not null, only render meshes with this texture slot
     * @param animationController If not null, provides animation transforms
     */
    private void renderBone(StrataModel model, StrataSkin skin, String textureSlotFilter,
                            StrataBone bone, MatrixStack poseStack, BufferBuilder builder,
                            Matrix4f parentModelMatrix, AnimationController animationController) {

        if (!bone.shouldRender()) {
            return;
        }

        poseStack.push();

        // Get static bone properties
        Vector3f pivot = bone.getPivot();
        Vector3f staticRotation = bone.getRotation();

        // Get animation transforms DIRECTLY from controller (no caching on bone!)
        Vector3f animRotation = new Vector3f(0, 0, 0);
        Vector3f animTranslation = new Vector3f(0, 0, 0);
        Vector3f animScale = new Vector3f(1, 1, 1);

        if (animationController != null) {
            // Request current animation state for this bone
            BoneTransform transform = animationController.getBoneTransform(bone.getName());
            if (transform != null) {
                animRotation = transform.rotation();
                animTranslation = transform.translation();
                animScale = transform.scale();
            }
        }

        // Handle position override (if set) - for manual control
        Vector3f finalTranslation = animTranslation;
//        if (bone.getPositionOverride() != null) {
//            finalTranslation = bone.getPositionOverride();
//        }

        // Move to pivot point
        poseStack.translate(pivot.x, pivot.y, pivot.z);

        // Apply animation translation
        poseStack.translate(finalTranslation.x, finalTranslation.y, finalTranslation.z);

        // Apply rotations (model rotation + animation rotation)
        float totalRotZ = staticRotation.z + animRotation.z;
        float totalRotY = staticRotation.y + animRotation.y;
        float totalRotX = staticRotation.x + animRotation.x;

        if (totalRotZ != 0) poseStack.rotate(totalRotZ, 0, 0, 1);
        if (totalRotY != 0) poseStack.rotate(totalRotY, 0, 1, 0);
        if (totalRotX != 0) poseStack.rotate(totalRotX, 1, 0, 0);

        // Apply scale
        if (animScale.x != 1 || animScale.y != 1 || animScale.z != 1) {
            poseStack.scale(animScale.x, animScale.y, animScale.z);
        }

        // Move back from pivot
        poseStack.translate(-pivot.x, -pivot.y, -pivot.z);

        // MATRIX TRACKING: Update matrices if tracking is enabled
        if (bone.isTrackingMatrices()) {
            updateBoneMatrices(bone, poseStack.peek(), parentModelMatrix);
        }

        // Calculate current model-space matrix for children
        Matrix4f currentModelMatrix = new Matrix4f(parentModelMatrix).mul(poseStack.peek());

        // Render meshes in this bone
        for (String meshId : bone.getMeshIds()) {
            StrataMeshData meshData = model.getMesh(meshId);
            if (meshData != null) {
                // Filter by texture slot if specified
                if (textureSlotFilter != null && !meshData.textureSlot().equals(textureSlotFilter)) {
                    continue;
                }

                if (meshData.type().equals("blockbench_cuboid")) {
                    renderCuboid(model, meshData, skin, poseStack.peek(), builder);
                } else {
                    renderMesh(model, meshData, skin, poseStack.peek(), builder);
                }
            }
        }

        // Render child bones (pass animation controller down the hierarchy)
        for (StrataBone child : bone.getChildren()) {
            renderBone(model, skin, textureSlotFilter, child, poseStack, builder,
                    currentModelMatrix, animationController);
        }

        poseStack.pop();

        // Reset change flags after rendering
        bone.resetStateChanges();
    }

    /**
     * Updates bone transformation matrices for advanced features.
     */
    private void updateBoneMatrices(StrataBone bone, Matrix4f localMatrix, Matrix4f parentModelMatrix) {
        // Local space matrix (relative to parent)
        bone.setLocalSpaceMatrix(new Matrix4f(localMatrix));

        // Model space matrix (relative to model root)
        Matrix4f modelMatrix = new Matrix4f(parentModelMatrix).mul(localMatrix);
        bone.setModelSpaceMatrix(modelMatrix);

        // World space would be updated by EntityRenderer with entity position
        // For now, just copy model space (will be fixed when entity renders)
        bone.setWorldSpaceMatrix(new Matrix4f(modelMatrix));
    }

    // ========================================================================
    // MESH RENDERING (unchanged from original)
    // ========================================================================

    private void renderCuboid(StrataModel model, StrataMeshData meshData, StrataSkin skin,
                              Matrix4f matrix, BufferBuilder builder) {
        StrataMeshData.Cuboid cuboid = meshData.cuboid();
        if (cuboid == null) return;

        Matrix4f localMatrix = new Matrix4f(matrix);
        Vector3f origin = meshData.origin();
        Vector3f rotation = new Vector3f(meshData.rotation());
        Vector3f from = new Vector3f(cuboid.from());
        Vector3f to = new Vector3f(cuboid.to());
        float inflate = cuboid.inflate();

        float uScale = model.uScale();
        float vScale = model.vScale();

        from.sub(inflate, inflate, inflate);
        to.add(inflate, inflate, inflate);

        float x0 = from.x, y0 = from.y, z0 = from.z;
        float x1 = to.x,   y1 = to.y,   z1 = to.z;

        localMatrix.translate(origin.x, origin.y, origin.z);
        localMatrix.rotateZYX(rotation.z, rotation.y, rotation.x);
        localMatrix.translate(-origin.x, -origin.y, -origin.z);

        cuboid.faces().forEach((name, face) -> {
            float[] uv = face.uv();
            float u1 = uv[0] * uScale;
            float v1 = uv[1] * vScale;
            float u2 = uv[2] * uScale;
            float v2 = uv[3] * vScale;

            switch (name) {
                case "north" ->
                        drawCuboidFace(localMatrix, builder, x1, y0, z0, x0, y0, z0, x0, y1, z0, x1, y1, z0, u1, v2, u2, v1);
                case "south" ->
                        drawCuboidFace(localMatrix, builder, x0, y0, z1, x1, y0, z1, x1, y1, z1, x0, y1, z1, u1, v2, u2, v1);
                case "west" ->
                        drawCuboidFace(localMatrix, builder, x0, y0, z0, x0, y0, z1, x0, y1, z1, x0, y1, z0, u1, v2, u2, v1);
                case "east" ->
                        drawCuboidFace(localMatrix, builder, x1, y0, z1, x1, y0, z0, x1, y1, z0, x1, y1, z1, u1, v2, u2, v1);
                case "up" ->
                        drawCuboidFace(localMatrix, builder, x0, y1, z1, x1, y1, z1, x1, y1, z0, x0, y1, z0, u1, v2, u2, v1);
                case "down" ->
                        drawCuboidFace(localMatrix, builder, x0, y0, z0, x1, y0, z0, x1, y0, z1, x0, y0, z1, u1, v2, u2, v1);
            }
        });
    }

    private void drawCuboidFace(Matrix4f matrix, BufferBuilder builder,
                                float x1, float y1, float z1, float x2, float y2, float z2,
                                float x3, float y3, float z3, float x4, float y4, float z4,
                                float u1, float v1, float u2, float v2) {
        builder.vertex(matrix, x1, y1, z1).tex(u1, v1).color(1, 1, 1, 1).end();
        builder.vertex(matrix, x2, y2, z2).tex(u2, v1).color(1, 1, 1, 1).end();
        builder.vertex(matrix, x3, y3, z3).tex(u2, v2).color(1, 1, 1, 1).end();

        builder.vertex(matrix, x1, y1, z1).tex(u1, v1).color(1, 1, 1, 1).end();
        builder.vertex(matrix, x3, y3, z3).tex(u2, v2).color(1, 1, 1, 1).end();
        builder.vertex(matrix, x4, y4, z4).tex(u1, v2).color(1, 1, 1, 1).end();
    }

    private void renderMesh(StrataModel model, StrataMeshData meshData, StrataSkin skin,
                            Matrix4f matrix, BufferBuilder builder) {
        StrataMeshData.Mesh mesh = meshData.mesh();
        if (mesh == null) return;

        Matrix4f localMatrix = new Matrix4f(matrix);
        Vector3f origin = meshData.origin();
        Vector3f rotation = meshData.rotation();

        localMatrix.translate(origin.x, origin.y, origin.z);
        localMatrix.rotateXYZ(rotation.x, rotation.y, rotation.z);
        localMatrix.translate(-origin.x, -origin.y, -origin.z);

        Map<String, Vector3f> vertices = mesh.vertices();
        Map<String, StrataMeshData.Face> faces = mesh.faces();

        for (StrataMeshData.Face face : faces.values()) {
            List<String> ids = face.getVertexIds();
            Map<String, float[]> uvs = face.getUvs();

            if (ids.size() == 4) {
                List<String> sortedIds = sortQuad(vertices, ids);
                renderQuad(vertices, uvs, sortedIds.get(0), sortedIds.get(1),
                        sortedIds.get(2), sortedIds.get(3), localMatrix, builder);
            } else if (ids.size() == 3) {
                renderTriangle(vertices, uvs, ids.get(0), ids.get(1), ids.get(2),
                        localMatrix, builder);
            }
        }
    }

    private void renderTriangle(Map<String, Vector3f> vertices, Map<String, float[]> uvs,
                                String v1Id, String v2Id, String v3Id,
                                Matrix4f matrix, BufferBuilder builder) {
        Vector3f v1 = vertices.get(v1Id);
        Vector3f v2 = vertices.get(v2Id);
        Vector3f v3 = vertices.get(v3Id);

        if (v1 == null || v2 == null || v3 == null) return;

        float[] uv1 = uvs.getOrDefault(v1Id, new float[]{0, 0});
        float[] uv2 = uvs.getOrDefault(v2Id, new float[]{1, 0});
        float[] uv3 = uvs.getOrDefault(v3Id, new float[]{1, 1});

        builder.vertex(matrix, v1.x, v1.y, v1.z).tex(uv1[0], uv1[1]).color(1, 1, 1, 1).end();
        builder.vertex(matrix, v2.x, v2.y, v2.z).tex(uv2[0], uv2[1]).color(1, 1, 1, 1).end();
        builder.vertex(matrix, v3.x, v3.y, v3.z).tex(uv3[0], uv3[1]).color(1, 1, 1, 1).end();
    }

    private void renderQuad(Map<String, Vector3f> vertices, Map<String, float[]> uvs,
                            String v1Id, String v2Id, String v3Id, String v4Id,
                            Matrix4f matrix, BufferBuilder builder) {
        renderTriangle(vertices, uvs, v1Id, v2Id, v3Id, matrix, builder);
        renderTriangle(vertices, uvs, v3Id, v4Id, v1Id, matrix, builder);
    }

    private List<String> sortQuad(Map<String, Vector3f> allVertices, List<String> faceIds) {
        Vector3f v0 = allVertices.get(faceIds.get(0));
        Vector3f v1 = allVertices.get(faceIds.get(1));
        Vector3f v2 = allVertices.get(faceIds.get(2));
        Vector3f v3 = allVertices.get(faceIds.get(3));

        if (v0 == null || v1 == null || v2 == null || v3 == null) return faceIds;

        Vector3f center = new Vector3f(v0).add(v1).add(v2).add(v3).div(4.0f);
        Vector3f diagA = new Vector3f(v2).sub(v0);
        Vector3f diagB = new Vector3f(v3).sub(v1);
        Vector3f normal = new Vector3f(diagA).cross(diagB).normalize();
        Vector3f right = new Vector3f(v0).sub(center).normalize();
        Vector3f up = new Vector3f(normal).cross(right).normalize();

        List<Map.Entry<Float, String>> sorted = new ArrayList<>();

        for (String id : faceIds) {
            Vector3f v = allVertices.get(id);
            Vector3f dir = new Vector3f(v).sub(center);
            float x = dir.dot(right);
            float y = dir.dot(up);
            float angle = (float) Math.atan2(y, x);
            sorted.add(new AbstractMap.SimpleEntry<>(angle, id));
        }

        sorted.sort(Map.Entry.comparingByKey());

        List<String> result = new ArrayList<>();
        for (Map.Entry<Float, String> entry : sorted) {
            result.add(entry.getValue());
        }
        return result;
    }

    /**
     * Simple data class to hold bone transform data.
     * Replaces the need to store this on StrataBone.
     */
    public static record BoneTransform(
            Vector3f rotation,
            Vector3f translation,
            Vector3f scale
    ) {
        public BoneTransform {
            // Defensive copy
            rotation = new Vector3f(rotation);
            translation = new Vector3f(translation);
            scale = new Vector3f(scale);
        }
    }
}