package engine.helios;

public enum VertexFormat {
    // 3 floats pos, 4 floats color
    POSITION_COLOR(3, 0, 4),
    // 3 floats pos, 2 floats tex, 4 floats color
    POSITION_TEXTURE_COLOR(3, 2, 4);

    public final int posCount;
    public final int texCount;
    public final int colorCount;
    public final int stride;

    VertexFormat(int pos, int tex, int color) {
        this.posCount = pos;
        this.texCount = tex;
        this.colorCount = color;
        this.stride = (pos + tex + color) * Float.BYTES;
    }
}