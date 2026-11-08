/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.feature.vault.ui

import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import com.dot.gallery.core.presentation.components.galleryDetailPaneFits
import com.dot.gallery.core.presentation.components.GalleryPaneLayout
import androidx.compose.animation.AnimatedContentScope
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.DeferredAnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.ExperimentalDeferredTransitionApi
import androidx.compose.animation.core.rememberTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.backhandler.BackHandler
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.dot.gallery.core.Constants.Animation.enterAnimation
import com.dot.gallery.core.Constants.Animation.exitAnimation
import com.dot.gallery.core.Constants.Animation.navigateInAnimation
import com.dot.gallery.core.Constants.Animation.navigateUpAnimation
import com.dot.gallery.core.LocalEventHandler
import com.dot.gallery.core.LocalViewerDismissBridge
import com.dot.gallery.core.ViewerDismissBridge
import com.dot.gallery.core.navigateUp
import com.dot.gallery.core.presentation.util.LocalHazeState
import com.dot.gallery.core.ui.settings.rememberPreference
import com.dot.gallery.domain.model.UIEvent
import com.dot.gallery.domain.model.Vault
import com.dot.gallery.domain.model.VaultState
import com.dot.gallery.domain.settings.SettingsKeys
import com.dot.gallery.domain.util.EventHandler
import com.dot.gallery.feature.vault.domain.model.GateMode
import com.dot.gallery.feature.vault.domain.model.VaultAuthType
import com.dot.gallery.feature.vault.domain.model.VaultCredentialStatus
import com.dot.gallery.feature.vault.domain.model.VaultGateAction
import com.dot.gallery.feature.vault.domain.model.VerifyResult
import com.dot.gallery.feature.vault.ui.components.VaultDeviceAuthBridge
import com.dot.gallery.feature.vault.ui.components.VaultPasswordUnlockDialog
import dev.chrisbanes.haze.hazeSource
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource
import kotlin.uuid.Uuid
import com.dot.gallery.feature.vault.generated.resources.Res
import com.dot.gallery.feature.vault.generated.resources.vault_locked_out
import com.dot.gallery.feature.vault.generated.resources.vault_unlock_list
import com.dot.gallery.feature.vault.generated.resources.vault_unlock_named
import com.dot.gallery.feature.vault.generated.resources.vault_wrong_password_attempts

/**
 * Vault screen: an internal navigation flow (loading → gate auth → vault
 * selector → setup wizard → media display) guarded by two independent
 * memory-only authorizations — the *gate* covering the vault list itself and
 * per-vault auth covering an open vault. Both are revoked when the app process
 * stops ([lifecycleOwner] ON_STOP) and the gate is re-armed on ON_START.
 *
 * Platform seams (mirroring `PrivateFolderContent`):
 * - [resolveGateAction] maps the stored gate mode + credential status into a
 *   [VaultGateAction] — the host wraps its credential store with
 *   [com.dot.gallery.feature.vault.domain.util.VaultGatePolicy]; Deny exits via
 *   the parent event handler's navigate-up.
 * - [gateDeviceAuth] / [vaultDeviceAuth] bridge to the platform
 *   device-credential prompt (BiometricPrompt on Android); their one-shot
 *   [VaultDeviceAuthBridge.outcome] is consumed here.
 * - [eventHandler] is a fresh handler instance created by the host (the app's
 *   `DefaultEventHandler`) — it is re-provided as [LocalEventHandler] for the
 *   vault sub-screens and its navigation actions are wired to the internal
 *   NavController; follow-theme/navigation-bar events forward to the parent
 *   handler so the app shell still receives them.
 * - [lifecycleOwner] is the process lifecycle (`ProcessLifecycleOwner.get()` on
 *   Android) — ON_STOP revokes both authorizations.
 * - [onSystemBarStyleChange] receives the resolved system-bars dark-content
 *   flag (`darkTheme || current route is not the media viewer`) so the host can
 *   re-apply edge-to-edge styling.
 * - [vaultDisplay] hosts the app's `VaultDisplay` (delegating to
 *   [VaultDisplayContent]) inside the gate+vault-authenticated destination. Its
 *   `requestAddMedia` flag is set when the setup's Ready page asks to add photos; the host
 *   opens its media picker and calls `onAddMediaRequestHandled`.
 * - [readGateMode] reads the stored vault-list lock for the list summary and Ready page;
 *   [openSecuritySettings] opens the system screen-lock settings from the setup notice.
 * - [mediaViewer] hosts the app's `MediaViewScreenRoute` — both as the
 *   [VaultRoute.EncryptedMediaViewScreen] destination and inside the deferred
 *   swipe-dismiss overlay driven by [ViewerDismissBridge] (`:core:ui`).
 * - [viewerExitVisual] / [viewerFlightLayer] host the app's non-interactive
 *   exit stand-in and the committed return-flight layer for that overlay.
 */
@OptIn(
    ExperimentalSharedTransitionApi::class, ExperimentalDeferredTransitionApi::class,
    ExperimentalComposeUiApi::class
)
@Composable
fun VaultContent(
    vaultState: State<VaultState>,
    currentVault: MutableStateFlow<Vault?>,
    setVault: (vault: Vault, onFailed: (reason: String) -> Unit, onSuccess: () -> Unit) -> Unit,
    eventHandler: EventHandler,
    lifecycleOwner: LifecycleOwner,
    gateDeviceAuth: VaultDeviceAuthBridge,
    vaultDeviceAuth: VaultDeviceAuthBridge,
    deviceSecurityAvailable: Boolean,
    resolveGateAction: suspend () -> VaultGateAction,
    shouldSkipVaultAuth: suspend () -> Boolean,
    vaultCredentialStatus: suspend (Vault) -> VaultCredentialStatus,
    verifyGateSecret: suspend (String) -> VerifyResult,
    verifyVaultSecret: suspend (Vault, String) -> VerifyResult,
    readLockAllVaults: suspend () -> Boolean,
    writeGateMode: suspend (GateMode) -> Unit,
    writeGateSecret: suspend (type: VaultAuthType, secret: String) -> Unit,
    writeVaultSecret: suspend (vault: Vault, type: VaultAuthType, secret: String) -> Unit,
    writeLockAllVaults: suspend (Boolean) -> Unit,
    onSystemBarStyleChange: (darkIcons: Boolean) -> Unit,
    modifier: Modifier = Modifier,
    readGateMode: suspend () -> GateMode = { GateMode.NONE },
    openSecuritySettings: (() -> Unit)? = null,
    vaultDisplay: @Composable (
        sharedTransitionScope: SharedTransitionScope,
        animatedContentScope: AnimatedContentScope,
        onNavigateUp: () -> Unit,
        onMediaClick: (Long) -> Unit,
        onVaultDeleted: () -> Unit,
        onAuthenticateVault: (Vault) -> Unit,
        requestAddMedia: Boolean,
        onAddMediaRequestHandled: () -> Unit,
    ) -> Unit,
    mediaViewer: @Composable (
        sharedTransitionScope: SharedTransitionScope,
        animatedVisibilityScope: AnimatedVisibilityScope,
        mediaId: Long,
        onDismissRequest: (() -> Unit)?,
        viewerSessionKey: Int,
        dismissBridge: ViewerDismissBridge?,
    ) -> Unit,
    viewerExitVisual: @Composable (ViewerDismissBridge) -> Unit,
    viewerFlightLayer: @Composable (ViewerDismissBridge) -> Unit,
) {
    val parentEventHandler = LocalEventHandler.current
    val navController = rememberNavController()
    var addNewVault by remember { mutableStateOf(false) }
    var requestAddMedia by remember { mutableStateOf(false) }
    // Total wizard pages: name, unlock method, lock entry and Ready, plus the vault-list lock
    // when the first vault is created.
    var setupTotalSteps by remember { mutableIntStateOf(4) }
    var setupIncludedGate by remember { mutableStateOf(false) }

    LaunchedEffect(eventHandler) {
        eventHandler.navigateAction = {
            navController.navigate(it) {
                launchSingleTop = true
                restoreState = true
            }
        }
        eventHandler.navigateUpAction = navController::navigateUp
    }
    LaunchedEffect(eventHandler) {
        eventHandler.updaterFlow.collectLatest { event ->
            when (event) {
                is UIEvent.NavigationRouteEvent -> eventHandler.navigateAction(event.route)
                UIEvent.NavigationUpEvent -> eventHandler.navigateUpAction()
                is UIEvent.SetFollowThemeEvent -> parentEventHandler.setFollowThemeAction(event.followTheme)
                is UIEvent.ToggleNavigationBarEvent -> parentEventHandler.toggleNavigationBarAction(
                    event.isVisible
                )

                UIEvent.UpdateDatabase -> {}
            }
        }
    }
    CompositionLocalProvider(
        LocalEventHandler provides eventHandler
    ) {
        // Authorization is intentionally memory-only and is revoked when the app stops.
        var isAuthenticated by remember { mutableStateOf(false) }
        var isGateAuthenticated by remember { mutableStateOf(false) }
        var authEpoch by remember { mutableStateOf(0) }
        var gateAuthRequest by remember { mutableStateOf(0) }
        var reauthenticateOnStart by remember { mutableStateOf(false) }
        var isInitialSetupFlow by remember { mutableStateOf(false) }
        var vaultViewerMediaId by remember { mutableStateOf<Long?>(null) }
        var retainedVaultViewerMediaId by remember { mutableStateOf<Long?>(null) }
        var vaultViewerOpenCount by remember { mutableIntStateOf(0) }
        // Deferred transition for the vault media overlay. Swipe-dismiss does not use the
        // transition's MutableTransform — the viewer applies its drag offset to an inner card
        // so the gesture handler's coordinate space never moves; committing runs a root-level
        // return flight while this container's plain fade-out runs underneath.
        val vaultViewerDismissBridge = remember { ViewerDismissBridge() }
        val vaultViewerVisibility = vaultViewerDismissBridge.transitionState
        // animateTo (not a bare targetState write — its setter is internal) resolves any
        // still-armed deferred gesture into the matching transition. Guarded so a recomposition
        // during an armed drag (targetState still true behind the pending false) doesn't cancel
        // the defer.
        if (vaultViewerVisibility.targetState != (vaultViewerMediaId != null)) {
            vaultViewerVisibility.animateTo(vaultViewerMediaId != null)
        }
        val vaultViewerTransition = rememberTransition(vaultViewerVisibility)
        val viewerAnimationsEnabled by rememberPreference(SettingsKeys.sharedElements)
        val vaultViewerActive = vaultViewerVisibility.currentState || vaultViewerVisibility.targetState
        val vaultDualPane by rememberPreference(SettingsKeys.galleryDualPane)
        var vaultPaneFraction by rememberPreference(SettingsKeys.galleryPaneFraction)
        val windowSize = LocalWindowInfo.current.containerSize
        val vaultSplitEligible = vaultDualPane && with(LocalDensity.current) {
            galleryDetailPaneFits(windowSize.width.toDp(), windowSize.height.toDp())
        }
        // Safety net: if the overlay disappears while a drag still holds the defer armed, release
        // the source cell's shared-element suppression.
        if (!vaultViewerActive) {
            vaultViewerDismissBridge.suppressedElementKey = null
        }
        // An enter flight only makes sense while the overlay is still opening — a mid-flight
        // dismiss drops the morph rather than letting it finish over the exiting container.
        if (vaultViewerMediaId == null) {
            vaultViewerDismissBridge.flight?.takeIf { it.isEnter }?.let {
                vaultViewerDismissBridge.flight = null
            }
        }
        LaunchedEffect(vaultViewerMediaId, isAuthenticated, isGateAuthenticated) {
            if (!isAuthenticated || !isGateAuthenticated) {
                vaultViewerMediaId = null
            } else if (vaultViewerMediaId != null) {
                retainedVaultViewerMediaId = vaultViewerMediaId
            }
        }

        // Per-vault auth state
        var showPasswordDialog by remember { mutableStateOf(false) }
        var passwordError by remember { mutableStateOf<String?>(null) }
        var detectedAuthType by remember { mutableStateOf<VaultAuthType?>(null) }
        var pendingAuthVault by remember { mutableStateOf<Vault?>(null) }

        // Gate auth state
        var showGatePasswordDialog by remember { mutableStateOf(false) }
        var gatePasswordError by remember { mutableStateOf<String?>(null) }
        var gateAuthType by remember { mutableStateOf<VaultAuthType?>(null) }
        var gateDeviceAuthPending by remember { mutableStateOf(false) }

        val scope = rememberCoroutineScope()

        /** Navigate to VaultDisplay after successful per-vault auth */
        fun onVaultAuthSuccess(vault: Vault) {
            if (!isGateAuthenticated) return
            isAuthenticated = true
            currentVault.value = vault
            setVault(
                vault,
                { /* host logs */ },
                { /* host logs */ }
            )
            val currentRoute = navController.currentBackStackEntry?.destination?.route
            if (currentRoute?.contains("VaultDisplay") != true) {
                navController.navigate(VaultRoute.VaultDisplay) {
                    popUpTo(VaultRoute.VaultSelect) { inclusive = false }
                    launchSingleTop = true
                }
            }
        }

        // Per-vault device-credential prompt result, delivered one-shot through the bridge.
        val vaultDeviceOutcome = vaultDeviceAuth.outcome
        LaunchedEffect(vaultDeviceOutcome) {
            when (vaultDeviceOutcome) {
                true -> {
                    vaultDeviceAuth.consumeOutcome()
                    pendingAuthVault?.let { onVaultAuthSuccess(it) }
                    pendingAuthVault = null
                }

                false -> {
                    vaultDeviceAuth.consumeOutcome()
                    pendingAuthVault = null
                    requestAddMedia = false
                }

                null -> Unit
            }
        }

        /** Authenticate a specific vault: custom password dialog or device security. */
        fun authenticateVault(vault: Vault) {
            val attemptEpoch = authEpoch
            scope.launch {
                pendingAuthVault = vault
                if (shouldSkipVaultAuth()) {
                    // One lock covers every vault — the satisfied gate is enough.
                    if (attemptEpoch != authEpoch || !isGateAuthenticated) return@launch
                    pendingAuthVault = null
                    onVaultAuthSuccess(vault)
                    return@launch
                }
                val status = vaultCredentialStatus(vault)
                if (attemptEpoch != authEpoch || !isGateAuthenticated) return@launch
                when (status) {
                    is VaultCredentialStatus.Valid -> {
                        detectedAuthType = status.authType
                        showPasswordDialog = true
                    }
                    VaultCredentialStatus.Missing -> {
                        detectedAuthType = null
                        if (vaultDeviceAuth.isSupported) {
                            vaultDeviceAuth.authenticate()
                        } else {
                            pendingAuthVault = null
                        }
                    }
                    VaultCredentialStatus.Corrupt -> {
                        detectedAuthType = null
                        pendingAuthVault = null
                    }
                }
            }
        }

        fun onGateAuthSuccess() {
            isInitialSetupFlow = false
            isGateAuthenticated = true
            showGatePasswordDialog = false
            gatePasswordError = null
            navController.navigate(VaultRoute.VaultSelect) {
                popUpTo(VaultRoute.VaultGateAuth) { inclusive = true }
                launchSingleTop = true
            }
        }

        // Gate device-credential prompt result, delivered one-shot through the bridge.
        val gateDeviceOutcome = gateDeviceAuth.outcome
        LaunchedEffect(gateDeviceOutcome) {
            when (gateDeviceOutcome) {
                true -> {
                    gateDeviceAuth.consumeOutcome()
                    if (gateDeviceAuthPending) onGateAuthSuccess()
                    gateDeviceAuthPending = false
                }

                false -> {
                    gateDeviceAuth.consumeOutcome()
                    val wasPending = gateDeviceAuthPending
                    gateDeviceAuthPending = false
                    if (wasPending) parentEventHandler.navigateUp()
                }

                null -> Unit
            }
        }

        /** Trigger gate authentication through the host-resolved [VaultGateAction]. */
        fun triggerGateAuth() {
            val attemptEpoch = authEpoch
            scope.launch {
                val action = resolveGateAction()
                if (attemptEpoch != authEpoch) return@launch
                when (action) {
                    VaultGateAction.Reveal -> onGateAuthSuccess()
                    VaultGateAction.ChallengeDevice -> {
                        gateDeviceAuthPending = true
                        gateDeviceAuth.authenticate()
                    }
                    is VaultGateAction.ChallengeCustom -> {
                        gateAuthType = action.authType
                        showGatePasswordDialog = true
                    }
                    VaultGateAction.Deny -> parentEventHandler.navigateUp()
                }
            }
        }

        OnLifecycleEvent(lifecycleOwner = lifecycleOwner) { event ->
            when (event) {
                Lifecycle.Event.ON_STOP -> {
                    authEpoch += 1
                    isAuthenticated = false
                    isGateAuthenticated = false
                    gateDeviceAuthPending = false
                    showPasswordDialog = false
                    showGatePasswordDialog = false
                    passwordError = null
                    gatePasswordError = null
                    detectedAuthType = null
                    gateAuthType = null
                    pendingAuthVault = null
                    requestAddMedia = false
                    if (!isInitialSetupFlow) currentVault.value = null
                    vaultDeviceAuth.cancel()
                    gateDeviceAuth.cancel()
                    reauthenticateOnStart =
                        vaultState.value.vaults.isNotEmpty() && !isInitialSetupFlow
                }
                Lifecycle.Event.ON_START -> {
                    if (reauthenticateOnStart) {
                        reauthenticateOnStart = false
                        gateAuthRequest += 1
                        navController.navigate(VaultRoute.VaultGateAuth) {
                            popUpTo(VaultRoute.VaultSelect) { inclusive = true }
                            launchSingleTop = true
                        }
                    }
                }
                else -> Unit
            }
        }

        val navBackStackEntry by navController.currentBackStackEntryAsState()
        val systemBarFollowThemeState = rememberSaveable(navBackStackEntry) {
            mutableStateOf(
                navBackStackEntry?.destination?.route?.contains("EncryptedMediaViewScreen") == false
            )
        }
        val forcedTheme by rememberPreference(SettingsKeys.forceTheme)
        val localDarkTheme by rememberPreference(SettingsKeys.darkMode)
        val systemDarkTheme = isSystemInDarkTheme()
        val darkTheme by remember(forcedTheme, localDarkTheme, systemDarkTheme) {
            mutableStateOf(if (forcedTheme) localDarkTheme else systemDarkTheme)
        }
        val latestSystemBarStyleChange by rememberUpdatedState(onSystemBarStyleChange)
        LaunchedEffect(darkTheme, systemBarFollowThemeState.value) {
            latestSystemBarStyleChange(darkTheme || !systemBarFollowThemeState.value)
        }

        SharedTransitionLayout {
            CompositionLocalProvider(LocalViewerDismissBridge provides vaultViewerDismissBridge) {
            Box(modifier = Modifier.fillMaxSize()) {
            // Wide windows keep the vault grid beside the open item, like the main gallery.
            GalleryPaneLayout(
                split = vaultSplitEligible && vaultViewerActive,
                splitRequested = vaultSplitEligible && vaultViewerMediaId != null,
                masterFraction = vaultPaneFraction,
                onMasterFractionChange = { vaultPaneFraction = it },
                masterContent = {
                    NavHost(
                        modifier = Modifier
                            .fillMaxSize()
                            .then(
                                // A split viewer leaves the grid usable beside it.
                                if (vaultViewerActive && !vaultSplitEligible) Modifier.clearAndSetSemantics { }
                                else Modifier
                            ),
                        navController = navController,
                        startDestination = VaultRoute.LoadingScreen,
                        enterTransition = { navigateInAnimation },
                        exitTransition = { navigateUpAnimation },
                        popEnterTransition = { navigateInAnimation },
                        popExitTransition = { navigateUpAnimation }
                    ) {
                        composable<VaultRoute.LoadingScreen> {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator()
                            }
                            // One-time navigation when loading finishes
                            LaunchedEffect(vaultState.value.isLoading) {
                                if (!vaultState.value.isLoading) {
                                    val dest = if (vaultState.value.vaults.isEmpty()) {
                                        isInitialSetupFlow = true
                                        VaultRoute.VaultSetup
                                    } else {
                                        VaultRoute.VaultGateAuth
                                    }
                                    navController.navigate(dest) {
                                        popUpTo(VaultRoute.LoadingScreen) { inclusive = true }
                                        launchSingleTop = true
                                    }
                                }
                            }
                        }

                        composable<VaultRoute.VaultGateAuth> {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator()
                            }
                            LaunchedEffect(gateAuthRequest) {
                                if (isGateAuthenticated) {
                                    navController.navigate(VaultRoute.VaultSelect) {
                                        popUpTo(VaultRoute.VaultGateAuth) { inclusive = true }
                                        launchSingleTop = true
                                    }
                                } else {
                                    triggerGateAuth()
                                }
                            }
                        }

                        composable<VaultRoute.VaultSelect> {
                            if (isGateAuthenticated) {
                                val vaults = vaultState.value.vaults
                                var gateMode by remember { mutableStateOf<GateMode?>(null) }
                                var lockTypes by remember { mutableStateOf<Map<Uuid, VaultAuthType?>>(emptyMap()) }
                                LaunchedEffect(vaults) {
                                    gateMode = readGateMode()
                                    lockTypes = vaults.mapNotNull { vault ->
                                        when (val status = vaultCredentialStatus(vault)) {
                                            is VaultCredentialStatus.Valid -> vault.uuid to status.authType
                                            VaultCredentialStatus.Missing -> vault.uuid to null
                                            VaultCredentialStatus.Corrupt -> null
                                        }
                                    }.toMap()
                                }
                                VaultSelectContent(
                                    gateMode = gateMode,
                                    lockTypes = lockTypes,
                                    vaultState = vaultState.value,
                                    onVaultSelected = { vault -> authenticateVault(vault) },
                                    onCreateVault = {
                                        addNewVault = true
                                        navController.navigate(VaultRoute.VaultSetup) {
                                            launchSingleTop = true
                                        }
                                    },
                                    onChangeGateSecurity = {
                                        navController.navigate(VaultRoute.VaultGateSetup) {
                                            launchSingleTop = true
                                        }
                                    },
                                    onNavigateUp = { parentEventHandler.navigateUp() }
                                )
                            } else {
                                LaunchedEffect(Unit) {
                                    navController.navigate(VaultRoute.VaultGateAuth) {
                                        popUpTo(VaultRoute.VaultSelect) { inclusive = true }
                                        launchSingleTop = true
                                    }
                                }
                            }
                        }

                        composable<VaultRoute.VaultSetup> {
                            if (vaultState.value.vaults.isEmpty() || isInitialSetupFlow || isGateAuthenticated) {
                                LaunchedEffect(Unit) {
                                    setupIncludedGate = vaultState.value.vaults.isEmpty()
                                    setupTotalSteps = if (setupIncludedGate) 4 else 3
                                }
                                VaultSetupContent(
                                    modifier = Modifier.hazeSource(LocalHazeState.current),
                                    existingVaults = vaultState.value.vaults,
                                    deviceSecurityAvailable = deviceSecurityAvailable,
                                    totalSteps = if (vaultState.value.vaults.isEmpty()) 4 else 3,
                                    onOpenSecuritySettings = openSecuritySettings,
                                    onCancel = {
                                        addNewVault = false
                                        if (vaultState.value.vaults.isEmpty()) {
                                            parentEventHandler.navigateUp()
                                        } else {
                                            eventHandler.navigateUp()
                                        }
                                    },
                                    onCreate = { vault ->
                                        currentVault.value = vault
                                        navController.navigate(VaultRoute.VaultPasswordSetup) {
                                            popUpTo(VaultRoute.VaultSetup) { inclusive = true }
                                            launchSingleTop = true
                                        }
                                    }
                                )
                            } else {
                                LaunchedEffect(Unit) {
                                    navController.navigate(VaultRoute.VaultGateAuth) {
                                        popUpTo(VaultRoute.VaultSetup) { inclusive = true }
                                        launchSingleTop = true
                                    }
                                }
                            }
                        }
                        composable<VaultRoute.VaultPasswordSetup> {
                            if (isInitialSetupFlow || isGateAuthenticated) {
                                val currentVaultValue by currentVault.collectAsStateWithLifecycle()
                                val isFirstVault = vaultState.value.vaults.isEmpty()

                                fun afterPasswordSetup() {
                                    addNewVault = false
                                    val vault = currentVaultValue ?: return
                                    val first = isFirstVault
                                    setVault(
                                        vault,
                                        { /* name was already validated in VaultSetup */ },
                                        {
                                            if (first) {
                                                navController.navigate(VaultRoute.VaultGateSetup) {
                                                    popUpTo(VaultRoute.VaultPasswordSetup) { inclusive = true }
                                                    launchSingleTop = true
                                                }
                                            } else {
                                                navController.navigate(VaultRoute.VaultReady) {
                                                    popUpTo(VaultRoute.VaultPasswordSetup) { inclusive = true }
                                                    launchSingleTop = true
                                                }
                                            }
                                        }
                                    )
                                }

                                VaultPasswordSetupContent(
                                    modifier = Modifier.hazeSource(LocalHazeState.current),
                                    vaultName = currentVaultValue?.name.orEmpty(),
                                    totalSteps = setupTotalSteps,
                                    onCancel = {
                                        addNewVault = false
                                        currentVault.value = null
                                        if (vaultState.value.vaults.isEmpty()) {
                                            parentEventHandler.navigateUp()
                                        } else {
                                            navController.navigate(VaultRoute.VaultSelect) {
                                                popUpTo(VaultRoute.VaultPasswordSetup) { inclusive = true }
                                                launchSingleTop = true
                                            }
                                        }
                                    },
                                    onSkip = { afterPasswordSetup() },
                                    onSecretSet = { type, secret ->
                                        val vault = currentVaultValue
                                        if (vault != null) {
                                            scope.launch {
                                                writeVaultSecret(vault, type, secret)
                                                afterPasswordSetup()
                                            }
                                        }
                                    }
                                )
                            } else {
                                LaunchedEffect(Unit) {
                                    navController.navigate(VaultRoute.VaultGateAuth) {
                                        popUpTo(VaultRoute.VaultPasswordSetup) { inclusive = true }
                                        launchSingleTop = true
                                    }
                                }
                            }
                        }
                        composable<VaultRoute.VaultGateSetup> {
                            if (isInitialSetupFlow || isGateAuthenticated) {
                                var lockAllVaults by remember { mutableStateOf(false) }
                                LaunchedEffect(Unit) {
                                    lockAllVaults = readLockAllVaults()
                                }

                                // Part of the first-vault wizard (ends on the Ready page) or opened
                                // from the vault list's security action (returns to the list).
                                val fromSetup = remember { isInitialSetupFlow }
                                var storedMode by remember { mutableStateOf<GateMode?>(null) }
                                LaunchedEffect(Unit) {
                                    storedMode = if (fromSetup) GateMode.DEVICE else readGateMode()
                                }

                                fun afterGateSetup() {
                                    isInitialSetupFlow = false
                                    isGateAuthenticated = true
                                    navController.navigate(if (fromSetup) VaultRoute.VaultReady else VaultRoute.VaultSelect) {
                                        popUpTo(VaultRoute.VaultGateSetup) { inclusive = true }
                                        launchSingleTop = true
                                    }
                                }

                                val initialMode = storedMode
                                if (initialMode != null) VaultGateSetupContent(
                                    modifier = Modifier.hazeSource(LocalHazeState.current),
                                    lockAllVaults = lockAllVaults,
                                    initialMode = initialMode,
                                    step = if (fromSetup) 4 else null,
                                    totalSteps = 4,
                                    onBack = if (navController.previousBackStackEntry != null) {
                                        { navController.popBackStack() }
                                    } else null,
                                    onNoneSelected = {
                                        scope.launch {
                                            writeGateMode(GateMode.NONE)
                                            afterGateSetup()
                                        }
                                    },
                                    onDeviceSecuritySelected = {
                                        scope.launch {
                                            writeGateMode(GateMode.DEVICE)
                                            afterGateSetup()
                                        }
                                    },
                                    onCustomSecretSet = { type, secret ->
                                        scope.launch {
                                            writeGateMode(GateMode.CUSTOM)
                                            writeGateSecret(type, secret)
                                            afterGateSetup()
                                        }
                                    },
                                    onLockAllVaultsChanged = { checked ->
                                        lockAllVaults = checked
                                        scope.launch {
                                            writeLockAllVaults(checked)
                                        }
                                    }
                                )
                            } else {
                                LaunchedEffect(Unit) {
                                    navController.navigate(VaultRoute.VaultGateAuth) {
                                        popUpTo(VaultRoute.VaultGateSetup) { inclusive = true }
                                        launchSingleTop = true
                                    }
                                }
                            }
                        }
                        composable<VaultRoute.VaultReady> {
                            val readyVault by currentVault.collectAsStateWithLifecycle()
                            val vault = readyVault
                            if (isGateAuthenticated && vault != null) {
                                var unlockType by remember { mutableStateOf<VaultAuthType?>(null) }
                                var gateMode by remember { mutableStateOf<GateMode?>(null) }
                                var lockAll by remember { mutableStateOf(false) }
                                LaunchedEffect(vault) {
                                    unlockType = (vaultCredentialStatus(vault) as? VaultCredentialStatus.Valid)?.authType
                                    if (setupIncludedGate) gateMode = readGateMode()
                                    lockAll = readLockAllVaults()
                                }
                                fun toList() {
                                    navController.navigate(VaultRoute.VaultSelect) {
                                        popUpTo(VaultRoute.VaultReady) { inclusive = true }
                                        launchSingleTop = true
                                    }
                                }
                                BackHandler { toList() }
                                VaultReadyContent(
                                    modifier = Modifier.hazeSource(LocalHazeState.current),
                                    vaultName = vault.name,
                                    unlockType = unlockType,
                                    gateMode = gateMode,
                                    lockAllVaults = lockAll,
                                    totalSteps = setupTotalSteps,
                                    onLater = { toList() },
                                    onAddPhotos = {
                                        requestAddMedia = true
                                        toList()
                                        authenticateVault(vault)
                                    },
                                )
                            } else {
                                LaunchedEffect(Unit) {
                                    navController.navigate(VaultRoute.VaultGateAuth) {
                                        popUpTo(VaultRoute.VaultReady) { inclusive = true }
                                        launchSingleTop = true
                                    }
                                }
                            }
                        }
                        composable<VaultRoute.VaultDisplay> {
                            // If user reaches VaultDisplay without being authenticated
                            // (e.g., back navigation), send them back to VaultSelect
                            LaunchedEffect(isGateAuthenticated, isAuthenticated, vaultState) {
                                if (!isGateAuthenticated && vaultState.value.vaults.isNotEmpty()) {
                                    navController.navigate(VaultRoute.VaultGateAuth) {
                                        popUpTo(VaultRoute.VaultDisplay) { inclusive = true }
                                        launchSingleTop = true
                                    }
                                } else if (!isAuthenticated && !addNewVault && vaultState.value.vaults.isNotEmpty()) {
                                    navController.navigate(VaultRoute.VaultSelect) {
                                        popUpTo(VaultRoute.VaultDisplay) { inclusive = true }
                                        launchSingleTop = true
                                    }
                                }
                            }
                            if (isGateAuthenticated && isAuthenticated) {
                                vaultDisplay(
                                    this@SharedTransitionLayout,
                                    this@composable,
                                    {
                                        // When leaving VaultDisplay, revoke authentication
                                        isAuthenticated = false
                                        parentEventHandler.navigateUp()
                                    },
                                    { mediaId ->
                                        retainedVaultViewerMediaId = mediaId
                                        vaultViewerMediaId = mediaId
                                        vaultViewerOpenCount++
                                    },
                                    {
                                        isAuthenticated = false
                                        navController.navigate(VaultRoute.VaultSelect) {
                                            popUpTo(VaultRoute.VaultDisplay) { inclusive = true }
                                            launchSingleTop = true
                                        }
                                    },
                                    { vault -> authenticateVault(vault) },
                                    requestAddMedia,
                                    { requestAddMedia = false }
                                )
                            }
                        }

                        composable<VaultRoute.EncryptedMediaViewScreen> { backStackEntry ->
                            if (isGateAuthenticated && isAuthenticated) {
                                val args = backStackEntry.toRoute<VaultRoute.EncryptedMediaViewScreen>()
                                mediaViewer(
                                    this@SharedTransitionLayout,
                                    this@composable,
                                    args.mediaId,
                                    null,
                                    0,
                                    null
                                )
                            } else {
                                LaunchedEffect(Unit) {
                                    navController.navigate(VaultRoute.VaultGateAuth) {
                                        popUpTo(VaultRoute.VaultSelect) { inclusive = true }
                                        launchSingleTop = true
                                    }
                                }
                            }
                        }
                    }
                },
                detailContent = {
                    vaultViewerTransition.DeferredAnimatedVisibility(
                        visible = { it },
                        modifier = Modifier.fillMaxSize(),
                        // Long enough for the sharedBounds return flight to finish before the overlay
                        // layer is torn down. With media animations disabled there is no flight to
                        // cover — the viewer appears/disappears instantly.
                        enter = if (viewerAnimationsEnabled) fadeIn(tween(320)) else EnterTransition.None,
                        exit = if (viewerAnimationsEnabled) fadeOut(tween(320)) else ExitTransition.None,
                    ) {
                        if (vaultViewerMediaId == null) {
                            // Exiting: swap the interactive viewer for its non-interactive stand-in
                            // so a fast re-tap falls through to the vault grid — the still-fading
                            // viewer's gesture surfaces would otherwise swallow the touch.
                            viewerExitVisual(vaultViewerDismissBridge)
                        } else {
                        val mediaId = retainedVaultViewerMediaId
                        val currentVaultValue by currentVault.collectAsStateWithLifecycle()
                        if (mediaId != null && currentVaultValue != null) {
                            mediaViewer(
                                this@SharedTransitionLayout,
                                this,
                                mediaId,
                                { vaultViewerMediaId = null },
                                vaultViewerOpenCount,
                                vaultViewerDismissBridge
                            )
                        }
                        }
                    }
                },
            )
            // Committed swipe-dismiss flight morphs the media thumbnail from the release bounds
            // to the vault grid cell — outside the DeferredAV so its fade can't affect it.
            viewerFlightLayer(vaultViewerDismissBridge)
            }
            }
        }

        // Intercept system back button when password dialogs are visible
        BackHandler(enabled = showGatePasswordDialog) {
            showGatePasswordDialog = false
            gatePasswordError = null
            parentEventHandler.navigateUp()
        }
        BackHandler(enabled = showPasswordDialog) {
            showPasswordDialog = false
            passwordError = null
            pendingAuthVault = null
            requestAddMedia = false
        }

        // Gate password/PIN/pattern dialog
        AnimatedVisibility(
            visible = showGatePasswordDialog && !isGateAuthenticated,
            enter = enterAnimation,
            exit = exitAnimation
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.surface)
                    .clickable(
                        interactionSource = null,
                        indication = null,
                        onClick = {}
                    )
            ) {
                VaultPasswordUnlockDialog(
                    title = stringResource(Res.string.vault_unlock_list),
                    authType = gateAuthType,
                    onDismiss = {
                        showGatePasswordDialog = false
                        gatePasswordError = null
                        parentEventHandler.navigateUp()
                    },
                    onSubmit = { secret ->
                        val attemptEpoch = authEpoch
                        scope.launch {
                            val result = verifyGateSecret(secret)
                            if (attemptEpoch != authEpoch) return@launch
                            when (result) {
                                is VerifyResult.Success -> onGateAuthSuccess()

                                is VerifyResult.Failed -> {
                                    gatePasswordError = getString(
                                        Res.string.vault_wrong_password_attempts,
                                        result.attemptsLeft
                                    )
                                }

                                is VerifyResult.LockedOut -> {
                                    val seconds = (result.cooldownMs / 1000).coerceAtLeast(1)
                                    gatePasswordError = getString(Res.string.vault_locked_out, seconds)
                                }
                            }
                        }
                    },
                    errorMessage = gatePasswordError
                )
            }
        }

        // Per-vault password/PIN/pattern dialog
        AnimatedVisibility(
            visible = showPasswordDialog && isGateAuthenticated && !isAuthenticated,
            enter = enterAnimation,
            exit = exitAnimation
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.surface)
                    .clickable(
                        interactionSource = null,
                        indication = null,
                        onClick = {}
                    )
            ) {
                VaultPasswordUnlockDialog(
                    title = pendingAuthVault?.let { stringResource(Res.string.vault_unlock_named, it.name) },
                    authType = detectedAuthType,
                    onDismiss = {
                        showPasswordDialog = false
                        passwordError = null
                        pendingAuthVault = null
                        requestAddMedia = false
                    },
                    onSubmit = { secret ->
                        val vault = pendingAuthVault
                        if (vault != null) {
                            val attemptEpoch = authEpoch
                            scope.launch {
                                val result = verifyVaultSecret(vault, secret)
                                if (attemptEpoch != authEpoch || pendingAuthVault != vault) {
                                    return@launch
                                }
                                when (result) {
                                    is VerifyResult.Success -> {
                                        showPasswordDialog = false
                                        passwordError = null
                                        onVaultAuthSuccess(vault)
                                        pendingAuthVault = null
                                    }

                                    is VerifyResult.Failed -> {
                                        passwordError = getString(
                                            Res.string.vault_wrong_password_attempts,
                                            result.attemptsLeft
                                        )
                                    }

                                    is VerifyResult.LockedOut -> {
                                        val seconds = (result.cooldownMs / 1000).coerceAtLeast(1)
                                        passwordError = getString(Res.string.vault_locked_out, seconds)
                                    }
                                }
                            }
                        }
                    },
                    errorMessage = passwordError
                )
            }
        }
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
