/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.feature.settings.ui

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.dp
import com.dot.gallery.core.ui.components.settings
import org.jetbrains.compose.resources.stringResource
import com.dot.gallery.feature.settings.generated.resources.Res
import com.dot.gallery.feature.settings.generated.resources.ai_category
import com.dot.gallery.feature.settings.generated.resources.handover_title
import com.dot.gallery.feature.settings.generated.resources.ai_models_manager
import com.dot.gallery.feature.settings.generated.resources.categories
import com.dot.gallery.feature.settings.generated.resources.database
import com.dot.gallery.feature.settings.generated.resources.edit_backups
import com.dot.gallery.feature.settings.generated.resources.edit_backups_storage
import com.dot.gallery.feature.settings.generated.resources.edit_backups_summary
import com.dot.gallery.feature.settings.generated.resources.smart_features_cloud_indexing_summary
import com.dot.gallery.feature.settings.generated.resources.smart_features_cloud_section
import com.dot.gallery.feature.settings.generated.resources.smart_features_include_ignored_albums
import com.dot.gallery.feature.settings.generated.resources.smart_features_include_ignored_albums_scan_running
import com.dot.gallery.feature.settings.generated.resources.smart_features_include_ignored_albums_summary
import com.dot.gallery.feature.settings.generated.resources.smart_scan_manager_title
import com.dot.gallery.feature.settings.generated.resources.ocr_settings_entry_summary
import com.dot.gallery.core.presentation.components.GalleryPageTopBar
import com.dot.gallery.core.presentation.components.rememberScrolledUnderTopBar
import com.dot.gallery.core.presentation.util.LocalHazeState
import dev.chrisbanes.haze.hazeSource
import androidx.compose.foundation.lazy.rememberLazyListState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SmartFeaturesScreenContent(
    state: SmartFeaturesScreenState,
    actions: SmartFeaturesScreenActions = SmartFeaturesScreenActions(),
) {
    val listState = rememberLazyListState()

    Scaffold(
        topBar = {
            GalleryPageTopBar(
                title = stringResource(Res.string.ai_category),
                hazeState = LocalHazeState.current,
                scrolled = rememberScrolledUnderTopBar(listState),
            )
        }
    ) { padding ->
        // Resolve strings outside the non-composable settings{} DSL
        val smartFeaturesHeader = stringResource(Res.string.ai_category)
        val includeIgnoredAlbumsTitle = stringResource(Res.string.smart_features_include_ignored_albums)
        val includeIgnoredAlbumsSummary = stringResource(
            if (state.scanRunning) {
                Res.string.smart_features_include_ignored_albums_scan_running
            } else {
                Res.string.smart_features_include_ignored_albums_summary
            }
        )
        val aiModelsManagerTitle = if (state.aiAvailable) stringResource(Res.string.ai_models_manager) else ""
        val categoriesTitle = if (state.aiAvailable) stringResource(Res.string.categories) else ""
        val databaseHeader = stringResource(Res.string.database)
        val scanManagerTitle = stringResource(Res.string.smart_scan_manager_title)
        val storageHeader = stringResource(Res.string.edit_backups_storage)
        val editBackupsTitle = stringResource(Res.string.edit_backups)
        val editBackupsSummary = stringResource(Res.string.edit_backups_summary)
        val cloudIndexingTitle = stringResource(Res.string.smart_features_cloud_section)
        val cloudIndexingSummary = stringResource(Res.string.smart_features_cloud_indexing_summary)
        val handoverTitle = stringResource(Res.string.handover_title)
        val textRecognitionSummary = stringResource(Res.string.ocr_settings_entry_summary)

        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize().hazeSource(LocalHazeState.current),
            contentPadding = PaddingValues(
                start = padding.calculateStartPadding(LocalLayoutDirection.current),
                end = padding.calculateEndPadding(LocalLayoutDirection.current),
                top = 16.dp + padding.calculateTopPadding(),
                bottom = padding.calculateBottomPadding()
            )
        ) {
            settings {
                Header(smartFeaturesHeader)

                if (state.aiAvailable) {
                    SwitchPreference(
                        title = includeIgnoredAlbumsTitle,
                        summary = includeIgnoredAlbumsSummary,
                        enabled = !state.scanRunning,
                        isChecked = state.includeIgnoredAlbums,
                        onCheck = actions.onIncludeIgnoredAlbumsChange
                    )
                }

                // Offered in every build: text recognition lives in the hub, and its language packs are
                // downloaded or, without internet, added from files.
                Preference(
                    title = aiModelsManagerTitle,
                    summary = state.modelSummary.ifEmpty { textRecognitionSummary },
                    onClick = actions.onOpenAiModels
                )

                if (state.aiAvailable) {
                    Preference(
                        title = categoriesTitle,
                        summary = state.categoriesSummary,
                        enabled = state.modelsReady,
                        onClick = actions.onOpenCategories
                    )
                }

                if (state.hasCloudProviders) {
                    Preference(
                        title = cloudIndexingTitle,
                        summary = cloudIndexingSummary,
                        onClick = actions.onOpenCloudIndexing
                    )
                }

                Preference(
                    title = handoverTitle,
                    summary = state.handoverSummary,
                    onClick = actions.onOpenHandover
                )

                Header(databaseHeader)

                Preference(
                    title = scanManagerTitle,
                    summary = state.scanSummary,
                    onClick = actions.onOpenScanManager
                )

                Header(storageHeader)

                Preference(
                    title = editBackupsTitle,
                    summary = editBackupsSummary,
                    onClick = actions.onOpenEditBackups
                )
            }
        }
    }
}
