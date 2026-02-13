package engine.strata.util.math;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.concurrent.atomic.AtomicLong;

public final class RandomSeed {
    public static final long GOLDEN_RATIO_64 = -7046029254386353131L;
    public static final long SILVER_RATIO_64 = 7640891576956012809L;
    private static final AtomicLong SEED_UNIQUIFIER = new AtomicLong(8682522807148012L);

    /**
     * Stafford's Mix13. Used to spread entropy of a long seed across all bits.
     */
    public static long mixStafford13(long seed) {
        seed = (seed ^ seed >>> 30) * -4658895280553007687L;
        seed = (seed ^ seed >>> 27) * -7723592293110705685L;
        return seed ^ seed >>> 31;
    }

    public static XoroshiroSeed createXoroshiroSeed(long seed) {
        long l = seed ^ SILVER_RATIO_64;
        long m = l + GOLDEN_RATIO_64;
        return new XoroshiroSeed(mixStafford13(l), mixStafford13(m));
    }

    public static XoroshiroSeed createXoroshiroSeed(String seedName) {
        try {
            MessageDigest md5 = MessageDigest.getInstance("MD5");
            byte[] bs = md5.digest(seedName.getBytes(StandardCharsets.UTF_8));
            long l = ((long)bs[0] << 56) | ((long)(bs[1] & 255) << 48) | ((long)(bs[2] & 255) << 40) | ((long)(bs[3] & 255) << 32) | ((long)(bs[4] & 255) << 24) | ((long)(bs[5] & 255) << 16) | ((long)(bs[6] & 255) << 8) | (long)(bs[7] & 255);
            long m = ((long)bs[8] << 56) | ((long)(bs[9] & 255) << 48) | ((long)(bs[10] & 255) << 40) | ((long)(bs[11] & 255) << 32) | ((long)(bs[12] & 255) << 24) | ((long)(bs[13] & 255) << 16) | ((long)(bs[14] & 255) << 8) | (long)(bs[15] & 255);
            return new XoroshiroSeed(l, m);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("MD5 not found", e);
        }
    }

    public static long getSeed() {
        return SEED_UNIQUIFIER.updateAndGet(s -> s * 1181783497276652981L) ^ System.nanoTime();
    }

    public record XoroshiroSeed(long seedLo, long seedHi) {
        public XoroshiroSeed mix() {
            return new XoroshiroSeed(mixStafford13(seedLo), mixStafford13(seedHi));
        }
    }
}