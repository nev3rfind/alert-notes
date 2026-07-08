package com.alertnotes.features.about

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.alertnotes.BuildConfig
import com.alertnotes.R
import com.alertnotes.core.ui.components.AppListItem
import com.alertnotes.core.ui.components.AppTopBar
import com.alertnotes.core.ui.components.SectionCard
import com.alertnotes.core.ui.theme.spacing

private val ContentMaxWidth = 640.dp

/** Library name paired with its license; both resolved from resources. */
private data class LibraryEntry(val nameRes: Int, val licenseRes: Int)

private val openSourceLibraries = listOf(
    LibraryEntry(R.string.library_compose, R.string.license_apache_2),
    LibraryEntry(R.string.library_material3, R.string.license_apache_2),
    LibraryEntry(R.string.library_navigation, R.string.license_apache_2),
    LibraryEntry(R.string.library_room, R.string.license_apache_2),
    LibraryEntry(R.string.library_hilt, R.string.license_apache_2),
    LibraryEntry(R.string.library_datastore, R.string.license_apache_2),
    LibraryEntry(R.string.library_coroutines, R.string.license_apache_2),
    LibraryEntry(R.string.library_serialization, R.string.license_apache_2),
    LibraryEntry(R.string.library_biometric, R.string.license_apache_2),
)

@Composable
fun AboutScreen(onNavigateBack: () -> Unit) {
    Scaffold(
        topBar = {
            AppTopBar(
                title = stringResource(R.string.about_title),
                onNavigateBack = onNavigateBack,
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize(),
        ) {
            LazyColumn(
                modifier = Modifier
                    .widthIn(max = ContentMaxWidth)
                    .fillMaxSize()
                    .align(Alignment.TopCenter),
                contentPadding = PaddingValues(
                    horizontal = MaterialTheme.spacing.large,
                    vertical = MaterialTheme.spacing.small,
                ),
                verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.extraLarge),
            ) {
                item { AppIdentityHeader() }
                item {
                    SectionCard(title = stringResource(R.string.about_developer_title)) {
                        AppListItem(
                            title = stringResource(R.string.about_developer_name),
                            supportingText = stringResource(R.string.about_developer_subtitle),
                            leadingIcon = Icons.Outlined.Person,
                        )
                    }
                }
                item {
                    SectionCard(title = stringResource(R.string.about_privacy_title)) {
                        Text(
                            text = stringResource(R.string.about_privacy_text),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(MaterialTheme.spacing.large),
                        )
                    }
                }
                item {
                    SectionCard(title = stringResource(R.string.about_license_title)) {
                        Text(
                            text = stringResource(R.string.about_license_text),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(MaterialTheme.spacing.large),
                        )
                    }
                }
                item {
                    SectionCard(title = stringResource(R.string.about_libraries_title)) {
                        openSourceLibraries.forEachIndexed { index, library ->
                            if (index > 0) {
                                HorizontalDivider(
                                    modifier = Modifier.padding(start = MaterialTheme.spacing.large),
                                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                                )
                            }
                            AppListItem(
                                title = stringResource(library.nameRes),
                                supportingText = stringResource(library.licenseRes),
                                leadingIcon = Icons.Outlined.Code,
                                leadingIconTint = MaterialTheme.colorScheme.secondary,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AppIdentityHeader() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = MaterialTheme.spacing.extraLarge),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(72.dp)
                .background(
                    color = MaterialTheme.colorScheme.primary,
                    shape = MaterialTheme.shapes.large,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Outlined.NotificationsActive,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.size(40.dp),
            )
        }
        Text(
            text = stringResource(R.string.app_name),
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.padding(top = MaterialTheme.spacing.medium),
        )
        Text(
            text = stringResource(
                R.string.about_version_format,
                BuildConfig.VERSION_NAME,
                BuildConfig.VERSION_CODE,
            ),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
