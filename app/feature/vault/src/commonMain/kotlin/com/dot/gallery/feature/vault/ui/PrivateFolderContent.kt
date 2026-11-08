/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.feature.vault.ui

import androidx.compose.animation.AnimatedContentScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import com.dot.gallery.core.Constants.cellsList
import com.dot.gallery.core.LocalMediaSelector
import com.dot.gallery.core.presentation.components.EmptyMedia
import com.dot.gallery.core.presentation.components.GridPinchZoomLayout
import com.dot.gallery.core.presentation.components.MediaGridView
import com.dot.gallery.core.presentation.components.rememberGridPinchZoomState
import com.dot.gallery.core.presentation.util.LocalHazeState
import com.dot.gallery.core.presentation.util.selectedMedia
import com.dot.gallery.data.model.MediaMetadataState
import com.dot.gallery.domain.model.MediaState
import com.dot.gallery.domain.model.UriMedia
import com.dot.gallery.domain.settings.GROUP_NORMAL
import com.dot.gallery.feature.vault.domain.model.VaultAuthType
import com.dot.gallery.feature.vault.domain.model.VaultGateAction
import com.dot.gallery.feature.vault.domain.model.VerifyResult
import com.dot.gallery.feature.vault.ui.components.VaultDeviceAuthBridge
import com.dot.gallery.feature.vault.ui.components.VaultPasswordUnlockDialog
import dev.chrisbanes.haze.hazeSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource
import com.dot.gallery.feature.vault.generated.resources.Res
import com.dot.gallery.feature.vault.generated.resources.private_folder_security_title
import com.dot.gallery.feature.vault.generated.resources.security_private_folder
import com.dot.gallery.feature.vault.generated.resources.vault_locked_out
import com.dot.gallery.feature.vault.generated.resources.vault_wrong_password_attempts
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Close
import androidx.compose.runtime.CompositionLocalProvider
import com.dot.gallery.core.presentation.components.GalleryPageTopBar
import com.dot.gallery.core.presentation.components.GalleryPageTopBarAction
import com.dot.gallery.core.presentation.components.galleryDetailTopBarHeight
import com.dot.gallery.core.presentation.components.rememberScrolledUnderTopBar
import dev.chrisbanes.haze.HazePerformanceMode
import dev.chrisbanes.haze.LocalHazePerformanceMode

/**
 * Private-folder screen: a memory-only auth gate (device credential via
 * [deviceAuth] or custom secret via [verifyPassword]) in front of the
 * pinch-zoom media grid.
 *
 * Platform seams:
 * - [resolveGateAction] resolves the configured folder mode into a
 *   [VaultGateAction] — the host wraps its credential store plus
 *   [com.dot.gallery.feature.vault.domain.util.VaultGatePolicy]; a Deny result
 *   exits through [onNavigateUp].
 * - [deviceAuth] bridges to the platform device-credential prompt
 *   (BiometricPrompt on Android); its one-shot [VaultDeviceAuthBridge.outcome]
 *   is consumed here.
 * - [lifecycleOwner] is the process lifecycle (`ProcessLifecycleOwner.get()` on
 *   Android) — ON_STOP revokes the authorization.
 * - [mediaStateProvider] is invoked only after authentication so the host's
 *   private-folder scan never starts before unlock.
 * - [gridSizeIndex] is the persisted grid column count (the app keeps the
 *   orientation-aware SharedPreferences state).
 * - [selectionSheet] hosts the app's SelectionSheet overlay; placed in the root
 *   [Box] with `Alignment.BottomEnd` semantics left to the caller's modifier.
 */
@OptIn(
    ExperimentalSharedTransitionApi::class,
    ExperimentalMaterial3Api::class,
    ExperimentalFoundationApi::class
)
@Composable
fun PrivateFolderContent(
    paddingValues: PaddingValues,
    isScrolling: MutableState<Boolean>,
    metadataState: State<MediaMetadataState>,
    gridSizeIndex: MutableState<Int>,
    privateFolderAlbumId: Long,
    lifecycleOwner: LifecycleOwner,
    deviceAuth: VaultDeviceAuthBridge,
    resolveGateAction: suspend () -> VaultGateAction,
    verifyPassword: suspend (String) -> VerifyResult,
    onNavigateUp: () -> Unit,
    onOpenSecuritySettings: () -> Unit,
    onMediaClick: (UriMedia) -> Unit,
    mediaStateProvider: @Composable () -> State<MediaState<UriMedia>>,
    modifier: Modifier = Modifier,
    selectionSheet: @Composable BoxScope.(MediaState<UriMedia>, SnapshotStateList<UriMedia>) -> Unit = { _, _ -> },
    sharedTransitionScope: SharedTransitionScope,
    animatedContentScope: AnimatedContentScope,
) {
    val scope = rememberCoroutineScope()

    // Authorization is intentionally memory-only and is revoked when the app stops.
    var isAuthenticated by remember { mutableStateOf(false) }
    var authType by remember { mutableStateOf<VaultAuthType?>(null) }
    var showPasswordDialog by remember { mutableStateOf(false) }
    var passwordError by remember { mutableStateOf<String?>(null) }
    var authEpoch by remember { mutableStateOf(0) }
    var authRequest by remember { mutableStateOf(0) }
    var reauthenticateOnStart by remember { mutableStateOf(false) }
    var deviceAuthPending by remember { mutableStateOf(false) }

    // Device-credential prompt result, delivered one-shot through the bridge.
    val deviceOutcome = deviceAuth.outcome
    LaunchedEffect(deviceOutcome) {
        when (deviceOutcome) {
            true -> {
                deviceAuth.consumeOutcome()
                if (deviceAuthPending) isAuthenticated = true
                deviceAuthPending = false
            }

            false -> {
                deviceAuth.consumeOutcome()
                val wasPending = deviceAuthPending
                deviceAuthPending = false
                if (wasPending) onNavigateUp()
            }

            null -> Unit
        }
    }

    OnLifecycleEvent(lifecycleOwner = lifecycleOwner) { event ->
        when (event) {
            Lifecycle.Event.ON_STOP -> {
                authEpoch += 1
                isAuthenticated = false
                deviceAuthPending = false
                showPasswordDialog = false
                passwordError = null
                authType = null
                deviceAuth.cancel()
                reauthenticateOnStart = true
            }
            Lifecycle.Event.ON_START -> {
                if (reauthenticateOnStart) {
                    reauthenticateOnStart = false
                    authRequest += 1
                }
            }
            else -> Unit
        }
    }

    LaunchedEffect(authRequest) {
        if (isAuthenticated) return@LaunchedEffect
        val attemptEpoch = authEpoch
        when (val action = resolveGateAction()) {
            VaultGateAction.Reveal -> isAuthenticated = attemptEpoch == authEpoch
            VaultGateAction.ChallengeDevice -> {
                if (attemptEpoch == authEpoch) {
                    deviceAuthPending = true
                    deviceAuth.authenticate()
                } else {
                    onNavigateUp()
                }
            }
            is VaultGateAction.ChallengeCustom -> {
                if (attemptEpoch != authEpoch) return@LaunchedEffect
                authType = action.authType
                showPasswordDialog = true
            }
            VaultGateAction.Deny -> onNavigateUp()
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
                    onNavigateUp()
                },
                onSubmit = { secret ->
                    val attemptEpoch = authEpoch
                    scope.launch {
                        val result = verifyPassword(secret)
                        if (attemptEpoch != authEpoch || !showPasswordDialog) return@launch
                        when (result) {
                            is VerifyResult.Success -> {
                                showPasswordDialog = false
                                passwordError = null
                                isAuthenticated = true
                            }
                            is VerifyResult.Failed -> {
                                passwordError = getString(
                                    Res.string.vault_wrong_password_attempts,
                                    result.attemptsLeft
                                )
                            }
                            is VerifyResult.LockedOut -> {
                                val seconds = result.cooldownMs / 1000
                                passwordError = getString(
                                    Res.string.vault_locked_out,
                                    seconds
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

    val mediaState = mediaStateProvider()
    val selector = LocalMediaSelector.current
    val selectedMedia = selector.selectedMedia.collectAsState()

    var canScroll by rememberSaveable { mutableStateOf(true) }
    var lastCellIndex by gridSizeIndex

    val dpCacheWindow = LazyLayoutCacheWindow(ahead = 200.dp, behind = 100.dp)
    val pinchState = rememberGridPinchZoomState(
        cellsList = cellsList,
        initialCellsIndex = lastCellIndex,
        gridState = rememberLazyGridState(cacheWindow = dpCacheWindow)
    )

    LaunchedEffect(pinchState.isZooming) {
        withContext(Dispatchers.Default) {
            canScroll = !pinchState.isZooming
            lastCellIndex = cellsList.indexOf(pinchState.currentCells)
        }
    }

    Box(
        modifier = modifier.padding(
            start = paddingValues.calculateStartPadding(LocalLayoutDirection.current),
            end = paddingValues.calculateEndPadding(LocalLayoutDirection.current)
        )
    ) {
        // The grid scrolls under the bar, which turns into the blur bar once it does.
        val hazeState = LocalHazeState.current
        val topBarHeight = galleryDetailTopBarHeight()
        val isSelectionActive by selector.isSelectionActive.collectAsState()
        Box {
            GridPinchZoomLayout(
                state = pinchState,
                modifier = Modifier.hazeSource(hazeState),
                indicatorTopPadding = topBarHeight + 16.dp,
            ) {
                MediaGridView(
                    mediaState = mediaState,
                    metadataState = metadataState,
                    allowSelection = true,
                    showSearchBar = false,
                    // Sticky date headers clash with the blur bar.
                    enableStickyHeaders = false,
                    paddingValues = PaddingValues(
                        top = topBarHeight,
                        bottom = paddingValues.calculateBottomPadding() + 128.dp
                    ),
                    canScroll = canScroll,
                    allowHeaders = true,
                    groupMethod = GROUP_NORMAL,
                    isScrolling = isScrolling,
                    emptyContent = { EmptyMedia() },
                    sharedTransitionScope = sharedTransitionScope,
                    animatedContentScope = animatedContentScope,
                    onMediaClick = onMediaClick,
                )
            }
            CompositionLocalProvider(LocalHazePerformanceMode provides HazePerformanceMode.Performance) {
                GalleryPageTopBar(
                    title = stringResource(Res.string.security_private_folder),
                    count = mediaState.value.media.size.takeIf { it > 0 }?.toString(),
                    onBack = if (isSelectionActive) selector::clearSelection else null,
                    navigationIcon = if (isSelectionActive) Icons.Outlined.Close else Icons.AutoMirrored.Filled.ArrowBack,
                    hazeState = hazeState,
                    scrolled = rememberScrolledUnderTopBar(pinchState.gridState),
                    actions = {
                        GalleryPageTopBarAction(
                            icon = Icons.Outlined.Lock,
                            contentDescription = stringResource(Res.string.private_folder_security_title),
                            onClick = onOpenSecuritySettings,
                        )
                    },
                )
            }
        }
        val selectedMediaList by selectedMedia(
            media = mediaState.value.media,
            selectedSet = selectedMedia
        )
        selectionSheet(mediaState.value, selectedMediaList)
    }
}

@Composable
private fun OnLifecycleEvent(
    lifecycleOwner: LifecycleOwner,
    onEvent: (event: Lifecycle.Event) -> Unit
) {
    val eventHandler = rememberUpdatedState(onEvent)
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            eventHandler.value(event)
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }
}
