/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.core.presentation.components

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.NonRestartableComposable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import org.jetbrains.compose.resources.stringResource
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.navArgument
import com.dot.gallery.core.AppConstants
import com.dot.gallery.core.LocalMediaDistributor
import com.dot.gallery.core.ui.util.AppBottomSheetState
import com.dot.gallery.domain.model.Album
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import com.dot.gallery.core.LocalEventHandler
import com.dot.gallery.core.LocalMediaSelector
import com.dot.gallery.core.Settings
import com.dot.gallery.core.navigate
import com.dot.gallery.core.metrics.StartupTracer
import com.dot.gallery.core.presentation.components.util.OnLifecycleEvent
import com.dot.gallery.core.presentation.components.util.permissionGranted
import com.dot.gallery.core.startup.rememberStartupDestination
import com.dot.gallery.core.toggleNavigationBar
import com.dot.gallery.domain.model.Media
import com.dot.gallery.domain.model.MediaState
import com.dot.gallery.feature.albums.ui.collections.CollectionSheet
import com.dot.gallery.feature.albums.ui.collections.DeleteCollectionSheet
import com.dot.gallery.feature.albums.ui.components.AlbumGroupSheet
import com.dot.gallery.feature.albums.ui.components.DeleteGroupSheet
import com.dot.gallery.app.presentation.albums.AlbumGroupViewScreen
import com.dot.gallery.feature.albums.ui.EditGroupContent
import com.dot.gallery.app.presentation.albums.AlbumsScreen
import com.dot.gallery.app.presentation.albumtimeline.AlbumTimelineScreen
import com.dot.gallery.app.presentation.albumtimeline.albumViewerStateOwner
import com.dot.gallery.app.presentation.classifier.CategoriesSettingsScreen
import com.dot.gallery.app.presentation.classifier.CategoryEditorScreen
import com.dot.gallery.app.presentation.classifier.CategoriesScreen
import com.dot.gallery.app.presentation.location.LocationsScreen
import com.dot.gallery.app.presentation.classifier.CategoryViewScreen
import com.dot.gallery.feature.albums.ui.collections.CollectionAlbumSelectorContent
import com.dot.gallery.app.presentation.collection.CollectionViewScreen
import com.dot.gallery.app.presentation.dateformat.DateFormatScreen
import com.dot.gallery.feature.exif.ui.SecurityInfoSheet
import com.dot.gallery.feature.exif.ui.MetadataViewContent
import com.dot.gallery.app.presentation.favorites.FavoriteScreen
import com.dot.gallery.app.presentation.help.HelpScreen
import com.dot.gallery.app.presentation.help.TutorialCategoryScreen
import com.dot.gallery.app.presentation.help.TutorialDetailScreen
import com.dot.gallery.app.presentation.help.WhatsNewScreen
import com.dot.gallery.app.presentation.ignored.IgnoredScreen
import com.dot.gallery.app.presentation.library.LibraryScreen
import com.dot.gallery.app.presentation.location.LocationTimelineScreen
import com.dot.gallery.app.presentation.search.SearchScreen
import com.dot.gallery.app.presentation.settings.SettingsScreen
import com.dot.gallery.app.presentation.settings.subsettings.ColorPaletteScreen
import com.dot.gallery.app.presentation.settings.subsettings.SettingsGeneralScreen
import com.dot.gallery.app.presentation.settings.subsettings.SettingsMediaViewerScreen
import com.dot.gallery.app.presentation.settings.subsettings.SettingsNavigationScreen
import com.dot.gallery.feature.settings.ui.SettingsSelectionActionsContent
import com.dot.gallery.feature.settings.ui.SettingsViewerActionsContent
import com.dot.gallery.app.presentation.settings.subsettings.SlideshowSettingsScreen
import com.dot.gallery.app.presentation.settings.subsettings.SettingsTimelineAlbumsScreen
import com.dot.gallery.app.presentation.settings.subsettings.EditBackupsViewerScreen
import com.dot.gallery.app.presentation.settings.subsettings.AIModelsManagerScreen
import com.dot.gallery.app.presentation.settings.subsettings.IntelligenceFeatureScreen
import com.dot.gallery.feature.settings.ui.IntelligenceFeature
import com.dot.gallery.app.presentation.settings.subsettings.SettingsCloudIndexingScreen
import com.dot.gallery.app.presentation.settings.subsettings.SettingsHandoverScreen
import com.dot.gallery.app.presentation.settings.subsettings.SettingsSecurityScreen
import com.dot.gallery.app.presentation.settings.subsettings.SettingsBackupExportScreen
import com.dot.gallery.app.presentation.settings.subsettings.SettingsBackupImportScreen
import com.dot.gallery.app.presentation.settings.subsettings.SettingsSmartFeaturesScreen
import com.dot.gallery.app.presentation.settings.subsettings.SmartScanPreferenceDetailScreen
import com.dot.gallery.app.presentation.settings.subsettings.DeveloperScreen
import com.dot.gallery.app.presentation.settings.subsettings.SessionLogsScreen
import com.dot.gallery.core.logging.AppLog
import com.dot.gallery.cloud.core.ProviderType
import com.dot.gallery.cloud.ui.CloudAccountsScreen
import com.dot.gallery.cloud.ui.CloudAddServerScreen
import com.dot.gallery.cloud.ui.CloudUploadSettingsScreen
import com.dot.gallery.cloud.ui.archive.CloudArchiveScreen
import com.dot.gallery.cloud.ui.backup.BackupOptionsScreen
import com.dot.gallery.cloud.ui.backup.CloudBackupDashboardScreen
import com.dot.gallery.cloud.ui.backup.UploadDetailsScreen
import com.dot.gallery.cloud.ui.destinations.CloudDestinationsScreen
import com.dot.gallery.cloud.ui.memories.MemoriesScreen
import com.dot.gallery.cloud.ui.people.PeopleListScreen
import com.dot.gallery.cloud.ui.people.PersonDetailScreen
import com.dot.gallery.cloud.ui.sharing.SharedLinksScreen
import com.dot.gallery.cloud.ui.settings.CloudAdvancedSettingsScreen
import com.dot.gallery.cloud.ui.settings.CloudNetworkingScreen
import com.dot.gallery.cloud.ui.settings.CloudNotificationSettingsScreen
import com.dot.gallery.cloud.ui.offline.OfflineModeScreen
import com.dot.gallery.cloud.ui.settings.CloudProviderSettingsScreen
import com.dot.gallery.cloud.ui.settings.CloudViewerSettingsScreen
import com.dot.gallery.cloud.ui.space.FreeUpSpaceScreen
import com.dot.gallery.app.presentation.setup.SetupScreen
import com.dot.gallery.app.presentation.storycards.StoryCardsSettingsScreen
import com.dot.gallery.app.presentation.storycards.StoryViewerScreen
import com.dot.gallery.app.presentation.timeline.TimelineScreen
import com.dot.gallery.core.presentation.util.Screen
import com.dot.gallery.app.presentation.privatefolder.PrivateFolderScreen
import com.dot.gallery.app.presentation.privatefolder.PrivateFolderSecuritySetupScreen
import com.dot.gallery.app.presentation.vault.VaultScreen
import com.dot.gallery.feature.vault.utils.rememberBiometricState
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import com.dot.gallery.domain.model.UriMedia
import com.dot.gallery.core.presentation.components.DragHandle
import com.dot.gallery.domain.model.Category
import com.dot.gallery.feature.editor.adjustments.varfilter.Pop
import com.dot.gallery.core.presentation.components.shape
import com.dot.gallery.feature.editor.adjustments.filters.None
import com.dot.gallery.core.presentation.util.update
import com.dot.gallery.cloud.ui.archive.CloudArchiveViewModel
import com.dot.gallery.cloud.ui.people.PersonDetailViewModel
import com.dot.gallery.core.presentation.vm.NavigationViewModel
import com.dot.gallery.feature.albums.AlbumsViewModel
import com.dot.gallery.feature.albums.ui.AlbumTimelineViewModel
import com.dot.gallery.feature.classifier.CategoriesViewModel
import com.dot.gallery.feature.classifier.CategoryViewModel
import com.dot.gallery.feature.albums.ui.collections.CollectionViewModel
import com.dot.gallery.feature.exif.MetadataViewViewModel
import com.dot.gallery.feature.library.location.LocationsViewModel
import com.dot.gallery.feature.vault.privatefolder.PrivateFolderViewModel
import com.dot.gallery.feature.search.SearchViewModel
import com.dot.gallery.feature.timeline.storycards.StoryCardsViewModel
import com.dot.gallery.core.Constants.Animation.navigateInAnimation
import com.dot.gallery.core.Constants.Animation.navigateUpAnimation
import com.dot.gallery.core.Constants.Target.TARGET_FAVORITES
import com.dot.gallery.core.Constants.Target.TARGET_TRASH
import com.dot.gallery.core.Settings.Misc.rememberAllowBlur
import com.dot.gallery.core.Settings.Misc.rememberForcedLastScreen
import com.dot.gallery.core.Settings.Misc.rememberLastScreen
import com.dot.gallery.core.Settings.Misc.rememberSharedElements
import com.dot.gallery.core.Settings.Misc.rememberTimelineGroupByMonth
import com.dot.gallery.core.Settings.Misc.rememberTimelineGroupByYear
import com.dot.gallery.domain.util.isVideo
import com.dot.gallery.app.presentation.mediaview.MediaViewScreenRoute
import com.dot.gallery.app.presentation.trashed.TrashedGridScreen
import com.dot.gallery.core.ui.util.rememberAppBottomSheetState
import com.dot.gallery.injection.injectViewModel
import com.dot.gallery.generated.resources.Res
import com.dot.gallery.generated.resources.action_cancel
import com.dot.gallery.generated.resources.app_name
import com.dot.gallery.generated.resources.biometric_authentication
import com.dot.gallery.generated.resources.lock_album
import com.dot.gallery.generated.resources.lock_album_disclaimer
import com.dot.gallery.generated.resources.unlock_album_biometric_subtitle


@OptIn(ExperimentalSharedTransitionApi::class, ExperimentalPermissionsApi::class)
@Stable
@NonRestartableComposable
@Composable
fun NavigationComp(
    navController: NavHostController,
    sharedTransitionScope: SharedTransitionScope,
    paddingValues: PaddingValues,
    bottomBarState: MutableState<Boolean>,
    systemBarFollowThemeState: MutableState<Boolean>,
    toggleRotate: () -> Unit,
    isScrolling: MutableState<Boolean>,
    storyCardsViewModel: StoryCardsViewModel? = null,
    initialStartDestination: String
) {
    val navViewModel = injectViewModel<NavigationViewModel> {navigationViewModel}
    val bottomNavEntries = rememberNavigationItems()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val selector = LocalMediaSelector.current
    val isSelectionActive by selector.isSelectionActive.collectAsStateWithLifecycle()
    val groupTimelineByMonth by rememberTimelineGroupByMonth()
    val groupTimelineByYear by rememberTimelineGroupByYear()
    val context = LocalContext.current
    var permissionState by rememberSaveable { mutableStateOf(context.permissionGranted(AppConstants.PERMISSIONS)) }
    rememberMultiplePermissionsState(AppConstants.PERMISSIONS) {
        permissionState = it.all { item -> item.value }
    }
    var lastStartScreen by rememberLastScreen()
    var libraryEditMode by rememberSaveable { mutableStateOf(false) }
    val forcedLastScreen by rememberForcedLastScreen()
    // Read synchronously once so the wizard isn't briefly shown before the real start screen.
    val startDest = rememberStartupDestination(initialStartDestination)
    OnLifecycleEvent { _, event ->
        if (event == Lifecycle.Event.ON_STOP) {
            val currentDest = navBackStackEntry?.destination?.route ?: startDest
            // Only update lastStartScreen if user hasn't set a forced default screen
            if (!forcedLastScreen && (currentDest == Screen.TimelineScreen() || currentDest == Screen.LibraryScreen())) {
                lastStartScreen = currentDest
            }
        }
    }

    val shouldSkipAuth = rememberSaveable {
        mutableStateOf(false)
    }
    val allowBlur by rememberAllowBlur()

    LaunchedEffect(navBackStackEntry, isSelectionActive, libraryEditMode) {
        navBackStackEntry?.destination?.route?.let { route ->
            bottomBarState.value = bottomNavEntries.any { it.route == route } && !isSelectionActive && !libraryEditMode
            if (route != Screen.VaultScreen()) {
                shouldSkipAuth.value = false
            }
            systemBarFollowThemeState.value =
                !((route.contains(Screen.MediaViewScreen.route) && allowBlur) || route.contains(Screen.VaultScreen()))
        }
    }

    // Feed the developer-mode screen attribution + navigation breadcrumbs.
    // destination.route is the declared pattern (no resolved args), so nothing
    // user-identifying is logged.
    var lastLoggedRoute by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(navBackStackEntry) {
        val route = navBackStackEntry?.destination?.route?.substringBefore('?')
        if (route != null && route != lastLoggedRoute) {
            lastLoggedRoute = route
            AppLog.setScreen(route)
            AppLog.breadcrumb("navigate", mapOf("to" to route))
        }
    }
    val eventHandler = LocalEventHandler.current

    // Preloaded viewModels
    val distributor = LocalMediaDistributor.current
    val rootViewModelStoreOwner = checkNotNull(LocalViewModelStoreOwner.current)

    @Composable
    fun albumsState() = navViewModel.albumsState.collectAsStateWithLifecycle()

    @Composable
    fun timelineState() = navViewModel.timelineMediaState.collectAsStateWithLifecycle()

    @Composable
    fun metadataState() = navViewModel.metadataState.collectAsStateWithLifecycle()

    @Composable
    fun vaultState() = navViewModel.vaultState.collectAsStateWithLifecycle()

    @Composable
    fun searchViewModel(): SearchViewModel =
        injectViewModel<SearchViewModel>(viewModelStoreOwner = rootViewModelStoreOwner) { searchViewModel }

    LaunchedEffect(permissionState) {
        navViewModel.updatePermissionGranted(permissionState)
    }

    LaunchedEffect(groupTimelineByMonth) {
        navViewModel.updateGroupByMonth(groupTimelineByMonth)
    }

    LaunchedEffect(groupTimelineByYear) {
        navViewModel.updateGroupByYear(groupTimelineByYear)
    }

    // Media/story viewer destinations skip the nav fade when "Animate media items" is off —
    // the route opens and closes instantly. The delegated read keeps the lambdas live, so the
    // graph doesn't need rebuilding when the preference flips.
    val viewerAnimationsEnabled by rememberSharedElements()
    val viewerEnterTransition: AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition = {
        if (viewerAnimationsEnabled) navigateInAnimation else EnterTransition.None
    }
    val viewerExitTransition: AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition = {
        if (viewerAnimationsEnabled) navigateUpAnimation else ExitTransition.None
    }

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
                    // Pop the wizard off the back stack so pressing back from the timeline
                    // exits the app instead of returning to setup.
                    navController.navigate(Screen.TimelineScreen()) {
                        popUpTo(Screen.SetupScreen()) { inclusive = true }
                        launchSingleTop = true
                    }
                }
            }
            composable(
                route = Screen.TimelineScreen()
            ) {
                TimelineScreen(
                    paddingValues = paddingValues,
                    isScrolling = isScrolling,
                    sharedTransitionScope = sharedTransitionScope,
                    animatedContentScope = this,
                    mediaState = timelineState(),
                    metadataState = metadataState(),
                    storyCardsViewModel = storyCardsViewModel,
                )
            }
            composable(
                route = Screen.TrashedScreen()
            ) {
                val trashedMediaState = navViewModel.trashedMediaState.collectAsStateWithLifecycle()
                TrashedGridScreen(
                    paddingValues = paddingValues,
                    mediaState = trashedMediaState,
                    metadataState = metadataState(),
                    clearSelection = selector::clearSelection,
                    sharedTransitionScope = sharedTransitionScope,
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
                    metadataState = metadataState(),
                    clearSelection = selector::clearSelection,
                    sharedTransitionScope = sharedTransitionScope,
                    animatedContentScope = this
                )
            }
            composable(
                route = Screen.LibraryScreen()
            ) {
                val albumsViewModel = injectViewModel<AlbumsViewModel> {albumsViewModel}
                val albumSectionsEnabled by Settings.Album.rememberAlbumSectionsEnabled()
                LaunchedEffect(albumSectionsEnabled) {
                    if (albumSectionsEnabled) {
                        albumsViewModel.ensureDefaultSections()
                    }
                }
                val scope = rememberCoroutineScope()
                var pendingAlbum by remember { mutableStateOf<Album?>(null) }
                var biometricAction by remember { mutableStateOf<String?>(null) }
                var pendingLockAlbum by remember { mutableStateOf<Album?>(null) }
                val securitySheetState = rememberAppBottomSheetState()
                val lockDisclaimerSheetState = rememberAppBottomSheetState()
                val biometricState = rememberBiometricState(
                    title = stringResource(Res.string.biometric_authentication),
                    subtitle = stringResource(Res.string.unlock_album_biometric_subtitle),
                    onSuccess = {
                        when (biometricAction) {
                            "open" -> pendingAlbum?.let { album ->
                                albumsViewModel.onAlbumClick(eventHandler::navigate)(album)
                            }
                            "unlock" -> pendingAlbum?.let { album ->
                                albumsViewModel.toggleAlbumLock(album)
                            }
                        }
                        pendingAlbum = null
                        biometricAction = null
                    },
                    onFailed = {
                        pendingAlbum = null
                        biometricAction = null
                    }
                )
                val onAlbumClickWithLock: (Album) -> Unit = remember(biometricState) {
                    { album ->
                        if (album.isLocked) {
                            if (!biometricState.isSupported) {
                                scope.launch { securitySheetState.show() }
                            } else {
                                pendingAlbum = album
                                biometricAction = "open"
                                biometricState.authenticate()
                            }
                        } else {
                            albumsViewModel.onAlbumClick(eventHandler::navigate)(album)
                        }
                    }
                }
                val onLockAlbumWithCheck: (Album) -> Unit = remember(biometricState) {
                    { album ->
                        if (!biometricState.isSupported) {
                            scope.launch { securitySheetState.show() }
                        } else if (album.isLocked) {
                            pendingAlbum = album
                            biometricAction = "unlock"
                            biometricState.authenticate()
                        } else {
                            pendingLockAlbum = album
                            scope.launch { lockDisclaimerSheetState.show() }
                        }
                    }
                }
                SecurityInfoSheet(sheetState = securitySheetState)
                LockDisclaimerSheet(
                    sheetState = lockDisclaimerSheetState,
                    onConfirm = {
                        pendingLockAlbum?.let { albumsViewModel.toggleAlbumLock(it) }
                        pendingLockAlbum = null
                    }
                )
                // Album group sheet state
                val groupSheetState = rememberAppBottomSheetState()
                var groupDialogMode by remember { mutableStateOf("create") }
                var groupDialogGroupId by remember { mutableStateOf<Long?>(null) }
                var groupDialogInitialName by remember { mutableStateOf("") }
                var pendingGroupAlbum by remember { mutableStateOf<Album?>(null) }
                val deleteGroupSheetState = rememberAppBottomSheetState()
                var pendingDeleteGroupId by remember { mutableStateOf<Long?>(null) }

                val distributor = LocalMediaDistributor.current
                val albumsStateForGroups by distributor.albumsFlow.collectAsStateWithLifecycle()

                AlbumGroupSheet(
                    sheetState = groupSheetState,
                    mode = groupDialogMode,
                    initialName = groupDialogInitialName,
                    existingGroups = albumsStateForGroups.albumGroups,
                    onCreateGroup = { name ->
                        val albumId = pendingGroupAlbum?.id
                        if (albumId != null) {
                            albumsViewModel.createGroup(name, listOf(albumId))
                        } else {
                            albumsViewModel.createGroup(name, emptyList())
                        }
                        pendingGroupAlbum = null
                    },
                    onRenameGroup = { newName ->
                        groupDialogGroupId?.let { albumsViewModel.renameGroup(it, newName) }
                    },
                    onAddToExistingGroup = { groupId ->
                        pendingGroupAlbum?.let { album ->
                            albumsViewModel.addAlbumToGroup(groupId, album.id)
                        }
                        pendingGroupAlbum = null
                    }
                )

                DeleteGroupSheet(
                    sheetState = deleteGroupSheetState,
                    onConfirmDelete = {
                        pendingDeleteGroupId?.let { albumsViewModel.deleteGroup(it) }
                        pendingDeleteGroupId = null
                    }
                )

                val collectionSheetState = rememberAppBottomSheetState()
                var collectionDialogMode by remember { mutableStateOf("create") }
                var collectionDialogId by remember { mutableStateOf<Long?>(null) }
                var collectionDialogInitialName by remember { mutableStateOf("") }
                val deleteCollectionSheetState = rememberAppBottomSheetState()
                var pendingDeleteCollectionId by remember { mutableStateOf<Long?>(null) }

                CollectionSheet(
                    sheetState = collectionSheetState,
                    mode = collectionDialogMode,
                    initialName = collectionDialogInitialName,
                    onCreateCollection = { name ->
                        eventHandler.navigate(
                            Screen.CollectionAlbumSelectorScreen.collectionName(name)
                        )
                    },
                    onRenameCollection = { newName ->
                        collectionDialogId?.let { albumsViewModel.renameCollection(it, newName) }
                    }
                )

                DeleteCollectionSheet(
                    sheetState = deleteCollectionSheetState,
                    onConfirmDelete = {
                        pendingDeleteCollectionId?.let { albumsViewModel.deleteCollection(it) }
                        pendingDeleteCollectionId = null
                    }
                )

                val moveToSectionSheetState = rememberAppBottomSheetState()
                var pendingMoveAlbum by remember { mutableStateOf<Album?>(null) }
                var pendingMoveCurrentSectionId by remember { mutableStateOf<Long?>(null) }

                MoveToSectionSheet(
                    sheetState = moveToSectionSheetState,
                    sections = albumsStateForGroups.albumSections,
                    currentSectionId = pendingMoveCurrentSectionId,
                    onMoveToSection = { sectionId ->
                        pendingMoveAlbum?.let { album ->
                            albumsViewModel.moveAlbumToSection(album.id, sectionId)
                        }
                        pendingMoveAlbum = null
                        pendingMoveCurrentSectionId = null
                    },
                    onResetOverride = {
                        pendingMoveAlbum?.let { album ->
                            albumsViewModel.resetAlbumSectionOverride(album.id)
                        }
                        pendingMoveAlbum = null
                        pendingMoveCurrentSectionId = null
                    }
                )

                LibraryScreen(
                    paddingValues = paddingValues,
                    editMode = libraryEditMode,
                    onEditModeChange = { libraryEditMode = it },
                ) { albumsPadding, libraryHeader, libraryMenuItems, onLibraryContentDrawn, albumsMinCellWidth ->
                AlbumsScreen(
                    paddingValues = albumsPadding,
                    header = libraryHeader,
                    menuItems = libraryMenuItems,
                    onContentDrawn = onLibraryContentDrawn,
                    minCellWidth = albumsMinCellWidth,
                    isScrolling = isScrolling,
                    onAlbumClick = onAlbumClickWithLock,
                    onAlbumLongClick = albumsViewModel.onAlbumLongClick,
                    filterOptions = albumsViewModel.rememberFilters(),
                    onMoveAlbumToTrash = albumsViewModel::moveAlbumToTrash,
                    onIgnoreAlbum = albumsViewModel::addAlbumToIgnored,
                    onLockAlbum = onLockAlbumWithCheck,
                    onGroupClick = albumsViewModel.onGroupClick(eventHandler::navigate),
                    onRenameGroup = { group ->
                        groupDialogMode = "rename"
                        groupDialogGroupId = group.group.id
                        groupDialogInitialName = group.group.label
                        scope.launch { groupSheetState.show() }
                    },
                    onDeleteGroup = { group ->
                        pendingDeleteGroupId = group.group.id
                        scope.launch { deleteGroupSheetState.show() }
                    },
                    onEditGroup = { group ->
                        eventHandler.navigate(Screen.EditGroupScreen.groupId(group.group.id))
                    },
                    onAddToGroup = { album ->
                        pendingGroupAlbum = album
                        groupDialogMode = "addToGroup"
                        groupDialogInitialName = ""
                        scope.launch { groupSheetState.show() }
                    },
                    onToggleMergeSubfolders = albumsViewModel::toggleMergeSubfolders,
                    onMergeParentSubfolders = albumsViewModel::mergeParentSubfolders,
                    onToggleMergedSubfolderDisplayMode = albumsViewModel::toggleMergedSubfolderDisplayMode,
                    onCollectionClick = { cwc ->
                        eventHandler.navigate(
                            Screen.CollectionViewScreen.collectionId(cwc.collection.id)
                        )
                    },
                    onCollectionRename = { cwc ->
                        collectionDialogMode = "rename"
                        collectionDialogId = cwc.collection.id
                        collectionDialogInitialName = cwc.collection.label
                        scope.launch { collectionSheetState.show() }
                    },
                    onCollectionDelete = { cwc ->
                        pendingDeleteCollectionId = cwc.collection.id
                        scope.launch { deleteCollectionSheetState.show() }
                    },
                    onCollectionTogglePin = { cwc ->
                        albumsViewModel.toggleCollectionPin(
                            cwc.collection.id,
                            !cwc.collection.isPinned
                        )
                    },
                    onCollectionEditAlbums = { cwc ->
                        eventHandler.navigate(
                            Screen.CollectionAlbumSelectorScreen.collectionId(cwc.collection.id)
                        )
                    },
                    onCreateCollection = {
                        collectionDialogMode = "create"
                        collectionDialogId = null
                        collectionDialogInitialName = ""
                        scope.launch { collectionSheetState.show() }
                    },
                    onMoveToSection = if (albumSectionsEnabled) { album ->
                        pendingMoveAlbum = album
                        pendingMoveCurrentSectionId = albumsStateForGroups.albumSections
                            .firstOrNull { swa -> swa.albums.any { it.id == album.id } }
                            ?.section?.id
                        scope.launch { moveToSectionSheetState.show() }
                    } else null,
                    onToggleSectionExpanded = { sectionWithAlbums, expanded ->
                        albumsViewModel.toggleSectionExpanded(
                            sectionWithAlbums.section.id,
                            expanded
                        )
                    },
                    sharedTransitionScope = sharedTransitionScope,
                    animatedContentScope = this
                )
                }
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
                val appName = stringResource(Res.string.app_name)
                val argumentAlbumName = remember(backStackEntry) {
                    backStackEntry.arguments?.getString("albumName") ?: appName
                }
                val argumentAlbumId = remember(backStackEntry) {
                    backStackEntry.arguments?.getLong("albumId") ?: -1
                }
                val albumViewModel = injectViewModel<AlbumTimelineViewModel>(viewModelStoreOwner = backStackEntry) {albumTimelineViewModel(it)}
                val albumInitialSnapshot = remember(backStackEntry) { albumViewModel.mediaState.value }
                LaunchedEffect(backStackEntry) {
                    StartupTracer.trace("AlbumTimeline.initialState(album=$argumentAlbumId,loading=${albumInitialSnapshot.isLoading},items=${albumInitialSnapshot.media.size})") {}
                }
                val albumMediaState = albumViewModel.mediaState.collectAsStateWithLifecycle()
                AlbumTimelineScreen(
                    albumId = argumentAlbumId,
                    albumName = argumentAlbumName,
                    paddingValues = paddingValues,
                    albumMediaState = albumMediaState,
                    metadataState = metadataState(),
                    onAlbumClick = { album ->
                        navController.navigate(Screen.AlbumViewScreen.album(album.id, album.label))
                    },
                    isScrolling = isScrolling,
                    sharedTransitionScope = sharedTransitionScope,
                    animatedContentScope = this
                )
            }
            composable(
                route = Screen.AlbumGroupViewScreen.groupId(),
                arguments = listOf(
                    navArgument(name = "groupId") {
                        type = NavType.LongType
                        defaultValue = -1L
                    }
                )
            ) { backStackEntry ->
                val groupId = remember(backStackEntry) {
                    backStackEntry.arguments?.getLong("groupId") ?: -1L
                }
                val albumsViewModel = injectViewModel<AlbumsViewModel> {albumsViewModel}
                val groupViewScope = rememberCoroutineScope()
                val groupViewRenameSheetState = rememberAppBottomSheetState()
                var groupViewRenameGroupId by remember { mutableStateOf<Long?>(null) }
                var groupViewRenameInitialName by remember { mutableStateOf("") }

                AlbumGroupSheet(
                    sheetState = groupViewRenameSheetState,
                    mode = "rename",
                    initialName = groupViewRenameInitialName,
                    onRenameGroup = { newName ->
                        groupViewRenameGroupId?.let { albumsViewModel.renameGroup(it, newName) }
                    }
                )

                AlbumGroupViewScreen(
                    groupId = groupId,
                    onAlbumClick = { album ->
                        albumsViewModel.onAlbumClick(eventHandler::navigate)(album)
                    },
                    onAlbumLongClick = albumsViewModel.onAlbumLongClick,
                    onMoveAlbumToTrash = albumsViewModel::moveAlbumToTrash,
                    onIgnoreAlbum = albumsViewModel::addAlbumToIgnored,
                    onRemoveFromGroup = { album ->
                        albumsViewModel.removeAlbumFromGroup(groupId, album.id)
                    },
                    onRenameGroup = { group ->
                        groupViewRenameGroupId = group.group.id
                        groupViewRenameInitialName = group.group.label
                        groupViewScope.launch { groupViewRenameSheetState.show() }
                    },
                    onAddAlbums = { id -> eventHandler.navigate(Screen.EditGroupScreen.groupId(id)) },
                    sharedTransitionScope = sharedTransitionScope,
                    animatedContentScope = this
                )
            }
            composable(
                route = Screen.EditGroupScreen.groupId(),
                arguments = listOf(
                    navArgument(name = "groupId") {
                        type = NavType.LongType
                        defaultValue = -1L
                    }
                )
            ) { backStackEntry ->
                val groupId = remember(backStackEntry) {
                    backStackEntry.arguments?.getLong("groupId") ?: -1L
                }
                val albumsViewModel = injectViewModel<AlbumsViewModel> {albumsViewModel}
                EditGroupContent(
                    groupId = groupId,
                    onAddAlbum = { gId, albumId ->
                        albumsViewModel.addAlbumToGroup(gId, albumId)
                    },
                    onRemoveAlbum = { gId, albumId ->
                        albumsViewModel.removeAlbumFromGroup(gId, albumId)
                    }
                )
            }
            composable(
                route = Screen.CollectionAlbumSelectorScreen.collectionName(),
                arguments = listOf(
                    navArgument(name = "collectionName") {
                        type = NavType.StringType
                        defaultValue = ""
                    }
                )
            ) { backStackEntry ->
                val collectionName = remember(backStackEntry) {
                    backStackEntry.arguments?.getString("collectionName") ?: ""
                }
                val albumsViewModel = injectViewModel<AlbumsViewModel> {albumsViewModel}
                CollectionAlbumSelectorContent(
                    collectionName = collectionName,
                    onCreateWithAlbums = { name, albumIds ->
                        albumsViewModel.createCollectionWithAlbums(name, albumIds)
                        navController.popBackStack()
                    },
                    onSkip = { name ->
                        albumsViewModel.createCollection(name)
                        navController.popBackStack()
                    }
                )
            }
            composable(
                route = Screen.CollectionAlbumSelectorScreen.collectionId(),
                arguments = listOf(
                    navArgument(name = "collectionId") {
                        type = NavType.LongType
                        defaultValue = -1L
                    }
                )
            ) { backStackEntry ->
                val collectionId = remember(backStackEntry) {
                    backStackEntry.arguments?.getLong("collectionId") ?: -1L
                }
                val albumsViewModel = injectViewModel<AlbumsViewModel> {albumsViewModel}
                CollectionAlbumSelectorContent(
                    collectionId = collectionId,
                    onReplaceAlbumsInCollection = { id, albumIds ->
                        albumsViewModel.replaceAlbumsInCollection(id, albumIds)
                        navController.popBackStack()
                    }
                )
            }
            composable(
                route = Screen.MediaViewScreen.idAndAlbum(),
                enterTransition = viewerEnterTransition,
                exitTransition = viewerExitTransition,
                popEnterTransition = viewerEnterTransition,
                popExitTransition = viewerExitTransition,
                arguments = listOf(
                    navArgument(name = "mediaId") {
                        type = NavType.LongType
                        defaultValue = -1L
                    },
                    navArgument(name = "albumId") {
                        type = NavType.LongType
                        defaultValue = -1L
                    },
                    navArgument(name = "slideshow") {
                        type = NavType.BoolType
                        defaultValue = false
                    }
                )
            ) { backStackEntry ->
                val mediaId: Long = remember(backStackEntry) {
                    backStackEntry.arguments?.getLong("mediaId") ?: -1L
                }
                val albumId: Long = remember(backStackEntry) {
                    backStackEntry.arguments?.getLong("albumId") ?: -1L
                }
                val slideshow: Boolean = remember(backStackEntry) {
                    backStackEntry.arguments?.getBoolean("slideshow") ?: false
                }
                val isPrivateFolder = albumId == PrivateFolderViewModel.PRIVATE_FOLDER_ALBUM_ID
                val mediaState: State<MediaState<UriMedia>> = when {
                    isPrivateFolder -> {
                        injectViewModel<PrivateFolderViewModel>(viewModelStoreOwner = navController.getBackStackEntry(Screen.PrivateFolderScreen())) {privateFolderViewModel}.mediaState.collectAsStateWithLifecycle()
                    }
                    albumId == -1L -> timelineState()
                    else -> {
                        val owner = remember(backStackEntry, albumId) {
                            albumViewerStateOwner(navController, backStackEntry, albumId)
                        }
                        val albumViewModel = injectViewModel<AlbumTimelineViewModel>(viewModelStoreOwner = owner) {albumTimelineViewModel(it)}
                        val initialSnapshot = remember(backStackEntry) { albumViewModel.mediaState.value }
                        LaunchedEffect(backStackEntry) {
                            StartupTracer.trace("AlbumViewer.initialState(album=$albumId,item=$mediaId,loading=${initialSnapshot.isLoading},items=${initialSnapshot.media.size},index=${initialSnapshot.pagerMedia.indexOfFirst { it.id == mediaId }})") {}
                        }
                        albumViewModel.mediaState.collectAsStateWithLifecycle()
                    }
                }

                MediaViewScreenRoute(
                    toggleRotate = toggleRotate,
                    paddingValues = paddingValues,
                    mediaId = mediaId,
                    mediaState = mediaState,
                    metadataState = metadataState(),
                    albumsState = albumsState(),
                    vaultState = vaultState(),
                    slideshow = slideshow,
                    allowBlur = allowBlur,
                    sharedTransitionScope = sharedTransitionScope,
                    animatedContentScope = this
                )
            }
            composable(
                route = Screen.MediaViewScreen.idAndTarget(),
                enterTransition = viewerEnterTransition,
                exitTransition = viewerExitTransition,
                popEnterTransition = viewerEnterTransition,
                popExitTransition = viewerExitTransition,
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
                val archiveViewModel: CloudArchiveViewModel? =
                    if (target == "cloud_archive") injectViewModel { cloudArchiveViewModel } else null
                val mediaState = remember(target, archiveViewModel) {
                    when (target) {
                        TARGET_FAVORITES -> navViewModel.favoriteMediaState
                        TARGET_TRASH -> navViewModel.trashedMediaState
                        "cloud_archive" -> archiveViewModel!!.mediaState
                        else -> navViewModel.timelineMediaState
                    }
                }.collectAsStateWithLifecycle()

                MediaViewScreenRoute(
                    toggleRotate = toggleRotate,
                    paddingValues = paddingValues,
                    mediaId = mediaId,
                    target = target,
                    mediaState = mediaState,
                    metadataState = metadataState(),
                    albumsState = albumsState(),
                    vaultState = vaultState(),
                    allowBlur = allowBlur,
                    sharedTransitionScope = sharedTransitionScope,
                    animatedContentScope = this
                )
            }
            composable(
                route = Screen.MediaViewScreen.idAndQuery(),
                enterTransition = viewerEnterTransition,
                exitTransition = viewerExitTransition,
                popEnterTransition = viewerEnterTransition,
                popExitTransition = viewerExitTransition,
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
                    searchViewModel().searchResultsState.collectAsStateWithLifecycle()
                val mediaState =
                    remember(searchResultsState.value) { mutableStateOf(searchResultsState.value.results) }
                MediaViewScreenRoute(
                    toggleRotate = toggleRotate,
                    paddingValues = paddingValues,
                    mediaId = mediaId,
                    mediaState = mediaState,
                    metadataState = metadataState(),
                    albumsState = albumsState(),
                    vaultState = vaultState(),
                    allowBlur = allowBlur,
                    sharedTransitionScope = sharedTransitionScope,
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
                    albumsState = albumsState()
                )
            }

            composable(
                route = Screen.VaultScreen()
            ) {
                VaultScreen(
                    paddingValues = paddingValues,
                    toggleRotate = toggleRotate,
                    shouldSkipAuth = shouldSkipAuth,
                    allowBlur = allowBlur
                )
            }

            composable(
                route = Screen.PrivateFolderScreen()
            ) {
                PrivateFolderScreen(
                    paddingValues = paddingValues,
                    isScrolling = isScrolling,
                    metadataState = metadataState(),
                    sharedTransitionScope = sharedTransitionScope,
                    animatedContentScope = this
                )
            }

            composable(
                route = Screen.PrivateFolderSecurityScreen()
            ) {
                PrivateFolderSecuritySetupScreen(
                    onBack = { navController.navigateUp() },
                    onNone = { navController.navigateUp() },
                    onDeviceSecurity = { navController.navigateUp() },
                    onCustomComplete = { navController.navigateUp() }
                )
            }

            composable(
                route = Screen.CategoriesScreen()
            ) {
                val categoriesViewModel = injectViewModel<CategoriesViewModel> {categoriesViewModel}
                val categoriesWithCount by categoriesViewModel.categoriesWithCount.collectAsStateWithLifecycle()
                val distinctMediaCount by categoriesViewModel.distinctClassifiedMediaCount.collectAsStateWithLifecycle()
                val distributor = LocalMediaDistributor.current
                val categoryMediaState by distributor.timelineMediaFlow.collectAsStateWithLifecycle(
                    context = Dispatchers.IO,
                    initialValue = MediaState()
                )
                CategoriesScreen(
                    viewModel = categoriesViewModel,
                    categoriesWithCount = categoriesWithCount,
                    distinctMediaCount = distinctMediaCount,
                    mediaState = categoryMediaState,
                    sharedTransitionScope = sharedTransitionScope,
                    animatedContentScope = this
                )
            }

            composable(
                route = Screen.CategoriesSettingsScreen()
            ) {
                CategoriesSettingsScreen()
            }

            composable(
                route = Screen.LocationsScreen.withMediaId(),
                arguments = listOf(
                    navArgument("mediaId") {
                        type = NavType.LongType
                        defaultValue = -1L
                    }
                )
            ) { backStackEntry ->
                val initialMediaId = remember(backStackEntry) {
                    backStackEntry.arguments?.getLong("mediaId", -1L) ?: -1L
                }
                val locationsViewModel = injectViewModel<CategoriesViewModel> {categoriesViewModel}
                val locations by locationsViewModel.locations.collectAsStateWithLifecycle()
                val geoMedia by locationsViewModel.geoMedia.collectAsStateWithLifecycle()
                LocationsScreen(
                    metadataState = metadataState(),
                    locations = locations,
                    geoMedia = geoMedia,
                    initialMediaId = initialMediaId
                )
            }

            // Unified Category Editor — Create mode
            composable(
                route = Screen.CategoryEditorScreen.create()
            ) {
                CategoryEditorScreen(
                    categoryId = null,
                    paddingValues = paddingValues,
                    isScrolling = isScrolling,
                    metadataState = metadataState(),
                    sharedTransitionScope = sharedTransitionScope,
                    animatedContentScope = this,
                    onNavigateBack = { navController.navigateUp() }
                )
            }

            // Unified Category Editor — Edit mode
            composable(
                route = Screen.CategoryEditorScreen.edit(),
                arguments = listOf(
                    navArgument(name = "categoryId") {
                        type = NavType.LongType
                        defaultValue = -1
                    }
                )
            ) { backStackEntry ->
                val categoryId = remember(backStackEntry) {
                    backStackEntry.arguments?.getLong("categoryId") ?: -1
                }
                CategoryEditorScreen(
                    categoryId = categoryId,
                    paddingValues = paddingValues,
                    isScrolling = isScrolling,
                    metadataState = metadataState(),
                    sharedTransitionScope = sharedTransitionScope,
                    animatedContentScope = this,
                    onNavigateBack = { navController.navigateUp() }
                )
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
                    metadataState = metadataState(),
                    sharedTransitionScope = sharedTransitionScope,
                    animatedContentScope = this
                )
            }

            // New ID-based category view route
            composable(
                route = Screen.CategoryViewScreen.categoryId(),
                arguments = listOf(
                    navArgument(name = "categoryId") {
                        type = NavType.LongType
                        defaultValue = -1L
                    }
                )
            ) { backStackEntry ->
                val categoryId: Long = remember(backStackEntry) {
                    backStackEntry.arguments?.getLong("categoryId") ?: -1L
                }
                CategoryViewScreen(
                    categoryId = categoryId,
                    metadataState = metadataState(),
                    sharedTransitionScope = sharedTransitionScope,
                    animatedContentScope = this
                )
            }

            composable(
                route = Screen.MediaViewScreen.idAndCategory(),
                enterTransition = viewerEnterTransition,
                exitTransition = viewerExitTransition,
                popEnterTransition = viewerEnterTransition,
                popExitTransition = viewerExitTransition,
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

                val viewModel = injectViewModel<CategoryViewModel> {categoryViewModel}.apply {
                    this.category = category
                }
                val mediaState = viewModel.mediaByCategory
                    .collectAsStateWithLifecycle(MediaState())

                MediaViewScreenRoute(
                    toggleRotate = toggleRotate,
                    paddingValues = paddingValues,
                    mediaId = mediaId,
                    target = "category_$category",
                    mediaState = mediaState,
                    metadataState = metadataState(),
                    albumsState = albumsState(),
                    vaultState = vaultState(),
                    allowBlur = allowBlur,
                    sharedTransitionScope = sharedTransitionScope,
                    animatedContentScope = this
                )
            }

            composable(
                route = Screen.MediaViewScreen.idAndCategoryId(),
                enterTransition = viewerEnterTransition,
                exitTransition = viewerExitTransition,
                popEnterTransition = viewerEnterTransition,
                popExitTransition = viewerExitTransition,
                arguments = listOf(
                    navArgument(name = "mediaId") {
                        type = NavType.LongType
                        defaultValue = -1
                    },
                    navArgument(name = "categoryId") {
                        type = NavType.LongType
                        defaultValue = -1
                    }
                )
            ) { backStackEntry ->
                val mediaId: Long = remember(backStackEntry) {
                    backStackEntry.arguments?.getLong("mediaId") ?: -1
                }
                val categoryId: Long = remember(backStackEntry) {
                    backStackEntry.arguments?.getLong("categoryId") ?: -1
                }

                val viewModel = injectViewModel<CategoryViewModel> {categoryViewModel}.apply {
                    setCategoryId(categoryId)
                }
                val mediaState = viewModel.mediaByCategoryId
                    .collectAsStateWithLifecycle(MediaState())

                MediaViewScreenRoute(
                    toggleRotate = toggleRotate,
                    paddingValues = paddingValues,
                    mediaId = mediaId,
                    target = "categoryId_$categoryId",
                    mediaState = mediaState,
                    metadataState = metadataState(),
                    albumsState = albumsState(),
                    vaultState = vaultState(),
                    allowBlur = allowBlur,
                    sharedTransitionScope = sharedTransitionScope,
                    animatedContentScope = this
                )
            }

            composable(
                route = Screen.CollectionViewScreen.collectionId(),
                arguments = listOf(
                    navArgument(name = "collectionId") {
                        type = NavType.LongType
                        defaultValue = -1
                    }
                )
            ) { backStackEntry ->
                val collectionId: Long = remember(backStackEntry) {
                    backStackEntry.arguments?.getLong("collectionId") ?: -1
                }
                val collectionViewModel = injectViewModel<CollectionViewModel> {collectionViewModel}
                val collections by collectionViewModel.collectionsWithCount.collectAsStateWithLifecycle()
                val collectionName = remember(collections, collectionId) {
                    collections.find { it.collection.id == collectionId }?.collection?.label ?: ""
                }

                CollectionViewScreen(
                    collectionId = collectionId,
                    collectionName = collectionName,
                    paddingValues = paddingValues,
                    isScrolling = isScrolling,
                    metadataState = metadataState(),
                    onEditAlbums = {
                        eventHandler.navigate(
                            Screen.CollectionAlbumSelectorScreen.collectionId(collectionId)
                        )
                    },
                    sharedTransitionScope = sharedTransitionScope,
                    animatedContentScope = this
                )
            }

            composable(
                route = Screen.MediaViewScreen.idAndCollection(),
                enterTransition = viewerEnterTransition,
                exitTransition = viewerExitTransition,
                popEnterTransition = viewerEnterTransition,
                popExitTransition = viewerExitTransition,
                arguments = listOf(
                    navArgument(name = "mediaId") {
                        type = NavType.LongType
                        defaultValue = -1
                    },
                    navArgument(name = "collectionId") {
                        type = NavType.LongType
                        defaultValue = -1
                    },
                    navArgument(name = "slideshow") {
                        type = NavType.BoolType
                        defaultValue = false
                    }
                )
            ) { backStackEntry ->
                val mediaId: Long = remember(backStackEntry) {
                    backStackEntry.arguments?.getLong("mediaId") ?: -1
                }
                val collectionId: Long = remember(backStackEntry) {
                    backStackEntry.arguments?.getLong("collectionId") ?: -1
                }
                val slideshow: Boolean = remember(backStackEntry) {
                    backStackEntry.arguments?.getBoolean("slideshow") ?: false
                }

                val distributor = LocalMediaDistributor.current
                val collectionMediaFlow = remember(collectionId) {
                    distributor.collectionMediaFlow(collectionId)
                }
                val mediaState = collectionMediaFlow.collectAsStateWithLifecycle()

                MediaViewScreenRoute(
                    toggleRotate = toggleRotate,
                    paddingValues = paddingValues,
                    mediaId = mediaId,
                    target = "collection_$collectionId",
                    slideshow = slideshow,
                    mediaState = mediaState,
                    metadataState = metadataState(),
                    albumsState = albumsState(),
                    vaultState = vaultState(),
                    allowBlur = allowBlur,
                    sharedTransitionScope = sharedTransitionScope,
                    animatedContentScope = this
                )
            }

            composable(Screen.DateFormatScreen()) {
                DateFormatScreen()
            }
            composable(Screen.ColorPaletteScreen()) {
                ColorPaletteScreen()
            }
            composable(Screen.SettingsGeneralScreen()) {
                SettingsGeneralScreen()
            }
            composable(Screen.SettingsSmartFeaturesScreen()) {
                SettingsSmartFeaturesScreen()
            }
            composable(Screen.SettingsCloudIndexingScreen()) {
                SettingsCloudIndexingScreen()
            }
            composable(Screen.SettingsHandoverScreen()) {
                SettingsHandoverScreen()
            }
            composable(Screen.SmartScanPreferenceDetailScreen()) {
                SmartScanPreferenceDetailScreen()
            }
            composable(Screen.AIModelsManagerScreen()) {
                AIModelsManagerScreen()
            }
            composable(
                route = Screen.IntelligenceFeatureScreen.feature(),
                arguments = listOf(
                    navArgument(name = "feature") {
                        type = NavType.StringType
                        defaultValue = IntelligenceFeature.FACE_GROUPING.name
                    },
                    navArgument(name = "offer") {
                        type = NavType.BoolType
                        defaultValue = false
                    }
                )
            ) { backStackEntry ->
                val feature = remember(backStackEntry) {
                    IntelligenceFeature.fromName(backStackEntry.arguments?.getString("feature"))
                }
                val offer = remember(backStackEntry) {
                    backStackEntry.arguments?.getBoolean("offer") ?: false
                }
                IntelligenceFeatureScreen(
                    feature = feature,
                    showNotNow = offer,
                    onScanForPeople = {
                        // Opened from People: go back to it rather than stacking a second copy.
                        if (!navController.popBackStack(Screen.PeopleListScreen(), inclusive = false)) {
                            navController.navigate(Screen.PeopleListScreen()) {
                                popUpTo(Screen.IntelligenceFeatureScreen.feature()) { inclusive = true }
                            }
                        }
                    },
                )
            }
            composable(Screen.SettingsAppearanceScreen()) {
                ColorPaletteScreen()
            }
            composable(Screen.SettingsTimelineAlbumsScreen()) {
                SettingsTimelineAlbumsScreen()
            }
            composable(Screen.SettingsMediaViewerScreen()) {
                SettingsMediaViewerScreen()
            }
            composable(Screen.SettingsNavigationScreen()) {
                SettingsNavigationScreen()
            }
            composable(Screen.SettingsSecurityScreen()) {
                SettingsSecurityScreen()
            }
            composable(Screen.SettingsBackupScreen()) {
                com.dot.gallery.app.presentation.settings.subsettings.SettingsBackupHubScreen(
                    onExport = { navController.navigate(Screen.SettingsBackupExportScreen()) },
                    onImport = { navController.navigate(Screen.SettingsBackupImportScreen()) }
                )
            }
            composable(Screen.SettingsBackupExportScreen()) {
                SettingsBackupExportScreen(
                    navigateUp = { navController.navigateUp() }
                )
            }
            composable(Screen.SettingsBackupImportScreen()) {
                SettingsBackupImportScreen(
                    navigateUp = { navController.navigateUp() }
                )
            }
            composable(Screen.DeveloperScreen()) {
                DeveloperScreen()
            }
            composable(Screen.SessionLogsScreen()) {
                SessionLogsScreen()
            }
            composable(Screen.SettingsSelectionActionsScreen()) {
                SettingsSelectionActionsContent()
            }
            composable(Screen.SettingsViewerActionsScreen()) {
                SettingsViewerActionsContent()
            }
            composable(Screen.SlideshowSettingsScreen()) {
                SlideshowSettingsScreen()
            }
            composable(Screen.CloudAccountsScreen()) {
                CloudAccountsScreen()
            }
            // Legacy/debug-only route: cloud media lives in the unified timeline. Keep old deep
            // links working now that the standalone cloud timeline screen is gone.
            composable(Screen.CloudTimelineScreen()) {
                LaunchedEffect(Unit) {
                    navController.navigate(Screen.CloudBackupDashboardScreen()) {
                        popUpTo(Screen.CloudTimelineScreen()) { inclusive = true }
                    }
                }
            }
            composable(
                route = Screen.CloudAddServerScreen.providerType(),
                arguments = listOf(
                    navArgument(name = "providerType") {
                        type = NavType.StringType
                        defaultValue = ProviderType.IMMICH.name
                    }
                )
            ) { backStackEntry ->
                val typeName = remember(backStackEntry) {
                    backStackEntry.arguments?.getString("providerType") ?: ProviderType.IMMICH.name
                }
                val type = remember(typeName) {
                    try { ProviderType.valueOf(typeName) } catch (_: Exception) { ProviderType.IMMICH }
                }
                CloudAddServerScreen(
                    providerType = type,
                    onSaved = { navController.navigateUp() }
                )
            }
            composable(
                route = Screen.CloudEditServerScreen.configId(),
                arguments = listOf(
                    navArgument(name = "configId") {
                        type = NavType.LongType
                        defaultValue = -1L
                    }
                )
            ) { backStackEntry ->
                val configId = remember(backStackEntry) {
                    backStackEntry.arguments?.getLong("configId") ?: -1L
                }
                CloudAddServerScreen(
                    providerType = ProviderType.IMMICH,
                    configId = configId,
                    onSaved = { navController.navigateUp() }
                )
            }
            composable(
                route = Screen.CloudUploadSettingsScreen.configId(),
                arguments = listOf(
                    navArgument(name = "configId") {
                        type = NavType.LongType
                        defaultValue = -1L
                    }
                )
            ) {
                CloudUploadSettingsScreen(
                    navigateUp = { navController.navigateUp() }
                )
            }
            composable(
                route = Screen.CloudProviderSettingsScreen.configId(),
                arguments = listOf(
                    navArgument(name = "configId") {
                        type = NavType.LongType
                        defaultValue = -1L
                    }
                )
            ) { backStackEntry ->
                val configId = remember(backStackEntry) {
                    backStackEntry.arguments?.getLong("configId") ?: -1L
                }
                CloudProviderSettingsScreen(
                    configId = configId,
                    onDeleted = { navController.navigateUp() }
                )
            }
            composable(Screen.EditBackupsViewerScreen()) {
                EditBackupsViewerScreen()
            }

            composable(Screen.CloudArchiveScreen()) {
                val archiveViewModel = injectViewModel<CloudArchiveViewModel> {cloudArchiveViewModel}
                val archiveMediaState = archiveViewModel.mediaState.collectAsStateWithLifecycle()
                CloudArchiveScreen(
                    paddingValues = paddingValues,
                    mediaState = archiveMediaState,
                    metadataState = metadataState(),
                    clearSelection = selector::clearSelection,
                    sharedTransitionScope = sharedTransitionScope,
                    animatedContentScope = this
                )
            }

            composable(Screen.CloudBackupScreen()) {
                LaunchedEffect(Unit) {
                    navController.navigate(Screen.CloudBackupDashboardScreen()) {
                        popUpTo(Screen.CloudBackupScreen()) { inclusive = true }
                    }
                }
            }

            composable(Screen.CloudBackupAndSyncScreen()) {
                LaunchedEffect(Unit) {
                    navController.navigate(Screen.CloudBackupDashboardScreen()) {
                        popUpTo(Screen.CloudBackupAndSyncScreen()) { inclusive = true }
                    }
                }
            }

            composable(Screen.CloudBackupDashboardScreen()) {
                CloudBackupDashboardScreen(
                    onNavigateToAlbumPicker = { configId ->
                        // The Destinations screen scoped to this service acts as its album picker.
                        navController.navigate(Screen.CloudDestinationsScreen.configId(configId))
                    },
                    onNavigateToServiceSettings = { configId ->
                        navController.navigate(Screen.CloudProviderSettingsScreen.configId(configId))
                    },
                    onNavigateToBackupOptions = {
                        navController.navigate(Screen.BackupOptionsScreen())
                    },
                    onNavigateToUploadDetails = {
                        navController.navigate(Screen.UploadDetailsScreen())
                    },
                    onNavigateToAccounts = {
                        navController.navigate(Screen.CloudAccountsScreen())
                    },
                    onNavigateToDestinations = {
                        navController.navigate(Screen.CloudDestinationsScreen())
                    },
                    onNavigateToOffline = {
                        navController.navigate(Screen.CloudOfflineModeScreen())
                    },
                    onNavigateToFreeUpSpace = {
                        navController.navigate(Screen.FreeUpSpaceScreen.configId(-1L))
                    }
                )
            }

            composable(
                route = Screen.CloudDestinationsScreen.configId(),
                arguments = listOf(
                    navArgument(name = "configId") {
                        type = NavType.LongType
                        defaultValue = -1L
                    }
                )
            ) {
                CloudDestinationsScreen(
                    onAddAccount = {
                        navController.navigate(Screen.CloudAccountsScreen())
                    }
                )
            }

            composable(
                route = Screen.BackupOptionsScreen.configId(),
                arguments = listOf(
                    navArgument(name = "configId") {
                        type = NavType.LongType
                        defaultValue = -1L
                    }
                )
            ) { backStackEntry ->
                BackupOptionsScreen(
                    configId = backStackEntry.arguments?.getLong("configId") ?: -1L
                )
            }

            composable(Screen.UploadDetailsScreen()) {
                UploadDetailsScreen()
            }

            composable(Screen.SharedLinksScreen()) {
                SharedLinksScreen()
            }

            composable(Screen.PeopleListScreen()) {
                PeopleListScreen(
                    onPersonClick = { person ->
                        navController.navigate(
                            Screen.PersonDetailScreen.personId(person.serverConfigId, person.id)
                        )
                    },
                    onSetupFaceGrouping = {
                        navController.navigate(
                            Screen.IntelligenceFeatureScreen.feature(
                                IntelligenceFeature.FACE_GROUPING.name, offer = true
                            )
                        )
                    }
                )
            }

            composable(
                route = Screen.PersonDetailScreen.personId(),
                arguments = listOf(
                    navArgument("configId") {
                        type = NavType.LongType
                        defaultValue = Long.MIN_VALUE
                    },
                    navArgument("personId") { defaultValue = "" }
                )
            ) {
                PersonDetailScreen(
                    metadataState = metadataState(),
                    sharedTransitionScope = sharedTransitionScope,
                    animatedContentScope = this,
                )
            }

            composable(
                route = Screen.MediaViewScreen.idAndPerson(),
                enterTransition = viewerEnterTransition,
                exitTransition = viewerExitTransition,
                popEnterTransition = viewerEnterTransition,
                popExitTransition = viewerExitTransition,
                arguments = listOf(
                    navArgument(name = "mediaId") {
                        type = NavType.LongType
                        defaultValue = -1
                    },
                    navArgument(name = "configId") {
                        type = NavType.LongType
                        defaultValue = Long.MIN_VALUE
                    },
                    navArgument(name = "personId") {
                        type = NavType.StringType
                        defaultValue = ""
                    }
                )
            ) { backStackEntry ->
                val mediaId: Long = remember(backStackEntry) {
                    backStackEntry.arguments?.getLong("mediaId") ?: -1
                }
                val personId: String = remember(backStackEntry) {
                    backStackEntry.arguments?.getString("personId").orEmpty()
                }
                // injectViewModel reads personId from this entry's SavedStateHandle (the nav arg),
                // so the viewer pages over the person's media instead of the whole timeline.
                val personViewModel = injectViewModel<PersonDetailViewModel> {personDetailViewModel(it)}
                val mediaState = personViewModel.mediaState.collectAsStateWithLifecycle()

                MediaViewScreenRoute(
                    toggleRotate = toggleRotate,
                    paddingValues = paddingValues,
                    mediaId = mediaId,
                    target = "person_$personId",
                    mediaState = mediaState,
                    metadataState = metadataState(),
                    albumsState = albumsState(),
                    vaultState = vaultState(),
                    allowBlur = allowBlur,
                    sharedTransitionScope = sharedTransitionScope,
                    animatedContentScope = this
                )
            }

            composable(
                route = Screen.FreeUpSpaceScreen.configId(),
                arguments = listOf(
                    navArgument(name = "configId") {
                        type = NavType.LongType
                        defaultValue = -1L
                    }
                )
            ) {
                FreeUpSpaceScreen()
            }

            composable(Screen.MemoriesScreen()) {
                val memoriesStoryViewModel = storyCardsViewModel
                    ?: injectViewModel<StoryCardsViewModel>(viewModelStoreOwner = rootViewModelStoreOwner) { this.storyCardsViewModel }
                MemoriesScreen(onOpenStory = memoriesStoryViewModel::openCards)
            }

            composable(
                route = Screen.CloudNotificationSettingsScreen.configId(),
                arguments = listOf(
                    navArgument(name = "configId") {
                        type = NavType.LongType
                        defaultValue = -1L
                    }
                )
            ) { backStackEntry ->
                CloudNotificationSettingsScreen(
                    configId = backStackEntry.arguments?.getLong("configId") ?: -1L
                )
            }

            composable(
                route = Screen.CloudNetworkingScreen.configId(),
                arguments = listOf(
                    navArgument(name = "configId") {
                        type = NavType.LongType
                        defaultValue = -1L
                    }
                )
            ) { backStackEntry ->
                CloudNetworkingScreen(
                    configId = backStackEntry.arguments?.getLong("configId") ?: -1L
                )
            }

            composable(
                route = Screen.CloudViewerSettingsScreen.configId(),
                arguments = listOf(
                    navArgument(name = "configId") {
                        type = NavType.LongType
                        defaultValue = -1L
                    }
                )
            ) { backStackEntry ->
                CloudViewerSettingsScreen(
                    configId = backStackEntry.arguments?.getLong("configId") ?: -1L
                )
            }

            composable(
                route = Screen.CloudAdvancedSettingsScreen.configId(),
                arguments = listOf(
                    navArgument(name = "configId") {
                        type = NavType.LongType
                        defaultValue = -1L
                    }
                )
            ) { backStackEntry ->
                CloudAdvancedSettingsScreen(
                    configId = backStackEntry.arguments?.getLong("configId") ?: -1L
                )
            }

            composable(Screen.CloudOfflineModeScreen()) {
                OfflineModeScreen()
            }

            composable(Screen.SearchScreen()) {
                SearchScreen(
                    viewModel = searchViewModel(),
                    isScrolling = isScrolling,
                    sharedTransitionScope = sharedTransitionScope,
                    animatedContentScope = this
                )
            }

            composable(Screen.HelpScreen()) {
                HelpScreen()
            }

            composable(
                route = Screen.TutorialCategoryScreen.category(),
                arguments = listOf(
                    navArgument(name = "category") {
                        type = NavType.StringType
                        defaultValue = ""
                    }
                )
            ) { backStackEntry ->
                val category = remember(backStackEntry) {
                    backStackEntry.arguments?.getString("category", "") ?: ""
                }
                TutorialCategoryScreen(categoryName = category)
            }

            composable(
                route = Screen.TutorialDetailScreen.tipId(),
                arguments = listOf(
                    navArgument(name = "tipId") {
                        type = NavType.StringType
                        defaultValue = ""
                    }
                )
            ) { backStackEntry ->
                val tipId = remember(backStackEntry) {
                    backStackEntry.arguments?.getString("tipId", "") ?: ""
                }
                TutorialDetailScreen(tipId = tipId)
            }

            composable(Screen.WhatsNewScreen()) {
                WhatsNewScreen()
            }

            composable(Screen.LocationTimelineScreen.location()) { backStackEntry ->
                val gpsLocationNameCity: String = remember(backStackEntry) {
                    backStackEntry.arguments?.getString("gpsLocationNameCity").orEmpty()
                }
                val gpsLocationNameCountry: String = remember(backStackEntry) {
                    backStackEntry.arguments?.getString("gpsLocationNameCountry").orEmpty()
                }
                val latitude = remember(backStackEntry) {
                    backStackEntry.arguments?.getString("latitude")?.toDoubleOrNull()
                }
                val longitude = remember(backStackEntry) {
                    backStackEntry.arguments?.getString("longitude")?.toDoubleOrNull()
                }

                val locationsViewModel =
                    injectViewModel<LocationsViewModel>(key = "LocationViewModel") {
                        locationsViewModel(gpsLocationNameCity, gpsLocationNameCountry, latitude, longitude)
                    }
                val mediaState = locationsViewModel.mediaState.collectAsStateWithLifecycle()
                val latestGeoMedia by locationsViewModel.latestGeoMedia.collectAsStateWithLifecycle()

                LocationTimelineScreen(
                    gpsLocationNameCity = gpsLocationNameCity,
                    gpsLocationNameCountry = gpsLocationNameCountry,
                    latitude = latitude,
                    longitude = longitude,
                    mediaState = mediaState,
                    latestGeoMedia = latestGeoMedia,
                    metadataState = metadataState(),
                    paddingValues = paddingValues,
                    isScrolling = isScrolling,
                    sharedTransitionScope = sharedTransitionScope,
                    animatedContentScope = this
                )
            }

            composable(
                route = Screen.MetadataViewScreen.uriAndType(),
                arguments = listOf(
                    navArgument(name = "mediaUri") {
                        type = NavType.StringType
                        defaultValue = ""
                    },
                    navArgument(name = "isVideo") {
                        type = NavType.BoolType
                        defaultValue = false
                    }
                )
            ) { backStackEntry ->
                val mediaUri = remember(backStackEntry) {
                    backStackEntry.arguments?.getString("mediaUri") ?: ""
                }
                val isVideo = remember(backStackEntry) {
                    backStackEntry.arguments?.getBoolean("isVideo") ?: false
                }
                val metadataViewViewModel = injectViewModel<MetadataViewViewModel> {metadataViewViewModel}
                val metadataViewState by metadataViewViewModel.state.collectAsStateWithLifecycle()
                LaunchedEffect(mediaUri) {
                    metadataViewViewModel.loadMetadata(mediaUri, isVideo)
                }
                MetadataViewContent(
                    state = metadataViewState
                )
            }

            composable(
                route = Screen.MediaViewScreen.idAndLocation(),
                enterTransition = viewerEnterTransition,
                exitTransition = viewerExitTransition,
                popEnterTransition = viewerEnterTransition,
                popExitTransition = viewerExitTransition
            ) { backStackEntry ->
                val mediaId: Long = remember(backStackEntry) {
                    backStackEntry.arguments?.getString("mediaId")?.toLongOrNull() ?: -1
                }
                val gpsLocationNameCity: String = remember(backStackEntry) {
                    backStackEntry.arguments?.getString("gpsLocationNameCity").orEmpty()
                }
                val gpsLocationNameCountry: String = remember(backStackEntry) {
                    backStackEntry.arguments?.getString("gpsLocationNameCountry").orEmpty()
                }
                val latitude = remember(backStackEntry) {
                    backStackEntry.arguments?.getString("latitude")?.toDoubleOrNull()
                }
                val longitude = remember(backStackEntry) {
                    backStackEntry.arguments?.getString("longitude")?.toDoubleOrNull()
                }
                val slideshow = remember(backStackEntry) {
                    backStackEntry.arguments?.getString("slideshow")?.toBoolean() ?: false
                }
                val parentEntry = remember(backStackEntry) {
                    navController.getBackStackEntry(Screen.LocationTimelineScreen.location())
                }

                val locationsViewModel =
                    injectViewModel<LocationsViewModel>(
                        viewModelStoreOwner = parentEntry,
                        key = "LocationViewModel"
                    ) {
                        locationsViewModel(gpsLocationNameCity, gpsLocationNameCountry, latitude, longitude)
                    }
                val mediaState = locationsViewModel.mediaState.collectAsStateWithLifecycle()

                MediaViewScreenRoute(
                    toggleRotate = toggleRotate,
                    paddingValues = paddingValues,
                    mediaId = mediaId,
                    mediaState = mediaState,
                    metadataState = metadataState(),
                    albumsState = albumsState(),
                    vaultState = vaultState(),
                    target = "location_${gpsLocationNameCity}_${gpsLocationNameCountry}_${latitude}_$longitude",
                    slideshow = slideshow,
                    allowBlur = allowBlur,
                    sharedTransitionScope = sharedTransitionScope,
                    animatedContentScope = this
                )
            }

            composable(
                route = Screen.StoryViewerScreen.cardId(),
                enterTransition = viewerEnterTransition,
                exitTransition = viewerExitTransition,
                popEnterTransition = viewerEnterTransition,
                popExitTransition = viewerExitTransition,
                arguments = listOf(
                    navArgument(name = "cardId") {
                        type = NavType.LongType
                        defaultValue = -1L
                    }
                )
            ) { backStackEntry ->
                val cardId = remember(backStackEntry) {
                    backStackEntry.arguments?.getLong("cardId") ?: -1L
                }
                val routeStoryCardsViewModel = storyCardsViewModel
                    ?: injectViewModel<StoryCardsViewModel>(viewModelStoreOwner = rootViewModelStoreOwner) { this.storyCardsViewModel }
                val cards by routeStoryCardsViewModel.allCards.collectAsStateWithLifecycle()
                val snapshot by routeStoryCardsViewModel.viewerSnapshot.collectAsStateWithLifecycle()
                val metadata by routeStoryCardsViewModel.metadataFlow.collectAsStateWithLifecycle()
                val metadataMap = remember(metadata) { metadata.associateBy { it.mediaId } }

                LaunchedEffect(cardId, cards, snapshot) {
                    if (snapshot == null) routeStoryCardsViewModel.prepareViewer(cardId)
                }

                StoryViewerScreen(
                    cards = snapshot?.cards ?: cards,
                    initialCardId = snapshot?.initialCardId ?: cardId,
                    metadataMap = metadataMap,
                    onEnsureMetadata = routeStoryCardsViewModel::ensureMetadataAvailable,
                    onDismiss = {
                        routeStoryCardsViewModel.clearViewerSnapshot()
                        navController.navigateUp()
                    },
                    onOpenAllMemories = {
                        routeStoryCardsViewModel.clearViewerSnapshot()
                        navController.navigate(Screen.MemoriesScreen()) {
                            popUpTo(Screen.StoryViewerScreen.cardId()) { inclusive = true }
                        }
                    },
                )
            }

            composable(Screen.StoryCardsSettingsScreen()) {
                StoryCardsSettingsScreen(
                    onNavigateBack = { navController.navigateUp() }
                )
            }

    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LockDisclaimerSheet(
    sheetState: AppBottomSheetState,
    onConfirm: () -> Unit
) {
    val scope = rememberCoroutineScope()
    if (sheetState.isVisible) {
        ModalBottomSheet(
            sheetState = sheetState.sheetState,
            onDismissRequest = {
                scope.launch { sheetState.hide() }
            },
            containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
            tonalElevation = 0.dp,
            dragHandle = { DragHandle() },
            contentWindowInsets = { WindowInsets(0, 0, 0, 0) }
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 32.dp, vertical = 16.dp)
            ) {
                Text(
                    text = stringResource(Res.string.lock_album),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier
                        .padding(bottom = 16.dp)
                        .fillMaxWidth()
                )
                Text(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            shape = RoundedCornerShape(12.dp)
                        )
                        .padding(16.dp),
                    text = stringResource(Res.string.lock_album_disclaimer),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    SetupButton(
                        onClick = {
                            scope.launch { sheetState.hide() }
                        },
                        containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                        contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                        applyHorizontalPadding = false,
                        applyBottomPadding = false,
                        applyInsets = false,
                        modifier = Modifier.weight(1f),
                        text = stringResource(Res.string.action_cancel)
                    )
                    SetupButton(
                        onClick = {
                            onConfirm()
                            scope.launch { sheetState.hide() }
                        },
                        applyHorizontalPadding = false,
                        applyBottomPadding = false,
                        applyInsets = false,
                        modifier = Modifier.weight(1f),
                        text = stringResource(Res.string.lock_album)
                    )
                }
            }
        }
    }
}