package com.alertnotes.domain.scheduling

import com.alertnotes.domain.model.ActiveHours
import com.alertnotes.domain.model.Recurrence
import com.alertnotes.domain.model.Reminder
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The calculator is the heart of the reminder engine — every scheduling path
 * is covered here on the JVM with a fixed zone and fixed instants.
 *
 * Reference point: 2026-07-06 is a Monday.
 */
class NextTriggerCalculatorTest {

    private val calculator = NextTriggerCalculator()
    private val zone = ZoneId.of("UTC")
    private val monday = Instant.parse("2026-07-06T00:00:00Z")

    // region One-time

    @Test
    fun `one-time in the future fires at its exact moment`() {
        val triggerAt = Instant.parse("2026-07-08T15:30:00Z")
        val reminder = reminder(Recurrence.OneTime(triggerAt))

        assertEquals(triggerAt, calculator.nextTrigger(reminder, after = monday))
    }

    @Test
    fun `one-time in the past never fires again`() {
        val reminder = reminder(Recurrence.OneTime(Instant.parse("2026-07-01T09:00:00Z")))

        assertNull(calculator.nextTrigger(reminder, after = monday))
    }

    @Test
    fun `one-time ignores active hours by design`() {
        val triggerAt = Instant.parse("2026-07-06T20:00:00Z")
        val reminder = reminder(
            Recurrence.OneTime(triggerAt),
            activeHours = ActiveHours(LocalTime.of(8, 0), LocalTime.of(18, 0)),
        )

        assertEquals(triggerAt, calculator.nextTrigger(reminder, after = monday))
    }

    // endregion

    // region Enable / archive gates

    @Test
    fun `disabled reminder has no next trigger`() {
        val reminder = reminder(
            Recurrence.OneTime(Instant.parse("2026-07-08T15:30:00Z")),
            isEnabled = false,
        )

        assertNull(calculator.nextTrigger(reminder, after = monday))
    }

    @Test
    fun `unscheduled reminder has no next trigger`() {
        assertNull(calculator.nextTrigger(reminder(Recurrence.None), after = monday))
    }

    // endregion

    // region Daily / weekly

    @Test
    fun `daily fires later the same day when its time is still ahead`() {
        val reminder = reminder(Recurrence.Daily(LocalTime.of(9, 0)))
        val after = Instant.parse("2026-07-06T08:00:00Z")

        assertEquals(
            Instant.parse("2026-07-06T09:00:00Z"),
            calculator.nextTrigger(reminder, after),
        )
    }

    @Test
    fun `daily rolls to the next day once its time has passed`() {
        val reminder = reminder(Recurrence.Daily(LocalTime.of(9, 0)))
        val after = Instant.parse("2026-07-06T10:00:00Z")

        assertEquals(
            Instant.parse("2026-07-07T09:00:00Z"),
            calculator.nextTrigger(reminder, after),
        )
    }

    @Test
    fun `daily skips inactive days`() {
        val reminder = reminder(
            Recurrence.Daily(LocalTime.of(9, 0)),
            activeDays = setOf(DayOfWeek.MONDAY),
        )
        val after = Instant.parse("2026-07-06T10:00:00Z")

        assertEquals(
            Instant.parse("2026-07-13T09:00:00Z"),
            calculator.nextTrigger(reminder, after),
        )
    }

    @Test
    fun `daily whose time lies outside active hours can never fire`() {
        val reminder = reminder(
            Recurrence.Daily(LocalTime.of(20, 0)),
            activeHours = ActiveHours(LocalTime.of(8, 0), LocalTime.of(18, 0)),
        )

        assertNull(calculator.nextTrigger(reminder, after = monday))
    }

    @Test
    fun `daily stops after its end date`() {
        val reminder = reminder(
            Recurrence.Daily(LocalTime.of(9, 0)),
            endDate = LocalDate.parse("2026-07-06"),
        )
        val after = Instant.parse("2026-07-06T10:00:00Z")

        assertNull(calculator.nextTrigger(reminder, after))
    }

    @Test
    fun `daily waits for its start date`() {
        val reminder = reminder(
            Recurrence.Daily(LocalTime.of(9, 0)),
            startDate = LocalDate.parse("2026-07-10"),
        )

        assertEquals(
            Instant.parse("2026-07-10T09:00:00Z"),
            calculator.nextTrigger(reminder, after = monday),
        )
    }

    @Test
    fun `weekly fires only on selected days`() {
        val reminder = reminder(
            Recurrence.Weekly(LocalTime.of(9, 0)),
            activeDays = setOf(DayOfWeek.TUESDAY, DayOfWeek.FRIDAY),
        )
        val after = Instant.parse("2026-07-06T10:00:00Z")

        assertEquals(
            Instant.parse("2026-07-07T09:00:00Z"),
            calculator.nextTrigger(reminder, after),
        )
    }

    // endregion

    // region Intervals

    @Test
    fun `interval ticks on a fixed grid anchored at creation`() {
        val reminder = reminder(Recurrence.EveryMinutes(15), createdAt = monday)
        val after = Instant.parse("2026-07-06T00:20:00Z")

        assertEquals(
            Instant.parse("2026-07-06T00:30:00Z"),
            calculator.nextTrigger(reminder, after),
        )
    }

    @Test
    fun `interval anchors on the last trigger once one exists`() {
        val reminder = reminder(
            Recurrence.EveryHours(2),
            createdAt = monday,
            lastTriggeredAt = Instant.parse("2026-07-06T05:00:00Z"),
        )
        val after = Instant.parse("2026-07-06T05:00:00Z")

        assertEquals(
            Instant.parse("2026-07-06T07:00:00Z"),
            calculator.nextTrigger(reminder, after),
        )
    }

    @Test
    fun `interval skips occurrences outside active hours`() {
        val reminder = reminder(
            Recurrence.EveryHours(1),
            activeHours = ActiveHours(LocalTime.of(8, 0), LocalTime.of(10, 0)),
            createdAt = monday,
        )
        val after = Instant.parse("2026-07-06T11:00:00Z")

        assertEquals(
            Instant.parse("2026-07-07T08:00:00Z"),
            calculator.nextTrigger(reminder, after),
        )
    }

    @Test
    fun `overnight active hours admit occurrences on both sides of midnight`() {
        val reminder = reminder(
            Recurrence.EveryHours(1),
            activeHours = ActiveHours(LocalTime.of(22, 0), LocalTime.of(6, 0)),
            createdAt = monday,
        )
        val after = Instant.parse("2026-07-06T20:30:00Z")

        assertEquals(
            Instant.parse("2026-07-06T22:00:00Z"),
            calculator.nextTrigger(reminder, after),
        )
    }

    // endregion

    // region Monthly

    @Test
    fun `monthly clamps day 31 to shorter months`() {
        val reminder = reminder(Recurrence.Monthly(31, LocalTime.of(9, 0)))
        val after = Instant.parse("2026-01-31T10:00:00Z")

        assertEquals(
            Instant.parse("2026-02-28T09:00:00Z"),
            calculator.nextTrigger(reminder, after),
        )
    }

    @Test
    fun `monthly respects the date range`() {
        val reminder = reminder(
            Recurrence.Monthly(15, LocalTime.of(9, 0)),
            endDate = LocalDate.parse("2026-07-14"),
        )
        val after = Instant.parse("2026-07-06T00:00:00Z")

        assertNull(calculator.nextTrigger(reminder, after))
    }

    // endregion

    // region Time zones

    @Test
    fun `time of day is interpreted in the reminder's own zone`() {
        val vilnius = ZoneId.of("Europe/Vilnius") // UTC+3 in July
        val reminder = reminder(Recurrence.Daily(LocalTime.of(9, 0)), timeZone = vilnius)
        val after = Instant.parse("2026-07-06T00:00:00Z")

        assertEquals(
            Instant.parse("2026-07-06T06:00:00Z"),
            calculator.nextTrigger(reminder, after),
        )
    }

    // endregion

    private fun reminder(
        recurrence: Recurrence,
        isEnabled: Boolean = true,
        activeDays: Set<DayOfWeek> = Reminder.ALL_DAYS,
        activeHours: ActiveHours? = null,
        startDate: LocalDate? = null,
        endDate: LocalDate? = null,
        lastTriggeredAt: Instant? = null,
        createdAt: Instant = monday,
        timeZone: ZoneId = zone,
    ): Reminder = Reminder(
        id = 1,
        title = "Test reminder",
        recurrence = recurrence,
        isEnabled = isEnabled,
        activeDays = activeDays,
        activeHours = activeHours,
        startDate = startDate,
        endDate = endDate,
        timeZone = timeZone,
        lastTriggeredAt = lastTriggeredAt,
        createdAt = createdAt,
        updatedAt = createdAt,
    )
}
