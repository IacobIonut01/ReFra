package com.dot.gallery.feature_node.presentation.settings.subsettings

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dot.gallery.R
import com.dot.gallery.core.LocalEventHandler
import com.dot.gallery.core.ml.ModelStatus
import com.dot.gallery.core.navigate
import com.dot.gallery.core.presentation.components.NavigationBackButton
import com.dot.gallery.feature_node.presentation.settings.components.settings
import com.dot.gallery.feature_node.presentation.util.Screen

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsSmartFeaturesScreen(
    viewModel: SmartFeaturesViewModel = hiltViewModel()
) {
    val handler = LocalEventHandler.current
    val aiAvailable = viewModel.areAiFeaturesAvailable
    val modelStatus by viewModel.modelStatus.collectAsStateWithLifecycle()
    val isMetadataWorkerRunning by viewModel.isMetadataWorkerRunning.collectAsStateWithLifecycle()
    val metadataProgress by viewModel.metadataProgress.collectAsStateWithLifecycle()

    val scrollBehavior =
        TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(
                title = { Text(stringResource(R.string.ai_category)) },
                navigationIcon = { NavigationBackButton() },
                scrollBehavior = scrollBehavior,
                colors = TopAppBarDefaults.topAppBarColors(
                    scrolledContainerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { padding ->
        // Resolve strings outside the non-composable settings{} DSL
        val smartFeaturesHeader = stringResource(R.string.ai_category)
        val aiModelsManagerTitle = if (aiAvailable) stringResource(R.string.ai_models_manager) else ""
        val modelSummary = if (aiAvailable) when (modelStatus) {
            ModelStatus.READY -> stringResource(R.string.ai_models_ready_summary)
            ModelStatus.NOT_INSTALLED -> stringResource(R.string.ai_models_download_summary)
            ModelStatus.DOWNLOADING, ModelStatus.COPYING -> stringResource(R.string.ai_models_downloading)
            ModelStatus.ERROR -> stringResource(R.string.ai_models_error)
        } else ""
        val categoriesTitle = if (aiAvailable) stringResource(R.string.categories) else ""
        val categoriesSummary = if (aiAvailable) {
            if (modelStatus == ModelStatus.READY) {
                stringResource(R.string.categorise_your_media)
            } else {
                stringResource(R.string.ai_models_unavailable)
            }
        } else ""
        val databaseHeader = stringResource(R.string.database)
        val refreshMetadataTitle = stringResource(R.string.refresh_metadata)
        val metadataSummary = when {
            isMetadataWorkerRunning && metadataProgress in 1..99 ->
                stringResource(R.string.metadata_collecting_progress, metadataProgress)
            isMetadataWorkerRunning ->
                stringResource(R.string.metadata_collecting)
            else ->
                stringResource(R.string.metadata_idle)
        }
        val storageHeader = stringResource(R.string.edit_backups_storage)
        val editBackupsTitle = stringResource(R.string.edit_backups)
        val editBackupsSummary = stringResource(R.string.edit_backups_summary)

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = padding.calculateStartPadding(LocalLayoutDirection.current),
                end = padding.calculateEndPadding(LocalLayoutDirection.current),
                top = 16.dp + padding.calculateTopPadding(),
                bottom = padding.calculateBottomPadding()
            )
        ) {
            settings {
                if (aiAvailable) {
                    Header(smartFeaturesHeader)

                    Preference(
                        title = aiModelsManagerTitle,
                        summary = modelSummary,
                        onClick = { handler.navigate(Screen.AIModelsManagerScreen()) }
                    )

                    Preference(
                        title = categoriesTitle,
                        summary = categoriesSummary,
                        enabled = modelStatus == ModelStatus.READY,
                        onClick = { handler.navigate(Screen.CategoriesScreen()) }
                    )
                }

                Header(databaseHeader)

                Preference(
                    title = refreshMetadataTitle,
                    summary = metadataSummary,
                    enabled = !isMetadataWorkerRunning,
                    onClick = { viewModel.refreshMetadata() }
                )

                Header(storageHeader)

                Preference(
                    title = editBackupsTitle,
                    summary = editBackupsSummary,
                    onClick = { handler.navigate(Screen.EditBackupsViewerScreen()) }
                )
            }
        }
    }
}
