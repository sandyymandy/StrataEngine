package engine.strata.util.math;

import java.util.UUID;

/**
 * Comprehensive math utility class for game development.
 * Provides interpolation, coordinate conversion, clamping, and various helper functions.
 */
public class Math {

    // ==================== Constants ====================

    public static final double PI = java.lang.Math.PI;
    public static final double TAU = PI * 2.0;
    public static final float PI_F = (float) PI;
    public static final float TAU_F = (float) TAU;
    public static final double EPSILON = 1e-6;
    public static final float EPSILON_F = 1e-5f;

    // ==================== Interpolation ====================

    /**
     * Linear interpolation between two values.
     * @param start Starting value
     * @param end Ending value
     * @param alpha Interpolation factor (0.0 to 1.0)
     */
    public static double lerp(double start, double end, double alpha) {
        return start + (end - start) * alpha;
    }

    /**
     * Linear interpolation between two values (float version).
     */
    public static float lerp(float start, float end, float alpha) {
        return start + (end - start) * alpha;
    }

    /**
     * Linear interpolation returning float from double inputs.
     */
    public static float fLerp(double start, double end, float alpha) {
        return (float) (start + (end - start) * alpha);
    }

    /**
     * Interpolates between angles, taking the shortest path around the circle.
     * Handles wrap-around at 360 degrees.
     * @param start Starting angle in degrees
     * @param end Ending angle in degrees
     * @param alpha Interpolation factor (0.0 to 1.0)
     */
    public static float lerpAngle(float start, float end, float alpha) {
        float delta = ((end - start) % 360 + 540) % 360 - 180;
        return start + delta * alpha;
    }

    /**
     * Smooth interpolation using smoothstep function.
     * Results in ease-in-ease-out curve.
     */
    public static double smoothstep(double start, double end, double alpha) {
        alpha = clamp(alpha, 0.0, 1.0);
        double t = alpha * alpha * (3.0 - 2.0 * alpha);
        return lerp(start, end, t);
    }

    /**
     * Smoother interpolation using smootherstep function.
     * Results in smoother ease-in-ease-out than smoothstep.
     */
    public static double smootherstep(double start, double end, double alpha) {
        alpha = clamp(alpha, 0.0, 1.0);
        double t = alpha * alpha * alpha * (alpha * (alpha * 6.0 - 15.0) + 10.0);
        return lerp(start, end, t);
    }

    /**
     * Inverse lerp - finds the alpha value for a given position between start and end.
     */
    public static double inverseLerp(double start, double end, double value) {
        if (java.lang.Math.abs(end - start) < EPSILON) return 0.0;
        return (value - start) / (end - start);
    }

    // ==================== Clamping ====================

    /**
     * Clamps a value between min and max.
     */
    public static double clamp(double value, double min, double max) {
        return java.lang.Math.max(min, java.lang.Math.min(max, value));
    }

    /**
     * Clamps a value between min and max (float version).
     */
    public static float clamp(float value, float min, float max) {
        return java.lang.Math.max(min, java.lang.Math.min(max, value));
    }

    /**
     * Clamps a value between min and max (int version).
     */
    public static int clamp(int value, int min, int max) {
        return java.lang.Math.max(min, java.lang.Math.min(max, value));
    }

    /**
     * Clamps a value between 0 and 1.
     */
    public static double clamp01(double value) {
        return clamp(value, 0.0, 1.0);
    }

    /**
     * Clamps a value between 0 and 1 (float version).
     */
    public static float clamp01(float value) {
        return clamp(value, 0.0f, 1.0f);
    }

    // ==================== Coordinate Conversion ====================

    /**
     * Converts world coordinates to block coordinates using proper floor division.
     * This is critical for correct handling of negative coordinates.
     *
     * @param worldCoord World coordinate (can be negative)
     * @return Block coordinate
     */
    public static int worldToBlock(double worldCoord) {
        return (int) java.lang.Math.floor(worldCoord);
    }

    /**
     * Converts block coordinates to chunk coordinates using floor division.
     * Handles negative coordinates correctly.
     *
     * @param blockCoord Block coordinate
     * @param chunkSize Size of chunks (typically 16 or 32)
     * @return Chunk coordinate
     */
    public static int blockToChunk(int blockCoord, int chunkSize) {
        return java.lang.Math.floorDiv(blockCoord, chunkSize);
    }

    /**
     * Gets the local coordinate within a chunk (0 to chunkSize-1).
     * Handles negative coordinates correctly.
     *
     * @param blockCoord Block coordinate
     * @param chunkSize Size of chunks (typically 16 or 32)
     * @return Local coordinate within chunk
     */
    public static int blockToLocal(int blockCoord, int chunkSize) {
        return java.lang.Math.floorMod(blockCoord, chunkSize);
    }

    /**
     * Converts chunk coordinates to world block coordinates.
     *
     * @param chunkCoord Chunk coordinate
     * @param chunkSize Size of chunks
     * @return Block coordinate at the start of the chunk
     */
    public static int chunkToBlock(int chunkCoord, int chunkSize) {
        return chunkCoord * chunkSize;
    }

    // ==================== Angle Utilities ====================

    /**
     * Converts degrees to radians.
     */
    public static double toRadians(double degrees) {
        return degrees * PI / 180.0;
    }

    /**
     * Converts degrees to radians (float version).
     */
    public static float toRadians(float degrees) {
        return degrees * PI_F / 180.0f;
    }

    /**
     * Converts radians to degrees.
     */
    public static double toDegrees(double radians) {
        return radians * 180.0 / PI;
    }

    /**
     * Converts radians to degrees (float version).
     */
    public static float toDegrees(float radians) {
        return radians * 180.0f / PI_F;
    }

    /**
     * Normalizes an angle to the range [0, 360).
     */
    public static float normalizeAngle(float angle) {
        angle = angle % 360.0f;
        if (angle < 0) angle += 360.0f;
        return angle;
    }

    /**
     * Normalizes an angle to the range [-180, 180).
     */
    public static float normalizeAngleSigned(float angle) {
        angle = normalizeAngle(angle);
        if (angle >= 180.0f) angle -= 360.0f;
        return angle;
    }

    /**
     * Calculates the shortest angular distance between two angles.
     * Result is in range [-180, 180].
     */
    public static float angleDifference(float angle1, float angle2) {
        float diff = normalizeAngleSigned(angle2 - angle1);
        return diff;
    }

    // ==================== Distance & Geometry ====================

    /**
     * Calculates squared distance between two 2D points.
     * Faster than distance() as it avoids sqrt.
     */
    public static double distanceSquared(double x1, double y1, double x2, double y2) {
        double dx = x2 - x1;
        double dy = y2 - y1;
        return dx * dx + dy * dy;
    }

    /**
     * Calculates distance between two 2D points.
     */
    public static double distance(double x1, double y1, double x2, double y2) {
        return java.lang.Math.sqrt(distanceSquared(x1, y1, x2, y2));
    }

    /**
     * Calculates squared distance between two 3D points.
     * Faster than distance() as it avoids sqrt.
     */
    public static double distanceSquared(double x1, double y1, double z1,
                                         double x2, double y2, double z2) {
        double dx = x2 - x1;
        double dy = y2 - y1;
        double dz = z2 - z1;
        return dx * dx + dy * dy + dz * dz;
    }

    /**
     * Calculates distance between two 3D points.
     */
    public static double distance(double x1, double y1, double z1,
                                  double x2, double y2, double z2) {
        return java.lang.Math.sqrt(distanceSquared(x1, y1, z1, x2, y2, z2));
    }

    /**
     * Calculates Manhattan distance between two 2D points.
     */
    public static int manhattanDistance(int x1, int y1, int x2, int y2) {
        return java.lang.Math.abs(x2 - x1) + java.lang.Math.abs(y2 - y1);
    }

    /**
     * Calculates Manhattan distance between two 3D points.
     */
    public static int manhattanDistance(int x1, int y1, int z1, int x2, int y2, int z2) {
        return java.lang.Math.abs(x2 - x1) +
                java.lang.Math.abs(y2 - y1) +
                java.lang.Math.abs(z2 - z1);
    }

    // ==================== Comparison Utilities ====================

    /**
     * Checks if two doubles are approximately equal within epsilon.
     */
    public static boolean approximately(double a, double b) {
        return java.lang.Math.abs(a - b) < EPSILON;
    }

    /**
     * Checks if two floats are approximately equal within epsilon.
     */
    public static boolean approximately(float a, float b) {
        return java.lang.Math.abs(a - b) < EPSILON_F;
    }

    /**
     * Checks if a value is approximately zero.
     */
    public static boolean isZero(double value) {
        return java.lang.Math.abs(value) < EPSILON;
    }

    /**
     * Checks if a value is approximately zero (float version).
     */
    public static boolean isZero(float value) {
        return java.lang.Math.abs(value) < EPSILON_F;
    }

    // ==================== Rounding & Snapping ====================

    /**
     * Rounds to the nearest integer.
     */
    public static int round(double value) {
        return (int) java.lang.Math.round(value);
    }

    /**
     * Rounds to the nearest integer (float version).
     */
    public static int round(float value) {
        return java.lang.Math.round(value);
    }

    /**
     * Floors a value (always rounds down).
     */
    public static int floor(double value) {
        return (int) java.lang.Math.floor(value);
    }

    /**
     * Floors a value (float version).
     */
    public static int floor(float value) {
        return (int) java.lang.Math.floor(value);
    }

    /**
     * Ceils a value (always rounds up).
     */
    public static int ceil(double value) {
        return (int) java.lang.Math.ceil(value);
    }

    /**
     * Ceils a value (float version).
     */
    public static int ceil(float value) {
        return (int) java.lang.Math.ceil(value);
    }

    /**
     * Snaps a value to the nearest multiple of step.
     */
    public static double snap(double value, double step) {
        return java.lang.Math.round(value / step) * step;
    }

    /**
     * Snaps a value to the nearest multiple of step (float version).
     */
    public static float snap(float value, float step) {
        return java.lang.Math.round(value / step) * step;
    }

    // ==================== Sign & Absolute Value ====================

    /**
     * Returns the sign of a value (-1, 0, or 1).
     */
    public static int sign(double value) {
        if (value > 0) return 1;
        if (value < 0) return -1;
        return 0;
    }

    /**
     * Returns the sign of a value (-1, 0, or 1) (float version).
     */
    public static int sign(float value) {
        if (value > 0) return 1;
        if (value < 0) return -1;
        return 0;
    }

    /**
     * Returns the sign of a value (-1, 0, or 1) (int version).
     */
    public static int sign(int value) {
        if (value > 0) return 1;
        if (value < 0) return -1;
        return 0;
    }

    /**
     * Returns the absolute value.
     */
    public static double abs(double value) {
        return java.lang.Math.abs(value);
    }

    /**
     * Returns the absolute value (float version).
     */
    public static float abs(float value) {
        return java.lang.Math.abs(value);
    }

    /**
     * Returns the absolute value (int version).
     */
    public static int abs(int value) {
        return java.lang.Math.abs(value);
    }

    // ==================== Min/Max ====================

    /**
     * Returns the minimum of two values.
     */
    public static double min(double a, double b) {
        return java.lang.Math.min(a, b);
    }

    /**
     * Returns the minimum of three values.
     */
    public static double min(double a, double b, double c) {
        return java.lang.Math.min(a, java.lang.Math.min(b, c));
    }

    /**
     * Returns the maximum of two values.
     */
    public static double max(double a, double b) {
        return java.lang.Math.max(a, b);
    }

    /**
     * Returns the maximum of three values.
     */
    public static double max(double a, double b, double c) {
        return java.lang.Math.max(a, java.lang.Math.max(b, c));
    }

    // ==================== Power & Roots ====================

    /**
     * Calculates value raised to power.
     */
    public static double pow(double value, double power) {
        return java.lang.Math.pow(value, power);
    }

    /**
     * Calculates square root.
     */
    public static double sqrt(double value) {
        return java.lang.Math.sqrt(value);
    }

    /**
     * Calculates square root (float version).
     */
    public static float sqrt(float value) {
        return (float) java.lang.Math.sqrt(value);
    }

    /**
     * Fast inverse square root approximation.
     * Good for normalization when precision isn't critical.
     */
    public static float fastInvSqrt(float value) {
        float half = 0.5f * value;
        int i = Float.floatToIntBits(value);
        i = 0x5f3759df - (i >> 1);
        value = Float.intBitsToFloat(i);
        value = value * (1.5f - half * value * value);
        return value;
    }

    /**
     * Calculates value squared.
     */
    public static double square(double value) {
        return value * value;
    }

    /**
     * Calculates value squared (float version).
     */
    public static float square(float value) {
        return value * value;
    }

    // ==================== Trigonometry ====================

    /**
     * Calculates sine.
     */
    public static double sin(double radians) {
        return java.lang.Math.sin(radians);
    }

    /**
     * Calculates sine (float version).
     */
    public static float sin(float radians) {
        return (float) java.lang.Math.sin(radians);
    }

    /**
     * Calculates cosine.
     */
    public static double cos(double radians) {
        return java.lang.Math.cos(radians);
    }

    /**
     * Calculates cosine (float version).
     */
    public static float cos(float radians) {
        return (float) java.lang.Math.cos(radians);
    }

    /**
     * Calculates tangent.
     */
    public static double tan(double radians) {
        return java.lang.Math.tan(radians);
    }

    /**
     * Calculates arc sine (result in radians).
     */
    public static double asin(double value) {
        return java.lang.Math.asin(value);
    }

    /**
     * Calculates arc cosine (result in radians).
     */
    public static double acos(double value) {
        return java.lang.Math.acos(value);
    }

    /**
     * Calculates arc tangent (result in radians).
     */
    public static double atan(double value) {
        return java.lang.Math.atan(value);
    }

    /**
     * Calculates arc tangent of y/x (result in radians).
     * Takes into account quadrant.
     */
    public static double atan2(double y, double x) {
        return java.lang.Math.atan2(y, x);
    }

    // ==================== Wrapping & Modulo ====================

    /**
     * Returns the largest integer less than or equal to the algebraic quotient.
     * Useful for coordinate systems where negative values must behave consistently.
     */
    public static int floorDiv(int x, int y) {
        return java.lang.Math.floorDiv(x, y);
    }

    /**
     * Returns the largest integer less than or equal to the algebraic quotient (long version).
     */
    public static long floorDiv(long x, long y) {
        return java.lang.Math.floorDiv(x, y);
    }

    /**
     * Returns the floor modulus of the arguments.
     * The result has the same sign as the divisor and is in the range [0, |y|).
     */
    public static int floorMod(int x, int y) {
        return java.lang.Math.floorMod(x, y);
    }

    /**
     * Returns the floor modulus of the arguments (long version).
     */
    public static long floorMod(long x, long y) {
        return java.lang.Math.floorMod(x, y);
    }

    /**
     * Wraps a value to the range [min, max).
     */
    public static double wrap(double value, double min, double max) {
        double range = max - min;
        return value - range * java.lang.Math.floor((value - min) / range);
    }

    /**
     * Wraps a value to the range [min, max) (float version).
     */
    public static float wrap(float value, float min, float max) {
        float range = max - min;
        return value - range * (float) java.lang.Math.floor((value - min) / range);
    }

    /**
     * Wraps a value to the range [min, max) (int version).
     */
    public static int wrap(int value, int min, int max) {
        int range = max - min;
        return value - range * java.lang.Math.floorDiv(value - min, range);
    }

    /**
     * Proper modulo operation that handles negatives correctly.
     * Unlike % operator, this always returns positive results.
     */
    public static int mod(int value, int modulus) {
        return java.lang.Math.floorMod(value, modulus);
    }

    /**
     * Proper modulo operation (long version).
     */
    public static long mod(long value, long modulus) {
        return java.lang.Math.floorMod(value, modulus);
    }

    // ==================== Easing Functions ====================

    /**
     * Ease-in quadratic.
     */
    public static double easeInQuad(double t) {
        return t * t;
    }

    /**
     * Ease-out quadratic.
     */
    public static double easeOutQuad(double t) {
        return t * (2.0 - t);
    }

    /**
     * Ease-in-out quadratic.
     */
    public static double easeInOutQuad(double t) {
        return t < 0.5 ? 2.0 * t * t : -1.0 + (4.0 - 2.0 * t) * t;
    }

    /**
     * Ease-in cubic.
     */
    public static double easeInCubic(double t) {
        return t * t * t;
    }

    /**
     * Ease-out cubic.
     */
    public static double easeOutCubic(double t) {
        double f = t - 1.0;
        return f * f * f + 1.0;
    }

    /**
     * Ease-in-out cubic.
     */
    public static double easeInOutCubic(double t) {
        return t < 0.5 ? 4.0 * t * t * t : (t - 1.0) * (2.0 * t - 2.0) * (2.0 * t - 2.0) + 1.0;
    }

    // ==================== Random Utilities ====================

    /**
     * Generates a random UUID with proper version 4 bits.
     */
    public static UUID randomUuid(Random random) {
        long l = random.nextLong() & -61441L | 16384L;
        long m = random.nextLong() & 4611686018427387903L | Long.MIN_VALUE;
        return new UUID(l, m);
    }

    /**
     * Generates a random UUID using java.util.Random.
     */
    public static UUID randomUuid(java.util.Random random) {
        long l = random.nextLong() & -61441L | 16384L;
        long m = random.nextLong() & 4611686018427387903L | Long.MIN_VALUE;
        return new UUID(l, m);
    }

    // ==================== Miscellaneous ====================

    /**
     * Checks if a value is a power of two.
     */
    public static boolean isPowerOfTwo(int value) {
        return value > 0 && (value & (value - 1)) == 0;
    }

    /**
     * Rounds up to the next power of two.
     */
    public static int nextPowerOfTwo(int value) {
        if (value <= 0) return 1;
        value--;
        value |= value >> 1;
        value |= value >> 2;
        value |= value >> 4;
        value |= value >> 8;
        value |= value >> 16;
        return value + 1;
    }

    /**
     * Calculates the fractional part of a number.
     */
    public static double fract(double value) {
        return value - java.lang.Math.floor(value);
    }

    /**
     * Calculates the fractional part of a number (float version).
     */
    public static float fract(float value) {
        return value - (float) java.lang.Math.floor(value);
    }

    /**
     * Maps a value from one range to another.
     */
    public static double map(double value, double fromMin, double fromMax,
                             double toMin, double toMax) {
        return toMin + (value - fromMin) * (toMax - toMin) / (fromMax - fromMin);
    }

    /**
     * Maps a value from one range to another (float version).
     */
    public static float map(float value, float fromMin, float fromMax,
                            float toMin, float toMax) {
        return toMin + (value - fromMin) * (toMax - toMin) / (fromMax - fromMin);
    }
}