package engine.strata.client.render.util;

public interface BasicRenderer {
    void preRender(float partialTicks, float deltaTime);
    void postRender(float partialTicks, float deltaTime);
}
