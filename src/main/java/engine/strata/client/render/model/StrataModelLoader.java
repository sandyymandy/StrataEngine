package engine.strata.client.render.model;

import com.google.gson.*;
import engine.strata.core.io.ResourceManager;
import engine.strata.util.Identifier;
import org.joml.Vector3f;
import org.lwjgl.BufferUtils;
import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.List;
import static org.lwjgl.opengl.GL30.*;

public class StrataModelLoader {

    public static StrataModel load(Identifier id) {
        String json = ResourceManager.loadAsString(id, "models", "strmodel");
        JsonObject root = JsonParser.parseString(json).getAsJsonObject();

        JsonArray bonesArray = root.getAsJsonArray("bones");
        JsonObject meshPool = root.getAsJsonObject("meshes");

        // Find the root bone (parent is null)
        JsonObject rootBoneJson = null;
        for (JsonElement b : bonesArray) {
            if (b.getAsJsonObject().get("parent").isJsonNull()) {
                rootBoneJson = b.getAsJsonObject();
                break;
            }
        }

        return new StrataModel(parseBone(rootBoneJson, bonesArray, meshPool));
    }

    private static StrataModel.Bone parseBone(JsonObject boneJson, JsonArray allBones, JsonObject meshPool) {
        String name = boneJson.get("name").getAsString();
        JsonArray p = boneJson.getAsJsonArray("pivot");
        StrataModel.Bone bone = new StrataModel.Bone(name, new Vector3f(p.get(0).getAsFloat(), p.get(1).getAsFloat(), p.get(2).getAsFloat()));

        // 1. Attach Meshes
        if (boneJson.has("meshes")) {
            for (JsonElement mId : boneJson.getAsJsonArray("meshes")) {
                bone.addMesh(uploadMesh(meshPool.getAsJsonObject(mId.getAsString())));
            }
        }

        // 2. Find and attach Children
        for (JsonElement b : allBones) {
            JsonObject possibleChild = b.getAsJsonObject();
            JsonElement parent = possibleChild.get("parent");
            if (!parent.isJsonNull() && parent.getAsString().equals(name)) {
                bone.addChild(parseBone(possibleChild, allBones, meshPool));
            }
        }

        return bone;
    }

    private static StrataModel.Mesh uploadMesh(JsonObject meshJson) {
        JsonObject vertexPool = meshJson.getAsJsonObject("vertices");
        JsonObject faces = meshJson.getAsJsonObject("faces");

        List<Float> bufferData = new ArrayList<>();

        // Iterate through faces to build triangles
        for (String faceId : faces.keySet()) {
            JsonObject face = faces.getAsJsonObject(faceId);
            JsonArray faceVerts = face.getAsJsonArray("vertices");
            JsonObject faceUVs = face.getAsJsonObject("uv");

            // Triangulate Quads or Polygons (Fan Method)
            for (int i = 1; i < faceVerts.size() - 1; i++) {
                // Standard CCW order
                addVertexToBuffer(bufferData, faceVerts.get(0).getAsString(), vertexPool, faceUVs);
                addVertexToBuffer(bufferData, faceVerts.get(i + 1).getAsString(), vertexPool, faceUVs); // Swapped i and i+1
                addVertexToBuffer(bufferData, faceVerts.get(i).getAsString(), vertexPool, faceUVs);
            }
        }

        float[] data = new float[bufferData.size()];
        for (int i = 0; i < bufferData.size(); i++) data[i] = bufferData.get(i);

        int vao = glGenVertexArrays();
        int vbo = glGenBuffers();
        glBindVertexArray(vao);
        glBindBuffer(GL_ARRAY_BUFFER, vbo);

        FloatBuffer buffer = BufferUtils.createFloatBuffer(data.length);
        buffer.put(data).flip();
        glBufferData(GL_ARRAY_BUFFER, buffer, GL_STATIC_DRAW);

        int stride = 9 * 4; // Pos(3) + Tex(2) + Color(4)
        glVertexAttribPointer(0, 3, GL_FLOAT, false, stride, 0);
        glVertexAttribPointer(1, 2, GL_FLOAT, false, stride, 3 * 4);
        glVertexAttribPointer(2, 4, GL_FLOAT, false, stride, 5 * 4);
        glEnableVertexAttribArray(0);
        glEnableVertexAttribArray(1);
        glEnableVertexAttribArray(2);

        return new StrataModel.Mesh(vao, data.length / 9);
    }

    private static void addVertexToBuffer(List<Float> buffer, String vertId, JsonObject pool, JsonObject faceUVs) {
        JsonArray pos = pool.getAsJsonArray(vertId);
        JsonArray uv = faceUVs.getAsJsonArray(vertId);

        // Position
        buffer.add(pos.get(0).getAsFloat());
        buffer.add(pos.get(1).getAsFloat());
        buffer.add(pos.get(2).getAsFloat());
        // UVs
        buffer.add(uv.get(0).getAsFloat() / 16f); // Normalize UVs if Blockbench uses pixels
        buffer.add(uv.get(1).getAsFloat() / 16f);
        // Color (White)
        buffer.add(1f); buffer.add(1f); buffer.add(1f); buffer.add(1f);
    }
}