package engine.strata.client.frontend.ui;

public record UiRect(float x, float y, float width, float height) {
    public boolean contains(float px, float py) {
        return px >= x && py >= y && px <= x + width && py <= y + height;
    }
}

