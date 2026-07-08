package com.alertnotes.core.util

import com.alertnotes.di.ApplicationScope
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.isActive
import java.time.Instant

/**
 * The app's single heartbeat: one shared flow that emits the current instant
 * once per second, for every live countdown and clock in the UI.
 *
 * - **Drift-free**: each emission sleeps until the next wall-clock second
 *   boundary instead of a fixed 1000ms, so ticks never accumulate error.
 * - **Battery-friendly**: `WhileSubscribed` stops the loop entirely when no
 *   countdown is visible (screens gone or app in background — collectors use
 *   `collectAsStateWithLifecycle`).
 * - **Shared**: any number of on-screen countdowns cost exactly one timer.
 */
@Singleton
class SecondTicker @Inject constructor(
    @ApplicationScope scope: CoroutineScope,
    private val timeProvider: TimeProvider,
) {
    val now: SharedFlow<Instant> = flow {
        while (currentCoroutineContext().isActive) {
            val now = timeProvider.now()
            emit(now)
            delay(1_000L - now.toEpochMilli() % 1_000L)
        }
    }.shareIn(
        scope = scope,
        started = SharingStarted.WhileSubscribed(stopTimeoutMillis = 1_000),
        replay = 1,
    )
}
