package com.dot.gallery.editor.ui.video

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.dot.gallery.editor.render.video.VideoEditorState
import com.dot.gallery.editor.ui.HistoryRow
import com.dot.gallery.editor.ui.generated.resources.Res
import com.dot.gallery.editor.ui.generated.resources.editor_history_back_to_original
import com.dot.gallery.editor.ui.generated.resources.editor_history_original
import com.dot.gallery.editor.ui.generated.resources.editor_history_title
import com.dot.gallery.editor.ui.generated.resources.editor_history_undone
import com.dot.gallery.editor.ui.icons.EditorSymbols
import com.dot.gallery.editor.ui.icons.FitScreen
import com.dot.gallery.editor.ui.icons.Timeline
import org.jetbrains.compose.resources.stringResource

internal const val TAG_VIDEO_HISTORY_SHEET = "videoEditor.history.sheet"
internal const val TAG_VIDEO_HISTORY_ORIGINAL = "videoEditor.history.original"
internal const val TAG_VIDEO_HISTORY_ROW_PREFIX = "videoEditor.history.row."
internal const val TAG_VIDEO_HISTORY_UNDONE_PREFIX = "videoEditor.history.undone."

/**
 * The video editor's History sheet, the same list the photo editor shows:
 * Original, every committed step with the latest marked Now, then the
 * undone steps. Tapping a row jumps there and keeps the sheet open.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun VideoHistorySheet(
    state: VideoEditorState,
    actions: VideoEditorActions,
    onDismiss: () -> Unit
) {
    val past = state.historyPast
    val undone = state.historyUndone
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(),
        dragHandle = { BottomSheetDefaults.DragHandle() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 12.dp)
                .testTag(TAG_VIDEO_HISTORY_SHEET)
        ) {
            Text(
                text = stringResource(Res.string.editor_history_title),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
            )
            HistoryRow(
                icon = EditorSymbols.FitScreen,
                title = stringResource(Res.string.editor_history_original),
                subtitle = state.info?.displayName,
                highlighted = past.isEmpty(),
                tag = TAG_VIDEO_HISTORY_ORIGINAL,
                onClick = { actions.jumpToHistory(0) }
            )
            past.forEachIndexed { index, label ->
                HistoryRow(
                    icon = EditorSymbols.Timeline,
                    title = label,
                    subtitle = null,
                    highlighted = index == past.lastIndex,
                    nowTag = index == past.lastIndex,
                    tag = TAG_VIDEO_HISTORY_ROW_PREFIX + index,
                    onClick = { actions.jumpToHistory(index + 1) }
                )
            }
            if (undone.isNotEmpty()) {
                Text(
                    text = stringResource(Res.string.editor_history_undone),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 12.dp, top = 12.dp, bottom = 4.dp)
                )
                undone.forEachIndexed { index, label ->
                    HistoryRow(
                        icon = EditorSymbols.Timeline,
                        title = label,
                        subtitle = null,
                        highlighted = false,
                        dimmed = true,
                        tag = TAG_VIDEO_HISTORY_UNDONE_PREFIX + index,
                        onClick = { actions.jumpToHistory(past.size + index + 1) }
                    )
                }
            }
            OutlinedButton(
                onClick = { actions.jumpToHistory(0) },
                enabled = past.isNotEmpty(),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp)
            ) {
                Text(stringResource(Res.string.editor_history_back_to_original))
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}
