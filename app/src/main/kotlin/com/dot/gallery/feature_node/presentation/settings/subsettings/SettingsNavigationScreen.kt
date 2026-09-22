/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.feature_node.presentation.settings.subsettings

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CollectionsBookmark
import androidx.compose.material.icons.outlined.Photo
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.ViewTimeline
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dot.gallery.R
import com.dot.gallery.core.Position
import com.dot.gallery.core.Settings.Misc.rememberAutoHideNavBar
import com.dot.gallery.core.Settings.Misc.rememberAutoHideSearchBar
import com.dot.gallery.core.Settings.Misc.rememberAutoOpenSearchKeyboard
import com.dot.gallery.core.Settings.Misc.rememberForcedLastScreen
import com.dot.gallery.core.Settings.Misc.rememberLastScreen
import com.dot.gallery.core.Settings.Misc.rememberOldNavbar
import com.dot.gallery.core.Settings.Misc.rememberSelectionSheetConfig
import com.dot.gallery.core.Settings.Misc.rememberShowSelectionTitles
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import com.dot.gallery.core.SettingsEntity
import com.dot.gallery.feature_node.presentation.settings.components.BaseSettingsScreen
import com.dot.gallery.feature_node.presentation.settings.components.ChooserPreferenceDetailScreen
import com.dot.gallery.feature_node.presentation.settings.components.PreferenceOption
import com.dot.gallery.feature_node.presentation.settings.components.SwitchPreferenceDetailScreen
import com.dot.gallery.feature_node.presentation.settings.components.rememberPreference
import com.dot.gallery.feature_node.presentation.settings.components.rememberSwitchPreference
import com.dot.gallery.feature_node.presentation.util.Screen
import com.dot.gallery.core.LocalEventHandler
import com.dot.gallery.core.navigate
import com.dot.gallery.ui.core.Icons as AppIcons

private const val DETAIL_LAUNCH_SCREEN = "launch_screen"
private const val DETAIL_OLD_NAVBAR = "old_navbar"
private const val DETAIL_HIDE_SEARCH = "hide_search"
private const val DETAIL_OPEN_KEYBOARD = "open_keyboard"
private const val DETAIL_HIDE_NAV = "hide_nav"
private const val DETAIL_SELECTION_TITLES = "selection_titles"

@Composable
fun SettingsNavigationScreen() {
    var detailKey by rememberSaveable { mutableStateOf<String?>(null) }
    val listState = rememberLazyListState()

    var lastScreen by rememberLastScreen()
    var forcedLastScreen by rememberForcedLastScreen()
    var showOldNavbar by rememberOldNavbar()
    var autoHideSearch by rememberAutoHideSearchBar()
    var autoOpenKeyboard by rememberAutoOpenSearchKeyboard()
    var autoHideNavBar by rememberAutoHideNavBar()
    var showSelectionTitles by rememberShowSelectionTitles()
    val config by rememberSelectionSheetConfig()

    when (detailKey) {
        DETAIL_LAUNCH_SCREEN -> {
            BackHandler { detailKey = null }
            val useLastOpenedScreenLabel = stringResource(R.string.use_last_opened_screen)
            val launchOnTimelineLabel = stringResource(R.string.launch_on_timeline)
            val launchOnAlbumsLabel = stringResource(R.string.launch_on_albums)
            val launchOnLibraryLabel = stringResource(R.string.launch_on_library)
            val launchOptions = remember(
                lastScreen,
                forcedLastScreen,
                useLastOpenedScreenLabel,
                launchOnTimelineLabel,
                launchOnAlbumsLabel,
                launchOnLibraryLabel,
            ) {
                listOf(
                    PreferenceOption("auto", useLastOpenedScreenLabel, !forcedLastScreen),
                    PreferenceOption(Screen.TimelineScreen(), launchOnTimelineLabel, forcedLastScreen && lastScreen == Screen.TimelineScreen()),
                    PreferenceOption(Screen.AlbumsScreen(), launchOnAlbumsLabel, forcedLastScreen && lastScreen == Screen.AlbumsScreen()),
                    PreferenceOption(Screen.LibraryScreen(), launchOnLibraryLabel, forcedLastScreen && lastScreen == Screen.LibraryScreen()),
                )
            }
            ChooserPreferenceDetailScreen(
                title = stringResource(R.string.set_default_launch_screen),
                description = stringResource(R.string.launch_screen_description),
                preview = { LaunchScreenPreview(lastScreen, forcedLastScreen) },
                options = launchOptions,
                onOptionSelected = { selected ->
                    if (selected == "auto") {
                        forcedLastScreen = false
                        lastScreen = Screen.TimelineScreen()
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
                title = stringResource(R.string.old_navbar),
                isChecked = showOldNavbar,
                onCheckedChange = { showOldNavbar = it },
                description = stringResource(R.string.old_navbar_description),
                preview = { checked -> OldNavbarPreview(checked) },
                useColumnLayout = true,
            )
        }
        DETAIL_HIDE_SEARCH -> {
            BackHandler { detailKey = null }
            SwitchPreferenceDetailScreen(
                title = stringResource(R.string.auto_hide_searchbar),
                isChecked = autoHideSearch,
                onCheckedChange = { autoHideSearch = it },
                description = stringResource(R.string.auto_hide_searchbar_description),
            )
        }
        DETAIL_OPEN_KEYBOARD -> {
            BackHandler { detailKey = null }
            SwitchPreferenceDetailScreen(
                title = stringResource(R.string.auto_open_search_keyboard),
                isChecked = autoOpenKeyboard,
                onCheckedChange = { autoOpenKeyboard = it },
                description = stringResource(R.string.auto_open_search_keyboard_description),
            )
        }
        DETAIL_HIDE_NAV -> {
            BackHandler { detailKey = null }
            SwitchPreferenceDetailScreen(
                title = stringResource(R.string.auto_hide_navigationbar),
                isChecked = autoHideNavBar,
                onCheckedChange = { autoHideNavBar = it },
                description = stringResource(R.string.auto_hide_navigationbar_description),
            )
        }
        DETAIL_SELECTION_TITLES -> {
            BackHandler { detailKey = null }
            SwitchPreferenceDetailScreen(
                title = stringResource(R.string.show_selection_titles),
                isChecked = showSelectionTitles,
                onCheckedChange = { showSelectionTitles = it },
                description = stringResource(R.string.show_selection_titles_description),
                preview = { checked ->
                    SelectionSheetPreview(
                        config = config.sanitized(),
                        showTitlesOverride = checked
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
                showSelectionTitles = showSelectionTitles,
                onSelectionTitlesChange = { showSelectionTitles = it },
                onDetailClick = { detailKey = it },
                listState = listState,
            )
        }
    }
}

@Composable
private fun LaunchScreenPreview(lastScreen: String, forcedLastScreen: Boolean) {
    data class LaunchOption(val route: String, val label: String, val icon: androidx.compose.ui.graphics.vector.ImageVector)
    val options = listOf(
        LaunchOption(Screen.TimelineScreen(), stringResource(R.string.timeline), Icons.Outlined.Photo),
        LaunchOption(Screen.AlbumsScreen(), stringResource(R.string.albums), Icons.Outlined.CollectionsBookmark),
        LaunchOption(Screen.LibraryScreen(), stringResource(R.string.library), Icons.Outlined.PhotoLibrary),
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
                    text = stringResource(R.string.settings_navigation_auto_symbol),
                    style = MaterialTheme.typography.titleMedium,
                    color = if (isAutoSelected) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                )
            }
            Text(
                text = stringResource(R.string.auto),
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
        Icons.Outlined.Photo to stringResource(R.string.timeline),
        Icons.Outlined.CollectionsBookmark to stringResource(R.string.albums),
        Icons.Outlined.PhotoLibrary to stringResource(R.string.library),
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
                                    else androidx.compose.ui.graphics.Color.Transparent
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
    showSelectionTitles: Boolean,
    onSelectionTitlesChange: (Boolean) -> Unit,
    onDetailClick: (String) -> Unit,
    listState: LazyListState,
) {
    @Composable
    fun settings(): SnapshotStateList<SettingsEntity> {
        val launchHeaderTitle = stringResource(R.string.set_default_launch_screen)
        val launchHeader = remember(launchHeaderTitle) {
            SettingsEntity.Header(title = launchHeaderTitle)
        }

        val launchOnTimelineLabel = stringResource(R.string.launch_on_timeline)
        val launchOnAlbumsLabel = stringResource(R.string.launch_on_albums)
        val launchOnLibraryLabel = stringResource(R.string.launch_on_library)
        val launchAutoLabel = stringResource(R.string.launch_auto)
        val launchSummary = remember(
            lastScreen,
            forcedLastScreen,
            launchOnTimelineLabel,
            launchOnAlbumsLabel,
            launchOnLibraryLabel,
            launchAutoLabel,
        ) {
            if (forcedLastScreen) {
                when (lastScreen) {
                    Screen.TimelineScreen() -> launchOnTimelineLabel
                    Screen.AlbumsScreen() -> launchOnAlbumsLabel
                    else -> launchOnLibraryLabel
                }
            } else {
                launchAutoLabel
            }
        }
        val forcedLastScreenPref = rememberPreference(
            forcedLastScreen, lastScreen,
            title = stringResource(R.string.set_default_screen),
            summary = launchSummary,
            onClick = { onDetailClick(DETAIL_LAUNCH_SCREEN) },
            screenPosition = Position.Alone
        )

        val barsHeaderTitle = stringResource(R.string.navigation)
        val barsHeader = remember(barsHeaderTitle) {
            SettingsEntity.Header(title = barsHeaderTitle)
        }

        val showOldNavbarPref = rememberSwitchPreference(
            showOldNavbar,
            title = stringResource(R.string.old_navbar),
            summary = stringResource(R.string.old_navbar_summary),
            isChecked = showOldNavbar,
            onCheck = onOldNavbarChange,
            onClick = { onDetailClick(DETAIL_OLD_NAVBAR) },
            screenPosition = Position.Top
        )

        val autoHideSearchPref = rememberSwitchPreference(
            autoHideSearch,
            title = stringResource(R.string.auto_hide_searchbar),
            summary = stringResource(R.string.auto_hide_searchbar_summary),
            isChecked = autoHideSearch,
            onCheck = onAutoHideSearchChange,
            onClick = { onDetailClick(DETAIL_HIDE_SEARCH) },
            screenPosition = Position.Middle
        )

        val autoOpenKeyboardPref = rememberSwitchPreference(
            autoOpenKeyboard,
            title = stringResource(R.string.auto_open_search_keyboard),
            summary = stringResource(R.string.auto_open_search_keyboard_summary),
            isChecked = autoOpenKeyboard,
            onCheck = onAutoOpenKeyboardChange,
            onClick = { onDetailClick(DETAIL_OPEN_KEYBOARD) },
            screenPosition = Position.Middle
        )

        val autoHideNavBarPref = rememberSwitchPreference(
            autoHideNavBar,
            title = stringResource(R.string.auto_hide_navigationbar),
            summary = stringResource(R.string.auto_hide_navigationbar_summary),
            isChecked = autoHideNavBar,
            onCheck = onAutoHideNavChange,
            onClick = { onDetailClick(DETAIL_HIDE_NAV) },
            screenPosition = Position.Bottom
        )

        val interfaceHeaderTitle = stringResource(R.string.interface_settings)
        val interfaceHeader = remember(interfaceHeaderTitle) {
            SettingsEntity.Header(title = interfaceHeaderTitle)
        }

        val showSelectionTitlesPref = rememberSwitchPreference(
            showSelectionTitles,
            title = stringResource(R.string.show_selection_titles),
            summary = stringResource(R.string.show_selection_titles_summary),
            isChecked = showSelectionTitles,
            onCheck = onSelectionTitlesChange,
            onClick = { onDetailClick(DETAIL_SELECTION_TITLES) },
            screenPosition = Position.Top
        )

        val eventHandler = LocalEventHandler.current
        val selectionActionsPref = rememberPreference(
            title = stringResource(R.string.selection_actions),
            summary = stringResource(R.string.selection_actions_summary),
            onClick = {
                eventHandler.navigate(Screen.SettingsSelectionActionsScreen())
            },
            screenPosition = Position.Bottom
        )

        return remember(
            launchHeader, forcedLastScreenPref, barsHeader, showOldNavbarPref, autoHideSearchPref,
            autoOpenKeyboardPref, autoHideNavBarPref, interfaceHeader, showSelectionTitlesPref, selectionActionsPref
        ) {
            mutableStateListOf(
                launchHeader,
                forcedLastScreenPref,
                barsHeader,
                showOldNavbarPref,
                autoHideSearchPref,
                autoOpenKeyboardPref,
                autoHideNavBarPref,
                interfaceHeader,
                showSelectionTitlesPref,
                selectionActionsPref
            )
        }
    }

    BaseSettingsScreen(
        title = stringResource(R.string.settings_navigation),
        settingsList = settings(),
        listState = listState,
        searchRoute = Screen.SettingsNavigationScreen(),
    )
}
