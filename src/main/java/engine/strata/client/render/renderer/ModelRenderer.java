package engine.strata.client.render.renderer;

import engine.helios.BufferBuilder;
import engine.helios.MatrixStack;
import engine.helios.VertexFormat;
import engine.strata.client.render.model.StrataModel;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Renders StrataModel instances by converting mesh data into vertex buffers.
 */
public class ModelRenderer {

    /**
     * Renders a complete model with the given transformation matrix.
     * Called from the Render Thread.
     */
    public void render(StrataModel model, Matrix4f pose, BufferBuilder builder) {
        // Ensure the buffer is ready for writing
        if (!builder.isBuilding()) {
            builder.begin(VertexFormat.POSITION_TEXTURE_COLOR);
        }

        MatrixStack stack = new MatrixStack();
        stack.push();
        stack.peek().set(pose);

        renderBone(model, model.getRoot(), stack, builder);

        stack.pop();
    }

    /**
     * Renders a model with a MatrixStack (for compatibility).
     */
    public void render(StrataModel model, MatrixStack stack, BufferBuilder builder) {
        // Ensure the buffer is ready for writing
        if (!builder.isBuilding()) {
            builder.begin(VertexFormat.POSITION_TEXTURE_COLOR);
        }

        renderBone(model, model.getRoot(), stack, builder);
    }

    /**
     * Recursively renders a bone and all its children.
     */
    private void renderBone(StrataModel model, StrataModel.Bone bone, MatrixStack stack, BufferBuilder builder) {
        stack.push();

        // 1. Apply bone transformations
        Vector3f pivot = bone.getPivot();
        Vector3f rotation = bone.getRotation();
        Vector3f animRotation = bone.getAnimRotation();
        Vector3f animTranslation = bone.getAnimTranslation();
        Vector3f animScale = bone.getAnimScale();

        // Move to pivot point
        stack.translate(pivot.x, pivot.y, pivot.z);

        // Apply animation translation
        stack.translate(animTranslation.x, animTranslation.y, animTranslation.z);

        // Apply rotations (model rotation + animation rotation)
        // Rotation order: Z -> Y -> X
        float totalRotZ = rotation.z + animRotation.z;
        float totalRotY = rotation.y + animRotation.y;
        float totalRotX = rotation.x + animRotation.x;

        if (totalRotZ != 0) stack.rotate(totalRotZ, 0, 0, 1);
        if (totalRotY != 0) stack.rotate(totalRotY, 0, 1, 0);
        if (totalRotX != 0) stack.rotate(totalRotX, 1, 0, 0);

        // Apply scale
        if (animScale.x != 1 || animScale.y != 1 || animScale.z != 1) {
            stack.scale(animScale.x, animScale.y, animScale.z);
        }

        // Move back from pivot
        stack.translate(-pivot.x, -pivot.y, -pivot.z);

        // 2. Render all meshes in this bone
        for (String meshId : bone.getMeshIds()) {
            StrataModel.MeshData meshData = model.getMesh(meshId);
            if (meshData != null) {
                if (meshData.type().equals("blockbench_cuboid")) {
                    renderCuboid(meshData, stack.peek(), builder);
                } else {
                    renderMesh(meshData, stack.peek(), builder);
                }
            }
        }

        // 3. Render all child bones
        for (StrataModel.Bone child : bone.getChildren()) {
            renderBone(model, child, stack, builder);
        }

        stack.pop();
    }

    private void renderCuboid(StrataModel.MeshData meshData, Matrix4f matrix, BufferBuilder builder) {
        StrataModel.Cuboid cuboid = meshData.cuboid();
        if (cuboid == null) return;

        Vector3f origin = meshData.origin();
        Vector3f rotation = new Vector3f(meshData.rotation());
        Vector3f from = new Vector3f(cuboid.from());
        Vector3f to = new Vector3f(cuboid.to());
        float inflate = cuboid.inflate();

        // 1. Apply inflation to the corners
//        if (inflate != 0) {
//            from.sub(inflate, inflate, inflate);
//            to.add(inflate, inflate, inflate);
//        }

        // 2. Define the 8 corner points of the box
        float x0 = from.x, y0 = from.y, z0 = from.z;
        float x1 = to.x,   y1 = to.y,   z1 = to.z;

        // Apply local origin translation
        matrix.translate(origin.x, origin.y, origin.z);

        // 3. Render each face defined in the JSON
        // Mapping: {FaceName: [Vertex Indices]}
        cuboid.faces().forEach((name, face) -> {
            float[] uv = face.uv();
            // Convert [u1, v1, u2, v2] to 0.0-1.0 range (assuming 64x64 texture)
            float u1 = uv[0] / 64f, v1 = uv[1] / 64f;
            float u2 = uv[2] / 64f, v2 = uv[3] / 64f;

            switch (name) {
                case "north" -> drawCuboidFace(matrix, builder, x1, y0, z0, x0, y0, z0, x0, y1, z0, x1, y1, z0, u1, v1, u2, v2);
                case "south" -> drawCuboidFace(matrix, builder, x0, y0, z1, x1, y0, z1, x1, y1, z1, x0, y1, z1, u1, v1, u2, v2);
                case "west"  -> drawCuboidFace(matrix, builder, x0, y0, z0, x0, y0, z1, x0, y1, z1, x0, y1, z0, u1, v1, u2, v2);
                case "east"  -> drawCuboidFace(matrix, builder, x1, y0, z1, x1, y0, z0, x1, y1, z0, x1, y1, z1, u1, v1, u2, v2);
                case "up"    -> drawCuboidFace(matrix, builder, x0, y1, z0, x1, y1, z0, x1, y1, z1, x0, y1, z1, u1, v1, u2, v2);
                case "down"  -> drawCuboidFace(matrix, builder, x0, y0, z1, x1, y0, z1, x1, y0, z0, x0, y0, z0, u1, v1, u2, v2);
            }
        });

//        matrix.rotateXYZ(rotation.x, rotation.y, rotation.z);
        matrix.translate(-origin.x, -origin.y, -origin.z);
    }

    /**
     * Helper to draw a quad face (2 triangles) for the cuboid.
     */
    private void drawCuboidFace(Matrix4f matrix, BufferBuilder builder,
                                float x1, float y1, float z1, float x2, float y2, float z2,
                                float x3, float y3, float z3, float x4, float y4, float z4,
                                float u1, float v1, float u2, float v2) {
        // Triangle 1
        builder.vertex(matrix, x1, y1, z1).tex(u1, v1).color(1, 1, 1, 1).next();
        builder.vertex(matrix, x2, y2, z2).tex(u2, v1).color(1, 1, 1, 1).next();
        builder.vertex(matrix, x3, y3, z3).tex(u2, v2).color(1, 1, 1, 1).next();

        // Triangle 2
        builder.vertex(matrix, x1, y1, z1).tex(u1, v1).color(1, 1, 1, 1).next();
        builder.vertex(matrix, x3, y3, z3).tex(u2, v2).color(1, 1, 1, 1).next();
        builder.vertex(matrix, x4, y4, z4).tex(u1, v2).color(1, 1, 1, 1).next();
    }

    /**
     * Renders a single mesh by triangulating faces and streaming to the buffer.
     */
    private void renderMesh(StrataModel.MeshData meshData, Matrix4f matrix, BufferBuilder builder) {
        StrataModel.Mesh mesh = meshData.mesh();
        if (mesh == null) return;

        Vector3f origin = meshData.origin();
        Vector3f rotation = new Vector3f(meshData.rotation());
        Map<String, Vector3f> vertices = mesh.vertices();
        Map<String, StrataModel.Face> faces = mesh.faces();

        matrix.translate(origin.x,origin.y,origin.z);

        // Process each face
        for (StrataModel.Face face : faces.values()) {
            List<String> ids = face.getVertexIds();
            Map<String, float[]> uvs = face.getUvs();

            // Triangulate the face (support both triangles and quads)
            if (ids.size() == 4) {
                List<String> sortedIds = sortQuad(vertices, ids);

                renderQuad(vertices, uvs, sortedIds.get(0), sortedIds.get(1), sortedIds.get(2), sortedIds.get(3), matrix, builder);
            } else if (ids.size() == 3) {
                renderTriangle(vertices, uvs, ids.get(0), ids.get(1), ids.get(2), matrix, builder);
            }
        }

        matrix.translate(-origin.x,-origin.y,-origin.z);
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
                .next();

        // Vertex 2
        builder.vertex(matrix, v2.x, v2.y, v2.z)
                .tex(uv2[0], uv2[1])
                .color(1, 1, 1, 1)
                .next();

        // Vertex 3
        builder.vertex(matrix, v3.x, v3.y, v3.z)
                .tex(uv3[0], uv3[1])
                .color(1, 1, 1, 1)
                .next();
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