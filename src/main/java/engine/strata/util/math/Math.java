package engine.strata.util.math;

import java.util.UUID;

public class Math {

    public static double lerp(double start, double end, float alpha) {
        return start + (end - start) * alpha;
    }

    public static float lerpAngle(float start, float end, float alpha) {
        float delta = ((end - start) % 360 + 540) % 360 - 180;
        return start + delta * alpha;
    }

    public static float fLerp(double start, double end, float alpha) {
        return (float) (start + (end - start) * alpha);
    }


    public static UUID randomUuid(Random random) {
        long l = random.nextLong() & -61441L | 16384L;
        long m = random.nextLong() & 4611686018427387903L | Long.MIN_VALUE;
        return new UUID(l, m);
    }
}
