package com.alertnotes.features.profile

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Rotate90DegreesCw
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.alertnotes.R
import com.alertnotes.core.ui.components.PrimaryButton
import com.alertnotes.core.ui.theme.spacing

private val ViewportSize = 280.dp
private const val MIN_ZOOM = 1f
private const val MAX_ZOOM = 4f

/** Everything the renderer needs to reproduce the previewed crop. */
data class AvatarCrop(
    val rotationSteps: Int,
    val zoom: Float,
    val offsetX: Float,
    val offsetY: Float,
    val viewportPx: Float,
)

/**
 * Interactive crop editor: pinch to zoom, drag to reposition, rotate in 90°
 * steps — all inside a live circular preview that is exactly what uploads.
 * Pan and zoom are clamped so the photo always covers the circle.
 */
@Composable
internal fun AvatarEditorDialog(
    source: Bitmap,
    onConfirm: (AvatarCrop) -> Unit,
    onDismiss: () -> Unit,
) {
    val viewportPx = with(LocalDensity.current) { ViewportSize.toPx() }
    var rotationSteps by rememberSaveable { mutableIntStateOf(0) }
    var zoom by rememberSaveable { mutableFloatStateOf(MIN_ZOOM) }
    var offsetX by rememberSaveable { mutableFloatStateOf(0f) }
    var offsetY by rememberSaveable { mutableFloatStateOf(0f) }

    fun clampOffsets() {
        val (maxX, maxY) = AvatarImageProcessor.maxOffset(
            source = source,
            rotationSteps = rotationSteps,
            zoom = zoom,
            viewportPx = viewportPx,
        )
        offsetX = offsetX.coerceIn(-maxX, maxX)
        offsetY = offsetY.coerceIn(-maxY, maxY)
    }

    val transformState = rememberTransformableState { zoomChange, panChange, _ ->
        zoom = (zoom * zoomChange).coerceIn(MIN_ZOOM, MAX_ZOOM)
        offsetX += panChange.x
        offsetY += panChange.y
        clampOffsets()
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surface,
        ) {
            Column(
                modifier = Modifier.padding(MaterialTheme.spacing.extraLarge),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = stringResource(R.string.profile_avatar_editor_title),
                    style = MaterialTheme.typography.titleLarge,
                )
                Text(
                    text = stringResource(R.string.profile_avatar_editor_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = MaterialTheme.spacing.extraSmall),
                )
                Box(
                    modifier = Modifier
                        .padding(top = MaterialTheme.spacing.large)
                        .size(ViewportSize)
                        .clipToBounds()
                        .transformable(transformState),
                ) {
                    EditorCanvas(
                        source = source,
                        rotationSteps = rotationSteps,
                        zoom = zoom,
                        offsetX = offsetX,
                        offsetY = offsetY,
                        viewportPx = viewportPx,
                    )
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier.padding(top = MaterialTheme.spacing.small),
                ) {
                    IconButton(
                        onClick = {
                            rotationSteps = (rotationSteps + 1) % 4
                            clampOffsets()
                        },
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Rotate90DegreesCw,
                            contentDescription = stringResource(R.string.profile_avatar_rotate),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                    Text(
                        text = stringResource(R.string.profile_avatar_rotate),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                PrimaryButton(
                    text = stringResource(R.string.profile_avatar_use_photo),
                    onClick = {
                        onConfirm(
                            AvatarCrop(
                                rotationSteps = rotationSteps,
                                zoom = zoom,
                                offsetX = offsetX,
                                offsetY = offsetY,
                                viewportPx = viewportPx,
                            ),
                        )
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = MaterialTheme.spacing.large),
                )
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.padding(top = MaterialTheme.spacing.extraSmall),
                ) {
                    Text(text = stringResource(R.string.action_cancel))
                }
            }
        }
    }
}

/**
 * Draws the image with the exact transform [AvatarImageProcessor.renderAvatar]
 * replays, then dims everything outside the circular crop.
 */
@Composable
private fun EditorCanvas(
    source: Bitmap,
    rotationSteps: Int,
    zoom: Float,
    offsetX: Float,
    offsetY: Float,
    viewportPx: Float,
) {
    val image = source.asImageBitmap()
    Canvas(modifier = Modifier.size(ViewportSize)) {
        val total = AvatarImageProcessor.coverScale(source, viewportPx) * zoom
        val pivot = Offset(viewportPx / 2f + offsetX, viewportPx / 2f + offsetY)
        translate(left = pivot.x, top = pivot.y) {
            rotate(degrees = rotationSteps * 90f, pivot = Offset.Zero) {
                scale(scale = total, pivot = Offset.Zero) {
                    drawImage(
                        image = image,
                        topLeft = Offset(-source.width / 2f, -source.height / 2f),
                    )
                }
            }
        }
        // Dim outside the circle so the crop reads instantly.
        val mask = Path().apply {
            fillType = PathFillType.EvenOdd
            addRect(Rect(0f, 0f, size.width, size.height))
            addOval(
                Rect(
                    center = Offset(size.width / 2f, size.height / 2f),
                    radius = size.minDimension / 2f,
                ),
            )
        }
        drawPath(path = mask, color = Color.Black.copy(alpha = 0.55f))
    }
}
