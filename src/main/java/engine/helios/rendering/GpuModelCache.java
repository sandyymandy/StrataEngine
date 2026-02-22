package engine.helios.rendering;

import engine.helios.rendering.vertex.VertexFormat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.FloatBuffer;
import java.util.HashMap;
import java.util.Map;

/**
 * Helios-side cache of static VAO/VBO objects for GPU-driven model rendering.
 *
 * <p>This class knows nothing about Strata model types. It simply stores
 * {@link MeshRenderer} instances keyed by an opaque {@code String} cache key,
 * and provides upload / lookup / disposal methods.
 *
 * <p>The Strata-side {@code GpuModelBaker} is responsible for converting
 * {@code StrataMeshData} into a {@link FloatBuffer} and choosing a key, then
 * calling {@link #upload} here.
 *
 * <h3>Typical key convention (enforced by GpuModelBaker):</h3>
 * <pre>{@code "<modelNamespace>:<modelPath>/<meshId>" }</pre>
 */
public final class GpuModelCache {

    private static final Logger LOGGER = LoggerFactory.getLogger("GpuModelCache");
    private static final GpuModelCache INSTANCE = new GpuModelCache();

    /** Key → uploaded mesh. */
    private final Map<String, MeshRenderer> cache = new HashMap<>();

    private GpuModelCache() {}

    public static GpuModelCache getInstance() {
        return INSTANCE;
    }

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Returns {@code true} if this key already has an uploaded mesh.
     * Call this before {@link #upload} to avoid redundant uploads.
     */
    public boolean contains(String key) {
        return cache.containsKey(key);
    }

    /**
     * Uploads vertex data to the GPU and stores it under {@code key}.
     *
     * <p>If a mesh is already cached under this key it is disposed first.
     * Must be called on the render thread.
     *
     * @param key         Unique identifier for this mesh.
     * @param data        Vertex data in the layout described by {@code format}.
     * @param vertexCount Number of vertices in {@code data}.
     * @param format      Vertex attribute layout.
     */
    public void upload(String key, FloatBuffer data, int vertexCount, VertexFormat format) {
        // Dispose any previous mesh stored under this key.
        MeshRenderer existing = cache.get(key);
        if (existing != null) {
            existing.dispose();
        }

        MeshRenderer renderer = new MeshRenderer();
        renderer.upload(data, vertexCount, format);
        cache.put(key, renderer);

        LOGGER.debug("GpuModelCache: uploaded '{}' ({} verts, {} KB)",
                key, vertexCount, renderer.getBufferSize() / 1024);
    }

    /**
     * Returns the {@link MeshRenderer} for {@code key}, or {@code null} if it
     * has not been uploaded yet.
     */
    public MeshRenderer get(String key) {
        return cache.get(key);
    }

    /**
     * Disposes every mesh whose key starts with {@code prefix}.
     *
     * <p>Use this to free all meshes belonging to one model:
     * <pre>{@code GpuModelCache.getInstance().disposeByPrefix("strata:player/"); }</pre>
     */
    public void disposeByPrefix(String prefix) {
        int freed = 0;
        var it = cache.entrySet().iterator();
        while (it.hasNext()) {
            var entry = it.next();
            if (entry.getKey().startsWith(prefix)) {
                entry.getValue().dispose();
                it.remove();
                freed++;
            }
        }
        if (freed > 0) {
            LOGGER.debug("GpuModelCache: freed {} mesh(es) with prefix '{}'", freed, prefix);
        }
    }

    /**
     * Disposes ALL cached meshes and clears the cache.
     * Call this on renderer reload or shutdown.
     */
    public void disposeAll() {
        int freed = cache.size();
        cache.values().forEach(MeshRenderer::dispose);
        cache.clear();
        LOGGER.info("GpuModelCache: disposed {} mesh(es)", freed);
    }

    /** Returns the total number of cached meshes. */
    public int size() {
        return cache.size();
    }
}