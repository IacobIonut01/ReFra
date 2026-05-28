/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.feature_node.presentation.privatefolder

import androidx.compose.animation.AnimatedContentScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.layout.LazyLayoutCacheWindow
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dot.gallery.R
import com.dot.gallery.core.Constants.cellsList
import com.dot.gallery.core.LocalEventHandler
import com.dot.gallery.core.LocalMediaSelector
import com.dot.gallery.core.Settings.Misc.rememberGridSize
import com.dot.gallery.core.navigate
import com.dot.gallery.core.navigateUp
import com.dot.gallery.core.presentation.components.EmptyMedia
import com.dot.gallery.core.presentation.components.NavigationButton
import com.dot.gallery.core.presentation.components.SelectionSheet
import com.dot.gallery.feature_node.domain.model.Media
import com.dot.gallery.feature_node.domain.model.MediaMetadataState
import com.dot.gallery.feature_node.presentation.common.components.GridPinchZoomLayout
import com.dot.gallery.feature_node.presentation.common.components.MediaGridView
import com.dot.gallery.feature_node.presentation.common.components.TwoLinedDateToolbarTitle
import com.dot.gallery.feature_node.presentation.common.components.rememberGridPinchZoomState
import com.dot.gallery.feature_node.presentation.util.LocalHazeState
import com.dot.gallery.feature_node.presentation.util.Screen
import com.dot.gallery.feature_node.presentation.util.selectedMedia
import com.dot.gallery.feature_node.presentation.vault.components.VaultPasswordUnlockDialog
import com.dot.gallery.feature_node.presentation.vault.utils.GateMode
import com.dot.gallery.feature_node.presentation.vault.utils.VaultAuthType
import com.dot.gallery.feature_node.presentation.vault.utils.VaultPasswordManager
import com.dot.gallery.feature_node.presentation.vault.utils.VerifyResult
import com.dot.gallery.feature_node.presentation.vault.utils.rememberBiometricState
import dev.chrisbanes.haze.LocalHazeStyle
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalSharedTransitionApi::class, ExperimentalMaterial3Api::class,
    androidx.compose.foundation.ExperimentalFoundationApi::class
)
@Composable
fun PrivateFolderScreen(
    paddingValues: PaddingValues,
    isScrolling: MutableState<Boolean>,
    metadataState: State<MediaMetadataState>,
    sharedTransitionScope: SharedTransitionScope,
    animatedContentScope: AnimatedContentScope,
) {
    val context = LocalContext.current
    val eventHandler = LocalEventHandler.current
    val scope = rememberCoroutineScope()

    var isAuthenticated by rememberSaveable { mutableStateOf(false) }
    var authType by remember { mutableStateOf<VaultAuthType?>(null) }
    var showPasswordDialog by remember { mutableStateOf(false) }
    var passwordError by remember { mutableStateOf<String?>(null) }

    val wrongPasswordStr = stringResource(R.string.vault_wrong_password_attempts)
    val lockedOutStr = stringResource(R.string.vault_locked_out)

    val biometricState = rememberBiometricState(
        title = stringResource(R.string.private_folder_unlock),
        subtitle = stringResource(R.string.private_folder_unlock_subtitle),
        onSuccess = { isAuthenticated = true },
        onFailed = { eventHandler.navigateUp() }
    )

    LaunchedEffect(Unit) {
        if (isAuthenticated) return@LaunchedEffect
        when (VaultPasswordManager.getPrivateFolderMode(context)) {
            GateMode.NONE -> isAuthenticated = true
            GateMode.DEVICE -> {
                if (biometricState.isSupported) {
                    biometricState.authenticate()
                } else {
                    isAuthenticated = true
                }
            }
            GateMode.CUSTOM -> {
                val type = VaultPasswordManager.getAuthType(
                    context, VaultPasswordManager.PRIVATE_FOLDER_UUID
                )
                if (type != null) {
                    authType = type
                    showPasswordDialog = true
                } else {
                    isAuthenticated = true
                }
            }
        }
    }

    if (showPasswordDialog && !isAuthenticated) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surface)
        ) {
            VaultPasswordUnlockDialog(
                authType = authType,
                onDismiss = {
                    showPasswordDialog = false
                    passwordError = null
                    eventHandler.navigateUp()
                },
                onSubmit = { secret ->
                    scope.launch {
                        val result = VaultPasswordManager.verifyPassword(
                            context, VaultPasswordManager.PRIVATE_FOLDER_UUID, secret
                        )
                        when (result) {
                            is VerifyResult.Success -> {
                                showPasswordDialog = false
                                passwordError = null
                                isAuthenticated = true
                            }
                            is VerifyResult.Failed -> {
                                passwordError = String.format(
                                    wrongPasswordStr,
                                    result.attemptsLeft
                                )
                            }
                            is VerifyResult.LockedOut -> {
                                val seconds = result.cooldownMs / 1000
                                passwordError = String.format(
                                    lockedOutStr, seconds
                                )
                            }
                        }
                    }
                },
                errorMessage = passwordError
            )
        }
        return
    }

    if (!isAuthenticated) return

    val viewModel = hiltViewModel<PrivateFolderViewModel>()
    val mediaState = viewModel.mediaState.collectAsStateWithLifecycle()
    val selector = LocalMediaSelector.current
    val selectedMedia = selector.selectedMedia.collectAsStateWithLifecycle()

    var canScroll by rememberSaveable { mutableStateOf(true) }
    var lastCellIndex by rememberGridSize()

    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(
        state = rememberTopAppBarState(),
        canScroll = { canScroll },
        flingAnimationSpec = null
    )

    val dpCacheWindow = LazyLayoutCacheWindow(ahead = 200.dp, behind = 100.dp)
    val pinchState = rememberGridPinchZoomState(
        cellsList = cellsList,
        initialCellsIndex = lastCellIndex,
        gridState = rememberLazyGridState(cacheWindow = dpCacheWindow)
    )

    LaunchedEffect(pinchState.isZooming) {
        withContext(Dispatchers.IO) {
            canScroll = !pinchState.isZooming
            lastCellIndex = cellsList.indexOf(pinchState.currentCells)
        }
    }

    Box(
        modifier = Modifier.padding(
            start = paddingValues.calculateStartPadding(LocalLayoutDirection.current),
            end = paddingValues.calculateEndPadding(LocalLayoutDirection.current)
        )
    ) {
        Scaffold(
            modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
            topBar = {
                LargeTopAppBar(
                    modifier = Modifier.hazeEffect(
                        state = LocalHazeState.current,
                        style = LocalHazeStyle.current
                    ),
                    title = {
                        TwoLinedDateToolbarTitle(
                            albumName = stringResource(R.string.security_private_folder),
                            dateHeader = mediaState.value.dateHeader
                        )
                    },
                    navigationIcon = {
                        NavigationButton(
                            albumId = PrivateFolderViewModel.PRIVATE_FOLDER_ALBUM_ID,
                            target = null,
                            alwaysGoBack = true,
                        )
                    },
                    actions = {
                        IconButton(onClick = {
                            eventHandler.navigate(Screen.PrivateFolderSecurityScreen())
                        }) {
                            Icon(
                                imageVector = Icons.Outlined.Lock,
                                contentDescription = stringResource(R.string.private_folder_security_title)
                            )
                        }
                    },
                    scrollBehavior = scrollBehavior,
                    colors = TopAppBarDefaults.topAppBarColors(
                        scrolledContainerColor = MaterialTheme.colorScheme.surface,
                    ),
                )
            }
        ) { innerPadding ->
            GridPinchZoomLayout(
                state = pinchState,
                modifier = Modifier.hazeSource(LocalHazeState.current),
                indicatorTopPadding = innerPadding.calculateTopPadding() + 16.dp,
            ) {
                MediaGridView(
                    mediaState = mediaState,
                    metadataState = metadataState,
                    allowSelection = true,
                    showSearchBar = false,
                    enableStickyHeaders = true,
                    paddingValues = PaddingValues(
                        top = innerPadding.calculateTopPadding(),
                        bottom = paddingValues.calculateBottomPadding() + 128.dp
                    ),
                    canScroll = canScroll,
                    allowHeaders = true,
                    showMonthlyHeader = false,
                    isScrolling = isScrolling,
                    emptyContent = { EmptyMedia() },
                    sharedTransitionScope = sharedTransitionScope,
                    animatedContentScope = animatedContentScope
                ) {
                    eventHandler.navigate(
                        Screen.MediaViewScreen.idAndAlbum(
                            it.id,
                            PrivateFolderViewModel.PRIVATE_FOLDER_ALBUM_ID
                        )
                    )
                }
            }
        }
        val selectedMediaList by selectedMedia(
            media = mediaState.value.media,
            selectedSet = selectedMedia
        )
        SelectionSheet(
            modifier = Modifier.align(Alignment.BottomEnd),
            allMedia = mediaState.value,
            selectedMedia = selectedMediaList
        )
    }
}
