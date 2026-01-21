package engine.strata.world;

public class Chunk {
    public static final int SIZE = 32;
    // 1D array is much faster for the CPU than [32][32][32]
    private final short[] data = new short[SIZE * SIZE * SIZE];

    public void setBlock(int x, int y, int z, short blockId) {
        data[x | (y << 5) | (z << 10)] = blockId;
    }

    public short getBlock(int x, int y, int z) {
        return data[x | (y << 5) | (z << 10)];
    }
}
