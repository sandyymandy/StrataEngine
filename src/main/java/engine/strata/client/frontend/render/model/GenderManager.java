package engine.strata.client.frontend.render.model;

import engine.strata.client.frontend.render.animation.AnimationProcessor;
import engine.strata.util.Gender;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.regex.Pattern;

/**
 * Automatic gender-based bone visibility manager.
 *
 * <h3>Bone naming scheme (core.strmodel):</h3>
 * <pre>
 * Male body:      meshMaleTorso, meshMalePelvis, meshMaleArmR/L, meshMaleArmLowerR/L, meshMaleLegR/L
 * Female body:    meshFemaleTorso, meshFemalePelvis, meshFemaleArmR/L, meshFemaleArmLowerR/L,
 *                 meshFemaleLegR/L, breasts, breastL, breastR, nippleL, nippleR, cumflationBelly
 * Male genitals:  penis, shaft, tip, ball, texturing
 * Female genitals:vagina, vaginaClosed, vaginaOpen
 * Neutral NSFW:   anus, anusClosed, anusOpen  (shown for all genders in nude mode)
 * Shared/structural: torso, pelvis, body, armR/L, legR/L, head, neck, etc.
 * </pre>
 *
 * <h3>IMPORTANT — structural bones are never touched:</h3>
 * <p>{@code torso} is the structural parent of the neck, arms, and breasts. It must
 * always remain visible regardless of gender. Only the {@code meshMale*}/{@code meshFemale*}
 * child mesh bones are swapped.
 *
 * <h3>Gender behaviour:</h3>
 * <ul>
 *   <li><b>MALE:</b> meshMale* visible, meshFemale* + breasts hidden</li>
 *   <li><b>FEMALE:</b> meshFemale* + breasts visible, meshMale* hidden</li>
 *   <li><b>FUTANARI:</b> Female body + male genitals</li>
 *   <li><b>UNKNOWN:</b> No changes — model-file defaults apply</li>
 * </ul>
 */
public final class GenderManager {

    private static final Logger LOGGER = LoggerFactory.getLogger("GenderManager");

    /**
     * Matches male-specific mesh bones only.
     * NOTE: does NOT include "torso" — that is a shared structural bone whose
     * children include the neck, arms, and breasts. Hiding it would collapse
     * the entire upper body for female/futanari.
     */
    private static final Pattern MALE_BONE_PATTERN = Pattern.compile(
            "meshMale.*|penis",
            Pattern.CASE_INSENSITIVE
    );

    /** Matches female-specific mesh bones from core.strmodel. */
    private static final Pattern FEMALE_BONE_PATTERN = Pattern.compile(
            "meshFemale.*|breasts|vagina|cumflation.*|ass",
            Pattern.CASE_INSENSITIVE
    );

    /**
     * Matches neutral NSFW bones present on all genders.
     * Shown for all genders when nudeMode is enabled.
     */
    private static final Pattern NEUTRAL_NSFW_PATTERN = Pattern.compile(
            "anus",
            Pattern.CASE_INSENSITIVE
    );

    // ── Bone categorisation ───────────────────────────────────────────────────

    /** Categorises all bones in the model into male / female / neutral NSFW / shared groups. */
    public static BoneCategories categorizeBones(StrataModel model) {
        Set<String> maleBones = new HashSet<>();
        Set<String> femaleBones = new HashSet<>();
        Set<String> neutralNsfwBones = new HashSet<>();
        Set<String> sharedBones = new HashSet<>();

        for (String boneName : model.getAllBones().keySet()) {
            if (isMaleBone(boneName)) {
                maleBones.add(boneName);
            } else if (isFemaleBone(boneName)) {
                femaleBones.add(boneName);
            } else if (isNeutralNsfwBone(boneName)) {
                neutralNsfwBones.add(boneName);
            } else {
                sharedBones.add(boneName);
            }
        }

        return new BoneCategories(maleBones, femaleBones, neutralNsfwBones, sharedBones);
    }

    public static boolean isMaleBone(String boneName) {
        return MALE_BONE_PATTERN.matcher(boneName).matches();
    }

    public static boolean isFemaleBone(String boneName) {
        return FEMALE_BONE_PATTERN.matcher(boneName).matches();
    }

    public static boolean isNeutralNsfwBone(String boneName) {
        return NEUTRAL_NSFW_PATTERN.matcher(boneName).matches();
    }

    // ── Gender visibility application ─────────────────────────────────────────

    /**
     * Applies gender-based bone visibility. Call this after {@code resetAllBones()}.
     *
     * @param animProcessor     The animation processor to modify
     * @param model             The model being rendered
     * @param gender            The entity's gender
     * @param nudeMode          Whether NSFW content should be shown
     * @param modelSupportsNSFW Whether the model has genital bones
     */
    public static void applyGenderVisibility(AnimationProcessor animProcessor, StrataModel model,
                                             Gender gender, boolean nudeMode, boolean modelSupportsNSFW) {
        if (gender == null || gender == Gender.UNKNOWN) return;

        BoneCategories categories = categorizeBones(model);

        Set<String> maleGenitalBones = filterByKeyword(categories.maleBones, "penis");
        Set<String> femaleGenitalBones = filterByKeyword(categories.femaleBones, "vagina");
        Set<String> maleBodyBones = minus(categories.maleBones, maleGenitalBones);
        Set<String> femaleBodyBones = minus(categories.femaleBones, femaleGenitalBones);

        switch (gender) {
            case MALE -> {
                setBonesVisible(animProcessor, maleBodyBones, true);
                setBonesVisible(animProcessor, femaleBodyBones, false);
                if (modelSupportsNSFW) {
                    setBonesVisible(animProcessor, maleGenitalBones, nudeMode);
                    setBonesVisible(animProcessor, femaleGenitalBones, false);
                    setBonesVisible(animProcessor, categories.neutralNsfwBones, nudeMode);
                }
            }
            case FEMALE -> {
                setBonesVisible(animProcessor, maleBodyBones, false);
                setBonesVisible(animProcessor, femaleBodyBones, true);
                if (modelSupportsNSFW) {
                    setBonesVisible(animProcessor, maleGenitalBones, false);
                    setBonesVisible(animProcessor, femaleGenitalBones, nudeMode);
                    setBonesVisible(animProcessor, categories.neutralNsfwBones, nudeMode);
                }
            }
            case FUTANARI -> {
                // Female body + male genitals.
                setBonesVisible(animProcessor, maleBodyBones, false);
                setBonesVisible(animProcessor, femaleBodyBones, true);
                if (modelSupportsNSFW) {
                    setBonesVisible(animProcessor, maleGenitalBones, nudeMode);
                    setBonesVisible(animProcessor, femaleGenitalBones, nudeMode);
                    setBonesVisible(animProcessor, categories.neutralNsfwBones, nudeMode);
                }
            }
        }
    }



    /** Applies fine-grained per-body-part visibility from a {@link BodyPartOptions} config. */
    public static void applyCustomVisibility(AnimationProcessor animProcessor, StrataModel model, BodyPartOptions options) {
        BoneCategories categories = categorizeBones(model);

        Set<String> maleTorsoBones = filterByKeyword(categories.maleBones, "torso");
        Set<String> malePelvisBones = filterByKeyword(categories.maleBones, "pelvis");
        Set<String> maleGenitalBones = filterByKeyword(categories.maleBones, "penis");
        Set<String> maleArmBones = filterByKeyword(categories.maleBones, "arm");
        Set<String> maleLegBones = filterByKeyword(categories.maleBones, "leg");

        Set<String> femaleTorsoBones = filterByKeyword(categories.femaleBones, "torso");
        Set<String> femalePelvisBones = filterByKeyword(categories.femaleBones, "pelvis");
        Set<String> breastBones = filterByKeyword(categories.femaleBones, "breast", "nipple");
        Set<String> cumflationBones = filterByKeyword(categories.femaleBones, "cumflation");
        Set<String> femaleGenitalBones = filterByKeyword(categories.femaleBones, "vagina");
        Set<String> femaleArmBones = filterByKeyword(categories.femaleBones, "arm");
        Set<String> femaleLegBones = filterByKeyword(categories.femaleBones, "leg");

        setBonesVisible(animProcessor, maleTorsoBones, options.showMaleTorso);
        setBonesVisible(animProcessor, malePelvisBones, options.showMalePelvis);
        setBonesVisible(animProcessor, maleGenitalBones, options.showMaleGenitals);
        setBonesVisible(animProcessor, maleArmBones, options.showMaleArms);
        setBonesVisible(animProcessor, maleLegBones, options.showMaleLegs);

        setBonesVisible(animProcessor, femaleTorsoBones, options.showFemaleTorso);
        setBonesVisible(animProcessor, femalePelvisBones, options.showFemalePelvis);
        setBonesVisible(animProcessor, breastBones, options.showBreasts);
        setBonesVisible(animProcessor, cumflationBones, options.showCumflationBelly);
        setBonesVisible(animProcessor, femaleGenitalBones, options.showFemaleGenitals);
        setBonesVisible(animProcessor, femaleArmBones, options.showFemaleArms);
        setBonesVisible(animProcessor, femaleLegBones, options.showFemaleLegs);

        setBonesVisible(animProcessor, categories.neutralNsfwBones, options.showAnus);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static void setBonesVisible(AnimationProcessor animProcessor, Set<String> boneNames, boolean visible) {
        for (String boneName : boneNames) {
            animProcessor.setBoneVisible(boneName, visible);
        }
    }

    private static Set<String> filterByKeyword(Set<String> bones, String... keywords) {
        Set<String> filtered = new HashSet<>();
        for (String bone : bones) {
            String lower = bone.toLowerCase();
            for (String keyword : keywords) {
                if (lower.contains(keyword.toLowerCase())) {
                    filtered.add(bone);
                    break;
                }
            }
        }
        return filtered;
    }

    private static Set<String> minus(Set<String> a, Set<String> b) {
        Set<String> result = new HashSet<>(a);
        result.removeAll(b);
        return result;
    }

    // ── Debug ─────────────────────────────────────────────────────────────────

    public static void debugPrintBoneCategories(StrataModel model) {
        BoneCategories categories = categorizeBones(model);
        LOGGER.info("=== Bone Categories for {} ===", model.getId());
        LOGGER.info("Male bones ({}): {}", categories.maleBones.size(), categories.maleBones);
        LOGGER.info("Female bones ({}): {}", categories.femaleBones.size(), categories.femaleBones);
        LOGGER.info("Neutral NSFW ({}): {}", categories.neutralNsfwBones.size(), categories.neutralNsfwBones);
        LOGGER.info("Shared bones ({}): {}", categories.sharedBones.size(), categories.sharedBones);
    }

    // ── Data classes ──────────────────────────────────────────────────────────

    public record BoneCategories(
            Set<String> maleBones,
            Set<String> femaleBones,
            Set<String> neutralNsfwBones,
            Set<String> sharedBones
    ) {}

    public static class BodyPartOptions {
        public boolean showMaleTorso = true;
        public boolean showMalePelvis = true;
        public boolean showMaleArms = true;
        public boolean showMaleLegs = true;
        public boolean showMaleGenitals = true;

        public boolean showFemaleTorso = true;
        public boolean showFemalePelvis = true;
        public boolean showFemaleArms = true;
        public boolean showFemaleLegs = true;
        public boolean showBreasts = true;
        public boolean showCumflationBelly = false;
        public boolean showFemaleGenitals  = true;

        public boolean showAnus = true;

        public static BodyPartOptions safe() {
            BodyPartOptions opts = new BodyPartOptions();
            opts.showMaleGenitals = false;
            opts.showFemaleGenitals = false;
            opts.showAnus = false;
            return opts;
        }

        public static BodyPartOptions male() {
            BodyPartOptions opts = new BodyPartOptions();
            opts.showFemaleTorso = false;
            opts.showFemalePelvis = false;
            opts.showFemaleArms = false;
            opts.showFemaleLegs = false;
            opts.showBreasts = false;
            opts.showFemaleGenitals = false;
            return opts;
        }

        public static BodyPartOptions female() {
            BodyPartOptions opts = new BodyPartOptions();
            opts.showMaleTorso = false;
            opts.showMalePelvis = false;
            opts.showMaleArms = false;
            opts.showMaleLegs = false;
            opts.showMaleGenitals = false;
            return opts;
        }

        public static BodyPartOptions futanari() {
            BodyPartOptions opts = female();
            opts.showMaleGenitals = true;
            return opts;
        }
    }
}