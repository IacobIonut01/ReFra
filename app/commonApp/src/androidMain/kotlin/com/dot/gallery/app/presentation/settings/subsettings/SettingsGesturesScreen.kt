/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.app.presentation.settings.subsettings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dot.gallery.core.Settings
import com.dot.gallery.core.ml.ModelGroup
import com.dot.gallery.core.ml.ModelStatus
import com.dot.gallery.core.presentation.util.Screen
import com.dot.gallery.app.presentation.mediaview.TapNavigationPreview
import com.dot.gallery.feature.help.domain.SettingsSearchRegistry
import com.dot.gallery.feature.settings.subsettings.SmartFeaturesViewModel
import com.dot.gallery.feature.settings.ui.GesturesPreviews
import com.dot.gallery.feature.settings.ui.SettingsGesturesActions
import com.dot.gallery.feature.settings.ui.SettingsGesturesContent
import com.dot.gallery.feature.settings.ui.SettingsGesturesState
import com.dot.gallery.injection.injectViewModel
import kotlinx.coroutines.launch

/**
 * App-side shell for the Gestures settings page: owns the platform-only seams
 * (the Subject Cutout model status that gates the long-press option, the live
 * tap-navigation preview, the tap-sides prompt marker) and delegates everything
 * else to [SettingsGesturesContent] in `:feature:settings`.
 */
@Composable
fun SettingsGesturesScreen(
    smartFeatures: SmartFeaturesViewModel = injectViewModel { smartFeaturesViewModel }
) {
    val context = LocalContext.current
    val cutoutStatus by smartFeatures.modelStatus(ModelGroup.CUTOUT).collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()

    SettingsGesturesContent(
        state = SettingsGesturesState(
            cutoutModelAvailable = cutoutStatus == ModelStatus.READY,
        ),
        previews = GesturesPreviews(
            tapNavigation = { TapNavigationPreview(enabled = it) },
        ),
        actions = SettingsGesturesActions(
            onTapSidesChanged = {
                scope.launch { Settings.Misc.markTapSidesToNavigatePromptShown(context) }
            },
            searchSink = SettingsSearchRegistry::register,
            searchRoute = Screen.SettingsGesturesScreen(),
        ),
    )
}
