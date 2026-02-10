package engine.strata.client.render.animation;

import com.google.gson.*;
import engine.strata.core.io.ResourceManager;
import engine.strata.util.Identifier;
import org.joml.Vector3f;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

import static org.joml.Math.toRadians;

/**
 * Loads .stranim files containing multiple animations for a model.
 */
public class StrataAnimationLoader {
    private static final Logger LOGGER = LoggerFactory.getLogger("AnimationLoader");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    /**
     * Loads an animation container from a .stranim file.
     */
    public static StrataAnimation load(Identifier id) {
        LOGGER.info("Loading animation container: {}", id);

        try {
            String json = ResourceManager.loadAsString(id, "animations", "stranim");

            if (json == null || json.isEmpty()) {
                LOGGER.error("Animation file is null or empty: {}", id);
                return createFallbackAnimation(id);
            }

            JsonObject root = GSON.fromJson(json, JsonObject.class);

            // Parse container metadata
            Identifier animId = Identifier.of(root.get("id").getAsString());
            int version = root.has("format_version") ? root.get("format_version").getAsInt() : 1;

            if (version > 1) {
                LOGGER.warn("Animation format version {} not fully supported, attempting to load: {}", version, id);
            }

            // Parse all animations in the container
            Map<String, StrataAnimation.AnimationData> animations = new HashMap<>();
            JsonObject animsObj = root.getAsJsonObject("animations");

            if (animsObj == null || animsObj.size() == 0) {
                LOGGER.error("No animations defined in: {}", id);
                return createFallbackAnimation(id);
            }

            for (Map.Entry<String, JsonElement> entry : animsObj.entrySet()) {
                String animName = entry.getKey();
                JsonObject animObj = entry.getValue().getAsJsonObject();

                StrataAnimation.AnimationData animData = parseAnimation(animName, animObj);
                if (animData != null) {
                    animations.put(animName, animData);
                    LOGGER.debug("Loaded animation: {} (duration: {}s)", animName, animData.duration());
                }
            }

            if (animations.isEmpty()) {
                LOGGER.error("No valid animations loaded from: {}", id);
                return createFallbackAnimation(id);
            }

            LOGGER.info("Successfully loaded {} animation(s) from: {}", animations.size(), id);
            return new StrataAnimation(animId, version, animations);

        } catch (Exception e) {
            LOGGER.error("Failed to load animation: {}", id, e);
            return createFallbackAnimation(id);
        }
    }

    /**
     * Parses a single animation from JSON.
     */
    private static StrataAnimation.AnimationData parseAnimation(String name, JsonObject obj) {
        try {
            float duration = obj.get("duration").getAsFloat();
            boolean loop = obj.has("loop") && obj.get("loop").getAsBoolean();

            // Parse loop mode
            StrataAnimation.LoopMode loopMode = StrataAnimation.LoopMode.LOOP;
            if (obj.has("loop_mode")) {
                String mode = obj.get("loop_mode").getAsString().toUpperCase();
                loopMode = StrataAnimation.LoopMode.valueOf(mode);
            }

            // Parse blend durations
            float blendIn = obj.has("blend_in") ? obj.get("blend_in").getAsFloat() : 0.0f;
            float blendOut = obj.has("blend_out") ? obj.get("blend_out").getAsFloat() : 0.0f;

            // Parse bone animations
            Map<String, StrataAnimation.BoneAnimation> boneAnims = new HashMap<>();
            JsonObject bonesObj = obj.getAsJsonObject("bones");

            if (bonesObj != null) {
                for (Map.Entry<String, JsonElement> entry : bonesObj.entrySet()) {
                    String boneName = entry.getKey();
                    JsonObject boneObj = entry.getValue().getAsJsonObject();

                    StrataAnimation.BoneAnimation boneAnim = parseBoneAnimation(boneObj);
                    if (boneAnim != null) {
                        boneAnims.put(boneName, boneAnim);
                    }
                }
            }

            // Parse events
            List<StrataAnimation.AnimationEvent> events = new ArrayList<>();
            JsonArray eventsArray = obj.getAsJsonArray("events");

            if (eventsArray != null) {
                for (JsonElement elem : eventsArray) {
                    JsonObject eventObj = elem.getAsJsonObject();
                    StrataAnimation.AnimationEvent event = parseEvent(eventObj);
                    if (event != null) {
                        events.add(event);
                    }
                }
            }

            return new StrataAnimation.AnimationData(
                    name, duration, loop, loopMode, blendIn, blendOut, boneAnims, events
            );

        } catch (Exception e) {
            LOGGER.error("Failed to parse animation: {}", name, e);
            return null;
        }
    }

    /**
     * Parses bone animation data (rotation, translation, scale tracks).
     */
    private static StrataAnimation.BoneAnimation parseBoneAnimation(JsonObject obj) {
        try {
            List<StrataAnimation.RotationKeyframe> rotation = null;
            List<StrataAnimation.TranslationKeyframe> translation = null;
            List<StrataAnimation.ScaleKeyframe> scale = null;

            // Parse rotation keyframes
            if (obj.has("rotation")) {
                rotation = new ArrayList<>();
                JsonArray rotArray = obj.getAsJsonArray("rotation");

                for (JsonElement elem : rotArray) {
                    JsonObject kf = elem.getAsJsonObject();
                    float time = kf.get("time").getAsFloat();
                    Vector3f rot = parseVector3fToRadians(kf.getAsJsonArray("value"));
                    StrataAnimation.EasingFunction easing = parseEasing(kf);

                    rotation.add(new StrataAnimation.RotationKeyframe(time, rot, easing));
                }
            }

            // Parse translation keyframes
            if (obj.has("translation")) {
                translation = new ArrayList<>();
                JsonArray transArray = obj.getAsJsonArray("translation");

                for (JsonElement elem : transArray) {
                    JsonObject kf = elem.getAsJsonObject();
                    float time = kf.get("time").getAsFloat();
                    Vector3f trans = parseVector3f(kf.getAsJsonArray("value"));
                    StrataAnimation.EasingFunction easing = parseEasing(kf);

                    translation.add(new StrataAnimation.TranslationKeyframe(time, trans, easing));
                }
            }

            // Parse scale keyframes
            if (obj.has("scale")) {
                scale = new ArrayList<>();
                JsonArray scaleArray = obj.getAsJsonArray("scale");

                for (JsonElement elem : scaleArray) {
                    JsonObject kf = elem.getAsJsonObject();
                    float time = kf.get("time").getAsFloat();
                    Vector3f scl = parseVector3f(kf.getAsJsonArray("value"));
                    StrataAnimation.EasingFunction easing = parseEasing(kf);

                    scale.add(new StrataAnimation.ScaleKeyframe(time, scl, easing));
                }
            }

            // Only create bone animation if at least one track exists
            if (rotation != null || translation != null || scale != null) {
                return new StrataAnimation.BoneAnimation(rotation, translation, scale);
            }

            return null;

        } catch (Exception e) {
            LOGGER.error("Failed to parse bone animation", e);
            return null;
        }
    }

    /**
     * Parses an animation event.
     */
    private static StrataAnimation.AnimationEvent parseEvent(JsonObject obj) {
        try {
            float timestamp = obj.get("time").getAsFloat();
            String type = obj.get("type").getAsString();

            Map<String, String> params = new HashMap<>();
            if (obj.has("parameters")) {
                JsonObject paramsObj = obj.getAsJsonObject("parameters");
                for (Map.Entry<String, JsonElement> entry : paramsObj.entrySet()) {
                    params.put(entry.getKey(), entry.getValue().getAsString());
                }
            }

            return new StrataAnimation.AnimationEvent(timestamp, type, params);

        } catch (Exception e) {
            LOGGER.error("Failed to parse animation event", e);
            return null;
        }
    }

    /**
     * Parses easing function from keyframe JSON.
     */
    private static StrataAnimation.EasingFunction parseEasing(JsonObject kf) {
        if (!kf.has("easing")) {
            return StrataAnimation.EasingFunction.LINEAR;
        }

        String easingStr = kf.get("easing").getAsString().toUpperCase();

        try {
            return StrataAnimation.EasingFunction.valueOf(easingStr);
        } catch (IllegalArgumentException e) {
            LOGGER.warn("Unknown easing function: {}, using LINEAR", easingStr);
            return StrataAnimation.EasingFunction.LINEAR;
        }
    }

    /**
     * Parses a Vector3f from JSON array.
     */
    private static Vector3f parseVector3f(JsonArray array) {
        if (array == null || array.size() != 3) {
            return new Vector3f(0, 0, 0);
        }
        return new Vector3f(
                array.get(0).getAsFloat(),
                array.get(1).getAsFloat(),
                array.get(2).getAsFloat()
        );
    }

    /**
     * Parses a Vector3f from JSON and converts to radians.
     */
    private static Vector3f parseVector3fToRadians(JsonArray array) {
        if (array == null || array.size() != 3) {
            return new Vector3f(0, 0, 0);
        }
        return new Vector3f(
                 toRadians(array.get(0).getAsFloat()),
                toRadians(array.get(1).getAsFloat()),
                toRadians(array.get(2).getAsFloat())
        );
    }

    /**
     * Creates a fallback idle animation.
     */
    private static StrataAnimation createFallbackAnimation(Identifier id) {
        LOGGER.warn("Creating fallback animation for: {}", id);

        Map<String, StrataAnimation.AnimationData> animations = new HashMap<>();

        // Create a simple idle animation (no movement)
        StrataAnimation.AnimationData idle = new StrataAnimation.AnimationData(
                "idle",
                1.0f,
                true,
                StrataAnimation.LoopMode.LOOP,
                0.0f,
                0.0f,
                Collections.emptyMap(),
                Collections.emptyList()
        );

        animations.put("idle", idle);

        return new StrataAnimation(id, 1, animations);
    }
}