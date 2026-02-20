package engine.strata.world.chunk.render;

import engine.helios.RenderLayer;
import engine.strata.client.StrataClient;
import engine.strata.client.render.Camera;
import engine.strata.client.render.RenderLayers;
import engine.strata.util.Identifier;
import engine.strata.world.chunk.Chunk;
import engine.strata.world.chunk.ChunkManager;
import engine.strata.world.block.DynamicTextureArray;
import engine.strata.world.chunk.Region;
import engine.strata.world.block.TextureArrayManager;
import engine.strata.world.chunk.SubChunk;
import org.joml.Matrix4f;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.lwjgl.opengl.GL11.glBindTexture;
import static org.lwjgl.opengl.GL30.GL_TEXTURE_2D_ARRAY;

import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class ChunkRenderer {
    private static final Logger LOGGER = LoggerFactory.getLogger("ChunkRender");

    private final ChunkManager chunkManager;
    private final ChunkMeshBuilder chunkMeshBuilder;
    private final Camera        camera;

    private final Map<Long, ChunkMesh> meshes = new ConcurrentHashMap<>();

    private final RenderLayer chunkLayer;
    private final int textureArrayGlId;

    private int renderDistance = 64;

    // Stats
    private int chunksRendered    = 0;
    private int chunksCulled      = 0;
    private int trianglesRendered = 0;

    public ChunkRenderer(ChunkManager chunkManager, ChunkMeshBuilder chunkMeshBuilder,
                         Camera camera, Identifier atlasId) {
        this.chunkManager = chunkManager;
        this.chunkMeshBuilder = chunkMeshBuilder;
        this.camera       = camera;
        this.chunkLayer   = RenderLayers.getChunkLayer(atlasId);

        // Cache the GL id so we can rebind as GL_TEXTURE_2D_ARRAY after setup().
        // RenderLayer.setup() calls glBindTexture(GL_TEXTURE_2D, id) which is the
        // wrong target — we override it immediately before drawing.
        DynamicTextureArray arr = TextureArrayManager.getInstance().getArray();
        this.textureArrayGlId = (arr != null) ? arr.getTextureId() : 0;
    }

    // Main render loop

    public void render() {
        chunksRendered    = 0;
        chunksCulled      = 0;
        trianglesRendered = 0;

        submitMeshingJobs();
        processUploadQueue();
        pruneOrphanedMeshes();

        chunkLayer.setup(camera);

        // RenderLayer.setup() bound our texture as GL_TEXTURE_2D (wrong target).
        // Rebind it as GL_TEXTURE_2D_ARRAY so the sampler2DArray in the shader
        // actually sees the data.
        if (textureArrayGlId != 0) {
            glBindTexture(GL_TEXTURE_2D_ARRAY, textureArrayGlId);
        }

        renderVisibleChunks();
        chunkLayer.clean();
    }

    // Meshing jobs

    private void submitMeshingJobs() {
        for (Chunk chunk : chunkManager.getAllChunks()) {
            if (chunk.isGenerated() && chunk.needsRemesh()) {
                chunk.setNeedsRemesh(false);
                chunkMeshBuilder.requestMesh(chunk.getChunkX(), chunk.getChunkZ());
            }
        }
    }

    // Upload queue

    private void processUploadQueue() {
        int uploaded           = 0;
        int maxUploadsPerFrame = 4;

        while (uploaded < maxUploadsPerFrame && chunkMeshBuilder.hasUploadTasks()) {
            ChunkMeshBuilder.MeshUploadTask task = chunkMeshBuilder.pollUploadTask();
            if (task == null) break;

            long      key  = packChunkKey(task.chunkX, task.chunkZ);
            ChunkMesh mesh = meshes.computeIfAbsent(key,
                    k -> new ChunkMesh(task.chunkX, task.chunkZ));

            mesh.upload(task.getData(), task.vertexCount);
            // Release the off-heap DirectByteBuffer immediately, not at GC time.
            task.releaseBuffer();
            uploaded++;
        }

        if (uploaded > 0 && StrataClient.getInstance().getDebugInfo().showRenderDebug()) {
            LOGGER.debug("Uploaded {} chunk meshes to GPU", uploaded);
        }
    }

    // Orphan mesh pruning

    private void pruneOrphanedMeshes() {
        Iterator<Map.Entry<Long, ChunkMesh>> it = meshes.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<Long, ChunkMesh> entry = it.next();
            ChunkMesh mesh  = entry.getValue();
            Chunk     chunk = chunkManager.getChunk(mesh.getChunkX(), mesh.getChunkZ());
            if (chunk == null) {
                mesh.dispose();
                it.remove();
            }
        }
    }

    // Rendering
    private void renderVisibleChunks() {
        float camX = (float) camera.getPos().x;
        float camY = (float) camera.getPos().y;
        float camZ = (float) camera.getPos().z;

        // Convert chunk distance to blocks once; all culling uses block-space.
        float blockDistance = renderDistance * SubChunk.SIZE;

        chunkLayer.shaderStack().setUniform("u_Model", new Matrix4f().identity());

        List<Region> visibleRegions = chunkManager.getVisibleRegions(
                camX, camY, camZ, blockDistance);

        for (Region region : visibleRegions) {
            for (Chunk chunk : region.getChunks()) {
                if (!chunk.isGenerated()) continue;

                if (!isChunkVisible(chunk, camX, camY, camZ, blockDistance)) {
                    chunksCulled++;
                    continue;
                }

                renderChunk(chunk);
                chunksRendered++;
            }
        }
    }

    private boolean isChunkVisible(Chunk chunk, float camX, float camY, float camZ,
                                   float blockDistance) {
        float chunkCenterX = (chunk.getChunkX() * SubChunk.SIZE) + (SubChunk.SIZE / 2.0f);
        float chunkCenterZ = (chunk.getChunkZ() * SubChunk.SIZE) + (SubChunk.SIZE / 2.0f);

        int[] yRange = chunk.getYRange();
        if (yRange == null) return false;

        float chunkCenterY = (yRange[0] + yRange[1]) / 2.0f;
        float chunkHeight  = yRange[1] - yRange[0];
        float half         = SubChunk.SIZE / 2.0f;
        float radius = (float) Math.sqrt(
                half * half + (chunkHeight / 2.0f) * (chunkHeight / 2.0f) + half * half);

        return camera.isSphereVisibleWithDistance(
                chunkCenterX, chunkCenterY, chunkCenterZ, radius, blockDistance);
    }

    private void renderChunk(Chunk chunk) {
        long      key  = packChunkKey(chunk.getChunkX(), chunk.getChunkZ());
        ChunkMesh mesh = meshes.get(key);
        if (mesh == null || !mesh.isUploaded() || mesh.isEmpty()) return;

        mesh.render();
        trianglesRendered += mesh.getVertexCount() / 3;
    }

    // Disposal
    public void disposeMesh(int chunkX, int chunkZ) {
        long      key  = packChunkKey(chunkX, chunkZ);
        ChunkMesh mesh = meshes.remove(key);
        if (mesh != null) mesh.dispose();
    }

    public void disposeAll() {
        for (ChunkMesh mesh : meshes.values()) mesh.dispose();
        meshes.clear();
        LOGGER.info("Disposed all chunk meshes");
    }

    // Stats / accessors
    public int getMeshCount()         { return meshes.size(); }
    public int getChunksRendered()    { return chunksRendered; }
    public int getChunksCulled()      { return chunksCulled; }
    public int getTrianglesRendered() { return trianglesRendered; }

    /** Returns render distance in chunks. */
    public int getRenderDistance() { return renderDistance; }

    public void setRenderDistance(int chunks) {
        this.renderDistance = Math.max(1, Math.min(200, chunks));
        chunkManager.setRenderDistance(this.renderDistance);
    }

    public float getGPUMemoryUsageMB() {
        long total = 0;
        for (ChunkMesh m : meshes.values()) total += m.getBufferSize();
        return total / (1024.0f * 1024.0f);
    }

    private static long packChunkKey(int chunkX, int chunkZ) {
        return ((long) chunkX << 32) | (chunkZ & 0xFFFFFFFFL);
    }
}