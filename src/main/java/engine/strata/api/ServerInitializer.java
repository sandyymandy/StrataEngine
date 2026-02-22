package engine.strata.api;

public interface ServerInitializer {
    void onServerInitialize(); // Server-only (e.g., RCON, Auto-save)
}
