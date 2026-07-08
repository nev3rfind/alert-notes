package com.alertnotes.features.drawing

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Backspace
import androidx.compose.material.icons.automirrored.outlined.Redo
import androidx.compose.material.icons.automirrored.outlined.Undo
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.BorderColor
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.alertnotes.R
import com.alertnotes.core.ui.WindowWidthClass
import com.alertnotes.core.ui.rememberWindowWidthClass
import com.alertnotes.core.ui.theme.spacing
import com.alertnotes.domain.model.DrawingPoint
import com.alertnotes.domain.model.DrawingStroke
import com.alertnotes.domain.model.PaperStyle
import com.alertnotes.domain.model.ReminderDrawing
import com.alertnotes.domain.model.StrokeTool

private const val MAX_HISTORY = 50
private const val MIN_POINT_DISTANCE_PX = 3f

/** Selectable paper styles; null means "follow the surface color". */
private val PaperOptions: List<PaperStyle?> = listOf(null) + PaperStyle.entries

/** Ink palette; null means "the reminder theme's accent". */
private val InkOptions: List<Long?> = listOf(
    null,
    0xFF1C1917, // near-black
    0xFFFFFFFF, // white
    0xFFE4572E, // brand orange
    0xFFD32F2F, // red
    0xFFF9A825, // amber
    0xFF388E3C, // green
    0xFF00897B, // teal
    0xFF1E88E5, // blue
    0xFF5E35B1, // purple
    0xFFD81B60, // pink
    0xFF6D4C41, // brown
)

/** Undoable canvas model. Strokes survive rotation via a JSON saver. */
class DrawingCanvasState(initial: ReminderDrawing?) {
    var strokes: List<DrawingStroke> by mutableStateOf(initial?.strokes.orEmpty())
        private set
    var tool: StrokeTool by mutableStateOf(StrokeTool.PEN)
    var widthFraction: Float by mutableStateOf(DEFAULT_WIDTH_FRACTION)
    var backgroundArgb: Long? by mutableStateOf(initial?.backgroundArgb)
    var paperStyle: PaperStyle? by mutableStateOf(initial?.paperStyle)

    /** Selected pen color; null follows the reminder theme's accent. */
    var inkArgb: Long? by mutableStateOf(null)

    private val undoStack = ArrayDeque<List<DrawingStroke>>()
    private val redoStack = ArrayDeque<List<DrawingStroke>>()

    val canUndo: Boolean get() = undoStack.isNotEmpty()
    val canRedo: Boolean get() = redoStack.isNotEmpty()

    fun commit(stroke: DrawingStroke) {
        pushUndo()
        redoStack.clear()
        strokes = strokes + stroke
    }

    fun undo() {
        val previous = undoStack.removeLastOrNull() ?: return
        redoStack.addLast(strokes)
        strokes = previous
    }

    fun redo() {
        val next = redoStack.removeLastOrNull() ?: return
        undoStack.addLast(strokes)
        strokes = next
    }

    fun clear() {
        if (strokes.isEmpty()) return
        pushUndo()
        redoStack.clear()
        strokes = emptyList()
    }

    fun toDrawing(): ReminderDrawing? =
        if (strokes.isEmpty()) null else ReminderDrawing(strokes, backgroundArgb, paperStyle)

    private fun pushUndo() {
        undoStack.addLast(strokes)
        if (undoStack.size > MAX_HISTORY) undoStack.removeFirst()
    }

    companion object {
        const val DEFAULT_WIDTH_FRACTION = 0.015f

        /** Persists strokes and background across rotation (history resets). */
        val Saver: Saver<DrawingCanvasState, String> = Saver(
            save = { state -> state.toDrawing()?.let(ReminderDrawing::toSaverJson).orEmpty() },
            restore = { json -> DrawingCanvasState(json.fromSaverJsonOrNull()) },
        )
    }
}

/**
 * Full-screen drawing studio: pen / highlighter / eraser, brush size,
 * undo / redo / clear, and canvas backgrounds. Vector all the way down —
 * strokes are stored normalized and re-rendered losslessly at every size.
 */
@Composable
fun DrawingCanvasDialog(
    initial: ReminderDrawing?,
    inkColor: Color,
    onSave: (ReminderDrawing?) -> Unit,
    onDismiss: () -> Unit,
) {
    val state = rememberSaveable(saver = DrawingCanvasState.Saver) {
        DrawingCanvasState(initial)
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background,
        ) {
            val isWide = rememberWindowWidthClass() != WindowWidthClass.Compact
            Column(modifier = Modifier.fillMaxSize()) {
                CanvasTopBar(
                    onCancel = onDismiss,
                    onSave = { onSave(state.toDrawing()) },
                )
                if (isWide) {
                    // Wide windows: the toolbar docks beside a maximized canvas.
                    Row(modifier = Modifier.weight(1f)) {
                        DrawingSurface(
                            state = state,
                            inkColor = inkColor,
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight(),
                        )
                        DockedToolbar(
                            state = state,
                            themeInk = inkColor,
                            modifier = Modifier.width(216.dp),
                        )
                    }
                } else {
                    DrawingSurface(
                        state = state,
                        inkColor = inkColor,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                    )
                    CanvasToolbar(state = state, themeInk = inkColor)
                }
            }
        }
    }
}

@Composable
private fun CanvasTopBar(onCancel: () -> Unit, onSave: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                horizontal = MaterialTheme.spacing.small,
                vertical = MaterialTheme.spacing.extraSmall,
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onCancel) {
            Icon(
                imageVector = Icons.Filled.Close,
                contentDescription = stringResource(R.string.action_cancel),
            )
        }
        Text(
            text = stringResource(R.string.drawing_canvas_title),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.weight(1f),
        )
        TextButton(onClick = onSave) {
            Text(text = stringResource(R.string.action_save))
        }
    }
}

@Composable
private fun DrawingSurface(
    state: DrawingCanvasState,
    inkColor: Color,
    modifier: Modifier = Modifier,
) {
    // In-progress stroke in pixel space; committed strokes are normalized.
    val activePoints = remember { mutableStateListOf<Offset>() }
    val activePressures = remember { mutableStateListOf<Float>() }
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }
    val background = state.paperStyle?.baseColor
        ?: state.backgroundArgb?.toInkColor()
        ?: MaterialTheme.colorScheme.surface
    val effectiveInk = state.inkArgb?.toInkColor() ?: inkColor
    val canvasDescription = stringResource(R.string.cd_drawing_canvas)

    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Box(
            modifier = Modifier
                // Minimal chrome: the square canvas takes nearly the whole
                // remaining screen in either orientation.
                .padding(MaterialTheme.spacing.small)
                .aspectRatio(1f)
                .background(background, MaterialTheme.shapes.medium)
                .border(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.outlineVariant,
                    shape = MaterialTheme.shapes.medium,
                ),
        ) {
            // Paper pattern lives under the ink layer so erasing never cuts
            // into the paper.
            state.paperStyle?.let { style ->
                Canvas(modifier = Modifier.fillMaxSize()) {
                    drawPaperPattern(style)
                }
            }
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .onSizeChanged { canvasSize = it }
                    // Offscreen layer scopes eraser strokes to ink only.
                    .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
                    .semantics { contentDescription = canvasDescription }
                    .pointerInput(state.tool, state.widthFraction, effectiveInk) {
                        detectDragGestures(
                            onDragStart = { offset ->
                                activePoints.clear()
                                activePressures.clear()
                                activePoints.add(offset)
                            },
                            onDrag = { change, _ ->
                                change.consume()
                                val last = activePoints.lastOrNull()
                                val position = change.position
                                if (last == null ||
                                    (position - last).getDistance() >= MIN_POINT_DISTANCE_PX
                                ) {
                                    activePoints.add(position)
                                    activePressures.add(change.pressure)
                                }
                            },
                            onDragEnd = {
                                commitActiveStroke(
                                    state = state,
                                    activePoints = activePoints,
                                    pressures = activePressures,
                                    canvasSize = canvasSize,
                                    inkColor = effectiveInk,
                                )
                                activePoints.clear()
                                activePressures.clear()
                            },
                            onDragCancel = {
                                activePoints.clear()
                                activePressures.clear()
                            },
                        )
                    },
            ) {
                drawReminderStrokes(state.strokes)
                if (activePoints.isNotEmpty()) {
                    drawSmoothStroke(
                        points = activePoints.toList(),
                        color = effectiveInk,
                        widthPx = state.widthFraction * size.width,
                        tool = state.tool,
                    )
                }
            }
        }
    }
}

private fun commitActiveStroke(
    state: DrawingCanvasState,
    activePoints: List<Offset>,
    pressures: List<Float>,
    canvasSize: IntSize,
    inkColor: Color,
) {
    if (activePoints.isEmpty() || canvasSize.width == 0 || canvasSize.height == 0) return
    state.commit(
        DrawingStroke(
            points = activePoints.map {
                DrawingPoint(
                    x = (it.x / canvasSize.width).coerceIn(0f, 1f),
                    y = (it.y / canvasSize.height).coerceIn(0f, 1f),
                )
            },
            colorArgb = inkColor.toInkArgb(),
            widthFraction = state.widthFraction * pressureScale(pressures),
            tool = state.tool,
        ),
    )
}

/**
 * Pressure sensitivity where the hardware provides it: styluses (and some
 * screens) report varying pressure, which scales the committed stroke width.
 * Finger input on most screens reports a constant, which leaves width as-is.
 */
private fun pressureScale(pressures: List<Float>): Float {
    if (pressures.isEmpty()) return 1f
    val varies = pressures.max() - pressures.min() > 0.05f
    if (!varies) return 1f
    val average = (pressures.sum() / pressures.size).coerceIn(0f, 2f)
    return (0.5f + average).coerceIn(0.5f, 2f)
}

/** Compact toolbar: primary tools always visible, extras collapse away. */
@Composable
private fun CanvasToolbar(state: DrawingCanvasState, themeInk: Color) {
    var showExtras by rememberSaveable { mutableStateOf(false) }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                horizontal = MaterialTheme.spacing.large,
                vertical = MaterialTheme.spacing.extraSmall,
            ),
        verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.extraSmall),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ToolButtons(state)
            HistoryButtons(state)
            IconButton(onClick = { showExtras = !showExtras }) {
                Icon(
                    imageVector = Icons.Outlined.Tune,
                    contentDescription = stringResource(R.string.drawing_more_tools),
                    tint = if (showExtras) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
        }
        AnimatedVisibility(
            visible = showExtras,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut(),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.medium),
                ) {
                    Text(
                        text = stringResource(R.string.drawing_brush_size),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    BrushSizeSlider(state, modifier = Modifier.weight(1f))
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small),
                ) {
                    InkDots(state = state, themeInk = themeInk)
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small),
                ) {
                    PaperDots(state)
                }
            }
        }
    }
}

/** Side-docked toolbar for wide windows: everything visible, canvas maximal. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DockedToolbar(
    state: DrawingCanvasState,
    themeInk: Color,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.padding(MaterialTheme.spacing.medium),
        verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.medium),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.extraSmall)) {
            ToolButtons(state)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.extraSmall)) {
            HistoryButtons(state)
        }
        Text(
            text = stringResource(R.string.drawing_brush_size),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        BrushSizeSlider(state)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small)) {
            InkDots(state = state, themeInk = themeInk)
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small)) {
            PaperDots(state)
        }
    }
}

@Composable
private fun ToolButtons(state: DrawingCanvasState) {
    ToolButton(
        icon = Icons.Outlined.Edit,
        labelRes = R.string.drawing_tool_pen,
        selected = state.tool == StrokeTool.PEN,
        onClick = { state.tool = StrokeTool.PEN },
    )
    ToolButton(
        icon = Icons.Outlined.BorderColor,
        labelRes = R.string.drawing_tool_highlighter,
        selected = state.tool == StrokeTool.HIGHLIGHTER,
        onClick = { state.tool = StrokeTool.HIGHLIGHTER },
    )
    ToolButton(
        icon = Icons.AutoMirrored.Outlined.Backspace,
        labelRes = R.string.drawing_tool_eraser,
        selected = state.tool == StrokeTool.ERASER,
        onClick = { state.tool = StrokeTool.ERASER },
    )
}

@Composable
private fun HistoryButtons(state: DrawingCanvasState) {
    IconButton(onClick = state::undo, enabled = state.canUndo) {
        Icon(
            imageVector = Icons.AutoMirrored.Outlined.Undo,
            contentDescription = stringResource(R.string.drawing_undo),
        )
    }
    IconButton(onClick = state::redo, enabled = state.canRedo) {
        Icon(
            imageVector = Icons.AutoMirrored.Outlined.Redo,
            contentDescription = stringResource(R.string.drawing_redo),
        )
    }
    IconButton(onClick = state::clear, enabled = state.strokes.isNotEmpty()) {
        Icon(
            imageVector = Icons.Outlined.Delete,
            contentDescription = stringResource(R.string.drawing_clear),
        )
    }
}

@Composable
private fun BrushSizeSlider(state: DrawingCanvasState, modifier: Modifier = Modifier) {
    val description = stringResource(R.string.cd_brush_size)
    Slider(
        value = state.widthFraction,
        onValueChange = { state.widthFraction = it },
        valueRange = 0.006f..0.05f,
        modifier = modifier.semantics { contentDescription = description },
    )
}

@Composable
private fun InkDots(state: DrawingCanvasState, themeInk: Color) {
    val description = stringResource(R.string.cd_ink_color)
    InkOptions.forEach { option ->
        SelectableDot(
            selected = state.inkArgb == option,
            onSelect = { state.inkArgb = option },
            description = description,
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(option?.toInkColor() ?: themeInk),
            )
        }
    }
}

@Composable
private fun PaperDots(state: DrawingCanvasState) {
    val description = stringResource(R.string.cd_canvas_background)
    PaperOptions.forEach { option ->
        SelectableDot(
            selected = state.paperStyle == option && state.backgroundArgb == null,
            onSelect = {
                state.paperStyle = option
                // Paper styles supersede the legacy flat background.
                state.backgroundArgb = null
            },
            description = description,
        ) {
            val base = option?.baseColor ?: MaterialTheme.colorScheme.surface
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(base),
            ) {
                option?.let { style ->
                    // Miniature of the actual paper pattern.
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        drawPaperPattern(style)
                    }
                }
            }
        }
    }
}

@Composable
private fun ToolButton(
    icon: ImageVector,
    labelRes: Int,
    selected: Boolean,
    onClick: () -> Unit,
) {
    IconButton(
        onClick = onClick,
        // The active tool was conveyed only by tint; expose it to TalkBack.
        modifier = Modifier.semantics { this.selected = selected },
        colors = IconButtonDefaults.iconButtonColors(
            containerColor = if (selected) {
                MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
            } else {
                Color.Transparent
            },
            contentColor = if (selected) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
        ),
    ) {
        Icon(imageVector = icon, contentDescription = stringResource(labelRes))
    }
}

/**
 * A circular swatch with selection ring; content fills the clipped circle.
 * The touch/focus target is expanded to the 48dp accessibility minimum and
 * the selection state is exposed to TalkBack.
 */
@Composable
private fun SelectableDot(
    selected: Boolean,
    onSelect: () -> Unit,
    description: String,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = Modifier
            .minimumInteractiveComponentSize()
            .selectable(
                selected = selected,
                role = Role.RadioButton,
                onClick = onSelect,
            )
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(CircleShape),
        ) {
            content()
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .border(
                        width = if (selected) 2.dp else 1.dp,
                        color = if (selected) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.outlineVariant
                        },
                        shape = CircleShape,
                    ),
            )
        }
    }
}

// region Saver JSON plumbing (rotation survival)

private fun ReminderDrawing.toSaverJson(): String =
    kotlinx.serialization.json.Json.encodeToString(ReminderDrawing.serializer(), this)

private fun String.fromSaverJsonOrNull(): ReminderDrawing? =
    if (isBlank()) {
        null
    } else {
        runCatching {
            kotlinx.serialization.json.Json.decodeFromString(ReminderDrawing.serializer(), this)
        }.getOrNull()
    }

// endregion
