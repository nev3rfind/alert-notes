package com.alertnotes.services

import com.alertnotes.core.util.AppLogger
import com.alertnotes.di.ApplicationScope
import com.alertnotes.domain.model.AppMode
import com.alertnotes.domain.repository.ReminderRepository
import com.alertnotes.domain.repository.ReminderSharingRepository
import com.alertnotes.domain.repository.SettingsRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * Keeps both ends of reminder sharing converged without any screen open.
 *
 * Recipient side: whenever incoming shares or local reminders change,
 * [ReminderSharingRepository.syncIncomingShares] delivers family
 * auto-releases, applies permitted content updates, removes revoked
 * recipients-only assignments, and mirrors TRIGGERED/COMPLETED back to the
 * owner (a local alarm firing invalidates the reminders table, which is what
 * re-runs the sweep here).
 *
 * Owner side: whenever outgoing shares or local reminders change,
 * [ReminderSharingRepository.syncOwnedShares] pushes edited content to every
 * live share and re-asserts the archived state of recipients-only masters.
 *
 * Runs only in online mode; every sweep is idempotent (guarded by version
 * and timestamp comparisons), so listener replays are harmless and the
 * write-triggers-snapshot-triggers-sweep cycle always converges. Offline-mode
 * users never open a connection here.
 */
@Singleton
class ShareDeliveryObserver @Inject constructor(
    @param:ApplicationScope private val scope: CoroutineScope,
    private val settingsRepository: SettingsRepository,
    private val sharingRepository: ReminderSharingRepository,
    private val reminderRepository: ReminderRepository,
    private val historyRepository: com.alertnotes.domain.repository.ReminderHistoryRepository,
    private val logger: AppLogger,
) {

    /** Called once from Application.onCreate. */
    @OptIn(FlowPreview::class)
    fun start() {
        val isOnline: Flow<Boolean> = settingsRepository.preferences
            .map { it.appMode == AppMode.ONLINE }
            .distinctUntilChanged()

        scope.launch {
            combine(
                isOnline,
                sharingRepository.incomingShares,
                reminderRepository.observeReminders(),
                // Acknowledgements land in the history archive, not the
                // reminders table — without this signal a dismissal would
                // wait for the next unrelated sweep to reach the owner.
                historyRepository.observeLatest(),
            ) { online, incoming, _, _ -> online && incoming.isNotEmpty() }
                .debounce(SWEEP_DEBOUNCE_MILLIS)
                .collectLatest { actionable ->
                    if (actionable) {
                        runCatching { sharingRepository.syncIncomingShares() }
                            .onFailure { logger.w(TAG, "Incoming-share sweep failed", it) }
                    }
                }
        }
        scope.launch {
            combine(
                isOnline,
                sharingRepository.outgoingShares,
                reminderRepository.observeReminders(),
            ) { online, outgoing, _ -> online && outgoing.isNotEmpty() }
                .debounce(SWEEP_DEBOUNCE_MILLIS)
                .collectLatest { actionable ->
                    if (actionable) {
                        runCatching { sharingRepository.syncOwnedShares() }
                            .onFailure { logger.w(TAG, "Owned-share sweep failed", it) }
                    }
                }
        }
    }

    private companion object {
        const val TAG = "ShareDeliveryObserver"
        const val SWEEP_DEBOUNCE_MILLIS = 1_000L
    }
}
