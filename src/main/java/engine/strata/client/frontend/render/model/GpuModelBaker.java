package engine.strata.client.frontend.render.model;

import engine.helios.rendering.GpuModelCache;
import engine.helios.rendering.vertex.VertexFormat;
import engine.strata.util.Identifier;
import org.joml.Vector3f;
import org.lwjgl.BufferUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Strata-side baker that converts {@link StrataMeshData} into GPU-ready buffers
 * and registers them with {@link GpuModelCache}.
 *
 * <h3>VBO layout</h3>
 * Uses {@link VertexFormat#POSITION_TEXTURE}: {@code x y z u v} (5 floats / vertex).
 * Colour tint is supplied as the {@code u_Tint} shader uniform at draw time.
 *
 * <h3>Vertex positions</h3>
 * Stored in mesh-local space (no origin/rotation baked in).  The renderer applies
 * those transforms at draw time via the {@code u_Model} uniform, which means the
 * same baked VBO works for animated bones without any re-upload.
 */
public final class GpuModelBaker {

    private static final Logger LOGGER = LoggerFactory.getLogger("GpuModelBaker");
    private static final GpuModelBaker INSTANCE = new GpuModelBaker();

    public static GpuModelBaker getInstance() { return INSTANCE; }
    private GpuModelBaker() {}

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Ensures every mesh referenced by {@code model} is uploaded to the GPU.
     * Safe to call every frame — already-baked meshes are skipped instantly.
     * Must be called on the render thread.
     */
    public void ensureBaked(StrataModel model) {
        Identifier modelId = model.getId();
        String     prefix  = cachePrefix(modelId);

        for (StrataBone bone : model.getAllBones().values()) {
            for (String meshId : bone.getMeshIds()) {
                String key = prefix + meshId;
                if (GpuModelCache.getInstance().contains(key)) continue;

                StrataMeshData meshData = model.getMesh(meshId);
                if (meshData == null) {
                    LOGGER.warn("GpuModelBaker: mesh '{}' referenced by bone '{}' not found in model '{}'",
                            meshId, bone.getName(), modelId);
                    continue;
                }

                bakeMesh(key, meshData, model.uScale(), model.vScale());
            }
        }
    }

    /** Frees all GPU resources for {@code model}. */
    public void evict(StrataModel model) {
        GpuModelCache.getInstance().disposeByPrefix(cachePrefix(model.getId()));
    }

    /** Frees all baked models. */
    public void evictAll() {
        GpuModelCache.getInstance().disposeAll();
    }

    /** Returns the cache key for a specific mesh (used by ModelRenderer for lookup). */
    public String meshKey(Identifier modelId, String meshId) {
        return cachePrefix(modelId) + meshId;
    }

    // ── Baking dispatch ───────────────────────────────────────────────────────

    private void bakeMesh(String key, StrataMeshData meshData, float uScale, float vScale) {
        if ("blockbench_cuboid".equals(meshData.type())) {
            bakeCuboid(key, meshData, uScale, vScale);
        } else {
            bakeTriMesh(key, meshData, uScale, vScale);
        }
    }

    // ── Cuboid baking ─────────────────────────────────────────────────────────

    /**
     * Bakes a BlockBench cuboid into two triangles per face.
     *
     * <p>Vertex positions are stored as raw corner coordinates (inflated).
     * The per-mesh origin + rotation pivot is applied at draw time by
     * {@code ModelRenderer.applyMeshLocalTransform()} — NOT baked here.
     */
    private void bakeCuboid(String key, StrataMeshData meshData, float uScale, float vScale) {
        StrataMeshData.Cuboid cuboid = meshData.cuboid();
        if (cuboid == null) {
            LOGGER.warn("GpuModelBaker: cuboid data is null for key '{}'", key);
            return;
        }

        Vector3f from    = new Vector3f(cuboid.from());
        Vector3f to      = new Vector3f(cuboid.to());
        float    inflate = cuboid.inflate();

        from.sub(inflate, inflate, inflate);
        to.add(inflate, inflate, inflate);

        float x0 = from.x, y0 = from.y, z0 = from.z;
        float x1 = to.x,   y1 = to.y,   z1 = to.z;

        // 6 faces × 2 triangles × 3 vertices × 5 floats (POSITION_TEXTURE).
        int         faceCount = cuboid.faces().size();
        FloatBuffer buf       = BufferUtils.createFloatBuffer(faceCount * 6 * 5);
        int[]       vc        = {0};

        cuboid.faces().forEach((faceName, face) -> {
            float[] uv = face.uv();
            float   u1 = uv[0] * uScale;
            float   v1 = uv[1] * vScale;
            float   u2 = uv[2] * uScale;
            float   v2 = uv[3] * vScale;

            // Face vertex order and UV corners mirror the original renderCuboid /
            // drawCuboidFace exactly.  The call convention is:
            //   writeFace(buf, vc, v0, v1, v2, v3, u1, v2, u2, v1)
            // i.e. the last four args are the UV *rectangle corners*, where
            // (u1, v2) maps to the bottom-left vertex and (u2, v1) to top-right,
            // matching the original drawCuboidFace parameter layout.
            switch (faceName) {
                case "north" -> writeFace(buf, vc,
                        x1,y0,z0,  x0,y0,z0,  x0,y1,z0,  x1,y1,z0,  u1,v2, u2,v1);
                case "south" -> writeFace(buf, vc,
                        x0,y0,z1,  x1,y0,z1,  x1,y1,z1,  x0,y1,z1,  u1,v2, u2,v1);
                case "west"  -> writeFace(buf, vc,
                        x0,y0,z0,  x0,y0,z1,  x0,y1,z1,  x0,y1,z0,  u1,v2, u2,v1);
                case "east"  -> writeFace(buf, vc,
                        x1,y0,z1,  x1,y0,z0,  x1,y1,z0,  x1,y1,z1,  u1,v2, u2,v1);
                case "up"    -> writeFace(buf, vc,
                        x0,y1,z1,  x1,y1,z1,  x1,y1,z0,  x0,y1,z0,  u1,v2, u2,v1);
                case "down"  -> writeFace(buf, vc,
                        x0,y0,z0,  x1,y0,z0,  x1,y0,z1,  x0,y0,z1,  u1,v2, u2,v1);
            }
        });

        buf.flip();
        GpuModelCache.getInstance().upload(key, buf, vc[0], VertexFormat.POSITION_TEXTURE);
    }

    /**
     * Writes one quad as two CCW triangles (v0,v1,v2) and (v0,v2,v3).
     *
     * <h3>BUG FIX 3 — UV mapping</h3>
     * The parameters {@code u1, v1, u2, v2} represent the <em>four corners</em> of
     * the UV rectangle as passed in by the caller (which already swaps v1/v2 to match
     * BlockBench's coordinate system, identical to the original {@code drawCuboidFace}).
     * The correct vertex → UV assignment is:
     * <pre>
     *   v0 (bottom-left)  → (u1, v1)
     *   v1 (bottom-right) → (u2, v1)
     *   v2 (top-right)    → (u2, v2)
     *   v3 (top-left)     → (u1, v2)
     * </pre>
     * The previous version had v1/v2 swapped inside this method, causing all
     * texture coordinates to be flipped vertically across every cuboid face.
     *
     * @param u1 U coordinate of the left edge   (in normalised UV space)
     * @param v1 V coordinate of the bottom edge
     * @param u2 U coordinate of the right edge
     * @param v2 V coordinate of the top edge
     */
    private void writeFace(FloatBuffer buf, int[] vc,
                           float ax, float ay, float az,   // v0 bottom-left
                           float bx, float by, float bz,   // v1 bottom-right
                           float cx, float cy, float cz,   // v2 top-right
                           float dx, float dy, float dz,   // v3 top-left
                           float u1, float v1,             // left  / bottom UV
                           float u2, float v2) {           // right / top    UV
        // Triangle 1: v0, v1, v2
        buf.put(ax).put(ay).put(az).put(u1).put(v1); // v0 → (u1, v1)  ← fixed (was v2)
        buf.put(bx).put(by).put(bz).put(u2).put(v1); // v1 → (u2, v1)  ← fixed (was v2)
        buf.put(cx).put(cy).put(cz).put(u2).put(v2); // v2 → (u2, v2)  ← fixed (was v1)
        // Triangle 2: v0, v2, v3
        buf.put(ax).put(ay).put(az).put(u1).put(v1); // v0 → (u1, v1)  ← fixed (was v2)
        buf.put(cx).put(cy).put(cz).put(u2).put(v2); // v2 → (u2, v2)  ← fixed (was v1)
        buf.put(dx).put(dy).put(dz).put(u1).put(v2); // v3 → (u1, v2)  ← fixed (was v1)
        vc[0] += 6;
    }

    // ── Triangle-mesh baking ──────────────────────────────────────────────────

    /**
     * Bakes a freeform BlockBench mesh (triangles + quads) into triangles.
     * Vertex positions are stored in mesh-local space; the origin/rotation are
     * applied at draw time via {@code u_Model}.
     */
    private void bakeTriMesh(String key, StrataMeshData meshData, float uScale, float vScale) {
        StrataMeshData.Mesh mesh = meshData.mesh();
        if (mesh == null) {
            LOGGER.warn("GpuModelBaker: mesh data is null for key '{}'", key);
            return;
        }

        Map<String, Vector3f>              vertices = mesh.vertices();
        Map<String, StrataMeshData.Face>   faces    = mesh.faces();

        // Pre-count to allocate exactly the right buffer size.
        int triangleCount = 0;
        for (StrataMeshData.Face face : faces.values()) {
            int n = face.getVertexIds().size();
            if (n == 3) triangleCount += 1;
            else if (n == 4) triangleCount += 2;
        }

        FloatBuffer buf = BufferUtils.createFloatBuffer(triangleCount * 3 * 5);
        int[]       vc  = {0};

        for (StrataMeshData.Face face : faces.values()) {
            List<String>         ids = face.getVertexIds();
            Map<String, float[]> uvs = face.getUvs();

            if (ids.size() == 3) {
                writeTriangle(buf, vc, vertices, uvs, ids.get(0), ids.get(1), ids.get(2), uScale, vScale);
            } else if (ids.size() == 4) {
                List<String> sorted = sortQuad(vertices, ids);
                writeTriangle(buf, vc, vertices, uvs, sorted.get(0), sorted.get(1), sorted.get(2), uScale, vScale);
                writeTriangle(buf, vc, vertices, uvs, sorted.get(2), sorted.get(3), sorted.get(0), uScale, vScale);
            }
        }

        buf.flip();
        GpuModelCache.getInstance().upload(key, buf, vc[0], VertexFormat.POSITION_TEXTURE);
    }

    private void writeTriangle(FloatBuffer buf, int[] vc,
                               Map<String, Vector3f> vertices, Map<String, float[]> uvs,
                               String aId, String bId, String cId,
                               float uScale, float vScale) {
        Vector3f a = vertices.get(aId);
        Vector3f b = vertices.get(bId);
        Vector3f c = vertices.get(cId);
        if (a == null || b == null || c == null) return;

        float[] uvA = uvs.getOrDefault(aId, new float[]{0, 0});
        float[] uvB = uvs.getOrDefault(bId, new float[]{0, 0});
        float[] uvC = uvs.getOrDefault(cId, new float[]{0, 0});

        buf.put(a.x).put(a.y).put(a.z).put(uvA[0] * uScale).put(uvA[1] * vScale);
        buf.put(b.x).put(b.y).put(b.z).put(uvB[0] * uScale).put(uvB[1] * vScale);
        buf.put(c.x).put(c.y).put(c.z).put(uvC[0] * uScale).put(uvC[1] * vScale);
        vc[0] += 3;
    }

    // ── Quad sorting (identical algorithm to original ModelRenderer) ──────────

    private List<String> sortQuad(Map<String, Vector3f> allVerts, List<String> ids) {
        Vector3f v0 = allVerts.get(ids.get(0));
        Vector3f v1 = allVerts.get(ids.get(1));
        Vector3f v2 = allVerts.get(ids.get(2));
        Vector3f v3 = allVerts.get(ids.get(3));
        if (v0 == null || v1 == null || v2 == null || v3 == null) return ids;

        Vector3f center = new Vector3f(v0).add(v1).add(v2).add(v3).div(4.0f);
        Vector3f diagA  = new Vector3f(v2).sub(v0);
        Vector3f diagB  = new Vector3f(v3).sub(v1);
        Vector3f normal = new Vector3f(diagA).cross(diagB).normalize();
        Vector3f right  = new Vector3f(v0).sub(center).normalize();
        Vector3f up     = new Vector3f(normal).cross(right).normalize();

        record AP(float angle, String id) {}
        List<AP> pairs = new ArrayList<>(4);
        for (String id : ids) {
            Vector3f dir = new Vector3f(allVerts.get(id)).sub(center);
            pairs.add(new AP((float) Math.atan2(dir.dot(up), dir.dot(right)), id));
        }
        pairs.sort((a, b) -> Float.compare(a.angle(), b.angle()));

        List<String> result = new ArrayList<>(4);
        for (AP p : pairs) result.add(p.id());
        return result;
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private String cachePrefix(Identifier modelId) {
        return modelId.toString() + "/";
    }
}