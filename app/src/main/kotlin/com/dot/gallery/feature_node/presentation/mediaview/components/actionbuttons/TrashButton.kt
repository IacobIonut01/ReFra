package com.dot.gallery.feature_node.presentation.mediaview.components.actionbuttons

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dot.gallery.R
import com.dot.gallery.core.LocalMediaDistributor
import com.dot.gallery.core.LocalMediaHandler
import com.dot.gallery.core.PendingRemovalScope
import com.dot.gallery.core.Settings.Misc.rememberTrashEnabled
import com.dot.gallery.core.util.SdkCompat
import com.dot.gallery.feature_node.domain.model.Media
import com.dot.gallery.feature_node.domain.model.MediaState
import com.dot.gallery.feature_node.domain.model.Vault
import com.dot.gallery.feature_node.domain.repository.MediaMutationResult
import com.dot.gallery.feature_node.domain.util.isCloud
import com.dot.gallery.feature_node.domain.util.isEncrypted
import com.dot.gallery.feature_node.presentation.trashed.components.TrashDialog
import com.dot.gallery.feature_node.presentation.trashed.components.TrashDialogAction
import com.dot.gallery.feature_node.presentation.trashed.components.resolveTrashDialogAction
import com.dot.gallery.feature_node.presentation.util.rememberActivityResult
import com.dot.gallery.feature_node.presentation.util.rememberAppBottomSheetState
import com.dot.gallery.feature_node.presentation.util.toastError
import kotlinx.coroutines.launch

@Composable
fun <T : Media> TrashButton(
    media: T,
    followTheme: Boolean = false,
    enabled: Boolean,
    deleteMedia: ((Vault, T, () -> Unit) -> Unit)?,
    currentVault: Vault?,
    cloudSupportsTrash: Boolean = false,
    onTrashConfirmed: () -> Unit = {}
) {
    val handler = LocalMediaHandler.current
    val distributor = LocalMediaDistributor.current
    var shouldMoveToTrash by rememberSaveable { mutableStateOf(true) }
    val state = rememberAppBottomSheetState()
    val scope = rememberCoroutineScope()
    val deletionError = toastError(
        stringResource(if (media.isCloud) R.string.cloud_media_delete_failed else R.string.error_toast)
    )
    val trashEnabled by rememberTrashEnabled()
    val effectiveAction = resolveTrashDialogAction(
        trashRequested = shouldMoveToTrash && !media.isEncrypted,
        trashEnabled = if (media.isCloud) cloudSupportsTrash else trashEnabled,
        trashSupported = if (media.isCloud) cloudSupportsTrash else SdkCompat.supportsTrash
    )
    val trashEnabledRes = if (effectiveAction == TrashDialogAction.TRASH) {
        R.string.trash
    } else {
        R.string.action_delete_permanently
    }
    // A trashed item is arriving in the trash view (so the mark must not hide it there);
    // every other confirmation removes it from all views.
    val pendingScope = if (effectiveAction == TrashDialogAction.TRASH) {
        PendingRemovalScope.NON_TRASH
    } else {
        PendingRemovalScope.EVERYWHERE
    }
    // Cloud copies backing up the viewed item, for the device/cloud/both picker.
    val timelineState by distributor.timelineMediaFlow.collectAsStateWithLifecycle(
        initialValue = MediaState<Media.UriMedia>()
    )
    // Ids resolved by the delete-scope picker (can include cloud backup copies),
    // stashed while a MediaStore request is in flight.
    var pendingRemovalIds by remember { mutableStateOf<Set<Long>>(emptySet()) }
    val markPendingRemoval = {
        val ids = pendingRemovalIds.ifEmpty { setOf(media.id) }
        distributor.markPendingRemoval(ids, pendingScope)
        pendingRemovalIds = emptySet()
        if (media.id in ids) onTrashConfirmed()
    }
    val result = rememberActivityResult(
        onResultCanceled = {
            pendingRemovalIds = emptySet()
            scope.launch {
                state.hide()
                shouldMoveToTrash = true
            }
        },
        onResultOk = markPendingRemoval
    )
    MediaViewButton(
        currentMedia = media,
        imageVector = Icons.Outlined.DeleteOutline,
        followTheme = followTheme,
        title = stringResource(id = trashEnabledRes),
        onItemLongClick = {
            shouldMoveToTrash = false
            scope.launch {
                state.show()
            }
        },
        onItemClick = {
            shouldMoveToTrash = true
            scope.launch {
                state.show()
            }
        },
        enabled = enabled
    )

    TrashDialog(
        appBottomSheetState = state,
        data = listOf(media),
        action = if (deleteMedia != null && currentVault != null) {
            TrashDialogAction.DELETE
        } else {
            effectiveAction
        },
        cloudBackups = timelineState.cloudBackups
    ) { items ->
        if (deleteMedia != null && currentVault != null) {
            if (items.isNotEmpty()) {
                deleteMedia(currentVault, media) {}
            }
            distributor.markPendingRemoval(
                items.map { m -> m.id }.toSet(),
                PendingRemovalScope.EVERYWHERE
            )
            onTrashConfirmed()
        } else {
            val mutationResult = if (effectiveAction == TrashDialogAction.TRASH) {
                handler.trashMedia(result, items, true)
            } else {
                handler.deleteMedia(result, items)
            }
            when (mutationResult) {
                MediaMutationResult.COMPLETED -> {
                    val removedIds = items.mapTo(HashSet()) { m -> m.id }
                    distributor.markPendingRemoval(removedIds, pendingScope)
                    // Only leave the viewer when the viewed item itself was
                    // removed — a cloud-only scope keeps the local copy.
                    if (media.id in removedIds) onTrashConfirmed()
                }
                MediaMutationResult.FAILED -> deletionError.show()
                MediaMutationResult.REQUEST_LAUNCHED ->
                    pendingRemovalIds = items.mapTo(HashSet()) { m -> m.id }
            }
        }
    }
}
