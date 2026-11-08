/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.feature.settings.ui

import androidx.compose.foundation.layout.fillMaxSize
import com.dot.gallery.core.ui.components.PreviewPhoto
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BrightnessHigh
import androidx.compose.material.icons.outlined.CalendarToday
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Grain
import androidx.compose.material.icons.outlined.HdrOn
import androidx.compose.material.icons.outlined.HighQuality
import androidx.compose.material.icons.outlined.ImageSearch
import androidx.compose.material.icons.outlined.PanTool
import androidx.compose.material.icons.outlined.PlayCircle
import androidx.compose.material.icons.outlined.Slideshow
import androidx.compose.material.icons.outlined.SmartButton
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.outlined.TouchApp
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.VideoSettings
import androidx.compose.material.icons.outlined.Videocam
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.dot.gallery.core.Position
import com.dot.gallery.core.SettingsEntity
import com.dot.gallery.core.presentation.components.DragHandle
import com.dot.gallery.core.ui.components.BaseSettingsScreen
import com.dot.gallery.core.ui.components.ChooserPreferenceDetailScreen
import com.dot.gallery.core.ui.components.PreferenceOption
import com.dot.gallery.core.ui.components.SettingsItem
import com.dot.gallery.core.ui.components.SwitchPreferenceDetailScreen
import com.dot.gallery.core.ui.components.rememberPreference
import com.dot.gallery.core.ui.components.rememberSwitchPreference
import com.dot.gallery.core.ui.mediaview.VisualSearchTarget
import com.dot.gallery.core.ui.mediaview.resolveVisualSearchTarget
import com.dot.gallery.core.ui.mediaview.icon
import com.dot.gallery.core.ui.settings.rememberPreference
import com.dot.gallery.domain.settings.EDITOR_BUILTIN
import com.dot.gallery.domain.settings.EDITOR_STUDIO
import com.dot.gallery.domain.settings.LONG_PRESS_ACTION_CUTOUT
import com.dot.gallery.domain.settings.LONG_PRESS_ACTION_NONE
import com.dot.gallery.domain.settings.LONG_PRESS_ACTION_ROTATE
import com.dot.gallery.domain.settings.REENCODE_MODE_AUTO
import com.dot.gallery.domain.settings.REENCODE_MODE_MANUAL
import com.dot.gallery.domain.settings.SettingsKeys
import com.dot.gallery.domain.settings.VISUAL_SEARCH_CONVERT_ALWAYS
import com.dot.gallery.domain.settings.VISUAL_SEARCH_CONVERT_ASK
import com.dot.gallery.domain.settings.VISUAL_SEARCH_CONVERT_NEVER
import com.dot.gallery.domain.settings.VISUAL_SEARCH_FORMAT_JPEG
import com.dot.gallery.domain.settings.VISUAL_SEARCH_FORMAT_PNG
import com.dot.gallery.domain.settings.VISUAL_SEARCH_FORMAT_WEBP
import com.dot.gallery.domain.settings.VISUAL_SEARCH_POSITION_BOTTOM
import com.dot.gallery.domain.settings.VISUAL_SEARCH_POSITION_TOP
import com.dot.gallery.domain.settings.VISUAL_SEARCH_PROVIDER_AUTO
import com.dot.gallery.domain.settings.resolveLongPressAction
import com.dot.gallery.ui.core.Icons as GalleryIcons
import com.dot.gallery.ui.core.icons.VisualSearch
import kotlin.math.roundToInt
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import com.dot.gallery.feature.settings.generated.resources.Res
import com.dot.gallery.feature.settings.generated.resources.auto_hide_on_video_play
import com.dot.gallery.feature.settings.generated.resources.auto_hide_on_video_play_description
import com.dot.gallery.feature.settings.generated.resources.auto_hide_on_video_play_summary
import com.dot.gallery.feature.settings.generated.resources.auto_play_video
import com.dot.gallery.feature.settings.generated.resources.auto_play_video_description
import com.dot.gallery.feature.settings.generated.resources.auto_play_video_summary
import com.dot.gallery.feature.settings.generated.resources.dark_media_viewer_description
import com.dot.gallery.feature.settings.generated.resources.dark_media_viewer_summary
import com.dot.gallery.feature.settings.generated.resources.dark_media_viewer_title
import com.dot.gallery.feature.settings.generated.resources.default_editor_description
import com.dot.gallery.feature.settings.generated.resources.default_image_editor
import com.dot.gallery.feature.settings.generated.resources.default_video_editor
import com.dot.gallery.feature.settings.generated.resources.default_video_editor_ask
import com.dot.gallery.feature.settings.generated.resources.default_video_editor_description
import com.dot.gallery.feature.settings.generated.resources.default_video_editor_studio
import com.dot.gallery.feature.settings.generated.resources.default_image_editor_builtin
import com.dot.gallery.feature.settings.generated.resources.default_image_editor_studio
import com.dot.gallery.feature.settings.generated.resources.disable_smoothing_description
import com.dot.gallery.feature.settings.generated.resources.disable_smoothing_summary
import com.dot.gallery.feature.settings.generated.resources.disable_smoothing_title
import com.dot.gallery.feature.settings.generated.resources.full_brightness_view_description
import com.dot.gallery.feature.settings.generated.resources.full_brightness_view_summary
import com.dot.gallery.feature.settings.generated.resources.full_brightness_view_title
import com.dot.gallery.feature.settings.generated.resources.hdr_video_playback
import com.dot.gallery.feature.settings.generated.resources.hdr_video_playback_description
import com.dot.gallery.feature.settings.generated.resources.hdr_video_playback_summary
import com.dot.gallery.feature.settings.generated.resources.long_press_action_cutout
import com.dot.gallery.feature.settings.generated.resources.long_press_action_cutout_unavailable
import com.dot.gallery.feature.settings.generated.resources.long_press_action_description
import com.dot.gallery.feature.settings.generated.resources.long_press_action_none
import com.dot.gallery.feature.settings.generated.resources.long_press_action_rotate
import com.dot.gallery.feature.settings.generated.resources.long_press_action_title
import com.dot.gallery.feature.settings.generated.resources.media_view
import com.dot.gallery.feature.settings.generated.resources.reencode_quality_jxl_effort
import com.dot.gallery.feature.settings.generated.resources.reencode_quality_lossy
import com.dot.gallery.feature.settings.generated.resources.reencode_quality_mode_auto_summary
import com.dot.gallery.feature.settings.generated.resources.reencode_quality_mode_manual
import com.dot.gallery.feature.settings.generated.resources.reencode_quality_summary
import com.dot.gallery.feature.settings.generated.resources.reencode_quality_title
import com.dot.gallery.feature.settings.generated.resources.settings_media_viewer
import com.dot.gallery.feature.settings.generated.resources.show_date_header
import com.dot.gallery.feature.settings.generated.resources.show_date_header_description
import com.dot.gallery.feature.settings.generated.resources.show_date_header_summary
import com.dot.gallery.feature.settings.generated.resources.show_favorite_button
import com.dot.gallery.feature.settings.generated.resources.show_favorite_button_description
import com.dot.gallery.feature.settings.generated.resources.show_favorite_button_summary
import com.dot.gallery.feature.settings.generated.resources.slideshow
import com.dot.gallery.feature.settings.generated.resources.slideshow_settings_summary
import com.dot.gallery.feature.settings.generated.resources.tap_sides_to_navigate_description
import com.dot.gallery.feature.settings.generated.resources.tap_sides_to_navigate_summary
import com.dot.gallery.feature.settings.generated.resources.tap_sides_to_navigate_title
import com.dot.gallery.feature.settings.generated.resources.video_playback
import com.dot.gallery.feature.settings.generated.resources.video_surface_rebind
import com.dot.gallery.feature.settings.generated.resources.video_surface_rebind_description
import com.dot.gallery.feature.settings.generated.resources.video_surface_rebind_summary
import com.dot.gallery.feature.settings.generated.resources.viewer_action_bar
import com.dot.gallery.feature.settings.generated.resources.viewer_action_bar_summary
import com.dot.gallery.feature.settings.generated.resources.visual_search_convert_always
import com.dot.gallery.feature.settings.generated.resources.visual_search_convert_always_summary
import com.dot.gallery.feature.settings.generated.resources.visual_search_convert_ask
import com.dot.gallery.feature.settings.generated.resources.visual_search_convert_ask_summary
import com.dot.gallery.feature.settings.generated.resources.visual_search_convert_format_header
import com.dot.gallery.feature.settings.generated.resources.visual_search_convert_format_summary
import com.dot.gallery.feature.settings.generated.resources.visual_search_convert_header
import com.dot.gallery.feature.settings.generated.resources.visual_search_convert_never
import com.dot.gallery.feature.settings.generated.resources.visual_search_convert_never_summary
import com.dot.gallery.feature.settings.generated.resources.visual_search_convert_summary
import com.dot.gallery.feature.settings.generated.resources.visual_search_description
import com.dot.gallery.feature.settings.generated.resources.visual_search_format_jpeg_summary
import com.dot.gallery.feature.settings.generated.resources.visual_search_format_png_summary
import com.dot.gallery.feature.settings.generated.resources.visual_search_format_webp_summary
import com.dot.gallery.feature.settings.generated.resources.visual_search_none_installed
import com.dot.gallery.feature.settings.generated.resources.visual_search_position_bottom
import com.dot.gallery.feature.settings.generated.resources.visual_search_position_bottom_summary
import com.dot.gallery.feature.settings.generated.resources.visual_search_position_header
import com.dot.gallery.feature.settings.generated.resources.visual_search_position_summary
import com.dot.gallery.feature.settings.generated.resources.visual_search_position_top
import com.dot.gallery.feature.settings.generated.resources.visual_search_position_top_summary
import com.dot.gallery.feature.settings.generated.resources.visual_search_provider_auto
import com.dot.gallery.feature.settings.generated.resources.visual_search_provider_auto_summary
import com.dot.gallery.feature.settings.generated.resources.visual_search_provider_header
import com.dot.gallery.feature.settings.generated.resources.visual_search_provider_summary
import com.dot.gallery.feature.settings.generated.resources.visual_search_provider_title
import com.dot.gallery.feature.settings.generated.resources.visual_search_summary
import com.dot.gallery.feature.settings.generated.resources.visual_search_title
import com.dot.gallery.feature.settings.generated.resources.visual_search_vault_summary
import com.dot.gallery.feature.settings.generated.resources.visual_search_vault_title
import com.dot.gallery.feature.settings.generated.resources.visual_search_with

private const val DETAIL_BRIGHTNESS = "brightness"
private const val DETAIL_DATE_HEADER = "date_header"
private const val DETAIL_TAP_NAVIGATION = "tap_navigation"
private const val DETAIL_FAV_BUTTON = "fav_button"
private const val DETAIL_EDITOR = "editor"
private const val DETAIL_VIDEO_EDITOR = "video_editor"
private const val DETAIL_AUTO_HIDE_VIDEO = "auto_hide_video"
private const val DETAIL_AUTO_PLAY = "auto_play"
private const val DETAIL_SURFACE_REBIND = "surface_rebind"
private const val DETAIL_HDR_VIDEO = "hdr_video"
private const val DETAIL_DISABLE_SMOOTHING = "disable_smoothing"
private const val DETAIL_DARK_BACKGROUND = "dark_background"
private const val DETAIL_LONG_PRESS_ACTION = "long_press_action"
private const val DETAIL_VISUAL_SEARCH = "visual_search"

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun SettingsMediaViewerContent(
    state: SettingsMediaViewerState = SettingsMediaViewerState(),
    previews: MediaViewerPreviews = MediaViewerPreviews(),
    actions: SettingsMediaViewerActions = SettingsMediaViewerActions(),
) {
    var detailKey by rememberSaveable { mutableStateOf<String?>(null) }
    val listState = rememberLazyListState()

    var fullBrightnessView by rememberPreference(SettingsKeys.fullBrightnessView)
    var showMediaDateHeader by rememberPreference(SettingsKeys.mediaViewDateHeader)
    var tapSidesToNavigate by rememberPreference(SettingsKeys.tapSidesToNavigate)
    var showFavoriteButton by rememberPreference(SettingsKeys.showFavoriteButton)
    var defaultEditor by rememberPreference(SettingsKeys.defaultImageEditor)
    var defaultVideoEditor by rememberPreference(SettingsKeys.defaultVideoEditor)
    var autoHideOnVideoPlay by rememberPreference(SettingsKeys.autoHideOnVideoPlay)
    var autoPlayVideo by rememberPreference(SettingsKeys.videoAutoplay)
    var videoSurfaceRebind by rememberPreference(
        SettingsKeys.videoSurfaceRebind.withDefault(state.surfaceRebindDefault)
    )
    var hdrVideoPlayback by rememberPreference(SettingsKeys.videoHdrPlayback)
    var disableSmoothing by rememberPreference(SettingsKeys.disableSmoothing)
    var darkMediaViewer by rememberPreference(SettingsKeys.darkMediaViewer)
    val legacyLongPressCutout by rememberPreference(SettingsKeys.longPressCutout)
    var storedLongPressAction by rememberPreference(SettingsKeys.longPressAction)
    val longPressAction = resolveLongPressAction(storedLongPressAction, legacyLongPressCutout)
    var reencodeMode by rememberPreference(SettingsKeys.reencodeQualityMode)
    var reencodeLossyQuality by rememberPreference(SettingsKeys.reencodeLossyQuality)
    var reencodeJxlEffort by rememberPreference(SettingsKeys.reencodeJxlEffort)
    var visualSearchEnabled by rememberPreference(SettingsKeys.visualSearchEnabled)
    var visualSearchProvider by rememberPreference(SettingsKeys.visualSearchProvider)
    var visualSearchPosition by rememberPreference(SettingsKeys.visualSearchPosition)
    var visualSearchAllowVault by rememberPreference(SettingsKeys.visualSearchAllowVault)
    var visualSearchConvertMode by rememberPreference(SettingsKeys.visualSearchConvertMode)
    var visualSearchConvertFormat by rememberPreference(SettingsKeys.visualSearchConvertFormat)
    val onTapNavigationChange: (Boolean) -> Unit = { enabled ->
        tapSidesToNavigate = enabled
        actions.onTapSidesChanged()
    }

    when (detailKey) {
        DETAIL_BRIGHTNESS -> {
            BackHandler { detailKey = null }
            SwitchPreferenceDetailScreen(
                title = stringResource(Res.string.full_brightness_view_title),
                isChecked = fullBrightnessView,
                onCheckedChange = { fullBrightnessView = it },
                description = stringResource(Res.string.full_brightness_view_description),
                preview = { checked -> previews.fullBrightness(checked) },
            )
        }
        DETAIL_DATE_HEADER -> {
            BackHandler { detailKey = null }
            SwitchPreferenceDetailScreen(
                title = stringResource(Res.string.show_date_header),
                isChecked = showMediaDateHeader,
                onCheckedChange = { showMediaDateHeader = it },
                description = stringResource(Res.string.show_date_header_description),
                preview = { checked -> previews.dateHeader(checked) },
                useColumnLayout = true,
            )
        }
        DETAIL_TAP_NAVIGATION -> {
            BackHandler { detailKey = null }
            SwitchPreferenceDetailScreen(
                title = stringResource(Res.string.tap_sides_to_navigate_title),
                isChecked = tapSidesToNavigate,
                onCheckedChange = onTapNavigationChange,
                description = stringResource(Res.string.tap_sides_to_navigate_description),
                preview = { checked -> previews.tapNavigation(checked) },
                useColumnLayout = true,
                previewBelowSwitch = true,
            )
        }
        DETAIL_FAV_BUTTON -> {
            BackHandler { detailKey = null }
            SwitchPreferenceDetailScreen(
                title = stringResource(Res.string.show_favorite_button),
                isChecked = showFavoriteButton,
                onCheckedChange = { showFavoriteButton = it },
                description = stringResource(Res.string.show_favorite_button_description),
                preview = { checked -> previews.favoriteButton(checked) },
                useColumnLayout = true,
            )
        }
        DETAIL_EDITOR -> {
            BackHandler { detailKey = null }
            val builtinEditorLabel = stringResource(Res.string.default_image_editor_builtin)
            val studioEditorLabel = stringResource(Res.string.default_image_editor_studio)
            val editorOptions = remember(defaultEditor, state.editApps, builtinEditorLabel, studioEditorLabel) {
                val options = mutableListOf(
                    PreferenceOption(EDITOR_BUILTIN, builtinEditorLabel, defaultEditor == EDITOR_BUILTIN),
                    PreferenceOption(EDITOR_STUDIO, studioEditorLabel, defaultEditor == EDITOR_STUDIO),
                )
                state.editApps.forEach { app ->
                    options.add(PreferenceOption(app.packageName, app.label, defaultEditor == app.packageName))
                }
                options.toList()
            }
            ChooserPreferenceDetailScreen(
                title = stringResource(Res.string.default_image_editor),
                description = stringResource(Res.string.default_editor_description),
                preview = { previews.editor(defaultEditor, state.editApps) },
                options = editorOptions,
                onOptionSelected = { defaultEditor = it },
            )
        }
        DETAIL_VIDEO_EDITOR -> {
            BackHandler { detailKey = null }
            val studioVideoLabel = stringResource(Res.string.default_video_editor_studio)
            val askVideoLabel = stringResource(Res.string.default_video_editor_ask)
            val videoEditorOptions = remember(defaultVideoEditor, state.videoEditApps, studioVideoLabel, askVideoLabel) {
                val options = mutableListOf(
                    PreferenceOption(EDITOR_STUDIO, studioVideoLabel, defaultVideoEditor == EDITOR_STUDIO),
                    PreferenceOption(EDITOR_BUILTIN, askVideoLabel, defaultVideoEditor == EDITOR_BUILTIN),
                )
                state.videoEditApps.forEach { app ->
                    options.add(PreferenceOption(app.packageName, app.label, defaultVideoEditor == app.packageName))
                }
                options.toList()
            }
            ChooserPreferenceDetailScreen(
                title = stringResource(Res.string.default_video_editor),
                description = stringResource(Res.string.default_video_editor_description),
                preview = {},
                options = videoEditorOptions,
                onOptionSelected = { defaultVideoEditor = it },
            )
        }
        DETAIL_DISABLE_SMOOTHING -> {
            BackHandler { detailKey = null }
            SwitchPreferenceDetailScreen(
                title = stringResource(Res.string.disable_smoothing_title),
                isChecked = disableSmoothing,
                onCheckedChange = { disableSmoothing = it },
                description = stringResource(Res.string.disable_smoothing_description),
                preview = { checked -> previews.smoothing(checked) },
            )
        }
        DETAIL_DARK_BACKGROUND -> {
            BackHandler { detailKey = null }
            SwitchPreferenceDetailScreen(
                title = stringResource(Res.string.dark_media_viewer_title),
                isChecked = darkMediaViewer,
                onCheckedChange = { darkMediaViewer = it },
                description = stringResource(Res.string.dark_media_viewer_description),
            )
        }
        DETAIL_LONG_PRESS_ACTION -> {
            BackHandler { detailKey = null }
            val labels = longPressActionLabels()
            val cutoutUnavailableSummary = stringResource(Res.string.long_press_action_cutout_unavailable)
            val options = remember(longPressAction, labels, state.cutoutModelAvailable) {
                labels.map { (value, label) ->
                    val unavailable = value == LONG_PRESS_ACTION_CUTOUT && !state.cutoutModelAvailable
                    PreferenceOption(
                        value = value,
                        label = label,
                        isSelected = longPressAction == value,
                        description = if (unavailable) cutoutUnavailableSummary else null,
                        enabled = !unavailable,
                    )
                }
            }
            ChooserPreferenceDetailScreen(
                title = stringResource(Res.string.long_press_action_title),
                description = stringResource(Res.string.long_press_action_description),
                options = options,
                onOptionSelected = { storedLongPressAction = it },
            )
        }
        DETAIL_AUTO_HIDE_VIDEO -> {
            BackHandler { detailKey = null }
            SwitchPreferenceDetailScreen(
                title = stringResource(Res.string.auto_hide_on_video_play),
                isChecked = autoHideOnVideoPlay,
                onCheckedChange = { autoHideOnVideoPlay = it },
                description = stringResource(Res.string.auto_hide_on_video_play_description),
            )
        }
        DETAIL_AUTO_PLAY -> {
            BackHandler { detailKey = null }
            SwitchPreferenceDetailScreen(
                title = stringResource(Res.string.auto_play_video),
                isChecked = autoPlayVideo,
                onCheckedChange = { autoPlayVideo = it },
                description = stringResource(Res.string.auto_play_video_description),
            )
        }
        DETAIL_SURFACE_REBIND -> {
            BackHandler { detailKey = null }
            SwitchPreferenceDetailScreen(
                title = stringResource(Res.string.video_surface_rebind),
                isChecked = videoSurfaceRebind,
                onCheckedChange = { videoSurfaceRebind = it },
                description = stringResource(Res.string.video_surface_rebind_description),
            )
        }
        DETAIL_HDR_VIDEO -> {
            BackHandler { detailKey = null }
            SwitchPreferenceDetailScreen(
                title = stringResource(Res.string.hdr_video_playback),
                isChecked = hdrVideoPlayback,
                onCheckedChange = { hdrVideoPlayback = it },
                description = stringResource(Res.string.hdr_video_playback_description),
                enabled = state.hdrDisplayCapable,
            )
        }
        DETAIL_VISUAL_SEARCH -> {
            BackHandler { detailKey = null }
            SwitchPreferenceDetailScreen(
                title = stringResource(Res.string.visual_search_title),
                isChecked = visualSearchEnabled,
                onCheckedChange = { visualSearchEnabled = it },
                description = stringResource(Res.string.visual_search_description),
                preview = { checked ->
                    previews.visualSearch(
                        checked,
                        resolveVisualSearchTarget(
                            state.visualSearchTargets,
                            visualSearchProvider
                        )?.icon ?: GalleryIcons.VisualSearch,
                        visualSearchPosition,
                    )
                },
                customContent = {
                    VisualSearchSettingsContent(
                        targets = state.visualSearchTargets,
                        providerIcon = previews.providerIcon,
                        provider = visualSearchProvider,
                        onProviderChange = { visualSearchProvider = it },
                        position = visualSearchPosition,
                        onPositionChange = { visualSearchPosition = it },
                        allowVault = visualSearchAllowVault,
                        onAllowVaultChange = { visualSearchAllowVault = it },
                        convertMode = visualSearchConvertMode,
                        onConvertModeChange = { visualSearchConvertMode = it },
                        convertFormat = visualSearchConvertFormat,
                        onConvertFormatChange = { visualSearchConvertFormat = it },
                    )
                },
            )
        }
        else -> {
            MediaViewerListContent(
                state = state,
                fullBrightnessView = fullBrightnessView,
                onBrightnessChange = { fullBrightnessView = it },
                showMediaDateHeader = showMediaDateHeader,
                onDateHeaderChange = { showMediaDateHeader = it },
                tapSidesToNavigate = tapSidesToNavigate,
                onTapNavigationChange = onTapNavigationChange,
                showFavoriteButton = showFavoriteButton,
                onFavButtonChange = { showFavoriteButton = it },
                defaultEditor = defaultEditor,
                defaultVideoEditor = defaultVideoEditor,
                disableSmoothing = disableSmoothing,
                onDisableSmoothingChange = { disableSmoothing = it },
                darkMediaViewer = darkMediaViewer,
                onDarkMediaViewerChange = { darkMediaViewer = it },
                longPressAction = longPressAction,
                autoHideOnVideoPlay = autoHideOnVideoPlay,
                onAutoHideChange = { autoHideOnVideoPlay = it },
                autoPlayVideo = autoPlayVideo,
                onAutoPlayChange = { autoPlayVideo = it },
                videoSurfaceRebind = videoSurfaceRebind,
                onSurfaceRebindChange = { videoSurfaceRebind = it },
                hdrVideoPlayback = hdrVideoPlayback,
                onHdrVideoPlaybackChange = { hdrVideoPlayback = it },
                reencodeMode = reencodeMode,
                onReencodeModeChange = { reencodeMode = it },
                reencodeLossyQuality = reencodeLossyQuality,
                onReencodeLossyChange = { reencodeLossyQuality = it },
                reencodeJxlEffort = reencodeJxlEffort,
                onReencodeJxlEffortChange = { reencodeJxlEffort = it },
                visualSearchEnabled = visualSearchEnabled,
                onVisualSearchChange = { visualSearchEnabled = it },
                visualSearchSummary = when {
                    state.visualSearchTargets.isEmpty() ->
                        stringResource(Res.string.visual_search_none_installed)
                    !visualSearchEnabled -> stringResource(Res.string.visual_search_summary)
                    else -> resolveVisualSearchTarget(state.visualSearchTargets, visualSearchProvider)
                        ?.let { stringResource(Res.string.visual_search_with, it.displayName) }
                        ?: stringResource(Res.string.visual_search_summary)
                },
                onOpenSlideshow = actions.onOpenSlideshow,
                onOpenViewerActions = actions.onOpenViewerActions,
                onDetailClick = { detailKey = it },
                searchSink = actions.searchSink,
                searchRoute = actions.searchRoute,
                listState = listState,
            )
        }
    }
}

@Composable
private fun MediaViewerListContent(
    state: SettingsMediaViewerState,
    fullBrightnessView: Boolean,
    onBrightnessChange: (Boolean) -> Unit,
    showMediaDateHeader: Boolean,
    onDateHeaderChange: (Boolean) -> Unit,
    tapSidesToNavigate: Boolean,
    onTapNavigationChange: (Boolean) -> Unit,
    showFavoriteButton: Boolean,
    onFavButtonChange: (Boolean) -> Unit,
    defaultEditor: String,
    defaultVideoEditor: String,
    disableSmoothing: Boolean,
    onDisableSmoothingChange: (Boolean) -> Unit,
    darkMediaViewer: Boolean,
    onDarkMediaViewerChange: (Boolean) -> Unit,
    longPressAction: String,
    autoHideOnVideoPlay: Boolean,
    onAutoHideChange: (Boolean) -> Unit,
    autoPlayVideo: Boolean,
    onAutoPlayChange: (Boolean) -> Unit,
    videoSurfaceRebind: Boolean,
    onSurfaceRebindChange: (Boolean) -> Unit,
    hdrVideoPlayback: Boolean,
    onHdrVideoPlaybackChange: (Boolean) -> Unit,
    reencodeMode: String,
    onReencodeModeChange: (String) -> Unit,
    reencodeLossyQuality: Int,
    onReencodeLossyChange: (Int) -> Unit,
    reencodeJxlEffort: Int,
    onReencodeJxlEffortChange: (Int) -> Unit,
    visualSearchEnabled: Boolean,
    onVisualSearchChange: (Boolean) -> Unit,
    visualSearchSummary: String,
    onOpenSlideshow: () -> Unit,
    onOpenViewerActions: () -> Unit,
    onDetailClick: (String) -> Unit,
    searchSink: ((String, List<String>) -> Unit)?,
    searchRoute: String?,
    listState: LazyListState,
) {
    @Composable
    fun settings(): SnapshotStateList<SettingsEntity> {
        val viewingHeaderTitle = stringResource(Res.string.media_view)
        val viewingHeader = remember(viewingHeaderTitle) {
            SettingsEntity.Header(title = viewingHeaderTitle)
        }

        val fullBrightnessViewPref = rememberSwitchPreference(
            fullBrightnessView,
            title = stringResource(Res.string.full_brightness_view_title),
            icon = Icons.Outlined.BrightnessHigh,
            summary = stringResource(Res.string.full_brightness_view_summary),
            isChecked = fullBrightnessView,
            onCheck = onBrightnessChange,
            onClick = { onDetailClick(DETAIL_BRIGHTNESS) },
            screenPosition = Position.Top
        )

        val showMediaDateHeaderPref = rememberSwitchPreference(
            showMediaDateHeader,
            title = stringResource(Res.string.show_date_header),
            icon = Icons.Outlined.CalendarToday,
            summary = stringResource(Res.string.show_date_header_summary),
            isChecked = showMediaDateHeader,
            onCheck = onDateHeaderChange,
            onClick = { onDetailClick(DETAIL_DATE_HEADER) },
            screenPosition = Position.Middle
        )

        val tapNavigationPref = rememberSwitchPreference(
            tapSidesToNavigate,
            title = stringResource(Res.string.tap_sides_to_navigate_title),
            icon = Icons.Outlined.TouchApp,
            summary = stringResource(Res.string.tap_sides_to_navigate_summary),
            isChecked = tapSidesToNavigate,
            onCheck = onTapNavigationChange,
            onClick = { onDetailClick(DETAIL_TAP_NAVIGATION) },
            screenPosition = Position.Middle,
        )

        val showFavoriteButtonPref = rememberSwitchPreference(
            showFavoriteButton,
            title = stringResource(Res.string.show_favorite_button),
            icon = Icons.Outlined.FavoriteBorder,
            summary = stringResource(Res.string.show_favorite_button_summary),
            isChecked = showFavoriteButton,
            onCheck = onFavButtonChange,
            onClick = { onDetailClick(DETAIL_FAV_BUTTON) },
            screenPosition = Position.Middle
        )

        val viewerActionBarTitle = stringResource(Res.string.viewer_action_bar)
        val viewerActionBarSummary = stringResource(Res.string.viewer_action_bar_summary)
        val viewerActionBarPref = remember(
            viewerActionBarTitle,
            viewerActionBarSummary,
            onOpenViewerActions
        ) {
            SettingsEntity.Preference(
                title = viewerActionBarTitle,
                icon = Icons.Outlined.SmartButton,
                summary = viewerActionBarSummary,
                onClick = onOpenViewerActions,
                screenPosition = Position.Middle
            )
        }

        val builtinEditorLabel = stringResource(Res.string.default_image_editor_builtin)
        val studioEditorLabel = stringResource(Res.string.default_image_editor_studio)
        val editorSummary = remember(defaultEditor, state.editApps, builtinEditorLabel, studioEditorLabel) {
            if (defaultEditor == EDITOR_BUILTIN) {
                builtinEditorLabel
            } else if (defaultEditor == EDITOR_STUDIO) {
                studioEditorLabel
            } else {
                state.editApps.find { it.packageName == defaultEditor }?.label
                    ?: builtinEditorLabel
            }
        }
        val defaultEditorPref = rememberPreference(
            defaultEditor,
            title = stringResource(Res.string.default_image_editor),
            icon = Icons.Outlined.Edit,
            summary = editorSummary,
            onClick = { onDetailClick(DETAIL_EDITOR) },
            screenPosition = Position.Middle
        )

        val studioVideoLabel = stringResource(Res.string.default_video_editor_studio)
        val askVideoLabel = stringResource(Res.string.default_video_editor_ask)
        val videoEditorSummary = remember(defaultVideoEditor, state.videoEditApps, studioVideoLabel, askVideoLabel) {
            when (defaultVideoEditor) {
                EDITOR_STUDIO -> studioVideoLabel
                EDITOR_BUILTIN -> askVideoLabel
                else -> state.videoEditApps.find { it.packageName == defaultVideoEditor }?.label
                    ?: askVideoLabel
            }
        }
        val defaultVideoEditorPref = rememberPreference(
            defaultVideoEditor,
            title = stringResource(Res.string.default_video_editor),
            icon = Icons.Outlined.VideoSettings,
            summary = videoEditorSummary,
            onClick = { onDetailClick(DETAIL_VIDEO_EDITOR) },
            screenPosition = Position.Middle
        )

        val disableSmoothingPref = rememberSwitchPreference(
            disableSmoothing,
            title = stringResource(Res.string.disable_smoothing_title),
            icon = Icons.Outlined.Grain,
            summary = stringResource(Res.string.disable_smoothing_summary),
            isChecked = disableSmoothing,
            onCheck = onDisableSmoothingChange,
            onClick = { onDetailClick(DETAIL_DISABLE_SMOOTHING) },
            screenPosition = Position.Middle
        )

        val darkMediaViewerPref = rememberSwitchPreference(
            darkMediaViewer,
            title = stringResource(Res.string.dark_media_viewer_title),
            icon = Icons.Outlined.DarkMode,
            summary = stringResource(Res.string.dark_media_viewer_summary),
            isChecked = darkMediaViewer,
            onCheck = onDarkMediaViewerChange,
            onClick = { onDetailClick(DETAIL_DARK_BACKGROUND) },
            screenPosition = Position.Middle
        )

        val longPressActionSummary = longPressActionLabels()
            .firstOrNull { it.first == longPressAction }?.second.orEmpty()
        val longPressActionPref = rememberPreference(
            longPressAction,
            title = stringResource(Res.string.long_press_action_title),
            icon = Icons.Outlined.PanTool,
            summary = longPressActionSummary,
            onClick = { onDetailClick(DETAIL_LONG_PRESS_ACTION) },
            screenPosition = Position.Middle
        )

        val visualSearchPref = rememberSwitchPreference(
            visualSearchEnabled,
            title = stringResource(Res.string.visual_search_title),
            icon = Icons.Outlined.ImageSearch,
            summary = visualSearchSummary,
            isChecked = visualSearchEnabled,
            onCheck = onVisualSearchChange,
            onClick = { onDetailClick(DETAIL_VISUAL_SEARCH) },
            screenPosition = Position.Middle
        )

        val slideshowTitle = stringResource(Res.string.slideshow)
        val slideshowSummary = stringResource(Res.string.slideshow_settings_summary)
        val slideshowPref = remember(slideshowTitle, slideshowSummary, onOpenSlideshow) {
            SettingsEntity.Preference(
                title = slideshowTitle,
                icon = Icons.Outlined.Slideshow,
                summary = slideshowSummary,
                onClick = onOpenSlideshow,
                screenPosition = Position.Bottom
            )
        }

        // ── Save quality (format-preserving overwrite) ──
        val saveQualityHeaderTitle = stringResource(Res.string.reencode_quality_title)
        val saveQualityHeader = remember(saveQualityHeaderTitle) {
            SettingsEntity.Header(title = saveQualityHeaderTitle)
        }
        val isManualQuality = reencodeMode == REENCODE_MODE_MANUAL
        val manualQualityPref = rememberSwitchPreference(
            reencodeMode,
            title = stringResource(Res.string.reencode_quality_mode_manual),
            icon = Icons.Outlined.Tune,
            summary = stringResource(
                if (isManualQuality) Res.string.reencode_quality_summary
                else Res.string.reencode_quality_mode_auto_summary
            ),
            isChecked = isManualQuality,
            onCheck = { manual ->
                onReencodeModeChange(
                    if (manual) REENCODE_MODE_MANUAL
                    else REENCODE_MODE_AUTO
                )
            },
            screenPosition = if (isManualQuality) Position.Top else Position.Alone
        )
        val lossyQualityPref = SettingsEntity.SeekPreference(
            title = stringResource(Res.string.reencode_quality_lossy),
            icon = Icons.Outlined.HighQuality,
            currentValue = reencodeLossyQuality.toFloat(),
            minValue = 1f,
            maxValue = 100f,
            step = 0,
            valueMultiplier = 1,
            onSeek = { onReencodeLossyChange(it.roundToInt().coerceIn(1, 100)) },
            screenPosition = Position.Middle
        )
        val jxlEffortPref = SettingsEntity.SeekPreference(
            title = stringResource(Res.string.reencode_quality_jxl_effort),
            icon = Icons.Outlined.Speed,
            currentValue = reencodeJxlEffort.toFloat(),
            minValue = 1f,
            maxValue = 9f,
            step = 0,
            valueMultiplier = 1,
            onSeek = { onReencodeJxlEffortChange(it.roundToInt().coerceIn(1, 9)) },
            screenPosition = Position.Bottom
        )

        val videoPlaybackHeaderTitle = stringResource(Res.string.video_playback)
        val videoPlaybackHeader = remember(videoPlaybackHeaderTitle) {
            SettingsEntity.Header(title = videoPlaybackHeaderTitle)
        }

        val autoHideOnVideoPlayPref = rememberSwitchPreference(
            autoHideOnVideoPlay,
            title = stringResource(Res.string.auto_hide_on_video_play),
            icon = Icons.Outlined.VisibilityOff,
            summary = stringResource(Res.string.auto_hide_on_video_play_summary),
            isChecked = autoHideOnVideoPlay,
            onCheck = onAutoHideChange,
            onClick = { onDetailClick(DETAIL_AUTO_HIDE_VIDEO) },
            screenPosition = Position.Top
        )

        val autoPlayVideoPref = rememberSwitchPreference(
            autoPlayVideo,
            title = stringResource(Res.string.auto_play_video),
            icon = Icons.Outlined.PlayCircle,
            summary = stringResource(Res.string.auto_play_video_summary),
            isChecked = autoPlayVideo,
            onCheck = onAutoPlayChange,
            onClick = { onDetailClick(DETAIL_AUTO_PLAY) },
            screenPosition = Position.Middle
        )

        val videoSurfaceRebindPref = rememberSwitchPreference(
            videoSurfaceRebind,
            title = stringResource(Res.string.video_surface_rebind),
            icon = Icons.Outlined.Videocam,
            summary = stringResource(Res.string.video_surface_rebind_summary),
            isChecked = videoSurfaceRebind,
            onCheck = onSurfaceRebindChange,
            onClick = { onDetailClick(DETAIL_SURFACE_REBIND) },
            screenPosition = Position.Bottom
        )

        val hdrVideoPlaybackPref = rememberSwitchPreference(
            hdrVideoPlayback, state.hdrDisplayCapable,
            title = stringResource(Res.string.hdr_video_playback),
            icon = Icons.Outlined.HdrOn,
            summary = stringResource(Res.string.hdr_video_playback_summary),
            isChecked = hdrVideoPlayback,
            onCheck = onHdrVideoPlaybackChange,
            onClick = { onDetailClick(DETAIL_HDR_VIDEO) },
            enabled = state.hdrDisplayCapable,
            screenPosition = Position.Middle
        )

        return remember(
            viewingHeader, fullBrightnessViewPref, showMediaDateHeaderPref, tapNavigationPref,
            showFavoriteButtonPref, viewerActionBarPref, defaultEditorPref, disableSmoothingPref,
            darkMediaViewerPref,
            longPressActionPref,
            visualSearchPref, slideshowPref,
            saveQualityHeader, manualQualityPref, lossyQualityPref, jxlEffortPref, isManualQuality,
            videoPlaybackHeader, autoHideOnVideoPlayPref, autoPlayVideoPref, videoSurfaceRebindPref,
            hdrVideoPlaybackPref
        ) {
            mutableStateListOf<SettingsEntity>().apply {
                add(viewingHeader)
                add(fullBrightnessViewPref)
                add(showMediaDateHeaderPref)
                add(tapNavigationPref)
                if (state.supportsFavorites) {
                    add(showFavoriteButtonPref)
                }
                add(viewerActionBarPref)
                add(defaultEditorPref)
                add(defaultVideoEditorPref)
                add(disableSmoothingPref)
                add(darkMediaViewerPref)
                add(longPressActionPref)
                add(visualSearchPref)
                add(slideshowPref)

                add(saveQualityHeader)
                add(manualQualityPref)
                if (isManualQuality) {
                    add(lossyQualityPref)
                    add(jxlEffortPref)
                }

                add(videoPlaybackHeader)
                add(autoHideOnVideoPlayPref)
                add(autoPlayVideoPref)
                add(hdrVideoPlaybackPref)
                add(videoSurfaceRebindPref)
            }
        }
    }

    BaseSettingsScreen(
        title = stringResource(Res.string.settings_media_viewer),
        settingsList = settings(),
        listState = listState,
        searchRoute = searchRoute,
        searchSink = searchSink,
    )
}

@Composable
private fun longPressActionLabels(): List<Pair<String, String>> = listOf(
    LONG_PRESS_ACTION_ROTATE to stringResource(Res.string.long_press_action_rotate),
    LONG_PRESS_ACTION_CUTOUT to stringResource(Res.string.long_press_action_cutout),
    LONG_PRESS_ACTION_NONE to stringResource(Res.string.long_press_action_none),
)

@Composable
fun FullBrightnessPreview(isChecked: Boolean) {
    // The viewer on black: dimmed at the phone's own brightness, full with the setting on.
    Box(
        modifier = Modifier
            .size(width = 104.dp, height = 184.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(Color.Black)
    ) {
        PreviewPhoto(
            index = 0,
            modifier = Modifier
                .align(Alignment.Center)
                .fillMaxWidth()
                .height(68.dp)
        )
        if (!isChecked) {
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.55f)))
        }
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 12.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = if (isChecked) 0.6f else 0.25f))
                .padding(horizontal = 12.dp, vertical = 4.dp),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Outlined.BrightnessHigh,
                contentDescription = null,
                tint = Color.Black.copy(alpha = if (isChecked) 0.8f else 0.5f),
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

@Composable
fun VisualSearchSettingsContent(
    targets: List<VisualSearchTarget>,
    providerIcon: (@Composable (packageName: String) -> Unit)?,
    provider: String,
    onProviderChange: (String) -> Unit,
    position: String,
    onPositionChange: (String) -> Unit,
    allowVault: Boolean,
    onAllowVaultChange: (Boolean) -> Unit,
    convertMode: String,
    onConvertModeChange: (String) -> Unit,
    convertFormat: String,
    onConvertFormatChange: (String) -> Unit,
) {
    var showProviderSheet by rememberSaveable { mutableStateOf(false) }
    val resolvedProvider = resolveVisualSearchTarget(targets, provider)

    Column(modifier = Modifier.fillMaxWidth()) {
        SettingsItem(
            item = SettingsEntity.Header(
                title = stringResource(Res.string.visual_search_position_header)
            )
        )
        VisualSearchSectionExplanation(Res.string.visual_search_position_summary)
        listOf(
            Triple(
                VISUAL_SEARCH_POSITION_TOP,
                Res.string.visual_search_position_top,
                Res.string.visual_search_position_top_summary,
            ),
            Triple(
                VISUAL_SEARCH_POSITION_BOTTOM,
                Res.string.visual_search_position_bottom,
                Res.string.visual_search_position_bottom_summary,
            ),
        ).forEachIndexed { index, (value, labelRes, summaryRes) ->
            SettingsItem(
                item = SettingsEntity.Preference(
                    title = stringResource(labelRes),
                    summary = stringResource(summaryRes),
                    onClick = { onPositionChange(value) },
                    screenPosition = if (index == 0) Position.Top else Position.Bottom,
                ),
                customTrailingContent = {
                    RadioButton(
                        selected = position == value,
                        onClick = { onPositionChange(value) },
                    )
                },
            )
        }

        Spacer(Modifier.height(16.dp))

        SettingsItem(
            item = SettingsEntity.Header(
                title = stringResource(Res.string.visual_search_provider_header)
            )
        )
        VisualSearchSectionExplanation(Res.string.visual_search_provider_summary)
        if (targets.isEmpty()) {
            Text(
                text = stringResource(Res.string.visual_search_none_installed),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 24.dp),
            )
        } else {
            val iconSlot: (@Composable (androidx.compose.ui.graphics.vector.ImageVector?, String?, Int?) -> Unit)? =
                resolvedProvider?.let { target ->
                    providerIcon?.let { icon ->
                        { _, _, _ ->
                            Box(modifier = Modifier.size(24.dp), contentAlignment = Alignment.Center) {
                                icon(target.packageName)
                            }
                        }
                    }
                }
            SettingsItem(
                item = SettingsEntity.Preference(
                    title = stringResource(Res.string.visual_search_provider_title),
                    icon = Icons.Outlined.ImageSearch,
                    summary = resolvedProvider?.displayName
                        ?: stringResource(Res.string.visual_search_provider_auto),
                    onClick = { showProviderSheet = true },
                    screenPosition = Position.Alone,
                ),
                customIcon = iconSlot,
            )
        }

        Spacer(Modifier.height(16.dp))

        SettingsItem(
            item = SettingsEntity.SwitchPreference(
                title = stringResource(Res.string.visual_search_vault_title),
                icon = Icons.Outlined.Lock,
                summary = stringResource(Res.string.visual_search_vault_summary),
                isChecked = allowVault,
                onCheck = onAllowVaultChange,
                screenPosition = Position.Alone,
            )
        )

        Spacer(Modifier.height(16.dp))

        SettingsItem(
            item = SettingsEntity.Header(
                title = stringResource(Res.string.visual_search_convert_header)
            )
        )
        VisualSearchSectionExplanation(Res.string.visual_search_convert_summary)
        listOf(
            Triple(
                VISUAL_SEARCH_CONVERT_ASK,
                Res.string.visual_search_convert_ask,
                Res.string.visual_search_convert_ask_summary,
            ),
            Triple(
                VISUAL_SEARCH_CONVERT_ALWAYS,
                Res.string.visual_search_convert_always,
                Res.string.visual_search_convert_always_summary,
            ),
            Triple(
                VISUAL_SEARCH_CONVERT_NEVER,
                Res.string.visual_search_convert_never,
                Res.string.visual_search_convert_never_summary,
            ),
        ).forEachIndexed { index, (value, labelRes, summaryRes) ->
            SettingsItem(
                item = SettingsEntity.Preference(
                    title = stringResource(labelRes),
                    summary = stringResource(summaryRes),
                    onClick = { onConvertModeChange(value) },
                    screenPosition = when (index) {
                        0 -> Position.Top
                        2 -> Position.Bottom
                        else -> Position.Middle
                    },
                ),
                customTrailingContent = {
                    RadioButton(
                        selected = convertMode == value,
                        onClick = { onConvertModeChange(value) },
                    )
                },
            )
        }

        SettingsItem(
            item = SettingsEntity.Header(
                title = stringResource(Res.string.visual_search_convert_format_header)
            )
        )
        VisualSearchSectionExplanation(Res.string.visual_search_convert_format_summary)
        val formats = listOf(
            Triple(
                VISUAL_SEARCH_FORMAT_JPEG,
                "JPEG",
                Res.string.visual_search_format_jpeg_summary,
            ),
            Triple(
                VISUAL_SEARCH_FORMAT_PNG,
                "PNG",
                Res.string.visual_search_format_png_summary,
            ),
            Triple(
                VISUAL_SEARCH_FORMAT_WEBP,
                "WebP",
                Res.string.visual_search_format_webp_summary,
            ),
        )
        val formatEnabled = convertMode != VISUAL_SEARCH_CONVERT_NEVER
        formats.forEachIndexed { index, (value, label, summaryRes) ->
            SettingsItem(
                item = SettingsEntity.Preference(
                    title = label,
                    summary = stringResource(summaryRes),
                    enabled = formatEnabled,
                    onClick = { onConvertFormatChange(value) },
                    screenPosition = when (index) {
                        0 -> Position.Top
                        formats.lastIndex -> Position.Bottom
                        else -> Position.Middle
                    },
                ),
                customTrailingContent = {
                    RadioButton(
                        selected = convertFormat == value,
                        onClick = { onConvertFormatChange(value) },
                        enabled = formatEnabled,
                    )
                },
            )
        }
    }

    if (showProviderSheet) {
        VisualSearchProviderSheet(
            targets = targets,
            providerIcon = providerIcon,
            provider = provider,
            onSelect = {
                onProviderChange(it)
                showProviderSheet = false
            },
            onDismiss = { showProviderSheet = false },
        )
    }
}

@Composable
private fun VisualSearchSectionExplanation(textRes: StringResource) {
    Text(
        text = stringResource(textRes),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .padding(horizontal = 24.dp)
            .padding(bottom = 8.dp),
    )
}

/**
 * Bottom sheet listing every installed visual-search provider with its launcher icon, plus the
 * Automatic option that prefers well-known providers. Mirrors the option rows used by other
 * picker sheets in settings.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun VisualSearchProviderSheet(
    targets: List<VisualSearchTarget>,
    providerIcon: (@Composable (packageName: String) -> Unit)?,
    provider: String,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(),
        dragHandle = { DragHandle() },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp)
                .padding(bottom = 16.dp),
        ) {
            Text(
                text = stringResource(Res.string.visual_search_provider_title),
                style = MaterialTheme.typography.titleLarge,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                text = stringResource(Res.string.visual_search_provider_summary),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp, bottom = 16.dp),
            )
            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item(key = "auto") {
                    ProviderSheetRow(
                        title = stringResource(Res.string.visual_search_provider_auto),
                        summary = stringResource(Res.string.visual_search_provider_auto_summary),
                        selected = provider == VISUAL_SEARCH_PROVIDER_AUTO,
                        onClick = { onSelect(VISUAL_SEARCH_PROVIDER_AUTO) },
                    ) {
                        Icon(
                            imageVector = GalleryIcons.VisualSearch,
                            contentDescription = null,
                            modifier = Modifier.size(28.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                items(
                    items = targets,
                    key = { it.packageName },
                ) { target ->
                    ProviderSheetRow(
                        title = target.displayName,
                        summary = null,
                        selected = provider == target.packageName,
                        onClick = { onSelect(target.packageName) },
                    ) {
                        if (providerIcon != null) {
                            providerIcon(target.packageName)
                        } else {
                            Icon(
                                imageVector = GalleryIcons.VisualSearch,
                                contentDescription = null,
                                modifier = Modifier.size(28.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ProviderSheetRow(
    title: String,
    summary: String?,
    selected: Boolean,
    onClick: () -> Unit,
    icon: @Composable () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(
                if (selected) MaterialTheme.colorScheme.secondaryContainer
                else MaterialTheme.colorScheme.surfaceContainerHigh
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Box(modifier = Modifier.size(28.dp), contentAlignment = Alignment.Center) {
            icon()
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (summary != null) {
                Text(
                    text = summary,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        RadioButton(selected = selected, onClick = onClick)
    }
}
