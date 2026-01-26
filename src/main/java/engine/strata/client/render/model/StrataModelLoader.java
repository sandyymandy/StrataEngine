package engine.strata.client.render.model;

import com.google.gson.*;
import engine.strata.core.io.ResourceManager;
import engine.strata.util.Identifier;
import org.joml.Vector3f;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

public class StrataModelLoader {
    private static final Logger LOGGER = LoggerFactory.getLogger("ModelLoader");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    /**
     * Loads a model from a .strmodel JSON file.
     */
    public static StrataModel load(Identifier id) {
        try {
            String json = ResourceManager.loadAsString(id, "models", "strmodel");
            if (json == null || json.isEmpty()) {
                LOGGER.error("Failed to load model: {}", id);
                return createFallbackModel(id);
            }

            JsonObject root = GSON.fromJson(json, JsonObject.class);

            // Parse texture slots
            List<String> textureSlots = new ArrayList<>();
            JsonArray texturesArray = root.getAsJsonArray("textures");
            if (texturesArray != null) {
                for (JsonElement elem : texturesArray) {
                    textureSlots.add(elem.getAsString());
                }
            }

            // Parse meshes
            Map<String, StrataModel.MeshData> meshes = new HashMap<>();
            JsonObject meshesObj = root.getAsJsonObject("meshes");
            if (meshesObj != null) {
                for (Map.Entry<String, JsonElement> entry : meshesObj.entrySet()) {
                    String meshId = entry.getKey();
                    JsonObject meshObj = entry.getValue().getAsJsonObject();
                    meshes.put(meshId, parseMesh(meshObj));
                }
            }

            // Parse bones hierarchy
            JsonArray bonesArray = root.getAsJsonArray("bones");
            Map<String, StrataModel.Bone> boneMap = new HashMap<>();
            StrataModel.Bone rootBone = null;

            // First pass: create all bones
            for (JsonElement elem : bonesArray) {
                JsonObject boneObj = elem.getAsJsonObject();
                String name = boneObj.get("name").getAsString();

                Vector3f pivot = parseVector3f(boneObj.getAsJsonArray("pivot"));
                Vector3f rotation = parseVector3f(boneObj.getAsJsonArray("rotation"));

                List<String> meshIds = new ArrayList<>();
                JsonArray meshesArray = boneObj.getAsJsonArray("meshes");
                if (meshesArray != null) {
                    for (JsonElement meshElem : meshesArray) {
                        meshIds.add(meshElem.getAsString());
                    }
                }

                StrataModel.Bone bone = new StrataModel.Bone(name, null, pivot, rotation, meshIds);
                boneMap.put(name, bone);

                // The bone with parent=null is the root
                if (boneObj.get("parent").isJsonNull()) {
                    rootBone = bone;
                }
            }

            // Second pass: establish parent-child relationships
            for (JsonElement elem : bonesArray) {
                JsonObject boneObj = elem.getAsJsonObject();
                String name = boneObj.get("name").getAsString();
                StrataModel.Bone bone = boneMap.get(name);

                if (!boneObj.get("parent").isJsonNull()) {
                    String parentName = boneObj.get("parent").getAsString();
                    StrataModel.Bone parent = boneMap.get(parentName);
                    if (parent != null) {
                        parent.addChild(bone);
                    }
                }
            }

            if (rootBone == null) {
                LOGGER.error("No root bone found in model: {}", id);
                return createFallbackModel(id);
            }

            return new StrataModel(id, rootBone, textureSlots, meshes);

        } catch (Exception e) {
            LOGGER.error("Error loading model {}: {}", id, e.getMessage());
            e.printStackTrace();
            return createFallbackModel(id);
        }
    }

    /**
     * Parses a mesh from JSON.
     */
    private static StrataModel.MeshData parseMesh(JsonObject meshObj) {
        String type = meshObj.get("type").getAsString();
        String textureSlot = meshObj.get("texture").getAsString();
        Vector3f origin = parseVector3f(meshObj.getAsJsonArray("origin"));

        // Parse vertices
        Map<String, Vector3f> vertices = new HashMap<>();
        JsonObject verticesObj = meshObj.getAsJsonObject("vertices");
        for (Map.Entry<String, JsonElement> entry : verticesObj.entrySet()) {
            String vertId = entry.getKey();
            Vector3f pos = parseVector3f(entry.getValue().getAsJsonArray());
            vertices.put(vertId, pos);
        }

        // Parse faces
        Map<String, StrataModel.Face> faces = new HashMap<>();
        JsonObject facesObj = meshObj.getAsJsonObject("faces");
        for (Map.Entry<String, JsonElement> entry : facesObj.entrySet()) {
            String faceId = entry.getKey();
            JsonObject faceObj = entry.getValue().getAsJsonObject();

            List<String> vertexIds = new ArrayList<>();
            JsonArray verticesArray = faceObj.getAsJsonArray("vertices");
            for (JsonElement elem : verticesArray) {
                vertexIds.add(elem.getAsString());
            }

            Map<String, float[]> uvs = new HashMap<>();
            JsonObject uvObj = faceObj.getAsJsonObject("uv");
            for (Map.Entry<String, JsonElement> uvEntry : uvObj.entrySet()) {
                String vertId = uvEntry.getKey();
                JsonArray uvArray = uvEntry.getValue().getAsJsonArray();
                float[] uv = new float[] {
                        uvArray.get(0).getAsFloat(),
                        uvArray.get(1).getAsFloat()
                };
                uvs.put(vertId, uv);
            }

            faces.put(faceId, new StrataModel.Face(vertexIds, uvs));
        }

        return new StrataModel.MeshData(type, textureSlot, origin, vertices, faces);
    }

    /**
     * Parses a Vector3f from a JSON array.
     */
    private static Vector3f parseVector3f(JsonArray array) {
        if (array == null || array.size() != 3) {
            return new Vector3f(0, 0, 0);
        }
        return new Vector3f(
                array.get(0).getAsFloat(),
                array.get(1).getAsFloat(),
                array.get(2).getAsFloat()
        );
    }

    /**
     * Creates a simple fallback cube model when loading fails.
     */
    private static StrataModel createFallbackModel(Identifier id) {
        LOGGER.warn("Creating fallback model for: {}", id);

        // Create a simple cube mesh
        Map<String, Vector3f> vertices = new HashMap<>();
        vertices.put("v0", new Vector3f(-0.5f, -0.5f, -0.5f));
        vertices.put("v1", new Vector3f(0.5f, -0.5f, -0.5f));
        vertices.put("v2", new Vector3f(0.5f, 0.5f, -0.5f));
        vertices.put("v3", new Vector3f(-0.5f, 0.5f, -0.5f));
        vertices.put("v4", new Vector3f(-0.5f, -0.5f, 0.5f));
        vertices.put("v5", new Vector3f(0.5f, -0.5f, 0.5f));
        vertices.put("v6", new Vector3f(0.5f, 0.5f, 0.5f));
        vertices.put("v7", new Vector3f(-0.5f, 0.5f, 0.5f));

        Map<String, float[]> uvs = new HashMap<>();
        uvs.put("v0", new float[]{0, 0});
        uvs.put("v1", new float[]{1, 0});
        uvs.put("v2", new float[]{1, 1});
        uvs.put("v3", new float[]{0, 1});

        Map<String, StrataModel.Face> faces = new HashMap<>();
        // Just create one face as an example
        faces.put("f0", new StrataModel.Face(
                Arrays.asList("v0", "v1", "v2"), uvs
        ));

        Map<String, StrataModel.MeshData> meshes = new HashMap<>();
        meshes.put("fallback_mesh", new StrataModel.MeshData(
                "blockbench_mesh", "main", new Vector3f(0, 0, 0), vertices, faces
        ));

        StrataModel.Bone root = new StrataModel.Bone(
                "root", null, new Vector3f(0, 0, 0),
                new Vector3f(0, 0, 0), Collections.singletonList("fallback_mesh")
        );

        return new StrataModel(id, root, Collections.singletonList("main"), meshes);
    }
}
