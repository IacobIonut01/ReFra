/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.feature_node.presentation.trashed.components

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import com.dot.gallery.core.presentation.components.SetupButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material3.Checkbox
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.bumptech.glide.integration.compose.ExperimentalGlideComposeApi
import com.bumptech.glide.integration.compose.GlideImage
import com.dot.gallery.R
import com.dot.gallery.cloud.ui.CloudSelectionViewModel
import com.dot.gallery.core.Constants.Animation.enterAnimation
import com.dot.gallery.core.Constants.Animation.exitAnimation
import com.dot.gallery.core.Settings
import com.dot.gallery.core.Settings.Misc.rememberTrashConfirmationEnabled
import com.dot.gallery.core.presentation.components.DragHandle
import com.dot.gallery.feature_node.domain.model.Media
import com.dot.gallery.feature_node.domain.util.getUri
import com.dot.gallery.feature_node.domain.util.isCloud
import com.dot.gallery.feature_node.presentation.trashed.components.TrashDialogAction.DELETE
import com.dot.gallery.feature_node.presentation.trashed.components.TrashDialogAction.RESTORE
import com.dot.gallery.feature_node.presentation.trashed.components.TrashDialogAction.TRASH
import com.dot.gallery.feature_node.presentation.util.AppBottomSheetState
import com.dot.gallery.feature_node.presentation.util.GlideInvalidation
import com.dot.gallery.feature_node.presentation.util.rememberFeedbackManager
import com.dot.gallery.ui.theme.Shapes
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalGlideComposeApi::class)
@Composable
fun <T : Media> TrashDialog(
    appBottomSheetState: AppBottomSheetState,
    data: List<T>,
    action: TrashDialogAction,
    cloudBackups: Map<Long, List<Media.UriMedia>> = emptyMap(),
    // Ids of items that cannot go through a recoverable trash path (e.g. cloud
    // items whose provider has no bin). Under a TRASH action they are marked in
    // the preview row and the dialog warns they will be deleted permanently —
    // the same treatment un-trashable SD-card items had (#1279).
    untrashableIds: Set<Long> = emptySet(),
    onConfirm: suspend (List<Media>) -> Unit
) {
    val dataCopy = remember(data) {
        data.toMutableStateList()
    }
    var confirmed by remember { mutableStateOf(false) }
    var resolvedItemCount by remember { mutableIntStateOf(0) }
    val scope = rememberCoroutineScope()
    val cloudSelectionViewModel = hiltViewModel<CloudSelectionViewModel>()
    var savedDeleteScope by Settings.Misc.rememberCloudDeleteScope()
    var setScopeAsDefault by remember { mutableStateOf(false) }

    // Cloud copies backing up the local items still in the list. When any exist
    // the user picks where the deletion applies — device, cloud, or both — unless
    // a default scope was saved (#1241). For trash the picker is only offered when
    // every copy's provider has a real bin; otherwise "trash from cloud" would be
    // a misleading permanent remote delete.
    val cloudCopies = dataCopy.flatMap { cloudBackups[it.id].orEmpty() }
    val scopeAllowed = action != RESTORE && cloudCopies.isNotEmpty() &&
        (action != TRASH || cloudSelectionViewModel.supportsTrash(cloudCopies))
    val askScope = scopeAllowed && savedDeleteScope == Settings.Misc.DELETE_SCOPE_ASK

    fun resolveItems(deleteScope: String): List<Media> = when (deleteScope) {
        Settings.Misc.DELETE_SCOPE_CLOUD -> dataCopy.filter { it.isCloud } + cloudCopies
        Settings.Misc.DELETE_SCOPE_BOTH -> dataCopy + cloudCopies
        else -> dataCopy.filter { !it.isCloud }
    }

    fun resolvedItems(): List<Media> =
        if (scopeAllowed) resolveItems(savedDeleteScope) else dataCopy.toList()

    val confirmItems: suspend (List<Media>) -> Unit = { items ->
        resolvedItemCount = items.size
        confirmed = true
        onConfirm.invoke(items)
        appBottomSheetState.hide()
    }

    val requireConfirmation by rememberTrashConfirmationEnabled()
    LaunchedEffect(appBottomSheetState.isVisible, requireConfirmation, action, askScope) {
        // `!confirmed` matters: saving a scope default flips askScope mid-flight,
        // which relaunches this effect while the hide animation still reports
        // the sheet visible — without the guard the confirm would fire twice.
        if (appBottomSheetState.isVisible && !requireConfirmation &&
            action == TRASH && !askScope && !confirmed
        ) {
            confirmItems(resolvedItems())
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
            dragHandle = { DragHandle() },
            contentWindowInsets = { WindowInsets(0, 0, 0, 0) }
        ) {
            val tertiaryContainer = MaterialTheme.colorScheme.tertiaryContainer
            val tertiaryOnContainer = MaterialTheme.colorScheme.onTertiaryContainer
            val mainButtonDefaultText = stringResource(
                when (action) {
                    TRASH -> R.string.action_move_to_trash
                    DELETE -> R.string.action_delete_permanently
                    RESTORE -> R.string.trash_restore
                }
            )
            val mainButtonConfirmText = stringResource(R.string.action_confirmed)
            val mainButtonText = remember(confirmed) {
                if (confirmed) mainButtonConfirmText else mainButtonDefaultText
            }
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                AnimatedVisibility(
                    visible = !confirmed,
                    enter = enterAnimation,
                    exit = exitAnimation
                ) {
                    val text = when (action) {
                        TRASH -> stringResource(R.string.dialog_to_trash)
                        DELETE -> stringResource(R.string.dialog_delete)
                        RESTORE -> stringResource(R.string.dialog_from_trash)
                    }
                    Column {
                        Text(
                            text = buildAnnotatedString {
                                withStyle(
                                    style = SpanStyle(
                                        color = MaterialTheme.colorScheme.onSurface,
                                        fontStyle = MaterialTheme.typography.titleLarge.fontStyle,
                                        fontSize = MaterialTheme.typography.titleLarge.fontSize,
                                        letterSpacing = MaterialTheme.typography.titleLarge.letterSpacing
                                    )
                                ) {
                                    append(text)
                                }
                                append("\n")
                                withStyle(
                                    style = SpanStyle(
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontStyle = MaterialTheme.typography.bodyMedium.fontStyle,
                                        fontSize = MaterialTheme.typography.bodyMedium.fontSize,
                                        letterSpacing = MaterialTheme.typography.bodyMedium.letterSpacing
                                    )
                                ) {
                                    append(stringResource(R.string.s_items, dataCopy.size))
                                }
                            },
                            textAlign = TextAlign.Center,
                            modifier = Modifier
                                .padding(24.dp)
                                .fillMaxWidth()
                        )
                    }
                }

                AnimatedVisibility(
                    visible = confirmed,
                    enter = enterAnimation,
                    exit = exitAnimation
                ) {
                    val itemCount = resolvedItemCount.takeIf { it > 0 } ?: dataCopy.size
                    val text =
                        when (action) {
                            TRASH -> stringResource(R.string.trashing_items, itemCount)
                            DELETE -> stringResource(R.string.deleting_items, itemCount)
                            RESTORE -> stringResource(R.string.restoring_items, itemCount)
                        }
                    Text(
                        text = text,
                        style = MaterialTheme.typography.titleLarge,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .padding(24.dp)
                            .fillMaxWidth()
                    )
                }

                val hasUntrashable by remember(action, untrashableIds) {
                    derivedStateOf { action == TRASH && dataCopy.any { it.id in untrashableIds } }
                }
                AnimatedVisibility(visible = hasUntrashable && !confirmed) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Row(
                            modifier = Modifier
                                .background(
                                    color = MaterialTheme.colorScheme.errorContainer,
                                    shape = Shapes.large
                                )
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                modifier = Modifier.size(18.dp),
                                imageVector = Icons.Outlined.ErrorOutline,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onErrorContainer
                            )
                            Text(
                                text = stringResource(R.string.trash_incompatible_title),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                        Text(
                            text = stringResource(R.string.trash_incompatible_summary),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
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
                        .padding(vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp, alignment),
                ) {
                    if (dataCopy.size > 1) {
                        item {
                            Spacer(modifier = Modifier.width(16.dp))
                        }
                    }
                    items(
                        items = dataCopy,
                        key = { it.toString() },
                        contentType = { it.mimeType }
                    ) {
                        val context = LocalContext.current
                        val longPressText = stringResource(R.string.long_press_to_remove)
                        val untrashable = action == TRASH && it.id in untrashableIds
                        val borderWidth = if (untrashable) 2.dp else 0.5.dp
                        val borderColor =
                            if (untrashable) MaterialTheme.colorScheme.error
                            else MaterialTheme.colorScheme.onSurfaceVariant
                        val shape = if (untrashable) Shapes.extraLarge else Shapes.large
                        val feedbackManager = rememberFeedbackManager()
                        Box(
                            modifier = Modifier
                                .animateItem()
                                .size(width = 80.dp, height = 120.dp)
                                .clip(shape)
                                .border(
                                    width = borderWidth,
                                    color = borderColor,
                                    shape = shape
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
                                        Toast
                                            .makeText(context, longPressText, Toast.LENGTH_SHORT)
                                            .show()
                                    }
                                )
                        ) {
                            GlideImage(
                                modifier = Modifier.fillMaxSize(),
                                model = it.getUri(),
                                contentDescription = it.label,
                                contentScale = ContentScale.Crop,
                                requestBuilderTransform = { builder ->
                                    builder.signature(GlideInvalidation.signature(it))
                                }
                            )
                        }
                    }
                }

                if (askScope) {
                    // The selection holds items that exist both on the device and
                    // in a cloud backup, so the user picks where the deletion
                    // applies (#1241). The checkbox saves the choice as the
                    // default scope for future deletes.
                    val secondaryContainer = MaterialTheme.colorScheme.secondaryContainer
                    val onSecondaryContainer = MaterialTheme.colorScheme.onSecondaryContainer
                    val backedUpCount = dataCopy.count { cloudBackups[it.id] != null }
                    val confirmScope: (String) -> Unit = { deleteScope ->
                        if (setScopeAsDefault) savedDeleteScope = deleteScope
                        confirmed = true
                        scope.launch {
                            confirmItems(resolveItems(deleteScope))
                        }
                    }
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = pluralStringResource(
                                R.plurals.delete_scope_backed_up_info,
                                backedUpCount,
                                backedUpCount
                            ),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 8.dp)
                        )
                        SetupButton(
                            onClick = { confirmScope(Settings.Misc.DELETE_SCOPE_DEVICE) },
                            containerColor = secondaryContainer,
                            contentColor = onSecondaryContainer,
                            applyHorizontalPadding = false,
                            applyBottomPadding = false,
                            applyInsets = false,
                            enabled = !confirmed,
                            text = stringResource(R.string.delete_scope_from_device)
                        )
                        SetupButton(
                            onClick = { confirmScope(Settings.Misc.DELETE_SCOPE_CLOUD) },
                            containerColor = secondaryContainer,
                            contentColor = onSecondaryContainer,
                            applyHorizontalPadding = false,
                            applyBottomPadding = false,
                            applyInsets = false,
                            enabled = !confirmed,
                            text = stringResource(R.string.delete_scope_from_cloud)
                        )
                        SetupButton(
                            onClick = { confirmScope(Settings.Misc.DELETE_SCOPE_BOTH) },
                            containerColor = secondaryContainer,
                            contentColor = onSecondaryContainer,
                            applyHorizontalPadding = false,
                            applyBottomPadding = false,
                            applyInsets = false,
                            enabled = !confirmed,
                            text = stringResource(R.string.delete_scope_from_both)
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = setScopeAsDefault,
                                onCheckedChange = { setScopeAsDefault = it }
                            )
                            Text(
                                text = stringResource(R.string.delete_scope_set_default),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        SetupButton(
                            onClick = {
                                scope.launch {
                                    appBottomSheetState.hide()
                                }
                            },
                            containerColor = tertiaryContainer,
                            contentColor = tertiaryOnContainer,
                            applyHorizontalPadding = false,
                            applyBottomPadding = false,
                            applyInsets = false,
                            text = stringResource(R.string.action_cancel)
                        )
                    }
                } else {
                    if (scopeAllowed) {
                        Text(
                            text = stringResource(
                                when (savedDeleteScope) {
                                    Settings.Misc.DELETE_SCOPE_CLOUD -> R.string.delete_scope_hint_cloud
                                    Settings.Misc.DELETE_SCOPE_BOTH -> R.string.delete_scope_hint_both
                                    else -> R.string.delete_scope_hint_device
                                }
                            ),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 24.dp)
                        )
                    }
                    Row(
                        modifier = Modifier.padding(24.dp),
                        horizontalArrangement = Arrangement
                            .spacedBy(24.dp, Alignment.CenterHorizontally)
                    ) {
                        AnimatedVisibility(
                            visible = !confirmed,
                            modifier = Modifier.weight(1f)
                        ) {
                            SetupButton(
                                onClick = {
                                    scope.launch {
                                        appBottomSheetState.hide()
                                    }
                                },
                                containerColor = tertiaryContainer,
                                contentColor = tertiaryOnContainer,
                                applyHorizontalPadding = false,
                                applyBottomPadding = false,
                                applyInsets = false,
                                text = stringResource(R.string.action_cancel)
                            )
                        }
                        SetupButton(
                            enabled = !confirmed,
                            modifier = Modifier.weight(1f),
                            onClick = {
                                confirmed = true
                                scope.launch {
                                    confirmItems(resolvedItems())
                                }
                            },
                            applyHorizontalPadding = false,
                            applyBottomPadding = false,
                            applyInsets = false,
                            text = mainButtonText
                        )
                    }
                }
            }
            Spacer(modifier = Modifier)
        }
    }
}
