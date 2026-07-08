package com.alertnotes.widgets

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.LocalSize
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.alertnotes.MainActivity
import com.alertnotes.R
import com.alertnotes.core.extensions.toCountdownString
import com.alertnotes.createReminderIntent
import com.alertnotes.domain.repository.ReminderRepository
import com.alertnotes.domain.scheduling.OccurrenceProjector
import com.alertnotes.features.alerts.spec
import com.alertnotes.features.calendar.toOccurrence
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import androidx.compose.ui.unit.DpSize
import kotlinx.coroutines.flow.first

class AlertNotesWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = AlertNotesWidget()
}

/** Snapshot the widget renders from; recomputed on every widget update. */
private data class WidgetData(
    val nextTitle: String?,
    val nextAt: Instant?,
    val nextAccent: Color,
    val today: List<Pair<String, String>>,
    val todayCount: Int,
    val now: Instant,
)

@EntryPoint
@InstallIn(SingletonComponent::class)
interface WidgetEntryPoint {
    fun reminderRepository(): ReminderRepository
    fun occurrenceProjector(): OccurrenceProjector
}

/**
 * The Alert Notes home-screen widget, responsive across three layouts:
 * small (next reminder + countdown), medium (adds quick actions), and large
 * (adds today's schedule). Updated whenever schedules change, alarms fire,
 * or the device boots — no polling service, no battery cost.
 */
class AlertNotesWidget : GlanceAppWidget() {

    override val sizeMode: SizeMode = SizeMode.Responsive(
        setOf(SMALL, MEDIUM, LARGE),
    )

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val data = loadData(context)
        provideContent {
            GlanceTheme {
                WidgetContent(context = context, data = data)
            }
        }
    }

    private suspend fun loadData(context: Context): WidgetData {
        val entryPoint = EntryPointAccessors.fromApplication(
            context.applicationContext,
            WidgetEntryPoint::class.java,
        )
        val repository = entryPoint.reminderRepository()
        val projector = entryPoint.occurrenceProjector()
        val zone = ZoneId.systemDefault()
        val now = Instant.now()
        val today = now.atZone(zone).toLocalDate()

        val next = repository.observeUpcoming(1).first().firstOrNull()
        val todayOccurrences = repository.getSchedulableReminders()
            .flatMap { reminder ->
                projector.occurrencesBetween(
                    reminder = reminder,
                    from = now,
                    until = today.plusDays(1).atStartOfDay(zone).toInstant(),
                    maxOccurrences = 20,
                ).map { reminder.toOccurrence(it, zone) }
            }
            .sortedBy { it.at }

        val timeFormatter = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)
        return WidgetData(
            nextTitle = next?.title,
            nextAt = next?.nextTriggerAt,
            nextAccent = next?.theme?.spec?.accent ?: Color(0xFFE4572E),
            today = todayOccurrences.take(4).map { it.time.format(timeFormatter) to it.reminder.title },
            todayCount = todayOccurrences.size,
            now = now,
        )
    }

    companion object {
        private val SMALL = DpSize(140.dp, 60.dp)
        private val MEDIUM = DpSize(220.dp, 120.dp)
        private val LARGE = DpSize(220.dp, 240.dp)
    }
}

@Composable
private fun WidgetContent(context: Context, data: WidgetData) {
    val size = LocalSize.current
    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(GlanceTheme.colors.widgetBackground)
            .cornerRadius(20.dp)
            .padding(12.dp)
            .clickable(actionStartActivity(Intent(context, MainActivity::class.java))),
    ) {
        NextReminderBlock(data)
        if (size.height >= 120.dp) {
            Spacer(modifier = GlanceModifier.height(8.dp))
            ActionRow(context)
        }
        if (size.height >= 240.dp) {
            Spacer(modifier = GlanceModifier.height(8.dp))
            TodayBlock(context, data)
        }
    }
}

@Composable
private fun NextReminderBlock(data: WidgetData) {
    val title = data.nextTitle
    val at = data.nextAt
    Column(modifier = GlanceModifier.fillMaxWidth()) {
        Text(
            text = title
                ?: androidx.glance.LocalContext.current.getString(R.string.widget_no_upcoming),
            style = TextStyle(
                color = GlanceTheme.colors.onSurface,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
            ),
            maxLines = 1,
        )
        if (title != null && at != null) {
            val remaining = Duration.between(data.now, at)
            Text(
                text = if (remaining.isNegative) {
                    androidx.glance.LocalContext.current.getString(R.string.countdown_due_now)
                } else {
                    androidx.glance.LocalContext.current.getString(
                        R.string.widget_next_in,
                        remaining.toCountdownString(),
                    )
                },
                style = TextStyle(
                    color = ColorProvider(data.nextAccent),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                ),
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun ActionRow(context: Context) {
    Row(
        modifier = GlanceModifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = androidx.glance.LocalContext.current.getString(R.string.widget_new_reminder),
            style = TextStyle(
                color = GlanceTheme.colors.primary,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
            ),
            modifier = GlanceModifier
                .background(GlanceTheme.colors.secondaryContainer)
                .cornerRadius(12.dp)
                .padding(horizontal = 10.dp, vertical = 6.dp)
                .clickable(actionStartActivity(createReminderIntent(context))),
        )
        Spacer(modifier = GlanceModifier.width(8.dp))
        Text(
            text = androidx.glance.LocalContext.current.getString(R.string.widget_open_app),
            style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 13.sp),
            modifier = GlanceModifier
                .padding(horizontal = 6.dp, vertical = 6.dp)
                .clickable(actionStartActivity(Intent(context, MainActivity::class.java))),
        )
    }
}

@Composable
private fun TodayBlock(context: Context, data: WidgetData) {
    Column(modifier = GlanceModifier.fillMaxWidth()) {
        Text(
            text = androidx.glance.LocalContext.current.getString(
                R.string.widget_today_count,
                data.todayCount,
            ),
            style = TextStyle(
                color = GlanceTheme.colors.onSurfaceVariant,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
            ),
        )
        data.today.forEach { (time, title) ->
            Spacer(modifier = GlanceModifier.height(4.dp))
            Row(modifier = GlanceModifier.fillMaxWidth()) {
                Text(
                    text = time,
                    style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 12.sp),
                    modifier = GlanceModifier.width(64.dp),
                )
                Text(
                    text = title,
                    style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 13.sp),
                    maxLines = 1,
                )
            }
        }
    }
}
