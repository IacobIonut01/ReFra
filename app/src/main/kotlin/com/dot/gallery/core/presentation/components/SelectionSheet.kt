/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.core.presentation.components

import android.app.Activity
import android.content.Intent
import android.net.Uri
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
import androidx.compose.material.icons.outlined.DeleteForever
import androidx.compose.material.icons.outlined.Deselect
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Restore
import androidx.compose.material.icons.outlined.SelectAll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.SheetValue
import androidx.compose.material3.rememberBottomSheetState
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
import com.dot.gallery.core.PendingRemovalScope
import com.dot.gallery.core.LocalMediaSelector
import com.dot.gallery.core.Settings
import com.dot.gallery.core.Settings.Misc.rememberAllowBlur
import com.dot.gallery.core.Settings.Misc.rememberSelectionSheetConfig
import com.dot.gallery.core.Settings.Misc.rememberShowFavoriteButton
import com.dot.gallery.core.Settings.Misc.rememberShowSelectionTitles
import com.dot.gallery.core.Settings.Misc.rememberTrashEnabled
import com.dot.gallery.core.util.SdkCompat
import com.dot.gallery.core.decoder.format.ImageReencoder
import com.dot.gallery.cloud.core.CloudRuntimeSettings
import com.dot.gallery.cloud.ui.CloudSelectionViewModel
import com.dot.gallery.feature_node.domain.model.ActionCondition
import com.dot.gallery.feature_node.domain.model.Media
import com.dot.gallery.feature_node.domain.repository.MediaMutationResult
import com.dot.gallery.feature_node.domain.model.MediaMetadataState
import com.dot.gallery.feature_node.domain.model.MediaState
import com.dot.gallery.feature_node.domain.model.SelectionAction
import com.dot.gallery.feature_node.domain.model.Vault
import com.dot.gallery.feature_node.domain.util.getUri
import com.dot.gallery.feature_node.domain.util.isCloud
import com.dot.gallery.feature_node.presentation.collection.CollectionViewModel
import com.dot.gallery.feature_node.presentation.collection.components.AddToCollectionSheet
import com.dot.gallery.feature_node.presentation.exif.CopyMediaSheet
import com.dot.gallery.feature_node.presentation.exif.MoveMediaSheet
import com.dot.gallery.feature_node.presentation.mediaview.MediaActionCapabilityPolicy
import com.dot.gallery.feature_node.presentation.mediaview.MediaActionPolicyInput
import com.dot.gallery.feature_node.presentation.mediaview.components.MediaInfoRow
import com.dot.gallery.feature_node.presentation.mediaview.isReadOnlyCloudMedia
import com.dot.gallery.feature_node.presentation.mediaview.rememberedDerivedState
import com.dot.gallery.feature_node.presentation.privatefolder.PrivateFolderMoveViewModel
import com.dot.gallery.feature_node.presentation.trashed.components.TrashDialog
import com.dot.gallery.feature_node.presentation.trashed.components.TrashDialogAction
import com.dot.gallery.feature_node.presentation.trashed.components.resolveTrashDialogAction
import com.dot.gallery.feature_node.presentation.util.LocalHazeState
import com.dot.gallery.feature_node.presentation.util.launchEditIntent
import com.dot.gallery.feature_node.presentation.util.rememberActivityResult
import com.dot.gallery.feature_node.presentation.util.rememberAppBottomSheetState
import com.dot.gallery.feature_node.presentation.util.rememberMediaInfo
import com.dot.gallery.feature_node.presentation.util.shareMediaWithVaultSupport
import com.dot.gallery.feature_node.presentation.vault.VaultViewModel
import com.dot.gallery.feature_node.presentation.vault.components.AddToVaultSheet
import com.dot.gallery.feature_node.presentation.vault.components.ConfirmationSheet
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
    isInPrivateFolder: Boolean = false,
    currentVault: Vault? = null,
    extraBottomActions: (@Composable RowScope.() -> Unit)? = null,
) {
    val albumsState = LocalMediaDistributor.current.albumsFlow.collectAsStateWithLifecycle()
    val selector = LocalMediaSelector.current
    val isSelectionActive by selector.isSelectionActive.collectAsStateWithLifecycle()

    val handler = LocalMediaHandler.current
    val distributor = LocalMediaDistributor.current
    val context = LocalContext.current
    val downloadingText = stringResource(R.string.downloading)
    val downloadCompleteText = stringResource(R.string.download_complete)
    val downloadFailedText = stringResource(R.string.download_failed)
    val noLocalItemsText = stringResource(R.string.vault_hide_no_local_items)
    val hideInProgressText = stringResource(R.string.vault_hide_in_progress)
    val scope = rememberCoroutineScope()
    // Non-encodable selected items pending a rotate → offered as PNG copies via the fallback sheet.
    var rotateFallbackList by remember { mutableStateOf<List<T>>(emptyList()) }
    if (rotateFallbackList.isNotEmpty()) {
        OverwriteFallbackSheet(
            onCreateCopy = {
                val pending = rotateFallbackList
                rotateFallbackList = emptyList()
                pending.forEach { media -> handler.rotateImage(media, 90, true) }
                selector.clearSelection()
            },
            onDismiss = {
                rotateFallbackList = emptyList()
                selector.clearSelection()
            }
        )
    }
    var shouldMoveToTrash by rememberSaveable { mutableStateOf(true) }
    val trashSheetState = rememberAppBottomSheetState()
    val moveSheetState = rememberAppBottomSheetState()
    val copySheetState = rememberAppBottomSheetState()
    var showCollectionSheet by rememberSaveable { mutableStateOf(false) }
    val collectionViewModel = hiltViewModel<CollectionViewModel>()
    val vaultViewModel = hiltViewModel<VaultViewModel>()
    val vaultSheetState = rememberAppBottomSheetState()
    val vaultDeleteConfirmState = rememberAppBottomSheetState()
    val vaultRestoreConfirmState = rememberAppBottomSheetState()
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
    // The fullest, unfiltered media source. The display list passed in
    // (allMedia/selectedMedia) can transiently lose items (e.g. cloud media
    // re-emitting), which would orphan an active selection and silently no-op
    // the hide. Resolving from the raw selector ids against this broader source
    // keeps local selections intact regardless of what the screen currently shows.
    val timelineMediaState = LocalMediaDistributor.current.timelineMediaFlow.collectAsStateWithLifecycle(
        initialValue = MediaState<Media.UriMedia>()
    )
    // Resolve the currently selected ids to hideable (non-cloud) URIs, using the
    // fullest available media so a changed display list can't drop the target.
    // Cloud items are skipped because their remote URIs can't be vault-encrypted.
    val resolveSelectedHideUris: () -> List<Uri> = resolve@{
        val ids = selector.selectedMedia.value
        if (ids.isEmpty()) return@resolve emptyList()
        val byId = HashMap<Long, Media>(ids.size)
        selectedMedia.forEach { if (it.id in ids) byId.putIfAbsent(it.id, it) }
        allMedia.media.forEach { if (it.id in ids) byId.putIfAbsent(it.id, it) }
        timelineMediaState.value.media.forEach { if (it.id in ids) byId.putIfAbsent(it.id, it) }
        ids.mapNotNull { byId[it] }
            .filterNot { it.isCloud }
            .map { it.getUri() }
    }
    // Ids whose favorite toggle is waiting on the system favorite request; the overrides
    // applied for instant feedback are rolled back if the request is canceled.
    var pendingFavoriteIds by remember { mutableStateOf<Set<Long>>(emptySet()) }
    val result = rememberActivityResult(
        onResultCanceled = {
            pendingFavoriteIds.forEach { distributor.clearFavoriteOverride(it) }
            pendingFavoriteIds = emptySet()
        },
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
    // Ids of the media the in-flight system-deletion request targets, stashed so the OK
    // branch can mark them pending removal. `null` means no deletion request is in flight.
    var pendingDeletionIds by remember { mutableStateOf<Set<Long>?>(null) }
    // Scope of the in-flight deletion request — EVERYWHERE for permanent delete,
    // NON_TRASH for trash (the item is arriving in the trash view),
    // TRASH_ONLY for restore out of trash.
    var pendingDeletionScope by remember {
        mutableStateOf(PendingRemovalScope.EVERYWHERE)
    }
    val deletionResult = rememberActivityResult(
        onResultCanceled = { pendingDeletionIds = null },
        onResultOk = {
            pendingDeletionIds?.let { distributor.markPendingRemoval(it, pendingDeletionScope) }
            pendingDeletionIds = null
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
    val privateFolderMoveViewModel = hiltViewModel<PrivateFolderMoveViewModel>()
    val privateFolderMoveConfirmState = rememberAppBottomSheetState()
    // Move-out and permanent-delete confirmations used only inside the private
    // folder, where items are SAF documents (see #1015).
    val privateFolderMoveOutConfirmState = rememberAppBottomSheetState()
    val privateFolderDeleteConfirmState = rememberAppBottomSheetState()
    val privateFolderUri by Settings.Security.rememberPrivateFolderUri()
    val privateFolderConfigured = privateFolderUri.isNotEmpty()
    val config by rememberSelectionSheetConfig()
    val sanitizedConfig = remember(config, isInVault, isInPrivateFolder, privateFolderConfigured) {
        var base = config.sanitized()
        // Inside the private folder the items are SAF documents, not MediaStore
        // entries, so MediaStore-based actions (copy/move/favorite/trash) crash
        // (#1015). Force a safe, SAF-only action set: share, move-out (reusing
        // MOVE) and permanent delete (reusing TRASH). Never offer
        // MOVE_TO_PRIVATE_FOLDER here (would copy back in + MediaStore-delete
        // crash).
        if (isInPrivateFolder) {
            return@remember base.copy(
                topActions = listOf(
                    SelectionAction.CLOSE,
                    SelectionAction.SELECT_ALL,
                    SelectionAction.INFO,
                ),
                middleActions = emptyList(),
                bottomActions = listOf(
                    SelectionAction.SHARE,
                    SelectionAction.MOVE,
                    SelectionAction.TRASH,
                ),
            )
        }
        if (isInVault && SelectionAction.ADD_TO_VAULT !in base.bottomActions) {
            base = base.copy(bottomActions = base.bottomActions + SelectionAction.ADD_TO_VAULT)
        }
        // Drop the private-folder move action if a private folder isn't
        // configured, but otherwise respect the user's saved config (don't
        // force it in).
        if (!privateFolderConfigured &&
            SelectionAction.MOVE_TO_PRIVATE_FOLDER in base.bottomActions
        ) {
            base = base.copy(
                bottomActions = base.bottomActions - SelectionAction.MOVE_TO_PRIVATE_FOLDER
            )
        }
        base
    }
    val showFavoriteButton by rememberShowFavoriteButton()
    val trashEnabled by rememberTrashEnabled()
    val effectiveTrashAction = resolveTrashDialogAction(
        trashRequested = shouldMoveToTrash,
        trashEnabled = trashEnabled,
        trashSupported = SdkCompat.supportsTrash
    )

    // Cloud/remote selections use provider-aware source mutations plus copy/move transfers.
    // All provider resolution and capability logic lives in CloudSelectionViewModel; the sheet
    // only reads the resolved flags to decide which buttons to render.
    val cloudSelectionViewModel = hiltViewModel<CloudSelectionViewModel>()
    val cloudTrashConfirmState = rememberAppBottomSheetState()
    val cloudDeleteConfirmState = rememberAppBottomSheetState()
    val cloudDeleteFailedText = stringResource(R.string.cloud_media_delete_failed)
    val selectedSnapshot = selectedMedia.toList()
    val isCloudSelection = cloudSelectionViewModel.isCloudSelection(selectedSnapshot)
    val isMixedCloudSelection = selectedSnapshot.any { it.isCloud } && !isCloudSelection
    val cloudSupportsFavorite = isCloudSelection && cloudSelectionViewModel.supportsFavorite(selectedSnapshot)
    val cloudSupportsTrash = isCloudSelection && cloudSelectionViewModel.supportsTrash(selectedSnapshot)
    val cloudSettingsByConfigId by CloudRuntimeSettings.settingsByConfigId.collectAsStateWithLifecycle()
    val cloudSelectionReadOnly = isCloudSelection && selectedSnapshot.any {
        it.isReadOnlyCloudMedia(cloudSettingsByConfigId)
    }
    val cloudActionCapabilities = MediaActionCapabilityPolicy.resolve(
        MediaActionPolicyInput(
            isCloud = isCloudSelection,
            cloudReadOnly = cloudSelectionReadOnly,
            sourceAllowsDelete = isCloudSelection,
            providerSupportsFavorite = cloudSupportsFavorite,
            providerSupportsTrash = cloudSupportsTrash,
        )
    )

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
                extraBottomActions?.invoke(this)
                if (isCloudSelection) {
                    if (albumsState.value.albums.isNotEmpty() && cloudActionCapabilities.copyOrMove) {
                        SelectionBarColumn(
                            imageVector = SelectionAction.COPY.icon,
                            title = stringResource(SelectionAction.COPY.labelRes)
                        ) {
                            scope.launch { copySheetState.show() }
                        }
                        if (!cloudSelectionReadOnly) {
                            SelectionBarColumn(
                                imageVector = SelectionAction.MOVE.icon,
                                title = stringResource(SelectionAction.MOVE.labelRes)
                            ) {
                                scope.launch { moveSheetState.show() }
                            }
                        }
                    }
                    // Share — resolveShareableUri downloads/caches the full-size original,
                    // then shares it via a FileProvider content URI.
                    if (cloudActionCapabilities.share) {
                        SelectionBarColumn(
                            imageVector = SelectionAction.SHARE.icon,
                            title = stringResource(SelectionAction.SHARE.labelRes)
                        ) {
                            scope.launch {
                                context.shareMediaWithVaultSupport(selectedMedia)
                            }
                        }
                    }
                    // Favorite — only when every selected item's provider supports server favorites.
                    if (cloudActionCapabilities.favorite && showFavoriteButton) {
                        SelectionBarColumn(
                            imageVector = SelectionAction.FAVORITE.icon,
                            title = stringResource(SelectionAction.FAVORITE.labelRes)
                        ) {
                            scope.launch {
                                val targets = selectedMedia.toList()
                                targets.forEach {
                                    distributor.setFavoriteOverride(it.id, it.favorite == 0)
                                }
                                cloudSelectionViewModel.toggleFavorite(result, targets)
                            }
                        }
                    }
                    // Download to device.
                    run {
                        val downloadingText = context.getString(R.string.downloading)
                        val completeText = context.getString(R.string.download_complete)
                        val failedText = context.getString(R.string.download_failed)
                        SelectionBarColumn(
                            imageVector = SelectionAction.DOWNLOAD.icon,
                            title = stringResource(SelectionAction.DOWNLOAD.labelRes)
                        ) {
                            scope.launch {
                                Toast.makeText(context, downloadingText, Toast.LENGTH_SHORT).show()
                                val dl = cloudSelectionViewModel.download(selectedMedia)
                                val count = dl.getOrDefault(0)
                                Toast.makeText(
                                    context,
                                    if (count > 0) completeText else failedText,
                                    Toast.LENGTH_SHORT
                                ).show()
                                selector.clearSelection()
                            }
                        }
                    }
                    // Trash — recoverable, only when the provider has a real bin (e.g. Immich).
                    if (cloudActionCapabilities.trash && cloudSupportsTrash) {
                        SelectionBarColumn(
                            imageVector = SelectionAction.TRASH.icon,
                            title = stringResource(R.string.trash)
                        ) {
                            scope.launch { cloudTrashConfirmState.show() }
                        }
                    }
                    // Delete — permanent hard delete on the server, unless cloud read-only is active.
                    if (cloudActionCapabilities.trash) {
                        SelectionBarColumn(
                            imageVector = Icons.Outlined.DeleteForever,
                            title = stringResource(R.string.cloud_delete)
                        ) {
                            scope.launch { cloudDeleteConfirmState.show() }
                        }
                    }
                } else sanitizedConfig.bottomActions.forEach { action ->
                    val isVisible = isActionVisible(action, collectionId, showFavoriteButton, isInVault)
                    if (isVisible) {
                        when (action) {
                            SelectionAction.SHARE -> {
                                SelectionBarColumn(
                                    imageVector = action.icon,
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
                                    title = stringResource(action.labelRes)
                                ) {
                                    scope.launch {
                                        val targets = selectedMedia.toList()
                                        // Instant feedback: pin the toggled state over the
                                        // flows until MediaStore/the provider lands it.
                                        targets.forEach {
                                            distributor.setFavoriteOverride(
                                                it.id,
                                                it.favorite == 0
                                            )
                                        }
                                        pendingFavoriteIds =
                                            targets.mapTo(HashSet()) { it.id }
                                        handler.toggleFavorite(result = result, targets)
                                    }
                                }
                            }
                            
                            SelectionAction.COPY -> {
                                SelectionBarColumn(
                                    imageVector = action.icon,
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
                                    title = stringResource(
                                        if (isInPrivateFolder) R.string.private_folder_move_out
                                        else action.labelRes
                                    )
                                ) {
                                    if (isInPrivateFolder) {
                                        scope.launch { privateFolderMoveOutConfirmState.show() }
                                    } else if (isInVault) {
                                        vaultSheetAction = "move"
                                        scope.launch { vaultSheetState.show() }
                                    } else {
                                        scope.launch { moveSheetState.show() }
                                    }
                                }
                            }
                            
                            SelectionAction.TRASH -> {
                                if (isMixedCloudSelection) {
                                } else if (isInPrivateFolder) {
                                    SelectionBarColumn(
                                        imageVector = action.icon,
                                        title = stringResource(R.string.action_delete_permanently)
                                    ) {
                                        scope.launch { privateFolderDeleteConfirmState.show() }
                                    }
                                } else if (isInVault) {
                                    SelectionBarColumn(
                                        imageVector = action.icon,
                                        title = stringResource(R.string.action_delete_permanently)
                                    ) {
                                        scope.launch { vaultDeleteConfirmState.show() }
                                    }
                                } else {
                                    val trashEnabledRes = if (trashEnabled && SdkCompat.supportsTrash) {
                                        R.string.trash
                                    } else {
                                        R.string.action_delete_permanently
                                    }
                                    SelectionBarColumn(
                                        imageVector = action.icon,
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
                                    title = stringResource(
                                        if (isInVault) R.string.restore else action.labelRes
                                    )
                                ) {
                                    if (isInVault) {
                                        scope.launch { vaultRestoreConfirmState.show() }
                                    } else {
                                        vaultSheetAction = "hide"
                                        scope.launch { vaultSheetState.show() }
                                    }
                                }
                            }
                            SelectionAction.MOVE_TO_PRIVATE_FOLDER -> {
                                if (privateFolderConfigured) {
                                    SelectionBarColumn(
                                        imageVector = action.icon,
                                        title = stringResource(action.labelRes)
                                    ) {
                                        scope.launch { privateFolderMoveConfirmState.show() }
                                    }
                                }
                            }
                            SelectionAction.EDIT -> {
                                SelectionBarColumn(
                                    imageVector = action.icon,
                                    title = stringResource(action.labelRes)
                                ) {
                                    selectedMedia.firstOrNull()?.let { media ->
                                        scope.launch { context.launchEditIntent(media) }
                                    }
                                }
                            }
                            SelectionAction.ROTATE -> {
                                SelectionBarColumn(
                                    imageVector = action.icon,
                                    title = stringResource(action.labelRes)
                                ) {
                                    val (encodable, nonEncodable) = selectedMedia.partition {
                                        ImageReencoder.isReencodable(it.mimeType, it.label)
                                    }
                                    encodable.forEach { media ->
                                        handler.rotateImage(media, 90, false)
                                    }
                                    if (nonEncodable.isNotEmpty()) {
                                        // Ask before rotating formats that can't overwrite in place.
                                        rotateFallbackList = nonEncodable
                                    } else {
                                        selector.clearSelection()
                                    }
                                }
                            }
                            SelectionAction.DOWNLOAD -> {
                                val cloudItems = selectedMedia.filter { it.isCloud }
                                if (cloudItems.isNotEmpty()) {
                                    SelectionBarColumn(
                                        imageVector = action.icon,
                                        title = stringResource(action.labelRes)
                                    ) {
                                        scope.launch {
                                            Toast.makeText(context, downloadingText, Toast.LENGTH_SHORT).show()
                                            val result = handler.downloadCloudMedia(cloudItems)
                                            val count = result.getOrDefault(0)
                                            if (count > 0) {
                                                Toast.makeText(context, downloadCompleteText, Toast.LENGTH_SHORT).show()
                                            } else {
                                                Toast.makeText(context, downloadFailedText, Toast.LENGTH_SHORT).show()
                                            }
                                            selector.clearSelection()
                                        }
                                    }
                                }
                            }
                            SelectionAction.SET_AS_ALBUM_COVER -> {
                                val single = selectedMedia.singleOrNull()
                                // Local, real-album items only; the cloud branch above already
                                // handles full-cloud selections.
                                if (single != null && !single.isCloud && single.albumID > 0 &&
                                    !isInVault && !isInPrivateFolder
                                ) {
                                    val coverSetText = stringResource(R.string.album_cover_updated)
                                    SelectionBarColumn(
                                        imageVector = action.icon,
                                        title = stringResource(action.labelRes)
                                    ) {
                                        scope.launch {
                                            handler.updateAlbumThumbnail(single.albumID, single.getUri())
                                            Toast.makeText(context, coverSetText, Toast.LENGTH_SHORT).show()
                                            selector.clearSelection()
                                        }
                                    }
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
        // Inside a vault the gate was already passed to enter this session; the
        // sheet then only serves copy/move between vaults.
        requireGateAuth = !isInVault,
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
                        if (vaultEncryptBehavior == Settings.Vault.ENCRYPT_DELETE ||
                            vaultEncryptBehavior == Settings.Vault.ENCRYPT_KEEP
                        ) {
                            val urisToHide = resolveSelectedHideUris()
                            if (urisToHide.isEmpty()) {
                                Toast.makeText(context, noLocalItemsText, Toast.LENGTH_SHORT).show()
                                return@launch
                            }
                            Toast.makeText(context, hideInProgressText, Toast.LENGTH_SHORT).show()
                            if (vaultEncryptBehavior == Settings.Vault.ENCRYPT_DELETE) {
                                vaultViewModel.encryptAndRequestDeletion(targetVault, urisToHide)
                            } else {
                                vaultViewModel.addMediaKeepOriginals(targetVault, urisToHide)
                            }
                        } else {
                            hideTargetVault = targetVault
                            addToVaultSheetState.show()
                            return@launch // Don't clear selection yet
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
            val urisToHide = resolveSelectedHideUris()
            if (urisToHide.isEmpty()) {
                Toast.makeText(context, noLocalItemsText, Toast.LENGTH_SHORT).show()
                return@AddToVaultSheet
            }
            Toast.makeText(context, hideInProgressText, Toast.LENGTH_SHORT).show()
            scope.launch {
                vaultViewModel.encryptAndRequestDeletion(vault, urisToHide)
                selector.clearSelection()
            }
        },
        onEncryptAndKeep = {
            val vault = hideTargetVault ?: return@AddToVaultSheet
            val urisToHide = resolveSelectedHideUris()
            if (urisToHide.isEmpty()) {
                Toast.makeText(context, noLocalItemsText, Toast.LENGTH_SHORT).show()
                return@AddToVaultSheet
            }
            Toast.makeText(context, hideInProgressText, Toast.LENGTH_SHORT).show()
            scope.launch {
                vaultViewModel.addMediaKeepOriginals(vault, urisToHide)
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
        action = effectiveTrashAction,
    ) {
        // A trashed item leaves the regular views but is arriving in the trash view; a
        // permanent delete leaves every view.
        val removalScope = if (effectiveTrashAction == TrashDialogAction.TRASH) {
            PendingRemovalScope.NON_TRASH
        } else {
            PendingRemovalScope.EVERYWHERE
        }
        val mutationResult = if (effectiveTrashAction == TrashDialogAction.TRASH) {
            handler.trashMedia(deletionResult, it, true)
        } else {
            handler.deleteMedia(deletionResult, it)
        }
        when (mutationResult) {
            MediaMutationResult.COMPLETED -> {
                distributor.markPendingRemoval(it.map { m -> m.id }, removalScope)
                selector.clearSelection()
            }
            MediaMutationResult.REQUEST_LAUNCHED -> {
                pendingDeletionIds = it.mapTo(HashSet()) { m -> m.id }
                pendingDeletionScope = removalScope
            }
            MediaMutationResult.FAILED -> Unit
        }
    }

    ConfirmationSheet(
        state = vaultDeleteConfirmState,
        title = stringResource(R.string.vault_confirm_delete_items_title),
        summary = stringResource(R.string.vault_confirm_delete_items_summary),
        onConfirm = {
            scope.launch {
                val vault = currentVault ?: vaultViewModel.currentVault.value ?: return@launch
                selectedMedia.filterIsInstance<Media.UriMedia>().forEach { media ->
                    vaultViewModel.deleteMedia(vault, media) {}
                }
                selector.clearSelection()
            }
        }
    )

    ConfirmationSheet(
        state = vaultRestoreConfirmState,
        title = stringResource(R.string.vault_confirm_restore_title),
        summary = stringResource(R.string.vault_confirm_restore_summary),
        onConfirm = {
            scope.launch {
                val vault = currentVault ?: vaultViewModel.currentVault.value ?: return@launch
                selectedMedia.filterIsInstance<Media.UriMedia>().forEach { media ->
                    vaultViewModel.restoreMedia(vault, media) {}
                }
                selector.clearSelection()
            }
        }
    )

    // Move selected local media into the private folder, then request deletion
    // of the originals so the operation behaves as a true move (#1015).
    val privateFolderMovingText = stringResource(R.string.private_folder_moving)
    val privateFolderMoveFailedText = stringResource(R.string.private_folder_move_failed)
    ConfirmationSheet(
        state = privateFolderMoveConfirmState,
        title = stringResource(R.string.private_folder_move_confirm_title),
        summary = stringResource(R.string.private_folder_move_confirm_summary),
        onConfirm = {
            val toMove = selectedMedia.filterNot { it.isCloud }.toList()
            if (toMove.isNotEmpty()) {
                scope.launch {
                    Toast.makeText(context, privateFolderMovingText, Toast.LENGTH_SHORT).show()
                    val moved = privateFolderMoveViewModel.copyIntoPrivateFolder(toMove)
                    if (moved.isNotEmpty()) {
                        when (handler.deleteMedia(deletionResult, moved)) {
                            MediaMutationResult.COMPLETED -> {
                                distributor.markPendingRemoval(
                                    moved.map { it.id },
                                    PendingRemovalScope.EVERYWHERE
                                )
                                selector.clearSelection()
                            }
                            MediaMutationResult.REQUEST_LAUNCHED -> {
                                pendingDeletionIds = moved.mapTo(HashSet()) { it.id }
                                pendingDeletionScope = PendingRemovalScope.EVERYWHERE
                            }
                            MediaMutationResult.FAILED -> Unit
                        }
                    } else {
                        Toast.makeText(context, privateFolderMoveFailedText, Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    )

    // Move selected private-folder items back out to the public gallery. These
    // are SAF documents, so the copy-out + SAF delete happen entirely in the
    // repository — no MediaStore delete request (which crashes here — #1015).
    val privateFolderMovingOutText = stringResource(R.string.private_folder_moving_out)
    val privateFolderMoveOutFailedText = stringResource(R.string.private_folder_move_out_failed)
    ConfirmationSheet(
        state = privateFolderMoveOutConfirmState,
        title = stringResource(R.string.private_folder_move_out_confirm_title),
        summary = stringResource(R.string.private_folder_move_out_confirm_summary),
        onConfirm = {
            val toMove = selectedMedia.toList()
            if (toMove.isNotEmpty()) {
                scope.launch {
                    Toast.makeText(context, privateFolderMovingOutText, Toast.LENGTH_SHORT).show()
                    val moved = privateFolderMoveViewModel.moveOutOfPrivateFolder(toMove)
                    if (moved == 0) {
                        Toast.makeText(context, privateFolderMoveOutFailedText, Toast.LENGTH_SHORT).show()
                    }
                    selector.clearSelection()
                }
            }
        }
    )

    // Permanently delete selected private-folder items via SAF document
    // deletion (never a MediaStore delete request — #1015).
    val privateFolderDeleteFailedText = stringResource(R.string.private_folder_delete_failed)
    ConfirmationSheet(
        state = privateFolderDeleteConfirmState,
        title = stringResource(R.string.private_folder_delete_confirm_title),
        summary = stringResource(R.string.private_folder_delete_confirm_summary),
        onConfirm = {
            val toDelete = selectedMedia.toList()
            if (toDelete.isNotEmpty()) {
                scope.launch {
                    val deleted = privateFolderMoveViewModel.deleteFromPrivateFolder(toDelete)
                    if (deleted == 0) {
                        Toast.makeText(context, privateFolderDeleteFailedText, Toast.LENGTH_SHORT).show()
                    }
                    selector.clearSelection()
                }
            }
        }
    )

    // Cloud/remote confirmations — trash is recoverable, delete is permanent.
    ConfirmationSheet(
        state = cloudTrashConfirmState,
        title = stringResource(R.string.cloud_media_trash_confirm_title),
        summary = stringResource(R.string.cloud_media_trash_confirm_summary),
        onConfirm = {
            val toTrash = selectedMedia.toList()
            scope.launch {
                when (cloudSelectionViewModel.trash(deletionResult, toTrash)) {
                    MediaMutationResult.COMPLETED -> {
                        distributor.markPendingRemoval(
                            toTrash.map { it.id },
                            PendingRemovalScope.NON_TRASH
                        )
                        selector.clearSelection()
                    }
                    MediaMutationResult.FAILED -> Toast.makeText(
                        context,
                        cloudDeleteFailedText,
                        Toast.LENGTH_SHORT
                    ).show()
                    MediaMutationResult.REQUEST_LAUNCHED -> {
                        pendingDeletionIds = toTrash.mapTo(HashSet()) { it.id }
                        pendingDeletionScope = PendingRemovalScope.NON_TRASH
                    }
                }
            }
        }
    )

    ConfirmationSheet(
        state = cloudDeleteConfirmState,
        title = stringResource(R.string.cloud_media_delete_confirm_title),
        summary = stringResource(R.string.cloud_media_delete_confirm_summary),
        onConfirm = {
            val toDelete = selectedMedia.toList()
            scope.launch {
                when (cloudSelectionViewModel.delete(deletionResult, toDelete)) {
                    MediaMutationResult.COMPLETED -> {
                        distributor.markPendingRemoval(
                            toDelete.map { it.id },
                            PendingRemovalScope.EVERYWHERE
                        )
                        selector.clearSelection()
                    }
                    MediaMutationResult.FAILED -> Toast.makeText(
                        context,
                        cloudDeleteFailedText,
                        Toast.LENGTH_SHORT
                    ).show()
                    MediaMutationResult.REQUEST_LAUNCHED -> {
                        pendingDeletionIds = toDelete.mapTo(HashSet()) { it.id }
                        pendingDeletionScope = PendingRemovalScope.EVERYWHERE
                    }
                }
            }
        }
    )

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
            sheetState = rememberBottomSheetState(initialValue = SheetValue.Hidden, enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded)),
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
        ActionCondition.SUPPORTS_FAVORITES -> showFavoriteButton
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
fun RowScope.SelectionBarColumn(
    imageVector: ImageVector,
    title: String,
    onItemLongClick: (() -> Unit)? = null,
    onItemClick: () -> Unit,
) {
    val showTitles by rememberShowSelectionTitles()
    val tintColor = MaterialTheme.colorScheme.onSurface
    val minHeightSizeModifier = remember(showTitles) {
        if (showTitles) Modifier.defaultMinSize(minHeight = 80.dp)
        else Modifier.defaultMinSize(minHeight = 64.dp)
    }
    // The bottom bar scrolls horizontally once items overflow, so items keep a
    // minimum width instead of weighting to an equal share — when everything fits,
    // the row's SpaceEvenly arrangement spreads them across the full width.
    // With "fill action bar" enabled the items weight to an equal share of the
    // viewport instead, squeezing long labels rather than scrolling.
    val selectionSheetConfig by rememberSelectionSheetConfig()
    val minWidthSizeModifier = when {
        selectionSheetConfig.fillActionBar -> Modifier.weight(1f)
        showTitles -> Modifier.defaultMinSize(minWidth = 80.dp)
        else -> Modifier.defaultMinSize(minWidth = 64.dp)
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