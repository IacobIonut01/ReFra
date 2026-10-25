/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.app.presentation.settings.subsettings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dot.gallery.core.ml.ModelGroup
import com.dot.gallery.core.ml.ModelStatus
import com.dot.gallery.feature.settings.ui.SmartScanDetailActions
import com.dot.gallery.feature.settings.ui.SmartScanDetailContent
import com.dot.gallery.feature.settings.ui.SmartScanDetailState
import com.dot.gallery.handover.HandoverConfig
import com.dot.gallery.injection.injectViewModel
import com.dot.gallery.feature.settings.subsettings.SmartFeaturesViewModel

@Composable
fun SmartScanPreferenceDetailScreen(
    viewModel: SmartFeaturesViewModel = injectViewModel { smartFeaturesViewModel }
) {
    val searchStatus by viewModel.activeSearchModelStatus().collectAsStateWithLifecycle()
    val faceDetectStatus by viewModel.modelStatus(ModelGroup.FACE_DETECT).collectAsStateWithLifecycle()
    val faceRecognitionStatus by viewModel.modelStatus(ModelGroup.FACE_RECOGNITION).collectAsStateWithLifecycle()
    val activeScan by viewModel.activeSmartScan.collectAsStateWithLifecycle()
    val activePhases by viewModel.activeSmartScanPhases.collectAsStateWithLifecycle()
    val latestScan by viewModel.latestSmartScan.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val handoverReady by remember {
        HandoverConfig.configuredFlow(context)
    }.collectAsStateWithLifecycle(initialValue = false)

    SmartScanDetailContent(
        state = SmartScanDetailState(
            activeScan = activeScan,
            activePhases = activePhases,
            latestScan = latestScan,
            searchReady = searchStatus == ModelStatus.READY || handoverReady,
            personsReady = faceDetectStatus == ModelStatus.READY &&
                faceRecognitionStatus == ModelStatus.READY,
        ),
        actions = SmartScanDetailActions(
            onMetadata = viewModel::refreshMetadata,
            onEmbeddings = viewModel::refreshEmbeddings,
            onCategories = viewModel::refreshCategories,
            onPersons = viewModel::refreshPersons,
            onAll = viewModel::refreshAll,
            onFullRefresh = viewModel::fullRefresh,
            onCancel = viewModel::cancelActiveScan,
            onRetry = viewModel::retryLatestScan,
        ),
    )
}
