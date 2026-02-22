package engine.strata.world.block.model;

import engine.strata.util.Identifier;
import org.joml.Vector3f;

import java.util.*;

/**
 * Represents a block model: geometry (cuboid elements) plus resolved textures.
 *
 * <h3>Texture variable system</h3>
 * Model JSON can contain two kinds of texture entries:
 * <ol>
 *   <li><b>Direct</b>: {@code "all": "strata:block/stone"} — maps a variable
 *       name to a real texture {@link Identifier}.</li>
 *   <li><b>Alias</b>: {@code "particle": "#all"} — maps a variable name to
 *       another variable, resolved transitively.</li>
 * </ol>
 *
 * Face texture references use the form {@code "#varname"}.  After full
 * resolution, every {@link FaceData#getTexture()} returns a concrete
 * {@link Identifier} (or the missing-texture fallback is used).
 *
 * <h3>Inheritance</h3>
 * {@link #mergeWithParent(BlockModel)} applies parent → child override order.
 * {@link #resolveTextures()} collapses the alias chain and fills in all face
 * textures, producing an {@link #isResolved()} model ready for rendering.
 */
public class BlockModel {

    private final Identifier id;
    private final Identifier parentId;
    private final List<Element> elements;

    /**
     * Direct variable→texture mappings (e.g. {@code "all" → strata:block/stone}).
     */
    private final Map<String, Identifier> textures;

    /**
     * Variable→variable alias mappings (e.g. {@code "particle" → "#all"}).
     * Kept separate so we can do a clean chain-resolution without
     * confusing aliases with real identifiers.
     */
    private final Map<String, String> textureAliases;

    private final boolean isResolved;

    // ── Constructor ───────────────────────────────────────────────────────────

    public BlockModel(Identifier id, Identifier parentId,
                      List<Element> elements,
                      Map<String, Identifier> textures,
                      Map<String, String> textureAliases,
                      boolean isResolved) {
        this.id             = id;
        this.parentId       = parentId;
        this.elements       = elements;
        this.textures       = textures;
        this.textureAliases = textureAliases;
        this.isResolved     = isResolved;
    }

    // ── Resolution ────────────────────────────────────────────────────────────

    /**
     * Resolves all texture variable chains and bakes the result into each
     * {@link FaceData}.  Returns a new, fully-resolved {@link BlockModel}.
     *
     * Call this after {@link #mergeWithParent(BlockModel)} so the full
     * variable scope is available.
     */
    public BlockModel resolveTextures() {
        // Build the full resolved map: follow alias chains to real identifiers.
        Map<String, Identifier> resolved = new HashMap<>(textures);

        for (String key : textureAliases.keySet()) {
            Identifier tex = resolveAlias(key, textures, textureAliases, 0);
            if (tex != null) resolved.put(key, tex);
        }

        // Resolve each element's face textures.
        List<Element> resolvedElements = new ArrayList<>();
        for (Element element : elements) {
            resolvedElements.add(element.resolveTextures(resolved));
        }

        return new BlockModel(id, null, resolvedElements, resolved,
                Collections.emptyMap(), true);
    }

    /**
     * Follows alias chains to produce a concrete texture identifier.
     * Returns null if the chain is broken (unresolvable variable).
     */
    private static Identifier resolveAlias(String key,
                                           Map<String, Identifier> direct,
                                           Map<String, String> aliases,
                                           int depth) {
        if (depth > 16) return null; // prevent infinite loops

        Identifier tex = direct.get(key);
        if (tex != null) return tex;

        String ref = aliases.get(key);
        if (ref == null) return null;

        // Strip leading '#' if present
        String nextKey = ref.startsWith("#") ? ref.substring(1) : ref;
        return resolveAlias(nextKey, direct, aliases, depth + 1);
    }

    /**
     * Merges this model with its parent: this model's values take priority.
     * Elements: use parent's if we have none.
     * Textures: parent provides defaults, child overrides.
     */
    public BlockModel mergeWithParent(BlockModel parent) {
        List<Element> mergedElements = elements.isEmpty() ? parent.elements : elements;

        Map<String, Identifier> mergedTextures = new HashMap<>(parent.textures);
        mergedTextures.putAll(this.textures);

        Map<String, String> mergedAliases = new HashMap<>(parent.textureAliases);
        mergedAliases.putAll(this.textureAliases);

        return new BlockModel(id, null, mergedElements, mergedTextures,
                mergedAliases, false);
    }

    // ── Getters ───────────────────────────────────────────────────────────────

    public Identifier            getId()             { return id; }
    public Identifier            getParentId()       { return parentId; }
    public List<Element>         getElements()       { return elements; }
    public Map<String, Identifier> getTextures()     { return textures; }
    public Map<String, String>   getTextureAliases() { return textureAliases; }
    public boolean               isResolved()        { return isResolved; }
    public boolean               hasParent()         { return parentId != null; }

    // ═════════════════════════════════════════════════════════════════════════
    // Element
    // ═════════════════════════════════════════════════════════════════════════

    /**
     * A single cuboid element defined by two corners in 0–16 block space.
     */
    public static class Element {

        /** Minimum corner in 0–16 block space. */
        private final Vector3f from;

        /** Maximum corner in 0–16 block space. */
        private final Vector3f to;

        private final Map<Face, FaceData> faces;

        public Element(Vector3f from, Vector3f to, Map<Face, FaceData> faces) {
            this.from  = from;
            this.to    = to;
            this.faces = faces;
        }

        public Element resolveTextures(Map<String, Identifier> textures) {
            Map<Face, FaceData> resolvedFaces = new EnumMap<>(Face.class);
            for (Map.Entry<Face, FaceData> entry : faces.entrySet()) {
                resolvedFaces.put(entry.getKey(),
                        entry.getValue().resolveTexture(textures));
            }
            return new Element(from, to, resolvedFaces);
        }

        public Vector3f              getFrom()           { return from; }
        public Vector3f              getTo()             { return to; }
        public Map<Face, FaceData>   getFaces()          { return faces; }
        public FaceData              getFace(Face face)  { return faces.get(face); }
        public boolean               hasFace(Face face)  { return faces.containsKey(face); }

        // ── Normalized helpers (0→1 range, used by ChunkMeshBuilder) ─────────

        /** from.x / 16 */
        public float fx() { return from.x / 16f; }
        /** from.y / 16 */
        public float fy() { return from.y / 16f; }
        /** from.z / 16 */
        public float fz() { return from.z / 16f; }
        /** to.x / 16 */
        public float tx() { return to.x / 16f; }
        /** to.y / 16 */
        public float ty() { return to.y / 16f; }
        /** to.z / 16 */
        public float tz() { return to.z / 16f; }
    }

    // ═════════════════════════════════════════════════════════════════════════
    // FaceData
    // ═════════════════════════════════════════════════════════════════════════

    /**
     * Per-face rendering data: texture reference, culling direction, UV, rotation.
     */
    public static class FaceData {

        /** Unresolved variable name from JSON, e.g. {@code "#all"}. Null once resolved. */
        private final String textureVariable;

        /** Resolved texture identifier. Null until resolved. */
        private final Identifier texture;

        /** Direction of the adjacent block that, if opaque, hides this face. Null = never cull. */
        private final Face cullface;

        /** Rotation in 90° steps (0, 90, 180, 270). */
        private final int rotation;

        /**
         * Custom UV in 0–16 space: [u1, v1, u2, v2].
         * Null = auto-generate from element bounds.
         */
        private final float[] uv;

        public FaceData(String textureVariable, Identifier texture,
                        Face cullface, int rotation, float[] uv) {
            this.textureVariable = textureVariable;
            this.texture         = texture;
            this.cullface        = cullface;
            this.rotation        = rotation;
            this.uv              = uv;
        }

        public FaceData resolveTexture(Map<String, Identifier> textures) {
            if (texture != null) return this; // already resolved

            if (textureVariable == null) return this;

            String varName = textureVariable.startsWith("#")
                    ? textureVariable.substring(1)
                    : textureVariable;

            Identifier resolved = textures.get(varName);
            if (resolved == null) return this; // keep variable for debug visibility

            return new FaceData(null, resolved, cullface, rotation, uv);
        }

        /** UV in normalised 0→1 space for the given element, auto-generated if not set. */
        public float[] getUvNormalized(Element element, Face face) {
            if (uv != null) {
                // Custom UV — already in 0-16 space, normalise.
                return new float[]{ uv[0]/16f, uv[1]/16f, uv[2]/16f, uv[3]/16f };
            }
            // Auto-generate from element bounds in 0-16 space.
            return autoUV(element, face);
        }

        private float[] autoUV(Element element, Face face) {
            float fx = element.getFrom().x, fy = element.getFrom().y, fz = element.getFrom().z;
            float tx = element.getTo().x,   ty = element.getTo().y,   tz = element.getTo().z;

            switch (face) {
                case DOWN:  return new float[]{ fx/16f, fz/16f, tx/16f, tz/16f };
                case UP:    return new float[]{ fx/16f, (16-tz)/16f, tx/16f, (16-fz)/16f };
                case NORTH: return new float[]{ (16-tx)/16f, (16-ty)/16f, (16-fx)/16f, (16-fy)/16f };
                case SOUTH: return new float[]{ fx/16f, (16-ty)/16f, tx/16f, (16-fy)/16f };
                case WEST:  return new float[]{ fz/16f, (16-ty)/16f, tz/16f, (16-fy)/16f };
                case EAST:  return new float[]{ (16-tz)/16f, (16-ty)/16f, (16-fz)/16f, (16-fy)/16f };
                default:    return new float[]{ 0, 0, 1, 1 };
            }
        }

        public String     getTextureVariable() { return textureVariable; }
        public Identifier getTexture()         { return texture; }
        public Face       getCullface()        { return cullface; }
        public boolean    shouldCull()         { return cullface != null; }
        public int        getRotation()        { return rotation; }
        /** Raw UV array in 0-16 space, or null for auto. */
        public float[]    getUv()              { return uv; }
        public boolean    hasCustomUV()        { return uv != null; }
        public boolean    isResolved()         { return texture != null; }
    }

    // ═════════════════════════════════════════════════════════════════════════
    // Face enum
    // ═════════════════════════════════════════════════════════════════════════

    public enum Face {
        DOWN ("down"),
        UP   ("up"),
        NORTH("north"),
        SOUTH("south"),
        WEST ("west"),
        EAST ("east");

        private final String jsonName;

        Face(String jsonName) { this.jsonName = jsonName; }

        public String getJsonName() { return jsonName; }

        public static Face fromString(String name) {
            for (Face f : values()) if (f.jsonName.equals(name)) return f;
            return null;
        }

        public Face getOpposite() {
            switch (this) {
                case DOWN:  return UP;
                case UP:    return DOWN;
                case NORTH: return SOUTH;
                case SOUTH: return NORTH;
                case WEST:  return EAST;
                case EAST:  return WEST;
                default:    return this;
            }
        }

        /** Local-space block offset for the direction this face points. */
        public int[] offset() {
            switch (this) {
                case DOWN:  return new int[]{ 0,-1, 0};
                case UP:    return new int[]{ 0, 1, 0};
                case NORTH: return new int[]{ 0, 0,-1};
                case SOUTH: return new int[]{ 0, 0, 1};
                case WEST:  return new int[]{-1, 0, 0};
                case EAST:  return new int[]{ 1, 0, 0};
                default:    return new int[]{ 0, 0, 0};
            }
        }
    }

    // ═════════════════════════════════════════════════════════════════════════
    // Builder
    // ═════════════════════════════════════════════════════════════════════════

    public static class Builder {

        private Identifier id;
        private Identifier parentId;
        private final List<Element>         elements       = new ArrayList<>();
        private final Map<String, Identifier> textures     = new HashMap<>();
        private final Map<String, String>   textureAliases = new HashMap<>();

        public Builder id(Identifier id)           { this.id       = id;       return this; }
        public Builder parent(Identifier parentId) { this.parentId = parentId; return this; }
        public Builder addElement(Element element) { elements.add(element);    return this; }

        /**
         * Adds a direct texture mapping (value is a real identifier string like
         * {@code "strata:block/stone"}).
         */
        public Builder texture(String name, Identifier textureId) {
            textures.put(name, textureId);
            return this;
        }

        /**
         * Adds a raw texture entry from JSON.
         * Automatically routes to {@link #texture} or {@link #textureAlias}
         * depending on whether the value starts with {@code "#"}.
         */
        public Builder textureRaw(String name, String raw) {
            if (raw.startsWith("#")) {
                textureAliases.put(name, raw);
            } else {
                textures.put(name, Identifier.of(raw));
            }
            return this;
        }

        /** Adds a variable alias (value is a "#varname" reference). */
        public Builder textureAlias(String name, String ref) {
            textureAliases.put(name, ref);
            return this;
        }

        public BlockModel build() {
            return new BlockModel(id, parentId, elements, textures, textureAliases, false);
        }
    }

    public static Builder builder() { return new Builder(); }
}