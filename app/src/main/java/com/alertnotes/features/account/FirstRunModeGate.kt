package com.alertnotes.features.account

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel

/**
 * First-launch overlay shown while no [com.alertnotes.domain.model.AppMode]
 * is persisted: choose the experience, then — for online — authenticate.
 * Picking a mode (offline directly, online after sign-in/registration)
 * persists it, which unmounts the gate; the regular onboarding flow follows.
 */
@Composable
fun FirstRunModeGate(
    viewModel: AccountViewModel = hiltViewModel(),
) {
    var showAuth by rememberSaveable { mutableStateOf(false) }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding(),
            contentAlignment = Alignment.Center,
        ) {
            AnimatedContent(
                targetState = showAuth,
                transitionSpec = {
                    val forward = targetState
                    val enter = fadeIn() + slideInHorizontally { if (forward) it / 3 else -it / 3 }
                    val exit = fadeOut() + slideOutHorizontally { if (forward) -it / 3 else it / 3 }
                    enter togetherWith exit
                },
                label = "firstRunGate",
            ) { authVisible ->
                if (authVisible) {
                    AuthFlow(
                        viewModel = viewModel,
                        onExit = { showAuth = false },
                    )
                } else {
                    ModeSelectionScreen(
                        onChooseOffline = viewModel::chooseOfflineMode,
                        onChooseOnline = { showAuth = true },
                    )
                }
            }
        }
    }
}
