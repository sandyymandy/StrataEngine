package engine.strata.core.entrypoint;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

public class EntrypointManager {
    // Key is the entrypoint type (e.g., "common"), Value is the list of ALL mod instances for that type
    private static final Map<String, List<Object>> ENTRYPOINTS = new HashMap<>();

    /**
     * Registers an entrypoint instance under a specific key (type).
     */
    public static void load(String key, String className, ClassLoader loader) {
        try {
            Class<?> clazz = Class.forName(className, true, loader);
            Object instance = clazz.getDeclaredConstructor().newInstance();
            // We only care about the 'key' (e.g., "common") regardless of namespace
            ENTRYPOINTS.computeIfAbsent(key, k -> new ArrayList<>()).add(instance);
        } catch (Exception e) {
            System.err.println("Failed to load entrypoint [" + key + "] from class: " + className);
            e.printStackTrace();
        }
    }

    /**
     * Invokes all registered objects for a specific key (e.g., "common")
     * that match the provided interface.
     */
    public static <T> void invoke(String key, Class<T> type, Consumer<T> action) {
        List<Object> entries = ENTRYPOINTS.get(key);

        if (entries != null) {
            for (Object entry : entries) {
                if (type.isInstance(entry)) {
                    action.accept(type.cast(entry));
                }
            }
        }
    }
}