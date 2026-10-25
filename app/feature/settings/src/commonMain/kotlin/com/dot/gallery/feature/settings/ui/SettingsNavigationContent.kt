/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.feature.settings.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CollectionsBookmark
import androidx.compose.material.icons.outlined.Photo
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.backhandler.BackHandler
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dot.gallery.core.Position
import com.dot.gallery.core.SettingsEntity
import com.dot.gallery.core.ui.components.BaseSettingsScreen
import com.dot.gallery.core.ui.components.ChooserPreferenceDetailScreen
import com.dot.gallery.core.ui.components.PreferenceOption
import com.dot.gallery.core.ui.components.SwitchPreferenceDetailScreen
import com.dot.gallery.core.ui.components.rememberPreference
import com.dot.gallery.core.ui.components.rememberSwitchPreference
import com.dot.gallery.core.ui.settings.rememberPreference
import com.dot.gallery.domain.settings.LAUNCH_ROUTE_AUTO
import com.dot.gallery.domain.settings.SettingsKeys
import org.jetbrains.compose.resources.stringResource
import com.dot.gallery.core.presentation.components.rememberSelectionSheetConfig
import com.dot.gallery.feature.settings.generated.resources.Res
import com.dot.gallery.feature.settings.generated.resources.albums
import com.dot.gallery.feature.settings.generated.resources.auto
import com.dot.gallery.feature.settings.generated.resources.auto_hide_navigationbar
import com.dot.gallery.feature.settings.generated.resources.auto_hide_navigationbar_description
import com.dot.gallery.feature.settings.generated.resources.auto_hide_navigationbar_summary
import com.dot.gallery.feature.settings.generated.resources.auto_hide_searchbar
import com.dot.gallery.feature.settings.generated.resources.auto_hide_searchbar_description
import com.dot.gallery.feature.settings.generated.resources.auto_hide_searchbar_summary
import com.dot.gallery.feature.settings.generated.resources.auto_open_search_keyboard
import com.dot.gallery.feature.settings.generated.resources.auto_open_search_keyboard_description
import com.dot.gallery.feature.settings.generated.resources.auto_open_search_keyboard_summary
import com.dot.gallery.feature.settings.generated.resources.interface_settings
import com.dot.gallery.feature.settings.generated.resources.launch_auto
import com.dot.gallery.feature.settings.generated.resources.launch_on_albums
import com.dot.gallery.feature.settings.generated.resources.launch_on_library
import com.dot.gallery.feature.settings.generated.resources.launch_on_timeline
import com.dot.gallery.feature.settings.generated.resources.launch_screen_description
import com.dot.gallery.feature.settings.generated.resources.library
import com.dot.gallery.feature.settings.generated.resources.navigation
import com.dot.gallery.feature.settings.generated.resources.old_navbar
import com.dot.gallery.feature.settings.generated.resources.gallery_dual_pane
import com.dot.gallery.feature.settings.generated.resources.gallery_dual_pane_description
import com.dot.gallery.feature.settings.generated.resources.gallery_dual_pane_summary
import com.dot.gallery.feature.settings.generated.resources.old_navbar_description
import com.dot.gallery.feature.settings.generated.resources.old_navbar_summary
import com.dot.gallery.feature.settings.generated.resources.selection_actions
import com.dot.gallery.feature.settings.generated.resources.selection_actions_summary
import com.dot.gallery.feature.settings.generated.resources.set_default_launch_screen
import com.dot.gallery.feature.settings.generated.resources.set_default_screen
import com.dot.gallery.feature.settings.generated.resources.settings_navigation
import com.dot.gallery.feature.settings.generated.resources.settings_navigation_auto_symbol
import com.dot.gallery.feature.settings.generated.resources.show_selection_titles
import com.dot.gallery.feature.settings.generated.resources.show_selection_titles_description
import com.dot.gallery.feature.settings.generated.resources.show_selection_titles_summary
import com.dot.gallery.feature.settings.generated.resources.timeline
import com.dot.gallery.feature.settings.generated.resources.use_last_opened_screen

private const val DETAIL_LAUNCH_SCREEN = "launch_screen"
private const val DETAIL_OLD_NAVBAR = "old_navbar"
private const val DETAIL_HIDE_SEARCH = "hide_search"
private const val DETAIL_OPEN_KEYBOARD = "open_keyboard"
private const val DETAIL_HIDE_NAV = "hide_nav"
private const val DETAIL_SELECTION_TITLES = "selection_titles"
private const val DETAIL_DUAL_PANE = "dual_pane"

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun SettingsNavigationContent(
    state: SettingsNavigationState,
    actions: SettingsNavigationActions,
) {
    var detailKey by rememberSaveable { mutableStateOf<String?>(null) }

    var lastScreen by rememberPreference(SettingsKeys.lastScreen)
    var forcedLastScreen by rememberPreference(SettingsKeys.forcedLastScreen)
    var showOldNavbar by rememberPreference(SettingsKeys.oldNavbar)
    var autoHideSearch by rememberPreference(SettingsKeys.autoHideSearchBar)
    var autoOpenKeyboard by rememberPreference(SettingsKeys.autoOpenSearchKeyboard)
    var autoHideNavBar by rememberPreference(SettingsKeys.autoHideNavBar)
    var showSelectionTitles by rememberPreference(SettingsKeys.selectionTitles)
    var dualPane by rememberPreference(SettingsKeys.galleryDualPane)

    when (detailKey) {
        DETAIL_LAUNCH_SCREEN -> {
            BackHandler { detailKey = null }
            val useLastOpenedScreenLabel = stringResource(Res.string.use_last_opened_screen)
            val launchOnTimelineLabel = stringResource(Res.string.launch_on_timeline)
            val launchOnAlbumsLabel = stringResource(Res.string.launch_on_albums)
            val launchOnLibraryLabel = stringResource(Res.string.launch_on_library)
            val launchOptions = remember(
                lastScreen,
                forcedLastScreen,
                state,
                useLastOpenedScreenLabel,
                launchOnTimelineLabel,
                launchOnAlbumsLabel,
                launchOnLibraryLabel,
            ) {
                listOf(
                    PreferenceOption(
                        LAUNCH_ROUTE_AUTO, useLastOpenedScreenLabel,
                        !forcedLastScreen
                    ),
                    PreferenceOption(
                        state.timelineRoute, launchOnTimelineLabel,
                        forcedLastScreen && lastScreen == state.timelineRoute
                    ),
                    PreferenceOption(
                        state.albumsRoute, launchOnAlbumsLabel,
                        forcedLastScreen && lastScreen == state.albumsRoute
                    ),
                    PreferenceOption(
                        state.libraryRoute, launchOnLibraryLabel,
                        forcedLastScreen && lastScreen == state.libraryRoute
                    ),
                )
            }
            ChooserPreferenceDetailScreen(
                title = stringResource(Res.string.set_default_launch_screen),
                description = stringResource(Res.string.launch_screen_description),
                preview = {
                    LaunchScreenPreview(
                        lastScreen = lastScreen,
                        forcedLastScreen = forcedLastScreen,
                        timelineRoute = state.timelineRoute,
                        albumsRoute = state.albumsRoute,
                        libraryRoute = state.libraryRoute,
                    )
                },
                options = launchOptions,
                onOptionSelected = { selected ->
                    if (selected == LAUNCH_ROUTE_AUTO) {
                        forcedLastScreen = false
                        lastScreen = state.timelineRoute
                    } else {
                        forcedLastScreen = true
                        lastScreen = selected
                    }
                },
            )
        }
        DETAIL_OLD_NAVBAR -> {
            BackHandler { detailKey = null }
            SwitchPreferenceDetailScreen(
                title = stringResource(Res.string.old_navbar),
                isChecked = showOldNavbar,
                onCheckedChange = { showOldNavbar = it },
                description = stringResource(Res.string.old_navbar_description),
                preview = { checked -> OldNavbarPreview(checked) },
                useColumnLayout = true,
            )
        }
        DETAIL_HIDE_SEARCH -> {
            BackHandler { detailKey = null }
            SwitchPreferenceDetailScreen(
                title = stringResource(Res.string.auto_hide_searchbar),
                isChecked = autoHideSearch,
                onCheckedChange = { autoHideSearch = it },
                description = stringResource(Res.string.auto_hide_searchbar_description),
            )
        }
        DETAIL_OPEN_KEYBOARD -> {
            BackHandler { detailKey = null }
            SwitchPreferenceDetailScreen(
                title = stringResource(Res.string.auto_open_search_keyboard),
                isChecked = autoOpenKeyboard,
                onCheckedChange = { autoOpenKeyboard = it },
                description = stringResource(Res.string.auto_open_search_keyboard_description),
            )
        }
        DETAIL_HIDE_NAV -> {
            BackHandler { detailKey = null }
            SwitchPreferenceDetailScreen(
                title = stringResource(Res.string.auto_hide_navigationbar),
                isChecked = autoHideNavBar,
                onCheckedChange = { autoHideNavBar = it },
                description = stringResource(Res.string.auto_hide_navigationbar_description),
            )
        }
        DETAIL_DUAL_PANE -> {
            BackHandler { detailKey = null }
            SwitchPreferenceDetailScreen(
                title = stringResource(Res.string.gallery_dual_pane),
                isChecked = dualPane,
                onCheckedChange = { dualPane = it },
                description = stringResource(Res.string.gallery_dual_pane_description),
            )
        }
        DETAIL_SELECTION_TITLES -> {
            BackHandler { detailKey = null }
            SwitchPreferenceDetailScreen(
                title = stringResource(Res.string.show_selection_titles),
                isChecked = showSelectionTitles,
                onCheckedChange = { showSelectionTitles = it },
                description = stringResource(Res.string.show_selection_titles_description),
                preview = { checked ->
                    SelectionSheetPreview(
                        config = rememberSelectionSheetConfig().value.sanitized(),
                        showTitlesOverride = checked,
                    )
                },
                useColumnLayout = true,
            )
        }
        else -> {
            NavigationListScreen(
                lastScreen = lastScreen,
                forcedLastScreen = forcedLastScreen,
                showOldNavbar = showOldNavbar,
                onOldNavbarChange = { showOldNavbar = it },
                autoHideSearch = autoHideSearch,
                onAutoHideSearchChange = { autoHideSearch = it },
                autoOpenKeyboard = autoOpenKeyboard,
                onAutoOpenKeyboardChange = { autoOpenKeyboard = it },
                autoHideNavBar = autoHideNavBar,
                onAutoHideNavChange = { autoHideNavBar = it },
                dualPane = dualPane,
                onDualPaneChange = { dualPane = it },
                showSelectionTitles = showSelectionTitles,
                onSelectionTitlesChange = { showSelectionTitles = it },
                onDetailClick = { detailKey = it },
                onOpenSelectionActions = actions.onOpenSelectionActions,
                timelineRoute = state.timelineRoute,
                albumsRoute = state.albumsRoute,
                libraryRoute = state.libraryRoute,
                searchRoute = actions.searchRoute,
                searchSink = actions.searchSink,
            )
        }
    }
}

@Composable
private fun LaunchScreenPreview(
    lastScreen: String,
    forcedLastScreen: Boolean,
    timelineRoute: String,
    albumsRoute: String,
    libraryRoute: String,
) {
    data class LaunchOption(val route: String, val label: String, val icon: ImageVector)
    val options = listOf(
        LaunchOption(timelineRoute, stringResource(Res.string.timeline), Icons.Outlined.Photo),
        LaunchOption(albumsRoute, stringResource(Res.string.albums), Icons.Outlined.CollectionsBookmark),
        LaunchOption(libraryRoute, stringResource(Res.string.library), Icons.Outlined.PhotoLibrary),
    )
    val selectedRoute = if (forcedLastScreen) lastScreen else null

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(24.dp),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        // Auto option
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            val isAutoSelected = !forcedLastScreen
            val borderColor = if (isAutoSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                    .border(2.dp, borderColor, RoundedCornerShape(10.dp)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = stringResource(Res.string.settings_navigation_auto_symbol),
                    style = MaterialTheme.typography.titleMedium,
                    color = if (isAutoSelected) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                )
            }
            Text(
                text = stringResource(Res.string.auto),
                style = MaterialTheme.typography.labelSmall,
                color = if (isAutoSelected) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                textAlign = TextAlign.Center
            )
        }
        // Screen options
        options.forEach { option ->
            val isSelected = selectedRoute == option.route
            val borderColor = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                        .border(2.dp, borderColor, RoundedCornerShape(10.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = option.icon,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                        tint = if (isSelected) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    )
                }
                Text(
                    text = option.label,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isSelected) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
private fun OldNavbarPreview(isChecked: Boolean) {
    val activeColor = MaterialTheme.colorScheme.primary
    val inactiveColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
    val surfaceColor = MaterialTheme.colorScheme.surfaceContainer
    val secondaryContainer = MaterialTheme.colorScheme.secondaryContainer
    val onSecondaryContainer = MaterialTheme.colorScheme.onSecondaryContainer

    val icons = listOf(
        Icons.Outlined.Photo to stringResource(Res.string.timeline),
        Icons.Outlined.CollectionsBookmark to stringResource(Res.string.albums),
        Icons.Outlined.PhotoLibrary to stringResource(Res.string.library),
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (isChecked) {
            // Classic Material navigation bar (full width, with labels)
            Row(
                modifier = Modifier
                    .fillMaxWidth(0.85f)
                    .height(56.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(surfaceColor),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                icons.forEachIndexed { index, (icon, label) ->
                    val isActive = index == 0
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        // Indicator pill behind icon (Material 3 style)
                        Box(
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(
                                    if (isActive) secondaryContainer
                                    else Color.Transparent
                                )
                                .padding(horizontal = 12.dp, vertical = 2.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = icon,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                                tint = if (isActive) onSecondaryContainer else inactiveColor
                            )
                        }
                        Text(
                            text = label,
                            style = MaterialTheme.typography.labelSmall,
                            fontSize = 9.sp,
                            color = if (isActive) activeColor else inactiveColor,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        } else {
            // Modern GalleryNavBar (floating compact pill, icon-only)
            Row(
                modifier = Modifier
                    .clip(CircleShape)
                    .background(surfaceColor)
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(20.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                icons.forEachIndexed { index, (icon, _) ->
                    val isActive = index == 0
                    if (isActive) {
                        // Selected item with pill background
                        Box(
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(secondaryContainer)
                                .padding(horizontal = 14.dp, vertical = 6.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = icon,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp),
                                tint = onSecondaryContainer
                            )
                        }
                    } else {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                            tint = inactiveColor
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun NavigationListScreen(
    lastScreen: String,
    forcedLastScreen: Boolean,
    showOldNavbar: Boolean,
    onOldNavbarChange: (Boolean) -> Unit,
    autoHideSearch: Boolean,
    onAutoHideSearchChange: (Boolean) -> Unit,
    autoOpenKeyboard: Boolean,
    onAutoOpenKeyboardChange: (Boolean) -> Unit,
    autoHideNavBar: Boolean,
    onAutoHideNavChange: (Boolean) -> Unit,
    dualPane: Boolean,
    onDualPaneChange: (Boolean) -> Unit,
    showSelectionTitles: Boolean,
    onSelectionTitlesChange: (Boolean) -> Unit,
    onDetailClick: (String) -> Unit,
    onOpenSelectionActions: () -> Unit,
    timelineRoute: String,
    albumsRoute: String,
    libraryRoute: String,
    searchRoute: String?,
    searchSink: ((String, List<String>) -> Unit)?,
) {
    val listState = rememberLazyListState()

    val launchHeaderTitle = stringResource(Res.string.set_default_launch_screen)
    val launchHeader = remember(launchHeaderTitle) {
        SettingsEntity.Header(title = launchHeaderTitle)
    }

    val launchOnTimelineLabel = stringResource(Res.string.launch_on_timeline)
    val launchOnAlbumsLabel = stringResource(Res.string.launch_on_albums)
    val launchOnLibraryLabel = stringResource(Res.string.launch_on_library)
    val launchAutoLabel = stringResource(Res.string.launch_auto)
    val launchSummary = remember(
        lastScreen,
        forcedLastScreen,
        timelineRoute,
        albumsRoute,
        libraryRoute,
        launchOnTimelineLabel,
        launchOnAlbumsLabel,
        launchOnLibraryLabel,
        launchAutoLabel,
    ) {
        if (forcedLastScreen) {
            when (lastScreen) {
                timelineRoute -> launchOnTimelineLabel
                albumsRoute -> launchOnAlbumsLabel
                else -> launchOnLibraryLabel
            }
        } else {
            launchAutoLabel
        }
    }
    val forcedLastScreenPref = rememberPreference(
        forcedLastScreen, lastScreen,
        title = stringResource(Res.string.set_default_screen),
        summary = launchSummary,
        onClick = { onDetailClick(DETAIL_LAUNCH_SCREEN) },
        screenPosition = Position.Alone
    )

    val barsHeaderTitle = stringResource(Res.string.navigation)
    val barsHeader = remember(barsHeaderTitle) {
        SettingsEntity.Header(title = barsHeaderTitle)
    }

    val showOldNavbarPref = rememberSwitchPreference(
        showOldNavbar,
        title = stringResource(Res.string.old_navbar),
        summary = stringResource(Res.string.old_navbar_summary),
        isChecked = showOldNavbar,
        onCheck = onOldNavbarChange,
        onClick = { onDetailClick(DETAIL_OLD_NAVBAR) },
        screenPosition = Position.Top
    )

    val autoHideSearchPref = rememberSwitchPreference(
        autoHideSearch,
        title = stringResource(Res.string.auto_hide_searchbar),
        summary = stringResource(Res.string.auto_hide_searchbar_summary),
        isChecked = autoHideSearch,
        onCheck = onAutoHideSearchChange,
        onClick = { onDetailClick(DETAIL_HIDE_SEARCH) },
        screenPosition = Position.Middle
    )

    val autoOpenKeyboardPref = rememberSwitchPreference(
        autoOpenKeyboard,
        title = stringResource(Res.string.auto_open_search_keyboard),
        summary = stringResource(Res.string.auto_open_search_keyboard_summary),
        isChecked = autoOpenKeyboard,
        onCheck = onAutoOpenKeyboardChange,
        onClick = { onDetailClick(DETAIL_OPEN_KEYBOARD) },
        screenPosition = Position.Middle
    )

    val autoHideNavBarPref = rememberSwitchPreference(
        autoHideNavBar,
        title = stringResource(Res.string.auto_hide_navigationbar),
        summary = stringResource(Res.string.auto_hide_navigationbar_summary),
        isChecked = autoHideNavBar,
        onCheck = onAutoHideNavChange,
        onClick = { onDetailClick(DETAIL_HIDE_NAV) },
        screenPosition = Position.Middle
    )

    val dualPanePref = rememberSwitchPreference(
        dualPane,
        title = stringResource(Res.string.gallery_dual_pane),
        summary = stringResource(Res.string.gallery_dual_pane_summary),
        isChecked = dualPane,
        onCheck = onDualPaneChange,
        onClick = { onDetailClick(DETAIL_DUAL_PANE) },
        screenPosition = Position.Bottom
    )

    val interfaceHeaderTitle = stringResource(Res.string.interface_settings)
    val interfaceHeader = remember(interfaceHeaderTitle) {
        SettingsEntity.Header(title = interfaceHeaderTitle)
    }

    val showSelectionTitlesPref = rememberSwitchPreference(
        showSelectionTitles,
        title = stringResource(Res.string.show_selection_titles),
        summary = stringResource(Res.string.show_selection_titles_summary),
        isChecked = showSelectionTitles,
        onCheck = onSelectionTitlesChange,
        onClick = { onDetailClick(DETAIL_SELECTION_TITLES) },
        screenPosition = Position.Top
    )

    val selectionActionsPref = rememberPreference(
        title = stringResource(Res.string.selection_actions),
        summary = stringResource(Res.string.selection_actions_summary),
        onClick = onOpenSelectionActions,
        screenPosition = Position.Bottom
    )

    val settingsList: SnapshotStateList<SettingsEntity> = remember(
        launchHeader, forcedLastScreenPref, barsHeader, showOldNavbarPref,
        autoHideSearchPref, autoOpenKeyboardPref, autoHideNavBarPref, dualPanePref,
        interfaceHeader, showSelectionTitlesPref, selectionActionsPref
    ) {
        mutableStateListOf(
            launchHeader,
            forcedLastScreenPref,
            barsHeader,
            showOldNavbarPref,
            autoHideSearchPref,
            autoOpenKeyboardPref,
            autoHideNavBarPref,
            dualPanePref,
            interfaceHeader,
            showSelectionTitlesPref,
            selectionActionsPref
        )
    }

    BaseSettingsScreen(
        title = stringResource(Res.string.settings_navigation),
        settingsList = settingsList,
        listState = listState,
        searchRoute = searchRoute,
        searchSink = searchSink,
    )
}
