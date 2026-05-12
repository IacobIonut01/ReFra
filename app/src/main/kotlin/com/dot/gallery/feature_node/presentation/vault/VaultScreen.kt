package com.dot.gallery.feature_node.presentation.vault

import android.graphics.Color
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.foundation.background
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
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
import com.dot.gallery.feature_node.domain.model.UIEvent
import com.dot.gallery.feature_node.domain.model.Vault
import com.dot.gallery.feature_node.presentation.mediaview.MediaViewScreenRoute
import com.dot.gallery.feature_node.presentation.util.SecureWindow
import com.dot.gallery.feature_node.presentation.vault.components.VaultPasswordUnlockDialog
import com.dot.gallery.feature_node.presentation.vault.utils.GateMode
import com.dot.gallery.feature_node.presentation.vault.utils.VaultAuthType
import com.dot.gallery.feature_node.presentation.vault.utils.VaultPasswordManager
import com.dot.gallery.feature_node.presentation.vault.utils.VerifyResult
import com.dot.gallery.feature_node.presentation.vault.utils.rememberBiometricState
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun VaultScreen(
    paddingValues: PaddingValues,
    toggleRotate: () -> Unit,
    shouldSkipAuth: MutableState<Boolean>
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

        var isAuthenticated by rememberSaveable { mutableStateOf(shouldSkipAuth.value) }
        var isGateAuthenticated by rememberSaveable { mutableStateOf(shouldSkipAuth.value) }

        // Per-vault auth state
        var showPasswordDialog by remember { mutableStateOf(false) }
        var passwordError by remember { mutableStateOf<String?>(null) }
        var detectedAuthType by remember { mutableStateOf<VaultAuthType?>(null) }
        var pendingAuthVault by rememberSaveable { mutableStateOf<Vault?>(null) }

        // Gate auth state
        var showGatePasswordDialog by remember { mutableStateOf(false) }
        var gatePasswordError by remember { mutableStateOf<String?>(null) }
        var gateAuthType by remember { mutableStateOf<VaultAuthType?>(null) }

        val wrongPasswordAttemptsStr = stringResource(R.string.vault_wrong_password_attempts)
        val lockedOutStr = stringResource(R.string.vault_locked_out)
        val scope = rememberCoroutineScope()

        /** Navigate to VaultDisplay after successful per-vault auth */
        fun onVaultAuthSuccess(vault: Vault) {
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

        /** Authenticate a specific vault: custom password dialog or device biometric. */
        fun authenticateVault(vault: Vault) {
            scope.launch {
                pendingAuthVault = vault
                val authType = VaultPasswordManager.getAuthType(context, vault.uuid)
                if (authType != null) {
                    detectedAuthType = authType
                    showPasswordDialog = true
                } else if (biometricState.isSupported) {
                    detectedAuthType = null
                    biometricState.authenticate()
                } else {
                    onVaultAuthSuccess(vault)
                    pendingAuthVault = null
                    detectedAuthType = null
                }
            }
        }

        fun onGateAuthSuccess() {
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
            onSuccess = { onGateAuthSuccess() },
            onFailed = { globalEventHandler.navigateUp() }
        )

        /** Trigger gate authentication based on stored GateMode */
        fun triggerGateAuth() {
            scope.launch {
                when (VaultPasswordManager.getGateMode(context)) {
                    GateMode.NONE -> onGateAuthSuccess()
                    GateMode.DEVICE -> {
                        if (gateBiometricState.isSupported) {
                            gateBiometricState.authenticate()
                        } else {
                            onGateAuthSuccess()
                        }
                    }
                    GateMode.CUSTOM -> {
                        val authType = VaultPasswordManager.getAuthType(
                            context, VaultPasswordManager.GATE_UUID
                        )
                        if (authType != null) {
                            gateAuthType = authType
                            showGatePasswordDialog = true
                        } else {
                            // Custom auth was set but credentials are missing — fallback
                            onGateAuthSuccess()
                        }
                    }
                }
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
            NavHost(
                modifier = Modifier.fillMaxSize(),
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
                    LaunchedEffect(Unit) {
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
                }

                composable<VaultScreens.VaultSetup> {
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
                }
                composable<VaultScreens.VaultPasswordSetup> {
                    val currentVaultValue by viewModel.currentVault.collectAsStateWithLifecycle()
                    val isFirstVault = vaultState.value.vaults.size <= 1

                    fun afterPasswordSetup() {
                        addNewVault = false
                        if (isFirstVault) {
                            // First vault → show gate security setup
                            navController.navigate(VaultScreens.VaultGateSetup) {
                                popUpTo<VaultScreens.VaultPasswordSetup> { inclusive = true }
                                launchSingleTop = true
                            }
                        } else {
                            // Subsequent vaults → go to vault selector
                            navController.navigate(VaultScreens.VaultSelect) {
                                popUpTo<VaultScreens.VaultPasswordSetup> { inclusive = true }
                                launchSingleTop = true
                            }
                        }
                    }

                    VaultPasswordSetupScreen(
                        vault = currentVaultValue,
                        onSkip = { afterPasswordSetup() },
                        onComplete = { afterPasswordSetup() }
                    )
                }
                composable<VaultScreens.VaultGateSetup> {
                    fun afterGateSetup() {
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
                }
                composable<VaultScreens.VaultDisplay> {
                    // If user reaches VaultDisplay without being authenticated
                    // (e.g., back navigation), send them back to VaultSelect
                    LaunchedEffect(isAuthenticated, vaultState) {
                        if (!isAuthenticated && !addNewVault && vaultState.value.vaults.isNotEmpty()) {
                            navController.navigate(VaultScreens.VaultSelect) {
                                popUpTo<VaultScreens.VaultDisplay> { inclusive = true }
                                launchSingleTop = true
                            }
                        }
                    }
                    AnimatedVisibility(
                        visible = isAuthenticated,
                        enter = enterAnimation,
                        exit = exitAnimation
                    ) {
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
                            onCreateVaultClick = {
                                addNewVault = true
                                navController.navigate(VaultScreens.VaultSetup) {
                                    launchSingleTop = true
                                }
                            },
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
                                navController.navigate(VaultScreens.EncryptedMediaViewScreen(mediaId))
                            },
                        )
                    }
                }

                composable<VaultScreens.EncryptedMediaViewScreen> { backStackEntry ->
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
                        sharedTransitionScope = this@SharedTransitionLayout,
                        animatedContentScope = this@composable
                    )
                }
            }
        }

        // Gate password/PIN/pattern dialog
        AnimatedVisibility(
            visible = showGatePasswordDialog,
            enter = enterAnimation,
            exit = exitAnimation
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.surface)
            ) {
                VaultPasswordUnlockDialog(
                    authType = gateAuthType,
                    onDismiss = {
                        showGatePasswordDialog = false
                        gatePasswordError = null
                        globalEventHandler.navigateUp()
                    },
                    onSubmit = { secret ->
                        scope.launch {
                            when (val result = VaultPasswordManager.verifyPassword(
                                context,
                                VaultPasswordManager.GATE_UUID,
                                secret
                            )) {
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
            visible = showPasswordDialog,
            enter = enterAnimation,
            exit = exitAnimation
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.surface)
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
                            scope.launch {
                                when (val result = VaultPasswordManager.verifyPassword(
                                    context,
                                    vault.uuid,
                                    secret
                                )) {
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
