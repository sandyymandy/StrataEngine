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
     * Parses a mesh or cuboid from JSON.
     */
    private static StrataModel.MeshData parseMesh(JsonObject meshObj) {
        String type = meshObj.get("type").getAsString();
        String textureSlot = meshObj.get("texture").getAsString();
        Vector3f origin = parseVector3f(meshObj.getAsJsonArray("origin"));
        Vector3f rotation = meshObj.has("rotation") ? parseVector3f(meshObj.getAsJsonArray("rotation")) : new Vector3f();;

        if ("blockbench_cuboid".equals(type)) {
            // 1. Parse Cuboid Geometry
            Vector3f from = parseVector3f(meshObj.getAsJsonArray("from"));
            Vector3f to = parseVector3f(meshObj.getAsJsonArray("to"));
            float inflate = meshObj.has("inflate") ? meshObj.get("inflate").getAsFloat() : 0;

            // 2. Parse Cuboid Faces
            Map<String, StrataModel.CuboidFace> cuboidFaces = new HashMap<>();
            JsonObject facesObj = meshObj.getAsJsonObject("faces");
            if (facesObj != null) {
                for (Map.Entry<String, JsonElement> entry : facesObj.entrySet()) {
                    JsonObject faceObj = entry.getValue().getAsJsonObject();
                    float[] uv = parseRawFloatArray(faceObj.getAsJsonArray("uv"));
                    int faceRotation = faceObj.has("face_rotation") ? faceObj.get("face_rotation").getAsInt() : 0;

                    cuboidFaces.put(entry.getKey(), new StrataModel.CuboidFace(uv, faceRotation));
                }
            }

            StrataModel.Cuboid cuboidData = new StrataModel.Cuboid(from, to, inflate, cuboidFaces);
            return new StrataModel.MeshData(type, textureSlot, origin, rotation, null, cuboidData);

        } else {
            // Original "blockbench_mesh" parsing logic
            Map<String, Vector3f> vertices = new HashMap<>();
            JsonObject verticesObj = meshObj.getAsJsonObject("vertices");
            if (verticesObj != null) {
                for (Map.Entry<String, JsonElement> entry : verticesObj.entrySet()) {
                    vertices.put(entry.getKey(), parseVector3f(entry.getValue().getAsJsonArray()));
                }
            }

            Map<String, StrataModel.Face> meshFaces = new HashMap<>();
            JsonObject facesObj = meshObj.getAsJsonObject("faces");
            if (facesObj != null) {
                for (Map.Entry<String, JsonElement> entry : facesObj.entrySet()) {
                    JsonObject faceObj = entry.getValue().getAsJsonObject();
                    List<String> vertexIds = new ArrayList<>();
                    faceObj.getAsJsonArray("vertices").forEach(e -> vertexIds.add(e.getAsString()));

                    Map<String, float[]> uvs = new HashMap<>();
                    JsonObject uvObj = faceObj.getAsJsonObject("uv");
                    for (Map.Entry<String, JsonElement> uvEntry : uvObj.entrySet()) {
                        uvs.put(uvEntry.getKey(), parseRawFloatArray(uvEntry.getValue().getAsJsonArray()));
                    }
                    meshFaces.put(entry.getKey(), new StrataModel.Face(vertexIds, uvs));
                }
            }

            StrataModel.Mesh meshData = new StrataModel.Mesh(vertices, meshFaces);
            return new StrataModel.MeshData(type, textureSlot, origin, rotation, meshData, null);
        }
    }

    // Helper for raw float arrays (UVs, Positions, Normals)
    private static float[] parseRawFloatArray(JsonArray array) {
        float[] result = new float[array.size()];
        for (int i = 0; i < array.size(); i++) {
            result[i] = array.get(i).getAsFloat();
        }
        return result;
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

        // 1. Define the geometric boundaries (Standard 1x1x1 block size is -8 to 8 in Blockbench)
        Vector3f from = new Vector3f(-8, -8, -8);
        Vector3f to = new Vector3f(8, 8, 8);

        // 2. Define faces with default UVs [u1, v1, u2, v2]
        Map<String, StrataModel.CuboidFace> faces = new HashMap<>();
        float[] defaultUv = new float[]{0, 0, 16, 16};

        faces.put("north", new StrataModel.CuboidFace(defaultUv, 0));
        faces.put("south", new StrataModel.CuboidFace(defaultUv, 0));
        faces.put("west",  new StrataModel.CuboidFace(defaultUv, 0));
        faces.put("up",    new StrataModel.CuboidFace(defaultUv, 0));
        faces.put("down",  new StrataModel.CuboidFace(defaultUv, 0));

        // 3. Create the Cuboid data container
        StrataModel.Cuboid cuboidData = new StrataModel.Cuboid(from, to, 0, faces);

        // 4. Create the MeshData using the new two-variable constructor
        Map<String, StrataModel.MeshData> meshes = new HashMap<>();
        meshes.put("fallback_cuboid", new StrataModel.MeshData(
                "blockbench_cuboid", "main", new Vector3f(), new Vector3f(), null, cuboidData
        ));

        // 5. Build the bone hierarchy
        StrataModel.Bone root = new StrataModel.Bone(
                "root", null, new Vector3f(0, 0, 0),
                new Vector3f(0, 0, 0), Collections.singletonList("fallback_cuboid")
        );

        return new StrataModel(id, root, Collections.singletonList("main"), meshes);
    }
}
