package com.sunny.skin.data.model

/**
 * Body taxonomy driving the filter chips (Saved), the Front/Back body template
 * and its highlight zones (Overview), and per-scan location tags.
 *
 * [BodyRegion] is the coarse filter grouping shown as chips (All is implicit).
 * [BodySide] is the Front/Back toggle on the body template.
 * [BodyPart] is the specific location tagged on a scan (e.g. "Upper Back").
 */
enum class BodyRegion(val label: String) {
    HEAD("Head"),
    ARMS("Arms"),
    TORSO("Torso"),
    LEGS("Legs"),
}

enum class BodySide(val label: String) {
    FRONT("Front"),
    BACK("Back"),
}

/**
 * A specific, taggable location. [region] feeds the filter; [side] feeds the
 * body template; [zone] names the highlightable area on the body diagram so the
 * Overview can shade regions the user has already scanned.
 */
enum class BodyPart(
    val label: String,
    val region: BodyRegion,
    val side: BodySide,
    val zone: BodyZone,
) {
    SCALP("Scalp", BodyRegion.HEAD, BodySide.BACK, BodyZone.HEAD),
    FACE("Face", BodyRegion.HEAD, BodySide.FRONT, BodyZone.HEAD),
    NECK("Neck", BodyRegion.HEAD, BodySide.FRONT, BodyZone.NECK),

    CHEST("Chest", BodyRegion.TORSO, BodySide.FRONT, BodyZone.CHEST),
    ABDOMEN("Abdomen", BodyRegion.TORSO, BodySide.FRONT, BodyZone.ABDOMEN),
    UPPER_BACK("Upper Back", BodyRegion.TORSO, BodySide.BACK, BodyZone.UPPER_BACK),
    LOWER_BACK("Lower Back", BodyRegion.TORSO, BodySide.BACK, BodyZone.LOWER_BACK),
    SHOULDER("Shoulder", BodyRegion.TORSO, BodySide.FRONT, BodyZone.SHOULDER),

    LEFT_ARM("Left Arm", BodyRegion.ARMS, BodySide.FRONT, BodyZone.LEFT_ARM),
    RIGHT_ARM("Right Arm", BodyRegion.ARMS, BodySide.FRONT, BodyZone.RIGHT_ARM),
    LEFT_HAND("Left Hand", BodyRegion.ARMS, BodySide.FRONT, BodyZone.LEFT_ARM),
    RIGHT_HAND("Right Hand", BodyRegion.ARMS, BodySide.FRONT, BodyZone.RIGHT_ARM),

    LEFT_LEG("Left Leg", BodyRegion.LEGS, BodySide.FRONT, BodyZone.LEFT_LEG),
    RIGHT_LEG("Right Leg", BodyRegion.LEGS, BodySide.FRONT, BodyZone.RIGHT_LEG),
    MIDDLE_TOE("Middle Toe", BodyRegion.LEGS, BodySide.FRONT, BodyZone.LEFT_LEG),
    FOOT("Foot", BodyRegion.LEGS, BodySide.FRONT, BodyZone.RIGHT_LEG);

    /** "Torso · Upper Back" style location line used across the UI. */
    val locationLine: String get() = "${region.label} · $label"

    companion object {
        fun forRegion(region: BodyRegion): List<BodyPart> = entries.filter { it.region == region }
    }
}

/** Highlightable areas on the front/back body silhouette (Overview template). */
enum class BodyZone {
    HEAD, NECK, CHEST, ABDOMEN, SHOULDER, UPPER_BACK, LOWER_BACK,
    LEFT_ARM, RIGHT_ARM, LEFT_LEG, RIGHT_LEG,
}
