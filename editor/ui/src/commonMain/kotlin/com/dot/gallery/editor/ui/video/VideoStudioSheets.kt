package com.dot.gallery.editor.ui.video

import com.dot.gallery.editor.ui.generated.resources.editor_video_desc_settings
import com.dot.gallery.editor.ui.generated.resources.editor_tool_desc_history
import com.dot.gallery.editor.ui.CataloguePreview
import com.dot.gallery.editor.ui.EditorCatalogueExtraCell
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dot.gallery.editor.render.video.VideoEditorState
import com.dot.gallery.editor.ui.EditorToolGroup
import com.dot.gallery.editor.ui.EditorToolId
import com.dot.gallery.editor.ui.EditorToolIds
import com.dot.gallery.editor.ui.TAG_CATALOGUE_GRID
import com.dot.gallery.editor.ui.TAG_CATALOGUE_SEARCH
import com.dot.gallery.editor.ui.CatalogueSection
import com.dot.gallery.editor.ui.EditorToolCatalogueSheet
import org.jetbrains.compose.resources.StringResource
import com.dot.gallery.editor.ui.generated.resources.Res
import com.dot.gallery.editor.ui.generated.resources.editor_all_tools
import com.dot.gallery.editor.ui.generated.resources.editor_video_desc_export
import com.dot.gallery.editor.ui.generated.resources.editor_video_desc_voice
import com.dot.gallery.editor.ui.generated.resources.editor_video_desc_eq
import com.dot.gallery.editor.ui.generated.resources.editor_video_desc_ducking
import com.dot.gallery.editor.ui.generated.resources.editor_video_desc_routing
import com.dot.gallery.editor.ui.generated.resources.editor_video_desc_mixer
import com.dot.gallery.editor.ui.generated.resources.editor_video_desc_clip_audio
import com.dot.gallery.editor.ui.generated.resources.editor_video_desc_add_audio
import com.dot.gallery.editor.ui.generated.resources.editor_video_desc_layers
import com.dot.gallery.editor.ui.generated.resources.editor_video_desc_colour
import com.dot.gallery.editor.ui.generated.resources.editor_video_desc_transform
import com.dot.gallery.editor.ui.generated.resources.editor_video_desc_trim
import com.dot.gallery.editor.ui.generated.resources.editor_video_desc_arrange
import com.dot.gallery.editor.ui.generated.resources.editor_video_desc_add_media
import com.dot.gallery.editor.ui.generated.resources.editor_history_title
import com.dot.gallery.editor.ui.generated.resources.editor_search_tools
import com.dot.gallery.editor.ui.generated.resources.editor_settings
import com.dot.gallery.editor.ui.generated.resources.editor_video_exit_focus
import com.dot.gallery.editor.ui.generated.resources.editor_video_focus_preview
import com.dot.gallery.editor.ui.generated.resources.editor_video_more
import com.dot.gallery.editor.ui.generated.resources.editor_video_tools_title
import org.jetbrains.compose.resources.stringResource

internal const val TAG_VIDEO_MORE_SHEET = "videoEditor.moreSheet"
internal const val TAG_VIDEO_MORE_ALL_TOOLS = "videoEditor.more.allTools"
internal const val TAG_VIDEO_MORE_MIXER = "videoEditor.more.mixer"
internal const val TAG_VIDEO_MORE_SETTINGS = "videoEditor.more.settings"
internal const val TAG_VIDEO_MORE_HISTORY = "videoEditor.more.history"

/**
 * All tools: the same list the photo editor uses — search, the Edit ·
 * Picture · Audio · Deliver chips and one row per tool with its preview
 * tile and a line on what it does. A query spans every section; rows the
 * session cannot honour yet stay disabled.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun VideoCatalogueSheet(
    state: VideoEditorState,
    phone: Boolean,
    initialGroup: EditorToolGroup?,
    activeTool: EditorToolId?,
    onSelect: (EditorToolId) -> Unit,
    onDismiss: () -> Unit,
    onHistory: () -> Unit = {},
    onSettings: () -> Unit = {}
) {
    // The same modal sheet the photo editor's All tools uses, on every
    // window: the drag handle, the list's own title row, no extra chrome.
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        dragHandle = { BottomSheetDefaults.DragHandle() }
    ) {
        Column(Modifier.testTag(TAG_VIDEO_CATALOGUE_SHEET)) {
            VideoCatalogueList(state, initialGroup, activeTool, onSelect, onHistory, onSettings)
        }
    }
}

/**
 * The video tools list itself, shared by the phone sheet and the large
 * layout's side pane.
 */
@Composable
internal fun VideoCatalogueList(
    state: VideoEditorState,
    initialGroup: EditorToolGroup?,
    activeTool: EditorToolId?,
    onSelect: (EditorToolId) -> Unit,
    onHistory: () -> Unit,
    onSettings: () -> Unit,
    showTitle: Boolean = true
) {
    EditorToolCatalogueSheet(
        entries = videoToolEntries(),
        state = state,
        activeTool = activeTool ?: EditorToolId(""),
        enabled = state.sequence != null && !state.isDrafting,
        onSelect = onSelect,
        title = if (showTitle) stringResource(Res.string.editor_all_tools) else null,
        initialGroup = initialGroup,
        showDragHandle = false,
        chips = VIDEO_CATALOGUE_CHIPS,
        describe = ::videoToolDescription,
        extraCells = listOf(
            EditorCatalogueExtraCell(
                icon = VideoGlyphs.Undo,
                label = stringResource(Res.string.editor_history_title),
                tag = TAG_VIDEO_CATALOGUE_HISTORY,
                description = stringResource(Res.string.editor_tool_desc_history),
                preview = CataloguePreview.History,
                onClick = onHistory
            ),
            EditorCatalogueExtraCell(
                icon = Icons.Outlined.Settings,
                label = stringResource(Res.string.editor_settings),
                tag = TAG_VIDEO_CATALOGUE_SETTINGS,
                description = stringResource(Res.string.editor_video_desc_settings),
                preview = CataloguePreview.Settings,
                onClick = onSettings
            )
        )
    )
}

internal const val TAG_VIDEO_CATALOGUE_HISTORY = "videoEditor.catalogue.history"
internal const val TAG_VIDEO_CATALOGUE_SETTINGS = "videoEditor.catalogue.settings"

private val VIDEO_CATALOGUE_CHIPS = listOf(
    CatalogueSection.Edit, CatalogueSection.Picture,
    CatalogueSection.Audio, CatalogueSection.Deliver
)

/** The line a video tool's catalogue row shows. */
internal fun videoToolDescription(id: EditorToolId): StringResource? = when (id) {
    EditorToolIds.VideoAddMedia -> Res.string.editor_video_desc_add_media
    EditorToolIds.VideoArrange -> Res.string.editor_video_desc_arrange
    EditorToolIds.VideoTrim -> Res.string.editor_video_desc_trim
    EditorToolIds.VideoTransform -> Res.string.editor_video_desc_transform
    EditorToolIds.VideoColour -> Res.string.editor_video_desc_colour
    EditorToolIds.VideoLayers -> Res.string.editor_video_desc_layers
    EditorToolIds.VideoAddAudio -> Res.string.editor_video_desc_add_audio
    EditorToolIds.VideoClipAudio -> Res.string.editor_video_desc_clip_audio
    EditorToolIds.VideoMixer -> Res.string.editor_video_desc_mixer
    EditorToolIds.VideoRouting -> Res.string.editor_video_desc_routing
    EditorToolIds.VideoDucking -> Res.string.editor_video_desc_ducking
    EditorToolIds.VideoEq -> Res.string.editor_video_desc_eq
    EditorToolIds.VideoVoice -> Res.string.editor_video_desc_voice
    EditorToolIds.VideoExport -> Res.string.editor_video_desc_export
    else -> null
}

/** The studio glyph for a registered tool. */
internal fun videoToolGlyph(id: EditorToolId): ImageVector = when (id) {
    EditorToolIds.VideoTrim -> VideoGlyphs.Trim
    EditorToolIds.VideoArrange -> VideoGlyphs.Arrange
    EditorToolIds.VideoLayers -> VideoGlyphs.Layers
    EditorToolIds.VideoAddMedia -> VideoGlyphs.Movie
    EditorToolIds.VideoTransform -> VideoGlyphs.Transform
    EditorToolIds.VideoColour -> VideoGlyphs.Palette
    EditorToolIds.VideoAddAudio -> VideoGlyphs.LibraryMusic
    EditorToolIds.VideoClipAudio -> VideoGlyphs.Audio
    EditorToolIds.VideoMixer -> VideoGlyphs.Tune
    EditorToolIds.VideoRouting -> VideoGlyphs.Routing
    EditorToolIds.VideoDucking -> VideoGlyphs.Ducking
    EditorToolIds.VideoEq -> VideoGlyphs.Eq
    EditorToolIds.VideoVoice -> VideoGlyphs.Mic
    else -> VideoGlyphs.Movie
}

/** More: Focus on the preview, All tools and the Mixer. */
@Composable
internal fun VideoMoreSheet(
    phone: Boolean,
    canvasFocused: Boolean,
    onToggleFocus: () -> Unit,
    onAllTools: () -> Unit,
    onMixer: () -> Unit,
    onDismiss: () -> Unit,
    onSettings: (() -> Unit)? = null,
    onHistory: (() -> Unit)? = null
) {
    VideoStudioSheet(
        title = stringResource(Res.string.editor_video_more),
        phone = phone,
        onDismiss = onDismiss,
        tag = TAG_VIDEO_MORE_SHEET
    ) {
        Column {
            VideoMoreRow(
                icon = if (canvasFocused) VideoGlyphs.CloseFullscreen else VideoGlyphs.OpenInFull,
                label = stringResource(
                    if (canvasFocused) Res.string.editor_video_exit_focus
                    else Res.string.editor_video_focus_preview
                ),
                onClick = onToggleFocus,
                modifier = Modifier.testTag(TAG_VIDEO_FOCUS)
            )
            VideoMoreRow(
                icon = VideoGlyphs.Apps,
                label = stringResource(Res.string.editor_all_tools),
                onClick = onAllTools,
                modifier = Modifier.testTag(TAG_VIDEO_MORE_ALL_TOOLS)
            )
            VideoMoreRow(
                icon = VideoGlyphs.Tune,
                label = stringResource(videoToolDescriptor(EditorToolIds.VideoMixer).title),
                onClick = onMixer,
                modifier = Modifier.testTag(TAG_VIDEO_MORE_MIXER)
            )
            if (onHistory != null) {
                VideoMoreRow(
                    icon = VideoGlyphs.Undo,
                    label = stringResource(Res.string.editor_history_title),
                    onClick = onHistory,
                    modifier = Modifier.testTag(TAG_VIDEO_MORE_HISTORY)
                )
            }
            if (onSettings != null) {
                VideoMoreRow(
                    icon = Icons.Outlined.Settings,
                    label = stringResource(Res.string.editor_settings),
                    onClick = onSettings,
                    modifier = Modifier.testTag(TAG_VIDEO_MORE_SETTINGS)
                )
            }
        }
    }
}

@Composable
private fun VideoMoreRow(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clip(RoundedCornerShape(16.dp))
            .clickable(role = Role.Button, onClickLabel = label, onClick = onClick)
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
        Text(label, fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurface)
    }
}
