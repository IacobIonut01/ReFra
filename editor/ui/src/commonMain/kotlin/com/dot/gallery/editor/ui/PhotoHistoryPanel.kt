package com.dot.gallery.editor.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.dot.gallery.editor.render.photo.PhotoEditorState
import com.dot.gallery.editor.ui.generated.resources.Res
import com.dot.gallery.editor.ui.generated.resources.editor_history_back_to_original
import com.dot.gallery.editor.ui.generated.resources.editor_history_compare
import com.dot.gallery.editor.ui.generated.resources.editor_history_now
import com.dot.gallery.editor.ui.generated.resources.editor_history_original
import com.dot.gallery.editor.ui.generated.resources.editor_history_undone
import com.dot.gallery.editor.ui.generated.resources.editor_tool_effects
import com.dot.gallery.editor.ui.generated.resources.editor_tool_transform_layer
import com.dot.gallery.editor.ui.icons.EditorSymbols
import com.dot.gallery.editor.ui.icons.FitScreen
import com.dot.gallery.editor.ui.icons.Timeline
import org.jetbrains.compose.resources.stringResource

internal const val TAG_HISTORY_ORIGINAL = "editor.history.original"
internal const val TAG_HISTORY_ROW_PREFIX = "editor.history.row."
internal const val TAG_HISTORY_UNDONE_PREFIX = "editor.history.undone."
internal const val TAG_HISTORY_COMPARE = "editor.history.compare"
internal const val TAG_HISTORY_BACK_TO_ORIGINAL =
    "editor.history.backToOriginal"

/**
 * The edit history as a list: Original, one row per committed step (the latest
 * highlighted as Now), then the undone steps under their caption at
 * reduced emphasis. Row taps jump the global history; the footer
 * carries Compare and Back to original.
 */
@Composable
internal fun PhotoHistoryPanel(
    state: PhotoEditorState,
    actions: PhotoEditorActions,
    toolEntries: List<EditorToolEntry<PhotoEditorState, PhotoEditorActions>>,
    viewport: EditorViewportState,
    bottomInset: Dp = 0.dp
) {
    val history = state.history
    val past = history.past
    val undone = history.undone
    // Step labels that name a tool resolve to that tool's catalogue icon;
    // gesture labels ("Adjust Exposure", "Reset") take the generic one.
    // The title resolution mirrors photoToolTitle's context aliases so a
    // transaction step ("Effects", "Transform layer") still matches.
    val stepIcons: Map<String, ImageVector> = toolEntries.associate { entry ->
        val titleRes = when (entry.descriptor.id) {
            EditorToolIds.PhotoTransform -> Res.string.editor_tool_transform_layer
            EditorToolIds.PhotoPosterize,
            EditorToolIds.PhotoEdges,
            EditorToolIds.PhotoBorders -> Res.string.editor_tool_effects
            else -> entry.descriptor.title
        }
        stringResource(titleRes) to entry.descriptor.icon
    }
    fun iconForLabel(label: String): ImageVector =
        stepIcons[label] ?: EditorSymbols.Timeline
    val upright = state.info?.uprightSize
    val format = photoSourceFormatLabel(state)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        HistoryRow(
            icon = EditorSymbols.FitScreen,
            title = stringResource(Res.string.editor_history_original),
            subtitle = upright?.let { "${it.width} × ${it.height} · $format" },
            highlighted = past.isEmpty(),
            tag = TAG_HISTORY_ORIGINAL,
            onClick = { actions.jumpToHistory(0) }
        )
        past.forEachIndexed { index, step ->
            HistoryRow(
                icon = iconForLabel(step.label),
                title = step.label,
                subtitle = photoEditsStepSummary(step.before, step.after),
                highlighted = index == past.lastIndex,
                nowTag = index == past.lastIndex,
                tag = TAG_HISTORY_ROW_PREFIX + index,
                onClick = { actions.jumpToHistory(index + 1) }
            )
        }
        if (undone.isNotEmpty()) {
            Text(
                text = stringResource(Res.string.editor_history_undone),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 12.dp, bottom = 4.dp)
            )
            undone.forEachIndexed { index, step ->
                HistoryRow(
                    icon = iconForLabel(step.label),
                    title = step.label,
                    subtitle = photoEditsStepSummary(step.before, step.after),
                    highlighted = false,
                    dimmed = true,
                    tag = TAG_HISTORY_UNDONE_PREFIX + index,
                    onClick = { actions.jumpToHistory(past.size + index + 1) }
                )
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp, bottom = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilledTonalButton(
                onClick = { viewport.compareSplit = !viewport.compareSplit },
                modifier = Modifier
                    .weight(1f)
                    .testTag(TAG_HISTORY_COMPARE)
            ) {
                Text(stringResource(Res.string.editor_history_compare))
            }
            OutlinedButton(
                onClick = { actions.jumpToHistory(0) },
                enabled = past.isNotEmpty(),
                modifier = Modifier
                    .weight(1f)
                    .testTag(TAG_HISTORY_BACK_TO_ORIGINAL)
            ) {
                Text(
                    text = stringResource(
                        Res.string.editor_history_back_to_original
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        Spacer(Modifier.height(bottomInset))
    }
}

/** The source's format as the header and the Original row print it. */
internal fun photoSourceFormatLabel(state: PhotoEditorState): String =
    state.info?.displayName
        ?.substringAfterLast('.', "")
        ?.uppercase()
        ?.takeIf { it.isNotEmpty() }
        ?: if (state.info?.isRaw == true) "RAW" else "JPG"

/** One history row: icon, title, subtitle and an optional Now tag. */
@Composable
internal fun HistoryRow(
    icon: ImageVector,
    title: String,
    subtitle: String?,
    highlighted: Boolean,
    tag: String,
    onClick: () -> Unit,
    nowTag: Boolean = false,
    dimmed: Boolean = false
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(
                if (highlighted) {
                    MaterialTheme.colorScheme.secondaryContainer
                } else {
                    Color.Transparent
                }
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 8.dp)
            .alpha(if (dimmed) 0.38f else 1f)
            .testTag(tag),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp)
        )
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (!subtitle.isNullOrEmpty()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        if (nowTag) {
            Text(
                text = stringResource(Res.string.editor_history_now),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1
            )
        }
    }
}
