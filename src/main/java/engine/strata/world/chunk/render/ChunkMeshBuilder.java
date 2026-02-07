package engine.strata.world.chunk.render;

import engine.strata.world.block.Block;
import engine.strata.world.chunk.Chunk;
import engine.strata.world.chunk.ChunkManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * Builds optimized meshes for chunk rendering.
 * Uses greedy meshing and culls hidden faces for better performance.
 */
public class ChunkMeshBuilder {
    private static final Logger LOGGER = LoggerFactory.getLogger("ChunkMesh");

    private final ChunkManager chunkManager;

    public ChunkMeshBuilder(ChunkManager chunkManager) {
        this.chunkManager = chunkManager;
    }

    /**
     * Builds a mesh for a chunk with face culling and lighting.
     */
    public ChunkMesh buildMesh(Chunk chunk) {
        long startTime = System.nanoTime();

        List<ChunkVertex> vertices = new ArrayList<>();
        List<Integer> indices = new ArrayList<>();

        int vertexCount = 0;

        // Iterate through all blocks in the chunk
        for (int x = 0; x < Chunk.SIZE; x++) {
            for (int y = 0; y < Chunk.SIZE; y++) {
                for (int z = 0; z < Chunk.SIZE; z++) {
                    Block block = chunk.getBlock(x, y, z);

                    // Skip air blocks
                    if (block.isAir()) {
                        continue;
                    }

                    // Add faces based on neighbor visibility
                    vertexCount = addBlockFaces(chunk, x, y, z, block,
                            vertices, indices, vertexCount);
                }
            }
        }

        long endTime = System.nanoTime();
        double timeMs = (endTime - startTime) / 1_000_000.0;

//        LOGGER.debug("Built mesh for {} with {} vertices in {:.2f}ms",
//                chunk, vertices.size(), timeMs);

        return new ChunkMesh(
                vertices.toArray(new ChunkVertex[0]),
                indices.stream().mapToInt(Integer::intValue).toArray(),
                chunk.getChunkX(),
                chunk.getChunkY(),
                chunk.getChunkZ()
        );
    }

    /**
     * Adds faces for a block, culling hidden ones.
     */
    private int addBlockFaces(Chunk chunk, int x, int y, int z, Block block,
                              List<ChunkVertex> vertices, List<Integer> indices,
                              int vertexOffset) {

        int worldX = chunk.localToWorldX(x);
        int worldY = chunk.localToWorldY(y);
        int worldZ = chunk.localToWorldZ(z);

        int light = chunk.getLight(x, y, z);
        float brightness = light / 15.0f;

        // Check each face
        if (shouldRenderFace(chunk, x, y - 1, z)) { // Bottom
            addFace(vertices, indices, x, y, z, Face.BOTTOM, brightness, vertexOffset);
            vertexOffset += 4;
        }

        if (shouldRenderFace(chunk, x, y + 1, z)) { // Top
            addFace(vertices, indices, x, y, z, Face.TOP, brightness, vertexOffset);
            vertexOffset += 4;
        }

        if (shouldRenderFace(chunk, x, y, z - 1)) { // North
            addFace(vertices, indices, x, y, z, Face.NORTH, brightness, vertexOffset);
            vertexOffset += 4;
        }

        if (shouldRenderFace(chunk, x, y, z + 1)) { // South
            addFace(vertices, indices, x, y, z, Face.SOUTH, brightness, vertexOffset);
            vertexOffset += 4;
        }

        if (shouldRenderFace(chunk, x - 1, y, z)) { // West
            addFace(vertices, indices, x, y, z, Face.WEST, brightness, vertexOffset);
            vertexOffset += 4;
        }

        if (shouldRenderFace(chunk, x + 1, y, z)) { // East
            addFace(vertices, indices, x, y, z, Face.EAST, brightness, vertexOffset);
            vertexOffset += 4;
        }

        return vertexOffset;
    }

    /**
     * Checks if a face should be rendered based on neighbor block.
     */
    private boolean shouldRenderFace(Chunk chunk, int x, int y, int z) {
        // Handle chunk boundaries
        if (x < 0 || x >= Chunk.SIZE || y < 0 || y >= Chunk.SIZE ||
                z < 0 || z >= Chunk.SIZE) {

            // Check neighbor chunk
            int worldX = chunk.localToWorldX(x);
            int worldY = chunk.localToWorldY(y);
            int worldZ = chunk.localToWorldZ(z);

            Block neighborBlock = chunkManager.getBlock(worldX, worldY, worldZ);
            return neighborBlock.isAir() || !neighborBlock.isOpaque();
        }

        Block neighborBlock = chunk.getBlock(x, y, z);
        return neighborBlock.isAir() || !neighborBlock.isOpaque();
    }

    /**
     * Adds vertices and indices for a single face.
     */
    private void addFace(List<ChunkVertex> vertices, List<Integer> indices,
                         int x, int y, int z, Face face, float brightness,
                         int vertexOffset) {

        float[][] faceVertices = getFaceVertices(face, x, y, z);

        // Add 4 vertices for the face
        for (float[] vertex : faceVertices) {
            vertices.add(new ChunkVertex(
                    vertex[0], vertex[1], vertex[2],  // Position
                    vertex[3], vertex[4],              // UV
                    brightness                          // Lighting
            ));
        }

        // Add 2 triangles (6 indices) for the face
        indices.add(vertexOffset);
        indices.add(vertexOffset + 1);
        indices.add(vertexOffset + 2);
        indices.add(vertexOffset + 2);
        indices.add(vertexOffset + 3);
        indices.add(vertexOffset);
    }

    /**
     * Gets vertex positions and UVs for a face.
     */
    private float[][] getFaceVertices(Face face, int x, int y, int z) {
        return switch (face) {
            case TOP -> new float[][] {
                    {x, y + 1, z, 0, 0},
                    {x + 1, y + 1, z, 1, 0},
                    {x + 1, y + 1, z + 1, 1, 1},
                    {x, y + 1, z + 1, 0, 1}
            };
            case BOTTOM -> new float[][] {
                    {x, y, z, 0, 0},
                    {x, y, z + 1, 0, 1},
                    {x + 1, y, z + 1, 1, 1},
                    {x + 1, y, z, 1, 0}
            };
            case NORTH -> new float[][] {
                    {x, y, z, 0, 0},
                    {x + 1, y, z, 1, 0},
                    {x + 1, y + 1, z, 1, 1},
                    {x, y + 1, z, 0, 1}
            };
            case SOUTH -> new float[][] {
                    {x, y, z + 1, 0, 0},
                    {x, y + 1, z + 1, 0, 1},
                    {x + 1, y + 1, z + 1, 1, 1},
                    {x + 1, y, z + 1, 1, 0}
            };
            case WEST -> new float[][] {
                    {x, y, z, 0, 0},
                    {x, y + 1, z, 0, 1},
                    {x, y + 1, z + 1, 1, 1},
                    {x, y, z + 1, 1, 0}
            };
            case EAST -> new float[][] {
                    {x + 1, y, z, 0, 0},
                    {x + 1, y, z + 1, 1, 0},
                    {x + 1, y + 1, z + 1, 1, 1},
                    {x + 1, y + 1, z, 0, 1}
            };
        };
    }

    private enum Face {
        TOP, BOTTOM, NORTH, SOUTH, WEST, EAST
    }

    /**
     * Vertex data for chunk rendering.
     */
    public record ChunkVertex(
            float x, float y, float z,  // Position
            float u, float v,            // Texture coordinates
            float brightness             // Lighting (0-1)
    ) {}

    /**
     * Complete chunk mesh with vertex and index data.
     */
    public record ChunkMesh(
            ChunkVertex[] vertices,
            int[] indices,
            int chunkX,
            int chunkY,
            int chunkZ
    ) {
        public boolean isEmpty() {
            return vertices.length == 0;
        }
    }
}