package engine.helios.rendering.vertex;

public enum VertexFormat {
    // ── Immediate-mode / Tessellator formats ──────────────────────────────────

    /** 3 floats pos + 4 floats color */
    POSITION_COLOR(3, 0, 4, 0, 0),

    /** 3 floats pos + 2 floats tex + 4 floats color */
    POSITION_TEXTURE_COLOR(3, 2, 4, 0, 0),

    /** 3 floats pos + 2 floats tex + 1 float layer + 1 float brightness (chunk atlas) */
    POSITION_TEXTURE_LAYER_BRIGHTNESS(3, 2, 0, 1, 1),

    // ── Static VBO format (GPU-side model rendering) ──────────────────────────

    /**
     * 3 floats pos + 2 floats tex, NO per-vertex colour.
     *
     * Used by {@code GpuModelCache} when baking {@code StrataMeshData} into
     * a static VAO/VBO.  The colour tint is supplied as a {@code u_Tint} shader
     * uniform so the same geometry can be re-drawn at different tints without
     * re-uploading any vertex data.
     *
     * Attribute layout expected by {@code vertex.vert}:
     *   location 0 → a_Position (3 floats)
     *   location 1 → a_TexCoord (2 floats)
     */
    POSITION_TEXTURE(3, 2, 0, 0, 0);

    // ── Fields ────────────────────────────────────────────────────────────────

    public final int posCount;
    public final int texCount;
    public final int colorCount;
    public final int layerCount;
    public final int brightnessCount;

    /** Byte stride between consecutive vertices. */
    public final int stride;

    VertexFormat(int pos, int tex, int color, int layer, int brightness) {
        this.posCount        = pos;
        this.texCount        = tex;
        this.colorCount      = color;
        this.layerCount      = layer;
        this.brightnessCount = brightness;
        this.stride = (pos + tex + color + layer + brightness) * Float.BYTES;
    }
}