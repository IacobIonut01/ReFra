/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.feature.settings.ui

import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Search
import com.dot.gallery.core.ui.components.PreviewIconBadge
import androidx.compose.material.icons.outlined.Checklist
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Keyboard
import androidx.compose.material.icons.outlined.Title
import androidx.compose.material.icons.outlined.VerticalAlignBottom
import androidx.compose.material.icons.outlined.VerticalSplit
import androidx.compose.material.icons.outlined.ViewDay
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
import com.dot.gallery.domain.settings.normalizeLaunchRoute
import org.jetbrains.compose.resources.stringResource
import com.dot.gallery.core.presentation.components.rememberSelectionSheetConfig
import com.dot.gallery.feature.settings.generated.resources.Res
import com.dot.gallery.feature.settings.generated.resources.albums
import com.dot.gallery.feature.settings.generated.resources.auto
import com.dot.gallery.feature.settings.generated.resources.auto_hide_navigationbar
import com.dot.gallery.feature.settings.generated.resources.auto_hide_navigationbar_description
import com.dot.gallery.feature.settings.generated.resources.auto_hide_navigationbar_summary
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
    var autoOpenKeyboard by rememberPreference(SettingsKeys.autoOpenSearchKeyboard)
    var autoHideNavBar by rememberPreference(SettingsKeys.autoHideNavBar)
    var showSelectionTitles by rememberPreference(SettingsKeys.selectionTitles)
    var dualPane by rememberPreference(SettingsKeys.galleryDualPane)

    when (detailKey) {
        DETAIL_LAUNCH_SCREEN -> {
            BackHandler { detailKey = null }
            val useLastOpenedScreenLabel = stringResource(Res.string.use_last_opened_screen)
            val launchOnTimelineLabel = stringResource(Res.string.launch_on_timeline)
            val launchOnLibraryLabel = stringResource(Res.string.launch_on_library)
            val launchOptions = remember(
                lastScreen,
                forcedLastScreen,
                state,
                useLastOpenedScreenLabel,
                launchOnTimelineLabel,
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
                        state.libraryRoute, launchOnLibraryLabel,
                        forcedLastScreen && normalizeLaunchRoute(lastScreen) == state.libraryRoute
                    ),
                )
            }
            ChooserPreferenceDetailScreen(
                title = stringResource(Res.string.set_default_launch_screen),
                description = stringResource(Res.string.launch_screen_description),
                optionPreview = { route -> LaunchTargetPreview(route, state.timelineRoute) },
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
                libraryRoute = state.libraryRoute,
                searchRoute = actions.searchRoute,
                searchSink = actions.searchSink,
            )
        }
    }
}

@Composable
private fun NavigationListScreen(
    lastScreen: String,
    forcedLastScreen: Boolean,
    showOldNavbar: Boolean,
    onOldNavbarChange: (Boolean) -> Unit,
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
    val launchOnLibraryLabel = stringResource(Res.string.launch_on_library)
    val launchAutoLabel = stringResource(Res.string.launch_auto)
    val launchSummary = remember(
        lastScreen,
        forcedLastScreen,
        timelineRoute,
        libraryRoute,
        launchOnTimelineLabel,
        launchOnLibraryLabel,
        launchAutoLabel,
    ) {
        if (forcedLastScreen) {
            when (lastScreen) {
                timelineRoute -> launchOnTimelineLabel
                else -> launchOnLibraryLabel
            }
        } else {
            launchAutoLabel
        }
    }
    val forcedLastScreenPref = rememberPreference(
        forcedLastScreen, lastScreen,
        title = stringResource(Res.string.set_default_screen),
        icon = Icons.Outlined.Home,
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
        icon = Icons.Outlined.ViewDay,
        summary = stringResource(Res.string.old_navbar_summary),
        isChecked = showOldNavbar,
        onCheck = onOldNavbarChange,
        onClick = { onDetailClick(DETAIL_OLD_NAVBAR) },
        screenPosition = Position.Top
    )

    val autoOpenKeyboardPref = rememberSwitchPreference(
        autoOpenKeyboard,
        title = stringResource(Res.string.auto_open_search_keyboard),
        icon = Icons.Outlined.Keyboard,
        summary = stringResource(Res.string.auto_open_search_keyboard_summary),
        isChecked = autoOpenKeyboard,
        onCheck = onAutoOpenKeyboardChange,
        onClick = { onDetailClick(DETAIL_OPEN_KEYBOARD) },
        screenPosition = Position.Middle
    )

    val autoHideNavBarPref = rememberSwitchPreference(
        autoHideNavBar,
        title = stringResource(Res.string.auto_hide_navigationbar),
        icon = Icons.Outlined.VerticalAlignBottom,
        summary = stringResource(Res.string.auto_hide_navigationbar_summary),
        isChecked = autoHideNavBar,
        onCheck = onAutoHideNavChange,
        onClick = { onDetailClick(DETAIL_HIDE_NAV) },
        screenPosition = Position.Middle
    )

    val dualPanePref = rememberSwitchPreference(
        dualPane,
        title = stringResource(Res.string.gallery_dual_pane),
        icon = Icons.Outlined.VerticalSplit,
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
        icon = Icons.Outlined.Title,
        summary = stringResource(Res.string.show_selection_titles_summary),
        isChecked = showSelectionTitles,
        onCheck = onSelectionTitlesChange,
        onClick = { onDetailClick(DETAIL_SELECTION_TITLES) },
        screenPosition = Position.Top
    )

    val selectionActionsPref = rememberPreference(
        title = stringResource(Res.string.selection_actions),
        icon = Icons.Outlined.Checklist,
        summary = stringResource(Res.string.selection_actions_summary),
        onClick = onOpenSelectionActions,
        screenPosition = Position.Bottom
    )

    val settingsList: SnapshotStateList<SettingsEntity> = remember(
        launchHeader, forcedLastScreenPref, barsHeader, showOldNavbarPref,
        autoOpenKeyboardPref, autoHideNavBarPref, dualPanePref,
        interfaceHeader, showSelectionTitlesPref, selectionActionsPref
    ) {
        mutableStateListOf(
            launchHeader,
            forcedLastScreenPref,
            barsHeader,
            showOldNavbarPref,
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

/** The icon a launch-screen tile shows: the screen it opens, or history for "last used". */
@Composable
private fun LaunchTargetPreview(route: String, timelineRoute: String) {
    PreviewIconBadge(
        icon = when (route) {
            LAUNCH_ROUTE_AUTO -> Icons.Outlined.History
            timelineRoute -> Icons.Outlined.Photo
            else -> Icons.Outlined.PhotoLibrary
        }
    )
}

/** The floating navigation group with its search button, or the classic bar with labels. */
@Composable
private fun OldNavbarPreview(isChecked: Boolean) {
    val container = MaterialTheme.colorScheme.surfaceContainerHigh
    val inactive = MaterialTheme.colorScheme.onSurfaceVariant
    val items = listOf(
        Icons.Outlined.Photo to stringResource(Res.string.timeline),
        Icons.Outlined.CollectionsBookmark to stringResource(Res.string.albums),
        Icons.Outlined.PhotoLibrary to stringResource(Res.string.library),
    )
    if (isChecked) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(container)
                .padding(vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            items.forEachIndexed { index, (icon, label) ->
                val active = index == 0
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(
                                if (active) MaterialTheme.colorScheme.secondaryContainer
                                else Color.Transparent
                            )
                            .padding(horizontal = 16.dp, vertical = 4.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = if (active) MaterialTheme.colorScheme.onSecondaryContainer else inactive,
                        )
                    }
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelMedium,
                        color = if (active) MaterialTheme.colorScheme.onSurface else inactive,
                    )
                }
            }
        }
    } else {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier
                    .height(64.dp)
                    .clip(CircleShape)
                    .background(container)
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items.forEachIndexed { index, (icon, _) ->
                    val active = index == 0
                    Box(
                        modifier = Modifier
                            .size(width = if (active) 56.dp else 48.dp, height = 40.dp)
                            .clip(CircleShape)
                            .background(
                                if (active) MaterialTheme.colorScheme.secondaryContainer
                                else Color.Transparent
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = if (active) MaterialTheme.colorScheme.onSecondaryContainer else inactive,
                        )
                    }
                }
            }
            Box(
                modifier = Modifier.size(64.dp).clip(CircleShape).background(container),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Outlined.Search, contentDescription = null, tint = inactive)
            }
        }
    }
}
