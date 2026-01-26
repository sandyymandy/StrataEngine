package engine.strata.util.math;

public class Math {

    public static double lerp(double start, double end, float alpha) {
        return start + (end - start) * alpha;
    }

    public static float lerpAngle(float start, float end, float alpha) {
        float delta = ((end - start) % 360 + 540) % 360 - 180;
        return start + delta * alpha;
    }
}
