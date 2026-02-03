package engine.strata.world.block;

import engine.strata.registry.Registry;
import engine.strata.registry.registries.Registries;
import engine.strata.util.Identifier;

/**
 * Registry of all block types in the game.
 * Uses the unified registry system like entities.
 */
public class Blocks {

    // Air block (empty space)
    public static final Block AIR = register("air",
            Block.BlockProperties.builder()
                    .solid(false)
                    .opaque(false)
                    .collidable(false)
                    .hardness(0)
                    .build()
    );

    // Basic solid blocks
    public static final Block STONE = register("stone",
            Block.BlockProperties.builder()
                    .hardness(1.5f)
                    .build()
    );

    public static final Block DIRT = register("dirt",
            Block.BlockProperties.builder()
                    .hardness(0.5f)
                    .build()
    );

    public static final Block GRASS = register("grass",
            Block.BlockProperties.builder()
                    .hardness(0.6f)
                    .build()
    );

    public static final Block WOOD = register("wood",
            Block.BlockProperties.builder()
                    .hardness(2.0f)
                    .build()
    );

    public static final Block LEAVES = register("leaves",
            Block.BlockProperties.builder()
                    .solid(false)
                    .opaque(false)
                    .hardness(0.2f)
                    .build()
    );

    // Glass (transparent)
    public static final Block GLASS = register("glass",
            Block.BlockProperties.builder()
                    .opaque(false)
                    .hardness(0.3f)
                    .build()
    );

    // Light-emitting blocks
    public static final Block GLOWSTONE = register("glowstone",
            Block.BlockProperties.builder()
                    .lightEmission(15)
                    .hardness(0.3f)
                    .build()
    );

    public static final Block TORCH = register("torch",
            Block.BlockProperties.builder()
                    .solid(false)
                    .opaque(false)
                    .collidable(false)
                    .lightEmission(14)
                    .hardness(0)
                    .build()
    );

    // Water (fluid)
    public static final Block WATER = register("water",
            Block.BlockProperties.builder()
                    .solid(false)
                    .opaque(false)
                    .collidable(false)
                    .hardness(100)
                    .build()
    );

    // Lava (fluid, light-emitting)
    public static final Block LAVA = register("lava",
            Block.BlockProperties.builder()
                    .solid(false)
                    .opaque(false)
                    .collidable(false)
                    .lightEmission(15)
                    .hardness(100)
                    .build()
    );

    // Sand
    public static final Block SAND = register("sand",
            Block.BlockProperties.builder()
                    .hardness(0.5f)
                    .build()
    );

    // Cobblestone
    public static final Block COBBLESTONE = register("cobblestone",
            Block.BlockProperties.builder()
                    .hardness(2.0f)
                    .build()
    );

    /**
     * Internal helper to register blocks with automatic numeric ID assignment.
     */
    private static Block register(String id, Block.BlockProperties properties) {
        Identifier identifier = Identifier.ofEngine(id);
        Block block = new Block(identifier, properties);

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
}