package engine.strata.registry;

import java.util.HashMap;
import java.util.Map;

public class Registry<T> {
    private final Map<String, T> entries = new HashMap<>();
    private final String name;

    public Registry(String name) {
        this.name = name;
    }

    public void register(String id, T value) {
        if (entries.containsKey(id)) {
            System.err.println("Warning: Overwriting registry entry: " + id);
        }
        entries.put(id, value);
    }

    public T get(String id) {
        return entries.get(id);
    }

    public Map<String, T> getAll() {
        return entries;
    }
}
