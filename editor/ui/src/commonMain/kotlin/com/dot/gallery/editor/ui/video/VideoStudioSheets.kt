package com.dot.gallery.editor.ui.video

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
import com.dot.gallery.editor.ui.generated.resources.Res
import com.dot.gallery.editor.ui.generated.resources.editor_all_tools
import com.dot.gallery.editor.ui.generated.resources.editor_group_audio
import com.dot.gallery.editor.ui.generated.resources.editor_group_deliver
import com.dot.gallery.editor.ui.generated.resources.editor_group_edit
import com.dot.gallery.editor.ui.generated.resources.editor_group_picture
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

private val VIDEO_CATEGORIES = listOf(
    Res.string.editor_group_edit to EditorToolGroup.Edit,
    Res.string.editor_group_picture to EditorToolGroup.Picture,
    Res.string.editor_group_audio to EditorToolGroup.Audio,
    Res.string.editor_group_deliver to EditorToolGroup.Deliver
)

/**
 * All tools: the search pill, the connected Edit · Picture · Audio ·
 * Deliver group and the tile grid of the real registry. A query spans
 * every category; tiles the session cannot honour yet stay disabled.
 */
@Composable
internal fun VideoCatalogueSheet(
    state: VideoEditorState,
    phone: Boolean,
    initialGroup: EditorToolGroup,
    activeTool: EditorToolId?,
    onSelect: (EditorToolId) -> Unit,
    onDismiss: () -> Unit
) {
    var group by rememberSaveable(initialGroup) { mutableStateOf(initialGroup.name) }
    var query by rememberSaveable { mutableStateOf("") }
    val entries = videoToolEntries()
    VideoStudioSheet(
        title = stringResource(Res.string.editor_video_tools_title),
        phone = phone,
        onDismiss = onDismiss,
        tag = TAG_VIDEO_CATALOGUE_SHEET
    ) {
        val searchLabel = stringResource(Res.string.editor_search_tools)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 52.dp)
                .clip(CircleShape)
                .background(videoSlabColor())
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                VideoGlyphs.Search, null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(22.dp)
            )
            Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                if (query.isEmpty()) {
                    Text(
                        searchLabel,
                        fontSize = 16.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                BasicTextField(
                    value = query,
                    onValueChange = { query = it },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyLarge.copy(
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 16.sp
                    ),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    modifier = Modifier
                        .fillMaxWidth()
                        .semantics { contentDescription = searchLabel }
                        .testTag(TAG_CATALOGUE_SEARCH)
                )
            }
        }
        val q = query.trim()
        if (q.isEmpty()) {
            VideoPillSegments(
                options = VIDEO_CATEGORIES.map { (label, category) ->
                    VideoSegment(
                        label = stringResource(label),
                        selected = group == category.name,
                        onClick = { group = category.name },
                        tag = "editor.catalogue.group." + category.name
                    )
                }
            )
        }
        val titles = entries.associate { it.descriptor.id to stringResource(it.descriptor.title) }
        val visible = entries.filter { entry ->
            if (q.isNotEmpty()) {
                titles[entry.descriptor.id].orEmpty().contains(q, ignoreCase = true)
            } else {
                entry.descriptor.group.name == group
            }
        }
        BoxWithConstraints(Modifier.fillMaxWidth().testTag(TAG_CATALOGUE_GRID)) {
            val columns = (maxWidth.value / 112f).toInt().coerceIn(2, 5)
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                visible.chunked(columns).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        row.forEachIndexed { index, entry ->
                            val id = entry.descriptor.id
                            VideoCatalogueTile(
                                icon = videoToolGlyph(id),
                                label = titles[id].orEmpty(),
                                selected = id == activeTool,
                                shapeIndex = index,
                                enabled = state.sequence != null && !state.isDrafting &&
                                    entry.available(state),
                                onClick = { onSelect(id) },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag(entry.tag)
                            )
                        }
                        repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
        }
    }
}

@Composable
private fun VideoCatalogueTile(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    shapeIndex: Int,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val corner = when {
        selected -> 36.dp
        shapeIndex % 3 == 1 -> 28.dp
        else -> 20.dp
    }
    val ink = when {
        !enabled -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
        selected -> MaterialTheme.colorScheme.onPrimaryContainer
        else -> MaterialTheme.colorScheme.onSurface
    }
    Column(
        modifier = modifier
            .heightIn(min = 96.dp)
            .clip(RoundedCornerShape(corner))
            .background(
                if (selected) MaterialTheme.colorScheme.primaryContainer
                else videoSlabColor()
            )
            .clickable(enabled = enabled, role = Role.Button, onClickLabel = label, onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically)
    ) {
        Icon(icon, null, tint = ink, modifier = Modifier.size(28.dp))
        Text(
            text = label,
            fontSize = 13.sp,
            lineHeight = 16.sp,
            color = ink,
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.labelLarge
        )
    }
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
    onSettings: (() -> Unit)? = null
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
