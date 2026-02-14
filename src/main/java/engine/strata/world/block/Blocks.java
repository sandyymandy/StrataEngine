package engine.strata.world.block;

import engine.strata.registry.Registry;
import engine.strata.registry.registries.Registries;
import engine.strata.util.Identifier;

/**
 * Registry of all block types in the game.
 *
 * TEXTURE SYSTEM:
 * This file demonstrates two approaches to block textures:
 *
 * 1. INDEX-BASED (for pre-built texture atlases):
 *    - Use numeric indices (0, 1, 2...) to reference atlas positions
 *    - Requires a pre-made atlas texture with known layout
 *    - Example: new BlockTexture(1) or new BlockTexture(3, 2, 2)
 *
 * 2. IDENTIFIER-BASED (for dynamic texture atlases):
 *    - Use Identifier objects to reference texture files
 *    - Textures are loaded from /assets/{namespace}/textures/blocks/{path}.png
 *    - Atlas is built automatically at runtime
 *    - Example: new BlockTexture(Identifier.ofEngine("stone"))
 *
 * You can mix both approaches - the system will resolve them at runtime.
 */
public class Blocks {

    // ========================================================================
    // BASIC BLOCKS - Using identifier-based textures (recommended)
    // ========================================================================

    // Air block (empty space) - no texture needed
    public static final Block AIR = register("air",
            Block.BlockProperties.builder()
                    .solid(false)
                    .opaque(false)
                    .collidable(false)
                    .hardness(0)
                    .build(),
            new BlockTexture(0) // Doesn't matter, won't be rendered
    );

    // Stone - single texture on all faces
    public static final Block STONE = register("stone",
            Block.BlockProperties.builder()
                    .hardness(1.5f)
                    .build(),
            new BlockTexture(Identifier.ofEngine("stone"))
    );

    // Dirt - single texture on all faces
    public static final Block DIRT = register("dirt",
            Block.BlockProperties.builder()
                    .hardness(0.5f)
                    .build(),
            new BlockTexture(Identifier.ofEngine("dirt"))
    );

    // Grass - different textures for top/sides/bottom (using identifiers)
    public static final Block GRASS = register("grass",
            Block.BlockProperties.builder()
                    .hardness(0.6f)
                    .build(),
            new BlockTexture(
                    Identifier.ofEngine("grass"),     // Top
                    Identifier.ofEngine("dirt")           // Bottom
            )
    );

    /**
     * Internal helper to register blocks with automatic numeric ID assignment.
     */
    private static Block register(String id, Block.BlockProperties properties, BlockTexture texture) {
        return register(Identifier.ofEngine(id), properties, texture);
    }

    /**
     * Registers a block with a custom identifier (for mod support).
     */
    private static Block register(Identifier identifier, Block.BlockProperties properties, BlockTexture texture) {
        Block block = new Block(identifier, properties, texture);

        // Register in the main registry
        Block registered = Registry.register(Registries.BLOCK, identifier, block);

        // Assign numeric ID for chunk storage
        short numericId = (short) Registries.BLOCK_ID_COUNTER.getAndIncrement();
        registered.setNumericId(numericId);

        // Store in numeric ID lookup
        Registries.BLOCK_BY_ID.put(numericId, registered);

        return registered;
    }

    /**
     * Gets a block by its identifier.
     */
    public static Block get(Identifier id) {
        Block block = Registries.BLOCK.get(id);
        return block != null ? block : AIR;
    }

    /**
     * Gets a block by its numeric ID (for chunk storage).
     */
    public static Block getByNumericId(short id) {
        Block block = Registries.BLOCK_BY_ID.get(id);
        return block != null ? block : AIR;
    }

    /**
     * Gets all registered blocks.
     * Useful for building texture atlases.
     */
    public static java.util.Collection<Block> getAllBlocks() {
        return Registries.BLOCK.values();
    }
}