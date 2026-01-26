package engine.strata.world;

import engine.strata.entity.Entity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class World {
    private static final Logger LOGGER = LoggerFactory.getLogger("World");

    // Use ConcurrentHashMap for thread-safe access
    private final ConcurrentHashMap<UUID, Entity> entities = new ConcurrentHashMap<>();

    // Cached list for iteration (updated after modifications)
    private volatile List<Entity> entityList = new ArrayList<>();

    public World() {
        LOGGER.info("Creating new World");
    }

    /**
     * Ticks all entities in the world.
     * Called from the Logic Thread.
     */
    public void tick() {
        // Tick all entities
        for (Entity entity : entityList) {
            entity.tick();
        }
    }

    /**
     * Adds an entity to the world.
     * Thread-safe for adding entities from any thread.
     */
    public void addEntity(Entity entity) {
        if (entity == null) {
            LOGGER.warn("Attempted to add null entity to world");
            return;
        }

        UUID id = UUID.randomUUID();
        entities.put(id, entity);
        updateEntityList();

        LOGGER.debug("Added entity {} with ID {}", entity.getClass().getSimpleName(), id);
    }

    /**
     * Removes an entity from the world.
     */
    public void removeEntity(UUID id) {
        Entity removed = entities.remove(id);
        if (removed != null) {
            updateEntityList();
            LOGGER.debug("Removed entity {} with ID {}", removed.getClass().getSimpleName(), id);
        }
    }

    /**
     * Removes an entity by reference (slower, requires iteration).
     */
    public void removeEntity(Entity entity) {
        entities.entrySet().removeIf(entry -> entry.getValue() == entity);
        updateEntityList();
    }

    /**
     * Gets all entities in the world.
     * Returns a snapshot that's safe to iterate.
     */
    public List<Entity> getEntities() {
        return entityList;
    }

    /**
     * Gets the number of entities in the world.
     */
    public int getEntityCount() {
        return entities.size();
    }

    /**
     * Clears all entities from the world.
     */
    public void clear() {
        entities.clear();
        updateEntityList();
        LOGGER.info("Cleared all entities from world");
    }

    /**
     * Updates the cached entity list after modifications.
     */
    private void updateEntityList() {
        entityList = new ArrayList<>(entities.values());
    }
}