/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.feature.viewer

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AddCircleOutline
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ClosedCaptionDisabled
import androidx.compose.material.icons.outlined.Subtitles
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.dot.gallery.core.Position
import com.dot.gallery.core.presentation.components.ExpressiveSheet
import com.dot.gallery.core.presentation.components.SheetHeader
import com.dot.gallery.core.presentation.components.SheetOptionRow
import com.dot.gallery.core.presentation.components.SheetRowTrailing
import com.dot.gallery.core.presentation.components.sheetRowPosition
import com.dot.gallery.core.ui.util.AppBottomSheetState
import com.dot.gallery.domain.model.SubtitleTrack
import com.dot.gallery.feature.viewer.generated.resources.Res
import com.dot.gallery.feature.viewer.generated.resources.add_subtitle_file
import com.dot.gallery.feature.viewer.generated.resources.remove_subtitle_track
import com.dot.gallery.feature.viewer.generated.resources.subtitle_external_label
import com.dot.gallery.feature.viewer.generated.resources.subtitle_off
import com.dot.gallery.feature.viewer.generated.resources.subtitles_title
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource

@Composable
fun SubtitleBottomSheet(
    state: AppBottomSheetState,
    subtitleTracks: List<SubtitleTrack>,
    onSelectSubtitle: (SubtitleTrack) -> Unit,
    onDisableSubtitles: () -> Unit,
    onAddSubtitle: () -> Unit,
    onRemoveSubtitle: (SubtitleTrack) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val hide: () -> Unit = { scope.launch { state.hide() } }
    ExpressiveSheet(sheetState = state, paneTitle = stringResource(Res.string.subtitles_title)) {
        SubtitleSheetContent(
            tracks = subtitleTracks.map {
                SubtitleOption(
                    key = "${it.groupIndex}:${it.trackIndex}",
                    label = it.label,
                    language = it.language,
                    selected = it.isSelected,
                    external = it.isManuallyAdded,
                )
            },
            onSelect = { key ->
                subtitleTracks.firstOrNull { "${it.groupIndex}:${it.trackIndex}" == key }?.let(onSelectSubtitle)
                hide()
            },
            onDisable = { onDisableSubtitles(); hide() },
            onAdd = { onAddSubtitle(); hide() },
            onRemove = { key ->
                subtitleTracks.firstOrNull { "${it.groupIndex}:${it.trackIndex}" == key }?.let(onRemoveSubtitle)
            },
        )
    }
}

/** One subtitle track shown by [SubtitleSheetContent]. */
data class SubtitleOption(
    val key: String,
    val label: String,
    val language: String?,
    val selected: Boolean,
    val external: Boolean,
)

/** Stateless body of [SubtitleBottomSheet]: Off, every track as a radio row, then Add subtitle file. */
@Composable
fun ColumnScope.SubtitleSheetContent(
    tracks: List<SubtitleOption>,
    onSelect: (String) -> Unit,
    onDisable: () -> Unit,
    onAdd: () -> Unit,
    onRemove: (String) -> Unit,
) {
    SheetHeader(title = stringResource(Res.string.subtitles_title))
    val count = tracks.size + 1
    SheetOptionRow(
        title = stringResource(Res.string.subtitle_off),
        icon = Icons.Outlined.ClosedCaptionDisabled,
        position = sheetRowPosition(0, count),
        trailing = SheetRowTrailing.Radio(tracks.none { it.selected }),
        onClick = onDisable,
    )
    val externalLabel = stringResource(Res.string.subtitle_external_label)
    val removeLabel = stringResource(Res.string.remove_subtitle_track)
    tracks.forEachIndexed { index, track ->
        val summary = listOfNotNull(track.language, externalLabel.takeIf { track.external }).joinToString(" · ")
        SheetOptionRow(
            title = track.label,
            summary = summary.ifBlank { null },
            icon = Icons.Outlined.Subtitles,
            position = sheetRowPosition(index + 1, count),
            trailing = SheetRowTrailing.Custom {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (track.external) {
                        IconButton(onClick = { onRemove(track.key) }) {
                            Icon(Icons.Outlined.Close, contentDescription = removeLabel, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    RadioButton(selected = track.selected, onClick = null)
                }
            },
            onClick = { onSelect(track.key) },
        )
    }
    Spacer(Modifier.height(12.dp))
    SheetOptionRow(
        title = stringResource(Res.string.add_subtitle_file),
        icon = Icons.Outlined.AddCircleOutline,
        position = Position.Alone,
        containerColor = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        trailing = SheetRowTrailing.Chevron,
        onClick = onAdd,
    )
}
