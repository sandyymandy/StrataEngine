package engine.strata.client.render.snapshot;

public final class DataTicket<T> {
    private final String id;
    private final Class<T> type;

    private DataTicket(String id, Class<T> type) {
        this.id = id;
        this.type = type;
    }

    public static <T> DataTicket<T> create(String id, Class<T> type) {
        return new DataTicket<>(id, type);
    }

    // Common Global Tickets
    public static final DataTicket<Float> PARTIAL_TICKS = create("partial_ticks", Float.class);
    public static final DataTicket<Float> HEALTH = create("health", Float.class);
    public static final DataTicket<Boolean> IS_MOVING = create("is_moving", Boolean.class);
}