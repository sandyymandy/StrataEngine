package engine.strata.api;

public interface ClientInitializer {
    void onClientInitialize(); // Client-only (e.g., Shaders, Keybinds)
}
