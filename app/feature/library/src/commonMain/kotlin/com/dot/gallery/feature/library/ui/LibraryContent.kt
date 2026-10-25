/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.feature.library.ui

import androidx.compose.animation.AnimatedContentScope
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ImageSearch
import androidx.compose.material.icons.outlined.People
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.backhandler.BackHandler
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.dot.gallery.core.Constants.albumCellsList
import com.dot.gallery.core.ScrollToTopHandler
import com.dot.gallery.core.animateOrJumpToTop
import com.dot.gallery.core.ml.ModelStatus
import com.dot.gallery.core.presentation.components.FloatingTopBarScrim
import com.dot.gallery.core.presentation.components.TopBarScrimScrollFade
import com.dot.gallery.core.presentation.components.topScrimVisibility
import com.dot.gallery.core.presentation.components.GridPinchZoomLayout
import com.dot.gallery.core.presentation.components.LocalMediaImageRenderer
import com.dot.gallery.core.presentation.components.rememberGridPinchZoomState
import com.dot.gallery.core.presentation.util.LocalHazeState
import com.dot.gallery.core.presentation.util.categorySharedElement
import com.dot.gallery.core.presentation.util.hazeEffectScaled
import com.dot.gallery.core.ui.settings.rememberPreference
import com.dot.gallery.core.util.rememberFeedbackManager
import com.dot.gallery.domain.settings.SECTION_CATEGORIES
import com.dot.gallery.domain.settings.SECTION_PEOPLE
import com.dot.gallery.domain.settings.SettingsKeys
import com.dot.gallery.domain.util.getUri
import com.dot.gallery.feature.library.ui.components.EditableLibraryShortcutsGrid
import com.dot.gallery.feature.library.ui.components.LibrarySectionsEditCard
import com.dot.gallery.feature.library.ui.components.LibrarySmallItem
import com.dot.gallery.feature.library.ui.components.MapPreviewCard
import com.dot.gallery.feature.library.ui.components.NoCategories
import com.dot.gallery.ui.theme.BlackScrim
import com.dot.gallery.ui.theme.WhiterBlackScrim
import com.dot.gallery.ui.theme.isDarkTheme
import dev.chrisbanes.haze.blur.materials.HazeMaterials
import dev.chrisbanes.haze.hazeSource
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import com.dot.gallery.feature.library.generated.resources.Res
import com.dot.gallery.feature.library.generated.resources.categories
import com.dot.gallery.feature.library.generated.resources.category_media_count
import com.dot.gallery.feature.library.generated.resources.cloud_people
import com.dot.gallery.feature.library.generated.resources.locations
import com.dot.gallery.feature.library.generated.resources.people_hidden_count
import com.dot.gallery.feature.library.generated.resources.settings_title


/**
 * The whole Library screen body — state in via [state], events out via
 * [actions], app-owned navigation via [routes].
 *
 * Platform seams stay outside this module:
 * - [searchBar]: the app's `MainSearchBar` wiring (shared transitions, search
 *   route). Receives the insets the content measured and the menu slot.
 * - [mapContent]: the static-map renderer for the locations header
 *   (`StaticMapPreview` app-side; null on builds without maps).
 * - Inset visibility (`statusBarsVisible`/`navigationBarsVisible` on
 *   [LibraryState]) is measured app-side from `ViewCompat`.
 * - The startup gate (`StartupContentEffect`) wraps this composable app-side.
 */
@OptIn(
    ExperimentalSharedTransitionApi::class,
    ExperimentalComposeUiApi::class,
    ExperimentalMaterial3Api::class
)
@Composable
fun LibraryContent(
    state: LibraryState,
    actions: LibraryActions,
    routes: LibraryRoutes,
    paddingValues: PaddingValues,
    isScrolling: MutableState<Boolean>,
    sharedTransitionScope: SharedTransitionScope,
    animatedContentScope: AnimatedContentScope,
    searchBar: @Composable (statusBarInsets: WindowInsets, menuItems: @Composable RowScope.() -> Unit) -> Unit,
    mapContent: (@Composable BoxScope.(latitude: Double, longitude: Double) -> Unit)? = null,
    /** Docs/test seam: opens the shortcut edit UI directly. */
    initialEditMode: Boolean = false,
) {
    val snapshot = state.snapshot
    val modelStatus = state.modelStatus
    val aiAvailable = state.aiAvailable
    val feedbackManager = rememberFeedbackManager()
    var lastCellIndex by remember(state.cellsIndex) {
        mutableStateOf(state.cellsIndex)
    }

    val locations = snapshot.locations.orEmpty()
    var showLocationCategories by rememberPreference(SettingsKeys.showLocationCategories)
    var hiddenSections by rememberPreference(SettingsKeys.hiddenSections)
    val indicatorState = snapshot.indicators

    // New category system
    val categoryItems = snapshot.categories
    val topCategories = categoryItems.orEmpty()
    val totalCategoryCount = snapshot.categoryCount
    val noCategoriesFound = noCategories(categoryItems)
    val latestGeo = snapshot.latestGeo

    // Cloud state
    val cloudState = snapshot.cloud

    var noClassification by rememberPreference(SettingsKeys.noClassification)
    val mapsEnabled = state.mapsEnabled
    val isDark = isDarkTheme()

    // Section visibility = user toggle (edit-mode Sections card / settings)
    // AND content — an empty section hides entirely. People is the exception:
    // its header stays while hidden people exist so they remain reachable (#1262).
    val showLocationsSection = showLocationCategories && locations.isNotEmpty()
    val showPeopleSection = SECTION_PEOPLE !in hiddenSections &&
        cloudState.hasPeople &&
        (cloudState.people.isNotEmpty() || cloudState.hiddenPeopleCount > 0)
    val showCategoriesSection = SECTION_CATEGORIES !in hiddenSections &&
        aiAvailable && !noClassification

    val containerSize = LocalWindowInfo.current.containerSize
    val layoutDirection = LocalLayoutDirection.current
    val densityState = LocalDensity.current
    val density = densityState.density
    val configurationSignature =
        "${containerSize.width}/${containerSize.height}/" +
            "$density/${densityState.fontScale}/$layoutDirection/$lastCellIndex"
    val sameConfiguration = snapshot.viewport.configuration == configurationSignature
    val statusBarTop = initialLibraryInset(
        WindowInsets.statusBars.getTop(densityState),
        snapshot.viewport.statusBarTop,
        sameConfiguration,
        state.statusBarsVisible
    )
    val navigationBarBottom = initialLibraryInset(
        with(densityState) { state.bottomBarInset.roundToPx() },
        snapshot.viewport.navigationBarBottom,
        sameConfiguration,
        state.navigationBarsVisible
    )
    val bottomBarInset = with(densityState) { navigationBarBottom.toDp() }

    val gridState = rememberLazyGridState(
        initialFirstVisibleItemIndex = restoredLibraryIndex(
            libraryGridSectionKeys(
                hasLocations = showLocationsSection,
                hasLocationsList = locations.isNotEmpty(),
                hasPeople = showPeopleSection,
                hasPeopleList = cloudState.people.isNotEmpty(),
                hasCategories = showCategoriesSection && topCategories.isNotEmpty(),
                hasNoCategories = showCategoriesSection &&
                    noCategoriesFound && modelStatus == ModelStatus.READY,
            ),
            snapshot.viewport.grid
        ),
        initialFirstVisibleItemScrollOffset =
            if (sameConfiguration) snapshot.viewport.grid.offset else 0
    )
    LaunchedEffect(gridState) {
        val position = snapshotFlow { gridState.measuredScrollPosition() }
            .filterNotNull().first()
        actions.trace("Library.firstLayout(index=${position.index},offset=${position.offset})")
    }
    val locationsListState = rememberLazyListState(
        initialFirstVisibleItemIndex = restoredLibraryIndex(
            locations.map { it.media.id.toString() },
            snapshot.viewport.locations
        ),
        initialFirstVisibleItemScrollOffset =
            if (sameConfiguration) snapshot.viewport.locations.offset else 0
    )
    val peopleListState = rememberLazyListState(
        initialFirstVisibleItemIndex = restoredLibraryIndex(
            cloudState.people.map { it.accountKey },
            snapshot.viewport.people
        ),
        initialFirstVisibleItemScrollOffset =
            if (sameConfiguration) snapshot.viewport.people.offset else 0
    )
    val categoriesListState = rememberLazyListState(
        initialFirstVisibleItemIndex = restoredLibraryIndex(
            topCategories.map { "category_${it.id}" },
            snapshot.viewport.categories
        ),
        initialFirstVisibleItemScrollOffset =
            if (sameConfiguration) snapshot.viewport.categories.offset else 0
    )

    val pinchState = rememberGridPinchZoomState(
        cellsList = albumCellsList,
        initialCellsIndex = lastCellIndex,
        gridState = gridState
    )

    LaunchedEffect(pinchState.isZooming) {
        lastCellIndex = albumCellsList.indexOf(pinchState.currentCells)
        actions.onCellsIndexChange(lastCellIndex)
    }

    // Re-tapping the Library tab scrolls back to the top (#1039).
    ScrollToTopHandler(routes.library) {
        pinchState.gridState.animateOrJumpToTop()
    }

    val currentOnViewportChanged by rememberUpdatedState(actions.onViewportChanged)
    val currentGeometry by rememberUpdatedState(
        LibraryViewport(
            configuration = configurationSignature,
            statusBarTop = statusBarTop,
            navigationBarBottom = navigationBarBottom
        )
    )
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(lifecycle, gridState) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            snapshotFlow {
                gridState.measuredScrollPosition()?.let { currentGeometry.copy(grid = it) }
            }.filterNotNull().distinctUntilChanged().collect { currentOnViewportChanged(it) }
        }
    }
    LaunchedEffect(lifecycle, locationsListState) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            snapshotFlow {
                locationsListState.measuredScrollPosition()?.let { currentGeometry.copy(locations = it) }
            }.filterNotNull().distinctUntilChanged().collect { currentOnViewportChanged(it) }
        }
    }
    LaunchedEffect(lifecycle, peopleListState) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            snapshotFlow {
                peopleListState.measuredScrollPosition()?.let { currentGeometry.copy(people = it) }
            }.filterNotNull().distinctUntilChanged().collect { currentOnViewportChanged(it) }
        }
    }
    LaunchedEffect(lifecycle, categoriesListState) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            snapshotFlow {
                categoriesListState.measuredScrollPosition()?.let { currentGeometry.copy(categories = it) }
            }.filterNotNull().distinctUntilChanged().collect { currentOnViewportChanged(it) }
        }
    }

    LifecycleResumeEffect(Unit) {
        onPauseOrDispose {
            currentOnViewportChanged(
                currentGeometry.copy(
                    grid = gridState.measuredScrollPosition() ?: LibraryScrollPosition(),
                    locations = locationsListState.measuredScrollPosition()
                        ?: LibraryScrollPosition(),
                    people = peopleListState.measuredScrollPosition()
                        ?: LibraryScrollPosition(),
                    categories = categoriesListState.measuredScrollPosition()
                        ?: LibraryScrollPosition()
                )
            )
        }
    }

    // Locations
    val totalLocationsCount = snapshot.locationCount

    // In-place shortcut editing (Quick-Settings style)
    var shortcutsEditMode by remember { mutableStateOf(initialEditMode) }
    BackHandler(enabled = shortcutsEditMode) { shortcutsEditMode = false }
    // Long-press on any Library section — not only the shortcut tiles — enters
    // the edit UI, matching how the tiles behave (#1262).
    val enterEditMode: () -> Unit = {
        shortcutsEditMode = true
        feedbackManager.vibrateStrong()
    }

    Scaffold(
        modifier = Modifier.padding(
            start = paddingValues.calculateStartPadding(LocalLayoutDirection.current),
            end = paddingValues.calculateEndPadding(LocalLayoutDirection.current)
        ),
        topBar = {
            searchBar(WindowInsets(top = statusBarTop)) {
                val tertiaryContainer = MaterialTheme.colorScheme.tertiaryFixed
                val onTertiaryContainer = MaterialTheme.colorScheme.onTertiaryFixed
                val allowBlur by rememberPreference(SettingsKeys.allowBlur)

                val settingsInteractionSource = remember { MutableInteractionSource() }
                val isPressed = settingsInteractionSource.collectIsPressedAsState()
                val cornerRadius by animateDpAsState(
                    targetValue = if (isPressed.value) 32.dp else 16.dp,
                    label = "cornerRadius"
                )

                val settingsBackgroundModifier = remember(allowBlur) {
                    if (!allowBlur) {
                        Modifier.background(
                            color = tertiaryContainer,
                            shape = RoundedCornerShape(cornerRadius)
                        )
                    } else {
                        Modifier
                    }
                }

                IconButton(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(RoundedCornerShape(cornerRadius))
                        .then(settingsBackgroundModifier)
                        .hazeEffectScaled(
                            state = LocalHazeState.current,
                            style = HazeMaterials.regular(
                                containerColor = tertiaryContainer
                            ),
                            glassContainerColor = tertiaryContainer
                        ),
                    interactionSource = settingsInteractionSource,
                    onClick = { actions.onNavigate(routes.settings) }
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Settings,
                        contentDescription = stringResource(Res.string.settings_title),
                        tint = onTertiaryContainer
                    )
                }
            }
        }
    ) { it ->
        val hideSearchBarSetting by rememberPreference(SettingsKeys.autoHideSearchBar)
        val topBarScrimZone by animateDpAsState(
            targetValue = if (!isScrolling.value || !hideSearchBarSetting) {
                SearchBarDefaults.InputFieldHeight + paddingValues.calculateTopPadding() + 8.dp
            } else paddingValues.calculateTopPadding(),
            label = "topBarScrimZone"
        )
        Box(modifier = Modifier.fillMaxSize()) {
        GridPinchZoomLayout(
            state = pinchState,
            modifier = Modifier.hazeSource(LocalHazeState.current),
            indicatorTopPadding = it.calculateTopPadding() + 16.dp,
        ) {
            LaunchedEffect(gridState.isScrollInProgress) {
                isScrolling.value = gridState.isScrollInProgress
            }
            LazyVerticalGrid(
                state = gridState,
                modifier = Modifier
                    .padding(horizontal = 8.dp)
                    .fillMaxSize()
                    .testTag("library-grid"),
                columns = gridCells,
                contentPadding = PaddingValues(
                    top = it.calculateTopPadding(),
                    bottom = bottomBarInset + 128.dp
                ),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item(
                    span = { GridItemSpan(maxLineSpan) },
                    key = "libraryShortcuts"
                ) {
                    val runtime = rememberLibraryRuntimeShortcuts(
                        indicatorState = indicatorState,
                        cloudState = cloudState,
                        routes = routes,
                        supportsTrash = state.supportsTrash,
                        supportsFavorites = state.supportsFavorites
                    )
                    var shortcutsLayout by rememberPreference(libraryShortcutsLayoutKey)
                    val working = remember(runtime, shortcutsLayout) {
                        mergeShortcutPrefs(shortcutsLayout, runtime).map { it.pref }
                    }
                    Column(
                        modifier = Modifier
                            .pinchItem(key = "libraryShortcuts")
                            .padding(horizontal = 16.dp)
                            .padding(top = 32.dp)
                    ) {
                        EditableLibraryShortcutsGrid(
                            working = working,
                            runtime = runtime,
                            editMode = shortcutsEditMode,
                            onClick = { route -> actions.onNavigate(route) },
                            onEnterEditMode = { shortcutsEditMode = true },
                            onExitEditMode = { shortcutsEditMode = false },
                            onChange = { newWorking ->
                                val availableIds = runtime.keys.map { it.id }.toSet()
                                val leftovers = shortcutsLayout.filter { it.id !in availableIds }
                                shortcutsLayout = newWorking + leftovers
                            }
                        )
                        // Manual section toggles — the only way back once a section is
                        // hidden, so it lives inside the same edit surface (#1262).
                        AnimatedVisibility(visible = shortcutsEditMode) {
                            LibrarySectionsEditCard(
                                showLocations = showLocationCategories,
                                onLocationsChange = { showLocationCategories = it },
                                hasLocations = locations.isNotEmpty(),
                                peopleAvailable = cloudState.hasPeople,
                                showPeople = SECTION_PEOPLE !in hiddenSections,
                                onPeopleChange = { checked ->
                                    hiddenSections = if (checked) {
                                        hiddenSections - SECTION_PEOPLE
                                    } else {
                                        hiddenSections + SECTION_PEOPLE
                                    }
                                },
                                categoriesAvailable = aiAvailable && !noClassification,
                                showCategories = SECTION_CATEGORIES !in hiddenSections,
                                onCategoriesChange = { checked ->
                                    hiddenSections = if (checked) {
                                        hiddenSections - SECTION_CATEGORIES
                                    } else {
                                        hiddenSections + SECTION_CATEGORIES
                                    }
                                },
                                modifier = Modifier.padding(top = 16.dp)
                            )
                        }
                    }
                }

                // Locations section — user-toggleable; with no geotagged media the
                // header stays as a plain navigable row instead of auto-hiding (#1262).
                if (showLocationsSection) {
                    item(
                        span = { GridItemSpan(maxLineSpan) },
                        key = "LocationsHeader"
                    ) {
                        if (mapsEnabled && locations.isNotEmpty()) {
                            MapPreviewCard(
                                modifier = Modifier
                                    .pinchItem(key = "LocationsHeader")
                                    .padding(horizontal = 16.dp)
                                    .padding(top = 8.dp)
                                    .clip(RoundedCornerShape(24.dp))
                                    .editLock(shortcutsEditMode)
                                    .combinedClickable(
                                        onClick = {
                                            actions.onNavigate(routes.locations)
                                        },
                                        onLongClick = enterEditMode
                                    ),
                                latestMedia = latestGeo?.media,
                                latitude = latestGeo?.latitude,
                                longitude = latestGeo?.longitude,
                                mapContent = mapContent
                            )
                        } else {
                            Column(
                                modifier = Modifier
                                    .pinchItem(key = "LocationsHeader")
                                    .padding(horizontal = 16.dp)
                                    .padding(top = 8.dp)
                                    .editLock(shortcutsEditMode),
                                verticalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                LibrarySmallItem(
                                    title = stringResource(Res.string.locations),
                                    icon = null,
                                    contentColor = MaterialTheme.colorScheme.onSurface,
                                    containerColor = MaterialTheme.colorScheme.surface,
                                    useIndicator = true,
                                    indicatorCounter = totalLocationsCount,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .combinedClickable(
                                            onClick = {
                                                actions.onNavigate(routes.locations)
                                            },
                                            onLongClick = enterEditMode
                                        )
                                )
                            }
                        }
                    }
                    // Locations carousel
                    if (locations.isNotEmpty()) {
                    item(
                        span = { GridItemSpan(maxLineSpan) },
                        key = "LocationsList"
                    ) {
                        LazyRow(
                            state = locationsListState,
                            modifier = Modifier
                                .padding(horizontal = 16.dp)
                                .padding(top = 8.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .editLock(shortcutsEditMode)
                                .testTag("library-locations"),
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            items(
                                items = locations,
                                key = { it.media.id }
                            ) { locationMedia ->
                                val media = locationMedia.media
                                val location = locationMedia.location
                                val imageRenderer = LocalMediaImageRenderer.current
                                val isDarkTheme = isDarkTheme()
                                val allowBlur by rememberPreference(SettingsKeys.allowBlur)
                                val followTheme = remember(allowBlur) { !allowBlur }
                                val gradientColor by animateColorAsState(
                                    if (followTheme) {
                                        if (isDarkTheme) BlackScrim else WhiterBlackScrim
                                    } else BlackScrim,
                                )
                                Box(
                                    modifier = Modifier
                                        .width(164.dp)
                                        .height(256.dp)
                                        .testTag("library-location-${media.id}")
                                        .clip(RoundedCornerShape(24.dp))
                                        .combinedClickable(
                                            onClick = {
                                                val city = locationMedia.city
                                                val country = locationMedia.country
                                                val latitude = locationMedia.latitude
                                                val longitude = locationMedia.longitude
                                                if (!city.isNullOrBlank() || !country.isNullOrBlank() ||
                                                    latitude != null && longitude != null
                                                ) {
                                                    actions.onOpenLocation(
                                                        city.orEmpty(),
                                                        country.orEmpty(),
                                                        latitude,
                                                        longitude,
                                                    )
                                                } else {
                                                    actions.onOpenMedia(media.id)
                                                }
                                            },
                                            onLongClick = enterEditMode
                                        ),
                                ) {
                                    imageRenderer.RenderImage(
                                        modifier = Modifier.fillMaxSize(),
                                        contentScale = ContentScale.Crop,
                                        model = media.getUri(),
                                        contentDescription = location,
                                        signature = media
                                    )
                                    Text(
                                        modifier = Modifier
                                            .align(Alignment.BottomCenter)
                                            .fillMaxWidth()
                                            .background(
                                                Brush.verticalGradient(
                                                    colors = listOf(
                                                        Color.Transparent,
                                                        gradientColor
                                                    )
                                                )
                                            )
                                            .padding(24.dp),
                                        text = location,
                                        style = MaterialTheme.typography.titleMedium,
                                        color = Color.White,
                                        fontWeight = FontWeight.SemiBold,
                                        textAlign = TextAlign.Center,
                                        overflow = TextOverflow.MiddleEllipsis
                                    )
                                }
                            }
                        }
                    }
                    }
                }

                // People section — circle heads row (below locations). The header stays
                // reachable while hidden people exist even when no visible person is left:
                // hiding the only person must not strand them behind an auto-hidden
                // section (#1262). The circles row itself stays visible-only.
                if (showPeopleSection) {
                    item(
                        span = { GridItemSpan(maxLineSpan) },
                        key = "PeopleHeader"
                    ) {
                        Column(
                            modifier = Modifier
                                .pinchItem(key = "PeopleHeader")
                                .padding(horizontal = 16.dp)
                                .padding(top = 8.dp)
                                .editLock(shortcutsEditMode),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            LibrarySmallItem(
                                title = stringResource(Res.string.cloud_people),
                                subtitle = if (cloudState.hiddenPeopleCount > 0) {
                                    pluralStringResource(
                                        Res.plurals.people_hidden_count,
                                        cloudState.hiddenPeopleCount,
                                        cloudState.hiddenPeopleCount
                                    )
                                } else null,
                                icon = null,
                                contentColor = MaterialTheme.colorScheme.onSurface,
                                containerColor = MaterialTheme.colorScheme.surface,
                                useIndicator = true,
                                indicatorCounter = snapshot.peopleCount,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .combinedClickable(
                                        onClick = {
                                            actions.onNavigate(routes.people)
                                        },
                                        onLongClick = enterEditMode
                                    )
                            )
                        }
                    }
                    if (cloudState.people.isNotEmpty()) {
                    item(
                        span = { GridItemSpan(maxLineSpan) },
                        key = "PeopleList"
                    ) {
                        LazyRow(
                            state = peopleListState,
                            modifier = Modifier
                                .padding(horizontal = 16.dp)
                                .padding(top = 8.dp)
                                .editLock(shortcutsEditMode)
                                .testTag("library-people"),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            items(
                                items = cloudState.people,
                                key = { it.accountKey }
                            ) { person ->
                                val personRenderer = LocalMediaImageRenderer.current
                                Box(
                                    modifier = Modifier
                                        .size(80.dp)
                                        .testTag("library-person-${person.accountKey}")
                                        .clip(CircleShape)
                                        .combinedClickable(
                                            onClick = {
                                                actions.onOpenPerson(
                                                    person.serverConfigId,
                                                    person.id,
                                                )
                                            },
                                            onLongClick = enterEditMode
                                        )
                                ) {
                                    if (person.thumbnailUrl != null) {
                                        personRenderer.RenderImage(
                                            model = person.thumbnailUrl,
                                            contentDescription = person.name,
                                            modifier = Modifier.fillMaxSize(),
                                            contentScale = ContentScale.Crop,
                                        )
                                    } else {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Outlined.People,
                                                contentDescription = null,
                                                modifier = Modifier.size(32.dp),
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                    }
                }

                if (showCategoriesSection) {
                    if (topCategories.isNotEmpty()) {
                        // "See all categories" header below carousel
                        item(
                            span = { GridItemSpan(maxLineSpan) },
                            key = "CategoriesHeader"
                        ) {
                            Column(
                                modifier = Modifier
                                    .pinchItem(key = "CategoriesHeader")
                                    .padding(horizontal = 16.dp)
                                    .padding(top = 8.dp)
                                    .editLock(shortcutsEditMode),
                                verticalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                LibrarySmallItem(
                                    title = stringResource(Res.string.categories),
                                    icon = null,
                                    contentColor = MaterialTheme.colorScheme.onSurface,
                                    containerColor = MaterialTheme.colorScheme.surface,
                                    useIndicator = true,
                                    indicatorCounter = totalCategoryCount,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .combinedClickable(
                                            onClick = {
                                                actions.onNavigate(routes.categories)
                                            },
                                            onLongClick = enterEditMode
                                        )
                                )
                            }
                        }
                        // Categories carousel first
                        item(
                            span = { GridItemSpan(maxLineSpan) },
                            key = "CategoriesList"
                        ) {
                            LazyRow(
                                state = categoriesListState,
                                modifier = Modifier
                                    .padding(horizontal = 16.dp)
                                    .padding(top = 8.dp)
                                    .clip(RoundedCornerShape(16.dp))
                                    .editLock(shortcutsEditMode)
                                    .testTag("library-categories"),
                                horizontalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                items(
                                    items = topCategories,
                                    key = { preview -> "category_${preview.id}" }
                                ) { preview ->
                                    val thumbnailMedia = preview.thumbnailMedia
                                    with(sharedTransitionScope) {
                                        val categoryRenderer = LocalMediaImageRenderer.current
                                        val isDarkTheme = isDarkTheme()
                                        val allowBlur by rememberPreference(SettingsKeys.allowBlur)
                                        val followTheme = remember(allowBlur) { !allowBlur }
                                        val gradientColor by animateColorAsState(
                                            if (followTheme) {
                                                if (isDarkTheme) BlackScrim else WhiterBlackScrim
                                            } else BlackScrim,
                                        )
                                        Box(
                                            modifier = Modifier
                                                .width(164.dp)
                                                .height(256.dp)
                                                .testTag("library-category-${preview.id}")
                                                .categorySharedElement(
                                                    categoryId = preview.id,
                                                    animatedVisibilityScope = animatedContentScope
                                                )
                                                .clip(RoundedCornerShape(24.dp))
                                                .combinedClickable(
                                                    onClick = {
                                                        actions.onOpenCategory(preview.id)
                                                    },
                                                    onLongClick = {
                                                        actions.onEditCategory(preview.id)
                                                    }
                                                ),
                                        ) {
                                            if (thumbnailMedia != null) {
                                                categoryRenderer.RenderImage(
                                                    modifier = Modifier.fillMaxSize(),
                                                    contentScale = ContentScale.Crop,
                                                    model = thumbnailMedia.getUri(),
                                                    contentDescription = preview.name,
                                                    signature = thumbnailMedia
                                                )
                                            } else {
                                                Box(
                                                    modifier = Modifier
                                                        .fillMaxSize()
                                                        .background(MaterialTheme.colorScheme.surfaceVariant),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Outlined.ImageSearch,
                                                        contentDescription = null,
                                                        modifier = Modifier.size(48.dp),
                                                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(
                                                            alpha = 0.5f
                                                        )
                                                    )
                                                }
                                            }
                                            Column(
                                                modifier = Modifier
                                                    .align(Alignment.BottomCenter)
                                                    .fillMaxWidth()
                                                    .background(
                                                        Brush.verticalGradient(
                                                            colors = listOf(
                                                                Color.Transparent,
                                                                gradientColor
                                                            )
                                                        )
                                                    )
                                                    .padding(16.dp),
                                                horizontalAlignment = Alignment.CenterHorizontally
                                            ) {
                                                Text(
                                                    text = preview.name,
                                                    style = MaterialTheme.typography.titleMedium,
                                                    color = Color.White,
                                                    fontWeight = FontWeight.SemiBold,
                                                    textAlign = TextAlign.Center,
                                                    overflow = TextOverflow.Ellipsis,
                                                    maxLines = 1
                                                )
                                                Text(
                                                    text = stringResource(
                                                        Res.string.category_media_count,
                                                        preview.mediaCount
                                                    ),
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = Color.White.copy(alpha = 0.7f),
                                                    textAlign = TextAlign.Center
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                    if (noCategoriesFound && modelStatus == ModelStatus.READY) {
                        item(
                            span = { GridItemSpan(maxLineSpan) },
                            key = "NoCategories"
                        ) {
                            NoCategories(
                                modifier = Modifier
                                    .pinchItem(key = "NoCategories")
                                    .padding(16.dp)
                                    .editLock(shortcutsEditMode)
                            ) {
                                actions.onNavigate(routes.categories)
                            }
                        }
                    }
                }
            }
        }
        FloatingTopBarScrim(
            barZoneHeight = topBarScrimZone,
            visibility = {
                topScrimVisibility(
                    gridState.firstVisibleItemIndex,
                    gridState.firstVisibleItemScrollOffset,
                    TopBarScrimScrollFade.toPx(),
                )
            },
        )
        }
    }

}

private fun LazyGridState.measuredScrollPosition(): LibraryScrollPosition? {
    if (layoutInfo.totalItemsCount <= 0 || isScrollInProgress) return null
    val index = firstVisibleItemIndex
    return LibraryScrollPosition(
        key = layoutInfo.visibleItemsInfo.firstOrNull { it.index == index }
            ?.key as? String,
        index = index,
        offset = firstVisibleItemScrollOffset
    )
}

private fun LazyListState.measuredScrollPosition(): LibraryScrollPosition? {
    if (layoutInfo.totalItemsCount <= 0 || isScrollInProgress) return null
    val index = firstVisibleItemIndex
    return LibraryScrollPosition(
        key = layoutInfo.visibleItemsInfo.firstOrNull { it.index == index }
            ?.key?.toString(),
        index = index,
        offset = firstVisibleItemScrollOffset
    )
}

/**
 * While the library shortcuts are being edited, dim a section and swallow taps
 * so the locations/people/categories rows below can't be clicked. Scrolling is
 * preserved (only tap gestures are consumed).
 */
private fun Modifier.editLock(locked: Boolean): Modifier = composed {
    if (!locked) this
    else this
        .alpha(0.35f)
        .pointerInput(Unit) {
            // Consume every pointer event on the Initial pass (delivered
            // parent -> child) so descendant clickables never receive it.
            awaitPointerEventScope {
                while (true) {
                    awaitPointerEvent(PointerEventPass.Initial).changes.forEach { it.consume() }
                }
            }
        }
}
