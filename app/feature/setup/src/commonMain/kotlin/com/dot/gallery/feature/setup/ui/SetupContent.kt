/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.feature.setup.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.backhandler.BackHandler
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.Painter
import com.dot.gallery.core.ui.settings.rememberPreference
import com.dot.gallery.domain.settings.SettingsKeys
import com.dot.gallery.core.ui.util.supportsBlur
import com.dot.gallery.feature.setup.domain.settings.SetupKeys
import dev.chrisbanes.haze.blur.HazeBlurStyle
import dev.chrisbanes.haze.blur.LocalHazeBlurStyle
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState


/**
 * First-launch / out-of-the-box setup wizard. Pages adapt to the build variant via
 * [SetupState.pages]: the Cloud page is omitted in offline builds (or when no remote
 * provider is bundled), and the AI Models page is omitted in offline builds unless
 * models are bundled (WithML). Completing the wizard records
 * [SetupKeys.CURRENT_SETUP_VERSION] so it is only shown again when the wizard itself
 * is reworked.
 *
 * [logoPainter] resolves an app-logo alias ([com.dot.gallery.domain.settings.ALIAS_REFRA]
 * et al.) to its launcher artwork — mipmap drawables on Android, so the seam stays
 * platform-side.
 */
@OptIn(ExperimentalSharedTransitionApi::class, ExperimentalComposeUiApi::class)
@Composable
fun SetupContent(
    state: SetupState,
    actions: SetupActions,
    logoPainter: @Composable (logoAlias: String) -> Painter,
    modifier: Modifier = Modifier,
    /** Docs/test seam: opens the wizard directly on this page instead of the first. */
    initialPage: SetupPage? = null,
) {
    var setupCompletedVersion by rememberPreference(SetupKeys.setupCompletedVersion)
    val appLogoAlias by rememberPreference(SettingsKeys.appLogoAlias)

    var index by rememberSaveable {
        mutableIntStateOf(state.pages.indexOf(initialPage).coerceAtLeast(0))
    }
    val safeIndex = index.coerceIn(0, state.pages.lastIndex)
    val totalSteps = state.pages.size - 1

    val goNext: () -> Unit = { if (safeIndex < state.pages.lastIndex) index = safeIndex + 1 }
    val goBack: () -> Unit = {
        if (safeIndex > 0) index = safeIndex - 1 else actions.onExit()
        Unit
    }
    val reduceMotion = LocalSetupReduceMotion.current
    // Start browsing plays the exit burst first; onFinish may restart the app, so it runs last.
    var exiting by remember { mutableStateOf(false) }
    val exitProgress = remember { Animatable(0f) }
    val heroTracker = remember { SetupHeroTracker() }
    val heroSink: (Offset) -> Unit = remember(heroTracker) { { center -> heroTracker.center = center } }
    val finish: () -> Unit = finish@{
        if (exiting) return@finish
        // Permission may have been revoked from system settings while later setup pages were open.
        // Recheck before persisting completion; selected-photos access is a valid result.
        if (!actions.hasMediaAccess()) {
            index = state.pages.indexOf(SetupPage.PERMISSIONS)
            return@finish
        }
        setupCompletedVersion = SetupKeys.CURRENT_SETUP_VERSION
        if (reduceMotion) actions.onFinish() else exiting = true
    }
    LaunchedEffect(exiting) {
        if (!exiting) return@LaunchedEffect
        exitProgress.animateTo(1f, tween(durationMillis = 600, easing = EmphasizedAccelerate))
        actions.onFinish()
    }

    BackHandler(enabled = !exiting) { goBack() }

    // One haze source for the whole wizard: the blob backdrop. Glass panels and the bottom bar
    // blur it; the allow-blur setting switches them to their solid fallback.
    val hazeState = rememberHazeState()
    val allowBlur by rememberPreference(SettingsKeys.allowBlur.withDefault(supportsBlur))
    val blurSwitch = remember(allowBlur) { HazeBlurStyle { blurEnabled(allowBlur) } }

    // A single SharedTransitionLayout wraps the pager so common chrome (back button, progress
    // indicator, action button) morphs between pages, with one continuous animated background
    // drawn behind it to avoid the per-page restart/flicker.
    SharedTransitionLayout(modifier = modifier.fillMaxSize()) {
        SetupAnimatedBackground(modifier = Modifier.hazeSource(hazeState), step = safeIndex)

        AnimatedContent(
            targetState = safeIndex,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    // Exit: the page shrinks back and fades under the growing burst.
                    val p = exitProgress.value
                    alpha = (1f - p * 1.6f).coerceIn(0f, 1f)
                    scaleX = 1f - 0.08f * p
                    scaleY = 1f - 0.08f * p
                },
            transitionSpec = { setupPageTransform(reduceMotion) },
            label = "setup-pages"
        ) { idx ->
            CompositionLocalProvider(
                LocalSetupSharedTransitionScope provides this@SharedTransitionLayout,
                LocalSetupAnimatedVisibilityScope provides this@AnimatedContent,
                LocalSetupHazeState provides hazeState,
                LocalHazeBlurStyle provides blurSwitch,
                LocalSetupHeroBoundsSink provides heroSink,
            ) {
                val stepNumber = idx
                when (state.pages[idx]) {
                    SetupPage.WELCOME -> SetupWelcomeContent(
                        appIcon = logoPainter(appLogoAlias),
                        onNext = goNext
                    )
                    SetupPage.PERMISSIONS -> SetupPermissionsContent(
                        state = state.permissions,
                        actions = actions.permissions,
                        stepNumber = stepNumber,
                        totalSteps = totalSteps,
                        onBack = goBack,
                        onNext = goNext
                    )
                    SetupPage.LOOKS -> SetupLooksFeelContent(
                        state = state.looksFeel,
                        actions = actions.looksFeel,
                        logoPainter = logoPainter,
                        stepNumber = stepNumber,
                        totalSteps = totalSteps,
                        onBack = goBack,
                        onNext = goNext
                    )
                    SetupPage.CLOUD -> SetupCloudContent(
                        state = state.cloud,
                        actions = actions.cloud,
                        stepNumber = stepNumber,
                        totalSteps = totalSteps,
                        onBack = goBack,
                        onNext = goNext,
                        onSkip = goNext
                    )
                    SetupPage.AI_MODELS -> SetupAiModelsContent(
                        state = state.aiModels,
                        actions = actions.aiModels,
                        stepNumber = stepNumber,
                        totalSteps = totalSteps,
                        onBack = goBack,
                        onNext = goNext,
                        onSkip = goNext
                    )
                    SetupPage.TIPS -> SetupAllSetContent(
                        stepNumber = stepNumber,
                        totalSteps = totalSteps,
                        onBack = goBack,
                        onFinish = finish
                    )
                }
            }
        }

        if (exiting) {
            SetupExitBurst(
                progress = { exitProgress.value },
                heroCenter = heroTracker.center,
                shape = SetupHeroShape.Done.toShape(),
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}
