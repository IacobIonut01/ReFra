/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.feature.settings.ui

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.PanTool
import androidx.compose.material.icons.outlined.Swipe
import androidx.compose.material.icons.outlined.TouchApp
import androidx.compose.material.icons.outlined.ZoomIn
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
import com.dot.gallery.core.ui.components.BaseSettingsScreen
import com.dot.gallery.core.ui.components.ChooserPreferenceDetailScreen
import com.dot.gallery.core.ui.components.PreferenceOption
import com.dot.gallery.core.ui.components.SwitchPreferenceDetailScreen
import com.dot.gallery.core.ui.components.rememberPreference
import com.dot.gallery.core.ui.components.rememberSwitchPreference
import com.dot.gallery.core.ui.settings.rememberPreference
import com.dot.gallery.domain.settings.LONG_PRESS_ACTION_CUTOUT
import com.dot.gallery.domain.settings.LONG_PRESS_ACTION_NONE
import com.dot.gallery.domain.settings.LONG_PRESS_ACTION_ROTATE
import com.dot.gallery.domain.settings.SettingsKeys
import com.dot.gallery.domain.settings.resolveLongPressAction
import org.jetbrains.compose.resources.stringResource
import com.dot.gallery.feature.settings.generated.resources.Res
import com.dot.gallery.feature.settings.generated.resources.gestures_double_tap_zoom_description
import com.dot.gallery.feature.settings.generated.resources.gestures_double_tap_zoom_summary
import com.dot.gallery.feature.settings.generated.resources.gestures_double_tap_zoom_title
import com.dot.gallery.feature.settings.generated.resources.gestures_swipe_dismiss_summary
import com.dot.gallery.feature.settings.generated.resources.gestures_swipe_dismiss_title
import com.dot.gallery.feature.settings.generated.resources.gestures_swipe_hold_header
import com.dot.gallery.feature.settings.generated.resources.gestures_tap_header
import com.dot.gallery.feature.settings.generated.resources.instant_side_taps_summary
import com.dot.gallery.feature.settings.generated.resources.instant_side_taps_title
import com.dot.gallery.feature.settings.generated.resources.long_press_action_cutout
import com.dot.gallery.feature.settings.generated.resources.long_press_action_cutout_unavailable
import com.dot.gallery.feature.settings.generated.resources.long_press_action_description
import com.dot.gallery.feature.settings.generated.resources.long_press_action_none
import com.dot.gallery.feature.settings.generated.resources.long_press_action_rotate
import com.dot.gallery.feature.settings.generated.resources.long_press_action_title
import com.dot.gallery.feature.settings.generated.resources.settings_gestures
import com.dot.gallery.feature.settings.generated.resources.tap_sides_to_navigate_description
import com.dot.gallery.feature.settings.generated.resources.tap_sides_to_navigate_summary
import com.dot.gallery.feature.settings.generated.resources.tap_sides_to_navigate_title

private const val DETAIL_TAP_NAVIGATION = "tap_navigation"
private const val DETAIL_DOUBLE_TAP_ZOOM = "double_tap_zoom"
private const val DETAIL_LONG_PRESS_ACTION = "long_press_action"

/**
 * The Gestures settings page: every touch interaction the media viewer responds
 * to, grouped into taps and swipe-and-hold. Preferences are read and written
 * through [SettingsKeys] here, the same store the media-viewer page used before
 * the split.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun SettingsGesturesContent(
    state: SettingsGesturesState = SettingsGesturesState(),
    previews: GesturesPreviews = GesturesPreviews(),
    actions: SettingsGesturesActions = SettingsGesturesActions(),
) {
    var detailKey by rememberSaveable { mutableStateOf<String?>(null) }
    val listState = rememberLazyListState()

    var tapSidesToNavigate by rememberPreference(SettingsKeys.tapSidesToNavigate)
    var instantSideTaps by rememberPreference(SettingsKeys.instantSideTaps)
    var doubleTapZoom by rememberPreference(SettingsKeys.viewerDoubleTapZoom)
    var swipeToDismiss by rememberPreference(SettingsKeys.viewerSwipeToDismiss)
    val legacyLongPressCutout by rememberPreference(SettingsKeys.longPressCutout)
    var storedLongPressAction by rememberPreference(SettingsKeys.longPressAction)
    val longPressAction = resolveLongPressAction(storedLongPressAction, legacyLongPressCutout)
    val onTapNavigationChange: (Boolean) -> Unit = { enabled ->
        tapSidesToNavigate = enabled
        actions.onTapSidesChanged()
    }

    when (detailKey) {
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
        DETAIL_DOUBLE_TAP_ZOOM -> {
            BackHandler { detailKey = null }
            SwitchPreferenceDetailScreen(
                title = stringResource(Res.string.gestures_double_tap_zoom_title),
                isChecked = doubleTapZoom,
                onCheckedChange = { doubleTapZoom = it },
                description = stringResource(Res.string.gestures_double_tap_zoom_description),
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
        else -> {
            GesturesListContent(
                tapSidesToNavigate = tapSidesToNavigate,
                onTapNavigationChange = onTapNavigationChange,
                instantSideTaps = instantSideTaps,
                onInstantSideTapsChange = { instantSideTaps = it },
                doubleTapZoom = doubleTapZoom,
                onDoubleTapZoomChange = { doubleTapZoom = it },
                swipeToDismiss = swipeToDismiss,
                onSwipeToDismissChange = { swipeToDismiss = it },
                longPressAction = longPressAction,
                onDetailClick = { detailKey = it },
                searchRoute = actions.searchRoute,
                searchSink = actions.searchSink,
                listState = listState,
            )
        }
    }
}

@Composable
private fun GesturesListContent(
    tapSidesToNavigate: Boolean,
    onTapNavigationChange: (Boolean) -> Unit,
    instantSideTaps: Boolean,
    onInstantSideTapsChange: (Boolean) -> Unit,
    doubleTapZoom: Boolean,
    onDoubleTapZoomChange: (Boolean) -> Unit,
    swipeToDismiss: Boolean,
    onSwipeToDismissChange: (Boolean) -> Unit,
    longPressAction: String,
    onDetailClick: (String) -> Unit,
    searchRoute: String?,
    searchSink: ((String, List<String>) -> Unit)?,
    listState: LazyListState,
) {
    @Composable
    fun settings(): SnapshotStateList<SettingsEntity> {
        val tapHeaderTitle = stringResource(Res.string.gestures_tap_header)
        val tapHeader = remember(tapHeaderTitle) {
            SettingsEntity.Header(title = tapHeaderTitle)
        }

        val tapNavigationPref = rememberSwitchPreference(
            tapSidesToNavigate,
            title = stringResource(Res.string.tap_sides_to_navigate_title),
            icon = Icons.Outlined.TouchApp,
            summary = stringResource(Res.string.tap_sides_to_navigate_summary),
            isChecked = tapSidesToNavigate,
            onCheck = onTapNavigationChange,
            onClick = { onDetailClick(DETAIL_TAP_NAVIGATION) },
            screenPosition = Position.Top,
        )

        // Only meaningful while side taps navigate; off defers them so a double tap can zoom.
        val instantSideTapsPref = rememberSwitchPreference(
            instantSideTaps, tapSidesToNavigate,
            title = stringResource(Res.string.instant_side_taps_title),
            icon = Icons.Outlined.Bolt,
            summary = stringResource(Res.string.instant_side_taps_summary),
            isChecked = instantSideTaps,
            onCheck = onInstantSideTapsChange,
            enabled = tapSidesToNavigate,
            screenPosition = Position.Middle,
        )

        val doubleTapZoomPref = rememberSwitchPreference(
            doubleTapZoom,
            title = stringResource(Res.string.gestures_double_tap_zoom_title),
            icon = Icons.Outlined.ZoomIn,
            summary = stringResource(Res.string.gestures_double_tap_zoom_summary),
            isChecked = doubleTapZoom,
            onCheck = onDoubleTapZoomChange,
            onClick = { onDetailClick(DETAIL_DOUBLE_TAP_ZOOM) },
            screenPosition = Position.Bottom,
        )

        val swipeHoldHeaderTitle = stringResource(Res.string.gestures_swipe_hold_header)
        val swipeHoldHeader = remember(swipeHoldHeaderTitle) {
            SettingsEntity.Header(title = swipeHoldHeaderTitle)
        }

        val swipeToDismissPref = rememberSwitchPreference(
            swipeToDismiss,
            title = stringResource(Res.string.gestures_swipe_dismiss_title),
            icon = Icons.Outlined.Swipe,
            summary = stringResource(Res.string.gestures_swipe_dismiss_summary),
            isChecked = swipeToDismiss,
            onCheck = onSwipeToDismissChange,
            screenPosition = Position.Top,
        )

        val longPressActionSummary = longPressActionLabels()
            .firstOrNull { it.first == longPressAction }?.second.orEmpty()
        val longPressActionPref = rememberPreference(
            longPressAction,
            title = stringResource(Res.string.long_press_action_title),
            icon = Icons.Outlined.PanTool,
            summary = longPressActionSummary,
            onClick = { onDetailClick(DETAIL_LONG_PRESS_ACTION) },
            screenPosition = Position.Bottom,
        )

        return remember(
            tapHeader, tapNavigationPref, instantSideTapsPref, doubleTapZoomPref,
            swipeHoldHeader, swipeToDismissPref, longPressActionPref,
        ) {
            mutableStateListOf<SettingsEntity>().apply {
                add(tapHeader)
                add(tapNavigationPref)
                add(instantSideTapsPref)
                add(doubleTapZoomPref)
                add(swipeHoldHeader)
                add(swipeToDismissPref)
                add(longPressActionPref)
            }
        }
    }

    BaseSettingsScreen(
        title = stringResource(Res.string.settings_gestures),
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
