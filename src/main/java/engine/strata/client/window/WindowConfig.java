package engine.strata.client.window;

public class WindowConfig {
    public int width;
    public int height;
    public int x;
    public int y;
    public String title;
    public WindowMode mode;

    public WindowConfig(int width, int height, String title, int x, int y, WindowMode mode) {
        this.width = width;
        this.height = height;
        this.title = title;
        this.x = x;
        this.y = y;
        this.mode = mode;
    }

    public WindowConfig(int width, int height, String title) {
        this(width, height, title, 100, 100, WindowMode.WINDOWED);
    }

    public WindowConfig(int width, int height, String title, WindowMode mode) {
        this(width, height, title, 100, 100, mode);
    }
}
