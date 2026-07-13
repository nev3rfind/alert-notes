package com.alertnotes

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.Location
import android.location.LocationManager
import android.os.Bundle
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
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
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.alertnotes.core.ui.components.PrimaryButton
import com.alertnotes.core.ui.components.SecondaryButton
import com.alertnotes.core.ui.theme.AlertNotesTheme
import com.alertnotes.core.ui.theme.spacing
import com.alertnotes.core.util.AckProofStore
import com.alertnotes.domain.model.AcknowledgeMethod
import com.alertnotes.services.AcknowledgementSession
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Instant
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Hosts proof capture in a NORMAL-launchMode activity. AlertActivity is
 * `singleInstance`, and Android silently cancels cross-task activity
 * results from singleInstance tasks. Outcomes travel back through
 * [AcknowledgementSession] (never activity results), and the alert
 * dispatcher stands down for the whole session.
 *
 * Location acquisition lives in a ViewModel so rotation, split-screen
 * resizes, and brief backgrounding never cancel an in-flight fix.
 */
@AndroidEntryPoint
class ProofCaptureActivity : FragmentActivity() {

    private val locationViewModel: LocationProofViewModel by viewModels()

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
                            viewModel = locationViewModel,
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

    override fun onResume() {
        super.onResume()
        // Returning from the system Location Settings screen: continue
        // automatically the moment services are enabled. Gated by mode —
        // photo sessions must never lazily spin up location acquisition.
        if (intent.getStringExtra(EXTRA_MODE) == MODE_LOCATION) {
            locationViewModel.onResumed()
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

// region Location proof

/** The acquisition state machine, config-change proof. */
sealed interface LocationStep {
    data object NeedsPermission : LocationStep
    data object PermissionDenied : LocationStep
    data object ServicesDisabled : LocationStep
    data object Acquiring : LocationStep
    data class Acquired(val proof: AckProofStore.LocationProof) : LocationStep
    data object Failed : LocationStep
}

/**
 * Reliable location acquisition via the Fused Location Provider:
 *
 * 1. runtime permission → 2. location services enabled (auto-continues
 * when the user returns from settings) → 3. last-known fix, used
 * immediately when fresh and accurate → 4. otherwise a HIGH_ACCURACY
 * update window (~12s), keeping the most accurate fix received and
 * finishing early on an excellent one → 5. explicit, retryable failure.
 */
@HiltViewModel
class LocationProofViewModel @Inject constructor(
    @param:ApplicationContext private val context: Context,
) : ViewModel() {

    private val _step = MutableStateFlow<LocationStep>(initialStep())
    val step: StateFlow<LocationStep> = _step.asStateFlow()

    private var acquisition: Job? = null
    private var attemptStartedAt = 0L

    /** Seconds the CURRENT (or last) attempt has been running. */
    val attemptSeconds: Long
        get() = if (attemptStartedAt == 0L) {
            0L
        } else {
            (SystemClock.elapsedRealtime() - attemptStartedAt) / 1_000L
        }

    private fun initialStep(): LocationStep =
        if (hasLocationPermission(context)) checkServicesThenAcquire() else LocationStep.NeedsPermission

    fun onPermissionResult(granted: Boolean) {
        _step.value = if (granted) checkServicesThenAcquire() else LocationStep.PermissionDenied
    }

    fun retry() {
        _step.value = if (hasLocationPermission(context)) {
            checkServicesThenAcquire()
        } else {
            LocationStep.NeedsPermission
        }
    }

    /** Auto-continue after the user enables services and comes back. */
    fun onResumed() {
        if (_step.value == LocationStep.ServicesDisabled && servicesEnabled()) {
            _step.value = checkServicesThenAcquire()
        }
    }

    private fun servicesEnabled(): Boolean {
        val manager = context.getSystemService(LocationManager::class.java) ?: return false
        return LocationManagerCompat.isLocationEnabled(manager)
    }

    private fun checkServicesThenAcquire(): LocationStep {
        if (!servicesEnabled()) return LocationStep.ServicesDisabled
        startAcquisition()
        return LocationStep.Acquiring
    }

    @SuppressLint("MissingPermission")
    private fun startAcquisition() {
        if (acquisition?.isActive == true) return
        attemptStartedAt = SystemClock.elapsedRealtime()
        acquisition = viewModelScope.launch {
            val fused = LocationServices.getFusedLocationProviderClient(context)
            // Step 3: a fresh-enough, accurate-enough cached fix wins outright.
            val lastKnown = runCatching { fused.lastLocation.await() }.getOrNull()
            val lastKnownUsable = lastKnown != null &&
                System.currentTimeMillis() - lastKnown.time <= LAST_KNOWN_MAX_AGE_MILLIS &&
                lastKnown.accuracy <= GOOD_ACCURACY_METERS
            if (lastKnownUsable) {
                _step.value = LocationStep.Acquired(lastKnown.toProof(context, attemptSeconds))
                return@launch
            }
            // Step 4: high-accuracy updates; keep the best fix, stop early
            // when an excellent one arrives, use the best-so-far on timeout.
            var best: Location? = lastKnown?.takeIf {
                System.currentTimeMillis() - it.time <= STALE_SEED_MAX_AGE_MILLIS
            }
            withTimeoutOrNull(FRESH_FIX_WINDOW_MILLIS) {
                locationUpdates(fused)
                    .onEach { candidate ->
                        val current = best
                        if (current == null || candidate.accuracy <= current.accuracy) {
                            best = candidate
                        }
                    }
                    .firstOrNull { it.accuracy <= EXCELLENT_ACCURACY_METERS }
            }
            val result = best
            _step.value = if (result != null && result.accuracy <= ACCEPTABLE_ACCURACY_METERS) {
                LocationStep.Acquired(result.toProof(context, attemptSeconds))
            } else {
                LocationStep.Failed
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun locationUpdates(
        fused: com.google.android.gms.location.FusedLocationProviderClient,
    ) = callbackFlow<Location> {
        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, UPDATE_INTERVAL_MILLIS)
            .setMinUpdateIntervalMillis(UPDATE_INTERVAL_MILLIS / 2)
            .build()
        val callback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                result.locations.forEach(::trySend)
            }
        }
        fused.requestLocationUpdates(request, callback, Looper.getMainLooper())
        awaitClose { fused.removeLocationUpdates(callback) }
    }

    private companion object {
        const val LAST_KNOWN_MAX_AGE_MILLIS = 2L * 60L * 1_000L
        const val STALE_SEED_MAX_AGE_MILLIS = 10L * 60L * 1_000L
        const val FRESH_FIX_WINDOW_MILLIS = 12_000L
        const val UPDATE_INTERVAL_MILLIS = 1_000L
        const val EXCELLENT_ACCURACY_METERS = 25f
        const val GOOD_ACCURACY_METERS = 50f
        const val ACCEPTABLE_ACCURACY_METERS = 250f
    }
}

/** Fix → proof, with best-effort reverse geocoding off the main thread. */
private suspend fun Location.toProof(
    context: Context,
    attemptSeconds: Long,
): AckProofStore.LocationProof = withContext(Dispatchers.IO) {
    val geocoded = runCatching {
        @Suppress("DEPRECATION")
        Geocoder(context).getFromLocation(latitude, longitude, 1)?.firstOrNull()
    }.getOrNull()
    AckProofStore.LocationProof(
        available = true,
        latitude = latitude,
        longitude = longitude,
        accuracyMeters = accuracy.toDouble(),
        altitudeMeters = if (hasAltitude()) altitude else null,
        speedMps = if (hasSpeed()) speed.toDouble() else null,
        address = geocoded?.getAddressLine(0).orEmpty(),
        city = geocoded?.locality.orEmpty(),
        region = geocoded?.adminArea.orEmpty(),
        country = geocoded?.countryName.orEmpty(),
        attemptSeconds = attemptSeconds,
        capturedAtMillis = Instant.now().toEpochMilli(),
    )
}

private fun hasLocationPermission(context: Context): Boolean =
    ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
        PackageManager.PERMISSION_GRANTED ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
        PackageManager.PERMISSION_GRANTED

@Composable
private fun LocationProofFlow(
    reminderId: Long,
    viewModel: LocationProofViewModel,
    onDone: () -> Unit,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val step by viewModel.step.collectAsStateWithLifecycle()
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { grants -> viewModel.onPermissionResult(grants.values.any { it }) }
    LaunchedEffect(step) {
        if (step == LocationStep.NeedsPermission) {
            permissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION,
                ),
            )
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

                LocationStep.PermissionDenied -> {
                    Text(
                        text = stringResource(R.string.proof_location_permission_denied),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                }

                LocationStep.ServicesDisabled -> {
                    Text(
                        text = stringResource(R.string.proof_location_disabled),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                    PrimaryButton(
                        text = stringResource(R.string.proof_location_open_settings),
                        onClick = {
                            runCatching {
                                context.startActivity(
                                    Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS),
                                )
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
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

                LocationStep.Failed -> {
                    Text(
                        text = stringResource(R.string.proof_location_unavailable),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                    // Never trap the user: closing records an honest
                    // "location proof unavailable" acknowledgement.
                    PrimaryButton(
                        text = stringResource(R.string.proof_location_close_reminder),
                        onClick = {
                            AckProofStore.writeLocationProof(
                                context,
                                reminderId,
                                AckProofStore.LocationProof(
                                    available = false,
                                    failureReason = context.getString(
                                        R.string.proof_location_unavailable_reason,
                                    ),
                                    attemptSeconds = viewModel.attemptSeconds,
                                    capturedAtMillis = Instant.now().toEpochMilli(),
                                ),
                            )
                            AcknowledgementSession.finish(reminderId, AcknowledgeMethod.LOCATION)
                            onDone()
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.medium)) {
                if (
                    step is LocationStep.Failed ||
                    step is LocationStep.Acquired ||
                    step is LocationStep.PermissionDenied ||
                    step is LocationStep.ServicesDisabled
                ) {
                    SecondaryButton(
                        text = stringResource(R.string.proof_retry),
                        onClick = viewModel::retry,
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

// endregion

// region Photo proof

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

// endregion

private const val PHOTO_PREVIEW_ASPECT = 4f / 3f
