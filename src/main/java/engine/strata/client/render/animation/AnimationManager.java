package engine.strata.client.render.animation;

import engine.strata.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

/**
 * Manages loading and caching of animations.
 * Similar to ModelManager but for .stranim files.
 */
public class AnimationManager {
    private static final Logger LOGGER = LoggerFactory.getLogger("AnimationManager");
    private static final Map<Identifier, StrataAnimation> ANIMATIONS = new HashMap<>();

    /**
     * Gets an animation container, loading it if necessary.
     */
    public static StrataAnimation getAnimation(Identifier id) {
        return ANIMATIONS.computeIfAbsent(id, StrataAnimationLoader::load);
    }

    /**
     * Clears all cached animations (useful for resource reloading).
     */
    public static void clearCache() {
        ANIMATIONS.clear();
        LOGGER.info("Cleared animation cache");
    }

    /**
     * Preloads an animation into the cache.
     */
    public static void preload(Identifier animationId) {
        getAnimation(animationId);
        LOGGER.info("Preloaded animation: {}", animationId);
    }

    /**
     * Checks if an animation is loaded in the cache.
     */
    public static boolean isLoaded(Identifier id) {
        return ANIMATIONS.containsKey(id);
    }

    /**
     * Gets the number of cached animations.
     */
    public static int getCacheSize() {
        return ANIMATIONS.size();
    }
}