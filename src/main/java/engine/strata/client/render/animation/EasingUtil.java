package engine.strata.client.render.animation;

/**
 * Easing functions based on easings.net
 * All functions take a normalized time value (0.0 to 1.0) and return an eased value.
 */
public class EasingUtil {

    /**
     * Applies an easing function to a normalized time value.
     *
     * @param t      Normalized time (0.0 to 1.0)
     * @param easing The easing function to apply
     * @return Eased value
     */
    public static float ease(float t, StrataAnimation.EasingFunction easing) {
        return switch (easing) {
            case LINEAR -> linear(t);

            case EASE_IN_SINE -> easeInSine(t);
            case EASE_OUT_SINE -> easeOutSine(t);
            case EASE_IN_OUT_SINE -> easeInOutSine(t);

            case EASE_IN_QUAD -> easeInQuad(t);
            case EASE_OUT_QUAD -> easeOutQuad(t);
            case EASE_IN_OUT_QUAD -> easeInOutQuad(t);

            case EASE_IN_CUBIC -> easeInCubic(t);
            case EASE_OUT_CUBIC -> easeOutCubic(t);
            case EASE_IN_OUT_CUBIC -> easeInOutCubic(t);

            case EASE_IN_QUART -> easeInQuart(t);
            case EASE_OUT_QUART -> easeOutQuart(t);
            case EASE_IN_OUT_QUART -> easeInOutQuart(t);

            case EASE_IN_QUINT -> easeInQuint(t);
            case EASE_OUT_QUINT -> easeOutQuint(t);
            case EASE_IN_OUT_QUINT -> easeInOutQuint(t);

            case EASE_IN_EXPO -> easeInExpo(t);
            case EASE_OUT_EXPO -> easeOutExpo(t);
            case EASE_IN_OUT_EXPO -> easeInOutExpo(t);

            case EASE_IN_CIRC -> easeInCirc(t);
            case EASE_OUT_CIRC -> easeOutCirc(t);
            case EASE_IN_OUT_CIRC -> easeInOutCirc(t);

            case EASE_IN_BACK -> easeInBack(t);
            case EASE_OUT_BACK -> easeOutBack(t);
            case EASE_IN_OUT_BACK -> easeInOutBack(t);

            case EASE_IN_ELASTIC -> easeInElastic(t);
            case EASE_OUT_ELASTIC -> easeOutElastic(t);
            case EASE_IN_OUT_ELASTIC -> easeInOutElastic(t);

            case EASE_IN_BOUNCE -> easeInBounce(t);
            case EASE_OUT_BOUNCE -> easeOutBounce(t);
            case EASE_IN_OUT_BOUNCE -> easeInOutBounce(t);
        };
    }

    // Linear
    private static float linear(float t) {
        return t;
    }

    // Sine easing functions
    private static float easeInSine(float t) {
        return 1 - (float) Math.cos((t * Math.PI) / 2);
    }

    private static float easeOutSine(float t) {
        return (float) Math.sin((t * Math.PI) / 2);
    }

    private static float easeInOutSine(float t) {
        return -(float) (Math.cos(Math.PI * t) - 1) / 2;
    }

    // Quad easing functions
    private static float easeInQuad(float t) {
        return t * t;
    }

    private static float easeOutQuad(float t) {
        return 1 - (1 - t) * (1 - t);
    }

    private static float easeInOutQuad(float t) {
        return t < 0.5f ? 2 * t * t : 1 - (float) Math.pow(-2 * t + 2, 2) / 2;
    }

    // Cubic easing functions
    private static float easeInCubic(float t) {
        return t * t * t;
    }

    private static float easeOutCubic(float t) {
        return 1 - (float) Math.pow(1 - t, 3);
    }

    private static float easeInOutCubic(float t) {
        return t < 0.5f ? 4 * t * t * t : 1 - (float) Math.pow(-2 * t + 2, 3) / 2;
    }

    // Quart easing functions
    private static float easeInQuart(float t) {
        return t * t * t * t;
    }

    private static float easeOutQuart(float t) {
        return 1 - (float) Math.pow(1 - t, 4);
    }

    private static float easeInOutQuart(float t) {
        return t < 0.5f ? 8 * t * t * t * t : 1 - (float) Math.pow(-2 * t + 2, 4) / 2;
    }

    // Quint easing functions
    private static float easeInQuint(float t) {
        return t * t * t * t * t;
    }

    private static float easeOutQuint(float t) {
        return 1 - (float) Math.pow(1 - t, 5);
    }

    private static float easeInOutQuint(float t) {
        return t < 0.5f ? 16 * t * t * t * t * t : 1 - (float) Math.pow(-2 * t + 2, 5) / 2;
    }

    // Expo easing functions
    private static float easeInExpo(float t) {
        return t == 0 ? 0 : (float) Math.pow(2, 10 * t - 10);
    }

    private static float easeOutExpo(float t) {
        return t == 1 ? 1 : 1 - (float) Math.pow(2, -10 * t);
    }

    private static float easeInOutExpo(float t) {
        if (t == 0) return 0;
        if (t == 1) return 1;
        return t < 0.5f
                ? (float) Math.pow(2, 20 * t - 10) / 2
                : (2 - (float) Math.pow(2, -20 * t + 10)) / 2;
    }

    // Circ easing functions
    private static float easeInCirc(float t) {
        return 1 - (float) Math.sqrt(1 - Math.pow(t, 2));
    }

    private static float easeOutCirc(float t) {
        return (float) Math.sqrt(1 - Math.pow(t - 1, 2));
    }

    private static float easeInOutCirc(float t) {
        return t < 0.5f
                ? (1 - (float) Math.sqrt(1 - Math.pow(2 * t, 2))) / 2
                : ((float) Math.sqrt(1 - Math.pow(-2 * t + 2, 2)) + 1) / 2;
    }

    // Back easing functions (overshoots)
    private static final float c1 = 1.70158f;
    private static final float c2 = c1 * 1.525f;
    private static final float c3 = c1 + 1;

    private static float easeInBack(float t) {
        return c3 * t * t * t - c1 * t * t;
    }

    private static float easeOutBack(float t) {
        return 1 + c3 * (float) Math.pow(t - 1, 3) + c1 * (float) Math.pow(t - 1, 2);
    }

    private static float easeInOutBack(float t) {
        return t < 0.5f
                ? ((float) Math.pow(2 * t, 2) * ((c2 + 1) * 2 * t - c2)) / 2
                : ((float) Math.pow(2 * t - 2, 2) * ((c2 + 1) * (t * 2 - 2) + c2) + 2) / 2;
    }

    // Elastic easing functions (springs)
    private static final float c4 = (float) ((2 * Math.PI) / 3);
    private static final float c5 = (float) ((2 * Math.PI) / 4.5);

    private static float easeInElastic(float t) {
        if (t == 0) return 0;
        if (t == 1) return 1;
        return -(float) Math.pow(2, 10 * t - 10) * (float) Math.sin((t * 10 - 10.75) * c4);
    }

    private static float easeOutElastic(float t) {
        if (t == 0) return 0;
        if (t == 1) return 1;
        return (float) Math.pow(2, -10 * t) * (float) Math.sin((t * 10 - 0.75) * c4) + 1;
    }

    private static float easeInOutElastic(float t) {
        if (t == 0) return 0;
        if (t == 1) return 1;
        return t < 0.5f
                ? -((float) Math.pow(2, 20 * t - 10) * (float) Math.sin((20 * t - 11.125) * c5)) / 2
                : ((float) Math.pow(2, -20 * t + 10) * (float) Math.sin((20 * t - 11.125) * c5)) / 2 + 1;
    }

    // Bounce easing functions
    private static float easeOutBounce(float t) {
        final float n1 = 7.5625f;
        final float d1 = 2.75f;

        if (t < 1 / d1) {
            return n1 * t * t;
        } else if (t < 2 / d1) {
            return n1 * (t -= 1.5f / d1) * t + 0.75f;
        } else if (t < 2.5 / d1) {
            return n1 * (t -= 2.25f / d1) * t + 0.9375f;
        } else {
            return n1 * (t -= 2.625f / d1) * t + 0.984375f;
        }
    }

    private static float easeInBounce(float t) {
        return 1 - easeOutBounce(1 - t);
    }

    private static float easeInOutBounce(float t) {
        return t < 0.5f
                ? (1 - easeOutBounce(1 - 2 * t)) / 2
                : (1 + easeOutBounce(2 * t - 1)) / 2;
    }
}