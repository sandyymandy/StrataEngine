package engine.strata.core;

public class StrataVersion {
    // The version of the engine itself
    public static final String VERSION = "1.0.0-alpha";

    // The API version (Mods check against this for compatibility)
    public static final int API_VERSION = 1;

    // Optional: useful for the window title or logs
    public static final String BRANDING = "Strata Engine";

    public static String getFullName() {
        return BRANDING + " " + VERSION + " (API " + API_VERSION + ")";
    }

    /**
     * Simple check to see if a mod's required API is supported.
     * Logic: If mod is built for API 2, and we are API 1, return false.
     */
    public static boolean isCompatible(int requiredApi) {
        return API_VERSION >= requiredApi;
    }
}