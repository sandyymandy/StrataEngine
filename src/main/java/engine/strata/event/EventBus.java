package engine.strata.event;

import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.Consumer;

public class EventBus {
    private final Map<Class<?>, List<Consumer<Object>>> listeners = new HashMap<>();
    private final Queue<Object> eventQueue = new ConcurrentLinkedQueue<>();

    /**
     * Subscribe a system (like the InputSystem) to an event type.
     */
    public <T> void subscribe(Class<T> eventType, Consumer<T> listener) {
        listeners.computeIfAbsent(eventType, k -> new ArrayList<>())
                .add(obj -> listener.accept(eventType.cast(obj)));
    }

    /**
     * Post an event (Called by the FrontEnd/Window).
     */
    public void post(Object event) {
        eventQueue.add(event);
    }

    /**
     * Dispatches all queued events to listeners.
     * MUST be called at the very start of the Logic Tick (BackEnd).
     */
    public void flush() {
        Object event;
        while ((event = eventQueue.poll()) != null) {
            List<Consumer<Object>> targets = listeners.get(event.getClass());
            if (targets != null) {
                Object finalEvent = event;
                targets.forEach(l -> l.accept(finalEvent));
            }
        }
    }
}