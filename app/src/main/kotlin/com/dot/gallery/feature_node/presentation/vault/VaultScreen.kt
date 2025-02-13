package com.dot.gallery.feature_node.presentation.vault

import android.graphics.Color
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.dot.gallery.R
import com.dot.gallery.core.Constants.Animation.enterAnimation
import com.dot.gallery.core.Constants.Animation.exitAnimation
import com.dot.gallery.core.Constants.Animation.navigateInAnimation
import com.dot.gallery.core.Constants.Animation.navigateUpAnimation
import com.dot.gallery.core.Settings.Misc.rememberForceTheme
import com.dot.gallery.core.Settings.Misc.rememberIsDarkMode
import com.dot.gallery.feature_node.presentation.common.ChanneledViewModel
import com.dot.gallery.feature_node.presentation.mediaview.MediaViewScreen
import com.dot.gallery.feature_node.presentation.util.SecureWindow
import com.dot.gallery.feature_node.presentation.vault.utils.rememberBiometricState
import kotlinx.coroutines.Dispatchers

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun VaultScreen(
    paddingValues: PaddingValues,
    toggleRotate: () -> Unit,
    shouldSkipAuth: MutableState<Boolean>,
    navigateUp: () -> Unit,
    navigate: (String) -> Unit
) = SecureWindow {
    val viewModel = hiltViewModel<VaultViewModel>()
    viewModel.attachToLifecycle()
    val navController = rememberNavController()

    val navPipe = hiltViewModel<ChanneledViewModel>()
    navPipe
        .initWithNav(navController)
        .collectAsStateWithLifecycle(
            LocalLifecycleOwner.current,
            context = Dispatchers.Main.immediate
        )

    var addNewVault by remember { mutableStateOf(false) }

    var isAuthenticated by remember { mutableStateOf(shouldSkipAuth.value) }
    val biometricState = rememberBiometricState(
        title = stringResource(R.string.biometric_authentication),
        subtitle = stringResource(R.string.unlock_your_vault),
        onSuccess = {
            isAuthenticated = true
            navPipe.navigate(VaultScreens.VaultDisplay())
        },
        onFailed = {
            isAuthenticated = false
        }
    )

    val vaultState = viewModel.vaultState.collectAsStateWithLifecycle()
    val startDestination by remember(vaultState.value) {
        derivedStateOf { vaultState.value.getStartScreen() }
    }

    val context = LocalContext.current

    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val systemBarFollowThemeState = rememberSaveable(navBackStackEntry) {
        mutableStateOf(
            navBackStackEntry?.destination?.route?.contains(VaultScreens.EncryptedMediaViewScreen()) == false
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
            startDestination = startDestination,
            enterTransition = { navigateInAnimation },
            exitTransition = { navigateUpAnimation },
            popEnterTransition = { navigateInAnimation },
            popExitTransition = { navigateUpAnimation }
        ) {
            composable(VaultScreens.LoadingScreen()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }

            composable(VaultScreens.VaultSetup()) {
                VaultSetup(
                    navigateUp = {
                        if (addNewVault) {
                            addNewVault = false
                            if (vaultState.value.vaults.isEmpty()) navigateUp() else navPipe.navigateUp()
                        } else {
                            navigateUp()
                        }
                    },
                    onCreate = {
                        addNewVault = false
                        isAuthenticated = false
                        biometricState.authenticate()
                    },
                    vm = viewModel
                )
            }
            composable(VaultScreens.VaultDisplay()) {
                LaunchedEffect(isAuthenticated, biometricState.isSupported, vaultState) {
                    if (!isAuthenticated && !addNewVault && vaultState.value.vaults.isNotEmpty()) {
                        if (biometricState.isSupported) {
                            biometricState.authenticate()
                        } else navigateUp()
                    }
                }
                AnimatedVisibility(
                    visible = isAuthenticated,
                    enter = enterAnimation,
                    exit = exitAnimation
                ) {
                    VaultDisplay(
                        navigateUp = navigateUp,
                        navigate = navPipe::navigate,
                        vaultState = vaultState,
                        currentVault = viewModel.currentVault,
                        createMediaState = viewModel::createMediaState,
                        addMediaListToVault = viewModel::addMedia,
                        deleteLeftovers = viewModel::deleteLeftovers,
                        deleteVault = viewModel::deleteVault,
                        setVault = { vault -> viewModel.setVault(vault) {} },
                        onCreateVaultClick = {
                            addNewVault = true
                            navPipe.navigate(VaultScreens.VaultSetup())
                        },
                        restoreVault = viewModel::restoreVault,
                        sharedTransitionScope = this@SharedTransitionLayout,
                        animatedContentScope = this@composable,
                        workerProgress = viewModel.progress,
                        workerIsRunning = viewModel.isRunning
                    )
                }
            }

            composable(
                route = VaultScreens.EncryptedMediaViewScreen.id(),
                arguments = listOf(
                    navArgument("mediaId") {
                        type = NavType.LongType
                    }
                )
            ) { backStackEntry ->
                val mediaId = remember(backStackEntry) {
                    backStackEntry.arguments?.getLong("mediaId") ?: -1
                }
                val mediaState = remember(viewModel.currentVault.value) {
                    viewModel.createMediaState(viewModel.currentVault.value)
                }.collectAsStateWithLifecycle()
                MediaViewScreen(
                    navigateUp = navPipe::navigateUp,
                    toggleRotate = toggleRotate,
                    paddingValues = paddingValues,
                    mediaId = mediaId,
                    mediaState = mediaState,
                    currentVault = viewModel.currentVault.value,
                    restoreMedia = viewModel::restoreMedia,
                    deleteMedia = viewModel::deleteMedia,
                    handler = null,
                    vaultState = vaultState,
                    addMedia = { vault, media ->
                        viewModel.addMedia(
                            vault = vault,
                            list = listOf(media.uri)
                        )
                    },
                    navigate = navigate,
                    sharedTransitionScope = this@SharedTransitionLayout,
                    animatedContentScope = this@composable
                )
            }
        }
    }
}

