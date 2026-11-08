/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.core.presentation.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.DeleteForever
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.RestoreFromTrash
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.backhandler.BackHandler
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.dot.gallery.core.presentation.util.remove
import com.dot.gallery.core.ui.settings.rememberPreference
import com.dot.gallery.core.ui.util.AppBottomSheetState
import com.dot.gallery.core.ui.util.rememberPlatformToast
import com.dot.gallery.core.util.rememberFeedbackManager
import com.dot.gallery.domain.model.Media
import com.dot.gallery.domain.model.UriMedia
import com.dot.gallery.domain.settings.DELETE_SCOPE_ASK
import com.dot.gallery.domain.settings.DELETE_SCOPE_BOTH
import com.dot.gallery.domain.settings.DELETE_SCOPE_CLOUD
import com.dot.gallery.domain.settings.DELETE_SCOPE_DEVICE
import com.dot.gallery.domain.settings.SettingsKeys
import com.dot.gallery.domain.trash.TrashDialogAction
import com.dot.gallery.domain.trash.TrashDialogAction.DELETE
import com.dot.gallery.domain.trash.TrashDialogAction.RESTORE
import com.dot.gallery.domain.trash.TrashDialogAction.TRASH
import com.dot.gallery.domain.util.getUri
import com.dot.gallery.domain.util.isCloud
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import com.dot.gallery.core.presentation.components.DragHandle
import com.dot.gallery.core.ui.generated.resources.Res
import com.dot.gallery.core.ui.generated.resources.action_cancel
import com.dot.gallery.core.ui.generated.resources.action_confirmed
import com.dot.gallery.core.ui.generated.resources.action_delete_permanently
import com.dot.gallery.core.ui.generated.resources.action_move_to_trash
import com.dot.gallery.core.ui.generated.resources.delete_scope_backed_up_info
import com.dot.gallery.core.ui.generated.resources.delete_scope_from_both
import com.dot.gallery.core.ui.generated.resources.delete_scope_from_cloud
import com.dot.gallery.core.ui.generated.resources.delete_scope_from_device
import com.dot.gallery.core.ui.generated.resources.delete_scope_hint_both
import com.dot.gallery.core.ui.generated.resources.delete_scope_hint_cloud
import com.dot.gallery.core.ui.generated.resources.delete_scope_hint_device
import com.dot.gallery.core.ui.generated.resources.delete_scope_set_default
import com.dot.gallery.core.ui.generated.resources.deleting_items
import com.dot.gallery.core.ui.generated.resources.dialog_delete
import com.dot.gallery.core.ui.generated.resources.dialog_from_trash
import com.dot.gallery.core.ui.generated.resources.dialog_to_trash
import com.dot.gallery.core.ui.generated.resources.long_press_to_remove
import com.dot.gallery.core.ui.generated.resources.restoring_items
import com.dot.gallery.core.ui.generated.resources.s_items
import com.dot.gallery.core.ui.generated.resources.trash_incompatible_summary
import com.dot.gallery.core.ui.generated.resources.trash_incompatible_title
import com.dot.gallery.core.ui.generated.resources.trash_restore
import com.dot.gallery.core.ui.generated.resources.trashing_items


@OptIn(ExperimentalMaterial3Api::class, androidx.compose.ui.ExperimentalComposeUiApi::class)
@Composable
fun <T : Media> TrashDialog(
    appBottomSheetState: AppBottomSheetState,
    data: List<T>,
    action: TrashDialogAction,
    cloudBackups: Map<Long, List<UriMedia>> = emptyMap(),
    /** Whether every listed cloud copy's provider declares a recoverable bin. */
    supportsCloudTrash: (List<UriMedia>) -> Boolean,
    // Ids of items that cannot go through a recoverable trash path (e.g. cloud
    // items whose provider has no bin). Under a TRASH action they are marked in
    // the preview row and the dialog warns they will be deleted permanently —
    // the same treatment un-trashable SD-card items had.
    untrashableIds: Set<Long> = emptySet(),
    onConfirm: suspend (List<Media>) -> Unit
) {
    val dataCopy = remember(data) {
        data.toMutableStateList()
    }
    var confirmed by remember { mutableStateOf(false) }
    var resolvedItemCount by remember { mutableIntStateOf(0) }
    val scope = rememberCoroutineScope()
    var savedDeleteScope by rememberPreference(SettingsKeys.cloudDeleteScope)
    var setScopeAsDefault by remember { mutableStateOf(false) }

    // Cloud copies backing up the local items still in the list. When any exist
    // the user picks where the deletion applies — device, cloud, or both — unless
    // a default scope was saved. For trash the picker is only offered when
    // every copy's provider has a real bin; otherwise "trash from cloud" would be
    // a misleading permanent remote delete.
    val cloudCopies = dataCopy.flatMap { cloudBackups[it.id].orEmpty() }
    val scopeAllowed = action != RESTORE && cloudCopies.isNotEmpty() &&
        (action != TRASH || supportsCloudTrash(cloudCopies))
    val askScope = scopeAllowed && savedDeleteScope == DELETE_SCOPE_ASK

    fun resolveItems(deleteScope: String): List<Media> = when (deleteScope) {
        DELETE_SCOPE_CLOUD -> dataCopy.filter { it.isCloud } + cloudCopies
        DELETE_SCOPE_BOTH -> dataCopy + cloudCopies
        else -> dataCopy.filter { !it.isCloud }
    }

    fun resolvedItems(): List<Media> =
        if (scopeAllowed) resolveItems(savedDeleteScope) else dataCopy.toList()

    val confirmItems: suspend (List<Media>) -> Unit = { items ->
        resolvedItemCount = items.size
        confirmed = true
        try {
            onConfirm.invoke(items)
        } finally {
            // The sheet must always leave, even when the confirm throws or the
            // invoking coroutine is cancelled mid-request — a wedged isVisible
            // with no sheet rendered dead-ends every later attempt.
            withContext(NonCancellable) {
                appBottomSheetState.hide()
            }
        }
    }

    val requireConfirmation by rememberPreference(SettingsKeys.trashConfirmationEnabled)
    LaunchedEffect(appBottomSheetState.isVisible, requireConfirmation, action, askScope) {
        // `!confirmed` matters: saving a scope default flips askScope mid-flight,
        // which relaunches this effect while the hide animation still reports
        // the sheet visible — without the guard the confirm would fire twice.
        if (appBottomSheetState.isVisible) {
            if (!requireConfirmation && action == TRASH && !askScope && !confirmed) {
                confirmItems(resolvedItems())
            }
        } else {
            // The no-confirmation path renders no sheet, so the reset inside the
            // UI branch below never runs for it and `confirmed` stays latched —
            // every later delete then silently does nothing. Per-show
            // state clears when the sheet leaves, whichever path used it.
            confirmed = false
            resolvedItemCount = 0
            setScopeAsDefault = false
        }
    }
    BackHandler(
        appBottomSheetState.isVisible && !confirmed
    ) {
        scope.launch {
            confirmed = false
            appBottomSheetState.hide()
        }
    }
    if (appBottomSheetState.isVisible && (requireConfirmation || action != TRASH || askScope)) {
        LaunchedEffect(appBottomSheetState.isVisible) {
            confirmed = false
            resolvedItemCount = 0
            setScopeAsDefault = false
        }
        ModalBottomSheet(
            sheetState = appBottomSheetState.sheetState,
            onDismissRequest = {
                scope.launch {
                    appBottomSheetState.hide()
                }
            },
            dragHandle = { DragHandle(modifier = Modifier.padding(vertical = 5.dp)) },
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            tonalElevation = 0.dp,
            shape = ExpressiveSheetShape,
            contentWindowInsets = { WindowInsets(0, 0, 0, 0) }
        ) {
            ExpressiveSheetColumn {
                TrashDialogHeader(
                    action = action,
                    confirmed = confirmed,
                    itemCount = dataCopy.size,
                    resolvedItemCount = resolvedItemCount,
                )

                val hasUntrashable by remember(action, untrashableIds) {
                    derivedStateOf { action == TRASH && dataCopy.any { it.id in untrashableIds } }
                }
                AnimatedVisibility(visible = hasUntrashable && !confirmed) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp)
                            .clip(RoundedCornerShape(24.dp))
                            .background(MaterialTheme.colorScheme.errorContainer)
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.ErrorOutline,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onErrorContainer
                        )
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(
                                text = stringResource(Res.string.trash_incompatible_title),
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                            Text(
                                text = stringResource(Res.string.trash_incompatible_summary),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                    }
                }

                val alpha by animateFloatAsState(
                    targetValue = if (!confirmed) 1f else 0.5f,
                    label = "alphaAnimation"
                )

                val alignment = if (dataCopy.size == 1) {
                    Alignment.CenterHorizontally
                } else Alignment.Start

                LazyRow(
                    modifier = Modifier
                        .alpha(alpha)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(24.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                        .padding(vertical = 12.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp, alignment),
                ) {
                    items(
                        items = dataCopy,
                        key = { it.toString() },
                        contentType = { it.mimeType }
                    ) {
                        val showToast = rememberPlatformToast()
                        val longPressText = stringResource(Res.string.long_press_to_remove)
                        val untrashable = action == TRASH && it.id in untrashableIds
                        val shape = RoundedCornerShape(16.dp)
                        val feedbackManager = rememberFeedbackManager()
                        Box(
                            modifier = Modifier
                                .animateItem()
                                .size(width = 72.dp, height = 96.dp)
                                .clip(shape)
                                .then(
                                    if (untrashable) Modifier.border(2.dp, MaterialTheme.colorScheme.error, shape)
                                    else Modifier
                                )
                                .combinedClickable(
                                    enabled = !confirmed,
                                    onLongClick = {
                                        feedbackManager.vibrate()
                                        scope.launch {
                                            dataCopy.remove(it)
                                            if (dataCopy.isEmpty()) {
                                                appBottomSheetState.hide()
                                                dataCopy.addAll(data)
                                            }
                                        }
                                    },
                                    onClick = {
                                        feedbackManager.vibrateStrong()
                                        showToast(longPressText)
                                    }
                                )
                        ) {
                            LocalMediaImageRenderer.current.RenderImage(
                                modifier = Modifier.fillMaxSize(),
                                model = it.getUri(),
                                contentScale = ContentScale.Crop,
                                contentDescription = it.label,
                                signature = it
                            )
                        }
                    }
                }

                if (askScope) {
                    // The selection holds items that exist both on the device and
                    // in a cloud backup, so the user picks where the deletion
                    // applies. The checkbox saves the choice as the
                    // default scope for future deletes.
                    val backedUpCount = dataCopy.count { cloudBackups[it.id] != null }
                    val confirmScope: (String) -> Unit = { deleteScope ->
                        if (setScopeAsDefault) savedDeleteScope = deleteScope
                        confirmed = true
                        scope.launch {
                            confirmItems(resolveItems(deleteScope))
                        }
                    }
                    Text(
                        text = pluralStringResource(
                            Res.plurals.delete_scope_backed_up_info,
                            backedUpCount,
                            backedUpCount
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 16.dp)
                    )
                    val scopes = listOf(
                        DELETE_SCOPE_DEVICE to Res.string.delete_scope_from_device,
                        DELETE_SCOPE_CLOUD to Res.string.delete_scope_from_cloud,
                        DELETE_SCOPE_BOTH to Res.string.delete_scope_from_both,
                    )
                    scopes.forEachIndexed { index, (deleteScope, label) ->
                        SheetOptionRow(
                            title = stringResource(label),
                            position = sheetRowPosition(index, scopes.size),
                            trailing = SheetRowTrailing.Chevron,
                            enabled = !confirmed,
                            onClick = { confirmScope(deleteScope) },
                        )
                    }
                    SheetOptionRow(
                        title = stringResource(Res.string.delete_scope_set_default),
                        modifier = Modifier.padding(top = 8.dp),
                        trailing = SheetRowTrailing.Checkbox(setScopeAsDefault),
                        onClick = { setScopeAsDefault = !setScopeAsDefault },
                    )
                    SheetActions {
                        SheetButton(
                            text = stringResource(Res.string.action_cancel),
                            onClick = { scope.launch { appBottomSheetState.hide() } },
                            style = SheetButtonStyle.Tonal,
                            modifier = Modifier.weight(1f),
                        )
                    }
                } else {
                    if (scopeAllowed) {
                        Text(
                            text = stringResource(
                                when (savedDeleteScope) {
                                    DELETE_SCOPE_CLOUD -> Res.string.delete_scope_hint_cloud
                                    DELETE_SCOPE_BOTH -> Res.string.delete_scope_hint_both
                                    else -> Res.string.delete_scope_hint_device
                                }
                            ),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp, vertical = 16.dp)
                        )
                    }
                    TrashDialogActions(
                        action = action,
                        confirmed = confirmed,
                        onCancel = { scope.launch { appBottomSheetState.hide() } },
                        onConfirm = {
                            confirmed = true
                            scope.launch {
                                confirmItems(resolvedItems())
                            }
                        },
                    )
                }
            }
        }
    }
}

/**
 * Heading of the trash, restore and delete confirmation: a toned badge, the action title and the
 * item count, or the in-progress message once confirmed.
 */
@Composable
fun TrashDialogHeader(
    action: TrashDialogAction,
    confirmed: Boolean,
    itemCount: Int,
    resolvedItemCount: Int = 0,
) {
    val count = resolvedItemCount.takeIf { it > 0 } ?: itemCount
    SheetHeader(
        title = if (confirmed) {
            when (action) {
                TRASH -> stringResource(Res.string.trashing_items, count)
                DELETE -> stringResource(Res.string.deleting_items, count)
                RESTORE -> stringResource(Res.string.restoring_items, count)
            }
        } else {
            when (action) {
                TRASH -> stringResource(Res.string.dialog_to_trash)
                DELETE -> stringResource(Res.string.dialog_delete)
                RESTORE -> stringResource(Res.string.dialog_from_trash)
            }
        },
        body = if (confirmed) null else stringResource(Res.string.s_items, itemCount),
        icon = when (action) {
            TRASH -> Icons.Outlined.Delete
            DELETE -> Icons.Outlined.DeleteForever
            RESTORE -> Icons.Outlined.RestoreFromTrash
        },
        tone = if (action == RESTORE) SheetTone.Primary else SheetTone.Error,
    )
}

/** Cancel and confirm pair of [TrashDialog]; the confirm is an error button for trash and delete. */
@Composable
fun TrashDialogActions(
    action: TrashDialogAction,
    confirmed: Boolean,
    onCancel: () -> Unit,
    onConfirm: () -> Unit,
) {
    SheetActions {
        AnimatedVisibility(visible = !confirmed, modifier = Modifier.weight(1f)) {
            SheetButton(
                text = stringResource(Res.string.action_cancel),
                onClick = onCancel,
                style = SheetButtonStyle.Tonal,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        SheetButton(
            text = if (confirmed) stringResource(Res.string.action_confirmed) else stringResource(
                when (action) {
                    TRASH -> Res.string.action_move_to_trash
                    DELETE -> Res.string.action_delete_permanently
                    RESTORE -> Res.string.trash_restore
                }
            ),
            onClick = onConfirm,
            enabled = !confirmed,
            style = if (action == RESTORE) SheetButtonStyle.Filled else SheetButtonStyle.Error,
            icon = when (action) {
                TRASH -> Icons.Outlined.Delete
                DELETE -> Icons.Outlined.DeleteForever
                RESTORE -> Icons.Outlined.RestoreFromTrash
            },
            modifier = Modifier.weight(1f),
        )
    }
}
