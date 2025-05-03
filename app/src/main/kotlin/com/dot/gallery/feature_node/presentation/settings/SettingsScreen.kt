/*
 * SPDX-FileCopyrightText: 2023 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.feature_node.presentation.settings

import android.os.Build
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.work.WorkManager
import com.dot.gallery.R
import com.dot.gallery.core.Position
import com.dot.gallery.core.Settings
import com.dot.gallery.core.Settings.Misc.rememberAudioFocus
import com.dot.gallery.core.Settings.Misc.rememberAutoHideNavBar
import com.dot.gallery.core.Settings.Misc.rememberAutoHideOnVideoPlay
import com.dot.gallery.core.Settings.Misc.rememberAutoHideSearchBar
import com.dot.gallery.core.Settings.Misc.rememberForcedLastScreen
import com.dot.gallery.core.Settings.Misc.rememberFullBrightnessView
import com.dot.gallery.core.Settings.Misc.rememberLastScreen
import com.dot.gallery.core.Settings.Misc.rememberVideoAutoplay
import com.dot.gallery.core.SettingsEntity
import com.dot.gallery.core.forceMetadataCollect
import com.dot.gallery.feature_node.presentation.settings.components.SettingsAppHeader
import com.dot.gallery.feature_node.presentation.settings.components.SettingsItem
import com.dot.gallery.feature_node.presentation.util.Screen
import com.dot.gallery.feature_node.presentation.util.restartApplication
import com.dot.gallery.ui.theme.Shapes
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    navigateUp: () -> Unit,
    navigate: (String) -> Unit
) {
    val scrollBehavior =
        TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())
    val showLaunchScreenDialog = rememberSaveable { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val context = LocalContext.current
    val settingsList = rememberSettingsList(
        navigate = navigate,
        showLaunchScreenDialog = showLaunchScreenDialog,
        launchMetadataRefresh = {
            Toast.makeText(context, context.getString(R.string.metadata_refresh_toast), Toast.LENGTH_SHORT).show()
            WorkManager.getInstance(context).forceMetadataCollect()
        }
    )

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.settings_title)
                    )
                },
                navigationIcon = {
                    IconButton(onClick = navigateUp) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back_cd)
                        )
                    }
                },
                scrollBehavior = scrollBehavior
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize(),
            contentPadding = padding
        ) {
            item { SettingsAppHeader() }
            items(
                items = settingsList,
                key = { it.title + it.type.toString() }
            ) { SettingsItem(it) }
        }

        if (showLaunchScreenDialog.value) {
            ModalBottomSheet(
                onDismissRequest = { showLaunchScreenDialog.value = false },
                contentWindowInsets = {
                    WindowInsets(bottom = WindowInsets.systemBars.getBottom(LocalDensity.current))
                }
            ) {
                Column(
                    modifier = Modifier
                        .background(
                            color = MaterialTheme.colorScheme.surfaceContainerLow,
                            shape = Shapes.extraLarge
                        )
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    CompositionLocalProvider(
                        value = LocalTextStyle.provides(
                            TextStyle.Default.copy(
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        )
                    ) {
                        val scope = rememberCoroutineScope()
                        Text(
                            text = stringResource(R.string.set_default_launch_screen),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Medium
                        )

                        var lastScreen by rememberLastScreen()
                        var forcedLastScreen by rememberForcedLastScreen()
                        val lastOpenScreenString = stringResource(R.string.use_last_opened_screen)
                        val timelineOpenScreenString = stringResource(R.string.launch_on_timeline)
                        val albumsOpenScreenString = stringResource(R.string.launch_on_albums)
                        val libraryOpenScreenString = stringResource(R.string.launch_on_library)

                        val openItems = remember(lastScreen, forcedLastScreen) {
                            listOf(
                                Triple(lastOpenScreenString, !forcedLastScreen) {
                                    forcedLastScreen = false
                                    lastScreen = Screen.TimelineScreen()
                                },
                                Triple(
                                    timelineOpenScreenString,
                                    forcedLastScreen && lastScreen == Screen.TimelineScreen()
                                ) {
                                    forcedLastScreen = true
                                    lastScreen = Screen.TimelineScreen()
                                },
                                Triple(
                                    albumsOpenScreenString,
                                    forcedLastScreen && lastScreen == Screen.AlbumsScreen()
                                ) {
                                    forcedLastScreen = true
                                    lastScreen = Screen.AlbumsScreen()
                                },
                                Triple(
                                    libraryOpenScreenString,
                                    forcedLastScreen && lastScreen == Screen.LibraryScreen()
                                ) {
                                    forcedLastScreen = true
                                    lastScreen = Screen.LibraryScreen()
                                }
                            )
                        }

                        LazyColumn {
                            items(
                                items = openItems,
                                key = { it.first }
                            ) { (title, enabled, onClick) ->
                                ListItem(
                                    modifier = Modifier
                                        .clip(Shapes.large)
                                        .clickable(onClick = onClick),
                                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                                    headlineContent = {
                                        Text(text = title)
                                    },
                                    trailingContent = {
                                        RadioButton(
                                            selected = enabled,
                                            onClick = onClick
                                        )
                                    }
                                )
                            }
                        }
                        Button(onClick = {
                            scope.launch {
                                sheetState.hide()
                                showLaunchScreenDialog.value = false
                            }
                        }) {
                            Text(
                                text = stringResource(R.string.done),
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun rememberSettingsList(
    navigate: (String) -> Unit,
    showLaunchScreenDialog: MutableState<Boolean>,
    launchMetadataRefresh: () -> Unit
): SnapshotStateList<SettingsEntity> {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var forceTheme by Settings.Misc.rememberForceTheme()
    val forceThemeValuePref = remember(forceTheme) {
        SettingsEntity.SwitchPreference(
            title = context.getString(R.string.settings_follow_system_theme_title),
            isChecked = !forceTheme,
            onCheck = { forceTheme = !it },
            screenPosition = Position.Top
        )
    }
    var darkModeValue by Settings.Misc.rememberIsDarkMode()
    val darkThemePref = remember(darkModeValue, forceTheme) {
        SettingsEntity.SwitchPreference(
            title = context.getString(R.string.settings_dark_mode_title),
            enabled = forceTheme,
            isChecked = darkModeValue,
            onCheck = { darkModeValue = it },
            screenPosition = Position.Middle
        )
    }
    var amoledModeValue by Settings.Misc.rememberIsAmoledMode()
    val amoledModePref = remember(amoledModeValue) {
        SettingsEntity.SwitchPreference(
            title = context.getString(R.string.amoled_mode_title),
            summary = context.getString(R.string.amoled_mode_summary),
            isChecked = amoledModeValue,
            onCheck = { amoledModeValue = it },
            screenPosition = Position.Bottom
        )
    }
    var trashCanEnabled by Settings.Misc.rememberTrashEnabled()
    val trashCanEnabledPref = remember(trashCanEnabled) {
        SettingsEntity.SwitchPreference(
            title = context.getString(R.string.settings_trash_title),
            summary = context.getString(R.string.settings_trash_summary),
            isChecked = trashCanEnabled,
            onCheck = { trashCanEnabled = it },
            screenPosition = Position.Top
        )
    }
    var secureMode by Settings.Misc.rememberSecureMode()
    val secureModePref = remember(secureMode) {
        SettingsEntity.SwitchPreference(
            title = context.getString(R.string.secure_mode_title),
            summary = context.getString(R.string.secure_mode_summary),
            isChecked = secureMode,
            onCheck = { secureMode = it },
            screenPosition = Position.Middle
        )
    }

    var allowVibrations by Settings.Misc.rememberAllowVibrations()
    val allowVibrationsPref = remember(allowVibrations) {
        SettingsEntity.SwitchPreference(
            title = context.getString(R.string.allow_vibrations),
            summary = context.getString(R.string.allow_vibrations_summary),
            isChecked = allowVibrations,
            onCheck = { allowVibrations = it },
            screenPosition = Position.Bottom
        )
    }

    var groupByMonth by Settings.Misc.rememberTimelineGroupByMonth()
    val groupByMonthPref = remember(groupByMonth) {
        SettingsEntity.SwitchPreference(
            title = context.getString(R.string.monthly_timeline_title),
            summary = context.getString(R.string.monthly_timeline_summary),
            isChecked = groupByMonth,
            onCheck = {
                scope.launch {
                    scope.async { groupByMonth = it }.await()
                    delay(50)
                    context.restartApplication()
                }
            },
            screenPosition = Position.Middle
        )
    }

    val shouldAllowBlur = remember { Build.VERSION.SDK_INT >= Build.VERSION_CODES.S }
    var allowBlur by Settings.Misc.rememberAllowBlur()
    val allowBlurPref = remember(allowBlur) {
        SettingsEntity.SwitchPreference(
            title = context.getString(R.string.fancy_blur),
            summary = context.getString(R.string.fancy_blur_summary),
            isChecked = allowBlur,
            onCheck = { allowBlur = it },
            enabled = shouldAllowBlur,
            screenPosition = Position.Middle
        )
    }

    var showOldNavbar by Settings.Misc.rememberOldNavbar()
    val showOldNavbarPref = remember(showOldNavbar) {
        SettingsEntity.SwitchPreference(
            title = context.getString(R.string.old_navbar),
            summary = context.getString(R.string.old_navbar_summary),
            isChecked = showOldNavbar,
            onCheck = { showOldNavbar = it },
            screenPosition = Position.Top
        )
    }

    var hideTimelineOnAlbum by Settings.Album.rememberHideTimelineOnAlbum()
    val hideTimelineOnAlbumPref = remember(hideTimelineOnAlbum) {
        SettingsEntity.SwitchPreference(
            title = context.getString(R.string.hide_timeline_for_albums),
            summary = context.getString(R.string.hide_timeline_for_album_summary),
            isChecked = hideTimelineOnAlbum,
            onCheck = { hideTimelineOnAlbum = it },
            screenPosition = Position.Middle
        )
    }

    val lastScreen by rememberLastScreen()
    val forcedLastScreen by rememberForcedLastScreen()
    val summary = remember(lastScreen, forcedLastScreen) {
        if (forcedLastScreen) {
            when (lastScreen) {
                Screen.TimelineScreen() -> context.getString(R.string.launch_on_timeline)
                Screen.AlbumsScreen() -> context.getString(R.string.launch_on_albums)
                else -> context.getString(R.string.launch_on_library)
            }
        } else {
            context.getString(R.string.launch_auto)
        }
    }
    val forcedLastScreenPref = remember(forcedLastScreen, lastScreen) {
        SettingsEntity.Preference(
            title = context.getString(R.string.set_default_screen),
            summary = summary,
            onClick = { showLaunchScreenDialog.value = true },
            screenPosition = Position.Middle
        )
    }

    var autoHideSearchSetting by rememberAutoHideSearchBar()

    val autoHideSearch = remember(autoHideSearchSetting) {
        SettingsEntity.SwitchPreference(
            title = context.getString(R.string.auto_hide_searchbar),
            summary = context.getString(R.string.auto_hide_searchbar_summary),
            isChecked = autoHideSearchSetting,
            onCheck = { autoHideSearchSetting = it },
            screenPosition = Position.Middle
        )
    }

    var autoHideNavigationSetting by rememberAutoHideNavBar()

    val autoHideNavigation = remember(autoHideNavigationSetting) {
        SettingsEntity.SwitchPreference(
            title = context.getString(R.string.auto_hide_navigationbar),
            summary = context.getString(R.string.auto_hide_navigationbar_summary),
            isChecked = autoHideNavigationSetting,
            onCheck = { autoHideNavigationSetting = it },
            screenPosition = Position.Bottom
        )
    }

    var audioFocus by rememberAudioFocus()
    val audioFocusPref = remember(audioFocus) {
        SettingsEntity.SwitchPreference(
            title = context.getString(R.string.take_audio_focus_title),
            summary = context.getString(R.string.take_audio_focus_summary),
            isChecked = audioFocus,
            onCheck = {
                scope.launch {
                    audioFocus = it
                    delay(50)
                    context.restartApplication()
                }
            },
            screenPosition = Position.Middle
        )
    }

    var fullBrightnessView by rememberFullBrightnessView()
    val fullBrightnessViewPref = remember(fullBrightnessView) {
        SettingsEntity.SwitchPreference(
            title = context.getString(R.string.full_brightness_view_title),
            summary = context.getString(R.string.full_brightness_view_summary),
            isChecked = fullBrightnessView,
            onCheck = { fullBrightnessView = it },
            screenPosition = Position.Middle
        )
    }

    var autoHideOnVideoPlay by rememberAutoHideOnVideoPlay()
    val autoHideOnVideoPlayPref = remember(autoHideOnVideoPlay) {
        SettingsEntity.SwitchPreference(
            title = context.getString(R.string.auto_hide_on_video_play),
            summary = context.getString(R.string.auto_hide_on_video_play_summary),
            isChecked = autoHideOnVideoPlay,
            onCheck = { autoHideOnVideoPlay = it },
            screenPosition = Position.Middle
        )
    }

    var autoPlayVideo by rememberVideoAutoplay()
    val autoPlayVideoPref = remember(autoPlayVideo) {
        SettingsEntity.SwitchPreference(
            title = context.getString(R.string.auto_play_video),
            summary = context.getString(R.string.auto_play_video_summary),
            isChecked = autoPlayVideo,
            onCheck = { autoPlayVideo = it },
            screenPosition = Position.Middle
        )
    }

    var noClassification by Settings.Misc.rememberNoClassification()
    val noClassificationPref = remember(noClassification) {
        SettingsEntity.SwitchPreference(
            title = context.getString(R.string.no_classification),
            summary = context.getString(R.string.no_classification_summary),
            isChecked = noClassification,
            onCheck = { noClassification = it },
            screenPosition = Position.Alone
        )
    }

    val dateHeaderPref = remember {
        SettingsEntity.Preference(
            title = context.getString(R.string.date_header),
            summary = context.getString(R.string.date_header_summary),
            onClick = { navigate(Screen.DateFormatScreen()) },
            screenPosition = Position.Top
        )
    }

    var sharedElements by Settings.Misc.rememberSharedElements()

    val sharedElementsPref = remember(sharedElements) {
        SettingsEntity.SwitchPreference(
            title = context.getString(R.string.shared_elements),
            summary = context.getString(R.string.shared_elements_summary),
            isChecked = sharedElements,
            onCheck = { sharedElements = it },
            screenPosition = Position.Bottom
        )
    }

    val refreshMetadataPref = remember {
        SettingsEntity.Preference(
            title = context.getString(R.string.refresh_metadata),
            summary = context.getString(R.string.refresh_metadata_summary),
            onClick = { launchMetadataRefresh() },
            screenPosition = Position.Alone
        )
    }

    return remember(
        arrayOf(
            forceTheme,
            darkModeValue,
            trashCanEnabled,
            groupByMonth,
            amoledModeValue,
            secureMode
        )
    ) {
        mutableStateListOf<SettingsEntity>().apply {
            /** ********************* **/
            add(SettingsEntity.Header(title = context.getString(R.string.settings_theme_header)))
            /** Theme Section Start **/
            add(forceThemeValuePref)
            add(darkThemePref)
            add(amoledModePref)
            /** ********************* **/
            add(SettingsEntity.Header(title = context.getString(R.string.settings_general)))
            /** General Section Start **/
            add(trashCanEnabledPref)
            add(secureModePref)
            add(allowVibrationsPref)
            /** ********************* **/
            add(SettingsEntity.Header(title = context.getString(R.string.customization)))
            /** Customization Section Start **/
            add(dateHeaderPref)
            add(groupByMonthPref)
            add(allowBlurPref)
            add(hideTimelineOnAlbumPref)
            add(forcedLastScreenPref)
            add(audioFocusPref)
            add(fullBrightnessViewPref)
            add(autoHideOnVideoPlayPref)
            add(autoPlayVideoPref)
            add(sharedElementsPref)
            /** ********************* **/
            /** Navigation Section Start **/
            add(SettingsEntity.Header(title = context.getString(R.string.navigation)))
            add(showOldNavbarPref)
            add(autoHideSearch)
            add(autoHideNavigation)
            add(SettingsEntity.Header(title = context.getString(R.string.ai_category)))
            add(noClassificationPref)
            /** ********************* **/
            /** Database Section Start **/
            add(SettingsEntity.Header(title = context.getString(R.string.database)))
            add(refreshMetadataPref)
            /** ********************* **/
        }
    }
}