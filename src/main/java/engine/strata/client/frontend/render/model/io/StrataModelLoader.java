package engine.strata.client.frontend.render.model.io;

import com.google.gson.*;
import engine.helios.physics.AABB;
import engine.strata.client.frontend.render.model.StrataBone;
import engine.strata.client.frontend.render.model.StrataMeshData;
import engine.strata.client.frontend.render.model.StrataModel;
import engine.strata.core.io.ResourceManager;
import engine.strata.util.Identifier;
import org.joml.Vector3f;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.*;

public class StrataModelLoader {
    private static final Logger LOGGER = LoggerFactory.getLogger("ModelLoader");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    // Binary format constants
    private static final String BINARY_MAGIC = "STRM";

    /**
     * Loads a model from a .strmodel file (supports both JSON and binary formats).
     */
    public static StrataModel load(Identifier id) {
        try {
            byte[] data = ResourceManager.loadAsBytes(id, "models/entities", "strmodel");
            if (data == null || data.length == 0) {
                LOGGER.error("Failed to load model: {}", id);
                return createFallbackModel(id);
            }

            // Check if it's binary format (starts with "STRM")
            if (data.length >= 4 &&
                    data[0] == 'S' && data[1] == 'T' && data[2] == 'R' && data[3] == 'M') {
                LOGGER.info("Loading binary format model: {}", id);
                return loadBinary(id, data);
            } else {
                // Fall back to JSON format
                LOGGER.info("Loading JSON format model: {}", id);
                String json = new String(data, StandardCharsets.UTF_8);
                return loadJson(id, json);
            }

        } catch (Exception e) {
            LOGGER.error("Error loading model {}: {}", id, e.getMessage());
            e.printStackTrace();
            return createFallbackModel(id);
        }
    }

    /**
     * Loads a model from binary format.
     */
    private static StrataModel loadBinary(Identifier id, byte[] data) throws IOException {
        BinaryReader reader = new BinaryReader(data);

        // Read and validate header
        String magic = reader.readFixedString(4);
        if (!BINARY_MAGIC.equals(magic)) {
            throw new IOException("Invalid binary format: wrong magic number");
        }

        int version = reader.readUInt16();
        int flags = reader.readUInt16();
        int stringCount = reader.readUInt16();
        int textureCount = reader.readUInt16();
        int boneCount = reader.readUInt16();
        int meshCount = reader.readUInt16();
        int cuboidCount = reader.readUInt16();
        int stringTableSize = reader.readInt32();

        // Read bounding box
        float minX = reader.readFloat32();
        float minY = reader.readFloat32();
        float minZ = reader.readFloat32();
        float maxX = reader.readFloat32();
        float maxY = reader.readFloat32();
        float maxZ = reader.readFloat32();

        AABB boundingBox = new AABB(minX, minY, minZ, maxX, maxY, maxZ);

        // Skip reserved bytes
        reader.skip(18);

        // Read string table
        String[] stringTable = new String[stringCount];
        for (int i = 0; i < stringCount; i++) {
            stringTable[i] = reader.readString();
        }

        // Read textures
        Map<String, StrataModel.TextureInfo> textureMap = new HashMap<>();
        String[] textureNames = new String[textureCount];

        for (int i = 0; i < textureCount; i++) {
            int nameIndex = reader.readUInt16();
            int width = reader.readUInt16();
            int height = reader.readUInt16();

            String textureName = stringTable[nameIndex];
            textureNames[i] = textureName;
            textureMap.put(textureName, new StrataModel.TextureInfo(width, height));
        }

        // Read bones
        BoneData[] bones = new BoneData[boneCount];

        for (int i = 0; i < boneCount; i++) {
            int nameIndex = reader.readUInt16();
            int parentIndex = reader.readInt16();

            Vector3f pivot = new Vector3f(
                    reader.readFloat32(),
                    reader.readFloat32(),
                    reader.readFloat32()
            );

            // Rotation is stored in degrees, convert to radians
            Vector3f rotation = new Vector3f(
                    (float) Math.toRadians(reader.readFloat32()),
                    (float) Math.toRadians(reader.readFloat32()),
                    (float) Math.toRadians(reader.readFloat32())
            );

            int boneFlags = reader.readUInt8();
            boolean hidden = (boneFlags & 1) != 0;

            int elementCount = reader.readUInt16();
            List<ElementRef> elements = new ArrayList<>();

            for (int j = 0; j < elementCount; j++) {
                int type = reader.readUInt8(); // 0 = mesh, 1 = cuboid
                int index = reader.readUInt16();
                elements.add(new ElementRef(type, index));
            }

            bones[i] = new BoneData(
                    stringTable[nameIndex],
                    parentIndex,
                    pivot,
                    rotation,
                    hidden,
                    elements
            );
        }

        // Read meshes
        Map<String, StrataMeshData> meshMap = new HashMap<>();
        MeshElement[] meshElements = new MeshElement[meshCount];

        for (int i = 0; i < meshCount; i++) {
            int nameIndex = reader.readUInt16();
            int textureIndex = reader.readUInt16();

            Vector3f origin = new Vector3f(
                    reader.readFloat32(),
                    reader.readFloat32(),
                    reader.readFloat32()
            );

            // Rotation is in degrees, convert to radians
            Vector3f rotation = new Vector3f(
                    (float) Math.toRadians(reader.readFloat32()),
                    (float) Math.toRadians(reader.readFloat32()),
                    (float) Math.toRadians(reader.readFloat32())
            );

            int meshFlags = reader.readUInt8();
            boolean hidden = (meshFlags & 1) != 0;
            boolean smooth = (meshFlags & 2) != 0;

            int vertexCount = reader.readInt32();
            int faceCount = reader.readInt32();

            // Read vertices
            Map<String, Vector3f> vertices = new HashMap<>();
            for (int j = 0; j < vertexCount; j++) {
                Vector3f vertex = new Vector3f(
                        reader.readFloat32(),
                        reader.readFloat32(),
                        reader.readFloat32()
                );
                vertices.put("v" + j, vertex);
            }

            // Read faces
            Map<String, StrataMeshData.Face> faces = new HashMap<>();
            for (int j = 0; j < faceCount; j++) {
                int faceVertCount = reader.readUInt8();
                List<String> vertexIds = new ArrayList<>();
                Map<String, float[]> uvs = new HashMap<>();

                for (int k = 0; k < faceVertCount; k++) {
                    int vIndex = reader.readInt32();
                    float u = reader.readFloat32();
                    float v = reader.readFloat32();

                    String vertexId = "v" + vIndex;
                    vertexIds.add(vertexId);
                    uvs.put(vertexId, new float[]{u, v});
                }

                faces.put("f" + j, new StrataMeshData.Face(vertexIds, uvs));
            }

            String meshName = stringTable[nameIndex];
            String textureName = textureNames[textureIndex];

            StrataMeshData.Mesh meshData = new StrataMeshData.Mesh(smooth, vertices, faces);
            StrataMeshData strataMesh = new StrataMeshData(
                    "blockbench_mesh",
                    textureName,
                    origin,
                    rotation,
                    meshData,
                    null
            );

            meshMap.put(meshName, strataMesh);
            meshElements[i] = new MeshElement(meshName, strataMesh);
        }

        // Read cuboids
        CuboidElement[] cuboidElements = new CuboidElement[cuboidCount];

        for (int i = 0; i < cuboidCount; i++) {
            int nameIndex = reader.readUInt16();
            int textureIndex = reader.readUInt16();

            Vector3f origin = new Vector3f(
                    reader.readFloat32(),
                    reader.readFloat32(),
                    reader.readFloat32()
            );

            // Rotation is in degrees, convert to radians
            Vector3f rotation = new Vector3f(
                    (float) Math.toRadians(reader.readFloat32()),
                    (float) Math.toRadians(reader.readFloat32()),
                    (float) Math.toRadians(reader.readFloat32())
            );

            Vector3f from = new Vector3f(
                    reader.readFloat32(),
                    reader.readFloat32(),
                    reader.readFloat32()
            );

            Vector3f to = new Vector3f(
                    reader.readFloat32(),
                    reader.readFloat32(),
                    reader.readFloat32()
            );

            float inflate = reader.readFloat32();
            int cuboidFlags = reader.readUInt8();
            boolean hidden = (cuboidFlags & 1) != 0;

            int faceCount = reader.readUInt8();
            Map<String, StrataMeshData.CuboidFace> faces = new HashMap<>();

            String[] faceNames = {"north", "south", "east", "west", "up", "down"};

            for (int j = 0; j < faceCount; j++) {
                int faceId = reader.readUInt8();
                float u1 = reader.readFloat32();
                float v1 = reader.readFloat32();
                float u2 = reader.readFloat32();
                float v2 = reader.readFloat32();
                int faceRotation = reader.readUInt8();

                if (faceId < faceNames.length) {
                    faces.put(faceNames[faceId], new StrataMeshData.CuboidFace(
                            new float[]{u1, v1, u2, v2},
                            faceRotation
                    ));
                }
            }

            String cuboidName = stringTable[nameIndex];
            String textureName = textureNames[textureIndex];

            StrataMeshData.Cuboid cuboidData = new StrataMeshData.Cuboid(from, to, inflate, faces);
            StrataMeshData strataCuboid = new StrataMeshData(
                    "blockbench_cuboid",
                    textureName,
                    origin,
                    rotation,
                    null,
                    cuboidData
            );

            meshMap.put(cuboidName, strataCuboid);
            cuboidElements[i] = new CuboidElement(cuboidName, strataCuboid);
        }

        // Build bone hierarchy
        StrataBone[] strataBones = new StrataBone[boneCount];

        for (int i = 0; i < boneCount; i++) {
            BoneData boneData = bones[i];

            // Collect mesh IDs for this bone
            List<String> meshIds = new ArrayList<>();
            for (ElementRef ref : boneData.elements) {
                if (ref.type == 0) {
                    // Mesh
                    meshIds.add(meshElements[ref.index].name);
                } else {
                    // Cuboid
                    meshIds.add(cuboidElements[ref.index].name);
                }
            }

            strataBones[i] = new StrataBone(
                    boneData.name,
                    null, // Parent will be set in next pass
                    boneData.pivot,
                    boneData.rotation,
                    meshIds,
                    boneData.hidden
            );
        }

        // Set parent relationships
        StrataBone rootBone = null;

        for (int i = 0; i < boneCount; i++) {
            BoneData boneData = bones[i];
            if (boneData.parentIndex >= 0 && boneData.parentIndex < boneCount) {
                strataBones[boneData.parentIndex].addChild(strataBones[i]);
            } else {
                // This is a root bone
                rootBone = strataBones[i];
            }
        }

        if (rootBone == null && boneCount > 0) {
            rootBone = strataBones[0];
        }

        if (rootBone == null) {
            LOGGER.error("No root bone found in binary model: {}", id);
            return createFallbackModel(id);
        }

        return new StrataModel(id, boundingBox, rootBone, textureMap, meshMap);
    }

    /**
     * Loads a model from JSON format (original implementation).
     */
    private static StrataModel loadJson(Identifier id, String json) {
        try {
            JsonObject root = GSON.fromJson(json, JsonObject.class);

            // Parse the Textures Map
            Map<String, StrataModel.TextureInfo> textureMap = new HashMap<>();
            JsonObject texObj = root.getAsJsonObject("textures");
            for (Map.Entry<String, JsonElement> entry : texObj.entrySet()) {
                JsonObject info = entry.getValue().getAsJsonObject();
                textureMap.put(entry.getKey(), new StrataModel.TextureInfo(
                        info.get("uv_width").getAsInt(),
                        info.get("uv_height").getAsInt()
                ));
            }

            // Parse meshes
            Map<String, StrataMeshData> meshes = new HashMap<>();
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
            Map<String, StrataBone> boneMap = new HashMap<>();
            StrataBone rootBone = null;

            // First pass: create all bones
            for (JsonElement elem : bonesArray) {
                JsonObject boneObj = elem.getAsJsonObject();
                String name = boneObj.get("name").getAsString();

                Vector3f pivot = parseVector3f(boneObj.getAsJsonArray("pivot"));
                Vector3f rotation = boneObj.has("rotation") ? parseVector3fToRadians(boneObj.getAsJsonArray("rotation")) : new Vector3f();

                List<String> meshIds = new ArrayList<>();
                JsonArray meshesArray = boneObj.getAsJsonArray("meshes");
                if (meshesArray != null) {
                    for (JsonElement meshElem : meshesArray) {
                        meshIds.add(meshElem.getAsString());
                    }
                }

                // Parse hidden field (defaults to false if not present)
                boolean hidden = boneObj.has("hidden");

                StrataBone bone = new StrataBone(name, null, pivot, rotation, meshIds, hidden);
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
                StrataBone bone = boneMap.get(name);

                if (!boneObj.get("parent").isJsonNull()) {
                    String parentName = boneObj.get("parent").getAsString();
                    StrataBone parent = boneMap.get(parentName);
                    if (parent != null) {
                        parent.addChild(bone);
                    }
                }
            }

            if (rootBone == null) {
                LOGGER.error("No root bone found in model: {}", id);
                return createFallbackModel(id);
            }

            // Parse bounding box
            AABB boundingBox = parseBoundingBox(root);

            return new StrataModel(id, boundingBox, rootBone, textureMap, meshes);

        } catch (Exception e) {
            LOGGER.error("Error loading JSON model {}: {}", id, e.getMessage());
            e.printStackTrace();
            return createFallbackModel(id);
        }
    }

    /**
     * Parses a mesh or cuboid from JSON.
     */
    private static StrataMeshData parseMesh(JsonObject meshObj) {
        String type = meshObj.get("type").getAsString();
        String textureSlot = meshObj.get("texture").getAsString();
        Vector3f origin = parseVector3f(meshObj.getAsJsonArray("origin"));
        Vector3f rotation = meshObj.has("rotation") ? parseVector3fToRadians(meshObj.getAsJsonArray("rotation")) : new Vector3f();

        if ("blockbench_cuboid".equals(type)) {
            // 1. Parse Cuboid Geometry
            Vector3f from = parseVector3f(meshObj.getAsJsonArray("from"));
            Vector3f to = parseVector3f(meshObj.getAsJsonArray("to"));
            float inflate = meshObj.has("inflate") ? meshObj.get("inflate").getAsFloat() : 0;

            // 2. Parse Cuboid Faces
            Map<String, StrataMeshData.CuboidFace> cuboidFaces = new HashMap<>();
            JsonObject facesObj = meshObj.getAsJsonObject("faces");
            if (facesObj != null) {
                for (Map.Entry<String, JsonElement> entry : facesObj.entrySet()) {
                    JsonObject faceObj = entry.getValue().getAsJsonObject();
                    float[] uv = parseRawFloatArray(faceObj.getAsJsonArray("uv"));
                    int faceRotation = faceObj.has("face_rotation") ? faceObj.get("face_rotation").getAsInt() : 0;

                    cuboidFaces.put(entry.getKey(), new StrataMeshData.CuboidFace(uv, faceRotation));
                }
            }

            StrataMeshData.Cuboid cuboidData = new StrataMeshData.Cuboid(from, to, inflate, cuboidFaces);
            return new StrataMeshData(type, textureSlot, origin, rotation, null, cuboidData);

        } else if("blockbench_mesh".equals(type)) {
            // Original "blockbench_mesh" parsing logic

            boolean shadeSmooth = meshObj.has("shade_smooth");

            Map<String, Vector3f> vertices = new HashMap<>();
            JsonObject verticesObj = meshObj.getAsJsonObject("vertices");
            if (verticesObj != null) {
                for (Map.Entry<String, JsonElement> entry : verticesObj.entrySet()) {
                    vertices.put(entry.getKey(), parseVector3f(entry.getValue().getAsJsonArray()));
                }
            }

            Map<String, StrataMeshData.Face> meshFaces = new HashMap<>();
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
                    meshFaces.put(entry.getKey(), new StrataMeshData.Face(vertexIds, uvs));
                }
            }

            StrataMeshData.Mesh meshData = new StrataMeshData.Mesh(shadeSmooth, vertices, meshFaces);
            return new StrataMeshData(type, textureSlot, origin, rotation, meshData, null);
        }
        LOGGER.error("Mesh Type is not blockbench_mesh or blockbench_cuboid");
        return null;
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
     * Parses a Vector3f from a JSON array and converts it to radians
     */
    private static Vector3f parseVector3fToRadians(JsonArray array) {
        if (array == null || array.size() != 3) {
            return new Vector3f(0, 0, 0);
        }
        return new Vector3f(
                (float) Math.toRadians(array.get(0).getAsFloat()),
                (float) Math.toRadians(array.get(1).getAsFloat()),
                (float) Math.toRadians(array.get(2).getAsFloat())
        );
    }

    /**
     * Parses the bounding box from the JSON root object.
     * Returns a default box if not present or invalid.
     */
    private static AABB parseBoundingBox(JsonObject root) {
        if (!root.has("bounding_box")) {
            LOGGER.warn("Model missing bounding_box, using default");
            return new AABB(-8, 0, -8, 8, 16, 8);
        }

        try {
            JsonObject bbox = root.getAsJsonObject("bounding_box");
            JsonArray minArray = bbox.getAsJsonArray("min");
            JsonArray maxArray = bbox.getAsJsonArray("max");

            double minX = minArray.get(0).getAsDouble();
            double minY = minArray.get(1).getAsDouble();
            double minZ = minArray.get(2).getAsDouble();
            double maxX = maxArray.get(0).getAsDouble();
            double maxY = maxArray.get(1).getAsDouble();
            double maxZ = maxArray.get(2).getAsDouble();

            return new AABB(minX, minY, minZ, maxX, maxY, maxZ);
        } catch (Exception e) {
            LOGGER.error("Failed to parse bounding_box: {}", e.getMessage());
            return new AABB(-8, 0, -8, 8, 16, 8);
        }
    }

    /**
     * Creates a simple fallback cube model when loading fails.
     */
    private static StrataModel createFallbackModel(Identifier id) {
        LOGGER.warn("Creating fallback model for: {}", id);

        Map<String, StrataModel.TextureInfo> textureMap = new HashMap<>();
        textureMap.put("untextured", new StrataModel.TextureInfo(16, 16));

        // 1. Define the geometric boundaries (Standard 1x1x1 block size is -8 to 8 in Blockbench)
        Vector3f from = new Vector3f(-8, -8, -8);
        Vector3f to = new Vector3f(8, 8, 8);

        // 2. Define faces with default UVs [u1, v1, u2, v2]
        Map<String, StrataMeshData.CuboidFace> faces = new HashMap<>();
        float[] defaultUv = new float[]{0, 0, 16, 16};

        faces.put("north", new StrataMeshData.CuboidFace(defaultUv, 0));
        faces.put("south", new StrataMeshData.CuboidFace(defaultUv, 0));
        faces.put("west",  new StrataMeshData.CuboidFace(defaultUv, 0));
        faces.put("up",    new StrataMeshData.CuboidFace(defaultUv, 0));
        faces.put("down",  new StrataMeshData.CuboidFace(defaultUv, 0));

        // 3. Create the Cuboid data container
        StrataMeshData.Cuboid cuboidData = new StrataMeshData.Cuboid(from, to, 0, faces);

        // 4. Create the MeshData using the new two-variable constructor
        Map<String, StrataMeshData> meshes = new HashMap<>();
        meshes.put("fallback_cuboid", new StrataMeshData(
                "blockbench_cuboid", "main", new Vector3f(), new Vector3f(), null, cuboidData
        ));

        // 5. Build the bone hierarchy (with identity quaternion rotation)
        StrataBone root = new StrataBone(
                "root", null, new Vector3f(0, 0, 0),
                new Vector3f(), // Identity rotation
                Collections.singletonList("fallback_cuboid")
        );

        // Create a default bounding box for the fallback cube
        AABB boundingBox = new AABB(-8, -8, -8, 8, 8, 8);

        return new StrataModel(id, boundingBox, root, textureMap, meshes);
    }

    // --------------------------
    // BINARY READER HELPER CLASS
    // --------------------------

    private static class BinaryReader {
        private final ByteBuffer buffer;

        public BinaryReader(byte[] data) {
            this.buffer = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN);
        }

        public int readUInt8() {
            return buffer.get() & 0xFF;
        }

        public int readInt16() {
            return buffer.getShort();
        }

        public int readUInt16() {
            return buffer.getShort() & 0xFFFF;
        }

        public int readInt32() {
            return buffer.getInt();
        }

        public float readFloat32() {
            return buffer.getFloat();
        }

        public String readString() {
            int length = readUInt16();
            byte[] bytes = new byte[length];
            buffer.get(bytes);
            return new String(bytes, StandardCharsets.UTF_8);
        }

        public String readFixedString(int length) {
            byte[] bytes = new byte[length];
            buffer.get(bytes);
            // Find null terminator
            int end = 0;
            while (end < bytes.length && bytes[end] != 0) end++;
            return new String(bytes, 0, end, StandardCharsets.UTF_8);
        }

        public void skip(int bytes) {
            buffer.position(buffer.position() + bytes);
        }
    }

    // --------------------------------------
    // HELPER DATA CLASSES FOR BINARY PARSING
    // --------------------------------------
    private static class BoneData {
        final String name;
        final int parentIndex;
        final Vector3f pivot;
        final Vector3f rotation;
        final boolean hidden;
        final List<ElementRef> elements;

        BoneData(String name, int parentIndex, Vector3f pivot, Vector3f rotation,
                 boolean hidden, List<ElementRef> elements) {
            this.name = name;
            this.parentIndex = parentIndex;
            this.pivot = pivot;
            this.rotation = rotation;
            this.hidden = hidden;
            this.elements = elements;
        }
    }

    private static class ElementRef {
        final int type; // 0 = mesh, 1 = cuboid
        final int index;

        ElementRef(int type, int index) {
            this.type = type;
            this.index = index;
        }
    }

    private static class MeshElement {
        final String name;
        final StrataMeshData data;

        MeshElement(String name, StrataMeshData data) {
            this.name = name;
            this.data = data;
        }
    }

    private static class CuboidElement {
        final String name;
        final StrataMeshData data;

        CuboidElement(String name, StrataMeshData data) {
            this.name = name;
            this.data = data;
        }
    }
}