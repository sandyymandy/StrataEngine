package engine.strata.world.block;

import engine.strata.registry.Registry;
import engine.strata.registry.registries.Registries;
import engine.strata.util.Identifier;

/**
 * Central registry of all built-in block types.
 *
 * <h3>How registration works</h3>
 * Calling {@link #register} assigns a numeric ID and stores the block in the
 * global block registry.  Visual appearance (geometry + textures) is driven
 * entirely by the block's associated JSON model, resolved at render time by
 * {@link engine.strata.world.block.model.BlockModelLoader}.
 *
 * <h3>Model resolution</h3>
 * By convention a block registered as {@code strata:stone} will load
 * {@code assets/strata/models/block/stone.json} automatically — you don't
 * need to do anything extra.  To override this (e.g. share a model between
 * two blocks) use {@link #register(String, Block.BlockProperties, String)}.
 *
 * <h3>Adding new blocks</h3>
 * <ol>
 *   <li>Add a {@code public static final Block} field here.</li>
 *   <li>Create {@code assets/strata/models/block/{id}.json} that declares the
 *       geometry and texture variables.</li>
 *   <li>Create the texture PNG at
 *       {@code assets/strata/textures/block/{texture}.png}.</li>
 * </ol>
 */
public class Blocks {

    // ── Built-in blocks ───────────────────────────────────────────────────────

    public static final Block AIR = register("air",
            Block.BlockProperties.builder()
                    .transparent(false)
                    .fullBlock(false)
                    .collidable(false)
                    .hardness(0)
                    .build()
    );

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

    public static final Block SIGN = register("sign",
            Block.BlockProperties.builder().transparent(true).fullBlock(false).build());

    // ── Registration helpers ──────────────────────────────────────────────────

    /**
     * Registers a block whose model ID matches its block ID (the common case).
     */
    private static Block register(String id, Block.BlockProperties properties) {
        return register(Identifier.ofEngine(id), properties, null);
    }

    /**
     * Registers a block with an explicit model ID override.
     *
     * @param id         block identifier path, e.g. {@code "mossy_cobblestone"}
     * @param properties physical behaviour
     * @param modelPath  model path override, e.g. {@code "cobblestone"} to reuse
     *                   an existing model; pass {@code null} to use the block ID
     */
    private static Block register(String id, Block.BlockProperties properties, String modelPath) {
        Identifier blockId = Identifier.ofEngine(id);
        Identifier modelId = (modelPath != null) ? Identifier.ofEngine(modelPath) : null;
        return register(blockId, properties, modelId);
    }

    /**
     * Core registration — assigns a numeric ID and stores the block in both
     * the named registry and the numeric lookup map.
     */
    private static Block register(Identifier id, Block.BlockProperties properties, Identifier modelId) {
        Block block = new Block(id, properties, modelId);

        Block registered = Registry.register(Registries.BLOCK, id, block);

        short numericId = (short) Registries.BLOCK_ID_COUNTER.getAndIncrement();
        registered.setNumericId(numericId);
        Registries.BLOCK_BY_ID.put(numericId, registered);

        return registered;
    }

    // ── Lookups ───────────────────────────────────────────────────────────────

    /** Returns the block with the given identifier, or {@link #AIR} if not found. */
    public static Block get(Identifier id) {
        Block block = Registries.BLOCK.get(id);
        return block != null ? block : AIR;
    }

    /** Returns the block with the given numeric ID, or {@link #AIR} if not found. */
    public static Block getByNumericId(short id) {
        Block block = Registries.BLOCK_BY_ID.get(id);
        return block != null ? block : AIR;
    }

    /** Returns all registered blocks. Useful for building texture arrays. */
    public static java.util.Collection<Block> getAllBlocks() {
        return Registries.BLOCK.values();
    }
}