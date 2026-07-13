package com.alertnotes

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.Location
import android.location.LocationManager
import android.os.Bundle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import android.os.CancellationSignal
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import androidx.fragment.app.FragmentActivity
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.alertnotes.core.ui.components.PrimaryButton
import com.alertnotes.core.ui.components.SecondaryButton
import com.alertnotes.core.ui.theme.AlertNotesTheme
import com.alertnotes.core.ui.theme.spacing
import com.alertnotes.core.util.AckProofStore
import com.alertnotes.domain.model.AcknowledgeMethod
import com.alertnotes.services.AcknowledgementSession
import dagger.hilt.android.AndroidEntryPoint
import java.time.Instant
import kotlin.coroutines.resume
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext

/**
 * Hosts proof capture in a NORMAL-launchMode activity. AlertActivity is
 * `singleInstance`, and Android silently cancels cross-task activity
 * results from singleInstance tasks — the second half of the camera
 * bounce. This activity owns the capture contracts; outcomes travel back
 * through [AcknowledgementSession] (never activity results), and the alert
 * dispatcher stands down for the whole session.
 */
@AndroidEntryPoint
class ProofCaptureActivity : FragmentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val reminderId = intent.getLongExtra(EXTRA_REMINDER_ID, -1L)
        val mode = intent.getStringExtra(EXTRA_MODE) ?: MODE_PHOTO
        if (reminderId <= 0L) {
            AcknowledgementSession.cancel()
            finish()
            return
        }
        setContent {
            AlertNotesTheme {
                Surface(color = MaterialTheme.colorScheme.background) {
                    when (mode) {
                        MODE_LOCATION -> LocationProofFlow(
                            reminderId = reminderId,
                            onDone = ::finish,
                        )

                        else -> PhotoProofFlow(
                            reminderId = reminderId,
                            onDone = ::finish,
                        )
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // Abandoned mid-capture (back, home, system kill): release the
        // session so the alert returns; a completed session is a no-op.
        if (isFinishing && AcknowledgementSession.activeReminderId != null) {
            AcknowledgementSession.cancel()
        }
    }

    companion object {
        private const val EXTRA_REMINDER_ID = "com.alertnotes.extra.PROOF_REMINDER_ID"
        private const val EXTRA_MODE = "com.alertnotes.extra.PROOF_MODE"
        const val MODE_PHOTO = "photo"
        const val MODE_LOCATION = "location"

        fun intent(context: Context, reminderId: Long, mode: String): Intent =
            Intent(context, ProofCaptureActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                .putExtra(EXTRA_REMINDER_ID, reminderId)
                .putExtra(EXTRA_MODE, mode)
    }
}

/**
 * Live camera capture → preview → Retake / Confirm / Cancel. The camera
 * launches immediately; only an explicit Confirm completes the
 * acknowledgement.
 */
@Composable
private fun PhotoProofFlow(reminderId: Long, onDone: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var captured by remember { mutableStateOf(false) }
    var captureCount by remember { mutableStateOf(0) }
    val captureUri = remember(reminderId) { AckProofStore.captureUriFor(context, reminderId) }
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture(),
    ) { success ->
        if (success) {
            captured = true
            captureCount++
        } else if (!captured) {
            AcknowledgementSession.cancel()
            onDone()
        }
    }
    LaunchedEffect(Unit) {
        if (!captured) launcher.launch(captureUri)
    }
    if (!captured) return
    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .widthIn(max = 560.dp)
                .fillMaxWidth()
                .align(Alignment.Center)
                .padding(MaterialTheme.spacing.large),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.large),
        ) {
            Text(
                text = stringResource(R.string.proof_photo_title),
                style = MaterialTheme.typography.titleLarge,
            )
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(AckProofStore.fileFor(context, reminderId))
                    // The file path is reused across retakes; the counter
                    // busts Coil's cache so the LATEST capture previews.
                    .memoryCacheKey("proof_${reminderId}_$captureCount")
                    .diskCacheKey("proof_${reminderId}_$captureCount")
                    .build(),
                contentDescription = stringResource(R.string.proof_photo_preview_cd),
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(PHOTO_PREVIEW_ASPECT)
                    .clip(MaterialTheme.shapes.large),
            )
            PrimaryButton(
                text = stringResource(R.string.proof_confirm),
                onClick = {
                    AcknowledgementSession.finish(reminderId, AcknowledgeMethod.PHOTO)
                    onDone()
                },
                modifier = Modifier.fillMaxWidth(),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.medium)) {
                SecondaryButton(
                    text = stringResource(R.string.proof_retake),
                    onClick = { launcher.launch(captureUri) },
                    modifier = Modifier.weight(1f),
                )
                SecondaryButton(
                    text = stringResource(R.string.action_cancel),
                    onClick = {
                        AcknowledgementSession.cancel()
                        onDone()
                    },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

private sealed interface LocationStep {
    data object NeedsPermission : LocationStep
    data object Acquiring : LocationStep
    data class Acquired(val proof: AckProofStore.LocationProof) : LocationStep
    data class Failed(val reasonRes: Int) : LocationStep
}

/**
 * Location proof: permission → high-accuracy fix → reverse geocode →
 * Confirm / Retry / Cancel. GPS failures explain themselves and offer a
 * retry, never a dead end.
 */
@Composable
private fun LocationProofFlow(reminderId: Long, onDone: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var step by remember {
        mutableStateOf<LocationStep>(
            if (hasLocationPermission(context)) LocationStep.Acquiring else LocationStep.NeedsPermission,
        )
    }
    var attempt by remember { mutableStateOf(0) }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { grants ->
        step = if (grants.values.any { it }) {
            LocationStep.Acquiring
        } else {
            LocationStep.Failed(R.string.proof_location_permission_denied)
        }
    }
    LaunchedEffect(step, attempt) {
        when (step) {
            LocationStep.NeedsPermission -> permissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION,
                ),
            )

            LocationStep.Acquiring -> {
                step = acquireLocationProof(context)
            }

            else -> Unit
        }
    }
    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .widthIn(max = 560.dp)
                .fillMaxWidth()
                .align(Alignment.Center)
                .padding(MaterialTheme.spacing.large),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.large),
        ) {
            Text(
                text = stringResource(R.string.proof_location_title),
                style = MaterialTheme.typography.titleLarge,
            )
            when (val current = step) {
                LocationStep.NeedsPermission, LocationStep.Acquiring -> {
                    CircularProgressIndicator()
                    Text(
                        text = stringResource(R.string.proof_location_acquiring),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                is LocationStep.Acquired -> {
                    val proof = current.proof
                    Text(
                        text = "%.5f, %.5f  (±%.0f m)".format(
                            proof.latitude,
                            proof.longitude,
                            proof.accuracyMeters,
                        ),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    val place = listOf(proof.address, proof.city, proof.region, proof.country)
                        .filter { it.isNotBlank() }
                        .joinToString(", ")
                    if (place.isNotBlank()) {
                        Text(
                            text = place,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    PrimaryButton(
                        text = stringResource(R.string.proof_confirm),
                        onClick = {
                            AckProofStore.writeLocationProof(context, reminderId, proof)
                            AcknowledgementSession.finish(reminderId, AcknowledgeMethod.LOCATION)
                            onDone()
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                is LocationStep.Failed -> {
                    Text(
                        text = stringResource(current.reasonRes),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.medium)) {
                if (step is LocationStep.Failed || step is LocationStep.Acquired) {
                    SecondaryButton(
                        text = stringResource(R.string.proof_retry),
                        onClick = {
                            attempt++
                            step = if (hasLocationPermission(context)) {
                                LocationStep.Acquiring
                            } else {
                                LocationStep.NeedsPermission
                            }
                        },
                        modifier = Modifier.weight(1f),
                    )
                }
                SecondaryButton(
                    text = stringResource(R.string.action_cancel),
                    onClick = {
                        AcknowledgementSession.cancel()
                        onDone()
                    },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

private fun hasLocationPermission(context: Context): Boolean =
    ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
        PackageManager.PERMISSION_GRANTED ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
        PackageManager.PERMISSION_GRANTED

/** One high-accuracy fix + best-effort reverse geocode, all off the UI. */
private suspend fun acquireLocationProof(context: Context): LocationStep =
    withContext(Dispatchers.IO) {
        val manager = context.getSystemService(LocationManager::class.java)
            ?: return@withContext LocationStep.Failed(R.string.proof_location_unavailable)
        val provider = when {
            manager.isProviderEnabled(LocationManager.GPS_PROVIDER) -> LocationManager.GPS_PROVIDER
            manager.isProviderEnabled(LocationManager.NETWORK_PROVIDER) ->
                LocationManager.NETWORK_PROVIDER

            else -> return@withContext LocationStep.Failed(R.string.proof_location_disabled)
        }
        val location = runCatching { currentLocation(context, manager, provider) }.getOrNull()
            ?: return@withContext LocationStep.Failed(R.string.proof_location_unavailable)
        val geocoded = runCatching {
            @Suppress("DEPRECATION")
            Geocoder(context).getFromLocation(location.latitude, location.longitude, 1)
                ?.firstOrNull()
        }.getOrNull()
        LocationStep.Acquired(
            AckProofStore.LocationProof(
                latitude = location.latitude,
                longitude = location.longitude,
                accuracyMeters = location.accuracy.toDouble(),
                address = geocoded?.getAddressLine(0).orEmpty(),
                city = geocoded?.locality.orEmpty(),
                region = geocoded?.adminArea.orEmpty(),
                country = geocoded?.countryName.orEmpty(),
                capturedAtMillis = Instant.now().toEpochMilli(),
            ),
        )
    }

@Suppress("MissingPermission")
private suspend fun currentLocation(
    context: Context,
    manager: LocationManager,
    provider: String,
): Location? = suspendCancellableCoroutine { continuation ->
    val signal = CancellationSignal()
    continuation.invokeOnCancellation { signal.cancel() }
    LocationManagerCompat.getCurrentLocation(
        manager,
        provider,
        signal,
        ContextCompat.getMainExecutor(context),
    ) { location ->
        if (continuation.isActive) continuation.resume(location)
    }
}

private const val PHOTO_PREVIEW_ASPECT = 4f / 3f
