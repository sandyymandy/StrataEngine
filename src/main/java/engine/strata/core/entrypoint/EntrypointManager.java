package engine.strata.core.entrypoint;

import engine.strata.util.Identifier;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class EntrypointManager {
    // Stores: "strata:main" -> List of instantiated entrypoint classes
    private static final Map<Identifier, List<Object>> ENTRYPOINTS = new HashMap<>();

    public static <T> void load(Identifier id, String className, Class<T> type) {
        try {
            Class<?> clazz = Class.forName(className);
            if (type.isAssignableFrom(clazz)) {
                T instance = type.cast(clazz.getDeclaredConstructor().newInstance());
                ENTRYPOINTS.computeIfAbsent(id, k -> new ArrayList<>()).add(instance);
            }
        } catch (Exception e) {
            System.err.println("Failed to load entrypoint [" + id + "] from class: " + className);
            e.printStackTrace();
        }
    }

    public static <T> void invoke(Class<T> type, java.util.function.Consumer<T> action) {
        for (List<Object> list : ENTRYPOINTS.values()) {
            for (Object entry : list) {
                if (type.isInstance(entry)) {
                    action.accept(type.cast(entry));
                }
            }
        }
    }
}