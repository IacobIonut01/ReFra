/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.app.presentation.settings.subsettings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dot.gallery.core.LocalEventHandler
import com.dot.gallery.core.navigate
import com.dot.gallery.core.smart.SmartScanPlan
import com.dot.gallery.data.entity.SmartScanStatus
import com.dot.gallery.feature.settings.ui.SmartFeaturesScreenActions
import com.dot.gallery.feature.settings.ui.SmartFeaturesScreenContent
import com.dot.gallery.feature.settings.ui.SmartFeaturesScreenState
import com.dot.gallery.core.presentation.util.Screen
import com.dot.gallery.feature.settings.ui.label
import com.dot.gallery.injection.injectViewModel
import com.dot.gallery.feature.settings.ui.smartScanEtaLabel
import com.dot.gallery.feature.settings.subsettings.SmartFeaturesViewModel
import org.jetbrains.compose.resources.stringResource
import com.dot.gallery.generated.resources.Res
import com.dot.gallery.generated.resources.handover_disabled_summary
import com.dot.gallery.generated.resources.handover_enabled_summary
import com.dot.gallery.generated.resources.smart_scan_last_result
import com.dot.gallery.generated.resources.smart_scan_manager_summary
import com.dot.gallery.generated.resources.smart_scan_settings_preparing
import com.dot.gallery.generated.resources.smart_scan_settings_progress
import com.dot.gallery.generated.resources.smart_scan_settings_progress_eta


@Composable
fun SettingsSmartFeaturesScreen(
    viewModel: SmartFeaturesViewModel = injectViewModel { smartFeaturesViewModel }
) {
    val handler = LocalEventHandler.current
    val aiAvailable = viewModel.areAiFeaturesAvailable
    val activeSmartScan by viewModel.activeSmartScan.collectAsStateWithLifecycle()
    val activeSmartScanPhases by viewModel.activeSmartScanPhases.collectAsStateWithLifecycle()
    val latestSmartScan by viewModel.latestSmartScan.collectAsStateWithLifecycle()
    val includeIgnoredAlbums by viewModel.includeIgnoredAlbums.collectAsStateWithLifecycle()
    val configuredCloudProviders by viewModel.configuredCloudProviders.collectAsStateWithLifecycle()
    val handoverEnabled by viewModel.handoverEnabled.collectAsStateWithLifecycle()

    val activePhase = activeSmartScanPhases.firstOrNull { it.status == SmartScanStatus.RUNNING }
        ?: activeSmartScanPhases.firstOrNull { it.status == SmartScanStatus.QUEUED }
    val activePhaseLabel = activePhase?.phase?.label().orEmpty()
    val smartScanSummary = activeSmartScan?.let {
        val overallPercent = (SmartScanPlan.overallProgress(activeSmartScanPhases) * 100).toInt()
        if (activePhase != null && activePhase.totalMedia > 0) {
            val eta = activePhase
                .takeIf { it.status == SmartScanStatus.RUNNING }
                ?.let {
                    SmartScanPlan.estimatedRemainingMillis(
                        it.totalMedia,
                        it.processedMedia,
                        it.startedAt
                    )
                }
            if (eta == null) {
                stringResource(
                    Res.string.smart_scan_settings_progress,
                    activePhaseLabel,
                    activePhase.processedMedia,
                    activePhase.totalMedia,
                    overallPercent
                )
            } else {
                stringResource(
                    Res.string.smart_scan_settings_progress_eta,
                    activePhaseLabel,
                    activePhase.processedMedia,
                    activePhase.totalMedia,
                    overallPercent,
                    smartScanEtaLabel(eta)
                )
            }
        } else {
            stringResource(Res.string.smart_scan_settings_preparing, activePhaseLabel, overallPercent)
        }
    } ?: latestSmartScan?.let { run ->
        stringResource(
            Res.string.smart_scan_last_result,
            run.status.label(),
            run.processedMedia,
            run.failedMedia,
            run.skippedMedia
        )
    } ?: stringResource(Res.string.smart_scan_manager_summary)

    SmartFeaturesScreenContent(
        state = SmartFeaturesScreenState(
            aiAvailable = aiAvailable,
            includeIgnoredAlbums = includeIgnoredAlbums,
            scanRunning = activeSmartScan != null,
            scanSummary = smartScanSummary,
            hasCloudProviders = configuredCloudProviders.isNotEmpty(),
            handoverEnabled = handoverEnabled,
            handoverSummary = stringResource(
                if (handoverEnabled) Res.string.handover_enabled_summary
                else Res.string.handover_disabled_summary
            ),
        ),
        actions = SmartFeaturesScreenActions(
            onIncludeIgnoredAlbumsChange = viewModel::setIncludeIgnoredAlbums,
            onOpenCloudIndexing = { handler.navigate(Screen.SettingsCloudIndexingScreen()) },
            onOpenScanManager = { handler.navigate(Screen.SmartScanPreferenceDetailScreen()) },
            onOpenEditBackups = { handler.navigate(Screen.EditBackupsViewerScreen()) },
            onOpenHandover = { handler.navigate(Screen.SettingsHandoverScreen()) },
        ),
        hubState = rememberAiModelsManagerState(viewModel),
        hubActions = aiModelsManagerActions(viewModel).copy(
            onOpenFeature = { feature ->
                handler.navigate(Screen.IntelligenceFeatureScreen.feature(feature.name))
            },
            onOpenTextRecognition = { handler.navigate(Screen.TextRecognitionSettingsScreen()) },
        ),
        textRecognition = rememberTextRecognitionState(),
    )
}
