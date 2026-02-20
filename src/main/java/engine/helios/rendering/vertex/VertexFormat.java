package engine.helios.rendering.vertex;

public enum VertexFormat {
    // 3 floats pos, 4 floats color
    POSITION_COLOR(3, 0, 4, 0, 0),
    // 3 floats pos, 2 floats tex, 4 floats color
    POSITION_TEXTURE_COLOR(3, 2, 4, 0, 0),
    // 3 floats pos, 2 floats tex, 1 float layer, 1 float brightness
    POSITION_TEXTURE_LAYER_BRIGHTNESS(3, 2, 0, 1, 1);

    public final int posCount;
    public final int texCount;
    public final int colorCount;
    public final int layerCount;
    public final int brightnessCount;
    public final int stride;

    VertexFormat(int pos, int tex, int color, int layer, int brightness) {
        this.posCount = pos;
        this.texCount = tex;
        this.colorCount = color;
        this.layerCount = layer;
        this.brightnessCount = brightness;
        this.stride = (pos + tex + color + layer + brightness) * Float.BYTES;
    }
}