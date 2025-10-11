/*
 * SPDX-FileCopyrightText: 2023 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.core.presentation.components

import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.NonRestartableComposable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.navArgument
import com.dot.gallery.R
import com.dot.gallery.core.Constants
import com.dot.gallery.core.Constants.Animation.navigateInAnimation
import com.dot.gallery.core.Constants.Animation.navigateUpAnimation
import com.dot.gallery.core.Constants.Target.TARGET_FAVORITES
import com.dot.gallery.core.Constants.Target.TARGET_TRASH
import com.dot.gallery.core.LocalEventHandler
import com.dot.gallery.core.LocalMediaSelector
import com.dot.gallery.core.Settings.Misc.rememberAllowBlur
import com.dot.gallery.core.Settings.Misc.rememberLastScreen
import com.dot.gallery.core.Settings.Misc.rememberTimelineGroupByMonth
import com.dot.gallery.core.navigate
import com.dot.gallery.core.navigateUp
import com.dot.gallery.core.presentation.components.util.OnLifecycleEvent
import com.dot.gallery.core.presentation.components.util.permissionGranted
import com.dot.gallery.core.presentation.vm.NavigationViewModel
import com.dot.gallery.core.toggleNavigationBar
import com.dot.gallery.feature_node.domain.model.MediaState
import com.dot.gallery.feature_node.presentation.albums.AlbumsScreen
import com.dot.gallery.feature_node.presentation.albums.AlbumsViewModel
import com.dot.gallery.feature_node.presentation.albumtimeline.AlbumTimelineScreen
import com.dot.gallery.feature_node.presentation.classifier.CategoriesScreen
import com.dot.gallery.feature_node.presentation.classifier.CategoryViewModel
import com.dot.gallery.feature_node.presentation.classifier.CategoryViewScreen
import com.dot.gallery.feature_node.presentation.dateformat.DateFormatScreen
import com.dot.gallery.feature_node.presentation.favorites.FavoriteScreen
import com.dot.gallery.feature_node.presentation.ignored.IgnoredScreen
import com.dot.gallery.feature_node.presentation.ignored.setup.IgnoredSetup
import com.dot.gallery.feature_node.presentation.library.LibraryScreen
import com.dot.gallery.feature_node.presentation.location.LocationTimelineScreen
import com.dot.gallery.feature_node.presentation.location.LocationsViewModel
import com.dot.gallery.feature_node.presentation.mediaview.MediaViewScreen
import com.dot.gallery.feature_node.presentation.mediaview.rememberedDerivedState
import com.dot.gallery.feature_node.presentation.search.SearchScreen
import com.dot.gallery.feature_node.presentation.search.SearchViewModel
import com.dot.gallery.feature_node.presentation.settings.SettingsScreen
import com.dot.gallery.feature_node.presentation.settings.subsettings.SettingsCustomizationScreen
import com.dot.gallery.feature_node.presentation.settings.subsettings.SettingsGeneralScreen
import com.dot.gallery.feature_node.presentation.settings.subsettings.SettingsSmartFeaturesScreen
import com.dot.gallery.feature_node.presentation.settings.subsettings.SettingsThemesScreen
import com.dot.gallery.feature_node.presentation.setup.SetupScreen
import com.dot.gallery.feature_node.presentation.timeline.TimelineScreen
import com.dot.gallery.feature_node.presentation.trashed.TrashedGridScreen
import com.dot.gallery.feature_node.presentation.util.Screen
import com.dot.gallery.feature_node.presentation.vault.VaultScreen
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberMultiplePermissionsState

@OptIn(ExperimentalSharedTransitionApi::class, ExperimentalPermissionsApi::class)
@Stable
@NonRestartableComposable
@Composable
fun NavigationComp(
    navController: NavHostController,
    paddingValues: PaddingValues,
    bottomBarState: MutableState<Boolean>,
    systemBarFollowThemeState: MutableState<Boolean>,
    toggleRotate: () -> Unit,
    isScrolling: MutableState<Boolean>
) {
    val navViewModel = hiltViewModel<NavigationViewModel>()
    val searchBarActive = rememberSaveable {
        mutableStateOf(false)
    }
    val bottomNavEntries = rememberNavigationItems()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val groupTimelineByMonth by rememberTimelineGroupByMonth()
    val context = LocalContext.current
    var permissionState by rememberSaveable { mutableStateOf(context.permissionGranted(Constants.PERMISSIONS)) }
    rememberMultiplePermissionsState(Constants.PERMISSIONS) {
        permissionState = it.all { item -> item.value }
    }
    var lastStartScreen by rememberLastScreen()
    val startDest by rememberSaveable(permissionState, lastStartScreen) {
        mutableStateOf(
            if (permissionState) {
                lastStartScreen
            } else Screen.SetupScreen()
        )
    }
    val currentDest = remember(navController.currentDestination) {
        navController.currentDestination?.route ?: lastStartScreen
    }
    OnLifecycleEvent { _, event ->
        if (event == Lifecycle.Event.ON_STOP) {
            if (currentDest == Screen.TimelineScreen() || currentDest == Screen.AlbumsScreen()) {
                lastStartScreen = currentDest
            }
        }
    }

    var lastShouldDisplay by rememberSaveable {
        mutableStateOf(bottomNavEntries.find { item -> item.route == currentDest } != null)
    }
    val shouldSkipAuth = rememberSaveable {
        mutableStateOf(false)
    }
    val allowBlur by rememberAllowBlur()

    LaunchedEffect(navBackStackEntry) {
        navBackStackEntry?.destination?.route?.let {
            val shouldDisplayBottomBar =
                bottomNavEntries.find { item -> item.route == it } != null && !searchBarActive.value
            if (lastShouldDisplay != shouldDisplayBottomBar) {
                bottomBarState.value = shouldDisplayBottomBar
                lastShouldDisplay = shouldDisplayBottomBar
            }
            if (it != Screen.VaultScreen()) {
                shouldSkipAuth.value = false
            }
            systemBarFollowThemeState.value =
                !((it.contains(Screen.MediaViewScreen.route) && allowBlur) || it.contains(Screen.VaultScreen()))
        }
    }
    val selector = LocalMediaSelector.current
    val eventHandler = LocalEventHandler.current

    // Preloaded viewModels
    val allAlbumsMediaState = navViewModel.allAlbumsMediaState.collectAsStateWithLifecycle()
    val albumsState = navViewModel.albumsState.collectAsStateWithLifecycle()
    val timelineState = navViewModel.timelineMediaState.collectAsStateWithLifecycle()
    val metadataState = navViewModel.metadataState.collectAsStateWithLifecycle()
    val vaultState = navViewModel.vaultState.collectAsStateWithLifecycle()

    LaunchedEffect(permissionState) {
        navViewModel.updatePermissionGranted(permissionState)
    }

    LaunchedEffect(groupTimelineByMonth) {
        navViewModel.updateGroupByMonth(groupTimelineByMonth)
    }

    val searchViewModel = hiltViewModel<SearchViewModel>()
    SharedTransitionLayout {
        NavHost(
            navController = navController,
            startDestination = startDest,
            enterTransition = { navigateInAnimation },
            exitTransition = { navigateUpAnimation },
            popEnterTransition = { navigateInAnimation },
            popExitTransition = { navigateUpAnimation }
        ) {
            composable(
                route = Screen.SetupScreen(),
            ) {
                LaunchedEffect(Unit) {
                    eventHandler.toggleNavigationBar(false)
                }
                SetupScreen {
                    permissionState = true
                    eventHandler.navigate(Screen.TimelineScreen())
                }
            }
            composable(
                route = Screen.TimelineScreen()
            ) {
                TimelineScreen(
                    paddingValues = paddingValues,
                    isScrolling = isScrolling,
                    sharedTransitionScope = this@SharedTransitionLayout,
                    animatedContentScope = this,
                    mediaState = timelineState,
                    metadataState = metadataState
                )
            }
            composable(
                route = Screen.TrashedScreen()
            ) {
                val trashedMediaState = navViewModel.trashedMediaState.collectAsStateWithLifecycle()
                TrashedGridScreen(
                    paddingValues = paddingValues,
                    mediaState = trashedMediaState,
                    metadataState = metadataState,
                    clearSelection = selector::clearSelection,
                    sharedTransitionScope = this@SharedTransitionLayout,
                    animatedContentScope = this
                )
            }
            composable(
                route = Screen.FavoriteScreen()
            ) {
                val favoritesMediaState =
                    navViewModel.favoriteMediaState.collectAsStateWithLifecycle()
                FavoriteScreen(
                    paddingValues = paddingValues,
                    mediaState = favoritesMediaState,
                    metadataState = metadataState,
                    clearSelection = selector::clearSelection,
                    sharedTransitionScope = this@SharedTransitionLayout,
                    animatedContentScope = this
                )
            }
            composable(
                route = Screen.AlbumsScreen()
            ) {
                val albumsViewModel = hiltViewModel<AlbumsViewModel>()
                AlbumsScreen(
                    paddingValues = paddingValues,
                    isScrolling = isScrolling,
                    onAlbumClick = albumsViewModel.onAlbumClick(eventHandler::navigate),
                    onAlbumLongClick = albumsViewModel.onAlbumLongClick,
                    filterOptions = albumsViewModel.rememberFilters(),
                    onMoveAlbumToTrash = albumsViewModel::moveAlbumToTrash,
                    sharedTransitionScope = this@SharedTransitionLayout,
                    animatedContentScope = this
                )
            }
            composable(
                route = Screen.AlbumViewScreen.albumAndName(),
                arguments = listOf(
                    navArgument(name = "albumId") {
                        type = NavType.LongType
                        defaultValue = -1
                    },
                    navArgument(name = "albumName") {
                        type = NavType.StringType
                        defaultValue = ""
                    }
                )
            ) { backStackEntry ->
                val appName = stringResource(id = R.string.app_name)
                val argumentAlbumName = remember(backStackEntry) {
                    backStackEntry.arguments?.getString("albumName") ?: appName
                }
                val argumentAlbumId = remember(backStackEntry) {
                    backStackEntry.arguments?.getLong("albumId") ?: -1
                }
                AlbumTimelineScreen(
                    albumId = argumentAlbumId,
                    albumName = argumentAlbumName,
                    paddingValues = paddingValues,
                    allAlbumsMediaState = allAlbumsMediaState,
                    metadataState = metadataState,
                    isScrolling = isScrolling,
                    sharedTransitionScope = this@SharedTransitionLayout,
                    animatedContentScope = this
                )
            }
            composable(
                route = Screen.MediaViewScreen.idAndAlbum(),
                arguments = listOf(
                    navArgument(name = "mediaId") {
                        type = NavType.LongType
                        defaultValue = -1L
                    },
                    navArgument(name = "albumId") {
                        type = NavType.LongType
                        defaultValue = -1L
                    }
                )
            ) { backStackEntry ->
                val mediaId: Long = remember(backStackEntry) {
                    backStackEntry.arguments?.getLong("mediaId") ?: -1L
                }
                val albumId: Long = remember(backStackEntry) {
                    backStackEntry.arguments?.getLong("albumId") ?: -1L
                }
                val albumMediaState = rememberedDerivedState(allAlbumsMediaState.value) {
                    allAlbumsMediaState.value[albumId] ?: MediaState()
                }
                val mediaState by rememberedDerivedState(albumId) {
                    if (albumId != -1L) {
                        albumMediaState
                    } else timelineState
                }

                MediaViewScreen(
                    toggleRotate = toggleRotate,
                    paddingValues = paddingValues,
                    mediaId = mediaId,
                    mediaState = mediaState,
                    metadataState = metadataState,
                    albumsState = albumsState,
                    vaultState = vaultState,
                    sharedTransitionScope = this@SharedTransitionLayout,
                    animatedContentScope = this
                )
            }
            composable(
                route = Screen.MediaViewScreen.idAndTarget(),
                arguments = listOf(
                    navArgument(name = "mediaId") {
                        type = NavType.LongType
                        defaultValue = -1
                    },
                    navArgument(name = "target") {
                        type = NavType.StringType
                        defaultValue = ""
                    }
                )
            ) { backStackEntry ->
                val mediaId: Long = remember(backStackEntry) {
                    backStackEntry.arguments?.getLong("mediaId") ?: -1
                }
                val target: String? = remember(backStackEntry) {
                    backStackEntry.arguments?.getString("target")
                }
                val mediaState = remember(target) {
                    when (target) {
                        TARGET_FAVORITES -> navViewModel.favoriteMediaState
                        TARGET_TRASH -> navViewModel.trashedMediaState
                        else -> navViewModel.timelineMediaState
                    }
                }.collectAsStateWithLifecycle()

                MediaViewScreen(
                    toggleRotate = toggleRotate,
                    paddingValues = paddingValues,
                    mediaId = mediaId,
                    target = target,
                    mediaState = mediaState,
                    metadataState = metadataState,
                    albumsState = albumsState,
                    vaultState = vaultState,
                    sharedTransitionScope = this@SharedTransitionLayout,
                    animatedContentScope = this
                )
            }
            composable(
                route = Screen.MediaViewScreen.idAndQuery(),
                arguments = listOf(
                    navArgument(name = "mediaId") {
                        type = NavType.LongType
                        defaultValue = -1
                    }
                )
            ) { backStackEntry ->
                val mediaId: Long = remember(backStackEntry) {
                    backStackEntry.arguments?.getLong("mediaId") ?: -1
                }
                val searchResultsState =
                    searchViewModel.searchResultsState.collectAsStateWithLifecycle()
                val mediaState =
                    remember(searchResultsState.value) { mutableStateOf(searchResultsState.value.results) }
                MediaViewScreen(
                    toggleRotate = toggleRotate,
                    paddingValues = paddingValues,
                    mediaId = mediaId,
                    mediaState = mediaState,
                    metadataState = metadataState,
                    albumsState = albumsState,
                    vaultState = vaultState,
                    sharedTransitionScope = this@SharedTransitionLayout,
                    animatedContentScope = this
                )
            }
            composable(
                route = Screen.SettingsScreen()
            ) {
                SettingsScreen()
            }
            composable(
                route = Screen.IgnoredScreen()
            ) {
                IgnoredScreen(
                    startSetup = { eventHandler.navigate(Screen.IgnoredSetupScreen()) },
                    albumsState = albumsState
                )
            }

            composable(
                route = Screen.IgnoredSetupScreen()
            ) {
                IgnoredSetup(
                    onCancel = eventHandler::navigateUp,
                    albumState = albumsState
                )
            }

            composable(
                route = Screen.VaultScreen()
            ) {
                VaultScreen(
                    paddingValues = paddingValues,
                    toggleRotate = toggleRotate,
                    shouldSkipAuth = shouldSkipAuth
                )
            }

            composable(
                route = Screen.LibraryScreen()
            ) {
                LibraryScreen(
                    paddingValues = paddingValues,
                    isScrolling = isScrolling,
                    metadataState = metadataState,
                    sharedTransitionScope = this@SharedTransitionLayout,
                    animatedContentScope = this
                )
            }

            composable(
                route = Screen.CategoriesScreen()
            ) {
                CategoriesScreen(metadataState = metadataState)
            }

            composable(
                route = Screen.CategoryViewScreen.category(),
                arguments = listOf(
                    navArgument(name = "category") {
                        type = NavType.StringType
                        defaultValue = ""
                    }
                )
            ) { backStackEntry ->
                val category: String = remember(backStackEntry) {
                    backStackEntry.arguments?.getString("category") ?: ""
                }
                CategoryViewScreen(
                    category = category,
                    metadataState = metadataState,
                    sharedTransitionScope = this@SharedTransitionLayout,
                    animatedContentScope = this
                )
            }

            composable(
                route = Screen.MediaViewScreen.idAndCategory(),
                arguments = listOf(
                    navArgument(name = "mediaId") {
                        type = NavType.LongType
                        defaultValue = -1
                    },
                    navArgument(name = "category") {
                        type = NavType.StringType
                        defaultValue = "null"
                    }
                )
            ) { backStackEntry ->
                val mediaId: Long = remember(backStackEntry) {
                    backStackEntry.arguments?.getLong("mediaId") ?: -1
                }
                val category: String = remember(backStackEntry) {
                    backStackEntry.arguments?.getString("category", "null").toString()
                }

                val viewModel = hiltViewModel<CategoryViewModel>().apply {
                    this.category = category
                }
                val mediaState = viewModel.mediaByCategory
                    .collectAsStateWithLifecycle(MediaState())

                MediaViewScreen(
                    toggleRotate = toggleRotate,
                    paddingValues = paddingValues,
                    mediaId = mediaId,
                    target = "category_$category",
                    mediaState = mediaState,
                    metadataState = metadataState,
                    albumsState = albumsState,
                    vaultState = vaultState,
                    sharedTransitionScope = this@SharedTransitionLayout,
                    animatedContentScope = this
                )
            }

            composable(Screen.DateFormatScreen()) {
                DateFormatScreen()
            }
            composable(Screen.SettingsThemeScreen()) {
                SettingsThemesScreen()
            }
            composable(Screen.SettingsGeneralScreen()) {
                SettingsGeneralScreen()
            }
            composable(Screen.SettingsCustomizationScreen()) {
                SettingsCustomizationScreen()
            }
            composable(Screen.SettingsSmartFeaturesScreen()) {
                SettingsSmartFeaturesScreen()
            }

            composable(Screen.SearchScreen()) {
                SearchScreen(
                    viewModel = searchViewModel,
                    metadataState = metadataState,
                    isScrolling = isScrolling,
                    sharedTransitionScope = this@SharedTransitionLayout,
                    animatedContentScope = this
                )
            }

            composable(Screen.LocationTimelineScreen.location()) { backStackEntry ->
                val gpsLocationNameCity: String = remember(backStackEntry) {
                    backStackEntry.arguments?.getString("gpsLocationNameCity", "null").toString()
                }
                val gpsLocationNameCountry: String = remember(backStackEntry) {
                    backStackEntry.arguments?.getString("gpsLocationNameCountry", "null").toString()
                }

                val locationsViewModel =
                    hiltViewModel<LocationsViewModel, LocationsViewModel.Factory>(
                        key = "LocationViewModel",
                        creationCallback = { factory ->
                            factory.create(gpsLocationNameCity, gpsLocationNameCountry)
                        }
                    )
                val mediaState = locationsViewModel.mediaState.collectAsStateWithLifecycle()

                LocationTimelineScreen(
                    gpsLocationNameCity = gpsLocationNameCity,
                    gpsLocationNameCountry = gpsLocationNameCountry,
                    mediaState = mediaState,
                    metadataState = metadataState,
                    paddingValues = paddingValues,
                    isScrolling = isScrolling,
                    sharedTransitionScope = this@SharedTransitionLayout,
                    animatedContentScope = this
                )
            }

            composable(Screen.MediaViewScreen.idAndLocation()) { backStackEntry ->
                val mediaId: Long = remember(backStackEntry) {
                    backStackEntry.arguments?.getString("mediaId")?.toLongOrNull() ?: -1
                }
                val gpsLocationNameCity: String = remember(backStackEntry) {
                    backStackEntry.arguments?.getString("gpsLocationNameCity", "null").toString()
                }
                val gpsLocationNameCountry: String = remember(backStackEntry) {
                    backStackEntry.arguments?.getString("gpsLocationNameCountry", "null").toString()
                }
                val parentEntry = remember(backStackEntry) {
                    navController.getBackStackEntry(Screen.LocationTimelineScreen.location())
                }

                val locationsViewModel =
                    hiltViewModel<LocationsViewModel, LocationsViewModel.Factory>(
                        viewModelStoreOwner = parentEntry,
                        key = "LocationViewModel",
                        creationCallback = { factory ->
                            factory.create(gpsLocationNameCity, gpsLocationNameCountry)
                        }
                    )
                val mediaState = locationsViewModel.mediaState.collectAsStateWithLifecycle()

                MediaViewScreen(
                    toggleRotate = toggleRotate,
                    paddingValues = paddingValues,
                    mediaId = mediaId,
                    mediaState = mediaState,
                    metadataState = metadataState,
                    albumsState = albumsState,
                    vaultState = vaultState,
                    target = "location_${gpsLocationNameCity}_$gpsLocationNameCountry",
                    sharedTransitionScope = this@SharedTransitionLayout,
                    animatedContentScope = this
                )
            }

        }
    }
}