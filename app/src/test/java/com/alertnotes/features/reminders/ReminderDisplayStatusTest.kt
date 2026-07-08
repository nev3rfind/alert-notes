package com.alertnotes.features.reminders

import com.alertnotes.domain.model.Recurrence
import com.alertnotes.domain.model.Reminder
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Test

class ReminderDisplayStatusTest {

    private val now: Instant = Instant.parse("2026-07-07T12:00:00Z")

    @Test
    fun `queue membership wins over everything else`() {
        val reminder = reminder(id = 1, nextTriggerAt = now.plusSeconds(60))

        assertEquals(
            ReminderDisplayStatus.RUNNING,
            reminder.displayStatus(runningReminderId = 1, queuedReminderIds = emptySet(), now = now),
        )
        assertEquals(
            ReminderDisplayStatus.QUEUED,
            reminder.displayStatus(runningReminderId = 9, queuedReminderIds = setOf(1), now = now),
        )
    }

    @Test
    fun `disabled reminders report disabled`() {
        val reminder = reminder(id = 2, isEnabled = false, nextTriggerAt = now.plusSeconds(60))

        assertEquals(ReminderDisplayStatus.DISABLED, statusOf(reminder))
    }

    @Test
    fun `near triggers are upcoming and distant ones are waiting`() {
        val soon = reminder(id = 3, nextTriggerAt = now.plus(Duration.ofHours(2)))
        val distant = reminder(id = 4, nextTriggerAt = now.plus(Duration.ofHours(48)))

        assertEquals(ReminderDisplayStatus.UPCOMING, statusOf(soon))
        assertEquals(ReminderDisplayStatus.WAITING, statusOf(distant))
    }

    @Test
    fun `a fired one-time reminder is completed`() {
        val reminder = reminder(
            id = 5,
            recurrence = Recurrence.OneTime(now.minusSeconds(3_600)),
            nextTriggerAt = null,
            lastTriggeredAt = now.minusSeconds(3_600),
        )

        assertEquals(ReminderDisplayStatus.COMPLETED, statusOf(reminder))
    }

    @Test
    fun `no future occurrence without a firing history is expired`() {
        val neverFired = reminder(
            id = 6,
            recurrence = Recurrence.OneTime(now.minusSeconds(3_600)),
            nextTriggerAt = null,
        )
        val unscheduled = reminder(id = 7, recurrence = Recurrence.None, nextTriggerAt = null)

        assertEquals(ReminderDisplayStatus.EXPIRED, statusOf(neverFired))
        assertEquals(ReminderDisplayStatus.EXPIRED, statusOf(unscheduled))
    }

    private fun statusOf(reminder: Reminder): ReminderDisplayStatus =
        reminder.displayStatus(runningReminderId = null, queuedReminderIds = emptySet(), now = now)

    private fun reminder(
        id: Long,
        isEnabled: Boolean = true,
        recurrence: Recurrence = Recurrence.Daily(java.time.LocalTime.NOON),
        nextTriggerAt: Instant?,
        lastTriggeredAt: Instant? = null,
    ): Reminder = Reminder(
        id = id,
        title = "Test",
        isEnabled = isEnabled,
        recurrence = recurrence,
        nextTriggerAt = nextTriggerAt,
        lastTriggeredAt = lastTriggeredAt,
        timeZone = ZoneId.of("UTC"),
        createdAt = now.minusSeconds(86_400),
        updatedAt = now.minusSeconds(86_400),
    )
}
