package com.alertnotes.features.reminders.editor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import com.alertnotes.R
import com.alertnotes.core.extensions.toCountdownString
import com.alertnotes.core.extensions.toDisplayDateTime
import com.alertnotes.core.extensions.toDisplayString
import com.alertnotes.core.extensions.toRelativeTimeString
import com.alertnotes.core.ui.components.CountdownText
import com.alertnotes.core.ui.components.SectionCard
import com.alertnotes.core.ui.theme.spacing
import com.alertnotes.domain.model.Recurrence
import com.alertnotes.domain.model.Reminder

/**
 * Read-only live overview shown when editing an existing reminder: next and
 * last trigger, the remaining countdown, and a human schedule summary.
 */
@Composable
fun StatusSection(
    draft: Reminder,
    modifier: Modifier = Modifier,
) {
    SectionCard(title = stringResource(R.string.editor_section_status), modifier = modifier) {
        Column(
            modifier = Modifier.padding(MaterialTheme.spacing.large),
            verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.medium),
        ) {
            StatusRow(label = stringResource(R.string.editor_status_next)) {
                val nextTrigger = draft.nextTriggerAt
                if (nextTrigger != null) {
                    Column(horizontalAlignment = Alignment.End) {
                        CountdownText(
                            target = nextTrigger,
                            fallback = stringResource(R.string.countdown_due_now),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Text(
                            text = nextTrigger.toDisplayDateTime(draft.timeZone),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                } else {
                    StatusValue(text = stringResource(R.string.editor_status_none))
                }
            }
            StatusRow(label = stringResource(R.string.editor_status_last)) {
                StatusValue(
                    text = draft.lastTriggeredAt?.toRelativeTimeString()
                        ?: stringResource(R.string.editor_status_never),
                )
            }
            StatusRow(label = stringResource(R.string.editor_status_schedule)) {
                StatusValue(text = draft.recurrence.toSummary(draft))
            }
            StatusRow(label = stringResource(R.string.editor_priority)) {
                StatusValue(text = stringResource(draft.priority.labelRes()))
            }
            StatusRow(label = stringResource(R.string.editor_history)) {
                StatusValue(
                    text = stringResource(
                        if (draft.historyEnabled) R.string.common_on else R.string.common_off,
                    ),
                )
            }
        }
    }
}

@Composable
private fun StatusRow(
    label: String,
    value: @Composable () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        value()
    }
}

@Composable
private fun StatusValue(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        textAlign = TextAlign.End,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
    )
}

/** Human-readable schedule summary, e.g. "Daily at 9:00 AM". */
@Composable
fun Recurrence.toSummary(reminder: Reminder): String = when (this) {
    is Recurrence.None -> stringResource(R.string.recurrence_none)

    is Recurrence.OneTime -> triggerAt.toDisplayDateTime(reminder.timeZone)

    is Recurrence.EveryMinutes -> stringResource(
        R.string.schedule_every,
        pluralStringResource(R.plurals.duration_minutes, minutes.toInt(), minutes.toInt()),
    )

    is Recurrence.EveryHours -> stringResource(
        R.string.schedule_every,
        pluralStringResource(R.plurals.duration_hours, hours.toInt(), hours.toInt()),
    )

    is Recurrence.CustomInterval -> stringResource(
        R.string.schedule_every,
        interval.toCountdownString(),
    )

    is Recurrence.Daily -> stringResource(R.string.schedule_daily, timeOfDay.toDisplayString())

    is Recurrence.Weekly -> stringResource(R.string.schedule_weekly, timeOfDay.toDisplayString())

    is Recurrence.Monthly -> stringResource(
        R.string.schedule_monthly,
        dayOfMonth,
        timeOfDay.toDisplayString(),
    )
}
