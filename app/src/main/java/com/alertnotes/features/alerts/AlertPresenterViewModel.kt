package com.alertnotes.features.alerts

import androidx.lifecycle.ViewModel
import com.alertnotes.domain.model.AcknowledgeMethod
import com.alertnotes.domain.model.ReminderDrawing
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Duration
import javax.inject.Inject
import kotlinx.coroutines.flow.StateFlow

/**
 * Thin bridge exposing the app-scoped [AlertPresenter] to composables via
 * the usual `hiltViewModel()` path. All logic lives in the presenter so the
 * overlay window and notification actions share it too.
 */
@HiltViewModel
class AlertPresenterViewModel @Inject constructor(
    private val presenter: AlertPresenter,
) : ViewModel() {

    val activeAlert: StateFlow<ActiveAlert?> = presenter.activeAlert

    fun dismiss(
        alert: ActiveAlert,
        method: AcknowledgeMethod,
        signature: ReminderDrawing? = null,
    ) = presenter.dismiss(alert, method, signature)

    fun snooze(alert: ActiveAlert, duration: Duration) = presenter.snooze(alert, duration)
}
