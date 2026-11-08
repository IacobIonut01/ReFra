package com.dot.gallery.app.presentation.vault

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import androidx.compose.animation.AnimatedContentScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.layout.LazyLayoutCacheWindow
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dot.gallery.core.LocalMediaSelector
import com.dot.gallery.core.Settings
import com.dot.gallery.feature.vault.ui.VaultEmptyState
import com.dot.gallery.core.presentation.components.GridPinchZoomLayout
import com.dot.gallery.core.presentation.components.MediaGridView
import com.dot.gallery.core.presentation.components.SelectionSheet
import com.dot.gallery.core.presentation.util.LocalHazeState
import com.dot.gallery.core.presentation.util.selectedMedia
import com.dot.gallery.data.model.MediaMetadataState
import com.dot.gallery.domain.model.MediaState
import com.dot.gallery.domain.model.UriMedia
import com.dot.gallery.domain.model.Vault
import com.dot.gallery.domain.model.VaultState
import com.dot.gallery.feature.vault.ui.VaultDisplayContent
import com.dot.gallery.app.presentation.picker.PickerActivityContract
import com.dot.gallery.core.ui.util.rememberActivityResult
import com.dot.gallery.feature.vault.ui.components.AddToVaultSheet
import com.dot.gallery.feature.vault.utils.VaultPasswordManager
import dev.chrisbanes.haze.hazeSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.launch
import com.dot.gallery.core.Constants.cellsList
import com.dot.gallery.core.Settings.Misc.rememberGridSize
import com.dot.gallery.core.Settings.Misc.rememberVaultGroupByDate
import com.dot.gallery.core.Settings.Misc.rememberVaultGroupMethod
import com.dot.gallery.core.presentation.components.rememberGridPinchZoomState
import com.dot.gallery.core.ui.util.rememberAppBottomSheetState
import com.dot.gallery.core.presentation.components.rememberScrolledUnderTopBar

/**
 * Vault media display screen. Chrome (top bar, FAB, progress + management sheets)
 * lives in `:feature:vault` ([VaultDisplayContent]); this wrapper owns the
 * Android seams — the media picker contract, pinch-zoom grid state, haze source,
 * the shared [MediaGridView]/[SelectionSheet], and credential persistence via
 * [VaultPasswordManager].
 */
@OptIn(ExperimentalSharedTransitionApi::class, ExperimentalFoundationApi::class)
@Composable
fun VaultDisplay(
    globalNavigateUp: () -> Unit,
    vaultState: State<VaultState>,
    currentVault: MutableStateFlow<Vault?>,
    createMediaState: (Vault?) -> StateFlow<MediaState<UriMedia>>,
    deleteLeftovers: (result: ActivityResultLauncher<IntentSenderRequest>, uris: List<Uri>) -> Unit,
    setVault: (Vault) -> Unit,
    deleteVault: (Vault) -> Unit,
    restoreVault: (Vault) -> Unit,
    workerProgress: StateFlow<Float>,
    workerIsRunning: StateFlow<Boolean>,
    sharedTransitionScope: SharedTransitionScope,
    animatedContentScope: AnimatedContentScope,
    metadataState: State<MediaMetadataState>,
    onAuthenticateVault: (Vault) -> Unit = {},
    onVaultDeleted: () -> Unit = {},
    encryptAndRequestDeletion: (Vault, List<Uri>) -> Unit = { _, _ -> },
    addMediaKeepOriginals: (Vault, List<Uri>) -> Unit = { _, _ -> },
    pendingDeletions: Flow<List<Uri>> = emptyFlow(),
    userMessage: Flow<String>? = null,
    onMediaClick: (Long) -> Unit = {},
    requestAddMedia: Boolean = false,
    onAddMediaRequestHandled: () -> Unit = {},
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val isRunning by workerIsRunning.collectAsStateWithLifecycle()
    val progress by workerProgress.collectAsStateWithLifecycle()
    val currentVaultValue by currentVault.collectAsStateWithLifecycle()
    val mediaState = remember(currentVaultValue) {
        createMediaState(currentVaultValue)
    }.collectAsStateWithLifecycle()

    var lastCellIndex by rememberGridSize()

    val dpCacheWindow = remember {
        LazyLayoutCacheWindow(ahead = 200.dp, behind = 100.dp)
    }
    val pinchState = rememberGridPinchZoomState(
        cellsList = cellsList,
        initialCellsIndex = lastCellIndex,
        gridState = rememberLazyGridState(
            cacheWindow = dpCacheWindow
        )
    )

    var canScroll by rememberSaveable { mutableStateOf(true) }

    LaunchedEffect(pinchState.isZooming) {
        canScroll = !pinchState.isZooming
        lastCellIndex = cellsList.indexOf(pinchState.currentCells)
    }

    var toAddMedia by remember { mutableStateOf<List<Uri>>(emptyList()) }

    var pickedUris by remember { mutableStateOf<List<Uri>>(emptyList()) }
    val addToVaultSheetState = rememberAppBottomSheetState()
    var vaultEncryptBehavior by Settings.Vault.rememberVaultEncryptBehavior()

    val pickerLauncher = rememberLauncherForActivityResult(PickerActivityContract()) { uriList ->
        scope.launch {
            if (uriList.isNotEmpty()) {
                val uris = uriList.map { it.toUri() }
                val vault = currentVaultValue ?: return@launch
                when (vaultEncryptBehavior) {
                    Settings.Vault.ENCRYPT_DELETE -> {
                        toAddMedia = uris
                        encryptAndRequestDeletion(vault, uris)
                    }

                    Settings.Vault.ENCRYPT_KEEP -> {
                        toAddMedia = uris
                        addMediaKeepOriginals(vault, uris)
                    }

                    else -> {
                        pickedUris = uris
                        addToVaultSheetState.show()
                    }
                }
            }
        }
    }
    val postEncryptLauncher = rememberActivityResult()

    // The setup's Ready page asked to add photos: open the picker once the vault is shown.
    LaunchedEffect(requestAddMedia, currentVaultValue) {
        if (requestAddMedia && currentVaultValue != null) {
            onAddMediaRequestHandled()
            pickerLauncher.launch(currentVaultValue?.uuid?.toString())
        }
    }

    LaunchedEffect(Unit) {
        pendingDeletions.collect { leftovers ->
            if (leftovers.isNotEmpty()) {
                deleteLeftovers(postEncryptLauncher, leftovers)
                toAddMedia = emptyList()
            }
        }
    }

    var hasCustomPassword by remember { mutableStateOf(false) }
    LaunchedEffect(currentVaultValue) {
        val uuid = currentVaultValue?.uuid ?: return@LaunchedEffect
        hasCustomPassword = VaultPasswordManager.hasCustomPassword(context, uuid)
    }

    val selector = LocalMediaSelector.current
    val selectionState = selector.isSelectionActive.collectAsStateWithLifecycle()
    val selectedMediaSet = selector.selectedMedia.collectAsStateWithLifecycle()
    val selectedMediaList by selectedMedia(
        media = mediaState.value.media,
        selectedSet = selectedMediaSet
    )

    VaultDisplayContent(
        vaultState = vaultState.value,
        currentVault = currentVaultValue,
        isSelectionActive = selectionState.value,
        workerIsRunning = isRunning,
        workerProgress = progress,
        pendingEncryptCount = toAddMedia.size,
        hasCustomPassword = hasCustomPassword,
        canScroll = canScroll,
        scrolled = rememberScrolledUnderTopBar(pinchState.gridState),
        mediaCount = mediaState.value.media.size.takeUnless { mediaState.value.isLoading },
        userMessage = userMessage,
        onNavigateUp = globalNavigateUp,
        onVaultDeleted = onVaultDeleted,
        onResyncVault = setVault,
        onAuthenticateVault = onAuthenticateVault,
        onDeleteVault = deleteVault,
        onRestoreVault = restoreVault,
        onSecretSet = { type, secret ->
            val uuid = currentVaultValue?.uuid ?: return@VaultDisplayContent
            VaultPasswordManager.setPassword(context, uuid, secret, type)
            hasCustomPassword = true
        },
        onRemovePassword = {
            val uuid = currentVaultValue?.uuid ?: return@VaultDisplayContent
            VaultPasswordManager.removePassword(context, uuid)
            hasCustomPassword = false
        },
        onAddMediaClick = { pickerLauncher.launch(currentVaultValue?.uuid?.toString()) },
        onEncryptSheetCancel = { dropCount ->
            toAddMedia = toAddMedia.dropLast(dropCount)
        },
        gridContent = { scaffoldPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .hazeSource(LocalHazeState.current)
            ) {
                GridPinchZoomLayout(
                    state = pinchState,
                    indicatorTopPadding = scaffoldPadding.calculateTopPadding() + 16.dp,
                ) {
                    val vaultGroupByDate by rememberVaultGroupByDate()
                    val vaultGroupMethod by rememberVaultGroupMethod()
                    MediaGridView(
                        mediaState = mediaState,
                        metadataState = metadataState,
                        paddingValues = scaffoldPadding,
                        showSearchBar = false,
                        allowSelection = true,
                        allowHeaders = vaultGroupByDate,
                        // Sticky date headers clash with the blur bar.
                        enableStickyHeaders = false,
                        groupMethod = if (vaultGroupByDate) vaultGroupMethod else Settings.Misc.GROUP_NORMAL,
                        canScroll = canScroll,
                        aboveGridContent = {},
                        isScrolling = remember { mutableStateOf(false) },
                        emptyContent = {
                            VaultEmptyState(
                                vaultName = currentVaultValue?.name.orEmpty(),
                                onAddMedia = { pickerLauncher.launch(currentVaultValue?.uuid?.toString()) },
                            )
                        },
                        sharedTransitionScope = sharedTransitionScope,
                        animatedContentScope = animatedContentScope,
                        onMediaClick = { encryptedMedia ->
                            onMediaClick(encryptedMedia.id)
                        },
                    )
                }
            }
        },
        selectionContent = {
            SelectionSheet(
                modifier = Modifier.align(Alignment.BottomEnd),
                allMedia = mediaState.value,
                selectedMedia = selectedMediaList,
                isInVault = true,
                currentVault = currentVaultValue,
            )

            AddToVaultSheet(
                state = addToVaultSheetState,
                itemCount = pickedUris.size,
                vaultName = currentVaultValue?.name,
                onEncryptAndDelete = {
                    val vault = currentVaultValue ?: return@AddToVaultSheet
                    toAddMedia = pickedUris
                    encryptAndRequestDeletion(vault, pickedUris)
                    pickedUris = emptyList()
                },
                onEncryptAndKeep = {
                    val vault = currentVaultValue ?: return@AddToVaultSheet
                    toAddMedia = pickedUris
                    addMediaKeepOriginals(vault, pickedUris)
                    pickedUris = emptyList()
                },
                onBehaviorChanged = { vaultEncryptBehavior = it }
            )
        }
    )
}
