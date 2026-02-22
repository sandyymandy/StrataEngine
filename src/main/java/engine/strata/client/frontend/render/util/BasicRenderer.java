package engine.strata.client.frontend.render.util;

public interface BasicRenderer {
    void preRender(float partialTicks, float deltaTime);
    void render(float partialTicks, float deltaTime);
    void postRender(float partialTicks, float deltaTime);
}
