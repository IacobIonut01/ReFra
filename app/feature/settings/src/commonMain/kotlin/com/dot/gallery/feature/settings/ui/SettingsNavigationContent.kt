/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.feature.settings.ui

import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Checklist
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Keyboard
import androidx.compose.material.icons.outlined.Title
import androidx.compose.material.icons.outlined.VerticalAlignBottom
import androidx.compose.material.icons.outlined.VerticalSplit
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.backhandler.BackHandler
import com.dot.gallery.core.Position
import com.dot.gallery.core.SettingsEntity
import com.dot.gallery.core.presentation.components.rememberSelectionSheetConfig
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
import com.dot.gallery.feature.settings.generated.resources.Res
import com.dot.gallery.feature.settings.generated.resources.auto_hide_navigationbar
import com.dot.gallery.feature.settings.generated.resources.auto_hide_navigationbar_description
import com.dot.gallery.feature.settings.generated.resources.auto_hide_navigationbar_summary
import com.dot.gallery.feature.settings.generated.resources.auto_open_search_keyboard
import com.dot.gallery.feature.settings.generated.resources.auto_open_search_keyboard_description
import com.dot.gallery.feature.settings.generated.resources.auto_open_search_keyboard_summary
import com.dot.gallery.feature.settings.generated.resources.gallery_dual_pane
import com.dot.gallery.feature.settings.generated.resources.gallery_dual_pane_description
import com.dot.gallery.feature.settings.generated.resources.gallery_dual_pane_summary
import com.dot.gallery.feature.settings.generated.resources.interface_settings
import com.dot.gallery.feature.settings.generated.resources.launch_auto
import com.dot.gallery.feature.settings.generated.resources.launch_auto_description
import com.dot.gallery.feature.settings.generated.resources.launch_on_library
import com.dot.gallery.feature.settings.generated.resources.launch_on_library_description
import com.dot.gallery.feature.settings.generated.resources.launch_on_timeline
import com.dot.gallery.feature.settings.generated.resources.launch_on_timeline_description
import com.dot.gallery.feature.settings.generated.resources.launch_screen_description
import com.dot.gallery.feature.settings.generated.resources.navigation
import com.dot.gallery.feature.settings.generated.resources.selection_actions
import com.dot.gallery.feature.settings.generated.resources.selection_actions_summary
import com.dot.gallery.feature.settings.generated.resources.set_default_launch_screen
import com.dot.gallery.feature.settings.generated.resources.set_default_screen
import com.dot.gallery.feature.settings.generated.resources.settings_navigation
import com.dot.gallery.feature.settings.generated.resources.show_selection_titles
import com.dot.gallery.feature.settings.generated.resources.show_selection_titles_description
import com.dot.gallery.feature.settings.generated.resources.show_selection_titles_summary
import com.dot.gallery.feature.settings.generated.resources.use_last_opened_screen

private const val DETAIL_LAUNCH_SCREEN = "launch_screen"
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
            val launchAutoDescription = stringResource(Res.string.launch_auto_description)
            val launchOnTimelineDescription = stringResource(Res.string.launch_on_timeline_description)
            val launchOnLibraryDescription = stringResource(Res.string.launch_on_library_description)
            val launchOptions = remember(
                lastScreen,
                forcedLastScreen,
                state,
                useLastOpenedScreenLabel,
                launchOnTimelineLabel,
                launchOnLibraryLabel,
                launchAutoDescription,
                launchOnTimelineDescription,
                launchOnLibraryDescription,
            ) {
                listOf(
                    PreferenceOption(
                        LAUNCH_ROUTE_AUTO, useLastOpenedScreenLabel,
                        !forcedLastScreen,
                        description = launchAutoDescription,
                    ),
                    PreferenceOption(
                        state.timelineRoute, launchOnTimelineLabel,
                        forcedLastScreen && lastScreen == state.timelineRoute,
                        description = launchOnTimelineDescription,
                    ),
                    PreferenceOption(
                        state.libraryRoute, launchOnLibraryLabel,
                        forcedLastScreen && normalizeLaunchRoute(lastScreen) == state.libraryRoute,
                        description = launchOnLibraryDescription,
                    ),
                )
            }
            ChooserPreferenceDetailScreen(
                title = stringResource(Res.string.set_default_launch_screen),
                description = stringResource(Res.string.launch_screen_description),
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

    val autoOpenKeyboardPref = rememberSwitchPreference(
        autoOpenKeyboard,
        title = stringResource(Res.string.auto_open_search_keyboard),
        icon = Icons.Outlined.Keyboard,
        summary = stringResource(Res.string.auto_open_search_keyboard_summary),
        isChecked = autoOpenKeyboard,
        onCheck = onAutoOpenKeyboardChange,
        onClick = { onDetailClick(DETAIL_OPEN_KEYBOARD) },
        screenPosition = Position.Top
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
        launchHeader, forcedLastScreenPref, barsHeader,
        autoOpenKeyboardPref, autoHideNavBarPref, dualPanePref,
        interfaceHeader, showSelectionTitlesPref, selectionActionsPref
    ) {
        mutableStateListOf(
            launchHeader,
            forcedLastScreenPref,
            barsHeader,
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
