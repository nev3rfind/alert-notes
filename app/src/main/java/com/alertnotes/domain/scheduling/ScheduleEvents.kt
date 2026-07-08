package com.alertnotes.domain.scheduling

/**
 * Outbound notifications from the scheduling engine — the seam that lets
 * platform surfaces (home-screen widgets today) react to schedule changes
 * without the domain layer knowing about them.
 */
interface ScheduleEvents {

    /** Any reminder's schedule, existence, or enabled state changed. */
    fun onScheduleChanged()
}
