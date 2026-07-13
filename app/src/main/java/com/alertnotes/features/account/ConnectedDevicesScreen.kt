package com.alertnotes.features.account

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.alertnotes.R
import com.alertnotes.core.extensions.toDisplayDateTime
import com.alertnotes.core.ui.components.AppTopBar
import com.alertnotes.core.ui.components.SectionCard
import com.alertnotes.core.ui.theme.spacing
import com.alertnotes.data.remote.DeviceRemoteDataSource
import com.alertnotes.domain.repository.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.ZoneId
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn

@HiltViewModel
class ConnectedDevicesViewModel @Inject constructor(
    authRepository: AuthRepository,
    deviceDataSource: DeviceRemoteDataSource,
) : ViewModel() {

    /** `null` means the device registry has not emitted yet (still loading). */
    @OptIn(ExperimentalCoroutinesApi::class)
    val devices: StateFlow<List<DeviceRemoteDataSource.ConnectedDevice>?> =
        authRepository.authState.flatMapLatest { user ->
            if (user == null) flowOf(emptyList()) else deviceDataSource.observeDevices(user.uid)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
}

/**
 * Every phone and tablet signed in to the account: identity, OS and app
 * versions, last-active time, and which one is this device. The registry
 * updates on every login, so a stale row simply ages out visibly.
 */
@Composable
fun ConnectedDevicesScreen(
    onNavigateBack: () -> Unit,
    viewModel: ConnectedDevicesViewModel = hiltViewModel(),
) {
    val devices by viewModel.devices.collectAsStateWithLifecycle()
    val zone = ZoneId.systemDefault()

    Scaffold(
        topBar = {
            AppTopBar(
                title = stringResource(R.string.more_devices_title),
                onNavigateBack = onNavigateBack,
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { innerPadding ->
        Box(modifier = Modifier.padding(innerPadding).fillMaxSize()) {
            val deviceList = devices
            if (deviceList == null) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            } else LazyColumn(
                modifier = Modifier
                    .widthIn(max = 640.dp)
                    .fillMaxSize()
                    .align(Alignment.TopCenter),
                contentPadding = PaddingValues(
                    horizontal = MaterialTheme.spacing.large,
                    vertical = MaterialTheme.spacing.small,
                ),
                verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.extraLarge),
            ) {
                item {
                    SectionCard(title = stringResource(R.string.more_devices_title)) {
                        if (deviceList.isEmpty()) {
                            Text(
                                text = stringResource(R.string.devices_empty),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(MaterialTheme.spacing.large),
                            )
                        } else {
                            deviceList.forEach { device ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(
                                            horizontal = MaterialTheme.spacing.large,
                                            vertical = MaterialTheme.spacing.small,
                                        ),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.PhoneAndroid,
                                        contentDescription = null,
                                        tint = if (device.isCurrentDevice) {
                                            MaterialTheme.colorScheme.primary
                                        } else {
                                            MaterialTheme.colorScheme.onSurfaceVariant
                                        },
                                    )
                                    Column(
                                        modifier = Modifier
                                            .weight(1f)
                                            .padding(horizontal = MaterialTheme.spacing.medium),
                                    ) {
                                        Text(
                                            text = device.name,
                                            style = MaterialTheme.typography.titleSmall,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                        Text(
                                            text = listOf(
                                                device.model,
                                                "Android ${device.androidVersion}",
                                                "v${device.appVersion}",
                                            ).filter { it.isNotBlank() }.joinToString(" · "),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                        device.lastSeen?.let { lastSeen ->
                                            Text(
                                                text = stringResource(R.string.devices_last_seen) +
                                                    " " + lastSeen.toDisplayDateTime(zone),
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                        }
                                    }
                                    if (device.isCurrentDevice) {
                                        Surface(
                                            shape = MaterialTheme.shapes.extraLarge,
                                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
                                        ) {
                                            Text(
                                                text = stringResource(R.string.devices_current_badge),
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.padding(
                                                    horizontal = MaterialTheme.spacing.small,
                                                    vertical = MaterialTheme.spacing.extraSmall,
                                                ),
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
