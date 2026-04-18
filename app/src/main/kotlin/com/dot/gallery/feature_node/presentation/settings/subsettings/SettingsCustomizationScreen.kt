package com.dot.gallery.feature_node.presentation.settings.subsettings

import android.os.Build
import androidx.appcompat.content.res.AppCompatResources
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.dot.gallery.R
import com.dot.gallery.core.LocalEventHandler
import com.dot.gallery.core.Position
import com.dot.gallery.core.Settings
import com.dot.gallery.core.Settings.Misc.rememberAppNameAlias
import com.dot.gallery.core.Settings.Misc.rememberAudioFocus
import com.dot.gallery.core.Settings.Misc.rememberAutoHideNavBar
import com.dot.gallery.core.Settings.Misc.rememberAutoHideOnVideoPlay
import com.dot.gallery.core.Settings.Misc.rememberAutoHideSearchBar
import com.dot.gallery.core.Settings.Misc.rememberDefaultImageEditor
import com.dot.gallery.core.Settings.Misc.rememberGroupSimilarMedia
import com.dot.gallery.core.Settings.Misc.rememberFavoriteIconPosition
import com.dot.gallery.core.Settings.Misc.rememberForcedLastScreen
import com.dot.gallery.core.Settings.Misc.rememberFullBrightnessView
import com.dot.gallery.core.Settings.Misc.rememberLastScreen
import com.dot.gallery.core.Settings.Misc.rememberShowFavoriteButton
import com.dot.gallery.core.Settings.Misc.rememberShowMediaViewDateHeader
import com.dot.gallery.core.Settings.Misc.rememberShowSelectionTitles
import com.dot.gallery.core.Settings.Misc.rememberVideoAutoplay
import com.dot.gallery.core.SettingsEntity
import com.dot.gallery.core.navigate
import com.dot.gallery.core.util.SdkCompat
import com.dot.gallery.feature_node.presentation.settings.components.BaseSettingsScreen
import com.dot.gallery.feature_node.presentation.settings.components.rememberPreference
import com.dot.gallery.feature_node.presentation.settings.components.rememberSwitchPreference
import com.dot.gallery.feature_node.presentation.util.Screen
import com.dot.gallery.feature_node.presentation.util.changeAppAlias
import com.dot.gallery.feature_node.presentation.util.getEditImageCapableApps
import com.dot.gallery.feature_node.presentation.util.restartApplication
import com.dot.gallery.ui.theme.Shapes
import androidx.core.graphics.drawable.toBitmap
import com.google.accompanist.drawablepainter.rememberDrawablePainter
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

        var groupSimilarMedia by rememberGroupSimilarMedia()
        val groupSimilarMediaPref = rememberSwitchPreference(
            groupSimilarMedia,
            title = stringResource(R.string.group_similar_media_title),
            summary = stringResource(R.string.group_similar_media_summary),
            isChecked = groupSimilarMedia,
            onCheck = { groupSimilarMedia = it },
            screenPosition = Position.Middle
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

        val showAppNameDialog = rememberSaveable { mutableStateOf(false) }
        val appNameSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        var appNameAlias by rememberAppNameAlias()
        val appNamePref = rememberPreference(
            appNameAlias,
            title = stringResource(R.string.change_app_name),
            summary = stringResource(R.string.change_app_name_summary),
            onClick = { showAppNameDialog.value = true },
            screenPosition = Position.Top
        )
        if (showAppNameDialog.value) {
            ModalBottomSheet(
                sheetState = appNameSheetState,
                onDismissRequest = { showAppNameDialog.value = false },
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
                        val appNameScope = rememberCoroutineScope()
                        Text(
                            text = stringResource(R.string.choose_app_name),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Medium
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        val aliasOptions = remember(appNameAlias) {
                            listOf(
                                Settings.Misc.ALIAS_REFRA to (appNameAlias == Settings.Misc.ALIAS_REFRA),
                                Settings.Misc.ALIAS_GALLERY to (appNameAlias == Settings.Misc.ALIAS_GALLERY)
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            aliasOptions.forEach { (alias, selected) ->
                                val borderColor = if (selected) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.outlineVariant
                                }
                                val containerColor = if (selected) {
                                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                                } else {
                                    Color.Transparent
                                }
                                Column(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(16.dp))
                                        .clickable {
                                            appNameAlias = alias
                                            context.changeAppAlias(alias)
                                        }
                                        .border(
                                            width = 2.dp,
                                            color = borderColor,
                                            shape = RoundedCornerShape(16.dp)
                                        )
                                        .background(containerColor)
                                        .padding(horizontal = 24.dp, vertical = 16.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Image(
                                        painter = rememberDrawablePainter(
                                            drawable = AppCompatResources.getDrawable(context, R.mipmap.ic_launcher_round)
                                        ),
                                        contentDescription = alias,
                                        modifier = Modifier
                                            .size(64.dp)
                                            .clip(CircleShape)
                                    )
                                    Text(
                                        text = alias,
                                        style = MaterialTheme.typography.bodyLarge,
                                        fontWeight = FontWeight.Medium,
                                        textAlign = TextAlign.Center
                                    )
                                    Icon(
                                        imageVector = Icons.Filled.CheckCircle,
                                        contentDescription = null,
                                        tint = if (selected) {
                                            MaterialTheme.colorScheme.primary
                                        } else {
                                            MaterialTheme.colorScheme.outlineVariant
                                        },
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(4.dp))

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Info,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp)
                            )
                            Text(
                                text = stringResource(R.string.change_app_name_info),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Spacer(modifier = Modifier.height(4.dp))

                        Button(onClick = {
                            appNameScope.launch {
                                appNameSheetState.hide()
                                showAppNameDialog.value = false
                                context.restartApplication()
                            }
                        }) {
                            Text(
                                text = stringResource(R.string.action_confirm),
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                        }
                    }
                }
            }
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
            screenPosition = Position.Middle
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
            screenPosition = Position.Middle
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
        var showSelectionTitles by rememberShowSelectionTitles()
        val showSelectionTitlesPref = rememberSwitchPreference(
            showSelectionTitles,
            title = stringResource(R.string.show_selection_titles),
            summary = stringResource(R.string.show_selection_titles_summary),
            isChecked = showSelectionTitles,
            onCheck = { showSelectionTitles = it },
            screenPosition = Position.Middle
        )

        val showFavIconDialog = rememberSaveable { mutableStateOf(false) }
        val favIconSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        var favIconPosition by rememberFavoriteIconPosition()
        val favIconPositionLabel = remember(favIconPosition) {
            when (favIconPosition) {
                Settings.Misc.FAV_ICON_DISABLED -> context.getString(R.string.fav_position_disabled)
                Settings.Misc.FAV_ICON_BOTTOM_START -> context.getString(R.string.fav_position_bottom_start)
                Settings.Misc.FAV_ICON_TOP_END -> context.getString(R.string.fav_position_top_end)
                Settings.Misc.FAV_ICON_TOP_START -> context.getString(R.string.fav_position_top_start)
                else -> context.getString(R.string.fav_position_bottom_end)
            }
        }
        val favIconPositionPref = rememberPreference(
            favIconPosition,
            title = stringResource(R.string.favorite_icon_on_thumbnails),
            summary = favIconPositionLabel,
            onClick = { showFavIconDialog.value = true },
            screenPosition = Position.Bottom
        )
        if (showFavIconDialog.value) {
            ModalBottomSheet(
                sheetState = favIconSheetState,
                onDismissRequest = { showFavIconDialog.value = false },
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
                        val favScope = rememberCoroutineScope()
                        Text(
                            text = stringResource(R.string.choose_favorite_icon_position),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Medium
                        )

                        // Animated preview
                        val isHidden = favIconPosition == Settings.Misc.FAV_ICON_DISABLED
                        val heartAlpha by animateFloatAsState(
                            targetValue = if (isHidden) 0f else 1f,
                            label = "heartAlpha"
                        )
                        val favAlignment = when (favIconPosition) {
                            Settings.Misc.FAV_ICON_BOTTOM_START -> Alignment.BottomStart
                            Settings.Misc.FAV_ICON_TOP_END -> Alignment.TopEnd
                            Settings.Misc.FAV_ICON_TOP_START -> Alignment.TopStart
                            else -> Alignment.BottomEnd
                        }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterHorizontally)
                        ) {
                            repeat(3) { index ->
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .aspectRatio(1f)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                                ) {
                                    if (index == 1) {
                                        Icon(
                                            modifier = Modifier
                                                .align(favAlignment)
                                                .padding(6.dp)
                                                .size(14.dp),
                                            imageVector = Icons.Filled.Favorite,
                                            tint = Color.Red.copy(alpha = heartAlpha),
                                            contentDescription = null
                                        )
                                    }
                                }
                            }
                        }

                        val positionOptions = remember(favIconPosition) {
                            listOf(
                                Triple(Settings.Misc.FAV_ICON_DISABLED, context.getString(R.string.fav_position_disabled), favIconPosition == Settings.Misc.FAV_ICON_DISABLED),
                                Triple(Settings.Misc.FAV_ICON_BOTTOM_END, context.getString(R.string.fav_position_bottom_end), favIconPosition == Settings.Misc.FAV_ICON_BOTTOM_END),
                                Triple(Settings.Misc.FAV_ICON_BOTTOM_START, context.getString(R.string.fav_position_bottom_start), favIconPosition == Settings.Misc.FAV_ICON_BOTTOM_START),
                                Triple(Settings.Misc.FAV_ICON_TOP_END, context.getString(R.string.fav_position_top_end), favIconPosition == Settings.Misc.FAV_ICON_TOP_END),
                                Triple(Settings.Misc.FAV_ICON_TOP_START, context.getString(R.string.fav_position_top_start), favIconPosition == Settings.Misc.FAV_ICON_TOP_START)
                            )
                        }

                        LazyColumn {
                            items(
                                items = positionOptions,
                                key = { it.first }
                            ) { (value, label, selected) ->
                                ListItem(
                                    modifier = Modifier
                                        .clip(Shapes.large)
                                        .clickable { favIconPosition = value },
                                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                                    headlineContent = {
                                        Text(text = label)
                                    },
                                    trailingContent = {
                                        RadioButton(
                                            selected = selected,
                                            onClick = { favIconPosition = value }
                                        )
                                    }
                                )
                            }
                        }
                        Button(onClick = {
                            favScope.launch {
                                favIconSheetState.hide()
                                showFavIconDialog.value = false
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
            screenPosition = Position.Middle
        )

        var showMediaDateHeader by rememberShowMediaViewDateHeader()
        val showMediaDateHeaderPref = rememberSwitchPreference(
            showMediaDateHeader,
            title = stringResource(R.string.show_date_header),
            summary = stringResource(R.string.show_date_header_summary),
            isChecked = showMediaDateHeader,
            onCheck = { showMediaDateHeader = it },
            screenPosition = Position.Middle
        )

        var showFavoriteButton by rememberShowFavoriteButton()
        val showFavoriteButtonPref = rememberSwitchPreference(
            showFavoriteButton,
            title = stringResource(R.string.show_favorite_button),
            summary = stringResource(R.string.show_favorite_button_summary),
            isChecked = showFavoriteButton,
            onCheck = { showFavoriteButton = it },
            screenPosition = Position.Middle
        )

        val showEditorDialog = rememberSaveable { mutableStateOf(false) }
        val editorSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        var defaultEditor by rememberDefaultImageEditor()
        val editApps = remember(context, context::getEditImageCapableApps)
        val editorSummary = remember(defaultEditor, editApps) {
            if (defaultEditor == Settings.Misc.EDITOR_BUILTIN) {
                context.getString(R.string.default_image_editor_builtin)
            } else {
                editApps.find { it.activityInfo.packageName == defaultEditor }
                    ?.loadLabel(context.packageManager)?.toString()
                    ?: context.getString(R.string.default_image_editor_builtin)
            }
        }
        val defaultEditorPref = rememberPreference(
            defaultEditor,
            title = stringResource(R.string.default_image_editor),
            summary = editorSummary,
            onClick = { showEditorDialog.value = true },
            screenPosition = Position.Bottom
        )
        if (showEditorDialog.value) {
            ModalBottomSheet(
                sheetState = editorSheetState,
                onDismissRequest = { showEditorDialog.value = false },
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
                        val editorScope = rememberCoroutineScope()
                        Text(
                            text = stringResource(R.string.choose_default_editor),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Medium
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            // Built-in editor option
                            val builtinSelected = defaultEditor == Settings.Misc.EDITOR_BUILTIN
                            val builtinBorderColor = if (builtinSelected) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.outlineVariant
                            }
                            val builtinContainerColor = if (builtinSelected) {
                                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                            } else {
                                Color.Transparent
                            }
                            Column(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(16.dp))
                                    .clickable { defaultEditor = Settings.Misc.EDITOR_BUILTIN }
                                    .border(
                                        width = 2.dp,
                                        color = builtinBorderColor,
                                        shape = RoundedCornerShape(16.dp)
                                    )
                                    .background(builtinContainerColor)
                                    .padding(horizontal = 24.dp, vertical = 16.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Image(
                                    painter = rememberDrawablePainter(
                                        drawable = AppCompatResources.getDrawable(context, R.mipmap.ic_launcher_round)
                                    ),
                                    contentDescription = stringResource(R.string.default_image_editor_builtin),
                                    modifier = Modifier
                                        .size(64.dp)
                                        .clip(CircleShape)
                                )
                                Text(
                                    text = stringResource(R.string.default_image_editor_builtin),
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.Medium,
                                    textAlign = TextAlign.Center
                                )
                                Icon(
                                    imageVector = Icons.Filled.CheckCircle,
                                    contentDescription = null,
                                    tint = if (builtinSelected) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.outlineVariant
                                    },
                                    modifier = Modifier.size(24.dp)
                                )
                            }

                            // External editor options
                            editApps.forEach { app ->
                                val packageName = app.activityInfo.packageName
                                val appLabel = remember(app) {
                                    app.loadLabel(context.packageManager).toString()
                                }
                                val appIcon = remember(app) {
                                    try {
                                        app.loadIcon(context.packageManager).toBitmap().asImageBitmap()
                                    } catch (_: Exception) {
                                        null
                                    }
                                }
                                if (appIcon != null) {
                                    val selected = defaultEditor == packageName
                                    val borderColor = if (selected) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.outlineVariant
                                    }
                                    val containerColor = if (selected) {
                                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                                    } else {
                                        Color.Transparent
                                    }
                                    Column(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(16.dp))
                                            .clickable { defaultEditor = packageName }
                                            .border(
                                                width = 2.dp,
                                                color = borderColor,
                                                shape = RoundedCornerShape(16.dp)
                                            )
                                            .background(containerColor)
                                            .padding(horizontal = 24.dp, vertical = 16.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Image(
                                            bitmap = appIcon,
                                            contentDescription = appLabel,
                                            modifier = Modifier
                                                .size(64.dp)
                                                .clip(CircleShape)
                                        )
                                        Text(
                                            text = appLabel,
                                            style = MaterialTheme.typography.bodyLarge,
                                            fontWeight = FontWeight.Medium,
                                            textAlign = TextAlign.Center
                                        )
                                        Icon(
                                            imageVector = Icons.Filled.CheckCircle,
                                            contentDescription = null,
                                            tint = if (selected) {
                                                MaterialTheme.colorScheme.primary
                                            } else {
                                                MaterialTheme.colorScheme.outlineVariant
                                            },
                                            modifier = Modifier.size(24.dp)
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(4.dp))

                        Button(onClick = {
                            editorScope.launch {
                                editorSheetState.hide()
                                showEditorDialog.value = false
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
            groupSimilarMediaPref,
            hideTimelineOnAlbumPref,
            allowBlurPref,
            forcedLastScreenPref,
            audioFocusPref,
            fullBrightnessViewPref,
            autoHideOnVideoPlayPref,
            autoPlayVideoPref,
            sharedElementsPref,
            showMediaDateHeaderPref,
            showSelectionTitlesPref,
            appNamePref,
            favIconPositionPref,
            showFavoriteButtonPref,
            defaultEditorPref
        ) {
            mutableStateListOf<SettingsEntity>().apply {
                add(timelineHeader)
                add(groupByMonthPref)
                add(groupSimilarMediaPref)
                add(hideTimelineOnAlbumPref)
                add(forcedLastScreenPref)
                add(showSelectionTitlesPref)
                if (SdkCompat.supportsFavorites) {
                    add(favIconPositionPref)
                }

                add(interfaceHeader)
                add(appNamePref)
                add(allowBlurPref)
                add(sharedElementsPref)

                add(mediaViewHeader)
                add(dateHeaderPref)
                add(fullBrightnessViewPref)
                add(showMediaDateHeaderPref)
                if (SdkCompat.supportsFavorites) {
                    add(showFavoriteButtonPref)
                }
                add(defaultEditorPref)

                add(videoPlaybackHeader)
                add(audioFocusPref)
                add(autoHideOnVideoPlayPref)
                add(autoPlayVideoPref)

                add(navigationHeader)
                add(showOldNavbarPref)
                add(autoHideSearch)
                add(autoHideNavigation)
            }
        }
    }

    BaseSettingsScreen(
        title = stringResource(R.string.customization),
        settingsList = settings(),
    )
}