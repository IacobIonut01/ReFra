/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.app.presentation.duplicates

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dot.gallery.core.LocalMediaDistributor
import com.dot.gallery.core.LocalMediaHandler
import com.dot.gallery.core.PendingRemovalScope
import com.dot.gallery.core.LocalEventHandler
import com.dot.gallery.core.navigate
import com.dot.gallery.core.presentation.util.Screen
import com.dot.gallery.core.duplicates.DuplicateSet
import com.dot.gallery.core.presentation.components.ConfirmationSheet
import com.dot.gallery.core.presentation.components.SheetSummaryTile
import com.dot.gallery.core.presentation.components.SheetTone
import com.dot.gallery.core.ui.util.rememberActivityResult
import com.dot.gallery.core.ui.util.rememberAppBottomSheetState
import com.dot.gallery.core.util.SdkCompat
import com.dot.gallery.core.work.WorkSignals
import com.dot.gallery.core.work.isFinished
import com.dot.gallery.core.workers.DUPLICATE_HASH_DONE
import com.dot.gallery.core.workers.DUPLICATE_HASH_TOTAL
import com.dot.gallery.core.workers.DUPLICATE_HASH_WORK
import com.dot.gallery.core.workers.DuplicateHashWorker
import com.dot.gallery.data.repository.MediaMutationResult
import com.dot.gallery.domain.model.UriMedia
import com.dot.gallery.feature.albums.ui.util.formatSizeShort
import com.dot.gallery.generated.resources.Res
import com.dot.gallery.generated.resources.duplicates_confirm_action
import com.dot.gallery.generated.resources.duplicates_confirm_delete_action
import com.dot.gallery.generated.resources.duplicates_confirm_delete_summary
import com.dot.gallery.generated.resources.duplicates_confirm_delete_title
import com.dot.gallery.generated.resources.duplicates_confirm_details
import com.dot.gallery.generated.resources.duplicates_confirm_summary
import com.dot.gallery.generated.resources.duplicates_confirm_title
import com.dot.gallery.injection.injectViewModel
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/** Progress of a running duplicate check, or null when none is queued or running. */
data class DuplicateCheckProgress(val done: Int, val total: Int)

@Composable
fun DuplicatesScreen() {
    val viewModel = injectViewModel<DuplicatesViewModel> { duplicatesViewModel(it) }
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val handler = LocalMediaHandler.current
    val distributor = LocalMediaDistributor.current
    val scope = rememberCoroutineScope()
    val eventHandler = LocalEventHandler.current

    val work by remember {
        WorkSignals.observe(DUPLICATE_HASH_WORK, DuplicateHashWorker.WORKER_NAME)
    }.collectAsStateWithLifecycle(initialValue = null)
    val progress = work?.takeIf { !it.state.isFinished }?.let {
        DuplicateCheckProgress(
            done = it.progress.getInt(DUPLICATE_HASH_DONE, 0),
            total = it.progress.getInt(DUPLICATE_HASH_TOTAL, 0),
        )
    }

    var pendingIds by remember { mutableStateOf<Set<Long>>(emptySet()) }
    fun removed(ids: Set<Long>) {
        distributor.markPendingRemoval(ids, PendingRemovalScope.EVERYWHERE)
        viewModel.onRemoved(ids)
    }
    val request = rememberActivityResult(
        onResultCanceled = { pendingIds = emptySet() },
        onResultOk = {
            removed(pendingIds)
            pendingIds = emptySet()
        },
    )

    val confirmState = rememberAppBottomSheetState()
    var preview by remember { mutableStateOf<Pair<DuplicateSet<UriMedia>, Long>?>(null) }

    DuplicatesContent(
        state = state,
        progress = progress,
        onFilter = viewModel::setFilter,
        onToggle = viewModel::toggle,
        onOpen = { set, id -> preview = set to id },
        onOpenSettings = { eventHandler.navigate(Screen.DuplicateSettingsScreen.route) },
        onCheckAgain = viewModel::checkAgain,
        onCheckLargeOnce = viewModel::checkLargeOnce,
        onMoveToTrash = { scope.launch { confirmState.show() } },
    )

    val selected = state.selected
    val canTrash = SdkCompat.supportsTrash
    ConfirmationSheet(
        state = confirmState,
        title = if (canTrash) {
            pluralStringResource(Res.plurals.duplicates_confirm_title, selected.size, selected.size)
        } else {
            pluralStringResource(Res.plurals.duplicates_confirm_delete_title, selected.size, selected.size)
        },
        summary = stringResource(
            if (canTrash) Res.string.duplicates_confirm_summary else Res.string.duplicates_confirm_delete_summary
        ),
        confirmText = stringResource(
            if (canTrash) Res.string.duplicates_confirm_action else Res.string.duplicates_confirm_delete_action
        ),
        icon = Icons.Outlined.ContentCopy,
        tone = if (canTrash) SheetTone.Primary else SheetTone.Error,
        confirmIcon = Icons.Outlined.DeleteOutline,
        details = {
            val sets = state.sets.count { set -> set.selected(state.choices) { it.id }.isNotEmpty() }
            SheetSummaryTile(
                title = stringResource(
                    Res.string.duplicates_confirm_details,
                    formatSizeShort(state.selectedBytes),
                    sets,
                ),
            )
        },
        onConfirm = {
            val targets = state.selected
            if (targets.isEmpty()) return@ConfirmationSheet
            scope.launch {
                val ids = targets.mapTo(HashSet()) { it.id }
                val result = if (canTrash) {
                    handler.trashMedia(request, targets, true)
                } else {
                    handler.deleteMedia(request, targets)
                }
                when (result) {
                    MediaMutationResult.COMPLETED -> removed(ids)
                    MediaMutationResult.REQUEST_LAUNCHED -> pendingIds = ids
                    MediaMutationResult.FAILED -> Unit
                }
            }
        },
    )

    preview?.let { (set, startId) ->
        // Follow the live set so a choice made in the preview shows at once.
        val live = state.sets.firstOrNull { it.sha1 == set.sha1 }
        if (live == null) {
            preview = null
        } else {
            DuplicateCopyPreview(
                set = live,
                startId = startId,
                choices = state.choices,
                onKeep = { id -> viewModel.keep(live, id) },
                onDontRemove = { id -> viewModel.dontRemove(live, id) },
                onDismiss = { preview = null },
            )
        }
    }
}
