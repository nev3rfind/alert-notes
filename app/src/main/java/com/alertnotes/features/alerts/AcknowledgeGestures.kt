package com.alertnotes.features.alerts

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.unit.IntSize
import com.alertnotes.R
import com.alertnotes.domain.model.DrawingPoint
import com.alertnotes.domain.model.DrawingStroke
import com.alertnotes.domain.model.ReminderDrawing
import com.alertnotes.features.drawing.buildSmoothPath
import com.alertnotes.features.drawing.toInkArgb
import kotlin.math.hypot
import kotlinx.coroutines.delay

private const val TICK_SAMPLES = 72
private const val TICK_COMPLETION_FRACTION = 0.95f
private const val COMPLETION_HOLD_MILLIS = 420L

/** Brand fill for painted acknowledgement gestures. */
private val TickFillColor = Color(0xFFE4572E)

/**
 * The signature Alert Notes acknowledgement: a large grey checkmark that the
 * user paints over with a finger. Painted regions fill with brand orange;
 * at ~95% coverage the checkmark pulses fully orange and the reminder is
 * acknowledged.
 */
@Composable
fun TickGestureCanvas(
    onAcknowledged: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }
    val trail = remember { mutableStateListOf<Offset>() }
    var paintedFraction by remember { mutableFloatStateOf(0f) }
    var completed by remember { mutableStateOf(false) }
    val completionScale = remember { Animatable(1f) }

    // The check geometry and its coverage samples, rebuilt on resize.
    val geometry = remember(canvasSize) { TickGeometry(canvasSize) }

    LaunchedEffect(completed) {
        if (completed) {
            completionScale.animateTo(1.08f, spring(stiffness = Spring.StiffnessMedium))
            completionScale.animateTo(1f, spring(stiffness = Spring.StiffnessMediumLow))
            delay(COMPLETION_HOLD_MILLIS)
            onAcknowledged()
            // Re-arm: if the dismissal upstream was blocked (e.g. the user
            // cancelled the biometric prompt), the gesture must stay usable —
            // a one-shot flag would leave the alert with no dismiss path.
            delay(COMPLETION_HOLD_MILLIS)
            completed = false
        }
    }

    val description = stringResource(R.string.cd_tick_canvas)
    val acknowledgeLabel = stringResource(R.string.alert_acknowledge)
    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(1.2f)
            .onSizeChanged { canvasSize = it }
            .graphicsLayer {
                scaleX = completionScale.value
                scaleY = completionScale.value
                compositingStrategy = CompositingStrategy.Offscreen
            }
            .semantics {
                contentDescription = description
                progressBarRangeInfo = ProgressBarRangeInfo(paintedFraction, 0f..1f)
                // TalkBack users acknowledge without painting.
                customActions = listOf(
                    CustomAccessibilityAction(acknowledgeLabel) {
                        completed = true
                        true
                    },
                )
            }
            .pointerInput(geometry) {
                detectDragGestures(
                    onDragStart = { offset ->
                        trail.add(offset)
                        paintedFraction = geometry.markPainted(offset)
                    },
                    onDrag = { change, _ ->
                        change.consume()
                        trail.add(change.position)
                        paintedFraction = geometry.markPainted(change.position)
                        if (!completed && paintedFraction >= TICK_COMPLETION_FRACTION) {
                            completed = true
                        }
                    },
                )
            },
    ) {
        val checkPath = geometry.path ?: return@Canvas
        val strokeWidth = geometry.strokeWidth
        val checkStroke = Stroke(
            width = strokeWidth,
            cap = StrokeCap.Round,
            join = StrokeJoin.Round,
        )
        // Base: the large grey checkmark.
        drawPath(
            path = checkPath,
            color = Color.Gray.copy(alpha = 0.30f),
            style = checkStroke,
        )
        if (completed) {
            drawPath(path = checkPath, color = TickFillColor, style = checkStroke)
        } else if (trail.isNotEmpty()) {
            // Orange = finger trail ∩ checkmark, via an intersection layer.
            drawContext.canvas.saveLayer(
                bounds = androidx.compose.ui.geometry.Rect(Offset.Zero, size),
                paint = Paint(),
            )
            drawPath(
                path = buildSmoothPath(trail.toList()),
                color = TickFillColor,
                style = Stroke(
                    width = geometry.brushRadius * 2f,
                    cap = StrokeCap.Round,
                    join = StrokeJoin.Round,
                ),
            )
            drawPath(
                path = checkPath,
                color = Color.White,
                style = checkStroke,
                blendMode = BlendMode.DstIn,
            )
            drawContext.canvas.restore()
        }
    }
}

/** Checkmark path, coverage samples, and painting bookkeeping. */
private class TickGeometry(size: IntSize) {
    val path: Path?
    val strokeWidth: Float
    val brushRadius: Float
    private val samples: List<Offset>
    private val painted: BooleanArray
    private var paintedCount = 0

    init {
        if (size.width == 0 || size.height == 0) {
            path = null
            strokeWidth = 0f
            brushRadius = 0f
            samples = emptyList()
            painted = BooleanArray(0)
        } else {
            val width = size.width.toFloat()
            val height = size.height.toFloat()
            strokeWidth = width * 0.13f
            brushRadius = strokeWidth * 0.85f
            path = Path().apply {
                moveTo(width * 0.18f, height * 0.55f)
                lineTo(width * 0.42f, height * 0.80f)
                lineTo(width * 0.84f, height * 0.22f)
            }
            val measure = PathMeasure().apply { setPath(path, false) }
            samples = (0 until TICK_SAMPLES).map { index ->
                measure.getPosition(measure.length * index / (TICK_SAMPLES - 1f))
            }
            painted = BooleanArray(samples.size)
        }
    }

    /** Marks samples near [touch] painted; returns the covered fraction. */
    fun markPainted(touch: Offset): Float {
        if (samples.isEmpty()) return 0f
        samples.forEachIndexed { index, sample ->
            if (!painted[index] &&
                hypot(sample.x - touch.x, sample.y - touch.y) <= brushRadius
            ) {
                painted[index] = true
                paintedCount++
            }
        }
        return paintedCount.toFloat() / samples.size
    }
}

private const val SIGNATURE_MIN_LENGTH_FACTOR = 1.2f

/** Signature pad aspect ratio; signature renderers must match it. */
const val SIGNATURE_ASPECT_RATIO = 2.2f

/**
 * Signature acknowledgement: draw a short signature; once enough ink is on
 * the pad (total path length beyond a width-relative threshold) the lift of
 * the finger completes the acknowledgement. The drawn strokes are handed to
 * [onAcknowledged] as a normalized vector so they can be archived in history.
 */
@Composable
fun SignaturePad(
    inkColor: Color,
    onAcknowledged: (ReminderDrawing) -> Unit,
    modifier: Modifier = Modifier,
) {
    val strokes = remember { mutableStateListOf<List<Offset>>() }
    val activePoints = remember { mutableStateListOf<Offset>() }
    var inkedLength by remember { mutableFloatStateOf(0f) }
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }
    var completed by remember { mutableStateOf(false) }

    LaunchedEffect(completed) {
        if (completed) {
            delay(COMPLETION_HOLD_MILLIS)
            onAcknowledged(captureSignature(strokes.toList(), canvasSize, inkColor))
            // Re-arm (see TickGestureCanvas): a blocked dismissal — cancelled
            // biometric prompt — must not permanently disarm the pad.
            delay(COMPLETION_HOLD_MILLIS)
            completed = false
        }
    }

    val description = stringResource(R.string.cd_signature_pad)
    val acknowledgeLabel = stringResource(R.string.alert_acknowledge)
    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(2.2f)
            .onSizeChanged { canvasSize = it }
            .semantics {
                contentDescription = description
                customActions = listOf(
                    CustomAccessibilityAction(acknowledgeLabel) {
                        completed = true
                        true
                    },
                )
            }
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { offset ->
                        activePoints.clear()
                        activePoints.add(offset)
                    },
                    onDrag = { change, _ ->
                        change.consume()
                        activePoints.lastOrNull()?.let { last ->
                            inkedLength += (change.position - last).getDistance()
                        }
                        activePoints.add(change.position)
                    },
                    onDragEnd = {
                        if (activePoints.isNotEmpty()) {
                            strokes.add(activePoints.toList())
                            activePoints.clear()
                        }
                        val threshold = canvasSize.width * SIGNATURE_MIN_LENGTH_FACTOR
                        if (!completed && canvasSize.width > 0 && inkedLength >= threshold) {
                            completed = true
                        }
                    },
                    onDragCancel = { activePoints.clear() },
                )
            },
    ) {
        val baselineY = size.height * 0.82f
        drawLine(
            color = inkColor.copy(alpha = 0.25f),
            start = Offset(size.width * 0.08f, baselineY),
            end = Offset(size.width * 0.92f, baselineY),
            strokeWidth = 2f,
        )
        val strokeStyle = Stroke(
            width = size.height * 0.035f,
            cap = StrokeCap.Round,
            join = StrokeJoin.Round,
        )
        strokes.forEach { points ->
            drawPath(path = buildSmoothPath(points), color = inkColor, style = strokeStyle)
        }
        if (activePoints.isNotEmpty()) {
            drawPath(
                path = buildSmoothPath(activePoints.toList()),
                color = inkColor,
                style = strokeStyle,
            )
        }
    }
}

/**
 * Normalizes the pad's raw strokes to the storable vector form. Width
 * fraction mirrors the pad's on-screen stroke (height * 0.035 on a canvas
 * [SIGNATURE_ASPECT_RATIO] times wider than tall).
 */
private fun captureSignature(
    strokes: List<List<Offset>>,
    size: IntSize,
    inkColor: Color,
): ReminderDrawing {
    if (size.width == 0 || size.height == 0) return ReminderDrawing()
    return ReminderDrawing(
        strokes = strokes.filter { it.isNotEmpty() }.map { points ->
            DrawingStroke(
                points = points.map {
                    DrawingPoint(x = it.x / size.width, y = it.y / size.height)
                },
                colorArgb = inkColor.toInkArgb(),
                widthFraction = 0.035f / SIGNATURE_ASPECT_RATIO,
            )
        },
    )
}
