/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.core.presentation.components

import android.app.Activity
import android.content.Intent
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.compose.LocalActivity
import androidx.activity.result.IntentSenderRequest
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Deselect
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Restore
import androidx.compose.material.icons.outlined.SelectAll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dot.gallery.R
import com.dot.gallery.core.LocalMediaDistributor
import com.dot.gallery.core.LocalMediaHandler
import com.dot.gallery.core.LocalMediaSelector
import com.dot.gallery.core.Settings
import com.dot.gallery.core.Settings.Misc.rememberAllowBlur
import com.dot.gallery.core.Settings.Misc.rememberSelectionSheetConfig
import com.dot.gallery.core.Settings.Misc.rememberShowFavoriteButton
import com.dot.gallery.core.Settings.Misc.rememberShowSelectionTitles
import com.dot.gallery.core.Settings.Misc.rememberTrashEnabled
import com.dot.gallery.core.util.SdkCompat
import com.dot.gallery.feature_node.domain.model.ActionCondition
import com.dot.gallery.feature_node.domain.model.Media
import com.dot.gallery.feature_node.domain.model.MediaMetadataState
import com.dot.gallery.feature_node.domain.model.MediaState
import com.dot.gallery.feature_node.domain.model.SelectionAction
import com.dot.gallery.feature_node.domain.model.Vault
import com.dot.gallery.feature_node.domain.util.getUri
import com.dot.gallery.feature_node.presentation.collection.CollectionViewModel
import com.dot.gallery.feature_node.presentation.collection.components.AddToCollectionSheet
import com.dot.gallery.feature_node.presentation.exif.CopyMediaSheet
import com.dot.gallery.feature_node.presentation.exif.MoveMediaSheet
import com.dot.gallery.feature_node.presentation.mediaview.components.MediaInfoRow
import com.dot.gallery.feature_node.presentation.mediaview.rememberedDerivedState
import com.dot.gallery.feature_node.presentation.trashed.components.TrashDialog
import com.dot.gallery.feature_node.presentation.trashed.components.TrashDialogAction
import com.dot.gallery.feature_node.presentation.util.LocalHazeState
import com.dot.gallery.feature_node.presentation.util.launchEditIntent
import com.dot.gallery.feature_node.presentation.util.rememberActivityResult
import com.dot.gallery.feature_node.presentation.util.rememberAppBottomSheetState
import com.dot.gallery.feature_node.presentation.util.rememberMediaInfo
import com.dot.gallery.feature_node.presentation.util.shareMediaWithVaultSupport
import com.dot.gallery.feature_node.presentation.vault.VaultViewModel
import com.dot.gallery.feature_node.presentation.vault.components.AddToVaultSheet
import com.dot.gallery.feature_node.presentation.vault.components.SelectVaultSheet
import com.dot.gallery.ui.theme.Shapes
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.materials.ExperimentalHazeMaterialsApi
import dev.chrisbanes.haze.materials.HazeMaterials
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3WindowSizeClassApi::class, ExperimentalHazeMaterialsApi::class, ExperimentalMaterial3Api::class)
@Composable
fun <T : Media> BoxScope.SelectionSheet(
    modifier: Modifier = Modifier,
    allMedia: MediaState<T>,
    selectedMedia: SnapshotStateList<T>,
    collectionId: Long? = null,
    isInVault: Boolean = false,
    currentVault: Vault? = null,
) {
    val albumsState = LocalMediaDistributor.current.albumsFlow.collectAsStateWithLifecycle()
    val selector = LocalMediaSelector.current
    val isSelectionActive by selector.isSelectionActive.collectAsStateWithLifecycle()

    val handler = LocalMediaHandler.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var shouldMoveToTrash by rememberSaveable { mutableStateOf(true) }
    val trashSheetState = rememberAppBottomSheetState()
    val moveSheetState = rememberAppBottomSheetState()
    val copySheetState = rememberAppBottomSheetState()
    var showCollectionSheet by rememberSaveable { mutableStateOf(false) }
    val collectionViewModel = hiltViewModel<CollectionViewModel>()
    val vaultViewModel = hiltViewModel<VaultViewModel>()
    val vaultSheetState = rememberAppBottomSheetState()
    // Tracks what the vault sheet is for: "hide", "copy", or "move"
    var vaultSheetAction by rememberSaveable { mutableStateOf("hide") }
    var vaultEncryptBehavior by Settings.Vault.rememberVaultEncryptBehavior()
    val addToVaultSheetState = rememberAppBottomSheetState()
    var hideTargetVault by remember { mutableStateOf<Vault?>(null) }
    val vaults = vaultViewModel.vaultState.collectAsStateWithLifecycle()
    var showInfoSheet by rememberSaveable { mutableStateOf(false) }
    val metadataState = LocalMediaDistributor.current.metadataFlow.collectAsStateWithLifecycle(
        initialValue = MediaMetadataState()
    )
    val result = rememberActivityResult(
        onResultOk = {
            selector.clearSelection()
            if (trashSheetState.isVisible) {
                scope.launch {
                    trashSheetState.hide()
                    shouldMoveToTrash = true
                }
            }
        }
    )
    val windowSizeClass = calculateWindowSizeClass(LocalActivity.current as Activity)
    val tabletMode = remember(windowSizeClass) {
        windowSizeClass.widthSizeClass > WindowWidthSizeClass.Compact
    }
    val sizeModifier = remember(tabletMode) {
        if (!tabletMode) Modifier.fillMaxWidth()
        else Modifier.wrapContentWidth()
    }
    val config by rememberSelectionSheetConfig()
    val sanitizedConfig = remember(config, isInVault) {
        val base = config.sanitized()
        if (isInVault && SelectionAction.ADD_TO_VAULT !in base.bottomActions) {
            base.copy(bottomActions = base.bottomActions + SelectionAction.ADD_TO_VAULT)
        } else base
    }
    val showFavoriteButton by rememberShowFavoriteButton()
    val trashEnabled = rememberTrashEnabled()

    AnimatedVisibility(
        modifier = modifier,
        visible = isSelectionActive,
        enter = slideInVertically { it * 2 },
        exit = slideOutVertically { it * 2 }
    ) {
        val allowBlur by rememberAllowBlur()
        val surfaceColor = MaterialTheme.colorScheme.surfaceColorAtElevation(2.dp)
        val backgroundModifier = remember(allowBlur) {
            if (!allowBlur) {
                Modifier.background(
                    color = surfaceColor,
                    shape = Shapes.extraLarge
                )
            } else {
                Modifier
            }
        }
        val shape = Shapes.extraLarge
        Column(
            modifier = Modifier
                .padding(horizontal = 32.dp)
                .navigationBarsPadding()
                .then(sizeModifier)
                .wrapContentHeight()
                .clip(shape)
                .padding(vertical = 16.dp)
                .align(Alignment.BottomEnd),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Top row — driven by config, horizontally scrollable with fade
            val topScrollState = rememberScrollState()
            val rightAligned = sanitizedConfig.topActionsRightAligned
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                    .drawWithContent {
                        drawContent()
                        val fadeWidth = 32.dp.toPx()
                        if (topScrollState.canScrollForward) {
                            drawRect(
                                brush = Brush.horizontalGradient(
                                    colors = listOf(Color.Black, Color.Transparent),
                                    startX = size.width - fadeWidth,
                                    endX = size.width
                                ),
                                blendMode = BlendMode.DstIn
                            )
                        }
                    }
            ) {
                if (rightAligned) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // CLOSE stays on the left
                        if (SelectionAction.CLOSE in sanitizedConfig.topActions) {
                            SelectionAddon(
                                onClick = {
                                    scope.launch {
                                        selector.clearSelection()
                                    }
                                },
                                imageVector = Icons.Outlined.Close,
                                contentDescription = stringResource(R.string.selection_dialog_close_cd),
                                text = selectedMedia.size.toString()
                            )
                        }
                        // Remaining actions pushed to the right, scrollable
                        Row(
                            modifier = Modifier
                                .weight(1f)
                                .horizontalScroll(topScrollState),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.End)
                        ) {
                            sanitizedConfig.topActions.forEach { action ->
                                if (action != SelectionAction.CLOSE) {
                                    when (action) {
                                        SelectionAction.SELECT_ALL -> {
                                            SelectAllAddon(allMedia = allMedia)
                                        }
                                        SelectionAction.INFO -> {
                                            AnimatedVisibility(visible = selectedMedia.size == 1) {
                                                SelectionAddon(
                                                    onClick = { showInfoSheet = true },
                                                    imageVector = Icons.Outlined.Info,
                                                    contentDescription = stringResource(R.string.media_details),
                                                    text = stringResource(R.string.media_details)
                                                )
                                            }
                                        }
                                        else -> {}
                                    }
                                }
                            }
                        }
                    }
                } else {
                    Row(
                        modifier = Modifier.horizontalScroll(topScrollState),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        sanitizedConfig.topActions.forEach { action ->
                            when (action) {
                                SelectionAction.CLOSE -> {
                                    SelectionAddon(
                                        onClick = {
                                            scope.launch {
                                                selector.clearSelection()
                                            }
                                        },
                                        imageVector = Icons.Outlined.Close,
                                        contentDescription = stringResource(R.string.selection_dialog_close_cd),
                                        text = selectedMedia.size.toString()
                                    )
                                }
                                SelectionAction.SELECT_ALL -> {
                                    SelectAllAddon(
                                        allMedia = allMedia
                                    )
                                }
                                SelectionAction.INFO -> {
                                    AnimatedVisibility(visible = selectedMedia.size == 1) {
                                        SelectionAddon(
                                            onClick = { showInfoSheet = true },
                                            imageVector = Icons.Outlined.Info,
                                            contentDescription = stringResource(R.string.media_details),
                                            text = stringResource(R.string.media_details)
                                        )
                                    }
                                }
                                else -> {}
                            }
                        }
                    }
                }
            }
            // Middle actions — full-width pill buttons
            sanitizedConfig.middleActions.forEach { action ->
                val isVisible = isActionVisible(action, collectionId, showFavoriteButton, isInVault)
                if (isVisible) {
                    when (action) {
                        SelectionAction.COLLECTION -> {
                            val inCollection = collectionId != null
                            MiddleActionButton(
                                icon = action.icon,
                                text = stringResource(
                                    if (inCollection) R.string.remove_from_collection
                                    else R.string.add_to_collection
                                ),
                                surfaceColor = surfaceColor,
                                onClick = {
                                    if (inCollection) {
                                        scope.launch {
                                            selectedMedia.forEach { media ->
                                                collectionViewModel.removeMediaFromCollection(collectionId, media.id)
                                            }
                                            selector.clearSelection()
                                        }
                                    } else {
                                        showCollectionSheet = true
                                    }
                                }
                            )
                        }
                        else -> {
                            MiddleActionButton(
                                icon = action.icon,
                                text = stringResource(action.labelRes),
                                surfaceColor = surfaceColor,
                                onClick = {}
                            )
                        }
                    }
                }
            }
            Row(
                modifier = Modifier
                    .then(sizeModifier)
                    .then(backgroundModifier)
                    .clip(shape)
                    .shadow(
                        elevation = 4.dp,
                        shape = shape
                    )
                    .hazeEffect(
                        state = LocalHazeState.current,
                        style = HazeMaterials.regular(
                            containerColor = surfaceColor
                        )
                    )
                    .horizontalScroll(rememberScrollState()),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                sanitizedConfig.bottomActions.forEach { action ->
                    val isVisible = isActionVisible(action, collectionId, showFavoriteButton, isInVault)
                    if (isVisible) {
                        when (action) {
                            SelectionAction.SHARE -> {
                                SelectionBarColumn(
                                    imageVector = action.icon,
                                    tabletMode = tabletMode,
                                    title = stringResource(action.labelRes)
                                ) {
                                    scope.launch {
                                        context.shareMediaWithVaultSupport(selectedMedia, currentVault = currentVault)
                                    }
                                }
                            }
                            SelectionAction.FAVORITE -> {
                                SelectionBarColumn(
                                    imageVector = action.icon,
                                    tabletMode = tabletMode,
                                    title = stringResource(action.labelRes)
                                ) {
                                    scope.launch {
                                        handler.toggleFavorite(result = result, selectedMedia)
                                    }
                                }
                            }
                            
                            SelectionAction.COPY -> {
                                SelectionBarColumn(
                                    imageVector = action.icon,
                                    tabletMode = tabletMode,
                                    title = stringResource(action.labelRes)
                                ) {
                                    if (isInVault) {
                                        vaultSheetAction = "copy"
                                        scope.launch { vaultSheetState.show() }
                                    } else {
                                        scope.launch { copySheetState.show() }
                                    }
                                }
                            }
                            SelectionAction.MOVE -> {
                                SelectionBarColumn(
                                    imageVector = action.icon,
                                    tabletMode = tabletMode,
                                    title = stringResource(action.labelRes)
                                ) {
                                    if (isInVault) {
                                        vaultSheetAction = "move"
                                        scope.launch { vaultSheetState.show() }
                                    } else {
                                        scope.launch { moveSheetState.show() }
                                    }
                                }
                            }
                            
                            SelectionAction.TRASH -> {
                                if (isInVault) {
                                    SelectionBarColumn(
                                        imageVector = action.icon,
                                        tabletMode = tabletMode,
                                        title = stringResource(R.string.trash_delete)
                                    ) {
                                        scope.launch {
                                            val vault = currentVault ?: vaultViewModel.currentVault.value ?: return@launch
                                            selectedMedia.filterIsInstance<Media.UriMedia>().forEach { media ->
                                                vaultViewModel.deleteMedia(vault, media) {}
                                            }
                                            selector.clearSelection()
                                        }
                                    }
                                } else {
                                    val trashEnabledRes = remember(trashEnabled) {
                                        if (trashEnabled.value) R.string.trash else R.string.trash_delete
                                    }
                                    SelectionBarColumn(
                                        imageVector = action.icon,
                                        tabletMode = tabletMode,
                                        title = stringResource(id = trashEnabledRes),
                                        onItemLongClick = {
                                            scope.launch {
                                                shouldMoveToTrash = false
                                                trashSheetState.show()
                                            }
                                        },
                                        onItemClick = {
                                            scope.launch {
                                                shouldMoveToTrash = true
                                                trashSheetState.show()
                                            }
                                        }
                                    )
                                }
                            }
                            SelectionAction.ADD_TO_VAULT -> {
                                SelectionBarColumn(
                                    imageVector = if (isInVault) Icons.Outlined.Restore else action.icon,
                                    tabletMode = tabletMode,
                                    title = stringResource(
                                        if (isInVault) R.string.restore else action.labelRes
                                    )
                                ) {
                                    if (isInVault) {
                                        scope.launch {
                                            val vault = currentVault ?: vaultViewModel.currentVault.value ?: return@launch
                                            selectedMedia.filterIsInstance<Media.UriMedia>().forEach { media ->
                                                vaultViewModel.restoreMedia(vault, media) {}
                                            }
                                            selector.clearSelection()
                                        }
                                    } else {
                                        vaultSheetAction = "hide"
                                        scope.launch { vaultSheetState.show() }
                                    }
                                }
                            }
                            SelectionAction.EDIT -> {
                                SelectionBarColumn(
                                    imageVector = action.icon,
                                    tabletMode = tabletMode,
                                    title = stringResource(action.labelRes)
                                ) {
                                    selectedMedia.firstOrNull()?.let { media ->
                                        context.launchEditIntent(media)
                                    }
                                }
                            }
                            SelectionAction.ROTATE -> {
                                SelectionBarColumn(
                                    imageVector = action.icon,
                                    tabletMode = tabletMode,
                                    title = stringResource(action.labelRes)
                                ) {
                                    selectedMedia.forEach { media ->
                                        handler.rotateImage(media, 90)
                                    }
                                    selector.clearSelection()
                                }
                            }
                            else -> {} // Top-zone actions don't appear in bottom bar
                        }
                    }
                }
            }
        }
    }

    SelectVaultSheet(
        state = vaultSheetState,
        vaultState = vaults.value,
        excludeVault = if (isInVault) vaultViewModel.currentVault.value else null,
        onVaultSelected = { targetVault ->
            scope.launch {
                when (vaultSheetAction) {
                    "copy", "move" -> {
                        val isCopy = vaultSheetAction == "copy"
                        val sourceVault = vaultViewModel.currentVault.value ?: return@launch
                        val mediaToTransfer = selectedMedia.filterIsInstance<Media.UriMedia>()
                        for (media in mediaToTransfer) {
                            vaultViewModel.transferMedia(sourceVault, targetVault, media, copy = isCopy)
                        }
                        // Switch to target vault so the user sees the result
                        vaultViewModel.currentVault.value = targetVault
                    }
                    else -> {
                        // Regular hide: encrypt into selected vault
                        when (vaultEncryptBehavior) {
                            Settings.Vault.ENCRYPT_DELETE -> {
                                Toast.makeText(context, context.getString(R.string.vault_hide_in_progress), Toast.LENGTH_SHORT).show()
                                vaultViewModel.encryptAndRequestDeletion(
                                    targetVault,
                                    selectedMedia.map { it.getUri() }
                                )
                            }
                            Settings.Vault.ENCRYPT_KEEP -> {
                                Toast.makeText(context, context.getString(R.string.vault_hide_in_progress), Toast.LENGTH_SHORT).show()
                                vaultViewModel.addMediaKeepOriginals(
                                    targetVault,
                                    selectedMedia.map { it.getUri() }
                                )
                            }
                            else -> {
                                hideTargetVault = targetVault
                                addToVaultSheetState.show()
                                return@launch // Don't clear selection yet
                            }
                        }
                    }
                }
                selector.clearSelection()
            }
        }
    )

    AddToVaultSheet(
        state = addToVaultSheetState,
        onEncryptAndDelete = {
            val vault = hideTargetVault ?: return@AddToVaultSheet
            Toast.makeText(context, context.getString(R.string.vault_hide_in_progress), Toast.LENGTH_SHORT).show()
            scope.launch {
                vaultViewModel.encryptAndRequestDeletion(vault, selectedMedia.map { it.getUri() })
                selector.clearSelection()
            }
        },
        onEncryptAndKeep = {
            val vault = hideTargetVault ?: return@AddToVaultSheet
            Toast.makeText(context, context.getString(R.string.vault_hide_in_progress), Toast.LENGTH_SHORT).show()
            scope.launch {
                vaultViewModel.addMediaKeepOriginals(vault, selectedMedia.map { it.getUri() })
                selector.clearSelection()
            }
        },
        onBehaviorChanged = { vaultEncryptBehavior = it }
    )

    val hideResult = rememberActivityResult(onResultOk = {})
    // Show user feedback (Toast) for hide operations
    LaunchedEffect(Unit) {
        vaultViewModel.userMessage.collect { message ->
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        }
    }
    // Collect deletion batches for permission request
    LaunchedEffect(Unit) {
        vaultViewModel.pendingDeletions.collect { leftovers ->
            if (leftovers.isNotEmpty()) {
                if (SdkCompat.supportsMediaStoreRequests) {
                    val intentSender = MediaStore.createDeleteRequest(
                        context.contentResolver,
                        leftovers
                    ).intentSender
                    val senderRequest = IntentSenderRequest.Builder(intentSender)
                        .setFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION, 0)
                        .build()
                    hideResult.launch(senderRequest)
                } else {
                    withContext(Dispatchers.IO) {
                        leftovers.forEach { uri ->
                            runCatching {
                                context.contentResolver.delete(uri, null, null)
                            }
                        }
                    }
                }
            }
        }
    }

    if (albumsState.value.albums.isNotEmpty()) {
        MoveMediaSheet(
            sheetState = moveSheetState,
            mediaList = selectedMedia,
            albumState = albumsState,
            onFinish = selector::clearSelection
        )

        CopyMediaSheet(
            sheetState = copySheetState,
            mediaList = selectedMedia,
            albumsState = albumsState,
            onFinish = selector::clearSelection
        )
    }

    TrashDialog(
        appBottomSheetState = trashSheetState,
        data = selectedMedia,
        action = remember(shouldMoveToTrash) {
            if (shouldMoveToTrash) TrashDialogAction.TRASH else TrashDialogAction.DELETE
        },
    ) {
        selector.clearSelection()
        if (shouldMoveToTrash && SdkCompat.supportsTrash) {
            handler.trashMedia(result, it, true)
        } else {
            handler.deleteMedia(result, it)
        }
    }

    AddToCollectionSheet(
        visible = showCollectionSheet,
        collections = albumsState.value.collections,
        onDismiss = { showCollectionSheet = false },
        onCollectionSelected = { collectionId ->
            collectionViewModel.addMediaListToCollection(
                collectionId,
                selectedMedia.map { it.id }
            )
            selector.clearSelection()
        },
        onCreateAndAdd = { name ->
            collectionViewModel.createCollectionAndAddMedia(
                name,
                selectedMedia.map { it.id }
            )
            selector.clearSelection()
        }
    )

    // Info bottom sheet — shows media details for a single selected item
    if (showInfoSheet && selectedMedia.size == 1) {
        val media = selectedMedia.first()
        val metadata = remember(metadataState.value, media) {
            metadataState.value.metadataMap[media.id]
        }
        val mediaInfoList = rememberMediaInfo(
            media = media,
            exifMetadata = metadata,
            onLabelClick = {}
        )
        ModalBottomSheet(
            onDismissRequest = { showInfoSheet = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .padding(bottom = 32.dp)
            ) {
                Text(
                    text = stringResource(R.string.media_details),
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
                mediaInfoList.forEach { info ->
                    MediaInfoRow(
                        label = info.label,
                        content = info.content,
                        icon = info.icon,
                        onClick = info.onClick,
                        onLongClick = info.onLongClick
                    )
                }
            }
        }
    }
}

private fun isActionVisible(
    action: SelectionAction,
    collectionId: Long?,
    showFavoriteButton: Boolean,
    isInVault: Boolean = false,
): Boolean {
    if (isInVault) {
        // In vault: only allow close, select_all, share, trash (delete), restore (ADD_TO_VAULT)
        return action in setOf(
            SelectionAction.CLOSE,
            SelectionAction.SELECT_ALL,
            SelectionAction.SHARE,
            SelectionAction.TRASH,
            SelectionAction.ADD_TO_VAULT,
        )
    }
    return when (action.requiresCondition) {
        ActionCondition.NONE -> true
        ActionCondition.SUPPORTS_FAVORITES -> showFavoriteButton && SdkCompat.supportsFavorites
        ActionCondition.IN_COLLECTION -> collectionId != null
    }
}

@Composable
fun <T: Media> SelectAllAddon(
    allMedia: MediaState<T>,
) {
    val scope = rememberCoroutineScope()
    val selector = LocalMediaSelector.current
    val selectedMedia by selector.selectedMedia.collectAsStateWithLifecycle()
    val selectedAll by rememberedDerivedState(selectedMedia, allMedia) {
        selectedMedia.size == allMedia.media.size && allMedia.media.isNotEmpty()
    }
    val selectAllText = if (selectedAll) {
        stringResource(R.string.clear_selection)
    } else {
        stringResource(R.string.select_all)
    }
    val selectAllContentDesc = if (selectedAll) {
        stringResource(R.string.clear_selection)
    } else {
        stringResource(R.string.select_all)
    }

    val selectAllIcon = if (selectedAll) {
        Icons.Outlined.Deselect
    } else {
        Icons.Outlined.SelectAll
    }

    val selectAllContainerColor by animateColorAsState(
        targetValue = if (selectedAll) MaterialTheme.colorScheme.primaryContainer
        else MaterialTheme.colorScheme.surfaceColorAtElevation(2.dp)
    )
    val selectAllContentColor by animateColorAsState(
        targetValue = if (selectedAll) MaterialTheme.colorScheme.onPrimaryContainer
        else MaterialTheme.colorScheme.onSurface
    )

    SelectionAddon(
        onClick = {
            scope.launch {
                if (selectedAll) selector.clearSelection()
                else selector.addToSelection(allMedia.media.map { it.id })
            }
        },
        text = selectAllText,
        imageVector = selectAllIcon,
        contentDescription = selectAllContentDesc,
        contentColor = selectAllContentColor,
        containerColor = selectAllContainerColor
    )
}

@OptIn(ExperimentalHazeMaterialsApi::class)
@Composable
fun SelectionAddon(
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    imageVector: ImageVector,
    contentDescription: String,
    contentColor: Color = MaterialTheme.colorScheme.onSurface,
    containerColor: Color = MaterialTheme.colorScheme.surfaceColorAtElevation(2.dp),
    text: String? = null,
) {
    val allowBlur by rememberAllowBlur()
    val backgroundModifier = remember(allowBlur) {
        if (!allowBlur) {
            Modifier.background(
                color = containerColor,
                shape = Shapes.extraLarge
            )
        } else {
            Modifier
        }
    }
    val shape = Shapes.extraLarge
    Row(
        modifier = modifier
            .then(backgroundModifier)
            .clip(shape)
            .shadow(
                elevation = 4.dp,
                shape = shape
            )
            .hazeEffect(
                state = LocalHazeState.current,
                style = HazeMaterials.thin(
                    containerColor = containerColor
                )
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Image(
            modifier = Modifier.size(24.dp),
            imageVector = imageVector,
            colorFilter = ColorFilter.tint(contentColor),
            contentDescription = contentDescription
        )
        if (text != null) {
            Text(
                text = text,
                style = MaterialTheme.typography.titleMedium,
                color = contentColor,
                maxLines = 1,
                softWrap = false,
            )
        }
    }
}

@OptIn(ExperimentalHazeMaterialsApi::class)
@Composable
private fun MiddleActionButton(
    icon: ImageVector,
    text: String,
    surfaceColor: Color,
    onClick: () -> Unit,
) {
    val allowBlur by rememberAllowBlur()
    val shape = Shapes.extraLarge
    val backgroundModifier = remember(allowBlur) {
        if (!allowBlur) {
            Modifier.background(
                color = surfaceColor,
                shape = shape
            )
        } else {
            Modifier
        }
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(backgroundModifier)
            .clip(shape)
            .shadow(
                elevation = 4.dp,
                shape = shape
            )
            .hazeEffect(
                state = LocalHazeState.current,
                style = HazeMaterials.regular(
                    containerColor = surfaceColor
                )
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        Image(
            modifier = Modifier.size(24.dp),
            imageVector = icon,
            colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.onSurface),
            contentDescription = text
        )
        Spacer(modifier = Modifier.size(12.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
        )
    }
}

@Composable
private fun RowScope.SelectionBarColumn(
    imageVector: ImageVector,
    title: String,
    tabletMode: Boolean,
    onItemLongClick: (() -> Unit)? = null,
    onItemClick: () -> Unit,
) {
    val showTitles by rememberShowSelectionTitles()
    val tintColor = MaterialTheme.colorScheme.onSurface
    val minHeightSizeModifier = remember(showTitles) {
        if (showTitles) Modifier.defaultMinSize(minHeight = 80.dp)
        else Modifier.defaultMinSize(minHeight = 64.dp)
    }
    val minWidthSizeModifier = remember(tabletMode) {
        if (showTitles) {
            if (tabletMode) Modifier.defaultMinSize(minWidth = 80.dp)
            else Modifier.weight(1f)
        } else {
            if (tabletMode) Modifier.defaultMinSize(minWidth = 64.dp)
            else Modifier.weight(1f)
        }
    }
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .then(minHeightSizeModifier)
            .then(minWidthSizeModifier)
            .combinedClickable(
                onClick = onItemClick,
                onLongClick = onItemLongClick
            )
            .padding(top = 12.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Image(
            imageVector = imageVector,
            colorFilter = ColorFilter.tint(tintColor),
            contentDescription = title,
            modifier = Modifier
                .height(32.dp)
        )
        if (showTitles) {
            Spacer(modifier = Modifier.size(4.dp))
            Text(
                text = title,
                modifier = Modifier,
                fontWeight = FontWeight.Medium,
                style = MaterialTheme.typography.bodyMedium,
                color = tintColor,
                textAlign = TextAlign.Center
            )
        }
    }
}