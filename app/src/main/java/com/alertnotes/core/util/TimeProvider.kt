package com.alertnotes.core.util

import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Injectable clock. Production code never calls [Instant.now] directly, so
 * tests and future scheduling logic can substitute a controlled time source.
 */
interface TimeProvider {
    fun now(): Instant
}

@Singleton
class SystemTimeProvider @Inject constructor() : TimeProvider {
    override fun now(): Instant = Instant.now()
}
