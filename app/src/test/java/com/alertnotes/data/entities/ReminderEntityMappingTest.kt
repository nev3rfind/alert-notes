package com.alertnotes.data.entities

import com.alertnotes.domain.model.AcknowledgementType
import com.alertnotes.domain.model.ActiveHours
import com.alertnotes.domain.model.DisplayMode
import com.alertnotes.domain.model.DrawingPoint
import com.alertnotes.domain.model.DrawingStroke
import com.alertnotes.domain.model.FloatingCardPosition
import com.alertnotes.domain.model.FloatingCardSize
import com.alertnotes.domain.model.OverlayPreference
import com.alertnotes.domain.model.Recurrence
import com.alertnotes.domain.model.Reminder
import com.alertnotes.domain.model.ReminderDrawing
import com.alertnotes.domain.model.ReminderPriority
import com.alertnotes.domain.model.ReminderTheme
import com.alertnotes.domain.model.ReminderType
import com.alertnotes.domain.model.StrokeTool
import com.alertnotes.domain.model.SwipeDirection
import java.time.DayOfWeek
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Test

/** Entity ⇄ domain mapping must be lossless for every persisted field. */
class ReminderEntityMappingTest {

    @Test
    fun `fully populated reminder round-trips through the entity`() {
        val original = Reminder(
            id = 42,
            title = "Take medication",
            description = "Two pills with water",
            type = ReminderType.DRAWING,
            drawing = ReminderDrawing(
                strokes = listOf(
                    DrawingStroke(
                        points = listOf(DrawingPoint(0.1f, 0.2f), DrawingPoint(0.8f, 0.9f)),
                        colorArgb = 0xFFE4572EL.toInt().toLong(),
                        widthFraction = 0.02f,
                        tool = StrokeTool.HIGHLIGHTER,
                    ),
                ),
                backgroundArgb = 0xFFFFFFFFL.toInt().toLong(),
            ),
            isEnabled = true,
            isArchived = false,
            priority = ReminderPriority.CRITICAL,
            theme = ReminderTheme.PURPLE_GRADIENT,
            swipeDirection = SwipeDirection.LEFT,
            displayMode = DisplayMode.FLOATING_CARD,
            floatingCardPosition = FloatingCardPosition.BOTTOM_RIGHT,
            floatingCardSize = FloatingCardSize.LARGE,
            autoDismissAfter = Duration.ofSeconds(90),
            acknowledgement = AcknowledgementType.SIGNATURE,
            dismissCountdown = Duration.ofSeconds(10),
            requiresBiometric = true,
            biometricPinFallback = false,
            snoozeEnabled = true,
            allowedSnoozeDurations = listOf(Duration.ofMinutes(5), Duration.ofMinutes(15)),
            historyEnabled = false,
            vibrationEnabled = false,
            soundEnabled = true,
            wakeScreen = true,
            showOnLockScreen = false,
            overlayPreference = OverlayPreference.PREFER_OVERLAY,
            recurrence = Recurrence.Monthly(31, LocalTime.of(8, 30)),
            activeDays = setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.SUNDAY),
            activeHours = ActiveHours(LocalTime.of(7, 0), LocalTime.of(22, 30)),
            startDate = LocalDate.parse("2026-07-01"),
            endDate = LocalDate.parse("2026-12-31"),
            timeZone = ZoneId.of("Europe/Vilnius"),
            nextTriggerAt = Instant.parse("2026-07-31T05:30:00Z"),
            lastTriggeredAt = Instant.parse("2026-06-30T05:30:00Z"),
            createdAt = Instant.parse("2026-06-01T12:00:00Z"),
            updatedAt = Instant.parse("2026-07-01T12:00:00Z"),
        )

        assertEquals(original, original.toEntity().toDomain())
    }

    @Test
    fun `every recurrence kind round-trips`() {
        val kinds = listOf(
            Recurrence.None,
            Recurrence.OneTime(Instant.parse("2026-07-06T10:00:00Z")),
            Recurrence.EveryMinutes(45),
            Recurrence.EveryHours(6),
            Recurrence.CustomInterval(Duration.ofMinutes(135)),
            Recurrence.Daily(LocalTime.of(9, 15)),
            Recurrence.Weekly(LocalTime.of(18, 0)),
            Recurrence.Monthly(15, LocalTime.NOON),
        )

        kinds.forEach { recurrence ->
            val reminder = minimalReminder(recurrence)
            assertEquals(recurrence, reminder.toEntity().toDomain().recurrence)
        }
    }

    @Test
    fun `legacy three-value floating positions map to their centered equivalents`() {
        assertEquals(FloatingCardPosition.TOP_CENTER, "TOP".toFloatingCardPosition())
        assertEquals(FloatingCardPosition.BOTTOM_CENTER, "BOTTOM".toFloatingCardPosition())
        assertEquals(FloatingCardPosition.CENTER, "CENTER".toFloatingCardPosition())
        assertEquals(FloatingCardPosition.TOP_LEFT, "TOP_LEFT".toFloatingCardPosition())
        assertEquals(FloatingCardPosition.CENTER, "GARBAGE".toFloatingCardPosition())
    }

    @Test
    fun `active day mask covers all weekday combinations`() {
        val combinations = listOf(
            emptySet(),
            setOf(DayOfWeek.MONDAY),
            setOf(DayOfWeek.SUNDAY),
            setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY),
            Reminder.ALL_DAYS,
        )

        combinations.forEach { days ->
            assertEquals(days, days.toMask().toActiveDays())
        }
    }

    private fun minimalReminder(recurrence: Recurrence): Reminder = Reminder(
        id = 1,
        title = "Test",
        recurrence = recurrence,
        timeZone = ZoneId.of("UTC"),
        createdAt = Instant.parse("2026-07-06T00:00:00Z"),
        updatedAt = Instant.parse("2026-07-06T00:00:00Z"),
    )
}
