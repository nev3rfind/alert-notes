package com.alertnotes.core.extensions

import android.text.format.DateUtils
import java.time.Instant

/**
 * Formats an [Instant] as a localized relative phrase such as
 * "5 minutes ago" or "yesterday", following the device language.
 */
fun Instant.toRelativeTimeString(): String =
    DateUtils.getRelativeTimeSpanString(
        toEpochMilli(),
        System.currentTimeMillis(),
        DateUtils.MINUTE_IN_MILLIS,
    ).toString()
