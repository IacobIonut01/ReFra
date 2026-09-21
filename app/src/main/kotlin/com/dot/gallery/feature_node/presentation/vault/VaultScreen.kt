package com.dot.gallery.feature_node.presentation.vault

import android.graphics.Color
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.DeferredAnimatedVisibility
import androidx.compose.animation.ExperimentalSharedTransitionApi

import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.core.ExperimentalDeferredTransitionApi
import androidx.compose.animation.core.rememberTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.dot.gallery.R
import com.dot.gallery.core.Constants.Animation.enterAnimation
import com.dot.gallery.core.Constants.Animation.exitAnimation
import com.dot.gallery.core.Constants.Animation.navigateInAnimation
import com.dot.gallery.core.Constants.Animation.navigateUpAnimation
import com.dot.gallery.core.DefaultEventHandler
import com.dot.gallery.core.LocalEventHandler
import com.dot.gallery.core.Settings.Misc.rememberForceTheme
import com.dot.gallery.core.Settings.Misc.rememberIsDarkMode
import com.dot.gallery.core.navigateUp
import com.dot.gallery.core.presentation.components.util.OnLifecycleEvent
import com.dot.gallery.feature_node.domain.model.UIEvent
import com.dot.gallery.feature_node.domain.model.Vault
import com.dot.gallery.feature_node.presentation.mediaview.MediaViewScreenRoute
import com.dot.gallery.feature_node.presentation.mediaview.LocalViewerDismissBridge
import com.dot.gallery.feature_node.presentation.mediaview.ViewerDismissBridge
import com.dot.gallery.feature_node.presentation.mediaview.components.media.ViewerDismissFlightLayer
import com.dot.gallery.feature_node.presentation.util.SecureWindow
import com.dot.gallery.feature_node.presentation.vault.components.VaultPasswordUnlockDialog
import com.dot.gallery.feature_node.presentation.vault.utils.GateMode
import com.dot.gallery.feature_node.presentation.vault.utils.VaultAuthType
import com.dot.gallery.feature_node.presentation.vault.utils.VaultCredentialStatus
import com.dot.gallery.feature_node.presentation.vault.utils.VaultGatePolicy
import com.dot.gallery.feature_node.presentation.vault.utils.VaultPasswordManager
import com.dot.gallery.feature_node.presentation.vault.utils.VerifyResult
import com.dot.gallery.feature_node.presentation.vault.utils.rememberBiometricState
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

@OptIn(ExperimentalSharedTransitionApi::class, ExperimentalDeferredTransitionApi::class)
@Composable
fun VaultScreen(
    paddingValues: PaddingValues,
    toggleRotate: () -> Unit,
    @Suppress("UNUSED_PARAMETER") shouldSkipAuth: MutableState<Boolean>,
    allowBlur: Boolean
) = SecureWindow {
    val globalEventHandler = LocalEventHandler.current
    val viewModel = hiltViewModel<VaultViewModel>()
    val navController = rememberNavController()
    var addNewVault by remember { mutableStateOf(false) }

    val localEventHandler = remember { DefaultEventHandler() }
    LaunchedEffect(localEventHandler) {
        localEventHandler.navigateAction = {
            navController.navigate(it) {
                launchSingleTop = true
                restoreState = true
            }
        }
        localEventHandler.navigateUpAction = navController::navigateUp
    }
    LaunchedEffect(localEventHandler) {
        localEventHandler.updaterFlow.collectLatest { event ->
            when (event) {
                is UIEvent.NavigationRouteEvent -> localEventHandler.navigateAction(event.route)
                UIEvent.NavigationUpEvent -> localEventHandler.navigateUpAction()
                is UIEvent.SetFollowThemeEvent -> globalEventHandler.setFollowThemeAction(event.followTheme)
                is UIEvent.ToggleNavigationBarEvent -> globalEventHandler.toggleNavigationBarAction(
                    event.isVisible
                )

                UIEvent.UpdateDatabase -> {}
            }
        }
    }
    CompositionLocalProvider(
        LocalEventHandler provides localEventHandler
    ) {
        val context = LocalContext.current
        val albumState = viewModel.albumsState.collectAsStateWithLifecycle()
        val metadataState = viewModel.metadataState.collectAsStateWithLifecycle()
        val vaultState = viewModel.vaultState.collectAsStateWithLifecycle()

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
        val vaultViewerActive = vaultViewerVisibility.currentState || vaultViewerVisibility.targetState
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

        val wrongPasswordAttemptsStr = stringResource(R.string.vault_wrong_password_attempts)
        val lockedOutStr = stringResource(R.string.vault_locked_out)
        val scope = rememberCoroutineScope()

        /** Navigate to VaultDisplay after successful per-vault auth */
        fun onVaultAuthSuccess(vault: Vault) {
            if (!isGateAuthenticated) return
            isAuthenticated = true
            viewModel.currentVault.value = vault
            viewModel.setVault(
                vault = vault,
                onFailed = { println("Vault set failed: $it") },
                onSuccess = { println("Vault switched to ${vault.name}") }
            )
            val currentRoute = navController.currentBackStackEntry?.destination?.route
            if (currentRoute?.contains("VaultDisplay") != true) {
                navController.navigate(VaultScreens.VaultDisplay) {
                    popUpTo<VaultScreens.VaultSelect> { inclusive = false }
                    launchSingleTop = true
                }
            }
        }

        // Per-vault biometric
        val biometricState = rememberBiometricState(
            title = stringResource(R.string.biometric_authentication),
            subtitle = stringResource(R.string.verify_identity),
            onSuccess = {
                pendingAuthVault?.let { onVaultAuthSuccess(it) }
                pendingAuthVault = null
            },
            onFailed = { pendingAuthVault = null }
        )

        /** Authenticate a specific vault: custom password dialog or device security. */
        fun authenticateVault(vault: Vault) {
            val attemptEpoch = authEpoch
            scope.launch {
                pendingAuthVault = vault
                if (VaultGatePolicy.shouldSkipVaultAuth(
                        mode = VaultPasswordManager.getGateMode(context),
                        lockAllVaults = VaultPasswordManager.getVaultLockAll(context)
                    )
                ) {
                    // One lock covers every vault — the satisfied gate is enough.
                    if (attemptEpoch != authEpoch || !isGateAuthenticated) return@launch
                    pendingAuthVault = null
                    onVaultAuthSuccess(vault)
                    return@launch
                }
                val status = VaultPasswordManager.getCredentialStatus(context, vault.uuid)
                if (attemptEpoch != authEpoch || !isGateAuthenticated) return@launch
                when (status) {
                    is VaultCredentialStatus.Valid -> {
                        detectedAuthType = status.authType
                        showPasswordDialog = true
                    }
                    VaultCredentialStatus.Missing -> {
                        detectedAuthType = null
                        if (biometricState.isSupported) {
                            biometricState.authenticate()
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
            navController.navigate(VaultScreens.VaultSelect) {
                popUpTo<VaultScreens.VaultGateAuth> { inclusive = true }
                launchSingleTop = true
            }
        }

        // Gate biometric
        val gateBiometricState = rememberBiometricState(
            title = stringResource(R.string.biometric_authentication),
            subtitle = stringResource(R.string.verify_identity),
            onSuccess = {
                if (gateDeviceAuthPending) onGateAuthSuccess()
                gateDeviceAuthPending = false
            },
            onFailed = {
                val wasPending = gateDeviceAuthPending
                gateDeviceAuthPending = false
                if (wasPending) globalEventHandler.navigateUp()
            }
        )

        /** Trigger gate authentication based on stored GateMode */
        fun triggerGateAuth() {
            val attemptEpoch = authEpoch
            scope.launch {
                val gateMode = VaultPasswordManager.getGateMode(context)
                if (attemptEpoch != authEpoch) return@launch
                when (gateMode) {
                    GateMode.NONE -> onGateAuthSuccess()
                    GateMode.DEVICE -> {
                        if (gateBiometricState.isSupported) {
                            gateDeviceAuthPending = true
                            gateBiometricState.authenticate()
                        } else {
                            gateDeviceAuthPending = false
                            globalEventHandler.navigateUp()
                        }
                    }
                    GateMode.CUSTOM -> {
                        val status = VaultPasswordManager.getCredentialStatus(
                            context, VaultPasswordManager.GATE_UUID
                        )
                        if (attemptEpoch != authEpoch) return@launch
                        when (status) {
                            is VaultCredentialStatus.Valid -> {
                                gateAuthType = status.authType
                                showGatePasswordDialog = true
                            }
                            VaultCredentialStatus.Missing,
                            VaultCredentialStatus.Corrupt -> globalEventHandler.navigateUp()
                        }
                    }
                }
            }
        }

        OnLifecycleEvent(lifecycleOwner = ProcessLifecycleOwner.get()) { _, event ->
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
                    if (!isInitialSetupFlow) viewModel.currentVault.value = null
                    biometricState.cancelAuthentication()
                    gateBiometricState.cancelAuthentication()
                    reauthenticateOnStart =
                        vaultState.value.vaults.isNotEmpty() && !isInitialSetupFlow
                }
                Lifecycle.Event.ON_START -> {
                    if (reauthenticateOnStart) {
                        reauthenticateOnStart = false
                        gateAuthRequest += 1
                        navController.navigate(VaultScreens.VaultGateAuth) {
                            popUpTo<VaultScreens.VaultSelect> { inclusive = true }
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
        val forcedTheme by rememberForceTheme()
        val localDarkTheme by rememberIsDarkMode()
        val systemDarkTheme = isSystemInDarkTheme()
        val darkTheme by remember(forcedTheme, localDarkTheme, systemDarkTheme) {
            mutableStateOf(if (forcedTheme) localDarkTheme else systemDarkTheme)
        }
        LaunchedEffect(darkTheme, systemBarFollowThemeState.value) {
            (context as? ComponentActivity)?.enableEdgeToEdge(
                statusBarStyle = SystemBarStyle.auto(
                    Color.TRANSPARENT,
                    Color.TRANSPARENT,
                ) { darkTheme || !systemBarFollowThemeState.value },
                navigationBarStyle = SystemBarStyle.auto(
                    Color.TRANSPARENT,
                    Color.TRANSPARENT,
                ) { darkTheme || !systemBarFollowThemeState.value }
            )
        }

        SharedTransitionLayout {
            CompositionLocalProvider(LocalViewerDismissBridge provides vaultViewerDismissBridge) {
            Box(modifier = Modifier.fillMaxSize()) {
            NavHost(
                modifier = Modifier
                    .fillMaxSize()
                    .then(
                        if (vaultViewerActive) Modifier.clearAndSetSemantics { }
                        else Modifier
                    ),
                navController = navController,
                startDestination = VaultScreens.LoadingScreen,
                enterTransition = { navigateInAnimation },
                exitTransition = { navigateUpAnimation },
                popEnterTransition = { navigateInAnimation },
                popExitTransition = { navigateUpAnimation }
            ) {
                composable<VaultScreens.LoadingScreen> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                    // One-time navigation when loading finishes
                    LaunchedEffect(vaultState.value.isLoading) {
                        if (!vaultState.value.isLoading) {
                            val dest = if (vaultState.value.vaults.isEmpty()) {
                                isInitialSetupFlow = true
                                VaultScreens.VaultSetup
                            } else {
                                VaultScreens.VaultGateAuth
                            }
                            navController.navigate(dest) {
                                popUpTo<VaultScreens.LoadingScreen> { inclusive = true }
                                launchSingleTop = true
                            }
                        }
                    }
                }

                composable<VaultScreens.VaultGateAuth> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                    LaunchedEffect(gateAuthRequest) {
                        if (isGateAuthenticated) {
                            navController.navigate(VaultScreens.VaultSelect) {
                                popUpTo<VaultScreens.VaultGateAuth> { inclusive = true }
                                launchSingleTop = true
                            }
                        } else {
                            triggerGateAuth()
                        }
                    }
                }

                composable<VaultScreens.VaultSelect> {
                    if (isGateAuthenticated) {
                        VaultSelectScreen(
                            vaultState = vaultState,
                            onVaultSelected = { vault -> authenticateVault(vault) },
                            onCreateVault = {
                                addNewVault = true
                                navController.navigate(VaultScreens.VaultSetup) {
                                    launchSingleTop = true
                                }
                            },
                            onChangeGateSecurity = {
                                navController.navigate(VaultScreens.VaultGateSetup) {
                                    launchSingleTop = true
                                }
                            },
                            onNavigateUp = globalEventHandler::navigateUp
                        )
                    } else {
                        LaunchedEffect(Unit) {
                            navController.navigate(VaultScreens.VaultGateAuth) {
                                popUpTo<VaultScreens.VaultSelect> { inclusive = true }
                                launchSingleTop = true
                            }
                        }
                    }
                }

                composable<VaultScreens.VaultSetup> {
                    if (vaultState.value.vaults.isEmpty() || isInitialSetupFlow || isGateAuthenticated) {
                        VaultSetup(
                            navigateUp = {
                                addNewVault = false
                                if (vaultState.value.vaults.isEmpty()) globalEventHandler.navigateUp() else localEventHandler.navigateUp()
                            },
                            onCreate = {
                                navController.navigate(VaultScreens.VaultPasswordSetup) {
                                    popUpTo<VaultScreens.VaultSetup> { inclusive = true }
                                    launchSingleTop = true
                                }
                            },
                            vm = viewModel
                        )
                    } else {
                        LaunchedEffect(Unit) {
                            navController.navigate(VaultScreens.VaultGateAuth) {
                                popUpTo<VaultScreens.VaultSetup> { inclusive = true }
                                launchSingleTop = true
                            }
                        }
                    }
                }
                composable<VaultScreens.VaultPasswordSetup> {
                    if (isInitialSetupFlow || isGateAuthenticated) {
                        val currentVaultValue by viewModel.currentVault.collectAsStateWithLifecycle()
                        val isFirstVault = vaultState.value.vaults.isEmpty()

                        fun afterPasswordSetup() {
                            addNewVault = false
                            val vault = currentVaultValue ?: return
                            val first = isFirstVault
                            viewModel.setVault(
                                vault = vault,
                                onFailed = { /* name was already validated in VaultSetup */ },
                                onSuccess = {
                                    if (first) {
                                        navController.navigate(VaultScreens.VaultGateSetup) {
                                            popUpTo<VaultScreens.VaultPasswordSetup> { inclusive = true }
                                            launchSingleTop = true
                                        }
                                    } else {
                                        navController.navigate(VaultScreens.VaultSelect) {
                                            popUpTo<VaultScreens.VaultPasswordSetup> { inclusive = true }
                                            launchSingleTop = true
                                        }
                                    }
                                }
                            )
                        }

                        VaultPasswordSetupScreen(
                            vault = currentVaultValue,
                            onSkip = { afterPasswordSetup() },
                            onComplete = { afterPasswordSetup() }
                        )
                    } else {
                        LaunchedEffect(Unit) {
                            navController.navigate(VaultScreens.VaultGateAuth) {
                                popUpTo<VaultScreens.VaultPasswordSetup> { inclusive = true }
                                launchSingleTop = true
                            }
                        }
                    }
                }
                composable<VaultScreens.VaultGateSetup> {
                    if (isInitialSetupFlow || isGateAuthenticated) {
                        fun afterGateSetup() {
                            isInitialSetupFlow = false
                            isGateAuthenticated = true
                            navController.navigate(VaultScreens.VaultSelect) {
                                popUpTo<VaultScreens.VaultGateSetup> { inclusive = true }
                                launchSingleTop = true
                            }
                        }

                        VaultGateSetupScreen(
                            onBack = if (navController.previousBackStackEntry != null) {
                                { navController.popBackStack() }
                            } else null,
                            onNone = { afterGateSetup() },
                            onDeviceSecurity = { afterGateSetup() },
                            onCustomComplete = { afterGateSetup() }
                        )
                    } else {
                        LaunchedEffect(Unit) {
                            navController.navigate(VaultScreens.VaultGateAuth) {
                                popUpTo<VaultScreens.VaultGateSetup> { inclusive = true }
                                launchSingleTop = true
                            }
                        }
                    }
                }
                composable<VaultScreens.VaultDisplay> {
                    // If user reaches VaultDisplay without being authenticated
                    // (e.g., back navigation), send them back to VaultSelect
                    LaunchedEffect(isGateAuthenticated, isAuthenticated, vaultState) {
                        if (!isGateAuthenticated && vaultState.value.vaults.isNotEmpty()) {
                            navController.navigate(VaultScreens.VaultGateAuth) {
                                popUpTo<VaultScreens.VaultDisplay> { inclusive = true }
                                launchSingleTop = true
                            }
                        } else if (!isAuthenticated && !addNewVault && vaultState.value.vaults.isNotEmpty()) {
                            navController.navigate(VaultScreens.VaultSelect) {
                                popUpTo<VaultScreens.VaultDisplay> { inclusive = true }
                                launchSingleTop = true
                            }
                        }
                    }
                    if (isGateAuthenticated && isAuthenticated) {
                        VaultDisplay(
                            globalNavigateUp = {
                                // When leaving VaultDisplay, revoke authentication
                                isAuthenticated = false
                                globalEventHandler.navigateUp()
                            },
                            vaultState = vaultState,
                            currentVault = viewModel.currentVault,
                            createMediaState = viewModel::createMediaState,
                            deleteLeftovers = viewModel::deleteLeftovers,
                            deleteVault = viewModel::deleteVault,
                            setVault = { vault -> viewModel.setVault(vault) {} },
                            onAuthenticateVault = { vault ->
                                authenticateVault(vault)
                            },
                            onVaultDeleted = {
                                isAuthenticated = false
                                navController.navigate(VaultScreens.VaultSelect) {
                                    popUpTo<VaultScreens.VaultDisplay> { inclusive = true }
                                    launchSingleTop = true
                                }
                            },
                            restoreVault = viewModel::restoreVault,
                            sharedTransitionScope = this@SharedTransitionLayout,
                            animatedContentScope = this@composable,
                            workerProgress = viewModel.progress,
                            workerIsRunning = viewModel.isRunning,
                            metadataState = metadataState,
                            encryptAndRequestDeletion = viewModel::encryptAndRequestDeletion,
                            addMediaKeepOriginals = viewModel::addMediaKeepOriginals,
                            pendingDeletions = viewModel.pendingDeletions,
                            userMessage = viewModel.userMessage,
                            onMediaClick = { mediaId ->
                                retainedVaultViewerMediaId = mediaId
                                vaultViewerMediaId = mediaId
                                vaultViewerOpenCount++
                            },
                        )
                    }
                }

                composable<VaultScreens.EncryptedMediaViewScreen> { backStackEntry ->
                    if (isGateAuthenticated && isAuthenticated) {
                        val args = backStackEntry.toRoute<VaultScreens.EncryptedMediaViewScreen>()
                        val mediaId = args.mediaId
                        val currentVaultValue by viewModel.currentVault.collectAsStateWithLifecycle()
                        val mediaState = remember(currentVaultValue) {
                            viewModel.createMediaState(currentVaultValue)
                        }.collectAsStateWithLifecycle()
                        MediaViewScreenRoute(
                            toggleRotate = toggleRotate,
                            paddingValues = paddingValues,
                            mediaId = mediaId,
                            mediaState = mediaState,
                            vaultState = vaultState,
                            albumsState = albumState,
                            metadataState = metadataState,
                            currentVault = currentVaultValue,
                            restoreMedia = viewModel::restoreMedia,
                            deleteMedia = viewModel::deleteMedia,
                            allowBlur = allowBlur,
                            sharedTransitionScope = this@SharedTransitionLayout,
                            animatedContentScope = this@composable
                        )
                    } else {
                        LaunchedEffect(Unit) {
                            navController.navigate(VaultScreens.VaultGateAuth) {
                                popUpTo<VaultScreens.VaultSelect> { inclusive = true }
                                launchSingleTop = true
                            }
                        }
                    }
                }
            }
            vaultViewerTransition.DeferredAnimatedVisibility(
                visible = { it },
                modifier = Modifier.fillMaxSize(),
                // Long enough for the sharedBounds return flight to finish before the overlay
                // layer is torn down.
                enter = fadeIn(tween(320)),
                exit = fadeOut(tween(320)),
            ) {
                val mediaId = retainedVaultViewerMediaId
                val currentVaultValue by viewModel.currentVault.collectAsStateWithLifecycle()
                if (mediaId != null && currentVaultValue != null) {
                    val mediaState = remember(currentVaultValue) {
                        viewModel.createMediaState(currentVaultValue)
                    }.collectAsStateWithLifecycle()
                    MediaViewScreenRoute(
                        toggleRotate = toggleRotate,
                        paddingValues = paddingValues,
                        mediaId = mediaId,
                        mediaState = mediaState,
                        vaultState = vaultState,
                        albumsState = albumState,
                        metadataState = metadataState,
                        currentVault = currentVaultValue,
                        restoreMedia = viewModel::restoreMedia,
                        deleteMedia = viewModel::deleteMedia,
                        allowBlur = allowBlur,
                        sharedTransitionScope = this@SharedTransitionLayout,
                        animatedContentScope = this,
                        onDismissRequest = { vaultViewerMediaId = null },
                        viewerSessionKey = vaultViewerOpenCount,
                        dismissBridge = vaultViewerDismissBridge,
                    )
                }
            }
            // Committed swipe-dismiss flight morphs the media thumbnail from the release bounds
            // to the vault grid cell — outside the DeferredAV so its fade can't affect it.
            ViewerDismissFlightLayer(vaultViewerDismissBridge)
            }
            }
        }

        // Intercept system back button when password dialogs are visible
        BackHandler(enabled = showGatePasswordDialog) {
            showGatePasswordDialog = false
            gatePasswordError = null
            globalEventHandler.navigateUp()
        }
        BackHandler(enabled = showPasswordDialog) {
            showPasswordDialog = false
            passwordError = null
            pendingAuthVault = null
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
                    authType = gateAuthType,
                    onDismiss = {
                        showGatePasswordDialog = false
                        gatePasswordError = null
                        globalEventHandler.navigateUp()
                    },
                    onSubmit = { secret ->
                        val attemptEpoch = authEpoch
                        scope.launch {
                            val result = VaultPasswordManager.verifyPassword(
                                context,
                                VaultPasswordManager.GATE_UUID,
                                secret
                            )
                            if (attemptEpoch != authEpoch) return@launch
                            when (result) {
                                is VerifyResult.Success -> onGateAuthSuccess()

                                is VerifyResult.Failed -> {
                                    gatePasswordError = String.format(
                                        wrongPasswordAttemptsStr,
                                        result.attemptsLeft
                                    )
                                }

                                is VerifyResult.LockedOut -> {
                                    val seconds = (result.cooldownMs / 1000).coerceAtLeast(1)
                                    gatePasswordError = String.format(lockedOutStr, seconds)
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
                    authType = detectedAuthType,
                    onDismiss = {
                        showPasswordDialog = false
                        passwordError = null
                        pendingAuthVault = null
                    },
                    onSubmit = { secret ->
                        val vault = pendingAuthVault
                        if (vault != null) {
                            val attemptEpoch = authEpoch
                            scope.launch {
                                val result = VaultPasswordManager.verifyPassword(
                                    context,
                                    vault.uuid,
                                    secret
                                )
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
                                        passwordError = String.format(
                                            wrongPasswordAttemptsStr,
                                            result.attemptsLeft
                                        )
                                    }

                                    is VerifyResult.LockedOut -> {
                                        val seconds = (result.cooldownMs / 1000).coerceAtLeast(1)
                                        passwordError = String.format(lockedOutStr, seconds)
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
