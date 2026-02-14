package engine.strata.world.chunk.render;

import engine.strata.util.Identifier;
import engine.strata.world.block.Block;
import engine.strata.world.block.BlockTexture;
import engine.strata.world.block.DynamicTextureAtlas;
import engine.strata.world.chunk.Chunk;
import engine.strata.world.chunk.ChunkManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * Builds optimized meshes for chunk rendering.
 * Uses face culling to only render visible faces (not hidden by adjacent blocks).
 * Supports both index-based and identifier-based texture references.
 */
public class ChunkMeshBuilder {
    private static final Logger LOGGER = LoggerFactory.getLogger("ChunkMesh");

    private final ChunkManager chunkManager;
    private final DynamicTextureAtlas textureAtlas;

    public ChunkMeshBuilder(ChunkManager chunkManager, DynamicTextureAtlas textureAtlas) {
        this.chunkManager = chunkManager;
        this.textureAtlas = textureAtlas;
    }

    /**
     * Builds a mesh for a chunk with face culling and lighting.
     * Only creates faces that are visible (adjacent to air or transparent blocks).
     */
    public ChunkMesh buildMesh(Chunk chunk) {
        long startTime = System.nanoTime();

        List<ChunkVertex> vertices = new ArrayList<>();
        List<Integer> indices = new ArrayList<>();

        int vertexCount = 0;
        int facesAdded = 0;

        // Iterate through all blocks in the chunk
        for (int x = 0; x < Chunk.SIZE; x++) {
            for (int y = 0; y < Chunk.SIZE; y++) {
                for (int z = 0; z < Chunk.SIZE; z++) {
                    Block block = chunk.getBlock(x, y, z);

                    // Skip air blocks
                    if (block.isAir()) {
                        continue;
                    }

                    // Add only visible faces (those adjacent to air or transparent blocks)
                    int beforeVertices = vertices.size();
                    vertexCount = addBlockFaces(chunk, x, y, z, block,
                            vertices, indices, vertexCount);
                    facesAdded += (vertices.size() - beforeVertices) / 4;
                }
            }
        }

        long endTime = System.nanoTime();
        double timeMs = (endTime - startTime) / 1_000_000.0;

        if (facesAdded > 0) {
            LOGGER.debug("Built mesh for {} with {} vertices, {} faces in {}ms",
                    chunk, vertices.size(), facesAdded, timeMs);
        }

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
     * ONLY CREATES FACES THAT ARE VISIBLE (adjacent to air or transparent blocks).
     */
    private int addBlockFaces(Chunk chunk, int x, int y, int z, Block block,
                              List<ChunkVertex> vertices, List<Integer> indices,
                              int vertexOffset) {

        int light = chunk.getLight(x, y, z);
        float brightness = light / 15.0f;

        BlockTexture blockTexture = block.getTexture();
        if (blockTexture == null) {
            return vertexOffset; // No texture, skip rendering
        }

        // Check each face and only render if it's exposed to air/transparency

        // Bottom face (Y-)
        if (shouldRenderFace(chunk, x, y - 1, z)) {
            addFace(vertices, indices, x, y, z, Face.BOTTOM, blockTexture, brightness, vertexOffset);
            vertexOffset += 4;
        }

        // Top face (Y+)
        if (shouldRenderFace(chunk, x, y + 1, z)) {
            addFace(vertices, indices, x, y, z, Face.TOP, blockTexture, brightness, vertexOffset);
            vertexOffset += 4;
        }

        // North face (Z-)
        if (shouldRenderFace(chunk, x, y, z - 1)) {
            addFace(vertices, indices, x, y, z, Face.NORTH, blockTexture, brightness, vertexOffset);
            vertexOffset += 4;
        }

        // South face (Z+)
        if (shouldRenderFace(chunk, x, y, z + 1)) {
            addFace(vertices, indices, x, y, z, Face.SOUTH, blockTexture, brightness, vertexOffset);
            vertexOffset += 4;
        }

        // West face (X-)
        if (shouldRenderFace(chunk, x - 1, y, z)) {
            addFace(vertices, indices, x, y, z, Face.WEST, blockTexture, brightness, vertexOffset);
            vertexOffset += 4;
        }

        // East face (X+)
        if (shouldRenderFace(chunk, x + 1, y, z)) {
            addFace(vertices, indices, x, y, z, Face.EAST, blockTexture, brightness, vertexOffset);
            vertexOffset += 4;
        }

        return vertexOffset;
    }

    /**
     * Checks if a face should be rendered based on neighbor block.
     * Returns true if the neighbor is air or transparent (face is visible).
     * Returns false if the neighbor is solid opaque (face is hidden).
     */
    private boolean shouldRenderFace(Chunk chunk, int x, int y, int z) {
        // Handle chunk boundaries - check neighbor chunks
        if (x < 0 || x >= Chunk.SIZE || y < 0 || y >= Chunk.SIZE ||
                z < 0 || z >= Chunk.SIZE) {

            // Convert to world coordinates and check neighbor chunk
            int worldX = chunk.localToWorldX(x);
            int worldY = chunk.localToWorldY(y);
            int worldZ = chunk.localToWorldZ(z);

            Block neighborBlock = chunkManager.getBlock(worldX, worldY, worldZ);

            // Render face if neighbor is air or transparent
            return neighborBlock.isAir() || !neighborBlock.isOpaque();
        }

        // Check within current chunk
        Block neighborBlock = chunk.getBlock(x, y, z);

        // Render face if neighbor is air or transparent
        return neighborBlock.isAir() || !neighborBlock.isOpaque();
    }

    /**
     * Adds vertices and indices for a single face with proper texture coordinates.
     * Resolves texture references to atlas indices automatically.
     */
    private void addFace(List<ChunkVertex> vertices, List<Integer> indices,
                         int x, int y, int z, Face face, BlockTexture blockTexture,
                         float brightness, int vertexOffset) {

        float[][] faceVertices = getFaceVertices(face, x, y, z);

        // Get the texture for this face
        BlockTexture.Face blockFace = BlockTexture.Face.valueOf(face.name());
        int textureIndex = resolveTextureIndex(blockTexture, blockFace);

        // Add 4 vertices for the face with texture coordinates from atlas
        for (int i = 0; i < faceVertices.length; i++) {
            float[] vertex = faceVertices[i];

            // Get UV coordinates from the texture atlas
            float[] uv = textureAtlas.getUV(textureIndex, vertex[3], vertex[4]);

            vertices.add(new ChunkVertex(
                    vertex[0], vertex[1], vertex[2],  // Position
                    uv[0], uv[1],                      // UV from atlas
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
     * Resolves a texture reference (either index or identifier) to an atlas index.
     * Handles both pre-indexed textures and dynamic identifier-based textures.
     */
    private int resolveTextureIndex(BlockTexture blockTexture, BlockTexture.Face face) {
        // Try to get the texture index directly (for index-based textures)
        int index = blockTexture.getTextureIndexForFace(face);
        if (index >= 0) {
            return index;
        }

        // Otherwise, resolve the identifier through the atlas
        Identifier textureId = blockTexture.getTextureIdForFace(face);
        if (textureId != null) {
            return textureAtlas.getTextureIndex(textureId);
        }

        // Fallback to index 0 if neither is available
        LOGGER.warn("No texture found for face {}, using index 0", face);
        return 0;
    }

    /**
     * Gets vertex positions and UVs for a face.
     * Vertices are in LOCAL CHUNK SPACE (0-16).
     * Vertices are ordered COUNTER-CLOCKWISE when viewed from outside the block.
     */
    private float[][] getFaceVertices(Face face, int x, int y, int z) {
        return switch (face) {
            case TOP -> new float[][] {
                    {x, y + 1, z, 0, 0},
                    {x, y + 1, z + 1, 0, 1},
                    {x + 1, y + 1, z + 1, 1, 1},
                    {x + 1, y + 1, z, 1, 0}
            };
            case BOTTOM -> new float[][] {
                    {x, y, z, 0, 0},
                    {x + 1, y, z, 1, 0},
                    {x + 1, y, z + 1, 1, 1},
                    {x, y, z + 1, 0, 1}
            };
            case NORTH -> new float[][] {
                    {x, y, z, 0, 0},
                    {x, y + 1, z, 0, 1},
                    {x + 1, y + 1, z, 1, 1},
                    {x + 1, y, z, 1, 0}
            };
            case SOUTH -> new float[][] {
                    {x, y, z + 1, 0, 0},
                    {x + 1, y, z + 1, 1, 0},
                    {x + 1, y + 1, z + 1, 1, 1},
                    {x, y + 1, z + 1, 0, 1}
            };
            case WEST -> new float[][] {
                    {x, y, z, 0, 0},
                    {x, y, z + 1, 1, 0},
                    {x, y + 1, z + 1, 1, 1},
                    {x, y + 1, z, 0, 1}
            };
            case EAST -> new float[][] {
                    {x + 1, y, z, 0, 0},
                    {x + 1, y + 1, z, 0, 1},
                    {x + 1, y + 1, z + 1, 1, 1},
                    {x + 1, y, z + 1, 1, 0}
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
            float x, float y, float z,  // Position (local chunk space)
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