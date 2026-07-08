package com.alertnotes.domain.scheduling

import com.alertnotes.domain.model.Recurrence
import com.alertnotes.domain.model.Reminder
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OccurrenceProjectorTest {

    private val projector = OccurrenceProjector(NextTriggerCalculator())
    private val zone = ZoneId.of("UTC")

    // 2026-07-06 is a Monday.
    private val windowStart = Instant.parse("2026-07-06T00:00:00Z")
    private val windowEnd = Instant.parse("2026-07-13T00:00:00Z") // exclusive

    @Test
    fun `daily reminder appears once per day across the window`() {
        val reminder = reminder(Recurrence.Daily(LocalTime.of(9, 0)))

        val occurrences = projector.occurrencesBetween(reminder, windowStart, windowEnd)

        assertEquals(7, occurrences.size)
        assertEquals(Instant.parse("2026-07-06T09:00:00Z"), occurrences.first())
        assertEquals(Instant.parse("2026-07-12T09:00:00Z"), occurrences.last())
    }

    @Test
    fun `weekly reminder lands only on its active days`() {
        val reminder = reminder(
            Recurrence.Weekly(LocalTime.of(8, 0)),
            activeDays = setOf(DayOfWeek.MONDAY, DayOfWeek.THURSDAY),
        )

        val occurrences = projector.occurrencesBetween(reminder, windowStart, windowEnd)

        assertEquals(
            listOf(
                Instant.parse("2026-07-06T08:00:00Z"),
                Instant.parse("2026-07-09T08:00:00Z"),
            ),
            occurrences,
        )
    }

    @Test
    fun `interval reminders are capped, not unbounded`() {
        val reminder = reminder(Recurrence.EveryMinutes(1))

        val occurrences = projector.occurrencesBetween(
            reminder,
            windowStart,
            windowEnd,
            maxOccurrences = 100,
        )

        assertEquals(100, occurrences.size)
    }

    @Test
    fun `a window-start occurrence is included and window-end excluded`() {
        val reminder = reminder(Recurrence.OneTime(windowStart))

        val occurrences = projector.occurrencesBetween(reminder, windowStart, windowEnd)

        assertEquals(listOf(windowStart), occurrences)
        assertTrue(
            projector.occurrencesBetween(
                reminder(Recurrence.OneTime(windowEnd)),
                windowStart,
                windowEnd,
            ).isEmpty(),
        )
    }

    @Test
    fun `disabled reminders still project for calendar display`() {
        val reminder = reminder(Recurrence.Daily(LocalTime.NOON), isEnabled = false)

        val occurrences = projector.occurrencesBetween(reminder, windowStart, windowEnd)

        assertEquals(7, occurrences.size)
    }

    private fun reminder(
        recurrence: Recurrence,
        activeDays: Set<DayOfWeek> = Reminder.ALL_DAYS,
        isEnabled: Boolean = true,
    ): Reminder = Reminder(
        id = 1,
        title = "Test",
        recurrence = recurrence,
        activeDays = activeDays,
        isEnabled = isEnabled,
        timeZone = zone,
        createdAt = Instant.parse("2026-07-01T00:00:00Z"),
        updatedAt = Instant.parse("2026-07-01T00:00:00Z"),
    )
}
