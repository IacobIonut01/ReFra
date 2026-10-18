/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.app.presentation.settings.subsettings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dot.gallery.core.ml.ModelGroup
import com.dot.gallery.feature.settings.ui.AiModelGroupUi
import com.dot.gallery.feature.settings.ui.AiModelsManagerActions
import com.dot.gallery.feature.settings.ui.AiModelsManagerContent
import com.dot.gallery.feature.settings.ui.AiModelsManagerState
import com.dot.gallery.injection.injectViewModel
import com.dot.gallery.BuildConfig
import com.dot.gallery.feature.settings.subsettings.SmartFeaturesViewModel

/**
 * Android shell for [AiModelsManagerContent]: collects the per-group ModelManager
 * flows and forwards install/cancel/delete back to the ViewModel.
 */
@Composable
fun AIModelsManagerScreen(
    viewModel: SmartFeaturesViewModel = injectViewModel { smartFeaturesViewModel }
) {
    val activeSearchModel by viewModel.activeSearchModel.collectAsStateWithLifecycle()
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

    AiModelsManagerContent(
        state = AiModelsManagerState(
            isBundled = BuildConfig.ML_MODELS_BUNDLED,
            canInstallModels = viewModel.canInstallModels,
            activeSearchModel = activeSearchModel,
            groups = groups,
        ),
        actions = AiModelsManagerActions(
            onDownload = viewModel::downloadModels,
            onCancelDownload = viewModel::cancelDownload,
            onDelete = viewModel::deleteModels,
            onSelectSearchModel = viewModel::selectSearchModel,
            loadFileInfos = viewModel::getFileInfos,
        ),
    )
}
