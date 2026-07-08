package com.alertnotes.domain.model

/** What the reminder shows when it fires. */
enum class ReminderType {
    TEXT,
    DRAWING,
    CHECKLIST,
}

/**
 * Urgency of a reminder. [rank] is persisted and used for queue ordering, so
 * it must remain stable even if enum entries are ever reordered.
 */
enum class ReminderPriority(val rank: Int) {
    NORMAL(0),
    HIGH(1),
    CRITICAL(2),
}

/** How the alert is presented when it fires. */
enum class DisplayMode {
    FULL_SCREEN,
    FLOATING_CARD,
}

/** Gesture required to acknowledge an alert (enforced by the popup phase). */
enum class AcknowledgementType {
    /** The alert can simply be dismissed. */
    NONE,
    TAP,
    SWIPE,
    TICK_GESTURE,
    SIGNATURE,
}

/**
 * Anchor position for the floating-card display mode.
 * [horizontalBias]/[verticalBias] map directly onto Compose's BiasAlignment
 * (-1 = start/top, 0 = center, 1 = end/bottom).
 */
enum class FloatingCardPosition(val horizontalBias: Float, val verticalBias: Float) {
    TOP_LEFT(-1f, -1f),
    TOP_CENTER(0f, -1f),
    TOP_RIGHT(1f, -1f),
    CENTER(0f, 0f),
    BOTTOM_LEFT(-1f, 1f),
    BOTTOM_CENTER(0f, 1f),
    BOTTOM_RIGHT(1f, 1f),
}

/** Size preset for the floating-card display mode. */
enum class FloatingCardSize {
    SMALL,
    MEDIUM,
    LARGE,
}

/**
 * Per-reminder visual identity. Solid themes gently breathe; gradient themes
 * animate slowly. Rendering lives in `features/alerts/ReminderThemes`.
 */
enum class ReminderTheme {
    PRIMARY_ORANGE,
    FOREST_GREEN,
    GOLDEN_YELLOW,
    MIDNIGHT_PURPLE,
    BERRY_RED,
    SOFT_CREAM,
    ORANGE_GRADIENT,
    PURPLE_GRADIENT,
}

/** How much of the alert surface width a reminder drawing occupies. */
enum class DrawingSize(val fraction: Float) {
    SIZE_40(0.4f),
    SIZE_60(0.6f),
    SIZE_80(0.8f),
    SIZE_100(1f),
}

/**
 * Where the drawing sits inside the alert content. Modeled as an enum so
 * future layouts (corners, behind-text, split) are additive.
 */
enum class DrawingPosition {
    TOP,
    CENTER,
    BOTTOM,
}

/** Direction a swipe acknowledgement must travel. */
enum class SwipeDirection(val dx: Float, val dy: Float) {
    UP(0f, -1f),
    DOWN(0f, 1f),
    LEFT(-1f, 0f),
    RIGHT(1f, 0f),
}

/** How eagerly the alert may draw over other apps (consumed by the popup phase). */
enum class OverlayPreference {
    /** Overlay when permitted, otherwise fall back to in-app / notification. */
    AUTO,

    /** Always try to draw over other apps. */
    PREFER_OVERLAY,

    /** Never draw over other apps. */
    NEVER_OVERLAY,
}
