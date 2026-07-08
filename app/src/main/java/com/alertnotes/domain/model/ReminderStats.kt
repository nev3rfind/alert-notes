package com.alertnotes.domain.model

/** Dashboard counters, computed by a single aggregate query. */
data class ReminderStats(
    val total: Int = 0,
    val enabled: Int = 0,
    /** Enabled reminders whose next trigger falls within the stats horizon (24h). */
    val dueSoon: Int = 0,
) {
    val disabled: Int get() = total - enabled
}
