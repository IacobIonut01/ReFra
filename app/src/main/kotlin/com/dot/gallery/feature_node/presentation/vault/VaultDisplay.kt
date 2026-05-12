package com.dot.gallery.feature_node.presentation.vault

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import androidx.compose.animation.AnimatedContentScope
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.layout.LazyLayoutCacheWindow
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.LockOpen
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Restore
import androidx.compose.material.icons.rounded.ArrowDropDown
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastCoerceAtLeast
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dot.gallery.R
import com.dot.gallery.core.Constants.Animation.enterAnimation
import com.dot.gallery.core.Constants.Animation.exitAnimation
import com.dot.gallery.core.Constants.cellsList
import com.dot.gallery.core.LocalEventHandler
import com.dot.gallery.core.LocalMediaSelector
import com.dot.gallery.core.Settings
import com.dot.gallery.core.Settings.Misc.rememberGridSize
import com.dot.gallery.core.navigate
import com.dot.gallery.core.presentation.components.EmptyMedia
import com.dot.gallery.core.presentation.components.ModalSheet
import com.dot.gallery.core.presentation.components.NavigationBackButton
import com.dot.gallery.core.presentation.components.SelectionSheet
import com.dot.gallery.core.presentation.components.SetupButton
import com.dot.gallery.feature_node.domain.model.Media
import com.dot.gallery.feature_node.domain.model.MediaMetadataState
import com.dot.gallery.feature_node.domain.model.MediaState
import com.dot.gallery.feature_node.domain.model.Vault
import com.dot.gallery.feature_node.domain.model.VaultState
import com.dot.gallery.feature_node.presentation.common.components.GridPinchZoomLayout
import com.dot.gallery.feature_node.presentation.common.components.MediaGridView
import com.dot.gallery.feature_node.presentation.common.components.OptionItem
import com.dot.gallery.feature_node.presentation.common.components.OptionSheet
import com.dot.gallery.feature_node.presentation.common.components.rememberGridPinchZoomState
import com.dot.gallery.feature_node.presentation.picker.PickerActivityContract
import com.dot.gallery.feature_node.presentation.util.AppBottomSheetState
import com.dot.gallery.feature_node.presentation.util.LocalHazeState
import com.dot.gallery.feature_node.presentation.util.rememberActivityResult
import com.dot.gallery.feature_node.presentation.util.rememberAppBottomSheetState
import com.dot.gallery.feature_node.presentation.util.selectedMedia
import com.dot.gallery.feature_node.presentation.vault.components.AddToVaultSheet
import com.dot.gallery.feature_node.presentation.vault.components.DeleteVaultSheet
import com.dot.gallery.feature_node.presentation.vault.components.NewVaultSheet
import com.dot.gallery.feature_node.presentation.vault.components.RemovePasswordSheet
import com.dot.gallery.feature_node.presentation.vault.components.RestoreVaultSheet
import com.dot.gallery.feature_node.presentation.vault.components.SelectVaultSheet
import com.dot.gallery.feature_node.presentation.vault.components.VaultPasswordSetupSheet
import com.dot.gallery.feature_node.presentation.vault.utils.VaultPasswordManager
import dev.chrisbanes.haze.hazeSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@OptIn(
    ExperimentalMaterial3Api::class, ExperimentalSharedTransitionApi::class,
    ExperimentalFoundationApi::class
)
@Composable
fun VaultDisplay(
    globalNavigateUp: () -> Unit,
    vaultState: State<VaultState>,
    currentVault: MutableStateFlow<Vault?>,
    createMediaState: (Vault?) -> StateFlow<MediaState<Media.UriMedia>>,
    onCreateVaultClick: () -> Unit,
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
) {
    val eventHandler = LocalEventHandler.current
    val isRunning by workerIsRunning.collectAsStateWithLifecycle()
    val progress by workerProgress.collectAsStateWithLifecycle()
    val currentVaultValue by currentVault.collectAsStateWithLifecycle()
    val mediaState = remember(currentVaultValue) {
        createMediaState(currentVaultValue)
    }.collectAsStateWithLifecycle()

    // Keep current vault in sync; if deleted or null, navigate back
    LaunchedEffect(vaultState.value, currentVaultValue) {
        val current = currentVaultValue
        val vaults = vaultState.value.vaults
        if (current != null && vaults.any { it.uuid == current.uuid }) {
            setVault(current)
        } else if (current == null && vaults.isNotEmpty()) {
            // Vault was deleted but others remain — go to vault selector for auth
            onVaultDeleted()
        } else if (current == null && vaults.isEmpty()) {
            // Last vault deleted — exit to library
            globalNavigateUp()
        }
    }

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
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior(
        state = rememberTopAppBarState(),
        canScroll = { canScroll },
    )

    LaunchedEffect(pinchState.isZooming) {
        canScroll = !pinchState.isZooming
        lastCellIndex = cellsList.indexOf(pinchState.currentCells)
    }

    val scope = rememberCoroutineScope()
    val bottomSheetState = rememberAppBottomSheetState()

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

    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(userMessage) {
        userMessage?.collect { message ->
            snackbarHostState.showSnackbar(message)
        }
    }

    val context = androidx.compose.ui.platform.LocalContext.current
    val passwordSetupSheetState = rememberAppBottomSheetState()
    var hasCustomPassword by remember { mutableStateOf(false) }
    val passwordSetMsg = stringResource(R.string.vault_password_set)
    val passwordRemovedMsg = stringResource(R.string.vault_password_removed)
    LaunchedEffect(currentVaultValue) {
        val uuid = currentVaultValue?.uuid ?: return@LaunchedEffect
        hasCustomPassword = VaultPasswordManager.hasCustomPassword(context, uuid)
    }
    val removePasswordSheetState = rememberAppBottomSheetState()
    RemovePasswordSheet(
        state = removePasswordSheetState,
        onConfirm = {
            val uuid = currentVaultValue?.uuid ?: return@RemovePasswordSheet
            scope.launch {
                VaultPasswordManager.removePassword(context, uuid)
                hasCustomPassword = false
                snackbarHostState.showSnackbar(passwordRemovedMsg)
            }
        }
    )
    VaultPasswordSetupSheet(
        state = passwordSetupSheetState,
        onSecretSet = { type, secret ->
            val uuid = currentVaultValue?.uuid ?: return@VaultPasswordSetupSheet
            scope.launch {
                VaultPasswordManager.setPassword(context, uuid, secret, type)
                hasCustomPassword = true
                snackbarHostState.showSnackbar(passwordSetMsg)
            }
        }
    )

    LaunchedEffect(Unit) {
        pendingDeletions.collect { leftovers ->
            if (leftovers.isNotEmpty()) {
                deleteLeftovers(postEncryptLauncher, leftovers)
                toAddMedia = emptyList()
                bottomSheetState.hide()
            }
        }
    }

    val newVaultSheetState = rememberAppBottomSheetState()
    val decryptVaultSheetState = rememberAppBottomSheetState()
    val deleteVaultSheetState = rememberAppBottomSheetState()
    val actionsSheetState = rememberAppBottomSheetState()

    LaunchedEffect(vaultState.value) {
        if (vaultState.value.isLoading) return@LaunchedEffect
        if (vaultState.value.vaults.isNotEmpty()) return@LaunchedEffect
        globalNavigateUp()
    }

    LaunchedEffect(isRunning) {
        if (isRunning) {
            bottomSheetState.show()
        } else {
            bottomSheetState.hide()
        }
    }

    ModalSheet(
        sheetState = bottomSheetState,
        onDismissRequest = {},
        title = stringResource(R.string.encrypting_media),
        content = {
            Box(
                modifier = Modifier
                    .padding(32.dp)
                    .align(Alignment.CenterHorizontally)
                    .padding(bottom = 48.dp),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(
                    progress = {
                        progress.fastCoerceAtLeast(0f)
                    },
                    strokeWidth = 4.dp,
                    strokeCap = StrokeCap.Round,
                    modifier = Modifier.size(128.dp),
                )
                Text(text = "${progress.roundToInt()}%")
            }

            SetupButton(
                onClick = {
                    scope.launch {
                        val indexesToDrop = (progress * toAddMedia.size / 100).roundToInt()
                        toAddMedia = toAddMedia.dropLast(indexesToDrop)
                        bottomSheetState.hide()
                    }
                },
                containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                applyHorizontalPadding = false,
                applyBottomPadding = false,
                applyInsets = false,
                text = stringResource(R.string.action_cancel)
            )
        }
    )

    val selector = LocalMediaSelector.current
    val selectionState = selector.isSelectionActive.collectAsStateWithLifecycle()
    val selectedMediaSet = selector.selectedMedia.collectAsStateWithLifecycle()
    val selectedMediaList by selectedMedia(
        media = mediaState.value.media,
        selectedSet = selectedMediaSet
    )

    Box(
        modifier = Modifier
    ) {
        Scaffold(
            modifier = Modifier
                .fillMaxSize()
                .nestedScroll(scrollBehavior.nestedScrollConnection),
            snackbarHost = { SnackbarHost(snackbarHostState) },
            floatingActionButton = {
                AnimatedVisibility(
                    visible = !selectionState.value,
                    enter = enterAnimation,
                    exit = exitAnimation
                ) {
                    FloatingActionButton(
                        onClick = {
                            pickerLauncher.launch(Unit)
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Add,
                            contentDescription = stringResource(R.string.add_media_to_vault_cd)
                        )
                    }
                }
            },
            topBar = {
                TopAppBar(
                    title = {
                        Column {
                            val sheetState = rememberAppBottomSheetState()
                            Row(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable(
                                        enabled = remember(vaultState.value) {
                                            vaultState.value.vaults.size > 1
                                        },
                                    ) {
                                        scope.launch {
                                            sheetState.show()
                                        }
                                    },
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                Text(
                                    text = currentVaultValue?.name
                                        ?: stringResource(R.string.unknown_vault)
                                )
                                AnimatedVisibility(
                                    visible = vaultState.value.vaults.size > 1,
                                    enter = enterAnimation,
                                    exit = exitAnimation
                                ) {
                                    Icon(
                                        modifier = Modifier
                                            .size(24.dp)
                                            .background(
                                                color = MaterialTheme.colorScheme.secondaryContainer,
                                                shape = CircleShape
                                            ),
                                        imageVector = Icons.Rounded.ArrowDropDown,
                                        contentDescription = stringResource(R.string.switch_vault_cd),
                                        tint = MaterialTheme.colorScheme.onSecondaryContainer
                                    )
                                }
                            }
                            SelectVaultSheet(
                                state = sheetState,
                                vaultState = vaultState.value,
                                excludeVault = currentVaultValue
                            ) { vault ->
                                onAuthenticateVault(vault)
                            }
                        }
                    },
                    navigationIcon = {
                        NavigationBackButton(
                            forcedAction = globalNavigateUp,
                        )
                    },
                    actions = {
                        IconButton(onClick = {
                            scope.launch { actionsSheetState.show() }
                        }) {
                            Icon(
                                imageVector = Icons.Outlined.MoreVert,
                                contentDescription = stringResource(R.string.more_options)
                            )
                        }
                    },
                    scrollBehavior = scrollBehavior
                )
            }
        ) { scaffoldPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .hazeSource(LocalHazeState.current)
            ) {
                GridPinchZoomLayout(
                    state = pinchState,
                    indicatorTopPadding = scaffoldPadding.calculateTopPadding() + 16.dp,
                ) {
                    MediaGridView(
                        mediaState = mediaState,
                        metadataState = metadataState,
                        paddingValues = scaffoldPadding,
                        showSearchBar = false,
                        allowSelection = true,
                        canScroll = canScroll,
                        aboveGridContent = {},
                        isScrolling = remember { mutableStateOf(false) },
                        emptyContent = ::EmptyMedia,
                        sharedTransitionScope = sharedTransitionScope,
                        animatedContentScope = animatedContentScope,
                        onMediaClick = { encryptedMedia ->
                            onMediaClick(encryptedMedia.id)
                        },
                    )
                }
            }
        }

        SelectionSheet(
            modifier = Modifier.align(Alignment.BottomEnd),
            allMedia = mediaState.value,
            selectedMedia = selectedMediaList,
            isInVault = true,
            currentVault = currentVaultValue,
        )

        AddToVaultSheet(
            state = addToVaultSheetState,
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

        NewVaultSheet(
            state = newVaultSheetState,
            onConfirm = onCreateVaultClick
        )
        DeleteVaultSheet(
            state = deleteVaultSheetState,
            vaultName = currentVaultValue?.name
        ) {
            val vault = currentVaultValue ?: vaultState.value.vaults.firstOrNull()
            vault?.let { it1 -> deleteVault(it1) }
        }
        RestoreVaultSheet(
            state = decryptVaultSheetState
        ) {
            val vault = currentVaultValue ?: vaultState.value.vaults.firstOrNull()
            vault?.let { it1 -> restoreVault(it1) }
        }
        VaultActionsSheet(
            state = actionsSheetState,
            hasCustomPassword = hasCustomPassword,
            onNewVault = { scope.launch { newVaultSheetState.show() } },
            onDecryptVault = { scope.launch { decryptVaultSheetState.show() } },
            onCustomPassword = {
                if (hasCustomPassword) {
                    scope.launch { removePasswordSheetState.show() }
                } else {
                    scope.launch { passwordSetupSheetState.show() }
                }
            },
            onDeleteVault = { scope.launch { deleteVaultSheetState.show() } }
        )
    }

}

@Composable
private fun VaultActionsSheet(
    state: AppBottomSheetState,
    hasCustomPassword: Boolean,
    onNewVault: () -> Unit,
    onDecryptVault: () -> Unit,
    onCustomPassword: () -> Unit,
    onDeleteVault: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val newVaultLabel = stringResource(R.string.new_vault)
    val decryptLabel = stringResource(R.string.decrypt_vault)
    val customPasswordLabel = stringResource(
        if (hasCustomPassword) R.string.vault_remove_custom_password
        else R.string.vault_custom_password
    )
    val deleteVaultLabel = stringResource(R.string.delete_vault)

    val options = remember(
        hasCustomPassword,
        newVaultLabel,
        decryptLabel,
        customPasswordLabel,
        deleteVaultLabel
    ) {
        listOf(
            OptionItem(
                icon = Icons.Outlined.Add,
                text = newVaultLabel,
                onClick = {
                    scope.launch { state.hide() }
                    onNewVault()
                }
            ),
            OptionItem(
                icon = Icons.Outlined.Restore,
                text = decryptLabel,
                onClick = {
                    scope.launch { state.hide() }
                    onDecryptVault()
                }
            ),
            OptionItem(
                icon = if (hasCustomPassword) Icons.Outlined.Lock else Icons.Outlined.LockOpen,
                text = customPasswordLabel,
                onClick = {
                    scope.launch { state.hide() }
                    onCustomPassword()
                }
            ),
            OptionItem(
                icon = Icons.Default.DeleteOutline,
                text = deleteVaultLabel,
                onClick = {
                    scope.launch { state.hide() }
                    onDeleteVault()
                }
            ),
        ).toMutableStateList()
    }

    OptionSheet(
        state = state,
        headerContent = {
            Text(
                text = stringResource(R.string.vault_actions),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp)
            )
        },
        optionList = arrayOf(options)
    )
}