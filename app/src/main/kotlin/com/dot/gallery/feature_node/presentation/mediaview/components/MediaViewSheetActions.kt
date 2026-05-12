package com.dot.gallery.feature_node.presentation.mediaview.components

import android.content.Intent
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.result.IntentSenderRequest
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.DriveFileMove
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.Collections
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.CopyAll
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Restore
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.dot.gallery.R
import com.dot.gallery.core.Settings
import com.dot.gallery.core.Settings.Misc.rememberAllowBlur
import com.dot.gallery.core.util.SdkCompat
import com.dot.gallery.feature_node.data.data_source.KeychainHolder
import com.dot.gallery.feature_node.domain.model.AlbumState
import com.dot.gallery.feature_node.domain.model.Media
import com.dot.gallery.feature_node.domain.model.Vault
import com.dot.gallery.feature_node.domain.model.VaultState
import com.dot.gallery.feature_node.domain.util.canMakeActions
import com.dot.gallery.feature_node.domain.util.getUri
import com.dot.gallery.feature_node.domain.util.isEncrypted
import com.dot.gallery.feature_node.domain.util.isImage
import com.dot.gallery.feature_node.domain.util.isLocalContent
import com.dot.gallery.feature_node.domain.util.isVideo
import com.dot.gallery.feature_node.presentation.collection.CollectionViewModel
import com.dot.gallery.feature_node.presentation.collection.components.AddToCollectionSheet
import com.dot.gallery.feature_node.presentation.exif.CopyMediaSheet
import com.dot.gallery.feature_node.presentation.exif.MoveMediaSheet
import com.dot.gallery.feature_node.presentation.util.LocalHazeState
import com.dot.gallery.feature_node.presentation.util.copyEncryptedMediaToClipboard
import com.dot.gallery.feature_node.presentation.util.copyMediaToClipboard
import com.dot.gallery.feature_node.presentation.util.launchEditImageIntent
import com.dot.gallery.feature_node.presentation.util.launchEditIntent
import com.dot.gallery.feature_node.presentation.util.launchOpenWithIntent
import com.dot.gallery.feature_node.presentation.util.launchUseAsIntent
import com.dot.gallery.feature_node.presentation.util.rememberActivityResult
import com.dot.gallery.feature_node.presentation.util.rememberAppBottomSheetState
import com.dot.gallery.feature_node.presentation.util.shareEncryptedMedia
import com.dot.gallery.feature_node.presentation.util.shareMedia
import com.dot.gallery.feature_node.presentation.vault.VaultViewModel
import com.dot.gallery.feature_node.presentation.vault.components.AddToVaultSheet
import com.dot.gallery.feature_node.presentation.vault.components.SelectVaultSheet
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.materials.ExperimentalHazeMaterialsApi
import dev.chrisbanes.haze.materials.HazeMaterials
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun <T : Media> MediaViewSheetActions(
    media: T,
    albumsState: State<AlbumState>,
    vaults: State<VaultState>,
    restoreMedia: ((Vault, T, () -> Unit) -> Unit)?,
    currentVault: Vault?
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // Sheet states for complex actions
    val hideSheetState = rememberAppBottomSheetState()
    val copySheetState = rememberAppBottomSheetState()
    val moveSheetState = rememberAppBottomSheetState()
    var showCollectionSheet by rememberSaveable { mutableStateOf(false) }

    val defaultEditor by Settings.Misc.rememberDefaultImageEditor()

    // Resolve strings
    val shareText = stringResource(R.string.share)
    val copyToClipboardText = stringResource(R.string.copy_to_clipboard)
    val hideText = stringResource(R.string.hide)
    val restoreText = stringResource(R.string.restore)
    val openWithText = stringResource(R.string.open_with)
    val useAsText = stringResource(R.string.use_as)
    val copyText = stringResource(R.string.copy)
    val moveText = stringResource(R.string.move)
    val editText = stringResource(R.string.edit)
    val addToCollectionText = stringResource(R.string.add_to_collection)
    // Lazily create a single KeychainHolder for encrypted operations
    val keychainHolder = remember(currentVault) {
        if (currentVault != null) lazy { KeychainHolder(context) } else null
    }

    // Build action list
    val actions = remember(media, albumsState.value, vaults.value, currentVault) {
        buildList<ActionGridItem> {
            // Share
            add(ActionGridItem(
                icon = Icons.Outlined.Share,
                text = shareText,
                onClick = {
                    scope.launch {
                        if (media.isEncrypted && currentVault != null && keychainHolder != null) {
                            context.shareEncryptedMedia(media, currentVault, keychainHolder.value)
                        } else {
                            context.shareMedia(media)
                        }
                    }
                }
            ))
            // Copy to Clipboard
            add(ActionGridItem(
                icon = Icons.Outlined.ContentCopy,
                text = copyToClipboardText,
                onClick = {
                    if (media.isEncrypted && currentVault != null && keychainHolder != null) {
                        scope.launch {
                            context.copyEncryptedMediaToClipboard(media, keychainHolder.value)
                        }
                    } else {
                        context.copyMediaToClipboard(media)
                    }
                }
            ))
            // Hide
            if (media.isLocalContent) {
                add(ActionGridItem(
                    icon = Icons.Outlined.Lock,
                    text = hideText,
                    enabled = vaults.value.vaults.isNotEmpty(),
                    onClick = { scope.launch { hideSheetState.show() } }
                ))
            }
            // Restore
            if (media.isEncrypted && restoreMedia != null && currentVault != null) {
                add(ActionGridItem(
                    icon = Icons.Outlined.Restore,
                    text = restoreText,
                    onClick = { scope.launch { restoreMedia(currentVault, media) {} } }
                ))
            }
            // Open As / Use As
            add(ActionGridItem(
                icon = Icons.AutoMirrored.Outlined.OpenInNew,
                text = if (media.isVideo) openWithText else useAsText,
                onClick = {
                    scope.launch {
                        if (media.isVideo) context.launchOpenWithIntent(media)
                        else context.launchUseAsIntent(media)
                    }
                }
            ))
            // Copy & Move
            if (albumsState.value.albums.isNotEmpty() && media.canMakeActions) {
                add(ActionGridItem(
                    icon = Icons.Outlined.CopyAll,
                    text = copyText,
                    onClick = { scope.launch { copySheetState.show() } }
                ))
                add(ActionGridItem(
                    icon = Icons.AutoMirrored.Outlined.DriveFileMove,
                    text = moveText,
                    onClick = { scope.launch { moveSheetState.show() } }
                ))
            }
            // Edit
            if (!media.isEncrypted) {
                add(ActionGridItem(
                    icon = Icons.Outlined.Edit,
                    text = editText,
                    onClick = {
                        if (media.isImage && defaultEditor != Settings.Misc.EDITOR_BUILTIN) {
                            try {
                                context.launchEditImageIntent(defaultEditor, media.getUri())
                            } catch (_: Exception) {
                                context.launchEditIntent(media)
                            }
                        } else {
                            context.launchEditIntent(media)
                        }
                    }
                ))
            }
            // Add to Collection
            if (media.isLocalContent && media.canMakeActions) {
                add(ActionGridItem(
                    icon = Icons.Outlined.Collections,
                    text = addToCollectionText,
                    onClick = { showCollectionSheet = true }
                ))
            }
        }
    }

    // Render as 2-column grid
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        actions.chunked(2).forEach { rowItems ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                rowItems.forEach { item ->
                    ActionGridCell(
                        modifier = Modifier.weight(1f),
                        icon = item.icon,
                        text = item.text,
                        enabled = item.enabled,
                        onClick = item.onClick
                    )
                }
                if (rowItems.size < 2) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }

    // --- Complex action sheets ---

    // Hide (vault selection)
    if (media.isLocalContent) {
        val vaultViewModel = hiltViewModel<VaultViewModel>()
        var vaultEncryptBehavior by Settings.Vault.rememberVaultEncryptBehavior()
        val addToVaultSheetState = rememberAppBottomSheetState()
        var selectedVault by remember { mutableStateOf<com.dot.gallery.feature_node.domain.model.Vault?>(null) }
        val hideResult = rememberActivityResult(onResultOk = {
            scope.launch { hideSheetState.hide() }
        })
        val hidingText = stringResource(R.string.vault_hide_in_progress)
        fun startHide(vault: com.dot.gallery.feature_node.domain.model.Vault, deleteOriginals: Boolean) {
            Toast.makeText(context, hidingText, Toast.LENGTH_SHORT).show()
            if (deleteOriginals) {
                vaultViewModel.hideAndRequestDeletion(vault, media.getUri())
            } else {
                vaultViewModel.addMediaKeepOriginals(vault, listOf(media.getUri()))
            }
        }
        SelectVaultSheet(
            state = hideSheetState,
            vaultState = vaults.value,
            onVaultSelected = { vault ->
                scope.launch {
                    when (vaultEncryptBehavior) {
                        Settings.Vault.ENCRYPT_DELETE -> startHide(vault, deleteOriginals = true)
                        Settings.Vault.ENCRYPT_KEEP -> startHide(vault, deleteOriginals = false)
                        else -> {
                            selectedVault = vault
                            addToVaultSheetState.show()
                        }
                    }
                }
            }
        )
        AddToVaultSheet(
            state = addToVaultSheetState,
            onEncryptAndDelete = {
                val vault = selectedVault ?: return@AddToVaultSheet
                startHide(vault, deleteOriginals = true)
            },
            onEncryptAndKeep = {
                val vault = selectedVault ?: return@AddToVaultSheet
                startHide(vault, deleteOriginals = false)
            },
            onBehaviorChanged = { vaultEncryptBehavior = it }
        )
        LaunchedEffect(Unit) {
            vaultViewModel.userMessage.collect { message ->
                Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
            }
        }
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
    }

    // Copy & Move sheets
    if (albumsState.value.albums.isNotEmpty() && media.canMakeActions) {
        CopyMediaSheet(
            sheetState = copySheetState,
            mediaList = listOf(media),
            albumsState = albumsState,
            onFinish = { }
        )
        MoveMediaSheet(
            sheetState = moveSheetState,
            mediaList = listOf(media),
            albumState = albumsState,
            onFinish = { }
        )
    }

    // Add to Collection sheet
    if (media.isLocalContent && media.canMakeActions) {
        val collectionViewModel = hiltViewModel<CollectionViewModel>()
        AddToCollectionSheet(
            visible = showCollectionSheet,
            collections = albumsState.value.collections,
            onDismiss = { showCollectionSheet = false },
            onCollectionSelected = { collectionId ->
                collectionViewModel.addMediaToCollection(collectionId, media.id)
            },
            onCreateAndAdd = { name ->
                collectionViewModel.createCollectionAndAddMedia(name, listOf(media.id))
            }
        )
    }
}

private data class ActionGridItem(
    val icon: ImageVector,
    val text: String,
    val enabled: Boolean = true,
    val onClick: () -> Unit
)

@OptIn(ExperimentalHazeMaterialsApi::class)
@Composable
private fun ActionGridCell(
    modifier: Modifier = Modifier,
    icon: ImageVector,
    text: String,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    val isBlurEnabled by rememberAllowBlur()
    val surfaceColor = MaterialTheme.colorScheme.surfaceContainerHigh
    val backgroundModifier = remember(isBlurEnabled) {
        if (!isBlurEnabled) {
            Modifier.background(
                color = surfaceColor,
                shape = RoundedCornerShape(16.dp)
            )
        } else Modifier
    }
    val hazeStyle = HazeMaterials.regular(
        containerColor = MaterialTheme.colorScheme.surface
    )
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .then(backgroundModifier)
            .hazeEffect(
                state = LocalHazeState.current,
                style = hazeStyle
            )
            .clickable(enabled = enabled, onClick = onClick)
            .alpha(if (enabled) 1f else 0.4f)
            .padding(horizontal = 12.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.size(20.dp)
        )
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}