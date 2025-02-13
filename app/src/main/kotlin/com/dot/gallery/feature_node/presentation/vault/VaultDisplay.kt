package com.dot.gallery.feature_node.presentation.vault

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import androidx.compose.animation.AnimatedContentScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Restore
import androidx.compose.material.icons.rounded.ArrowDropDown
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.SuggestionChipDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastCoerceAtLeast
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dokar.pinchzoomgrid.PinchZoomGridLayout
import com.dokar.pinchzoomgrid.rememberPinchZoomGridState
import com.dot.gallery.R
import com.dot.gallery.core.Constants.Animation.enterAnimation
import com.dot.gallery.core.Constants.Animation.exitAnimation
import com.dot.gallery.core.Constants.cellsList
import com.dot.gallery.core.Settings.Misc.rememberGridSize
import com.dot.gallery.core.presentation.components.EmptyMedia
import com.dot.gallery.core.presentation.components.ModalSheet
import com.dot.gallery.feature_node.domain.model.Media
import com.dot.gallery.feature_node.domain.model.MediaState
import com.dot.gallery.feature_node.domain.model.Vault
import com.dot.gallery.feature_node.domain.model.VaultState
import com.dot.gallery.feature_node.presentation.common.components.MediaGridView
import com.dot.gallery.feature_node.presentation.picker.PickerActivityContract
import com.dot.gallery.feature_node.presentation.util.rememberActivityResult
import com.dot.gallery.feature_node.presentation.util.rememberAppBottomSheetState
import com.dot.gallery.feature_node.presentation.vault.components.DeleteVaultSheet
import com.dot.gallery.feature_node.presentation.vault.components.NewVaultSheet
import com.dot.gallery.feature_node.presentation.vault.components.RestoreVaultSheet
import com.dot.gallery.feature_node.presentation.vault.components.SelectVaultSheet
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class, ExperimentalSharedTransitionApi::class)
@Composable
fun VaultDisplay(
    navigateUp: () -> Unit,
    navigate: (route: String) -> Unit,
    vaultState: State<VaultState>,
    currentVault: MutableState<Vault?>,
    createMediaState: (Vault?) -> StateFlow<MediaState<Media.UriMedia>>,
    onCreateVaultClick: () -> Unit,
    addMediaListToVault: (vault: Vault, mediaList: List<Uri>) -> Unit,
    deleteLeftovers: (result: ActivityResultLauncher<IntentSenderRequest>, uris: List<Uri>) -> Unit,
    setVault: (Vault) -> Unit,
    deleteVault: (Vault) -> Unit,
    restoreVault: (Vault) -> Unit,
    workerProgress: StateFlow<Float>,
    workerIsRunning: StateFlow<Boolean>,
    sharedTransitionScope: SharedTransitionScope,
    animatedContentScope: AnimatedContentScope,
) {
    val isRunning by workerIsRunning.collectAsStateWithLifecycle()
    val progress by workerProgress.collectAsStateWithLifecycle()

    @Suppress("LocalVariableName")
    var _mediaState by remember(currentVault.value, isRunning, progress) {
        mutableStateOf(createMediaState(currentVault.value))
    }

    val mediaState = _mediaState.collectAsStateWithLifecycle()

    LaunchedEffect(vaultState.value, currentVault.value) {
        currentVault.value?.let { setVault(it) } ?: run {
            vaultState.value.vaults.firstOrNull()?.let { setVault(it) }
        }
    }

    var lastCellIndex by rememberGridSize()

    val pinchState = rememberPinchZoomGridState(
        cellsList = cellsList,
        initialCellsIndex = lastCellIndex
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

    val pickerLauncher = rememberLauncherForActivityResult(PickerActivityContract()) { uriList ->
        scope.launch {
            if (uriList.isNotEmpty()) {
                toAddMedia = uriList
                addMediaListToVault(currentVault.value!!, uriList)
            }
        }
    }
    val postEncryptLauncher = rememberActivityResult()

    LaunchedEffect(isRunning) {
        if (isRunning) {
            bottomSheetState.show()
        } else {
            bottomSheetState.hide()
            if (toAddMedia.isNotEmpty()) {
                deleteLeftovers(postEncryptLauncher, toAddMedia)
                toAddMedia = emptyList()
            }
        }
    }

    val newVaultSheetState = rememberAppBottomSheetState()
    val decryptVaultSheetState = rememberAppBottomSheetState()
    val deleteVaultSheetState = rememberAppBottomSheetState()

    LaunchedEffect(vaultState.value) {
        if (vaultState.value.isLoading) return@LaunchedEffect
        if (vaultState.value.vaults.isNotEmpty()) return@LaunchedEffect
        navigateUp()
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

            Button(
                onClick = {
                    scope.launch {
                        val indexesToDrop = (progress * toAddMedia.size / 100).roundToInt()
                        toAddMedia = toAddMedia.dropLast(indexesToDrop)
                        bottomSheetState.hide()
                    }
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                    contentColor = MaterialTheme.colorScheme.onTertiaryContainer
                )
            ) {
                Text(text = stringResource(R.string.action_cancel))
            }
        }
    )

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .nestedScroll(scrollBehavior.nestedScrollConnection),
        floatingActionButton = {
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
                                text = currentVault.value?.name
                                    ?: stringResource(R.string.unknown_vault)
                            )
                            androidx.compose.animation.AnimatedVisibility(
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
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                            }
                        }
                        SelectVaultSheet(
                            state = sheetState,
                            vaultState = vaultState.value
                        ) { vault ->
                            scope.launch {
                                setVault(vault)
                            }
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = navigateUp) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(id = R.string.back_cd)
                        )
                    }
                },
                scrollBehavior = scrollBehavior
            )
        }
    ) {
        PinchZoomGridLayout(state = pinchState) {
            MediaGridView(
                mediaState = mediaState,
                allowSelection = true,
                showSearchBar = false,
                enableStickyHeaders = false,
                paddingValues = it,
                canScroll = canScroll,
                showMonthlyHeader = false,
                isScrolling = remember { mutableStateOf(false) },
                aboveGridContent = {
                    Column {
                        Row(
                            modifier = Modifier
                                .padding(top = 12.dp)
                                .horizontalScroll(state = rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Spacer(modifier = Modifier.size(0.dp))
                            SuggestionChip(
                                onClick = {
                                    scope.launch {
                                        newVaultSheetState.show()
                                    }
                                },
                                label = { Text(text = stringResource(R.string.new_vault)) },
                                border = null,
                                shape = CircleShape,
                                colors = SuggestionChipDefaults.suggestionChipColors(
                                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                                    labelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                    iconContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                                ),
                                icon = {
                                    Icon(
                                        imageVector = Icons.Outlined.Add,
                                        contentDescription = null
                                    )
                                }
                            )
                            SuggestionChip(
                                onClick = {
                                    scope.launch {
                                        decryptVaultSheetState.show()
                                    }
                                },
                                label = { Text(text = stringResource(R.string.decrypt_vault)) },
                                border = null,
                                shape = CircleShape,
                                colors = SuggestionChipDefaults.suggestionChipColors(
                                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                    labelColor = MaterialTheme.colorScheme.onSecondaryContainer,
                                    iconContentColor = MaterialTheme.colorScheme.onSecondaryContainer
                                ),
                                icon = {
                                    Icon(
                                        imageVector = Icons.Outlined.Restore,
                                        contentDescription = null
                                    )
                                }
                            )
                            
                            SuggestionChip(
                                onClick = {
                                    scope.launch {
                                        deleteVaultSheetState.show()
                                    }
                                },
                                label = { Text(text = stringResource(R.string.delete_vault)) },
                                border = null,
                                shape = CircleShape,
                                colors = SuggestionChipDefaults.suggestionChipColors(
                                    containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                                    labelColor = MaterialTheme.colorScheme.onTertiaryContainer,
                                    iconContentColor = MaterialTheme.colorScheme.onTertiaryContainer
                                ),
                                icon = {
                                    Icon(
                                        imageVector = Icons.Default.DeleteOutline,
                                        contentDescription = null
                                    )
                                }
                            )
                            Spacer(modifier = Modifier.size(0.dp))
                        }
                    }
                    NewVaultSheet(
                        state = newVaultSheetState,
                        onConfirm = onCreateVaultClick
                    )
                    DeleteVaultSheet(
                        state = deleteVaultSheetState
                    ) {
                        val vault = currentVault.value ?: vaultState.value.vaults.firstOrNull()
                        vault?.let { it1 -> deleteVault(it1) }
                        if (vaultState.value.vaults.isEmpty()) {
                            navigateUp()
                        }
                    }
                    RestoreVaultSheet(
                        state = decryptVaultSheetState
                    ) {
                        val vault = currentVault.value ?: vaultState.value.vaults.firstOrNull()
                        vault?.let { it1 -> restoreVault(it1) }
                        if (vaultState.value.vaults.isEmpty()) {
                            navigateUp()
                        }
                    }
                },
                onMediaClick = { encryptedMedia ->
                    navigate(VaultScreens.EncryptedMediaViewScreen.id(encryptedMedia.id))
                },
                emptyContent = {
                    EmptyMedia()
                },
                sharedTransitionScope = sharedTransitionScope,
                animatedContentScope = animatedContentScope
            )
        }
    }

}