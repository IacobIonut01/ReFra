/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.feature_node.presentation.settings.subsettings

import android.graphics.Bitmap
import androidx.activity.compose.BackHandler
import androidx.appcompat.content.res.AppCompatResources
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import com.dot.gallery.feature_node.presentation.util.LocalHazeState
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.materials.ExperimentalHazeMaterialsApi
import dev.chrisbanes.haze.materials.HazeMaterials
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.composeunstyled.LocalTextStyle
import com.dot.gallery.R
import com.dot.gallery.core.Constants
import com.dot.gallery.core.Position
import com.dot.gallery.core.Settings
import com.dot.gallery.core.Settings.Misc.rememberAllowBlur
import com.dot.gallery.core.Settings.Misc.rememberDateHeaderFormat
import com.dot.gallery.core.Settings.Misc.rememberAutoHideOnVideoPlay
import com.dot.gallery.core.Settings.Misc.rememberDefaultImageEditor
import com.dot.gallery.core.Settings.Misc.rememberDisableSmoothing
import com.dot.gallery.core.Settings.Misc.rememberLongPressCutout
import com.dot.gallery.core.Settings.Misc.rememberReencodeJxlEffort
import com.dot.gallery.core.Settings.Misc.rememberReencodeLossyQuality
import com.dot.gallery.core.Settings.Misc.rememberReencodeQualityMode
import com.dot.gallery.core.Settings.Misc.rememberFullBrightnessView
import com.dot.gallery.core.Settings.Misc.rememberShowFavoriteButton
import com.dot.gallery.core.Settings.Misc.rememberShowMediaViewDateHeader
import com.dot.gallery.core.Settings.Misc.rememberTapSidesToNavigate
import com.dot.gallery.core.Settings.Misc.rememberVideoAutoplay
import com.dot.gallery.core.Settings.Misc.rememberVideoSurfaceRebind
import com.dot.gallery.core.Settings.Misc.rememberVisualSearchAllowVault
import com.dot.gallery.core.Settings.Misc.rememberVisualSearchConvertFormat
import com.dot.gallery.core.Settings.Misc.rememberVisualSearchConvertMode
import com.dot.gallery.core.Settings.Misc.rememberVisualSearchEnabled
import com.dot.gallery.core.Settings.Misc.rememberVisualSearchPosition
import com.dot.gallery.core.Settings.Misc.rememberVisualSearchProvider
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import com.dot.gallery.core.LocalEventHandler
import com.dot.gallery.core.navigate
import com.dot.gallery.core.SettingsEntity
import com.dot.gallery.core.presentation.components.DragHandle
import com.dot.gallery.core.util.SdkCompat
import com.dot.gallery.feature_node.presentation.mediaview.TapNavigationPreview
import com.dot.gallery.feature_node.presentation.mediaview.VisualSearchTarget
import com.dot.gallery.feature_node.presentation.mediaview.discoverVisualSearchTargets
import com.dot.gallery.feature_node.presentation.mediaview.icon
import com.dot.gallery.feature_node.presentation.mediaview.resolveVisualSearchTarget
import com.dot.gallery.ui.core.Icons as GalleryIcons
import com.dot.gallery.ui.core.icons.VisualSearch
import com.dot.gallery.feature_node.presentation.util.Screen
import com.dot.gallery.feature_node.presentation.settings.components.BaseSettingsScreen
import com.dot.gallery.feature_node.presentation.settings.components.ChooserPreferenceDetailScreen
import com.dot.gallery.feature_node.presentation.settings.components.PreferenceOption
import com.dot.gallery.feature_node.presentation.settings.components.SettingsItem
import com.dot.gallery.feature_node.presentation.settings.components.SwitchPreferenceDetailScreen
import com.dot.gallery.feature_node.presentation.settings.components.rememberPreference
import com.dot.gallery.feature_node.presentation.settings.components.rememberSwitchPreference
import com.dot.gallery.feature_node.presentation.util.getDate
import com.dot.gallery.feature_node.presentation.util.getEditImageCapableApps
import kotlin.math.roundToInt
import com.google.accompanist.drawablepainter.rememberDrawablePainter
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.launch

private const val DETAIL_BRIGHTNESS = "brightness"
private const val DETAIL_DATE_HEADER = "date_header"
private const val DETAIL_TAP_NAVIGATION = "tap_navigation"
private const val DETAIL_FAV_BUTTON = "fav_button"
private const val DETAIL_EDITOR = "editor"
private const val DETAIL_AUTO_HIDE_VIDEO = "auto_hide_video"
private const val DETAIL_AUTO_PLAY = "auto_play"
private const val DETAIL_SURFACE_REBIND = "surface_rebind"
private const val DETAIL_DISABLE_SMOOTHING = "disable_smoothing"
private const val DETAIL_LONG_PRESS_CUTOUT = "long_press_cutout"
private const val DETAIL_VISUAL_SEARCH = "visual_search"

@Composable
fun SettingsMediaViewerScreen() {
    var detailKey by rememberSaveable { mutableStateOf<String?>(null) }
    val listState = rememberLazyListState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var fullBrightnessView by rememberFullBrightnessView()
    var showMediaDateHeader by rememberShowMediaViewDateHeader()
    var tapSidesToNavigate by rememberTapSidesToNavigate()
    var showFavoriteButton by rememberShowFavoriteButton()
    var defaultEditor by rememberDefaultImageEditor()
    var autoHideOnVideoPlay by rememberAutoHideOnVideoPlay()
    var autoPlayVideo by rememberVideoAutoplay()
    var videoSurfaceRebind by rememberVideoSurfaceRebind()
    var disableSmoothing by rememberDisableSmoothing()
    var longPressCutout by rememberLongPressCutout()
    var reencodeMode by rememberReencodeQualityMode()
    var reencodeLossyQuality by rememberReencodeLossyQuality()
    var reencodeJxlEffort by rememberReencodeJxlEffort()
    var visualSearchEnabled by rememberVisualSearchEnabled()
    var visualSearchProvider by rememberVisualSearchProvider()
    var visualSearchPosition by rememberVisualSearchPosition()
    var visualSearchAllowVault by rememberVisualSearchAllowVault()
    var visualSearchConvertMode by rememberVisualSearchConvertMode()
    var visualSearchConvertFormat by rememberVisualSearchConvertFormat()
    val visualSearchTargets = remember(context) { context.discoverVisualSearchTargets() }
    val onTapNavigationChange: (Boolean) -> Unit = { enabled ->
        tapSidesToNavigate = enabled
        scope.launch { Settings.Misc.markTapSidesToNavigatePromptShown(context) }
    }

    val editApps = remember(context, context::getEditImageCapableApps)

    when (detailKey) {
        DETAIL_BRIGHTNESS -> {
            BackHandler { detailKey = null }
            SwitchPreferenceDetailScreen(
                title = stringResource(R.string.full_brightness_view_title),
                isChecked = fullBrightnessView,
                onCheckedChange = { fullBrightnessView = it },
                description = stringResource(R.string.full_brightness_view_description),
                preview = { checked -> FullBrightnessPreview(checked) },
            )
        }
        DETAIL_DATE_HEADER -> {
            BackHandler { detailKey = null }
            SwitchPreferenceDetailScreen(
                title = stringResource(R.string.show_date_header),
                isChecked = showMediaDateHeader,
                onCheckedChange = { showMediaDateHeader = it },
                description = stringResource(R.string.show_date_header_description),
                preview = { checked -> DateHeaderPreview(checked) },
                useColumnLayout = true,
            )
        }
        DETAIL_TAP_NAVIGATION -> {
            BackHandler { detailKey = null }
            SwitchPreferenceDetailScreen(
                title = stringResource(R.string.tap_sides_to_navigate_title),
                isChecked = tapSidesToNavigate,
                onCheckedChange = onTapNavigationChange,
                description = stringResource(R.string.tap_sides_to_navigate_description),
                preview = { checked -> TapNavigationPreview(enabled = checked) },
                useColumnLayout = true,
                previewBelowSwitch = true,
            )
        }
        DETAIL_FAV_BUTTON -> {
            BackHandler { detailKey = null }
            SwitchPreferenceDetailScreen(
                title = stringResource(R.string.show_favorite_button),
                isChecked = showFavoriteButton,
                onCheckedChange = { showFavoriteButton = it },
                description = stringResource(R.string.show_favorite_button_description),
                preview = { checked -> FavoriteButtonPreview(checked) },
                useColumnLayout = true,
            )
        }
        DETAIL_EDITOR -> {
            BackHandler { detailKey = null }
            val builtinEditorLabel = stringResource(R.string.default_image_editor_builtin)
            val editorOptions = remember(defaultEditor, editApps, builtinEditorLabel, context) {
                val options = mutableListOf(
                    PreferenceOption(Settings.Misc.EDITOR_BUILTIN, builtinEditorLabel, defaultEditor == Settings.Misc.EDITOR_BUILTIN)
                )
                editApps.forEach { app ->
                    val pkg = app.activityInfo.packageName
                    val label = app.loadLabel(context.packageManager).toString()
                    options.add(PreferenceOption(pkg, label, defaultEditor == pkg))
                }
                options.toList()
            }
            ChooserPreferenceDetailScreen(
                title = stringResource(R.string.default_image_editor),
                description = stringResource(R.string.default_editor_description),
                preview = { EditorPreview(defaultEditor, editApps) },
                options = editorOptions,
                onOptionSelected = { defaultEditor = it },
            )
        }
        DETAIL_DISABLE_SMOOTHING -> {
            BackHandler { detailKey = null }
            SwitchPreferenceDetailScreen(
                title = stringResource(R.string.disable_smoothing_title),
                isChecked = disableSmoothing,
                onCheckedChange = { disableSmoothing = it },
                description = stringResource(R.string.disable_smoothing_description),
                preview = { checked -> SmoothingPreview(disableSmoothing = checked) },
            )
        }
        DETAIL_LONG_PRESS_CUTOUT -> {
            BackHandler { detailKey = null }
            SwitchPreferenceDetailScreen(
                title = stringResource(R.string.long_press_cutout_title),
                isChecked = longPressCutout,
                onCheckedChange = { longPressCutout = it },
                description = stringResource(R.string.long_press_cutout_description),
            )
        }
        DETAIL_AUTO_HIDE_VIDEO -> {
            BackHandler { detailKey = null }
            SwitchPreferenceDetailScreen(
                title = stringResource(R.string.auto_hide_on_video_play),
                isChecked = autoHideOnVideoPlay,
                onCheckedChange = { autoHideOnVideoPlay = it },
                description = stringResource(R.string.auto_hide_on_video_play_description),
            )
        }
        DETAIL_AUTO_PLAY -> {
            BackHandler { detailKey = null }
            SwitchPreferenceDetailScreen(
                title = stringResource(R.string.auto_play_video),
                isChecked = autoPlayVideo,
                onCheckedChange = { autoPlayVideo = it },
                description = stringResource(R.string.auto_play_video_description),
            )
        }
        DETAIL_SURFACE_REBIND -> {
            BackHandler { detailKey = null }
            SwitchPreferenceDetailScreen(
                title = stringResource(R.string.video_surface_rebind),
                isChecked = videoSurfaceRebind,
                onCheckedChange = { videoSurfaceRebind = it },
                description = stringResource(R.string.video_surface_rebind_description),
            )
        }
        DETAIL_VISUAL_SEARCH -> {
            BackHandler { detailKey = null }
            SwitchPreferenceDetailScreen(
                title = stringResource(R.string.visual_search_title),
                isChecked = visualSearchEnabled,
                onCheckedChange = { visualSearchEnabled = it },
                description = stringResource(R.string.visual_search_description),
                preview = { checked ->
                    VisualSearchPreview(
                        enabled = checked,
                        icon = resolveVisualSearchTarget(
                            visualSearchTargets,
                            visualSearchProvider
                        )?.icon ?: GalleryIcons.VisualSearch,
                        position = visualSearchPosition,
                    )
                },
                customContent = {
                    VisualSearchSettingsContent(
                        targets = visualSearchTargets,
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
            MediaViewerListScreen(
                fullBrightnessView = fullBrightnessView,
                onBrightnessChange = { fullBrightnessView = it },
                showMediaDateHeader = showMediaDateHeader,
                onDateHeaderChange = { showMediaDateHeader = it },
                tapSidesToNavigate = tapSidesToNavigate,
                onTapNavigationChange = onTapNavigationChange,
                showFavoriteButton = showFavoriteButton,
                onFavButtonChange = { showFavoriteButton = it },
                defaultEditor = defaultEditor,
                editApps = editApps,
                disableSmoothing = disableSmoothing,
                onDisableSmoothingChange = { disableSmoothing = it },
                longPressCutout = longPressCutout,
                onLongPressCutoutChange = { longPressCutout = it },
                autoHideOnVideoPlay = autoHideOnVideoPlay,
                onAutoHideChange = { autoHideOnVideoPlay = it },
                autoPlayVideo = autoPlayVideo,
                onAutoPlayChange = { autoPlayVideo = it },
                videoSurfaceRebind = videoSurfaceRebind,
                onSurfaceRebindChange = { videoSurfaceRebind = it },
                reencodeMode = reencodeMode,
                onReencodeModeChange = { reencodeMode = it },
                reencodeLossyQuality = reencodeLossyQuality,
                onReencodeLossyChange = { reencodeLossyQuality = it },
                reencodeJxlEffort = reencodeJxlEffort,
                onReencodeJxlEffortChange = { reencodeJxlEffort = it },
                visualSearchEnabled = visualSearchEnabled,
                onVisualSearchChange = { visualSearchEnabled = it },
                visualSearchSummary = when {
                    visualSearchTargets.isEmpty() ->
                        stringResource(R.string.visual_search_none_installed)
                    !visualSearchEnabled -> stringResource(R.string.visual_search_summary)
                    else -> resolveVisualSearchTarget(visualSearchTargets, visualSearchProvider)
                        ?.let { stringResource(R.string.visual_search_with, it.displayName) }
                        ?: stringResource(R.string.visual_search_summary)
                },
                onDetailClick = { detailKey = it },
                listState = listState,
            )
        }
    }
}

@Composable
private fun MediaViewerListScreen(
    fullBrightnessView: Boolean,
    onBrightnessChange: (Boolean) -> Unit,
    showMediaDateHeader: Boolean,
    onDateHeaderChange: (Boolean) -> Unit,
    tapSidesToNavigate: Boolean,
    onTapNavigationChange: (Boolean) -> Unit,
    showFavoriteButton: Boolean,
    onFavButtonChange: (Boolean) -> Unit,
    defaultEditor: String,
    editApps: List<android.content.pm.ResolveInfo>,
    disableSmoothing: Boolean,
    onDisableSmoothingChange: (Boolean) -> Unit,
    longPressCutout: Boolean,
    onLongPressCutoutChange: (Boolean) -> Unit,
    autoHideOnVideoPlay: Boolean,
    onAutoHideChange: (Boolean) -> Unit,
    autoPlayVideo: Boolean,
    onAutoPlayChange: (Boolean) -> Unit,
    videoSurfaceRebind: Boolean,
    onSurfaceRebindChange: (Boolean) -> Unit,
    reencodeMode: String,
    onReencodeModeChange: (String) -> Unit,
    reencodeLossyQuality: Int,
    onReencodeLossyChange: (Int) -> Unit,
    reencodeJxlEffort: Int,
    onReencodeJxlEffortChange: (Int) -> Unit,
    visualSearchEnabled: Boolean,
    onVisualSearchChange: (Boolean) -> Unit,
    visualSearchSummary: String,
    onDetailClick: (String) -> Unit,
    listState: LazyListState,
) {
    @Composable
    fun settings(): SnapshotStateList<SettingsEntity> {
        val context = LocalContext.current
        val eventHandler = LocalEventHandler.current

        val viewingHeaderTitle = stringResource(R.string.media_view)
        val viewingHeader = remember(viewingHeaderTitle) {
            SettingsEntity.Header(title = viewingHeaderTitle)
        }

        val fullBrightnessViewPref = rememberSwitchPreference(
            fullBrightnessView,
            title = stringResource(R.string.full_brightness_view_title),
            summary = stringResource(R.string.full_brightness_view_summary),
            isChecked = fullBrightnessView,
            onCheck = onBrightnessChange,
            onClick = { onDetailClick(DETAIL_BRIGHTNESS) },
            screenPosition = Position.Top
        )

        val showMediaDateHeaderPref = rememberSwitchPreference(
            showMediaDateHeader,
            title = stringResource(R.string.show_date_header),
            summary = stringResource(R.string.show_date_header_summary),
            isChecked = showMediaDateHeader,
            onCheck = onDateHeaderChange,
            onClick = { onDetailClick(DETAIL_DATE_HEADER) },
            screenPosition = Position.Middle
        )

        val tapNavigationPref = rememberSwitchPreference(
            tapSidesToNavigate,
            title = stringResource(R.string.tap_sides_to_navigate_title),
            summary = stringResource(R.string.tap_sides_to_navigate_summary),
            isChecked = tapSidesToNavigate,
            onCheck = onTapNavigationChange,
            onClick = { onDetailClick(DETAIL_TAP_NAVIGATION) },
            screenPosition = Position.Middle,
        )

        val showFavoriteButtonPref = rememberSwitchPreference(
            showFavoriteButton,
            title = stringResource(R.string.show_favorite_button),
            summary = stringResource(R.string.show_favorite_button_summary),
            isChecked = showFavoriteButton,
            onCheck = onFavButtonChange,
            onClick = { onDetailClick(DETAIL_FAV_BUTTON) },
            screenPosition = Position.Middle
        )

        val builtinEditorLabel = stringResource(R.string.default_image_editor_builtin)
        val editorSummary = remember(defaultEditor, editApps, builtinEditorLabel, context) {
            if (defaultEditor == Settings.Misc.EDITOR_BUILTIN) {
                builtinEditorLabel
            } else {
                editApps.find { it.activityInfo.packageName == defaultEditor }
                    ?.loadLabel(context.packageManager)?.toString()
                    ?: builtinEditorLabel
            }
        }
        val defaultEditorPref = rememberPreference(
            defaultEditor,
            title = stringResource(R.string.default_image_editor),
            summary = editorSummary,
            onClick = { onDetailClick(DETAIL_EDITOR) },
            screenPosition = Position.Middle
        )

        val disableSmoothingPref = rememberSwitchPreference(
            disableSmoothing,
            title = stringResource(R.string.disable_smoothing_title),
            summary = stringResource(R.string.disable_smoothing_summary),
            isChecked = disableSmoothing,
            onCheck = onDisableSmoothingChange,
            onClick = { onDetailClick(DETAIL_DISABLE_SMOOTHING) },
            screenPosition = Position.Middle
        )

        val longPressCutoutPref = rememberSwitchPreference(
            longPressCutout,
            title = stringResource(R.string.long_press_cutout_title),
            summary = stringResource(R.string.long_press_cutout_summary),
            isChecked = longPressCutout,
            onCheck = onLongPressCutoutChange,
            onClick = { onDetailClick(DETAIL_LONG_PRESS_CUTOUT) },
            screenPosition = Position.Middle
        )

        val visualSearchPref = rememberSwitchPreference(
            visualSearchEnabled,
            title = stringResource(R.string.visual_search_title),
            summary = visualSearchSummary,
            isChecked = visualSearchEnabled,
            onCheck = onVisualSearchChange,
            onClick = { onDetailClick(DETAIL_VISUAL_SEARCH) },
            screenPosition = Position.Middle
        )

        val slideshowTitle = stringResource(R.string.slideshow)
        val slideshowSummary = stringResource(R.string.slideshow_settings_summary)
        val slideshowPref = remember(slideshowTitle, slideshowSummary, eventHandler) {
            SettingsEntity.Preference(
                title = slideshowTitle,
                summary = slideshowSummary,
                onClick = { eventHandler.navigate(Screen.SlideshowSettingsScreen()) },
                screenPosition = Position.Bottom
            )
        }

        // ── Save quality (format-preserving overwrite) ──
        val saveQualityHeaderTitle = stringResource(R.string.reencode_quality_title)
        val saveQualityHeader = remember(saveQualityHeaderTitle) {
            SettingsEntity.Header(title = saveQualityHeaderTitle)
        }
        val isManualQuality = reencodeMode == Settings.Misc.REENCODE_MODE_MANUAL
        val manualQualityPref = rememberSwitchPreference(
            reencodeMode,
            title = stringResource(R.string.reencode_quality_mode_manual),
            summary = stringResource(
                if (isManualQuality) R.string.reencode_quality_summary
                else R.string.reencode_quality_mode_auto_summary
            ),
            isChecked = isManualQuality,
            onCheck = { manual ->
                onReencodeModeChange(
                    if (manual) Settings.Misc.REENCODE_MODE_MANUAL
                    else Settings.Misc.REENCODE_MODE_AUTO
                )
            },
            screenPosition = if (isManualQuality) Position.Top else Position.Alone
        )
        val lossyQualityPref = SettingsEntity.SeekPreference(
            title = stringResource(R.string.reencode_quality_lossy),
            currentValue = reencodeLossyQuality.toFloat(),
            minValue = 1f,
            maxValue = 100f,
            step = 0,
            valueMultiplier = 1,
            onSeek = { onReencodeLossyChange(it.roundToInt().coerceIn(1, 100)) },
            screenPosition = Position.Middle
        )
        val jxlEffortPref = SettingsEntity.SeekPreference(
            title = stringResource(R.string.reencode_quality_jxl_effort),
            currentValue = reencodeJxlEffort.toFloat(),
            minValue = 1f,
            maxValue = 9f,
            step = 0,
            valueMultiplier = 1,
            onSeek = { onReencodeJxlEffortChange(it.roundToInt().coerceIn(1, 9)) },
            screenPosition = Position.Bottom
        )

        val videoPlaybackHeaderTitle = stringResource(R.string.video_playback)
        val videoPlaybackHeader = remember(videoPlaybackHeaderTitle) {
            SettingsEntity.Header(title = videoPlaybackHeaderTitle)
        }

        val autoHideOnVideoPlayPref = rememberSwitchPreference(
            autoHideOnVideoPlay,
            title = stringResource(R.string.auto_hide_on_video_play),
            summary = stringResource(R.string.auto_hide_on_video_play_summary),
            isChecked = autoHideOnVideoPlay,
            onCheck = onAutoHideChange,
            onClick = { onDetailClick(DETAIL_AUTO_HIDE_VIDEO) },
            screenPosition = Position.Top
        )

        val autoPlayVideoPref = rememberSwitchPreference(
            autoPlayVideo,
            title = stringResource(R.string.auto_play_video),
            summary = stringResource(R.string.auto_play_video_summary),
            isChecked = autoPlayVideo,
            onCheck = onAutoPlayChange,
            onClick = { onDetailClick(DETAIL_AUTO_PLAY) },
            screenPosition = Position.Middle
        )

        val videoSurfaceRebindPref = rememberSwitchPreference(
            videoSurfaceRebind,
            title = stringResource(R.string.video_surface_rebind),
            summary = stringResource(R.string.video_surface_rebind_summary),
            isChecked = videoSurfaceRebind,
            onCheck = onSurfaceRebindChange,
            onClick = { onDetailClick(DETAIL_SURFACE_REBIND) },
            screenPosition = Position.Bottom
        )

        return remember(
            viewingHeader, fullBrightnessViewPref, showMediaDateHeaderPref, tapNavigationPref,
            showFavoriteButtonPref, defaultEditorPref, disableSmoothingPref, longPressCutoutPref,
            visualSearchPref, slideshowPref,
            saveQualityHeader, manualQualityPref, lossyQualityPref, jxlEffortPref, isManualQuality,
            videoPlaybackHeader, autoHideOnVideoPlayPref, autoPlayVideoPref, videoSurfaceRebindPref
        ) {
            mutableStateListOf<SettingsEntity>().apply {
                add(viewingHeader)
                add(fullBrightnessViewPref)
                add(showMediaDateHeaderPref)
                add(tapNavigationPref)
                if (SdkCompat.supportsFavorites) {
                    add(showFavoriteButtonPref)
                }
                add(defaultEditorPref)
                add(disableSmoothingPref)
                add(longPressCutoutPref)
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
                add(videoSurfaceRebindPref)
            }
        }
    }

    BaseSettingsScreen(
        title = stringResource(R.string.settings_media_viewer),
        settingsList = settings(),
        listState = listState,
        searchRoute = Screen.SettingsMediaViewerScreen(),
    )
}

@Composable
private fun FullBrightnessPreview(isChecked: Boolean) {
    val bgBrightness = if (isChecked) 1f else 0.5f
    Box(
        modifier = Modifier
            .padding(24.dp)
            .size(width = 140.dp, height = 120.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color.White.copy(alpha = bgBrightness),
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = bgBrightness)
                    )
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        // Sun/brightness indicator
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(
                    Color.White.copy(alpha = if (isChecked) 0.9f else 0.3f)
                )
        )
    }
}

@Composable
@OptIn(ExperimentalHazeMaterialsApi::class)
private fun DateHeaderPreview(isChecked: Boolean) {
    val dateHeaderFormat by rememberDateHeaderFormat()
    val currentMillis = remember { System.currentTimeMillis() / 1000 }
    val textStyle = LocalTextStyle.current
    val allowBlur by rememberAllowBlur()
    val followTheme = remember(allowBlur) { !allowBlur }
    val contentColor by animateColorAsState(
        targetValue = if (followTheme) MaterialTheme.colorScheme.onSurface else Color.White,
        label = "contentColor"
    )
    val surfaceContainer = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.5f)
    val backgroundModifier = remember(surfaceContainer) {
        Modifier.background(color = surfaceContainer, shape = CircleShape)
    }

    val currentDate = remember(currentMillis, dateHeaderFormat, textStyle) {
        buildAnnotatedString {
            val date = currentMillis.getDate(dateHeaderFormat)
            if (date.isNotEmpty()) {
                val top = date.substringBefore("\n")
                val bottom = date.substringAfter("\n")
                withStyle(
                    style = textStyle.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    ).toSpanStyle()
                ) {
                    appendLine(top)
                }
                withStyle(
                    style = textStyle.copy(
                        fontWeight = FontWeight.Normal,
                        fontSize = 14.sp
                    ).toSpanStyle()
                ) {
                    append(bottom)
                }
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(128.dp)
            .padding(horizontal = 16.dp)
            .clip(RoundedCornerShape(12.dp))
    ) {
        Image(
            painter = painterResource(R.drawable.image_sample_2),
            contentDescription = null,
            modifier = Modifier
                .fillMaxSize()
                .blur(32.dp),
            contentScale = ContentScale.Crop
        )
        Spacer(
            modifier = Modifier
                .fillMaxSize()
                .background(color = Color.Black.copy(alpha = 0.1f))
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.Center)
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            IconButton(
                modifier = Modifier
                    .padding(horizontal = 8.dp)
                    .clip(CircleShape)
                    .then(backgroundModifier)
                    .hazeEffect(
                        state = LocalHazeState.current,
                        style = HazeMaterials.ultraThin(
                            containerColor = surfaceContainer
                        )
                    ),
                onClick = { }
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                    contentDescription = null,
                    tint = contentColor,
                    modifier = Modifier.height(48.dp)
                )
            }
            if (isChecked) {
                Text(
                    text = currentDate,
                    style = MaterialTheme.typography.titleSmall,
                    color = contentColor,
                    textAlign = TextAlign.Center
                )
            } else {
                Spacer(Modifier.weight(1f))
            }
            IconButton(
                modifier = Modifier
                    .padding(horizontal = 8.dp)
                    .clip(CircleShape)
                    .then(backgroundModifier)
                    .hazeEffect(
                        state = LocalHazeState.current,
                        style = HazeMaterials.ultraThin(
                            containerColor = surfaceContainer
                        )
                    ),
                onClick = { }
            ) {
                Icon(
                    imageVector = Icons.Outlined.Info,
                    contentDescription = null,
                    tint = contentColor,
                    modifier = Modifier.height(48.dp)
                )
            }
        }
    }
}

@Composable
private fun FavoriteButtonPreview(isChecked: Boolean) {
    val iconTint = MaterialTheme.colorScheme.onSurface
    val surfaceContainer = MaterialTheme.colorScheme.surfaceContainer

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(128.dp)
            .padding(horizontal = 16.dp)
            .clip(RoundedCornerShape(12.dp))
    ) {
        Image(
            painter = painterResource(R.drawable.image_sample_2),
            contentDescription = null,
            modifier = Modifier
                .fillMaxSize()
                .blur(8.dp),
            contentScale = ContentScale.Crop
        )
        Spacer(
            modifier = Modifier
                .fillMaxSize()
                .background(color = Color.Black.copy(alpha = 0.1f))
        )
        // Bottom floating action pill matching MediaViewQuickBottomBar
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 12.dp)
                .fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(100))
                    .background(surfaceContainer.copy(alpha = 0.85f))
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                // Share
                Box(Modifier.size(32.dp).clip(CircleShape), contentAlignment = Alignment.Center) {
                    Icon(Icons.Outlined.Share, null, Modifier.size(18.dp), tint = iconTint)
                }
                // Copy to Clipboard
                Box(Modifier.size(32.dp).clip(CircleShape), contentAlignment = Alignment.Center) {
                    Icon(Icons.Outlined.ContentCopy, null, Modifier.size(18.dp), tint = iconTint)
                }
                // Favorite (conditionally shown based on setting)
                if (isChecked) {
                    Box(Modifier.size(32.dp).clip(CircleShape), contentAlignment = Alignment.Center) {
                        Icon(Icons.Filled.Favorite, null, Modifier.size(18.dp), tint = iconTint)
                    }
                }
                // Edit
                Box(Modifier.size(32.dp).clip(CircleShape), contentAlignment = Alignment.Center) {
                    Icon(Icons.Outlined.Edit, null, Modifier.size(18.dp), tint = iconTint)
                }
                // Trash
                Box(Modifier.size(32.dp).clip(CircleShape), contentAlignment = Alignment.Center) {
                    Icon(Icons.Outlined.DeleteOutline, null, Modifier.size(18.dp), tint = iconTint)
                }
            }
        }
    }
}

@Composable
private fun EditorPreview(
    currentEditor: String,
    editApps: List<android.content.pm.ResolveInfo>
) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()

    // Auto-scroll to selected item when selection changes
    val allEditors = remember(editApps) {
        val list = mutableListOf(Settings.Misc.EDITOR_BUILTIN)
        editApps.forEach { list.add(it.activityInfo.packageName) }
        list
    }
    val selectedIndex = remember(currentEditor, allEditors) {
        allEditors.indexOf(currentEditor).coerceAtLeast(0)
    }
    LaunchedEffect(selectedIndex) {
        // Estimate scroll position: each card ~120dp + 12dp spacing
        val targetPx = (selectedIndex * 132 * context.resources.displayMetrics.density).toInt()
        scrollState.animateScrollTo(
            (targetPx - 100).coerceAtLeast(0)
        )
    }

    Box(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 24.dp)
                .horizontalScroll(scrollState)
                .padding(horizontal = 32.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            EditorOptionCard(
                label = stringResource(R.string.default_image_editor_builtin),
                selected = currentEditor == Settings.Misc.EDITOR_BUILTIN,
                icon = {
                    Image(
                        painter = rememberDrawablePainter(
                            drawable = AppCompatResources.getDrawable(context, R.mipmap.ic_launcher_round)
                        ),
                        contentDescription = stringResource(R.string.default_image_editor_builtin),
                        modifier = Modifier
                            .size(64.dp)
                            .clip(CircleShape)
                    )
                }
            )
            editApps.forEach { app ->
                val packageName = app.activityInfo.packageName
                val appLabel = remember(app) { app.loadLabel(context.packageManager).toString() }
                val appIcon = remember(app) {
                    try { app.loadIcon(context.packageManager).toBitmap().asImageBitmap() }
                    catch (_: Exception) { null }
                }
                if (appIcon != null) {
                    EditorOptionCard(
                        label = appLabel,
                        selected = currentEditor == packageName,
                        icon = {
                            Image(
                                bitmap = appIcon,
                                contentDescription = appLabel,
                                modifier = Modifier
                                    .size(64.dp)
                                    .clip(CircleShape)
                            )
                        }
                    )
                }
            }
        }
        // Soft fade-out gradient on left edge
        Box(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .size(32.dp, 160.dp)
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.surfaceContainerHigh,
                            Color.Transparent
                        )
                    )
                )
        )
        // Soft fade-out gradient on right edge
        Box(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .size(32.dp, 160.dp)
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(
                            Color.Transparent,
                            MaterialTheme.colorScheme.surfaceContainerHigh
                        )
                    )
                )
        )
    }
}

@Composable
private fun EditorOptionCard(
    label: String,
    selected: Boolean,
    icon: @Composable () -> Unit
) {
    val borderColor = if (selected) MaterialTheme.colorScheme.primary
    else MaterialTheme.colorScheme.outlineVariant
    val containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
    else Color.Transparent

    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .border(width = 2.dp, color = borderColor, shape = RoundedCornerShape(16.dp))
            .background(containerColor)
            .padding(horizontal = 24.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        icon()
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center
        )
        Icon(
            imageVector = Icons.Filled.CheckCircle,
            contentDescription = null,
            tint = if (selected) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.outlineVariant,
            modifier = Modifier.size(24.dp)
        )
    }
}

/**
 * Preview for the "Disable smoothing" setting. Draws a tiny pixel-art sample scaled up so the
 * difference between nearest-neighbor (crisp, [disableSmoothing] = true) and bilinear (smoothed,
 * [disableSmoothing] = false) filtering is clearly visible — the same effect applied in the viewer.
 */
@Composable
private fun SmoothingPreview(disableSmoothing: Boolean) {
    val sample = remember { createSmoothingSampleBitmap() }
    val filterQuality = if (disableSmoothing) FilterQuality.None else FilterQuality.Low
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .height(120.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surface),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawImage(
                image = sample,
                srcOffset = IntOffset.Zero,
                srcSize = IntSize(sample.width, sample.height),
                dstOffset = IntOffset.Zero,
                dstSize = IntSize(size.width.toInt(), size.height.toInt()),
                filterQuality = filterQuality
            )
        }
    }
}

/**
 * Builds a small (pixel-art) scene with a sun, sky gradient and two mountains. The curves and
 * diagonals make the smoothing/no-smoothing difference obvious when the bitmap is scaled up.
 */
private fun createSmoothingSampleBitmap(): ImageBitmap {
    val n = 20
    val bitmap = Bitmap.createBitmap(n, n, Bitmap.Config.ARGB_8888)

    val skyTop = 0xFF3A6EA5.toInt()
    val skyBottom = 0xFFBFE3F2.toInt()
    val sun = 0xFFFFD34E.toInt()
    val mountainBack = 0xFF6D8C5A.toInt()
    val mountainFront = 0xFF3F6034.toInt()

    val sunCx = n * 0.72f
    val sunCy = n * 0.30f
    val sunR = n * 0.16f

    for (y in 0 until n) {
        for (x in 0 until n) {
            var color = lerpArgb(skyTop, skyBottom, y / (n - 1f))

            val dx = x - sunCx
            val dy = y - sunCy
            if (dx * dx + dy * dy <= sunR * sunR) {
                color = sun
            }

            val backLine = n * 0.62f - (x - n * 0.2f) * 0.35f
            if (y >= backLine) color = mountainBack

            val frontLine = n * 0.95f - kotlin.math.abs(x - n * 0.55f) * 0.9f
            if (y >= frontLine) color = mountainFront

            bitmap.setPixel(x, y, color)
        }
    }
    return bitmap.asImageBitmap()
}

private fun lerpArgb(start: Int, end: Int, fraction: Float): Int {
    val f = fraction.coerceIn(0f, 1f)
    val a = ((start ushr 24 and 0xFF) + (((end ushr 24 and 0xFF) - (start ushr 24 and 0xFF)) * f)).toInt()
    val r = ((start ushr 16 and 0xFF) + (((end ushr 16 and 0xFF) - (start ushr 16 and 0xFF)) * f)).toInt()
    val g = ((start ushr 8 and 0xFF) + (((end ushr 8 and 0xFF) - (start ushr 8 and 0xFF)) * f)).toInt()
    val b = ((start and 0xFF) + (((end and 0xFF) - (start and 0xFF)) * f)).toInt()
    return (a shl 24) or (r shl 16) or (g shl 8) or b
}

/**
 * Mock of the viewer chrome showing where the visual-search button lands: the top-left stadium
 * next to Back when [position] is top, or inside the bottom quick-actions pill when bottom.
 */
@Composable
private fun VisualSearchPreview(enabled: Boolean, icon: ImageVector, position: String) {
    val iconTint = MaterialTheme.colorScheme.onSurface
    val surfaceContainer = MaterialTheme.colorScheme.surfaceContainer

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(128.dp)
            .padding(horizontal = 16.dp)
            .clip(RoundedCornerShape(12.dp))
    ) {
        Image(
            painter = painterResource(R.drawable.image_sample_2),
            contentDescription = null,
            modifier = Modifier
                .fillMaxSize()
                .blur(8.dp),
            contentScale = ContentScale.Crop
        )
        Spacer(
            modifier = Modifier
                .fillMaxSize()
                .background(color = Color.Black.copy(alpha = 0.1f))
        )
        if (enabled && position == Settings.Misc.VISUAL_SEARCH_POSITION_TOP) {
            Row(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(top = 12.dp, start = 12.dp)
                    .clip(RoundedCornerShape(100))
                    .background(surfaceContainer.copy(alpha = 0.85f))
                    .padding(horizontal = 6.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(32.dp), contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.AutoMirrored.Outlined.ArrowBack,
                        null,
                        Modifier.size(18.dp),
                        tint = iconTint
                    )
                }
                Box(
                    Modifier
                        .width(1.dp)
                        .height(18.dp)
                        .background(iconTint.copy(alpha = 0.2f))
                )
                Box(Modifier.size(32.dp), contentAlignment = Alignment.Center) {
                    Icon(icon, null, Modifier.size(18.dp), tint = iconTint)
                }
            }
        }
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 12.dp)
                .fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(100))
                    .background(surfaceContainer.copy(alpha = 0.85f))
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Box(Modifier.size(32.dp).clip(CircleShape), contentAlignment = Alignment.Center) {
                    Icon(Icons.Outlined.Share, null, Modifier.size(18.dp), tint = iconTint)
                }
                if (enabled && position == Settings.Misc.VISUAL_SEARCH_POSITION_BOTTOM) {
                    Box(Modifier.size(32.dp).clip(CircleShape), contentAlignment = Alignment.Center) {
                        Icon(icon, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                    }
                }
                Box(Modifier.size(32.dp).clip(CircleShape), contentAlignment = Alignment.Center) {
                    Icon(Icons.Outlined.ContentCopy, null, Modifier.size(18.dp), tint = iconTint)
                }
                Box(Modifier.size(32.dp).clip(CircleShape), contentAlignment = Alignment.Center) {
                    Icon(Icons.Outlined.Edit, null, Modifier.size(18.dp), tint = iconTint)
                }
                Box(Modifier.size(32.dp).clip(CircleShape), contentAlignment = Alignment.Center) {
                    Icon(Icons.Outlined.DeleteOutline, null, Modifier.size(18.dp), tint = iconTint)
                }
            }
        }
    }
}

/**
 * The extra sections on the visual-search detail screen: button position, the provider picker
 * (a single preference opening a bottom sheet with every installed app accepting image shares),
 * the vault toggle, and the exotic-format convert policy.
 */
@Composable
private fun VisualSearchSettingsContent(
    targets: List<VisualSearchTarget>,
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
    val context = LocalContext.current
    var showProviderSheet by rememberSaveable { mutableStateOf(false) }
    val resolvedProvider = resolveVisualSearchTarget(targets, provider)

    Column(modifier = Modifier.fillMaxWidth()) {
        SettingsItem(
            item = SettingsEntity.Header(
                title = stringResource(R.string.visual_search_position_header)
            )
        )
        VisualSearchSectionExplanation(R.string.visual_search_position_summary)
        listOf(
            Triple(
                Settings.Misc.VISUAL_SEARCH_POSITION_TOP,
                R.string.visual_search_position_top,
                R.string.visual_search_position_top_summary,
            ),
            Triple(
                Settings.Misc.VISUAL_SEARCH_POSITION_BOTTOM,
                R.string.visual_search_position_bottom,
                R.string.visual_search_position_bottom_summary,
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
                title = stringResource(R.string.visual_search_provider_header)
            )
        )
        VisualSearchSectionExplanation(R.string.visual_search_provider_summary)
        if (targets.isEmpty()) {
            Text(
                text = stringResource(R.string.visual_search_none_installed),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 24.dp),
            )
        } else {
            val providerIcon = remember(resolvedProvider?.packageName) {
                resolvedProvider?.let {
                    runCatching { context.packageManager.getApplicationIcon(it.packageName) }
                        .getOrNull()
                }
            }
            val iconSlot: (@Composable (ImageVector?, String?, Int?) -> Unit)? =
                if (providerIcon != null) {
                    { _, _, _ ->
                        Image(
                            painter = rememberDrawablePainter(providerIcon),
                            contentDescription = null,
                            modifier = Modifier.size(24.dp),
                        )
                    }
                } else null
            SettingsItem(
                item = SettingsEntity.Preference(
                    title = stringResource(R.string.visual_search_provider_title),
                    summary = resolvedProvider?.displayName
                        ?: stringResource(R.string.visual_search_provider_auto),
                    onClick = { showProviderSheet = true },
                    screenPosition = Position.Alone,
                ),
                customIcon = iconSlot,
            )
        }

        Spacer(Modifier.height(16.dp))

        SettingsItem(
            item = SettingsEntity.SwitchPreference(
                title = stringResource(R.string.visual_search_vault_title),
                summary = stringResource(R.string.visual_search_vault_summary),
                isChecked = allowVault,
                onCheck = onAllowVaultChange,
                screenPosition = Position.Alone,
            )
        )

        Spacer(Modifier.height(16.dp))

        SettingsItem(
            item = SettingsEntity.Header(
                title = stringResource(R.string.visual_search_convert_header)
            )
        )
        VisualSearchSectionExplanation(R.string.visual_search_convert_summary)
        listOf(
            Triple(
                Settings.Misc.VISUAL_SEARCH_CONVERT_ASK,
                R.string.visual_search_convert_ask,
                R.string.visual_search_convert_ask_summary,
            ),
            Triple(
                Settings.Misc.VISUAL_SEARCH_CONVERT_ALWAYS,
                R.string.visual_search_convert_always,
                R.string.visual_search_convert_always_summary,
            ),
            Triple(
                Settings.Misc.VISUAL_SEARCH_CONVERT_NEVER,
                R.string.visual_search_convert_never,
                R.string.visual_search_convert_never_summary,
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
                title = stringResource(R.string.visual_search_convert_format_header)
            )
        )
        VisualSearchSectionExplanation(R.string.visual_search_convert_format_summary)
        val formats = listOf(
            Triple(
                Settings.Misc.VISUAL_SEARCH_FORMAT_JPEG,
                "JPEG",
                R.string.visual_search_format_jpeg_summary,
            ),
            Triple(
                Settings.Misc.VISUAL_SEARCH_FORMAT_PNG,
                "PNG",
                R.string.visual_search_format_png_summary,
            ),
            Triple(
                Settings.Misc.VISUAL_SEARCH_FORMAT_WEBP,
                "WebP",
                R.string.visual_search_format_webp_summary,
            ),
        )
        val formatEnabled = convertMode != Settings.Misc.VISUAL_SEARCH_CONVERT_NEVER
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
private fun VisualSearchSectionExplanation(textRes: Int) {
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
    provider: String,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
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
                text = stringResource(R.string.visual_search_provider_title),
                style = MaterialTheme.typography.titleLarge,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                text = stringResource(R.string.visual_search_provider_summary),
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
                        title = stringResource(R.string.visual_search_provider_auto),
                        summary = stringResource(R.string.visual_search_provider_auto_summary),
                        selected = provider == Settings.Misc.VISUAL_SEARCH_PROVIDER_AUTO,
                        onClick = { onSelect(Settings.Misc.VISUAL_SEARCH_PROVIDER_AUTO) },
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
                    val appIcon = remember(target.packageName) {
                        runCatching {
                            context.packageManager.getApplicationIcon(target.packageName)
                        }.getOrNull()
                    }
                    ProviderSheetRow(
                        title = target.displayName,
                        summary = null,
                        selected = provider == target.packageName,
                        onClick = { onSelect(target.packageName) },
                    ) {
                        if (appIcon != null) {
                            Image(
                                painter = rememberDrawablePainter(appIcon),
                                contentDescription = null,
                                modifier = Modifier.size(28.dp),
                            )
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
