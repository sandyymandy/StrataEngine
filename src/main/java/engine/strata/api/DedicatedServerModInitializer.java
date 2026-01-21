package engine.strata.api;

public interface DedicatedServerModInitializer {
    void onServerInitialize(); // Server-only (e.g., RCON, Auto-save)
}
