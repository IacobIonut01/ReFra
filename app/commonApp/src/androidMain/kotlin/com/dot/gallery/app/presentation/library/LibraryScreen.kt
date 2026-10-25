package com.dot.gallery.app.presentation.library

import androidx.compose.animation.AnimatedContentScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dot.gallery.core.LocalEventHandler
import com.dot.gallery.core.Settings
import com.dot.gallery.core.Settings.Album.rememberAlbumGridSize
import com.dot.gallery.core.Settings.Misc.rememberCartoBasemapKey
import com.dot.gallery.core.Settings.Misc.rememberMapAppearance
import com.dot.gallery.core.metrics.StartupTracer
import com.dot.gallery.core.navigate
import com.dot.gallery.core.startup.StartupContentEffect
import com.dot.gallery.core.util.SdkCompat
import com.dot.gallery.feature.library.ui.LibraryActions
import com.dot.gallery.feature.library.ui.LibraryContent
import com.dot.gallery.feature.library.ui.LibraryRoutes
import com.dot.gallery.feature.library.ui.LibraryState
import com.dot.gallery.app.presentation.main.MainScreenTopBar
import com.dot.gallery.core.presentation.util.Screen
import com.dot.gallery.app.presentation.util.StaticMapPreview
import com.dot.gallery.core.presentation.util.rememberBottomBarInset
import com.dot.gallery.ui.theme.isDarkTheme
import com.dot.gallery.core.presentation.util.effectiveCartoBasemapKey
import com.dot.gallery.injection.injectViewModel
import com.dot.gallery.BuildConfig
import com.dot.gallery.feature.library.LibraryViewModel


/**
 * App-side shell for the Library screen: owns the ViewModel, the platform
 * seams (ViewCompat inset visibility, SharedPreferences cell index, the
 * startup-drawn gate) and the slotted [searchBar]/[mapContent] renderers.
 * Everything else lives in `:feature:library` ([LibraryContent]).
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun LibraryScreen(
    paddingValues: PaddingValues,
    isScrolling: MutableState<Boolean>,
    sharedTransitionScope: SharedTransitionScope,
    animatedContentScope: AnimatedContentScope,
) {
    val viewModel = injectViewModel<LibraryViewModel> {libraryViewModel}
    val snapshot by viewModel.state.collectAsStateWithLifecycle()
    val modelStatus by viewModel.modelStatus.collectAsStateWithLifecycle()
    var lastCellIndex by rememberAlbumGridSize()

    LifecycleStartEffect(Unit) {
        viewModel.onVisible()
        onStopOrDispose { viewModel.onHidden() }
    }

    val eventHandler = LocalEventHandler.current
    val rootInsets = ViewCompat.getRootWindowInsets(LocalView.current)
    val statusBarsVisible =
        rootInsets?.isVisible(WindowInsetsCompat.Type.statusBars())
    val navigationBarsVisible =
        rootInsets?.isVisible(WindowInsetsCompat.Type.navigationBars())
    val measuredBottomInset = rememberBottomBarInset(paddingValues)
    val isDark = isDarkTheme()
    val mapAppearance by Settings.Misc.rememberMapAppearance()
    val userCartoKey by Settings.Misc.rememberCartoBasemapKey()
    val cartoKey = remember(userCartoKey) {
        effectiveCartoBasemapKey(BuildConfig.CARTO_BASEMAP_KEY, userCartoKey)
    }

    StartupContentEffect(
        route = Screen.LibraryScreen(),
        ready = true,
        onContentDrawn = viewModel::onContentDrawn
    )

    val actions = remember(eventHandler) {
        LibraryActions(
            onNavigate = { route -> eventHandler.navigate(route) },
            onOpenLocation = { city, country, latitude, longitude ->
                eventHandler.navigate(
                    Screen.LocationTimelineScreen.location(
                        gpsLocationNameCity = city,
                        gpsLocationNameCountry = country,
                        latitude = latitude,
                        longitude = longitude,
                    )
                )
            },
            onOpenMedia = { mediaId ->
                eventHandler.navigate(Screen.MediaViewScreen.idAndAlbum(mediaId, -1L))
            },
            onOpenPerson = { configId, personId ->
                eventHandler.navigate(Screen.PersonDetailScreen.personId(configId, personId))
            },
            onOpenCategory = { categoryId ->
                eventHandler.navigate(Screen.CategoryViewScreen.categoryId(categoryId))
            },
            onEditCategory = { categoryId ->
                eventHandler.navigate(Screen.EditCategoryScreen.categoryId(categoryId))
            },
            onViewportChanged = viewModel::updateViewport,
            onCellsIndexChange = { lastCellIndex = it },
            trace = { msg -> StartupTracer.trace(msg) {} },
        )
    }

    LibraryContent(
        state = LibraryState(
            snapshot = snapshot,
            modelStatus = modelStatus,
            aiAvailable = viewModel.areAiFeaturesAvailable,
            mapsEnabled = BuildConfig.MAPS_ENABLED,
            statusBarsVisible = statusBarsVisible,
            bottomBarInset = measuredBottomInset,
            navigationBarsVisible = navigationBarsVisible,
            cellsIndex = lastCellIndex,
            supportsTrash = SdkCompat.supportsTrash,
            supportsFavorites = SdkCompat.supportsFavorites,
        ),
        actions = actions,
        routes = LibraryRoutes(
            library = Screen.LibraryScreen.route,
            settings = Screen.SettingsScreen(),
            locations = Screen.LocationsScreen(),
            people = Screen.PeopleListScreen.route,
            categories = Screen.CategoriesScreen(),
            trash = Screen.TrashedScreen.route,
            favorites = Screen.FavoriteScreen.route,
            vault = Screen.VaultScreen.route,
            ignored = Screen.IgnoredScreen.route,
            privateFolder = Screen.PrivateFolderScreen.route,
            cloudArchive = Screen.CloudArchiveScreen.route,
            cloudSharedLinks = Screen.SharedLinksScreen.route,
            cloudBackup = Screen.CloudBackupDashboardScreen.route,
            cloudAccounts = Screen.CloudAccountsScreen.route,
        ),
        paddingValues = paddingValues,
        isScrolling = isScrolling,
        sharedTransitionScope = sharedTransitionScope,
        animatedContentScope = animatedContentScope,
        searchBar = { statusBarInsets, _ ->
            MainScreenTopBar(
                isScrolling = isScrolling,
                statusBarInsets = statusBarInsets,
            )
        },
        mapContent = if (BuildConfig.MAPS_ENABLED) {
            { latitude, longitude ->
                StaticMapPreview(
                    latitude = latitude,
                    longitude = longitude,
                    appearance = mapAppearance,
                    effectiveAppIsDark = isDark,
                    zoom = 8,
                    apiKey = cartoKey,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        } else null,
    )
}
