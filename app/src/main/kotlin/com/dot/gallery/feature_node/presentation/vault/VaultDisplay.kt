package com.dot.gallery.feature_node.presentation.vault

import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.annotation.RequiresApi
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
import androidx.compose.runtime.mutableFloatStateOf
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
import com.dokar.pinchzoomgrid.PinchZoomGridLayout
import com.dokar.pinchzoomgrid.rememberPinchZoomGridState
import com.dot.gallery.R
import com.dot.gallery.core.Constants.Animation.enterAnimation
import com.dot.gallery.core.Constants.Animation.exitAnimation
import com.dot.gallery.core.Constants.cellsList
import com.dot.gallery.core.Settings.Misc.rememberGridSize
import com.dot.gallery.core.presentation.components.EmptyMedia
import com.dot.gallery.core.presentation.components.ModalSheet
import com.dot.gallery.feature_node.domain.model.Media.UriMedia
import com.dot.gallery.feature_node.domain.model.MediaState
import com.dot.gallery.feature_node.domain.model.Vault
import com.dot.gallery.feature_node.domain.model.VaultState
import com.dot.gallery.feature_node.presentation.common.components.MediaGridView
import com.dot.gallery.feature_node.presentation.mediaview.rememberedDerivedState
import com.dot.gallery.feature_node.presentation.picker.PickerActivityContract
import com.dot.gallery.feature_node.presentation.util.rememberAppBottomSheetState
import com.dot.gallery.feature_node.presentation.vault.components.DeleteVaultSheet
import com.dot.gallery.feature_node.presentation.vault.components.NewVaultSheet
import com.dot.gallery.feature_node.presentation.vault.components.SelectVaultSheet
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class, ExperimentalSharedTransitionApi::class)
@Composable
fun VaultDisplay(
    navigateUp: () -> Unit,
    navigate: (route: String) -> Unit,
    mediaState: State<MediaState<UriMedia>>,
    vaultState: VaultState,
    currentVault: MutableState<Vault?>,
    onCreateVaultClick: () -> Unit,
    addMediaToVault: (vault: Vault, media: UriMedia, onSuccess: () -> Unit, onFailed: (reason: String) -> Unit) -> Unit,
    addMediaListToVault: (vault: Vault, mediaList: List<UriMedia>) -> Unit,
    setVault: (Vault) -> Unit,
    deleteVault: (Vault) -> Unit,
    sharedTransitionScope: SharedTransitionScope,
    animatedContentScope: AnimatedContentScope,
) {

    LaunchedEffect(vaultState, currentVault.value) {
        currentVault.value?.let { setVault(it) } ?: run {
            vaultState.vaults.firstOrNull()?.let { setVault(it) }
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

    var toAddMedia by remember { mutableStateOf<List<UriMedia>>(emptyList()) }

    val pickerLauncher = rememberLauncherForActivityResult(PickerActivityContract()) { mediaList ->
        scope.launch {
            if (mediaList.isNotEmpty()) {
                toAddMedia = mediaList
                bottomSheetState.show()
            }
        }
    }

    val newVaultSheetState = rememberAppBottomSheetState()
    val deleteVaultSheetState = rememberAppBottomSheetState()

    LaunchedEffect(vaultState) {
        if (vaultState.isLoading) return@LaunchedEffect
        if (vaultState.vaults.isNotEmpty()) return@LaunchedEffect
        navigateUp()
    }

    var failedMedia by remember { mutableStateOf(emptyList<UriMedia>()) }
    var progress by remember { mutableFloatStateOf(0f) }
    var currentIndex by remember { mutableStateOf<Int?>(null) }
    val text by rememberedDerivedState {
        if (progress == 1f) {
            if (failedMedia.isEmpty()) {
                "Media added successfully"
            } else {
                "Failed to add ${failedMedia.size} media"
            }
        } else if (currentIndex != null) {
            "Encrypting media $currentIndex/${toAddMedia.size}"
        } else {
            "Encrypting media..."
        }
    }

    ModalSheet(
        sheetState = bottomSheetState,
        onDismissRequest = {
            toAddMedia = emptyList()
        },
        title = text,
        content = {
            LaunchedEffect(toAddMedia) {
                if (toAddMedia.isEmpty()) return@LaunchedEffect
                progress = 0f
                failedMedia = emptyList()
                currentIndex = null
                addMediaListToVault(currentVault.value!!, toAddMedia)
                progress = 1f
            }

            LaunchedEffect(progress) {
                if (progress == 1f) {
                    scope.launch {
                        bottomSheetState.hide()
                    }
                }
            }

            Box(
                modifier = Modifier
                    .padding(32.dp)
                    .align(Alignment.CenterHorizontally)
                    .padding(bottom = 48.dp),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(
                    progress = {
                        progress
                    },
                    strokeWidth = 4.dp,
                    strokeCap = StrokeCap.Round,
                    modifier = Modifier.size(128.dp),
                )
                Text(text = "${(progress * 100).roundToInt()}%")
            }

            Button(
                onClick = {
                    scope.launch {
                        toAddMedia = emptyList()
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
                                    enabled = remember(vaultState) {
                                        vaultState.vaults.size > 1
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
                                visible = vaultState.vaults.size > 1,
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
                            vaultState = vaultState
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
                                .padding(horizontal = 16.dp)
                                .padding(top = 12.dp)
                                .horizontalScroll(state = rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
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
                        }
                    }
                    NewVaultSheet(
                        state = newVaultSheetState,
                        onConfirm = onCreateVaultClick
                    )
                    DeleteVaultSheet(
                        state = deleteVaultSheetState,
                        onConfirm = {
                            val vault = currentVault.value ?: vaultState.vaults.firstOrNull()
                            vault?.let { it1 -> deleteVault(it1) }
                            if (vaultState.vaults.isEmpty()) {
                                navigateUp()
                            }
                        }
                    )
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