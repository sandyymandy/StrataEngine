package engine.strata.client.render.renderer;

import engine.helios.BufferBuilder;
import engine.helios.MatrixStack;
import engine.helios.VertexFormat;
import engine.strata.client.render.model.StrataModel;
import org.joml.Matrix4f;
import org.joml.Vector3f;

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
                renderMesh(meshData, stack.peek(), builder);
            }
        }

        // 3. Render all child bones
        for (StrataModel.Bone child : bone.getChildren()) {
            renderBone(model, child, stack, builder);
        }

        stack.pop();
    }

    /**
     * Renders a single mesh by triangulating faces and streaming to the buffer.
     */
    private void renderMesh(StrataModel.MeshData mesh, Matrix4f matrix, BufferBuilder builder) {
        Map<String, Vector3f> vertices = mesh.getVertices();
        Map<String, StrataModel.Face> faces = mesh.getFaces();

        // Process each face
        for (StrataModel.Face face : faces.values()) {
            List<String> vertexIds = face.getVertexIds();
            Map<String, float[]> uvs = face.getUvs();

            // Triangulate the face (support both triangles and quads)
            if (vertexIds.size() == 3) {
                // Triangle - render directly
                renderTriangle(vertices, uvs, vertexIds.get(0), vertexIds.get(1), vertexIds.get(2), matrix, builder);
            } else if (vertexIds.size() == 4) {
                // Quad - split into two triangles
                renderTriangle(vertices, uvs, vertexIds.get(0), vertexIds.get(1), vertexIds.get(2), matrix, builder);
                renderTriangle(vertices, uvs, vertexIds.get(2), vertexIds.get(3), vertexIds.get(0), matrix, builder);
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
}