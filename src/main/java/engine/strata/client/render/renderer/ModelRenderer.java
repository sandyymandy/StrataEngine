package engine.strata.client.render.renderer;

import engine.helios.BufferBuilder;
import engine.helios.MatrixStack;
import engine.helios.RenderLayer;
import engine.helios.VertexFormat;
import engine.strata.client.StrataClient;
import engine.strata.client.render.RenderLayers;
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
 * Renders StrataModel instances by converting mesh data into vertex buffers.
 */
public class ModelRenderer {
    private static final Logger LOGGER = LoggerFactory.getLogger("ModelRenderer");

    public void render(StrataModel model, StrataSkin skin, MatrixStack poseStack) {
        for (Map.Entry<String, StrataSkin.TextureData> entry : skin.textures().entrySet()) {
            String textureSlot = entry.getKey(); // e.g., "bia.png", "steve.png"
            StrataSkin.TextureData texData = entry.getValue();

            LOGGER.debug("Rendering test model layer '{}' with texture: {}", textureSlot, texData.path());

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

            // CRITICAL: Pass the texture slot filter so only matching meshes are rendered
            renderModel(model, skin, textureSlot, poseStack, buffer);
        }
    }

    /**
     * Renders a model with a MatrixStack.
     * This renders ALL meshes (used when you have a single texture).
     */
    public void renderModel(StrataModel model, StrataSkin skin, MatrixStack poseStack, BufferBuilder builder) {
        if (!builder.isBuilding()) {
            builder.begin(VertexFormat.POSITION_TEXTURE_COLOR);
        }
        renderBone(model, skin, null, model.getRoot(), poseStack, builder);
    }

    /**
     * Renders a model with a specific texture slot filter.
     * Only meshes matching the textureSlot will be rendered.
     *
     * @param textureSlotFilter The texture slot to render (e.g., "bia.png", "steve.png")
     */
    public void renderModel(StrataModel model, StrataSkin skin, String textureSlotFilter, MatrixStack poseStack, BufferBuilder builder) {
        if (!builder.isBuilding()) {
            builder.begin(VertexFormat.POSITION_TEXTURE_COLOR);
        }
        renderBone(model, skin, textureSlotFilter, model.getRoot(), poseStack, builder);
    }

    /**
     * Recursively renders a bone and all its children.
     *
     * @param textureSlotFilter If not null, only render meshes with this texture slot
     */
    private void renderBone(StrataModel model, StrataSkin skin, String textureSlotFilter,
                            StrataModel.Bone bone, MatrixStack poseStack, BufferBuilder builder) {
        poseStack.push();

        // 1. Apply bone transformations
        Vector3f pivot = bone.getPivot();
        Vector3f rotation = bone.getRotation();
        Vector3f animRotation = bone.getAnimRotation();
        Vector3f animTranslation = bone.getAnimTranslation();
        Vector3f animScale = bone.getAnimScale();

        // Move to pivot point
        poseStack.translate(pivot.x, pivot.y, pivot.z);

        // Apply animation translation
        poseStack.translate(animTranslation.x, animTranslation.y, animTranslation.z);

        // Apply rotations (model rotation + animation rotation)
        // Rotation order: Z -> Y -> X
        float totalRotZ = rotation.z + animRotation.z;
        float totalRotY = rotation.y + animRotation.y;
        float totalRotX = rotation.x + animRotation.x;

        if (totalRotZ != 0) poseStack.rotate(totalRotZ, 0, 0, 1);
        if (totalRotY != 0) poseStack.rotate(totalRotY, 0, 1, 0);
        if (totalRotX != 0) poseStack.rotate(totalRotX, 1, 0, 0);

        // Apply scale
        if (animScale.x != 1 || animScale.y != 1 || animScale.z != 1) {
            poseStack.scale(animScale.x, animScale.y, animScale.z);
        }

        // Move back from pivot
        poseStack.translate(-pivot.x, -pivot.y, -pivot.z);

        // 2. Render all meshes in this bone (filtered by texture slot)
        for (String meshId : bone.getMeshIds()) {
            StrataModel.MeshData meshData = model.getMesh(meshId);
            if (meshData != null) {
                // CRITICAL FIX: Filter by texture slot if specified
                if (textureSlotFilter != null && !meshData.textureSlot().equals(textureSlotFilter)) {
                    continue; // Skip this mesh, it uses a different texture
                }

                if (meshData.type().equals("blockbench_cuboid")) {
                    renderCuboid(model, meshData, skin, poseStack.peek(), builder);
                } else {
                    renderMesh(model, meshData, skin, poseStack.peek(), builder);
                }
            }
        }

        // 3. Render all child bones
        for (StrataModel.Bone child : bone.getChildren()) {
            renderBone(model, skin, textureSlotFilter, child, poseStack, builder);
        }

        poseStack.pop();
    }

    private void renderCuboid(StrataModel model, StrataModel.MeshData meshData, StrataSkin skin, Matrix4f matrix, BufferBuilder builder) {
        StrataModel.Cuboid cuboid = meshData.cuboid();
        if (cuboid == null) return;

        // 1. Use a local copy of the matrix so transformations don't leak
        Matrix4f localMatrix = new Matrix4f(matrix);

        Vector3f origin = meshData.origin();
        Vector3f rotation = new Vector3f(meshData.rotation());
        Vector3f from = new Vector3f(cuboid.from());
        Vector3f to = new Vector3f(cuboid.to());
        float inflate = cuboid.inflate();

        float uScale = model.uScale();
        float vScale = model.vScale();

        // 2. Apply inflation
        if (inflate != 0) {
            from.sub(inflate, inflate, inflate);
            to.add(inflate, inflate, inflate);
        }

        float x0 = from.x, y0 = from.y, z0 = from.z;
        float x1 = to.x,   y1 = to.y,   z1 = to.z;

        // 3. Apply Element Rotation around its Origin (Pivot)
        // Order: Translate to Pivot -> Rotate -> Translate back
        localMatrix.translate(origin.x, origin.y, origin.z);
        localMatrix.rotateZYX(rotation.z,rotation.y,rotation.x);
        localMatrix.translate(-origin.x, -origin.y, -origin.z);

        // 4. Render faces with corrected winding order (CCW)
        cuboid.faces().forEach((name, face) -> {
            float[] uv = face.uv();
            float u1 = uv[0] * uScale;
            float v1 = uv[1] * vScale;
            float u2 = uv[2] * uScale;
            float v2 = uv[3] * vScale;

            switch (name) {
                // format: drawCuboidFace(matrix, builder, bottom-left, bottom-right, top-right, top-left, u1, v1, u2, v2)

                case "north" -> // z0 plane
                        drawCuboidFace(localMatrix, builder, x1, y0, z0, x0, y0, z0, x0, y1, z0, x1, y1, z0, u1, v2, u2, v1);

                case "south" -> // z1 plane
                        drawCuboidFace(localMatrix, builder, x0, y0, z1, x1, y0, z1, x1, y1, z1, x0, y1, z1, u1, v2, u2, v1);

                case "west"  -> // x0 plane
                        drawCuboidFace(localMatrix, builder, x0, y0, z0, x0, y0, z1, x0, y1, z1, x0, y1, z0, u1, v2, u2, v1);

                case "east"  -> // x1 plane
                        drawCuboidFace(localMatrix, builder, x1, y0, z1, x1, y0, z0, x1, y1, z0, x1, y1, z1, u1, v2, u2, v1);

                case "up"    -> // y1 plane
                        drawCuboidFace(localMatrix, builder, x0, y1, z1, x1, y1, z1, x1, y1, z0, x0, y1, z0, u1, v2, u2, v1);

                case "down"  -> // y0 plane
                        drawCuboidFace(localMatrix, builder, x0, y0, z0, x1, y0, z0, x1, y0, z1, x0, y0, z1, u1, v2, u2, v1);
            }
        });
    }

    /**
     * Helper to draw a quad face (2 triangles) for the cuboid.
     */
    private void drawCuboidFace(Matrix4f matrix, BufferBuilder builder,
                                float x1, float y1, float z1, float x2, float y2, float z2,
                                float x3, float y3, float z3, float x4, float y4, float z4,
                                float u1, float v1, float u2, float v2) {
        // Triangle 1
        builder.vertex(matrix, x1, y1, z1).tex(u1, v1).color(1, 1, 1, 1).end();
        builder.vertex(matrix, x2, y2, z2).tex(u2, v1).color(1, 1, 1, 1).end();
        builder.vertex(matrix, x3, y3, z3).tex(u2, v2).color(1, 1, 1, 1).end();

        // Triangle 2
        builder.vertex(matrix, x1, y1, z1).tex(u1, v1).color(1, 1, 1, 1).end();
        builder.vertex(matrix, x3, y3, z3).tex(u2, v2).color(1, 1, 1, 1).end();
        builder.vertex(matrix, x4, y4, z4).tex(u1, v2).color(1, 1, 1, 1).end();
    }

    /**
     * Renders a single mesh by triangulating faces and streaming to the buffer.
     */
    private void renderMesh(StrataModel model, StrataModel.MeshData meshData, StrataSkin skin, Matrix4f matrix, BufferBuilder builder) {
        StrataModel.Mesh mesh = meshData.mesh();
        if (mesh == null) return;

        // FIX: Prevent transformation leakage
        Matrix4f localMatrix = new Matrix4f(matrix);
        Vector3f origin = meshData.origin();
        Vector3f rotation = meshData.rotation();

        // Apply local rotation at pivot
        localMatrix.translate(origin.x, origin.y, origin.z);
        localMatrix.rotateXYZ(rotation.x, rotation.y, rotation.z);
        localMatrix.translate(-origin.x, -origin.y, -origin.z);

        Map<String, Vector3f> vertices = mesh.vertices();
        Map<String, StrataModel.Face> faces = mesh.faces();

        for (StrataModel.Face face : faces.values()) {
            List<String> ids = face.getVertexIds();
            Map<String, float[]> uvs = face.getUvs();

            if (ids.size() == 4) {
                List<String> sortedIds = sortQuad(vertices, ids);
                renderQuad(vertices, uvs, sortedIds.get(0), sortedIds.get(1), sortedIds.get(2), sortedIds.get(3), localMatrix, builder);
            } else if (ids.size() == 3) {
                renderTriangle(vertices, uvs, ids.get(0), ids.get(1), ids.get(2), localMatrix, builder);
            }
        }
    }

    /**
     * Renders a single triangle.
     */
    private void renderTriangle(Map<String, Vector3f> vertices, Map<String, float[]> uvs,
                                String v1Id, String v2Id, String v3Id,
                                Matrix4f matrix, BufferBuilder builder) {
        // Get vertex positions
        Vector3f v1 = vertices.get(v1Id);
        Vector3f v2 = vertices.get(v2Id);
        Vector3f v3 = vertices.get(v3Id);

        if (v1 == null || v2 == null || v3 == null) {
            return; // Skip if any vertex is missing
        }

        // Get UVs (or use default if missing)
        float[] uv1 = uvs.getOrDefault(v1Id, new float[]{0, 0});
        float[] uv2 = uvs.getOrDefault(v2Id, new float[]{1, 0});
        float[] uv3 = uvs.getOrDefault(v3Id, new float[]{1, 1});

        // Vertex 1
        builder.vertex(matrix, v1.x, v1.y, v1.z)
                .tex(uv1[0], uv1[1])
                .color(1, 1, 1, 1)
                .end();

        // Vertex 2
        builder.vertex(matrix, v2.x, v2.y, v2.z)
                .tex(uv2[0], uv2[1])
                .color(1, 1, 1, 1)
                .end();

        // Vertex 3
        builder.vertex(matrix, v3.x, v3.y, v3.z)
                .tex(uv3[0], uv3[1])
                .color(1, 1, 1, 1)
                .end();
    }

    private void renderQuad(Map<String, Vector3f> vertices, Map<String, float[]> uvs,
                            String v1Id, String v2Id, String v3Id, String v4Id,
                            Matrix4f matrix, BufferBuilder builder) {

        // Triangle 1: V1 -> V2 -> V3
        renderTriangle(vertices, uvs, v1Id, v2Id, v3Id, matrix, builder);

        // Triangle 2: V3 -> V4 -> V1
        renderTriangle(vertices, uvs, v3Id, v4Id, v1Id, matrix, builder);
    }

    private List<String> sortQuad(Map<String, Vector3f> allVertices, List<String> faceIds) {
        // 1. Fetch positions
        Vector3f v0 = allVertices.get(faceIds.get(0));
        Vector3f v1 = allVertices.get(faceIds.get(1));
        Vector3f v2 = allVertices.get(faceIds.get(2));
        Vector3f v3 = allVertices.get(faceIds.get(3));

        if (v0 == null || v1 == null || v2 == null || v3 == null) return faceIds;

        // 2. Calculate Centroid (Center of the quad)
        Vector3f center = new Vector3f(v0).add(v1).add(v2).add(v3).div(4.0f);

        // 3. Calculate Normal (Cross product of diagonals is robust for non-planar quads)
        // Diagonal A: v0 -> v2, Diagonal B: v1 -> v3
        Vector3f diagA = new Vector3f(v2).sub(v0);
        Vector3f diagB = new Vector3f(v3).sub(v1);
        Vector3f normal = new Vector3f(diagA).cross(diagB).normalize();

        // 4. Define a reference axis (Right vector)
        Vector3f right = new Vector3f(v0).sub(center).normalize();
        // Calculate the Up vector perpendicular to Normal and Right
        Vector3f up = new Vector3f(normal).cross(right).normalize();

        // 5. Create a list of pairs (Angle, ID)
        List<Map.Entry<Float, String>> sorted = new ArrayList<>();

        for (String id : faceIds) {
            Vector3f v = allVertices.get(id);
            Vector3f dir = new Vector3f(v).sub(center);

            // Project onto our 2D plane defined by Right/Up
            float x = dir.dot(right);
            float y = dir.dot(up);

            // Calculate angle (0 to 2PI)
            float angle = (float) Math.atan2(y, x);
            sorted.add(new AbstractMap.SimpleEntry<>(angle, id));
        }

        // 6. Sort by angle (Counter-Clockwise)
        sorted.sort(Map.Entry.comparingByKey());

        // Return just the IDs
        List<String> result = new ArrayList<>();
        for (Map.Entry<Float, String> entry : sorted) {
            result.add(entry.getValue());
        }
        return result;
    }
}