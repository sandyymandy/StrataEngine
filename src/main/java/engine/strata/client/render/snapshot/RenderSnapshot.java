package engine.strata.client.render.snapshot;

import java.util.HashMap;
import java.util.Map;

public class RenderSnapshot {
    private final Map<DataTicket<?>, Object> dataMap = new HashMap<>();

    public <T> void addCustomData(DataTicket<T> ticket, T value) {
        dataMap.put(ticket, value);
    }

    @SuppressWarnings("unchecked")
    public <T> T getCustomData(DataTicket<T> ticket) {
        return (T) dataMap.get(ticket);
    }

    public <T> T getCustomDataOrDefault(DataTicket<T> ticket, T defaultValue) {
        T val = getCustomData(ticket);
        return val != null ? val : defaultValue;
    }
}
