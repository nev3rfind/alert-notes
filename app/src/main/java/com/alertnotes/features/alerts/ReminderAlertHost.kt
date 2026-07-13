package com.alertnotes.features.alerts

import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.zIndex
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.alertnotes.services.AcknowledgementSession
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.alertnotes.R
import com.alertnotes.biometric.BiometricGate
import com.alertnotes.core.ui.theme.spacing
import com.alertnotes.domain.model.AcknowledgeMethod
import com.alertnotes.domain.model.AcknowledgementType
import com.alertnotes.domain.model.DisplayMode
import com.alertnotes.domain.model.DrawingPosition
import com.alertnotes.domain.model.FloatingCardSize
import com.alertnotes.domain.model.Reminder
import com.alertnotes.domain.model.ReminderDrawing
import com.alertnotes.domain.model.ReminderPriority
import com.alertnotes.domain.model.SwipeDirection
import com.alertnotes.features.drawing.DrawingView
import java.time.Duration
import java.time.Instant
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Renders whatever the presentation engine says is active, above the whole
 * app. Full-screen alerts own the display; floating cards overlay the UI at
 * the reminder's configured position without blocking the rest of the app.
 * Both render on the reminder's animated theme surface.
 */
@Composable
fun ReminderAlertHost(
    viewModel: AlertPresenterViewModel = hiltViewModel(),
) {
    val presentedAlert by viewModel.activeAlert.collectAsStateWithLifecycle()
    val ackPhase by AcknowledgementSession.phase.collectAsStateWithLifecycle()
    // While a proof capture runs, the reminder is SUSPENDED — no surface may
    // render it. This host lives in MainActivity, AlertActivity, and the
    // overlay alike; gating only the dispatcher was not enough, because the
    // location screen sits in the app's own task with these hosts alive
    // beneath it — the alert UI "took over" from here, not from a re-launch.
    val alert = if (ackPhase == AcknowledgementSession.Phase.IDLE) presentedAlert else null
    val activity = LocalActivity.current
    val biometricTitle = stringResource(R.string.biometric_prompt_title)
    val cancelLabel = stringResource(R.string.action_cancel)

    // Every dismissal funnels through here: reminders that require
    // authentication show the biometric prompt (with optional device-PIN
    // fallback) before the alert is consumed.
    val requestDismiss: (ActiveAlert, AcknowledgeMethod, ReminderDrawing?) -> Unit =
        { target, method, signature ->
            val reminder = target.reminder
            if (reminder.requiresBiometric && activity is FragmentActivity) {
                BiometricGate.authenticate(
                    activity = activity,
                    allowDeviceCredential = reminder.biometricPinFallback,
                    title = biometricTitle,
                    cancelLabel = cancelLabel,
                    onSuccess = { viewModel.dismiss(target, method, signature) },
                )
            } else {
                viewModel.dismiss(target, method, signature)
            }
        }

    // While an alert is visible the back button belongs to it: it dismisses
    // when no acknowledgement gesture or checklist is required, and is
    // swallowed otherwise so the app underneath can't be navigated blindly.
    val current = alert
    BackHandler(enabled = current != null && current.reminder.displayMode == DisplayMode.FULL_SCREEN) {
        if (current != null &&
            current.reminder.acknowledgement == AcknowledgementType.NONE &&
            current.reminder.checklist.isEmpty()
        ) {
            requestDismiss(current, AcknowledgeMethod.BUTTON, null)
        }
    }

    AnimatedContent(
        targetState = alert,
        contentKey = { it?.entryId },
        transitionSpec = {
            val enter = fadeIn(tween(220)) + scaleIn(
                initialScale = 0.95f,
                animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
            )
            val exit = fadeOut(tween(180)) + slideOutVertically(tween(220)) { it / 24 }
            enter togetherWith exit
        },
        label = "alertHost",
    ) { target ->
        when (target?.reminder?.displayMode) {
            null -> Unit
            DisplayMode.FULL_SCREEN -> FullScreenAlert(
                alert = target,
                onDismiss = { method, signature -> requestDismiss(target, method, signature) },
                onSnooze = { duration -> viewModel.snooze(target, duration) },
            )

            DisplayMode.FLOATING_CARD -> Box(
                modifier = Modifier
                    .fillMaxSize()
                    .safeDrawingPadding()
                    .padding(MaterialTheme.spacing.large),
            ) {
                val reminder = target.reminder
                FloatingAlertCard(
                    alert = target,
                    onDismiss = { method, signature -> requestDismiss(target, method, signature) },
                    onSnooze = { duration -> viewModel.snooze(target, duration) },
                    modifier = Modifier.align(
                        BiasAlignment(
                            horizontalBias = reminder.floatingCardPosition.horizontalBias,
                            verticalBias = reminder.floatingCardPosition.verticalBias,
                        ),
                    ),
                )
            }
        }
    }
}

// region Full screen

/** Internal so the overlay engine and AlertActivity can reuse it directly. */
@Composable
internal fun FullScreenAlert(
    alert: ActiveAlert,
    onDismiss: (AcknowledgeMethod, ReminderDrawing?) -> Unit,
    onSnooze: (Duration) -> Unit,
) {
    val reminder = alert.reminder
    val spec = reminder.theme.spec
    val checkedItems = rememberChecklistChecked(alert)
    val checklistRemaining = reminder.checklist.size - checkedItems.count { it }
    // The dismiss lock gates EVERY acknowledgement path, not just the pill
    // button — otherwise tap-anywhere/swipe/gestures trivially bypass it.
    val lockRemaining = rememberDismissLockSeconds(alert)
    val acknowledgeReady = checklistRemaining == 0 && lockRemaining <= 0
    Box(
        modifier = Modifier
            .fillMaxSize()
            // Neutral outer background keeps focus on the themed surface.
            .background(MaterialTheme.colorScheme.background)
            .tapAcknowledgeable(
                enabled = reminder.acknowledgement == AcknowledgementType.TAP &&
                    acknowledgeReady,
                onAcknowledged = { onDismiss(reminder.tapMethod(), null) },
            )
            .safeDrawingPadding()
            .padding(MaterialTheme.spacing.large),
    ) {
        // The themed surface fills ~80% of the display area.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .criticalAttentionEffects(reminder.priority)
                .clip(MaterialTheme.shapes.extraLarge)
                .animatedThemeBackground(reminder.theme)
                .swipeDismissable(
                    enabled = reminder.acknowledgement == AcknowledgementType.SWIPE &&
                        acknowledgeReady,
                    direction = reminder.swipeDirection,
                    onDismissed = { onDismiss(AcknowledgeMethod.SWIPE, null) },
                ),
        ) {
            CriticalBadge(priority = reminder.priority)
            // TRAP-PROOF LAYOUT. Root cause of the 100%-drawing soft-lock:
            // everything lived in one centered, unbounded column, so a large
            // drawing pushed the actions off-screen with no way to scroll.
            // Now the layout is two zones: a scrollable, weight-bounded
            // content area, and a **reserved action area pinned below it**
            // that no content can ever cover or displace.
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(
                        horizontal = MaterialTheme.spacing.small,
                        vertical = MaterialTheme.spacing.large,
                    ),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    if (reminder.drawingPosition == DrawingPosition.TOP) {
                        AlertDrawing(alert = alert, below = false)
                    }
                    Column(
                        modifier = Modifier
                            .widthIn(max = 480.dp)
                            .padding(horizontal = MaterialTheme.spacing.medium),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            text = reminder.title,
                            style = MaterialTheme.typography.headlineLarge,
                            color = spec.contentColor,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Assertive },
                        )
                        if (reminder.description.isNotBlank()) {
                            Spacer(modifier = Modifier.height(MaterialTheme.spacing.medium))
                            Text(
                                text = reminder.description,
                                style = MaterialTheme.typography.bodyLarge,
                                color = spec.contentColor.copy(alpha = 0.85f),
                                textAlign = TextAlign.Center,
                                maxLines = 5,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        AlertChecklistBlock(alert = alert, checked = checkedItems)
                    }
                    if (reminder.drawingPosition != DrawingPosition.TOP) {
                        AlertDrawing(alert = alert, below = true)
                    }
                }
                // Reserved action zone: always laid out, always tappable.
                Column(
                    modifier = Modifier
                        .widthIn(max = 480.dp)
                        .padding(top = MaterialTheme.spacing.medium),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    AlertActions(
                        alert = alert,
                        checklistRemaining = checklistRemaining,
                        lockRemaining = lockRemaining,
                        onDismiss = onDismiss,
                        onSnooze = onSnooze,
                        compact = false,
                    )
                    AutoDismissIndicator(
                        alert = alert,
                        modifier = Modifier.padding(top = MaterialTheme.spacing.medium),
                    )
                }
            }
        }
    }
}

// endregion

// region Floating card

/**
 * The floating card itself, position-agnostic: the in-app host aligns it in
 * a full-size box; the overlay engine places it via window gravity.
 * Internal for exactly that reuse.
 */
@Composable
internal fun FloatingAlertCard(
    alert: ActiveAlert,
    onDismiss: (AcknowledgeMethod, ReminderDrawing?) -> Unit,
    onSnooze: (Duration) -> Unit,
    modifier: Modifier = Modifier,
) {
    val reminder = alert.reminder
    val spec = reminder.theme.spec
    val checkedItems = rememberChecklistChecked(alert)
    val checklistRemaining = reminder.checklist.size - checkedItems.count { it }
    val lockRemaining = rememberDismissLockSeconds(alert)
    val acknowledgeReady = checklistRemaining == 0 && lockRemaining <= 0
    Box(
        modifier = modifier
            .widthIn(max = AlertDefaults.floatingCardWidth(reminder.floatingCardSize))
            .fillMaxWidth()
            .swipeDismissable(
                enabled = reminder.acknowledgement == AcknowledgementType.SWIPE &&
                    acknowledgeReady,
                direction = reminder.swipeDirection,
                onDismissed = { onDismiss(AcknowledgeMethod.SWIPE, null) },
            )
            .shadow(elevation = 6.dp, shape = MaterialTheme.shapes.large)
            .clip(MaterialTheme.shapes.large)
            .animatedThemeBackground(reminder.theme)
            .tapAcknowledgeable(
                enabled = reminder.acknowledgement == AcknowledgementType.TAP &&
                    acknowledgeReady,
                onAcknowledged = { onDismiss(reminder.tapMethod(), null) },
            ),
    ) {
        Column(
            modifier = Modifier.padding(MaterialTheme.spacing.large),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Trap-proof, like the full-screen layout: everything above the
            // actions scrolls inside a bounded strip so the dismiss/snooze
            // area can never be pushed off the card (or the screen).
            Column(
                modifier = Modifier
                    .heightIn(max = 360.dp)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = reminder.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = spec.contentColor,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                )
                if (reminder.description.isNotBlank()) {
                    Spacer(modifier = Modifier.height(MaterialTheme.spacing.extraSmall))
                    Text(
                        text = reminder.description,
                        style = MaterialTheme.typography.bodyMedium,
                        color = spec.contentColor.copy(alpha = 0.85f),
                        textAlign = TextAlign.Center,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (reminder.floatingCardSize != FloatingCardSize.SMALL) {
                    AlertDrawing(alert = alert, below = true)
                }
                AlertChecklistBlock(alert = alert, checked = checkedItems)
            }
            Spacer(modifier = Modifier.height(MaterialTheme.spacing.medium))
            AlertActions(
                alert = alert,
                checklistRemaining = checklistRemaining,
                lockRemaining = lockRemaining,
                onDismiss = onDismiss,
                onSnooze = onSnooze,
                compact = true,
            )
            AutoDismissIndicator(
                alert = alert,
                modifier = Modifier.padding(top = MaterialTheme.spacing.medium),
            )
        }
    }
}

// endregion

// region Shared content, actions, countdowns, gestures

/**
 * The reminder's hand drawing, sized by its configured [DrawingSize]
 * fraction of the alert content width.
 */
@Composable
private fun AlertDrawing(alert: ActiveAlert, below: Boolean) {
    val drawing = alert.reminder.drawing ?: return
    if (drawing.isEmpty) return
    if (below) Spacer(modifier = Modifier.height(MaterialTheme.spacing.medium))
    DrawingView(
        drawing = drawing,
        modifier = Modifier
            .fillMaxWidth(alert.reminder.drawingSize.fraction)
            .clip(MaterialTheme.shapes.medium),
    )
    if (!below) Spacer(modifier = Modifier.height(MaterialTheme.spacing.medium))
}

/**
 * Per-presentation checked state for a checklist alert, keyed to the queue
 * entry so a re-trigger of the same reminder starts unchecked again.
 */
@Composable
private fun rememberChecklistChecked(alert: ActiveAlert): SnapshotStateList<Boolean> =
    remember(alert.entryId) {
        List(alert.reminder.checklist.size) { false }.toMutableStateList()
    }

/** Tap-to-dismiss records CHECKLIST when a completed checklist was the gate. */
private fun Reminder.tapMethod(): AcknowledgeMethod =
    if (checklist.isNotEmpty()) AcknowledgeMethod.CHECKLIST else AcknowledgeMethod.TAP

/**
 * The checklist reminder's item list: each row toggles with a springy check
 * animation; a progress line beneath counts completion. Acknowledgement is
 * gated elsewhere until every row is checked.
 */
@Composable
private fun AlertChecklistBlock(
    alert: ActiveAlert,
    checked: SnapshotStateList<Boolean>,
) {
    val items = alert.reminder.checklist
    if (items.isEmpty()) return
    val spec = alert.reminder.theme.spec
    Spacer(modifier = Modifier.height(MaterialTheme.spacing.large))
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small),
    ) {
        items.forEachIndexed { index, item ->
            ChecklistRow(
                text = item.text,
                checked = checked.getOrElse(index) { false },
                contentColor = spec.contentColor,
                accentColor = spec.accent,
                onToggle = { checked[index] = !checked[index] },
            )
        }
    }
    Spacer(modifier = Modifier.height(MaterialTheme.spacing.small))
    Text(
        text = stringResource(
            R.string.alert_checklist_progress,
            checked.count { it },
            items.size,
        ),
        style = MaterialTheme.typography.labelMedium,
        color = spec.contentColor.copy(alpha = 0.8f),
    )
}

@Composable
private fun ChecklistRow(
    text: String,
    checked: Boolean,
    contentColor: Color,
    accentColor: Color,
    onToggle: () -> Unit,
) {
    val checkScale by animateFloatAsState(
        targetValue = if (checked) 1f else 0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium,
        ),
        label = "checkScale",
    )
    val fillAlpha by animateFloatAsState(
        targetValue = if (checked) 0.9f else 0f,
        label = "checkFill",
    )
    val textAlpha by animateFloatAsState(
        targetValue = if (checked) 0.65f else 1f,
        label = "checkText",
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .background(contentColor.copy(alpha = if (checked) 0.08f else 0.14f))
            .toggleable(value = checked, role = Role.Checkbox, onValueChange = { onToggle() })
            .heightIn(min = 48.dp)
            .padding(
                horizontal = MaterialTheme.spacing.medium,
                vertical = MaterialTheme.spacing.small,
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(26.dp)
                .clip(CircleShape)
                .background(contentColor.copy(alpha = fillAlpha))
                .border(width = 2.dp, color = contentColor.copy(alpha = 0.9f), shape = CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Rounded.Check,
                contentDescription = null,
                tint = accentColor,
                modifier = Modifier.scale(checkScale),
            )
        }
        Spacer(modifier = Modifier.width(MaterialTheme.spacing.medium))
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge,
            color = contentColor.copy(alpha = textAlpha),
            textDecoration = if (checked) TextDecoration.LineThrough else null,
            modifier = Modifier.weight(1f),
        )
    }
}

/**
 * Snooze chips plus the acknowledgement affordance: a button for NONE/TAP,
 * the paint-the-checkmark canvas for TICK_GESTURE, the signature pad for
 * SIGNATURE, and a directional hint for SWIPE. Everything is tinted with the
 * theme's content color so it reads on any theme surface.
 */
@Composable
private fun AlertActions(
    alert: ActiveAlert,
    checklistRemaining: Int,
    lockRemaining: Long,
    onDismiss: (AcknowledgeMethod, ReminderDrawing?) -> Unit,
    onSnooze: (Duration) -> Unit,
    compact: Boolean,
) {
    val reminder = alert.reminder
    val spec = reminder.theme.spec
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(
            if (compact) MaterialTheme.spacing.small else MaterialTheme.spacing.large,
        ),
    ) {
        if (reminder.snoozeEnabled && reminder.allowedSnoozeDurations.isNotEmpty()) {
            SnoozeChipRow(
                durations = reminder.allowedSnoozeDurations,
                compact = compact,
                accentColor = spec.contentColor,
                onSnooze = onSnooze,
            )
        }
        // Checklist gate: while items are unchecked no acknowledgement
        // affordance appears — only a disabled pill saying what's left.
        // Snoozing above stays available.
        if (checklistRemaining > 0) {
            AlertPillButton(
                label = pluralStringResource(
                    R.plurals.alert_checklist_locked,
                    checklistRemaining,
                    checklistRemaining,
                ),
                enabled = false,
                spec = spec,
                compact = compact,
                onClick = {},
            )
            return@Column
        }
        // Dismiss lock: gates every acknowledgement affordance, gestures
        // included — a countdown that only disabled the button was trivially
        // bypassed by tap/swipe/tick/signature.
        if (lockRemaining > 0) {
            AlertPillButton(
                label = stringResource(R.string.alert_dismiss_locked, lockRemaining),
                enabled = false,
                spec = spec,
                compact = compact,
                onClick = {},
            )
            return@Column
        }
        when (reminder.acknowledgement) {
            AcknowledgementType.TICK_GESTURE -> {
                GesturePanel(compact = compact) {
                    TickGestureCanvas(
                        onAcknowledged = { onDismiss(AcknowledgeMethod.TICK_GESTURE, null) },
                    )
                }
                GestureHint(text = stringResource(R.string.alert_tick_hint), spec = spec)
            }

            AcknowledgementType.SIGNATURE -> {
                GesturePanel(compact = compact) {
                    SignaturePad(
                        inkColor = MaterialTheme.colorScheme.onSurface,
                        onAcknowledged = { signature ->
                            onDismiss(AcknowledgeMethod.SIGNATURE, signature)
                        },
                    )
                }
                GestureHint(text = stringResource(R.string.alert_signature_hint), spec = spec)
            }

            AcknowledgementType.PHOTO -> {
                ProofCaptureButton(
                    reminderId = reminder.id,
                    method = AcknowledgeMethod.PHOTO,
                    mode = com.alertnotes.ProofCaptureActivity.MODE_PHOTO,
                    label = stringResource(R.string.alert_photo_capture),
                    spec = spec,
                    compact = compact,
                    onDismiss = onDismiss,
                )
                GestureHint(text = stringResource(R.string.alert_photo_hint), spec = spec)
            }

            AcknowledgementType.LOCATION -> {
                ProofCaptureButton(
                    reminderId = reminder.id,
                    method = AcknowledgeMethod.LOCATION,
                    mode = com.alertnotes.ProofCaptureActivity.MODE_LOCATION,
                    label = stringResource(R.string.alert_location_capture),
                    spec = spec,
                    compact = compact,
                    onDismiss = onDismiss,
                )
                GestureHint(text = stringResource(R.string.alert_location_hint), spec = spec)
            }

            AcknowledgementType.SWIPE -> {
                GestureHint(
                    text = stringResource(reminder.swipeDirection.hintRes()),
                    spec = spec,
                )
            }

            else -> {
                val label = when {
                    reminder.checklist.isNotEmpty() ->
                        stringResource(R.string.alert_checklist_complete)

                    reminder.acknowledgement == AcknowledgementType.NONE ->
                        stringResource(R.string.alert_dismiss)

                    else -> stringResource(R.string.alert_acknowledge)
                }
                val buttonMethod = when {
                    reminder.checklist.isNotEmpty() -> AcknowledgeMethod.CHECKLIST
                    reminder.acknowledgement == AcknowledgementType.TAP -> AcknowledgeMethod.TAP
                    else -> AcknowledgeMethod.BUTTON
                }
                AlertPillButton(
                    label = label,
                    enabled = true,
                    spec = spec,
                    compact = compact,
                    onClick = { onDismiss(buttonMethod, null) },
                )
            }
        }
    }
}

/**
 * Warning banner pinned to the top of critical alerts — the priority's
 * badge and icon, unmistakable over any reminder theme.
 */
@Composable
private fun BoxScope.CriticalBadge(priority: ReminderPriority) {
    if (priority != ReminderPriority.CRITICAL) return
    Surface(
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.error,
        modifier = Modifier
            .align(Alignment.TopCenter)
            .padding(top = MaterialTheme.spacing.medium)
            .zIndex(1f),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(
                horizontal = MaterialTheme.spacing.medium,
                vertical = MaterialTheme.spacing.extraSmall,
            ),
        ) {
            Icon(
                imageVector = Icons.Rounded.Warning,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onError,
                modifier = Modifier.size(16.dp),
            )
            Text(
                text = stringResource(R.string.alert_critical_badge),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onError,
                modifier = Modifier.padding(start = MaterialTheme.spacing.extraSmall),
            )
        }
    }
}

/**
 * Proof-capture acknowledgement (camera or location). The capture runs in
 * [com.alertnotes.ProofCaptureActivity] — a NORMAL-launchMode task, because
 * this singleInstance alert activity cannot receive cross-task results —
 * and the outcome returns through [AcknowledgementSession], which also
 * makes the dispatcher stand down so the alert cannot re-front itself over
 * the camera. The alert is dismissed ONLY after the user confirms the
 * proof; cancelling returns to the alert exactly as it was.
 */
@Composable
private fun ProofCaptureButton(
    reminderId: Long,
    method: AcknowledgeMethod,
    mode: String,
    label: String,
    spec: ReminderThemeSpec,
    compact: Boolean,
    onDismiss: (AcknowledgeMethod, ReminderDrawing?) -> Unit,
) {
    val context = LocalContext.current
    LaunchedEffect(reminderId, method) {
        AcknowledgementSession.results.collect { result ->
            if (result.reminderId == reminderId && result.confirmed && result.method == method) {
                onDismiss(method, null)
            }
        }
    }
    AlertPillButton(
        label = label,
        enabled = true,
        spec = spec,
        compact = compact,
        onClick = {
            // One workflow at a time: a second tap (or a second method)
            // while a capture is running is ignored.
            if (AcknowledgementSession.begin(reminderId)) {
                context.startActivity(
                    com.alertnotes.ProofCaptureActivity.intent(context, reminderId, mode),
                )
            }
        },
    )
}

/** Inverted pill: content-colored fill, theme-accent label. */
@Composable
private fun AlertPillButton(
    label: String,
    enabled: Boolean,
    spec: ReminderThemeSpec,
    compact: Boolean,
    onClick: () -> Unit,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        shape = MaterialTheme.shapes.large,
        colors = ButtonDefaults.buttonColors(
            containerColor = spec.contentColor,
            contentColor = spec.accent,
            disabledContainerColor = spec.contentColor.copy(alpha = 0.4f),
            disabledContentColor = spec.accent.copy(alpha = 0.6f),
        ),
        modifier = (if (compact) Modifier else Modifier.widthIn(min = 220.dp))
            .heightIn(min = 48.dp),
    ) {
        Text(text = label, style = MaterialTheme.typography.labelLarge)
    }
}

/** Neutral inset that keeps gesture canvases readable on any theme. */
@Composable
private fun GesturePanel(
    compact: Boolean,
    content: @Composable () -> Unit,
) {
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier.fillMaxWidth(if (compact) 1f else 0.8f),
    ) {
        Box(modifier = Modifier.padding(MaterialTheme.spacing.small)) {
            content()
        }
    }
}

@Composable
private fun GestureHint(text: String, spec: ReminderThemeSpec) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = spec.contentColor.copy(alpha = 0.8f),
    )
}

private fun SwipeDirection.hintRes(): Int = when (this) {
    SwipeDirection.UP -> R.string.alert_swipe_hint_up
    SwipeDirection.DOWN -> R.string.alert_swipe_hint_down
    SwipeDirection.LEFT -> R.string.alert_swipe_hint_left
    SwipeDirection.RIGHT -> R.string.alert_swipe_hint_right
}

/**
 * Seconds until the dismiss action unlocks; 0 when there is no lock.
 * Anchored to [ActiveAlert.shownAt] so activity recreation (rotation, the
 * lock-screen AlertActivity relaunching) cannot reset the countdown.
 */
@Composable
private fun rememberDismissLockSeconds(alert: ActiveAlert): Long {
    val lock = alert.reminder.dismissCountdown ?: return 0
    val unlockAt = alert.shownAt.plus(lock)
    val remaining by produceState(
        initialValue = remainingSecondsUntil(unlockAt),
        key1 = alert.entryId,
    ) {
        while (value > 0) {
            delay(1_000)
            value = remainingSecondsUntil(unlockAt)
        }
    }
    return remaining
}

/** Whole seconds until [at], rounded up; never negative. */
private fun remainingSecondsUntil(at: Instant): Long {
    val millis = java.time.Duration.between(Instant.now(), at).toMillis()
    return if (millis <= 0) 0 else (millis + 999) / 1_000
}

/**
 * Thin progress line plus remaining time for auto-dismissing alerts. The
 * bar animates linearly to the alert's real deadline (anchored at
 * [ActiveAlert.shownAt], matching the presenter's actual auto-dismiss
 * timer) so recreation never desyncs the indicator from the dismissal.
 */
@Composable
private fun AutoDismissIndicator(
    alert: ActiveAlert,
    modifier: Modifier = Modifier,
) {
    val duration = alert.reminder.autoDismissAfter ?: return
    val contentColor = alert.reminder.theme.spec.contentColor
    val deadline = alert.shownAt.plus(duration)
    val totalMillis = duration.toMillis().coerceAtLeast(1L)
    val progress = remember(alert.entryId) {
        val leftMillis = java.time.Duration.between(Instant.now(), deadline)
            .toMillis()
            .coerceIn(0L, totalMillis)
        Animatable(leftMillis / totalMillis.toFloat())
    }
    LaunchedEffect(alert.entryId) {
        val leftMillis = java.time.Duration.between(Instant.now(), deadline)
            .toMillis()
            .coerceAtLeast(0L)
        progress.animateTo(
            targetValue = 0f,
            animationSpec = tween(leftMillis.toInt(), easing = LinearEasing),
        )
    }
    val remainingSeconds by produceState(
        initialValue = remainingSecondsUntil(deadline),
        key1 = alert.entryId,
    ) {
        while (value > 0) {
            delay(1_000)
            value = remainingSecondsUntil(deadline)
        }
    }

    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        LinearProgressIndicator(
            progress = { progress.value },
            modifier = Modifier
                .fillMaxWidth(0.4f)
                .height(3.dp)
                .clip(CircleShape),
            color = contentColor,
            trackColor = contentColor.copy(alpha = 0.2f),
            drawStopIndicator = {},
        )
        Spacer(modifier = Modifier.height(MaterialTheme.spacing.extraSmall))
        Text(
            text = stringResource(R.string.alert_auto_dismiss_in, remainingSeconds),
            style = MaterialTheme.typography.labelSmall,
            color = contentColor.copy(alpha = 0.8f),
        )
    }
}

/**
 * Tap-anywhere acknowledgement. Applied only while actually actionable so
 * TalkBack never announces a no-op click on the whole alert, and labelled
 * so the announced action says what it does.
 */
@Composable
private fun Modifier.tapAcknowledgeable(
    enabled: Boolean,
    onAcknowledged: () -> Unit,
): Modifier {
    if (!enabled) return this
    return this.clickable(
        interactionSource = remember { MutableInteractionSource() },
        indication = null,
        onClickLabel = stringResource(R.string.alert_acknowledge),
        onClick = onAcknowledged,
    )
}

/**
 * Directional swipe-away gesture: the content follows the finger along the
 * configured direction (opposite movement is ignored), fading as it goes,
 * and either springs back or dismisses past the threshold. The logical
 * travel is tracked synchronously (the Animatable only mirrors it) so the
 * threshold check on release never reads a stale value. TalkBack users get
 * an explicit "Acknowledge" custom action instead of the gesture.
 */
@Composable
private fun Modifier.swipeDismissable(
    enabled: Boolean,
    direction: SwipeDirection,
    onDismissed: () -> Unit,
): Modifier {
    if (!enabled) return this
    val travel = remember { Animatable(0f) }
    var travelTarget by remember { mutableFloatStateOf(0f) }
    val scope = rememberCoroutineScope()
    val thresholdPx = with(LocalDensity.current) {
        AlertDefaults.SwipeDismissThreshold.toPx()
    }
    val acknowledgeLabel = stringResource(R.string.alert_acknowledge)
    return this
        .semantics {
            customActions = listOf(
                CustomAccessibilityAction(acknowledgeLabel) {
                    onDismissed()
                    true
                },
            )
        }
        .graphicsLayer {
            translationX = travel.value * direction.dx
            translationY = travel.value * direction.dy
            alpha = 1f - (travel.value / (thresholdPx * 2f)).coerceIn(0f, 0.6f)
        }
        .pointerInput(direction) {
            detectDragGestures(
                onDragEnd = {
                    if (travelTarget >= thresholdPx) {
                        onDismissed()
                    } else {
                        travelTarget = 0f
                        scope.launch {
                            travel.animateTo(0f, spring(stiffness = Spring.StiffnessMediumLow))
                        }
                    }
                },
                onDragCancel = {
                    travelTarget = 0f
                    scope.launch { travel.animateTo(0f) }
                },
                onDrag = { change, dragAmount ->
                    change.consume()
                    // Project the drag onto the configured direction.
                    val along = dragAmount.x * direction.dx + dragAmount.y * direction.dy
                    travelTarget = (travelTarget + along).coerceAtLeast(0f)
                    val target = travelTarget
                    scope.launch { travel.snapTo(target) }
                },
            )
        }
}

// endregion
