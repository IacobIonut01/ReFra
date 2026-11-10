/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.app.presentation.settings.subsettings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dot.gallery.BuildConfig
import com.dot.gallery.core.LocalEventHandler
import com.dot.gallery.core.navigate
import com.dot.gallery.core.ml.ModelGroup
import com.dot.gallery.core.presentation.util.Screen
import com.dot.gallery.feature.settings.subsettings.SmartFeaturesViewModel
import com.dot.gallery.feature.settings.ui.AiModelGroupUi
import com.dot.gallery.feature.settings.ui.AiModelsManagerActions
import com.dot.gallery.feature.settings.ui.AiModelsManagerContent
import com.dot.gallery.feature.settings.ui.AiModelsManagerState
import com.dot.gallery.feature.settings.ui.IntelligenceFeature
import com.dot.gallery.feature.settings.ui.IntelligenceFeatureContent
import com.dot.gallery.injection.injectViewModel

/**
 * Android shell for the Intelligence Hub ([AiModelsManagerContent]): collects the per-group
 * ModelManager flows and opens each feature's preview page.
 */
@Composable
fun AIModelsManagerScreen(
    viewModel: SmartFeaturesViewModel = injectViewModel { smartFeaturesViewModel }
) {
    val handler = LocalEventHandler.current
    AiModelsManagerContent(
        state = rememberAiModelsManagerState(viewModel),
        actions = aiModelsManagerActions(viewModel).copy(
            onOpenFeature = { feature ->
                handler.navigate(Screen.IntelligenceFeatureScreen.feature(feature.name))
            },
            onOpenTextRecognition = { handler.navigate(Screen.TextRecognitionSettingsScreen()) },
        ),
        textRecognition = rememberTextRecognitionState(),
    )
}

/**
 * Android shell for one feature's preview ([IntelligenceFeatureContent]). [showNotNow] adds the
 * "Not now" action when the page was offered from setup or People rather than opened from the hub.
 */
@Composable
fun IntelligenceFeatureScreen(
    feature: IntelligenceFeature,
    showNotNow: Boolean,
    onScanForPeople: () -> Unit,
    viewModel: SmartFeaturesViewModel = injectViewModel { smartFeaturesViewModel }
) {
    val handler = LocalEventHandler.current
    IntelligenceFeatureContent(
        feature = feature,
        state = rememberAiModelsManagerState(viewModel),
        actions = aiModelsManagerActions(viewModel).copy(
            onScanForPeople = {
                viewModel.refreshPersons()
                onScanForPeople()
            },
            onNotNow = if (showNotNow) ({ handler.navigateUpAction() }) else null,
        ),
    )
}

@Composable
private fun rememberAiModelsManagerState(viewModel: SmartFeaturesViewModel): AiModelsManagerState {
    val activeSearchModel by viewModel.activeSearchModel.collectAsStateWithLifecycle()
    val activeFaceModel by viewModel.activeFaceModel.collectAsStateWithLifecycle()
    val scanOnlyWhileCharging by viewModel.scanOnlyWhileCharging.collectAsStateWithLifecycle()
    val includeIgnoredAlbums by viewModel.includeIgnoredAlbums.collectAsStateWithLifecycle()
    val groups = ModelGroup.entries.map { group ->
        val status by viewModel.modelStatus(group).collectAsStateWithLifecycle()
        val progress by viewModel.downloadProgress(group).collectAsStateWithLifecycle()
        val info by viewModel.downloadInfo(group).collectAsStateWithLifecycle()
        val error by viewModel.errorMessage(group).collectAsStateWithLifecycle()
        AiModelGroupUi(
            group = group,
            status = status,
            progress = progress,
            info = info,
            error = error,
            installedSize = viewModel.installedSize(group),
        )
    }
    return AiModelsManagerState(
        isBundled = BuildConfig.ML_MODELS_BUNDLED,
        canInstallModels = viewModel.canInstallModels,
        activeSearchModel = activeSearchModel,
        activeFaceModel = activeFaceModel,
        groups = groups,
        scanOnlyWhileCharging = scanOnlyWhileCharging,
        skipHiddenAlbums = !includeIgnoredAlbums,
    )
}

private fun aiModelsManagerActions(viewModel: SmartFeaturesViewModel) = AiModelsManagerActions(
    onDownload = viewModel::downloadModels,
    onCancelDownload = viewModel::cancelDownload,
    onDelete = viewModel::deleteModels,
    onSelectSearchModel = viewModel::selectSearchModel,
    onSelectFaceModel = viewModel::selectFaceModel,
    onScanOnlyWhileChargingChange = viewModel::setScanOnlyWhileCharging,
    onSkipHiddenAlbumsChange = { skip -> viewModel.setIncludeIgnoredAlbums(!skip) },
    onScanForPeople = viewModel::refreshPersons,
    loadFileInfos = viewModel::getFileInfos,
)
