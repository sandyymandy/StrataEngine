package engine.strata.api;

public interface ClientModInitializer {
    void onClientInitialize(); // Client-only (e.g., Shaders, Keybinds)
}
