package com.dot.gallery.app.presentation.library

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dot.gallery.BuildConfig
import com.dot.gallery.core.LocalEventHandler
import com.dot.gallery.core.Settings
import com.dot.gallery.core.Settings.Misc.rememberCartoBasemapKey
import com.dot.gallery.core.Settings.Misc.rememberMapAppearance
import com.dot.gallery.core.navigate
import com.dot.gallery.core.presentation.components.LocalGalleryPaneChrome
import com.dot.gallery.core.presentation.components.OptionItem
import com.dot.gallery.core.presentation.util.Screen
import com.dot.gallery.core.presentation.util.effectiveCartoBasemapKey
import com.dot.gallery.core.presentation.util.rememberBottomBarInset
import com.dot.gallery.core.ui.generated.resources.customize_library
import com.dot.gallery.core.util.SdkCompat
import com.dot.gallery.feature.library.LibraryViewModel
import com.dot.gallery.feature.library.ui.LibraryActions
import com.dot.gallery.feature.library.ui.LibraryEditContent
import com.dot.gallery.feature.library.ui.LibraryHeader
import com.dot.gallery.feature.library.ui.LibraryHeaderLayout
import com.dot.gallery.feature.library.ui.LibraryRoutes
import com.dot.gallery.feature.library.ui.LibraryState
import com.dot.gallery.feature.library.ui.rememberLibrarySections
import com.dot.gallery.injection.injectViewModel
import com.dot.gallery.app.presentation.util.StaticMapPreview
import com.dot.gallery.ui.theme.isDarkTheme
import org.jetbrains.compose.resources.stringResource
import com.dot.gallery.core.ui.generated.resources.Res as UiRes

/** Window width that fits shortcuts and sections beside the album grid. */
private val SidePaneMinWidth = 840.dp
private val WideMinWidth = 600.dp
private val SidePaneMaxWidth = 360.dp
private val SidePaneMinCellWidth = 130.dp
private const val SidePaneMaxFraction = 0.36f
private val PaneGap = 12.dp
private val PaneShape = RoundedCornerShape(28.dp)

/**
 * The Library tab: shortcuts and Locations, People and Categories, with the album grid below them
 * or, on wide windows, beside them. [albums] renders the grid and owns the scroller; it receives
 * the header to place above the albums, or null while that content sits in the side pane.
 */
@Composable
fun LibraryScreen(
    paddingValues: PaddingValues,
    editMode: Boolean,
    onEditModeChange: (Boolean) -> Unit,
    albums: @Composable (
        paddingValues: PaddingValues,
        header: (@Composable () -> Unit)?,
        menuItems: List<OptionItem>,
        onContentDrawn: () -> Unit,
        minCellWidth: Dp?,
    ) -> Unit,
) {
    val viewModel = injectViewModel<LibraryViewModel> { libraryViewModel }
    val snapshot by viewModel.state.collectAsStateWithLifecycle()
    val modelStatus by viewModel.modelStatus.collectAsStateWithLifecycle()

    LifecycleStartEffect(Unit) {
        viewModel.onVisible()
        onStopOrDispose { viewModel.onHidden() }
    }

    // Leaving the tab must not strand the next visit in edit mode with the dock hidden.
    val currentOnEditModeChange by rememberUpdatedState(onEditModeChange)
    DisposableEffect(Unit) {
        onDispose { currentOnEditModeChange(false) }
    }

    val eventHandler = LocalEventHandler.current
    val isDark = isDarkTheme()
    val mapAppearance by Settings.Misc.rememberMapAppearance()
    val userCartoKey by Settings.Misc.rememberCartoBasemapKey()
    val cartoKey = remember(userCartoKey) {
        effectiveCartoBasemapKey(BuildConfig.CARTO_BASEMAP_KEY, userCartoKey)
    }
    val actions = remember(eventHandler) {
        LibraryActions(onNavigate = { route -> eventHandler.navigate(route) })
    }
    val state = remember(snapshot, modelStatus) {
        LibraryState(
            snapshot = snapshot,
            modelStatus = modelStatus,
            aiAvailable = viewModel.areAiFeaturesAvailable,
            mapsEnabled = BuildConfig.MAPS_ENABLED,
            supportsTrash = SdkCompat.supportsTrash,
            supportsFavorites = SdkCompat.supportsFavorites,
        )
    }
    val routes = rememberLibraryRoutes()
    val mapContent: (@Composable androidx.compose.foundation.layout.BoxScope.(Double, Double) -> Unit)? =
        if (BuildConfig.MAPS_ENABLED) {
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
        } else null

    val customizeLabel = stringResource(UiRes.string.customize_library)
    val menuItems = remember(customizeLabel) {
        listOf(
            OptionItem(
                icon = Icons.Outlined.Edit,
                text = customizeLabel,
                onClick = { currentOnEditModeChange(true) },
            )
        )
    }
    val sections = rememberLibrarySections(state)
    val layoutDirection = LocalLayoutDirection.current

    // The window width needs no subcomposition above the album grid, unlike measuring this box.
    Box(modifier = Modifier.fillMaxSize()) {
        val windowWidth = with(LocalDensity.current) { LocalWindowInfo.current.containerSize.width.toDp() }
        // Without any section the pane would hold only a few tiles, so the grid takes the width.
        val sidePane = windowWidth >= SidePaneMinWidth && sections.count > 0
        val layout = when {
            sidePane -> LibraryHeaderLayout.SIDE_PANE
            windowWidth >= WideMinWidth -> LibraryHeaderLayout.WIDE
            else -> LibraryHeaderLayout.COMPACT
        }
        val header: @Composable (Modifier) -> Unit = { modifier ->
            LibraryHeader(
                state = state,
                actions = actions,
                routes = routes,
                layout = layout,
                mapContent = mapContent,
                modifier = modifier,
            )
        }

        // Two separate panes: the window colour shows through the gap between them.
        Row(
            modifier = Modifier
                .fillMaxSize()
                .then(if (sidePane) Modifier.background(MaterialTheme.colorScheme.background) else Modifier)
        ) {
            if (sidePane) {
                val startPadding = paddingValues.calculateStartPadding(layoutDirection)
                val statusBarTop = if (LocalGalleryPaneChrome.current) 0.dp
                    else WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
                val paneWidth: Dp = minOf(SidePaneMaxWidth, windowWidth * SidePaneMaxFraction)
                Column(
                    modifier = Modifier
                        .padding(start = startPadding)
                        .width(paneWidth)
                        .fillMaxHeight()
                        .clip(PaneShape)
                        .background(MaterialTheme.colorScheme.surfaceContainerLow)
                        .verticalScroll(rememberScrollState())
                        .padding(top = statusBarTop + 16.dp, bottom = rememberBottomBarInset(paddingValues) + 16.dp)
                ) {
                    header(Modifier.padding(horizontal = 16.dp))
                }
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .then(if (sidePane) Modifier.padding(start = PaneGap).clip(PaneShape) else Modifier)
            ) {
                albums(
                    if (sidePane) {
                        PaddingValues(
                            top = paddingValues.calculateTopPadding(),
                            bottom = paddingValues.calculateBottomPadding(),
                            end = paddingValues.calculateEndPadding(layoutDirection),
                        )
                    } else paddingValues,
                    if (sidePane) null else {
                        { header(Modifier.padding(start = 8.dp, end = 8.dp, top = 8.dp)) }
                    },
                    menuItems,
                    viewModel::onContentDrawn,
                    if (sidePane) SidePaneMinCellWidth else null,
                )
            }
        }

        if (editMode) {
            LibraryEditContent(
                state = state,
                routes = routes,
                layout = if (layout == LibraryHeaderLayout.SIDE_PANE) LibraryHeaderLayout.WIDE else layout,
                statusBarInsets = if (LocalGalleryPaneChrome.current) WindowInsets(0, 0, 0, 0) else WindowInsets.statusBars,
                onDone = { onEditModeChange(false) },
                mapContent = mapContent,
                modifier = Modifier
                    .background(MaterialTheme.colorScheme.background)
                    .padding(
                        start = paddingValues.calculateStartPadding(layoutDirection),
                        end = paddingValues.calculateEndPadding(layoutDirection),
                    ),
            )
        }
    }
}

@Composable
private fun rememberLibraryRoutes(): LibraryRoutes = remember {
    LibraryRoutes(
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
        memories = Screen.MemoriesScreen(),
        cloudSharedLinks = Screen.SharedLinksScreen.route,
        cloudBackup = Screen.CloudBackupDashboardScreen.route,
        cloudAccounts = Screen.CloudAccountsScreen.route,
    )
}
