package engine.strata.registry.registries;

import engine.strata.entity.util.EntityKey;
import engine.strata.registry.Registry;
import engine.strata.world.block.Block;
import engine.strata.world.item.Item;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Central registry holder for all game registries.
 */
public class Registries {

    // Entity registries
    public static final Registry<EntityKey<?>> ENTITY_KEY = new Registry<>();

    // Block registries
    public static final Registry<Block> BLOCK = new Registry<>();

    // Numeric ID mapping for blocks (used in chunks)
    // This allows efficient storage while keeping the registry system
    public static final ConcurrentHashMap<Short, Block> BLOCK_BY_ID = new ConcurrentHashMap<>();
    public static final AtomicInteger BLOCK_ID_COUNTER = new AtomicInteger(0);

    // Item registries
    public static final Registry<Item> ITEM = new Registry<>();

    // Numeric ID mapping for items (used in inventories and network)
    public static final ConcurrentHashMap<Short, Item> ITEM_BY_ID = new ConcurrentHashMap<>();
    public static final AtomicInteger ITEM_ID_COUNTER = new AtomicInteger(0);
}