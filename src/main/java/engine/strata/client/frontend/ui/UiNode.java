package engine.strata.client.frontend.ui;

import org.joml.Vector2f;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public abstract class UiNode {
    private UiNode parent;
    private final List<UiNode> children = new ArrayList<>();

    // Layout: anchor within parent (0..1), pivot within self (0..1)
    private final Vector2f anchor = new Vector2f(0, 0);
    private final Vector2f pivot = new Vector2f(0, 0);

    // Size: relative (0..1 of parent) + absolute pixels
    private final Vector2f size = new Vector2f(0, 0);
    private final Vector2f sizePx = new Vector2f(0, 0);

    // Offset in pixels applied at anchor
    private final Vector2f offsetPx = new Vector2f(0, 0);

    private boolean visible = true;
    private boolean enabled = true;

    private UiRect computedRect = new UiRect(0, 0, 0, 0);

    public UiNode parent() { return parent; }
    public List<UiNode> children() { return Collections.unmodifiableList(children); }

    public Vector2f anchor() { return anchor; }
    public Vector2f pivot() { return pivot; }
    public Vector2f size() { return size; }
    public Vector2f sizePx() { return sizePx; }
    public Vector2f offsetPx() { return offsetPx; }

    public UiRect rect() { return computedRect; }

    public boolean isVisible() { return visible; }
    public void setVisible(boolean visible) { this.visible = visible; }

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    public void addChild(UiNode child) {
        if (child.parent != null) {
            child.parent.children.remove(child);
        }
        child.parent = this;
        children.add(child);
    }

    public void removeChild(UiNode child) {
        if (children.remove(child)) {
            child.parent = null;
        }
    }

    public void layout(UiRect parentRect) {
        float w = parentRect.width() * size.x + sizePx.x;
        float h = parentRect.height() * size.y + sizePx.y;

        float anchorX = parentRect.x() + parentRect.width() * anchor.x + offsetPx.x;
        float anchorY = parentRect.y() + parentRect.height() * anchor.y + offsetPx.y;

        float x = anchorX - pivot.x * w;
        float y = anchorY - pivot.y * h;

        computedRect = new UiRect(x, y, w, h);

        for (UiNode child : children) {
            child.layout(computedRect);
        }
    }

    public void update(float mouseX, float mouseY, boolean mouseDown, float deltaTime) {
        for (UiNode child : children) {
            if (child.visible && child.enabled) {
                child.update(mouseX, mouseY, mouseDown, deltaTime);
            }
        }
    }

    public void render(UiRenderContext ctx) {
        for (UiNode child : children) {
            if (child.visible) {
                child.render(ctx);
            }
        }
    }

    /**
     * Returns the top-most interactive node under the point, or null.
     * Children are tested in reverse order so later-added nodes are on top.
     */
    public UiNode hitTest(float px, float py) {
        if (!visible || !enabled) return null;
        UiRect r = rect();
        if (!r.contains(px, py)) return null;

        for (int i = children.size() - 1; i >= 0; i--) {
            UiNode hit = children.get(i).hitTest(px, py);
            if (hit != null) return hit;
        }
        return isInteractive() ? this : null;
    }

    protected boolean isInteractive() { return false; }

    protected void onMouseEnter() {}
    protected void onMouseExit() {}
    protected void onMouseDown() {}
    protected void onMouseUp(boolean clicked) {}

    void dispatchMouseEnter() { onMouseEnter(); }
    void dispatchMouseExit() { onMouseExit(); }
    void dispatchMouseDown() { onMouseDown(); }
    void dispatchMouseUp(boolean clicked) { onMouseUp(clicked); }
}

