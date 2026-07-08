package com.alertnotes.features.drawing

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import com.alertnotes.domain.model.DrawingStroke
import com.alertnotes.domain.model.PaperStyle
import com.alertnotes.domain.model.ReminderDrawing
import com.alertnotes.domain.model.StrokeTool

/**
 * Shared vector rendering for reminder drawings: the editor canvas, list and
 * editor thumbnails, and the alert display all draw through here, so a
 * drawing looks identical everywhere at any size.
 */

/** Midpoint quadratic smoothing: raw touch samples become one fluid curve. */
fun buildSmoothPath(points: List<Offset>): Path {
    val path = Path()
    val first = points.firstOrNull() ?: return path
    path.moveTo(first.x, first.y)
    if (points.size == 1) {
        // A dot: draw a minimal segment so the round cap renders.
        path.lineTo(first.x + 0.01f, first.y + 0.01f)
        return path
    }
    for (i in 1 until points.size) {
        val previous = points[i - 1]
        val current = points[i]
        val mid = Offset((previous.x + current.x) / 2f, (previous.y + current.y) / 2f)
        path.quadraticTo(previous.x, previous.y, mid.x, mid.y)
    }
    val last = points.last()
    path.lineTo(last.x, last.y)
    return path
}

/** ARGB bits stored in the persisted Long → Compose color. */
fun Long.toInkColor(): Color = Color(this.toInt())

/** Compose color → ARGB bits for persistence. */
fun Color.toInkArgb(): Long = toArgb().toLong()

/** Renders committed strokes, scaling normalized points to this DrawScope. */
fun DrawScope.drawReminderStrokes(strokes: List<DrawingStroke>) {
    strokes.forEach { stroke ->
        val points = stroke.points.map { Offset(it.x * size.width, it.y * size.height) }
        drawSmoothStroke(
            points = points,
            color = stroke.colorArgb.toInkColor(),
            widthPx = stroke.widthFraction * size.width,
            tool = stroke.tool,
        )
    }
}

fun DrawScope.drawSmoothStroke(
    points: List<Offset>,
    color: Color,
    widthPx: Float,
    tool: StrokeTool,
) {
    if (points.isEmpty()) return
    val path = buildSmoothPath(points)
    when (tool) {
        StrokeTool.PEN -> drawPath(
            path = path,
            color = color,
            style = Stroke(width = widthPx, cap = StrokeCap.Round, join = StrokeJoin.Round),
        )

        StrokeTool.HIGHLIGHTER -> drawPath(
            path = path,
            color = color.copy(alpha = 0.35f),
            style = Stroke(width = widthPx * 2.2f, cap = StrokeCap.Round, join = StrokeJoin.Round),
        )

        StrokeTool.ERASER -> drawPath(
            path = path,
            color = Color.Transparent,
            style = Stroke(width = widthPx * 1.6f, cap = StrokeCap.Round, join = StrokeJoin.Round),
            blendMode = BlendMode.Clear,
        )
    }
}

// region Paper styles

/** The paper's flat base color; patterns draw on top of it. */
val PaperStyle.baseColor: Color
    get() = when (this) {
        PaperStyle.WHITE, PaperStyle.LIGHT_GRID, PaperStyle.DOTS -> Color(0xFFFFFFFF)
        PaperStyle.NOTEBOOK -> Color(0xFFFFFDF2)
        PaperStyle.DARK_GRID, PaperStyle.PLAIN_DARK -> Color(0xFF1C1917)
    }

private const val PAPER_CELLS = 12

/**
 * The paper's pattern (grid, dots, ruled lines), scaled to the canvas so it
 * looks identical in the editor, thumbnails, and alerts. Drawn in a layer
 * separate from the ink so the eraser never cuts into the paper.
 */
fun DrawScope.drawPaperPattern(style: PaperStyle) {
    val cell = size.width / PAPER_CELLS
    val hairline = (size.width * 0.0025f).coerceAtLeast(1f)
    when (style) {
        PaperStyle.WHITE, PaperStyle.PLAIN_DARK -> Unit

        PaperStyle.LIGHT_GRID -> drawGrid(cell, hairline, Color(0x33546E7A))

        PaperStyle.DARK_GRID -> drawGrid(cell, hairline, Color(0x40D6D3D1))

        PaperStyle.DOTS -> {
            val radius = (size.width * 0.006f).coerceAtLeast(1.5f)
            var y = cell
            while (y < size.height) {
                var x = cell
                while (x < size.width) {
                    drawCircle(color = Color(0x59546E7A), radius = radius, center = Offset(x, y))
                    x += cell
                }
                y += cell
            }
        }

        PaperStyle.NOTEBOOK -> {
            val line = cell * 1.1f
            var y = line
            while (y < size.height) {
                drawLine(
                    color = Color(0x4D64B5F6),
                    start = Offset(0f, y),
                    end = Offset(size.width, y),
                    strokeWidth = hairline,
                )
                y += line
            }
            drawLine(
                color = Color(0x59E57373),
                start = Offset(size.width * 0.12f, 0f),
                end = Offset(size.width * 0.12f, size.height),
                strokeWidth = hairline * 1.4f,
            )
        }
    }
}

private fun DrawScope.drawGrid(cell: Float, strokeWidth: Float, color: Color) {
    var x = cell
    while (x < size.width) {
        drawLine(color, Offset(x, 0f), Offset(x, size.height), strokeWidth)
        x += cell
    }
    var y = cell
    while (y < size.height) {
        drawLine(color, Offset(0f, y), Offset(size.width, y), strokeWidth)
        y += cell
    }
}

// endregion

/**
 * Read-only view of a drawing (thumbnails, alert display). The square canvas
 * matches the editing aspect so nothing distorts; the offscreen layer scopes
 * eraser strokes to the ink only. Paper style wins over the legacy flat
 * background color when both are present.
 */
@Composable
fun DrawingView(
    drawing: ReminderDrawing,
    modifier: Modifier = Modifier,
) {
    val background = drawing.paperStyle?.baseColor
        ?: drawing.backgroundArgb?.toInkColor()
        ?: MaterialTheme.colorScheme.surface
    Box(modifier = modifier.aspectRatio(1f)) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(background),
        )
        drawing.paperStyle?.let { style ->
            Canvas(modifier = Modifier.fillMaxSize()) {
                drawPaperPattern(style)
            }
        }
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen),
        ) {
            drawReminderStrokes(drawing.strokes)
        }
    }
}
