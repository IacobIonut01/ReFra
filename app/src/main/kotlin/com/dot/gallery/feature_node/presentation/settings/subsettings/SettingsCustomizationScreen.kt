package com.dot.gallery.feature_node.presentation.settings.subsettings

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.dot.gallery.R
import com.dot.gallery.core.LocalEventHandler
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
import com.dot.gallery.core.navigate
import com.dot.gallery.feature_node.presentation.settings.components.BaseSettingsScreen
import com.dot.gallery.feature_node.presentation.settings.components.rememberPreference
import com.dot.gallery.feature_node.presentation.settings.components.rememberSwitchPreference
import com.dot.gallery.feature_node.presentation.util.Screen
import com.dot.gallery.feature_node.presentation.util.restartApplication
import com.dot.gallery.ui.theme.Shapes
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsCustomizationScreen() {
    @Composable
    fun settings(): SnapshotStateList<SettingsEntity> {
        val context = LocalContext.current
        val scope = rememberCoroutineScope()
        val eventHandler = LocalEventHandler.current

        val timelineHeader = remember(context) {
            SettingsEntity.Header(
                title = context.getString(R.string.timeline)
            )
        }

        val dateHeaderPref = rememberPreference(
            title = stringResource(R.string.date_header),
            summary = stringResource(R.string.date_header_summary),
            onClick = { eventHandler.navigate(Screen.DateFormatScreen()) },
            screenPosition = Position.Top
        )
        var groupByMonth by Settings.Misc.rememberTimelineGroupByMonth()
        val groupByMonthPref = rememberSwitchPreference(
            groupByMonth,
            title = stringResource(R.string.monthly_timeline_title),
            summary = stringResource(R.string.monthly_timeline_summary),
            isChecked = groupByMonth,
            onCheck = {
                scope.launch {
                    scope.async { groupByMonth = it }.await()
                    delay(50)
                    context.restartApplication()
                }
            },
            screenPosition = Position.Top
        )

        var hideTimelineOnAlbum by Settings.Album.rememberHideTimelineOnAlbum()
        val hideTimelineOnAlbumPref = rememberSwitchPreference(
            hideTimelineOnAlbum,
            title = stringResource(R.string.hide_timeline_for_albums),
            summary = stringResource(R.string.hide_timeline_for_album_summary),
            isChecked = hideTimelineOnAlbum,
            onCheck = { hideTimelineOnAlbum = it },
            screenPosition = Position.Middle
        )

        val interfaceHeader = remember(context) {
            SettingsEntity.Header(
                title = context.getString(R.string.interface_settings)
            )
        }

        val shouldAllowBlur = remember { Build.VERSION.SDK_INT >= Build.VERSION_CODES.S }
        var allowBlur by Settings.Misc.rememberAllowBlur()
        val allowBlurPref = rememberSwitchPreference(
            allowBlur,
            title = stringResource(R.string.fancy_blur),
            summary = stringResource(R.string.fancy_blur_summary),
            isChecked = allowBlur,
            onCheck = { allowBlur = it },
            enabled = shouldAllowBlur,
            screenPosition = Position.Top
        )

        val showLaunchScreenDialog = rememberSaveable { mutableStateOf(false) }
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

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
        val forcedLastScreenPref = rememberPreference(
            forcedLastScreen, lastScreen,
            title = stringResource(R.string.set_default_screen),
            summary = summary,
            onClick = { showLaunchScreenDialog.value = true },
            screenPosition = Position.Bottom
        )
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

        val mediaViewHeader = remember(context) {
            SettingsEntity.Header(
                title = context.getString(R.string.media_view)
            )
        }

        var fullBrightnessView by rememberFullBrightnessView()
        val fullBrightnessViewPref = rememberSwitchPreference(
            fullBrightnessView,
            title = stringResource(R.string.full_brightness_view_title),
            summary = stringResource(R.string.full_brightness_view_summary),
            isChecked = fullBrightnessView,
            onCheck = { fullBrightnessView = it },
            screenPosition = Position.Bottom
        )

        val videoPlaybackHeader = remember(context) {
            SettingsEntity.Header(
                title = context.getString(R.string.video_playback)
            )
        }

        var audioFocus by rememberAudioFocus()
        val audioFocusPref = rememberSwitchPreference(
            audioFocus,
            title = stringResource(R.string.take_audio_focus_title),
            summary = stringResource(R.string.take_audio_focus_summary),
            isChecked = audioFocus,
            onCheck = {
                scope.launch {
                    audioFocus = it
                    delay(50)
                    context.restartApplication()
                }
            },
            screenPosition = Position.Top
        )

        var autoHideOnVideoPlay by rememberAutoHideOnVideoPlay()
        val autoHideOnVideoPlayPref = rememberSwitchPreference(
            autoHideOnVideoPlay,
            title = stringResource(R.string.auto_hide_on_video_play),
            summary = stringResource(R.string.auto_hide_on_video_play_summary),
            isChecked = autoHideOnVideoPlay,
            onCheck = { autoHideOnVideoPlay = it },
            screenPosition = Position.Middle
        )

        var autoPlayVideo by rememberVideoAutoplay()
        val autoPlayVideoPref = rememberSwitchPreference(
            autoPlayVideo,
            title = stringResource(R.string.auto_play_video),
            summary = stringResource(R.string.auto_play_video_summary),
            isChecked = autoPlayVideo,
            onCheck = { autoPlayVideo = it },
            screenPosition = Position.Bottom
        )

        var sharedElements by Settings.Misc.rememberSharedElements()
        val sharedElementsPref = rememberSwitchPreference(
            sharedElements,
            title = stringResource(R.string.shared_elements),
            summary = stringResource(R.string.shared_elements_summary),
            isChecked = sharedElements,
            onCheck = { sharedElements = it },
            screenPosition = Position.Bottom
        )

        val navigationHeader = remember(context) {
            SettingsEntity.Header(
                title = context.getString(R.string.navigation)
            )
        }

        var showOldNavbar by Settings.Misc.rememberOldNavbar()
        val showOldNavbarPref = rememberSwitchPreference(
            showOldNavbar,
            title = stringResource(R.string.old_navbar),
            summary = stringResource(R.string.old_navbar_summary),
            isChecked = showOldNavbar,
            onCheck = { showOldNavbar = it },
            screenPosition = Position.Top
        )


        var autoHideSearchSetting by rememberAutoHideSearchBar()
        val autoHideSearch = rememberSwitchPreference(
            autoHideSearchSetting,
            title = stringResource(R.string.auto_hide_searchbar),
            summary = stringResource(R.string.auto_hide_searchbar_summary),
            isChecked = autoHideSearchSetting,
            onCheck = { autoHideSearchSetting = it },
            screenPosition = Position.Middle
        )


        var autoHideNavigationSetting by rememberAutoHideNavBar()
        val autoHideNavigation = rememberSwitchPreference(
            autoHideNavigationSetting,
            title = stringResource(R.string.auto_hide_navigationbar),
            summary = stringResource(R.string.auto_hide_navigationbar_summary),
            isChecked = autoHideNavigationSetting,
            onCheck = { autoHideNavigationSetting = it },
            screenPosition = Position.Bottom
        )

        return remember(
            dateHeaderPref,
            groupByMonthPref,
            hideTimelineOnAlbumPref,
            allowBlurPref,
            forcedLastScreenPref,
            audioFocusPref,
            fullBrightnessViewPref,
            autoHideOnVideoPlayPref,
            autoPlayVideoPref,
            sharedElementsPref
        ) {
            mutableStateListOf(
                timelineHeader,
                groupByMonthPref,
                hideTimelineOnAlbumPref,
                forcedLastScreenPref,

                interfaceHeader,
                allowBlurPref,
                sharedElementsPref,

                mediaViewHeader,
                dateHeaderPref,
                fullBrightnessViewPref,

                videoPlaybackHeader,
                audioFocusPref,
                autoHideOnVideoPlayPref,
                autoPlayVideoPref,

                navigationHeader,
                showOldNavbarPref,
                autoHideSearch,
                autoHideNavigation
            )
        }
    }

    BaseSettingsScreen(
        title = stringResource(R.string.customization),
        settingsList = settings(),
    )
}