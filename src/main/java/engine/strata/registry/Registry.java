package engine.strata.registry;

import engine.strata.util.Identifier;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Registry<T> {
    private final Map<Identifier, T> entries = new HashMap<>();

    /**
     * The static entry point for all registrations.
     */
    public static <V, T extends V> T register(Registry<V> registry, Identifier id, T entry) {
        registry.entries.put(id, entry);
        return entry;
    }

    public T get(Identifier id) {
        return entries.get(id);
    }

    public Collection<T> values() {
        return entries.values();
    }
}