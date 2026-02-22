package engine.strata.world.block.model;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import engine.strata.core.io.ResourceManager;
import engine.strata.util.Identifier;
import org.joml.Vector3f;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;

/**
 * Loads, caches, and resolves block models from JSON assets.
 *
 * <h3>Load priority</h3>
 * For every identifier the loader first tries to read the corresponding JSON
 * file from the asset pack ({@code assets/{namespace}/models/block/{path}.json}).
 * Only when that file does not exist does it fall back to the code-generated
 * built-in models.  This means your own {@code cube.json}, {@code cube_all.json},
 * etc. always take precedence over anything hard-coded here.
 *
 * <h3>Asset path convention</h3>
 * Model identifier {@code strata:cube_all} maps to:
 * {@code assets/strata/models/block/cube_all.json}
 *
 * <h3>Parent reference path stripping</h3>
 * {@code block/}, {@code blocks/}, and {@code item/} directory prefixes are
 * stripped from parent references so {@code "parent": "strata:cube"} and
 * {@code "parent": "strata:block/cube"} both resolve identically.
 *
 * <h3>Built-in fallbacks</h3>
 * Code-generated models are used only when no JSON file is found:
 * <ul>
 *   <li>{@code strata:cube}            — six individually-named face variables</li>
 *   <li>{@code strata:cube_all}        — all faces use {@code #all}</li>
 *   <li>{@code strata:cube_bottom_top} — top/bottom distinct, sides share {@code #side}</li>
 *   <li>{@code strata:air}             — no elements</li>
 * </ul>
 */
public class BlockModelLoader {

    private static final Logger LOGGER = LoggerFactory.getLogger("BlockModelLoader");

    /** Raw (unresolved) models keyed by identifier. */
    private final Map<Identifier, BlockModel> modelCache    = new HashMap<>();

    /** Fully resolved models (ready for rendering). */
    private final Map<Identifier, BlockModel> resolvedCache = new HashMap<>();

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Returns the resolved model for the given identifier.
     * Loads + resolves on first call; subsequent calls return the cached result.
     */
    public BlockModel loadModel(Identifier modelId) {
        BlockModel cached = resolvedCache.get(modelId);
        if (cached != null) return cached;

        if (!modelCache.containsKey(modelId)) {
            loadRaw(modelId);
        }

        return resolveModel(modelId);
    }

    public void clearCache() {
        modelCache.clear();
        resolvedCache.clear();
        LOGGER.info("Cleared block model cache");
    }

    public int getCachedModelCount() { return resolvedCache.size(); }

    // ── Raw loading ───────────────────────────────────────────────────────────

    /**
     * Loads and caches the raw (unresolved) model.
     *
     * <p><b>Priority: disk JSON → built-in code model → missing fallback.</b>
     *
     * <p>The disk file is always attempted first so that asset-pack JSON files
     * (including the archetype models like {@code cube.json}) always win over
     * anything hard-coded in this class.
     */
    private void loadRaw(Identifier modelId) {
        // 1. Try loading from the asset pack on disk.
        try (InputStream is = ResourceManager.getResourceStream(modelId, "models/block", "json")) {
            if (is != null) {
                JsonObject json = JsonParser.parseReader(new InputStreamReader(is)).getAsJsonObject();
                BlockModel model = parseModel(modelId, json);
                modelCache.put(modelId, model);
                LOGGER.debug("Loaded model from disk: {}", modelId);
                return;
            }
        } catch (Exception e) {
            LOGGER.error("Failed to parse model JSON for {}", modelId, e);
            // Fall through to built-in / missing fallbacks below.
        }

        // 2. No file found — try a code-generated built-in.
        BlockModel builtin = getBuiltInModel(modelId);
        if (builtin != null) {
            LOGGER.debug("Using built-in model for: {}", modelId);
            modelCache.put(modelId, builtin);
            return;
        }

        // 3. Nothing found at all — warn and store the missing-model cube.
        LOGGER.warn("Model not found: {} — using missing-model fallback", modelId);
        modelCache.put(modelId, makeMissingModel(modelId));
    }

    // ── Parsing ───────────────────────────────────────────────────────────────

    private BlockModel parseModel(Identifier id, JsonObject json) {
        BlockModel.Builder builder = BlockModel.builder().id(id);

        if (json.has("parent")) {
            builder.parent(parseModelRef(json.get("parent").getAsString()));
        }

        if (json.has("textures")) {
            JsonObject textures = json.getAsJsonObject("textures");
            for (Map.Entry<String, JsonElement> entry : textures.entrySet()) {
                builder.textureRaw(entry.getKey(), entry.getValue().getAsString());
            }
        }

        if (json.has("elements")) {
            for (JsonElement elem : json.getAsJsonArray("elements")) {
                builder.addElement(parseElement(elem.getAsJsonObject()));
            }
        }

        return builder.build();
    }

    private BlockModel.Element parseElement(JsonObject json) {
        Vector3f from = parseVec3(json.getAsJsonArray("from"));
        Vector3f to   = parseVec3(json.getAsJsonArray("to"));

        Map<BlockModel.Face, BlockModel.FaceData> faces = new EnumMap<>(BlockModel.Face.class);
        if (json.has("faces")) {
            JsonObject facesJson = json.getAsJsonObject("faces");
            for (Map.Entry<String, JsonElement> entry : facesJson.entrySet()) {
                BlockModel.Face face = BlockModel.Face.fromString(entry.getKey());
                if (face != null) {
                    faces.put(face, parseFace(entry.getValue().getAsJsonObject()));
                }
            }
        }
        return new BlockModel.Element(from, to, faces);
    }

    private BlockModel.FaceData parseFace(JsonObject json) {
        String textureVar = json.has("texture") ? json.get("texture").getAsString() : "#missing";

        BlockModel.Face cullface = null;
        if (json.has("cullface"))
            cullface = BlockModel.Face.fromString(json.get("cullface").getAsString());

        int rotation = json.has("rotation") ? json.get("rotation").getAsInt() : 0;

        float[] uv = null;
        if (json.has("uv")) {
            JsonArray a = json.getAsJsonArray("uv");
            uv = new float[]{ a.get(0).getAsFloat(), a.get(1).getAsFloat(),
                    a.get(2).getAsFloat(), a.get(3).getAsFloat() };
        }

        return new BlockModel.FaceData(textureVar, null, cullface, rotation, uv);
    }

    private Vector3f parseVec3(JsonArray a) {
        return new Vector3f(a.get(0).getAsFloat(), a.get(1).getAsFloat(), a.get(2).getAsFloat());
    }

    /**
     * Parses a model reference from JSON into a normalised {@link Identifier}.
     * Strips leading {@code block/}, {@code blocks/}, or {@code item/} from the
     * path component so all common formats resolve to the same identifier.
     */
    private Identifier parseModelRef(String ref) {
        if (ref.contains(":")) {
            String[] parts = ref.split(":", 2);
            return Identifier.of(parts[0], stripModelDirPrefix(parts[1]));
        }
        return Identifier.ofEngine(stripModelDirPrefix(ref));
    }

    private static String stripModelDirPrefix(String path) {
        if (path.startsWith("block/"))  return path.substring(6);
        if (path.startsWith("blocks/")) return path.substring(7);
        if (path.startsWith("item/"))   return path.substring(5);
        return path;
    }

    // ── Resolution ────────────────────────────────────────────────────────────

    private BlockModel resolveModel(Identifier modelId) {
        BlockModel model = modelCache.get(modelId);
        if (model == null) {
            return makeMissingModel(modelId);
        }

        // Merge the full raw parent chain first (without resolving intermediates),
        // then resolve once at the end.  This preserves alias maps (e.g. up->#top)
        // that would otherwise be discarded when an intermediate parent is resolved
        // in isolation before the child's concrete textures have been merged in.
        BlockModel fullyMerged = getRawMerged(modelId);
        BlockModel resolved = fullyMerged.resolveTextures();

        resolvedCache.put(modelId, resolved);
        return resolved;
    }

    /**
     * Returns a raw (unresolved) model that has been merged with its full
     * ancestor chain — child values always win.  Never puts anything in
     * {@link #resolvedCache}; only {@link #modelCache} is used/populated.
     */
    private BlockModel getRawMerged(Identifier modelId) {
        if (!modelCache.containsKey(modelId)) {
            loadRaw(modelId);
        }
        BlockModel model = modelCache.get(modelId);
        if (model == null) return makeMissingModel(modelId);

        if (!model.hasParent()) {
            return model;
        }

        BlockModel rawParent = getRawMerged(model.getParentId());
        return model.mergeWithParent(rawParent);
    }

    // ── Built-in fallback models ──────────────────────────────────────────────

    /**
     * Returns a code-generated model for the given identifier, or {@code null}
     * if no built-in exists for it.
     *
     * These are used only when no JSON file was found on disk.
     */
    private BlockModel getBuiltInModel(Identifier id) {
        if (!id.namespace.equals("strata")) return null;

        switch (id.path) {
            case "cube":            return makeCube(id);
            case "cube_all":        return makeCubeAll(id);
            case "cube_bottom_top": return makeCubeBottomTop(id);
            case "air":             return makeAir(id);
            default:                return null;
        }
    }

    private BlockModel makeCube(Identifier id) {
        Map<BlockModel.Face, BlockModel.FaceData> faces = new EnumMap<>(BlockModel.Face.class);
        faces.put(BlockModel.Face.UP,    face("#up",    BlockModel.Face.UP));
        faces.put(BlockModel.Face.DOWN,  face("#down",  BlockModel.Face.DOWN));
        faces.put(BlockModel.Face.NORTH, face("#north", BlockModel.Face.NORTH));
        faces.put(BlockModel.Face.SOUTH, face("#south", BlockModel.Face.SOUTH));
        faces.put(BlockModel.Face.WEST,  face("#west",  BlockModel.Face.WEST));
        faces.put(BlockModel.Face.EAST,  face("#east",  BlockModel.Face.EAST));
        return BlockModel.builder()
                .id(id)
                .addElement(new BlockModel.Element(new Vector3f(0,0,0), new Vector3f(16,16,16), faces))
                .build();
    }

    private BlockModel makeCubeAll(Identifier id) {
        return BlockModel.builder()
                .id(id)
                .addElement(new BlockModel.Element(
                        new Vector3f(0,0,0), new Vector3f(16,16,16), allFaces("#all")))
                .textureAlias("particle", "#all")
                .build();
    }

    private BlockModel makeCubeBottomTop(Identifier id) {
        Map<BlockModel.Face, BlockModel.FaceData> faces = new EnumMap<>(BlockModel.Face.class);
        faces.put(BlockModel.Face.UP,    face("#top",    BlockModel.Face.UP));
        faces.put(BlockModel.Face.DOWN,  face("#bottom", BlockModel.Face.DOWN));
        faces.put(BlockModel.Face.NORTH, face("#side",   BlockModel.Face.NORTH));
        faces.put(BlockModel.Face.SOUTH, face("#side",   BlockModel.Face.SOUTH));
        faces.put(BlockModel.Face.WEST,  face("#side",   BlockModel.Face.WEST));
        faces.put(BlockModel.Face.EAST,  face("#side",   BlockModel.Face.EAST));
        return BlockModel.builder()
                .id(id)
                .addElement(new BlockModel.Element(new Vector3f(0,0,0), new Vector3f(16,16,16), faces))
                .textureAlias("particle", "#side")
                .build();
    }

    private BlockModel makeAir(Identifier id) {
        return BlockModel.builder().id(id).build();
    }

    private BlockModel makeMissingModel(Identifier id) {
        Map<BlockModel.Face, BlockModel.FaceData> faces = new EnumMap<>(BlockModel.Face.class);
        for (BlockModel.Face f : BlockModel.Face.values()) {
            faces.put(f, new BlockModel.FaceData(null, Identifier.ofEngine("missing"), f, 0, null));
        }
        Map<String, Identifier> textures = new HashMap<>();
        textures.put("all", Identifier.ofEngine("missing"));
        return new BlockModel(id, null,
                java.util.Collections.singletonList(
                        new BlockModel.Element(new Vector3f(0,0,0), new Vector3f(16,16,16), faces)),
                textures, java.util.Collections.emptyMap(), true);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static BlockModel.FaceData face(String var, BlockModel.Face cullface) {
        return new BlockModel.FaceData(var, null, cullface, 0, null);
    }

    private static Map<BlockModel.Face, BlockModel.FaceData> allFaces(String var) {
        Map<BlockModel.Face, BlockModel.FaceData> map = new EnumMap<>(BlockModel.Face.class);
        for (BlockModel.Face f : BlockModel.Face.values()) map.put(f, face(var, f));
        return map;
    }
}