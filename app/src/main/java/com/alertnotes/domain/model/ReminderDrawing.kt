package com.alertnotes.domain.model

import kotlinx.serialization.Serializable

/** Ink tool a stroke was drawn with; affects rendering, not storage shape. */
@Serializable
enum class StrokeTool {
    PEN,
    HIGHLIGHTER,
    ERASER,
}

/** Canvas paper style; null on legacy drawings (plain color background). */
@Serializable
enum class PaperStyle {
    WHITE,
    LIGHT_GRID,
    DARK_GRID,
    DOTS,
    NOTEBOOK,
    PLAIN_DARK,
}

/** One entry of a checklist reminder. */
@Serializable
data class ChecklistItem(val text: String)

/**
 * A point in canvas-relative coordinates (0..1 on a square canvas), so
 * drawings re-render losslessly at any size on any screen.
 */
@Serializable
data class DrawingPoint(val x: Float, val y: Float)

@Serializable
data class DrawingStroke(
    val points: List<DrawingPoint>,
    /** Ink color as ARGB; captured from the reminder theme at draw time. */
    val colorArgb: Long,
    /** Stroke width as a fraction of the canvas width. */
    val widthFraction: Float,
    val tool: StrokeTool = StrokeTool.PEN,
)

/**
 * A reminder's hand drawing: pure vector strokes serialized to JSON in the
 * database — no bitmaps, no quality loss.
 */
@Serializable
data class ReminderDrawing(
    val strokes: List<DrawingStroke> = emptyList(),
    /** Optional canvas background as ARGB; null renders on the surface color. */
    val backgroundArgb: Long? = null,
    /** Paper style; takes precedence over [backgroundArgb] when set. */
    val paperStyle: PaperStyle? = null,
) {
    val isEmpty: Boolean get() = strokes.isEmpty()
}
