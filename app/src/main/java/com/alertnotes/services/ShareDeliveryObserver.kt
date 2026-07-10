package com.alertnotes.services

import com.alertnotes.core.util.AppLogger
import com.alertnotes.di.ApplicationScope
import com.alertnotes.domain.model.AppMode
import com.alertnotes.domain.model.ShareStatus
import com.alertnotes.domain.repository.ReminderSharingRepository
import com.alertnotes.domain.repository.SettingsRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * Family auto-delivery: whenever a DELIVERED share addressed to this user
 * appears (live listener), download, store, and schedule it — no screen
 * needs to be open. Runs only in online mode; delivery itself is idempotent
 * (guarded by recipientReminderId), so replays are harmless. Offline-mode
 * users never open a connection here.
 */
@Singleton
class ShareDeliveryObserver @Inject constructor(
    @param:ApplicationScope private val scope: CoroutineScope,
    private val settingsRepository: SettingsRepository,
    private val sharingRepository: ReminderSharingRepository,
    private val logger: AppLogger,
) {

    /** Called once from Application.onCreate. */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun start() {
        scope.launch {
            combine(
                settingsRepository.preferences.map { it.appMode }.distinctUntilChanged(),
                sharingRepository.incomingShares,
            ) { mode, incoming ->
                mode == AppMode.ONLINE &&
                    incoming.any { it.share.status == ShareStatus.DELIVERED }
            }.collectLatest { hasReleasedShares ->
                if (hasReleasedShares) {
                    runCatching { sharingRepository.deliverReleasedShares() }
                        .onFailure { logger.w(TAG, "Released-share sweep failed", it) }
                }
            }
        }
    }

    private companion object {
        const val TAG = "ShareDeliveryObserver"
    }
}
