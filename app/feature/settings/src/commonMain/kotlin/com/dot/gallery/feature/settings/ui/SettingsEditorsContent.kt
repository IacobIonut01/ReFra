/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.feature.settings.ui

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.HighQuality
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.dot.gallery.core.Position
import com.dot.gallery.core.SettingsEntity
import com.dot.gallery.core.presentation.components.GalleryPageTopBar
import com.dot.gallery.core.presentation.components.rememberScrolledUnderTopBar
import com.dot.gallery.core.presentation.util.LocalHazeState
import com.dot.gallery.core.ui.components.PreviewIconBadge
import com.dot.gallery.core.ui.components.SettingsItem
import com.dot.gallery.core.ui.components.rememberSwitchPreference
import com.dot.gallery.core.ui.components.settingsContentWidth
import com.dot.gallery.core.ui.components.settingsFocusGroup
import com.dot.gallery.core.ui.settings.rememberPreference
import com.dot.gallery.domain.settings.EDITOR_BUILTIN
import com.dot.gallery.domain.settings.EDITOR_STUDIO
import com.dot.gallery.domain.settings.REENCODE_MODE_AUTO
import com.dot.gallery.domain.settings.REENCODE_MODE_MANUAL
import com.dot.gallery.domain.settings.SettingsKeys
import dev.chrisbanes.haze.hazeSource
import kotlin.math.roundToInt
import org.jetbrains.compose.resources.stringResource
import com.dot.gallery.feature.settings.generated.resources.Res
import com.dot.gallery.feature.settings.generated.resources.default_editor_description
import com.dot.gallery.feature.settings.generated.resources.default_image_editor_builtin
import com.dot.gallery.feature.settings.generated.resources.default_image_editor_studio
import com.dot.gallery.feature.settings.generated.resources.default_video_editor_ask
import com.dot.gallery.feature.settings.generated.resources.default_video_editor_description
import com.dot.gallery.feature.settings.generated.resources.default_video_editor_studio
import com.dot.gallery.feature.settings.generated.resources.editors_image_header
import com.dot.gallery.feature.settings.generated.resources.editors_video_header
import com.dot.gallery.feature.settings.generated.resources.reencode_quality_jxl_effort
import com.dot.gallery.feature.settings.generated.resources.reencode_quality_lossy
import com.dot.gallery.feature.settings.generated.resources.reencode_quality_mode_auto_summary
import com.dot.gallery.feature.settings.generated.resources.reencode_quality_mode_manual
import com.dot.gallery.feature.settings.generated.resources.reencode_quality_summary
import com.dot.gallery.feature.settings.generated.resources.reencode_quality_title
import com.dot.gallery.feature.settings.generated.resources.settings_editors

/**
 * The Editors settings page: which app the edit actions open for images and for
 * videos, plus how edited images are re-encoded on save. [appIcon] draws the
 * platform icon for a choice value (launcher art for the built-in sentinels,
 * package icons for external apps); without it — docs scenes — tiles fall back
 * to a generic badge. Preferences are read and written through [SettingsKeys]
 * here, the same store the media-viewer page used before the split.
 */
@Composable
fun SettingsEditorsContent(
    state: SettingsEditorsState = SettingsEditorsState(),
    appIcon: (@Composable (packageName: String) -> Unit)? = null,
    searchRoute: String? = null,
    searchSink: ((String, List<String>) -> Unit)? = null,
) {
    var defaultImageEditor by rememberPreference(SettingsKeys.defaultImageEditor)
    var imageEditorChoiceExplicit by rememberPreference(SettingsKeys.imageEditorChoiceExplicit)
    var defaultVideoEditor by rememberPreference(SettingsKeys.defaultVideoEditor)
    var reencodeMode by rememberPreference(SettingsKeys.reencodeQualityMode)
    var reencodeLossyQuality by rememberPreference(SettingsKeys.reencodeLossyQuality)
    var reencodeJxlEffort by rememberPreference(SettingsKeys.reencodeJxlEffort)
    val listState = rememberLazyListState()

    val builtinImageEditorLabel = stringResource(Res.string.default_image_editor_builtin)
    val studioImageEditorLabel = stringResource(Res.string.default_image_editor_studio)
    val imageEditorOptions = remember(state.editApps, builtinImageEditorLabel, studioImageEditorLabel) {
        buildList {
            add(EditorChoice(EDITOR_BUILTIN, builtinImageEditorLabel))
            add(EditorChoice(EDITOR_STUDIO, studioImageEditorLabel))
            state.editApps.forEach { add(EditorChoice(it.packageName, it.label)) }
        }
    }
    val studioVideoEditorLabel = stringResource(Res.string.default_video_editor_studio)
    val askVideoEditorLabel = stringResource(Res.string.default_video_editor_ask)
    val videoEditorOptions = remember(state.videoEditApps, studioVideoEditorLabel, askVideoEditorLabel) {
        buildList {
            add(EditorChoice(EDITOR_STUDIO, studioVideoEditorLabel))
            add(EditorChoice(EDITOR_BUILTIN, askVideoEditorLabel))
            state.videoEditApps.forEach { add(EditorChoice(it.packageName, it.label)) }
        }
    }

    val manualQualityTitle = stringResource(Res.string.reencode_quality_mode_manual)
    val lossyQualityTitle = stringResource(Res.string.reencode_quality_lossy)
    val jxlEffortTitle = stringResource(Res.string.reencode_quality_jxl_effort)
    val isManualQuality = reencodeMode == REENCODE_MODE_MANUAL

    if (searchRoute != null && searchSink != null) {
        val searchableTitles = remember(
            imageEditorOptions, videoEditorOptions, manualQualityTitle,
            lossyQualityTitle, jxlEffortTitle, isManualQuality,
        ) {
            buildList {
                addAll(imageEditorOptions.map(EditorChoice::label))
                addAll(videoEditorOptions.map(EditorChoice::label))
                add(manualQualityTitle)
                if (isManualQuality) {
                    add(lossyQualityTitle)
                    add(jxlEffortTitle)
                }
            }
        }
        LaunchedEffect(searchRoute, searchableTitles) {
            searchSink(searchRoute, searchableTitles)
        }
    }

    val saveQualityHeaderTitle = stringResource(Res.string.reencode_quality_title)
    val saveQualityHeader = remember(saveQualityHeaderTitle) {
        SettingsEntity.Header(title = saveQualityHeaderTitle)
    }
    val manualQualityPref = rememberSwitchPreference(
        reencodeMode,
        title = manualQualityTitle,
        icon = Icons.Outlined.Tune,
        summary = stringResource(
            if (isManualQuality) Res.string.reencode_quality_summary
            else Res.string.reencode_quality_mode_auto_summary
        ),
        isChecked = isManualQuality,
        onCheck = { manual ->
            reencodeMode = if (manual) REENCODE_MODE_MANUAL else REENCODE_MODE_AUTO
        },
        screenPosition = if (isManualQuality) Position.Top else Position.Alone
    )
    val lossyQualityPref = SettingsEntity.SeekPreference(
        title = lossyQualityTitle,
        icon = Icons.Outlined.HighQuality,
        currentValue = reencodeLossyQuality.toFloat(),
        minValue = 1f,
        maxValue = 100f,
        step = 0,
        valueMultiplier = 1,
        onSeek = { reencodeLossyQuality = it.roundToInt().coerceIn(1, 100) },
        screenPosition = Position.Middle
    )
    val jxlEffortPref = SettingsEntity.SeekPreference(
        title = jxlEffortTitle,
        icon = Icons.Outlined.Speed,
        currentValue = reencodeJxlEffort.toFloat(),
        minValue = 1f,
        maxValue = 9f,
        step = 0,
        valueMultiplier = 1,
        onSeek = { reencodeJxlEffort = it.roundToInt().coerceIn(1, 9) },
        screenPosition = Position.Bottom
    )

    val editorsTitle = stringResource(Res.string.settings_editors)
    val editorIcon: @Composable (String) -> Unit = appIcon
        ?: { PreviewIconBadge(Icons.Outlined.Edit) }

    Scaffold(
        modifier = Modifier.semantics { paneTitle = editorsTitle },
        topBar = {
            GalleryPageTopBar(
                title = editorsTitle,
                hazeState = LocalHazeState.current,
                scrolled = rememberScrolledUnderTopBar(listState),
            )
        }
    ) { padding ->
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .hazeSource(LocalHazeState.current)
                .settingsFocusGroup(),
            contentPadding = PaddingValues(
                start = padding.calculateStartPadding(LocalLayoutDirection.current),
                end = padding.calculateEndPadding(LocalLayoutDirection.current),
                top = 8.dp + padding.calculateTopPadding(),
                bottom = padding.calculateBottomPadding() + 24.dp
            ),
        ) {
            item(key = "image_editor_header") {
                SettingsItem(
                    item = SettingsEntity.Header(
                        title = stringResource(Res.string.editors_image_header)
                    )
                )
            }
            item(key = "image_editor_description") {
                Text(
                    text = stringResource(Res.string.default_editor_description),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .settingsContentWidth()
                        .padding(start = 8.dp, end = 8.dp, bottom = 8.dp),
                )
            }
            item(key = "image_editor_tiles") {
                EditorChooser(
                    options = imageEditorOptions,
                    selected = defaultImageEditor,
                    onSelect = {
                        defaultImageEditor = it
                        imageEditorChoiceExplicit = true
                    },
                    icon = editorIcon,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
            }
            item(key = "video_editor_header") {
                SettingsItem(
                    item = SettingsEntity.Header(
                        title = stringResource(Res.string.editors_video_header)
                    )
                )
            }
            item(key = "video_editor_description") {
                Text(
                    text = stringResource(Res.string.default_video_editor_description),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .settingsContentWidth()
                        .padding(start = 8.dp, end = 8.dp, bottom = 8.dp),
                )
            }
            item(key = "video_editor_tiles") {
                EditorChooser(
                    options = videoEditorOptions,
                    selected = defaultVideoEditor,
                    onSelect = { defaultVideoEditor = it },
                    icon = editorIcon,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
            }
            item(key = "save_quality_header") {
                SettingsItem(item = saveQualityHeader)
            }
            item(key = "manual_quality") {
                SettingsItem(item = manualQualityPref)
            }
            if (isManualQuality) {
                item(key = "lossy_quality") {
                    SettingsItem(item = lossyQualityPref)
                }
                item(key = "jxl_effort") {
                    SettingsItem(item = jxlEffortPref)
                }
            }
        }
    }
}
