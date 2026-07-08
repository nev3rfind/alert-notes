package com.alertnotes.features.security

import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.alertnotes.R
import com.alertnotes.biometric.BiometricGate
import com.alertnotes.core.ui.components.PrimaryButton
import com.alertnotes.core.ui.theme.spacing

/**
 * Process-scoped unlock state. Deliberately NOT rememberSaveable: saved
 * instance state survives process death, so persisting "unlocked" there
 * would restore a bypassed lock after the system kills and recreates the
 * app. Held in process memory instead — rotation (same process) stays
 * unlocked, process death re-locks.
 */
object AppLockState {
    val unlocked = mutableStateOf(false)

    /** True while the lock overlay is covering the app content. */
    fun isLocked(lockEnabled: Boolean): Boolean = lockEnabled && !unlocked.value
}

/**
 * The app lock: a full-screen scrim over the app content while locked,
 * cleared by fingerprint / face / device credential. Locks on first launch
 * and re-locks whenever the app leaves the foreground. Reminder alerts
 * render above it — a lock protects the user's notes, never their alarms.
 */
@Composable
fun AppLockOverlay(lockEnabled: Boolean) {
    if (!lockEnabled) return
    val activity = LocalActivity.current as? FragmentActivity ?: return

    var unlocked by AppLockState.unlocked
    // The credential screen can stop this activity; don't re-lock for it.
    var authenticating by remember { mutableStateOf(false) }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP && !authenticating) {
                unlocked = false
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    if (unlocked) return

    val promptTitle = stringResource(R.string.app_lock_prompt_title)
    val cancelLabel = stringResource(R.string.action_cancel)
    val requestUnlock = {
        authenticating = true
        BiometricGate.authenticate(
            activity = activity,
            allowDeviceCredential = true,
            title = promptTitle,
            cancelLabel = cancelLabel,
            onSuccess = {
                authenticating = false
                unlocked = true
            },
            onError = { authenticating = false },
        )
    }
    LaunchedEffect(Unit) { requestUnlock() }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(MaterialTheme.spacing.large),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(
                imageVector = Icons.Outlined.Lock,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(56.dp),
            )
            Text(
                text = stringResource(R.string.app_lock_title),
                style = MaterialTheme.typography.headlineSmall,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = MaterialTheme.spacing.large),
            )
            Text(
                text = stringResource(R.string.app_lock_message),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = MaterialTheme.spacing.small),
            )
            PrimaryButton(
                text = stringResource(R.string.app_lock_unlock),
                onClick = requestUnlock,
                modifier = Modifier.padding(top = MaterialTheme.spacing.extraLarge),
            )
        }
    }
}
