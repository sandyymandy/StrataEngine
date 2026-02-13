package engine.strata.util.math;

public class Random {
    private long seedLo;
    private long seedHi;

    public Random(long seed) {
        // Use the utility to mix the seed properly
        RandomSeed.XoroshiroSeed mixed = RandomSeed.createXoroshiroSeed(seed);
        this.seedLo = mixed.seedLo();
        this.seedHi = mixed.seedHi();
    }

    public long nextLong() {
        long s0 = this.seedLo;
        long s1 = this.seedHi;

        // The ++ scrambler
        long result = Long.rotateLeft(s0 + s1, 17) + s0;

        // The Xoroshiro128 engine
        s1 ^= s0;
        this.seedLo = Long.rotateLeft(s0, 49) ^ s1 ^ (s1 << 21);
        this.seedHi = Long.rotateLeft(s1, 28);

        return result;
    }

    public int nextInt(int bound) {
        if (bound <= 0) throw new IllegalArgumentException("Bound must be positive");
        long r = (nextLong() >>> 33) * bound;
        return (int)(r >> 31);
    }

    public float nextFloat() {
        return (nextLong() >>> 40) * 5.9604645E-8F;
    }

    public double nextDouble() {
        return (nextLong() >>> 11) * 1.1102230246251565E-16;
    }
}