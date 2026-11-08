package com.dot.gallery.app.presentation.ignored

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dot.gallery.core.ui.util.rememberAppBottomSheetState
import com.dot.gallery.domain.model.AlbumState
import com.dot.gallery.domain.model.IgnoredAlbum
import com.dot.gallery.feature.ignored.ui.IgnoredContent
import com.dot.gallery.feature.ignored.ui.IgnoredRuleEdit
import com.dot.gallery.feature.ignored.ui.IgnoredRuleSheet
import com.dot.gallery.feature.ignored.ui.IgnoredViewModel
import com.dot.gallery.feature.ignored.ui.setup.IgnoredSetupSheet
import com.dot.gallery.feature.ignored.ui.setup.IgnoredSetupViewModel
import com.dot.gallery.feature.ignored.ui.setup.SetupStep
import com.dot.gallery.feature.ignored.ui.showStoppedIgnoring
import com.dot.gallery.injection.injectViewModel
import kotlinx.coroutines.launch

@Composable
fun IgnoredScreen(
    albumsState: State<AlbumState>,
) {
    val vm = injectViewModel<IgnoredViewModel> { ignoredViewModel }
    val setupVm = injectViewModel<IgnoredSetupViewModel> { ignoredSetupViewModel }
    val state by vm.blacklistState.collectAsStateWithLifecycle()
    val setupUiState by setupVm.uiState.collectAsStateWithLifecycle()
    val setupSheetState = rememberAppBottomSheetState()
    val ruleSheetState = rememberAppBottomSheetState()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var selectedRule by remember { mutableStateOf<IgnoredAlbum?>(null) }

    IgnoredContent(
        state = state,
        albumsState = albumsState,
        snackbarHostState = snackbarHostState,
        onAddClick = {
            setupVm.startNew()
            scope.launch { setupSheetState.show() }
        },
        onAlbumClick = { rule ->
            selectedRule = rule
            scope.launch { ruleSheetState.show() }
        }
    )

    LaunchedEffect(albumsState.value.albumsWithBlacklisted) {
        setupVm.updateAlbums(albumsState.value.albumsWithBlacklisted)
    }

    IgnoredRuleSheet(
        sheetState = ruleSheetState,
        rule = selectedRule?.let { selected -> state.albums.firstOrNull { it.id == selected.id } ?: selected },
        albums = albumsState.value.albumsWithBlacklisted,
        onEdit = { rule, edit ->
            setupVm.startEdit(
                rule = rule,
                step = if (edit == IgnoredRuleEdit.ALBUMS) SetupStep.CHOOSE else SetupStep.WHERE
            )
            scope.launch { setupSheetState.show() }
        },
        onStopIgnoring = { rule ->
            vm.removeFromBlacklist(rule)
            scope.launch {
                if (snackbarHostState.showStoppedIgnoring(rule)) vm.updateIgnoredAlbum(rule)
            }
        },
        onDismiss = { selectedRule = null }
    )

    IgnoredSetupSheet(
        sheetState = setupSheetState,
        uiState = setupUiState,
        onAction = setupVm::onAction,
        onDismiss = setupVm::reset,
    )
}
