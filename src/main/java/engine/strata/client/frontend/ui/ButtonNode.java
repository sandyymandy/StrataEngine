package engine.strata.client.frontend.ui;

public class ButtonNode extends FrameNode {
    private Runnable onClick;

    private boolean hovered = false;
    private boolean pressed = false;

    private float normalR = 0.1f, normalG = 0.1f, normalB = 0.1f, normalA = 0.7f;
    private float hoverR  = 0.15f, hoverG  = 0.15f, hoverB  = 0.15f, hoverA  = 0.85f;
    private float pressR  = 0.2f, pressG  = 0.2f, pressB  = 0.2f, pressA  = 0.95f;

    public ButtonNode onClick(Runnable onClick) {
        this.onClick = onClick;
        return this;
    }

    public ButtonNode colors(float nr, float ng, float nb, float na,
                             float hr, float hg, float hb, float ha,
                             float pr, float pg, float pb, float pa) {
        normalR = nr; normalG = ng; normalB = nb; normalA = na;
        hoverR  = hr; hoverG  = hg; hoverB  = hb; hoverA  = ha;
        pressR  = pr; pressG  = pg; pressB  = pb; pressA  = pa;
        return this;
    }

    @Override
    protected boolean isInteractive() {
        return true;
    }

    @Override
    protected void onMouseEnter() {
        hovered = true;
    }

    @Override
    protected void onMouseExit() {
        hovered = false;
        pressed = false;
    }

    @Override
    protected void onMouseDown() {
        pressed = true;
    }

    @Override
    protected void onMouseUp(boolean clicked) {
        boolean wasPressed = pressed;
        pressed = false;
        if (clicked && wasPressed && onClick != null) {
            onClick.run();
        }
    }

    @Override
    public void render(UiRenderContext ctx) {
        if (pressed) {
            background(pressR, pressG, pressB, pressA);
        } else if (hovered) {
            background(hoverR, hoverG, hoverB, hoverA);
        } else {
            background(normalR, normalG, normalB, normalA);
        }
        super.render(ctx);
    }
}

