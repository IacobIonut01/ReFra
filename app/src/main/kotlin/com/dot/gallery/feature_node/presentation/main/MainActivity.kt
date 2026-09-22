/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.feature_node.presentation.main

import android.graphics.Color
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.WindowCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.datastore.preferences.core.emptyPreferences
import com.dot.gallery.core.Constants
import com.dot.gallery.core.MediaDistributor
import com.dot.gallery.core.MediaHandler
import com.dot.gallery.core.MediaSelector
import com.dot.gallery.core.LocalScrollToTop
import com.dot.gallery.core.ScrollToTopController
import com.dot.gallery.core.Settings
import com.dot.gallery.core.Settings.Misc.getSecureMode
import com.dot.gallery.core.activeDataStore
import com.dot.gallery.core.presentation.components.util.permissionGranted
import com.dot.gallery.core.Settings.Misc.rememberAllowBlur
import com.dot.gallery.core.Settings.Misc.rememberForceTheme
import com.dot.gallery.core.Settings.Misc.rememberIsDarkMode
import com.dot.gallery.core.presentation.components.AppBarContainer
import com.dot.gallery.core.presentation.components.NavigationComp
import com.dot.gallery.core.startup.LocalStartupWorkGate
import com.dot.gallery.core.startup.StartupContentEffect
import com.dot.gallery.core.startup.StartupPrefill
import com.dot.gallery.core.startup.StartupWorkGate
import com.dot.gallery.core.util.LocalInitialPreferences
import com.dot.gallery.core.util.SetupMediaProviders
import com.dot.gallery.feature_node.domain.model.UIEvent
import com.dot.gallery.feature_node.domain.util.EventHandler
import com.dot.gallery.feature_node.presentation.mediaview.LocalMediaViewerOverlayController
import com.dot.gallery.feature_node.presentation.mediaview.MediaViewerOverlayHost
import com.dot.gallery.feature_node.presentation.mediaview.canPresentMediaViewerOverlay
import com.dot.gallery.feature_node.presentation.mediaview.rememberMediaViewerOverlayController
import com.dot.gallery.feature_node.presentation.storycards.StoryCardsViewModel
import com.dot.gallery.feature_node.presentation.storycards.StoryViewerScreen
import com.dot.gallery.feature_node.presentation.storycards.StoryViewerSnapshot
import com.dot.gallery.feature_node.presentation.util.LocalHazeState
import com.dot.gallery.feature_node.presentation.util.Screen
import com.dot.gallery.feature_node.presentation.util.applyLauncherSplashTheme
import com.dot.gallery.feature_node.presentation.util.currentLauncherAlias
import com.dot.gallery.feature_node.presentation.util.printWarning
import com.dot.gallery.feature_node.presentation.util.toggleOrientation
import com.dot.gallery.ui.theme.GalleryTheme
import com.dot.gallery.core.image.thumbnail.ThumbnailTelemetry
import com.dot.gallery.core.metrics.StartupTracer
import dagger.hilt.android.AndroidEntryPoint
import dev.chrisbanes.haze.LocalHazeStyle
import dev.chrisbanes.haze.materials.ExperimentalHazeMaterialsApi
import dev.chrisbanes.haze.materials.HazeMaterials
import dev.chrisbanes.haze.rememberHazeState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    @Inject
    lateinit var eventHandler: EventHandler
    @Inject
    lateinit var mediaDistributor: MediaDistributor
    @Inject
    lateinit var mediaHandler: MediaHandler
    @Inject
    lateinit var mediaSelector: MediaSelector
    @Inject
    lateinit var startupWorkGate: StartupWorkGate
    @Inject
    lateinit var startupPrefill: StartupPrefill

    private var startupContentInstalled = false

    @OptIn(ExperimentalHazeMaterialsApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        val activitySpan = StartupTracer.begin("MainActivity.onCreate")
        // Match the splash icon to the enabled launcher alias before the theme is
        // read by installSplashScreen (and to re-assert the persisted override).
        applyLauncherSplashTheme(currentLauncherAlias())
        val splashScreen = StartupTracer.trace("MainActivity.installSplashScreen") { installSplashScreen() }
        splashScreen.setKeepOnScreenCondition { !startupContentInstalled }
        StartupTracer.trace("MainActivity.super.onCreate (Hilt DI)") {
            super.onCreate(savedInstanceState)
        }
        WindowCompat.setDecorFitsSystemWindows(window, false)
        enforceSecureFlag()
        enableEdgeToEdge()
        // Set permission state eagerly so media queries start immediately
        // instead of waiting for a LaunchedEffect after the first Compose frame.
        if (permissionGranted(Constants.PERMISSIONS)) {
            mediaDistributor.hasPermission.value = true
        }
        StartupTracer.end(activitySpan)
        lifecycleScope.launch {
            val initialPreferences = withContext(Dispatchers.IO) {
                try {
                    activeDataStore.data.first()
                } catch (e: IOException) {
                    printWarning("MainActivity: failed to read initial preferences (${e.javaClass.simpleName})")
                    emptyPreferences()
                }
            }
            if (Settings.Misc.secureMode(initialPreferences)) {
                window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
            }
            val initialStartDestination = Settings.Misc.startupDestination(
                initialPreferences,
                permissionGranted(Constants.PERMISSIONS)
            )
            startupPrefill.prepare(initialStartDestination)
            setContent {
                StartupTracer.trace("MainActivity.firstComposition") {}
                val preferences by remember {
                    activeDataStore.data
                }.collectAsStateWithLifecycle(initialValue = initialPreferences)
                CompositionLocalProvider(
                    LocalInitialPreferences provides preferences,
                    LocalStartupWorkGate provides startupWorkGate
                ) {
                    GalleryTheme {
                        LaunchedEffect(Unit) {
                            StartupTracer.trace("MainActivity.firstCompositionApplied") {}
                            StartupTracer.dump()
                        }
                        StartupContentEffect(
                            route = "MainActivity",
                            ready = true,
                            releaseGate = false,
                            committedLabel = "MainActivity.firstFrameCommitted",
                            drawnLabel = "MainActivity.firstDrawn"
                        )
                        val allowBlur by rememberAllowBlur()
                        val hazeState = rememberHazeState(
                            blurEnabled = allowBlur
                        )
                        val navController = rememberNavController()
                        val mediaViewerOverlayController = rememberMediaViewerOverlayController()
                        val storyCardsViewModel = hiltViewModel<StoryCardsViewModel>()
                        val storyViewerSnapshot by storyCardsViewModel.viewerSnapshot.collectAsStateWithLifecycle()
                        val retainedStoryViewerSnapshot = remember {
                            mutableStateOf<StoryViewerSnapshot?>(null)
                        }
                        var storyViewerOpenCount by remember { mutableIntStateOf(0) }
                        LaunchedEffect(storyViewerSnapshot) {
                            if (storyViewerSnapshot != null) {
                                retainedStoryViewerSnapshot.value = storyViewerSnapshot
                                mediaViewerOverlayController.clearRetained()
                                storyViewerOpenCount++
                            }
                        }
                        val navBackStackEntry by navController.currentBackStackEntryAsState()
                        val isStoryViewerRoute = navBackStackEntry?.destination?.route
                            ?.contains(Screen.StoryViewerScreen.route) == true
                        val storyViewerOverlayVisible = storyViewerSnapshot != null && !isStoryViewerRoute
                        val appOverlayVisible = storyViewerOverlayVisible || mediaViewerOverlayController.visible
                        val isScrolling = remember { mutableStateOf(false) }
                        val bottomBarState = rememberSaveable { mutableStateOf(true) }
                        val systemBarFollowThemeState = rememberSaveable { mutableStateOf(true) }
                        val forcedTheme by rememberForceTheme()
                        val localDarkTheme by rememberIsDarkMode()
                        val systemDarkTheme = isSystemInDarkTheme()
                        val darkTheme by remember(forcedTheme, localDarkTheme, systemDarkTheme) {
                            mutableStateOf(if (forcedTheme) localDarkTheme else systemDarkTheme)
                        }
                        LaunchedEffect(eventHandler, navController, mediaViewerOverlayController) {
                            lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                                val navigateAction: (String) -> Unit = { route ->
                                    if (
                                        canPresentMediaViewerOverlay(
                                            route = route,
                                            originRoute = navController.currentDestination?.route,
                                        ) && mediaViewerOverlayController.open(route)
                                    ) {
                                        retainedStoryViewerSnapshot.value = null
                                    } else {
                                        navController.navigate(route) {
                                            launchSingleTop = true
                                            restoreState = true
                                        }
                                    }
                                }
                                val toggleNavigationBarAction: (Boolean) -> Unit = { isVisible ->
                                    bottomBarState.value = isVisible
                                }
                                val navigateUpAction: () -> Unit = { navController.navigateUp() }
                                val setFollowThemeAction: (Boolean) -> Unit = { followTheme ->
                                    systemBarFollowThemeState.value = followTheme
                                }
                                eventHandler.navigateAction = navigateAction
                                eventHandler.toggleNavigationBarAction = toggleNavigationBarAction
                                eventHandler.navigateUpAction = navigateUpAction
                                eventHandler.setFollowThemeAction = setFollowThemeAction
                                try {
                                    eventHandler.updaterFlow.collect { event ->
                                        when (event) {
                                            UIEvent.UpdateDatabase -> Unit
                                            UIEvent.NavigationUpEvent -> eventHandler.navigateUpAction()
                                            is UIEvent.NavigationRouteEvent -> eventHandler.navigateAction(event.route)
                                            is UIEvent.ToggleNavigationBarEvent ->
                                                eventHandler.toggleNavigationBarAction(event.isVisible)
                                            is UIEvent.SetFollowThemeEvent ->
                                                eventHandler.setFollowThemeAction(event.followTheme)
                                        }
                                    }
                                } finally {
                                    eventHandler.navigateAction = {}
                                    eventHandler.toggleNavigationBarAction = {}
                                    eventHandler.navigateUpAction = {}
                                    eventHandler.setFollowThemeAction = {}
                                }
                            }
                        }
                        LaunchedEffect(darkTheme, systemBarFollowThemeState.value) {
                            enableEdgeToEdge(
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
                        val scrollToTopController = remember { ScrollToTopController() }
                        CompositionLocalProvider(
                            LocalHazeState provides hazeState,
                            LocalScrollToTop provides scrollToTopController,
                            LocalMediaViewerOverlayController provides mediaViewerOverlayController,
                            LocalHazeStyle provides HazeMaterials.regular(
                                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                            )
                        ) {
                            SetupMediaProviders(
                                eventHandler = eventHandler,
                                mediaDistributor = mediaDistributor,
                                mediaHandler = mediaHandler,
                                mediaSelector = mediaSelector
                            ) {
                                Scaffold(
                                    modifier = Modifier.fillMaxSize(),
                                    content = { paddingValues ->
                                        AppBarContainer(
                                            navController = navController,
                                            paddingValues = paddingValues,
                                            bottomBarState = bottomBarState.value,
                                            isScrolling = isScrolling.value,
                                            overlayVisible = appOverlayVisible,
                                            overlayContent = { sharedTransitionScope, animatedVisibilityScope, dismissBridge ->
                                                // One call site per overlay content type so the
                                                // composition (and each viewer's ViewerDismissState)
                                                // survives the active → retained hand-off while the
                                                // return-to-origin animation and the exit fade run.
                                                val mediaRoute = mediaViewerOverlayController.route
                                                    ?: mediaViewerOverlayController.retainedRoute
                                                val snapshot = storyViewerSnapshot
                                                    ?: retainedStoryViewerSnapshot.value
                                                if (mediaRoute != null && storyViewerSnapshot == null) {
                                                    MediaViewerOverlayHost(
                                                        route = mediaRoute,
                                                        controller = mediaViewerOverlayController,
                                                        navController = navController,
                                                        paddingValues = paddingValues,
                                                        allowBlur = allowBlur,
                                                        toggleRotate = ::toggleOrientation,
                                                        sharedTransitionScope = sharedTransitionScope,
                                                        animatedVisibilityScope = animatedVisibilityScope,
                                                        dismissBridge = dismissBridge,
                                                    )
                                                } else if (snapshot != null) {
                                                    val metadata by storyCardsViewModel.metadataFlow
                                                        .collectAsStateWithLifecycle()
                                                    val metadataMap = remember(metadata) {
                                                        metadata.associateBy { it.mediaId }
                                                    }
                                                    StoryViewerScreen(
                                                        cards = snapshot.cards,
                                                        initialCardId = snapshot.initialCardId,
                                                        metadataMap = metadataMap,
                                                        onEnsureMetadata = storyCardsViewModel::ensureMetadataAvailable,
                                                        onDismiss = storyCardsViewModel::clearViewerSnapshot,
                                                        sharedTransitionScope = sharedTransitionScope,
                                                        animatedVisibilityScope = animatedVisibilityScope,
                                                        sessionKey = storyViewerOpenCount,
                                                        dismissBridge = dismissBridge,
                                                    )
                                                }
                                            },
                                        ) { sharedTransitionScope ->
                                            NavigationComp(
                                                navController = navController,
                                                sharedTransitionScope = sharedTransitionScope,
                                                paddingValues = paddingValues,
                                                bottomBarState = bottomBarState,
                                                systemBarFollowThemeState = systemBarFollowThemeState,
                                                toggleRotate = ::toggleOrientation,
                                                isScrolling = isScrolling,
                                                storyCardsViewModel = storyCardsViewModel,
                                                initialStartDestination = initialStartDestination
                                            )
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
            }
            startupContentInstalled = true
        }
    }

    override fun onStop() {
        super.onStop()
        // Phase 1 (#1076): flush bounded thumbnail telemetry when backgrounding so a scroll/soak
        // session's latency + cache-source distribution can be read from logcat (staging/debug only).
        ThumbnailTelemetry.logDump()
    }

    private fun enforceSecureFlag() {
        lifecycleScope.launch {
            getSecureMode(this@MainActivity).collectLatest { enabled ->
                if (enabled) {
                    window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
                } else {
                    window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
                }
            }
        }
    }

}