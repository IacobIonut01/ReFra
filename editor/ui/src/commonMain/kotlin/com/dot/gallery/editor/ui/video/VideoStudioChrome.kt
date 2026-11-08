package com.dot.gallery.editor.ui.video

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import com.dot.gallery.editor.core.time.EditorTime
import com.dot.gallery.editor.render.video.VideoEditorState
import com.dot.gallery.editor.ui.TAG_HEADER_MORE
import com.dot.gallery.editor.ui.TAG_VIDEO_REGION_TOOLBAR
import com.dot.gallery.editor.ui.generated.resources.Res
import com.dot.gallery.editor.ui.generated.resources.editor_done
import com.dot.gallery.editor.ui.generated.resources.editor_layers
import com.dot.gallery.editor.ui.generated.resources.editor_more_options
import com.dot.gallery.editor.ui.generated.resources.editor_redo
import com.dot.gallery.editor.ui.generated.resources.editor_tools
import com.dot.gallery.editor.ui.generated.resources.editor_undo
import com.dot.gallery.editor.ui.generated.resources.editor_video_audio
import com.dot.gallery.editor.ui.generated.resources.editor_video_exit_focus
import com.dot.gallery.editor.ui.generated.resources.editor_video_export
import com.dot.gallery.editor.ui.generated.resources.editor_video_focus_preview
import com.dot.gallery.editor.ui.generated.resources.editor_video_media
import com.dot.gallery.editor.ui.generated.resources.editor_video_mute
import com.dot.gallery.editor.ui.generated.resources.editor_video_next_frame
import com.dot.gallery.editor.ui.generated.resources.editor_video_of_total
import com.dot.gallery.editor.ui.generated.resources.editor_video_pause
import com.dot.gallery.editor.ui.generated.resources.editor_video_play
import com.dot.gallery.editor.ui.generated.resources.editor_video_prev_frame
import com.dot.gallery.editor.ui.generated.resources.editor_video_reset
import com.dot.gallery.editor.ui.generated.resources.editor_video_unmute
import org.jetbrains.compose.resources.stringResource
import com.dot.gallery.editor.ui.PillSlot

internal const val TAG_VIDEO_PANEL = "videoEditor.panel"
internal const val TAG_VIDEO_BACK = "videoEditor.back"
internal const val TAG_VIDEO_UNDO = "videoEditor.undo"
internal const val TAG_VIDEO_REDO = "videoEditor.redo"
internal const val TAG_VIDEO_SAVE = "videoEditor.save"
internal const val TAG_VIDEO_EXPORT_MENU = "videoEditor.exportMenu"
internal const val TAG_VIDEO_PANEL_RESET = "videoEditor.panel.reset"
internal const val TAG_VIDEO_TOOL_DONE = "videoEditor.tool.done"

/** Which dock destination is lit. */
internal enum class VideoDockItem { Media, Layers, Audio, Tools }

// ---------------------------------------------------------------------------
// Header
// ---------------------------------------------------------------------------

/**
 * The studio header: the 48 dp glass Close circle, the source name on
 * tablets, the glass Undo · Redo capsule and the trailing action — the
 * Export split button at home (Export plus the More chevron), a single
 * Done pill while a phone tool owns the screen.
 */
@Composable
internal fun VideoStudioHeader(
    state: VideoEditorState,
    actions: VideoEditorActions,
    closeLabel: String,
    closeTag: String,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    title: String? = null,
    exportEnabled: Boolean = true,
    onExport: (() -> Unit)? = null,
    moreOpen: Boolean = false,
    onMore: (() -> Unit)? = null,
    onDone: (() -> Unit)? = null
) {
    val canUndo = state.canUndo && !state.isDrafting
    val canRedo = state.canRedo && !state.isDrafting
    val fontScale = LocalDensity.current.fontScale
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        VideoGlassIconButton(
            icon = VideoGlyphs.Close,
            label = closeLabel,
            onClick = onClose,
            modifier = Modifier.testTag(closeTag)
        )
        if (title != null) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 8.dp)
            )
        } else {
            Spacer(Modifier.weight(1f))
        }
        Row(
            modifier = Modifier
                .height(VideoStudio.Control)
                .clip(CircleShape)
                .videoGlass(CircleShape)
                .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            VideoPlainIconButton(
                icon = VideoGlyphs.Undo,
                label = stringResource(Res.string.editor_undo),
                onClick = actions.undo,
                enabled = canUndo,
                modifier = Modifier.testTag(TAG_VIDEO_UNDO)
            )
            VideoPlainIconButton(
                icon = VideoGlyphs.Redo,
                label = stringResource(Res.string.editor_redo),
                onClick = actions.redo,
                enabled = canRedo,
                modifier = Modifier.testTag(TAG_VIDEO_REDO)
            )
        }
        if (title == null) Spacer(Modifier.weight(1f))
        when {
            onDone != null -> {
                val doneLabel = stringResource(Res.string.editor_done)
                Row(
                    modifier = Modifier
                        .height(VideoStudio.Control)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary)
                        .clickable(
                            role = Role.Button,
                            onClickLabel = doneLabel,
                            onClick = onDone
                        )
                        .testTag(TAG_VIDEO_TOOL_DONE)
                        .padding(start = 16.dp, end = 20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = VideoGlyphs.Check,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(20.dp)
                    )
                    if (fontScale < 1.5f) {
                        Text(
                            text = doneLabel,
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onPrimary,
                            maxLines = 1
                        )
                    }
                }
            }
            onExport != null -> VideoExportSplitButton(
                enabled = exportEnabled,
                moreOpen = moreOpen,
                onExport = onExport,
                onMore = onMore
            )
        }
    }
}

/**
 * Export plus the More chevron as one connected split button — the
 * chevron springs from its join radius to a full pill while its sheet is
 * open.
 */
@Composable
private fun VideoExportSplitButton(
    enabled: Boolean,
    moreOpen: Boolean,
    onExport: () -> Unit,
    onMore: (() -> Unit)?
) {
    val exportLabel = stringResource(Res.string.editor_video_export)
    val moreLabel = stringResource(Res.string.editor_more_options)
    val container = if (enabled) MaterialTheme.colorScheme.primary
    else videoSlabColor()
    val content = if (enabled) MaterialTheme.colorScheme.onPrimary
    else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
    val showLabel = LocalDensity.current.fontScale < 1.5f
    Row(horizontalArrangement = Arrangement.spacedBy(VideoStudio.GroupGap)) {
        Box(
            modifier = Modifier
                .height(VideoStudio.Control)
                .clip(
                    RoundedCornerShape(
                        topStart = 24.dp, bottomStart = 24.dp,
                        topEnd = 6.dp, bottomEnd = 6.dp
                    )
                )
                .background(container)
                .clickable(
                    enabled = enabled,
                    role = Role.Button,
                    onClickLabel = exportLabel,
                    onClick = onExport
                )
                .semantics { if (!showLabel) contentDescription = exportLabel }
                .testTag(TAG_VIDEO_SAVE)
                .padding(start = 18.dp, end = 14.dp),
            contentAlignment = Alignment.Center
        ) {
            if (showLabel) {
                Text(
                    text = exportLabel,
                    style = MaterialTheme.typography.labelLarge,
                    color = content,
                    maxLines = 1
                )
            } else {
                Icon(VideoGlyphs.Check, null, tint = content, modifier = Modifier.size(20.dp))
            }
        }
        if (onMore != null) {
            val corner by animateDpAsState(
                if (moreOpen) 24.dp else 6.dp,
                spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMediumLow),
                label = "moreCorner"
            )
            Box(
                modifier = Modifier
                    .size(width = 44.dp, height = VideoStudio.Control)
                    .clip(
                        RoundedCornerShape(
                            topStart = corner, bottomStart = corner,
                            topEnd = 24.dp, bottomEnd = 24.dp
                        )
                    )
                    .background(MaterialTheme.colorScheme.primary)
                    .clickable(
                        role = Role.Button,
                        onClickLabel = moreLabel,
                        onClick = onMore
                    )
                    .semantics {
                        contentDescription = moreLabel
                        stateDescription = if (moreOpen) "expanded" else "collapsed"
                    }
                    .testTag(TAG_HEADER_MORE),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = VideoGlyphs.ExpandMore,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(22.dp)
                )
            }
        }
    }
}

/** A 48 dp glass circle carrying one icon — the header's Close. */
@Composable
internal fun VideoGlassIconButton(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    Box(
        modifier = modifier
            .size(VideoStudio.Control)
            .clip(CircleShape)
            .videoGlass(CircleShape)
            .clickable(
                enabled = enabled,
                role = Role.Button,
                onClickLabel = label,
                onClick = onClick
            )
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurface.copy(
                alpha = if (enabled) 1f else 0.38f
            ),
            modifier = Modifier.size(24.dp)
        )
    }
}

/** A transparent 48 dp icon target — undo/redo, sound, focus. */
@Composable
internal fun VideoPlainIconButton(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    selected: Boolean? = null,
    tint: Color = MaterialTheme.colorScheme.onSurface
) {
    Box(
        modifier = modifier
            .size(VideoStudio.Control)
            .clip(CircleShape)
            .clickable(
                enabled = enabled,
                role = Role.Button,
                onClickLabel = label,
                onClick = onClick
            )
            .semantics {
                contentDescription = label
                if (selected != null) this.selected = selected
            },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (enabled) tint else MaterialTheme.colorScheme.outline,
            modifier = Modifier.size(24.dp)
        )
    }
}

// ---------------------------------------------------------------------------
// Dock
// ---------------------------------------------------------------------------

/**
 * The studio dock — Media, Layers, Audio, Tools in the photo editor's
 * navigation anatomy: [PillSlot]s (the 48×32 dp selection pill behind a
 * 22 dp icon, 12 sp label) on the floating chrome material. A full-width
 * bar on phones; on large portrait windows ([hug]) the bar keeps the photo
 * slot pitch and is only as wide as its four slots. In landscape it is a
 * rail with the slots packed from the top at the photo rail's 62 dp pitch
 * (icon-only at 64 dp on phones, labelled at 80 dp on tablets).
 */
@Composable
internal fun VideoStudioDock(
    selected: VideoDockItem?,
    enabled: Boolean,
    mediaEnabled: Boolean,
    onSelect: (VideoDockItem) -> Unit,
    modifier: Modifier = Modifier,
    vertical: Boolean = false,
    labels: Boolean = true,
    hug: Boolean = false
) {
    val items = listOf(
        Triple(VideoDockItem.Media, VideoGlyphs.Media, TAG_VIDEO_MEDIA),
        Triple(VideoDockItem.Layers, VideoGlyphs.Layers, TAG_VIDEO_LAYERS),
        Triple(VideoDockItem.Audio, VideoGlyphs.Audio, TAG_VIDEO_AUDIO),
        Triple(VideoDockItem.Tools, VideoGlyphs.Tune, TAG_VIDEO_TOOLS)
    )
    val shape = RoundedCornerShape(VideoStudio.CardCorner)
    val content: @Composable (Modifier) -> Unit = { itemModifier ->
        items.forEach { (item, icon, tag) ->
            PillSlot(
                icon = icon,
                label = videoDockLabel(item),
                selected = selected == item,
                enabled = enabled && (item != VideoDockItem.Media || mediaEnabled),
                onClick = { onSelect(item) },
                labelled = labels,
                modifier = itemModifier.testTag(tag)
            )
        }
    }
    if (vertical) {
        Column(
            modifier = modifier
                .clip(shape)
                .videoGlass(shape)
                .padding(vertical = if (labels) 12.dp else 8.dp)
                .testTag(TAG_VIDEO_REGION_TOOLBAR),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            content(
                Modifier
                    .padding(horizontal = if (labels) 12.dp else 8.dp)
                    .fillMaxWidth()
                    .height(if (labels) VideoStudio.RailSlot else VideoStudio.Control)
            )
        }
    } else {
        Row(
            modifier = modifier
                .clip(shape)
                .videoGlass(shape)
                .padding(horizontal = 4.dp)
                .testTag(TAG_VIDEO_REGION_TOOLBAR),
            verticalAlignment = Alignment.CenterVertically
        ) {
            content(
                if (hug) Modifier.width(VideoStudio.BarSlot).fillMaxHeight()
                else Modifier.weight(1f).fillMaxHeight()
            )
        }
    }
}

@Composable
internal fun videoDockLabel(item: VideoDockItem): String = when (item) {
    VideoDockItem.Media -> stringResource(Res.string.editor_video_media)
    VideoDockItem.Layers -> stringResource(Res.string.editor_layers)
    VideoDockItem.Audio -> stringResource(Res.string.editor_video_audio)
    VideoDockItem.Tools -> stringResource(Res.string.editor_tools)
}

// ---------------------------------------------------------------------------
// Transport
// ---------------------------------------------------------------------------

/**
 * The studio transport: the expressive progress line, then the elapsed
 * readout (with "of total" when there is room), the connected previous /
 * Play / next group — Play is a scalloped cookie that springs into a
 * soft square while playing — and preview sound plus Focus on tablets.
 * Arrow keys step one frame and Space toggles playback while focused.
 */
@Composable
internal fun VideoStudioTransport(
    state: VideoEditorState,
    actions: VideoEditorActions,
    modifier: Modifier = Modifier,
    big: Boolean = true,
    timeSize: TextUnit = 20.sp,
    showTotal: Boolean = true,
    showFocus: Boolean = false,
    canvasFocused: Boolean = false,
    onToggleFocus: () -> Unit = {},
    focusRequester: FocusRequester? = null
) {
    val sequence = state.sequence
    val duration = sequence?.duration ?: EditorTime.Zero
    val hasFrames = sequence != null && duration.ticks > 0L
    fun step(delta: Long) {
        val seq = sequence ?: return
        actions.seek(
            EditorTime(state.position.ticks + delta).coerceIn(EditorTime.Zero, seq.duration)
        )
    }
    Column(
        modifier = modifier
            .fillMaxWidth()
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .onKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown || !hasFrames) return@onKeyEvent false
                val perFrame = sequence.frameRate.ticksPerFrame
                when (event.key) {
                    Key.DirectionLeft -> { step(-perFrame); true }
                    Key.DirectionRight -> { step(perFrame); true }
                    Key.Spacebar -> {
                        if (state.playing) actions.pause() else actions.play()
                        true
                    }
                    else -> false
                }
            }
            .focusable()
            .testTag(TAG_VIDEO_TRANSPORT),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        VideoWavyProgress(
            fraction = if (duration.ticks > 0L) {
                state.position.ticks.toFloat() / duration.ticks
            } else 0f,
            playing = state.playing
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f, fill = false)
                .heightIn(min = VideoStudio.Control),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 4.dp)
                    .testTag(TAG_VIDEO_TIMECODE)
                    .semantics(mergeDescendants = true) {}
            ) {
                Text(
                    text = formatTransportTimecode(state.position),
                    fontSize = timeSize,
                    lineHeight = timeSize * 1.15f,
                    fontWeight = FontWeight.Medium,
                    style = MaterialTheme.typography.titleLarge.copy(fontFeatureSettings = "tnum"),
                    maxLines = 1,
                    softWrap = false
                )
                if (showTotal) {
                    Text(
                        text = stringResource(
                            Res.string.editor_video_of_total,
                            formatTransportTimecode(duration)
                        ),
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontFeatureSettings = "tnum"
                        ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1
                    )
                }
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                VideoTransportStep(
                    icon = VideoGlyphs.SkipPrevious,
                    label = stringResource(Res.string.editor_video_prev_frame),
                    enabled = hasFrames,
                    shape = RoundedCornerShape(
                        topStart = 16.dp, bottomStart = 16.dp,
                        topEnd = 8.dp, bottomEnd = 8.dp
                    ),
                    tag = TAG_VIDEO_PREV_FRAME,
                    onClick = { step(-sequence!!.frameRate.ticksPerFrame) }
                )
                VideoPlayButton(
                    playing = state.playing,
                    enabled = hasFrames && !state.opening,
                    size = if (big) 64.dp else 48.dp,
                    onClick = { if (state.playing) actions.pause() else actions.play() }
                )
                VideoTransportStep(
                    icon = VideoGlyphs.SkipNext,
                    label = stringResource(Res.string.editor_video_next_frame),
                    enabled = hasFrames,
                    shape = RoundedCornerShape(
                        topStart = 8.dp, bottomStart = 8.dp,
                        topEnd = 16.dp, bottomEnd = 16.dp
                    ),
                    tag = TAG_VIDEO_NEXT_FRAME,
                    onClick = { step(sequence!!.frameRate.ticksPerFrame) }
                )
            }
            Row(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                VideoPlainIconButton(
                    icon = if (state.muted) VideoGlyphs.VolumeOff else VideoGlyphs.VolumeUp,
                    label = stringResource(
                        if (state.muted) Res.string.editor_video_unmute
                        else Res.string.editor_video_mute
                    ),
                    onClick = { actions.setMuted(!state.muted) },
                    enabled = state.info?.hasAudio == true,
                    selected = state.muted,
                    modifier = Modifier.testTag(TAG_VIDEO_MUTE)
                )
                if (showFocus) {
                    VideoPlainIconButton(
                        icon = if (canvasFocused) VideoGlyphs.CloseFullscreen
                        else VideoGlyphs.OpenInFull,
                        label = stringResource(
                            if (canvasFocused) Res.string.editor_video_exit_focus
                            else Res.string.editor_video_focus_preview
                        ),
                        onClick = onToggleFocus,
                        selected = canvasFocused,
                        modifier = Modifier.testTag(TAG_VIDEO_FOCUS)
                    )
                }
            }
        }
    }
}

@Composable
private fun VideoTransportStep(
    icon: ImageVector,
    label: String,
    enabled: Boolean,
    shape: Shape,
    tag: String,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(VideoStudio.Control)
            .clip(shape)
            .background(videoSlabColor())
            .clickable(enabled = enabled, role = Role.Button, onClickLabel = label, onClick = onClick)
            .semantics { contentDescription = label }
            .testTag(tag),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = if (enabled) 1f else 0.38f),
            modifier = Modifier.size(24.dp)
        )
    }
}

/**
 * The primary transport control: a primary cookie at rest that springs
 * into a tertiary soft square while playing.
 */
@Composable
internal fun VideoPlayButton(
    playing: Boolean,
    enabled: Boolean,
    size: Dp,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val label = stringResource(
        if (playing) Res.string.editor_video_pause else Res.string.editor_video_play
    )
    val morph by animateFloatAsState(
        if (playing) 1f else 0f,
        spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessLow),
        label = "playMorph"
    )
    val container by animateColorAsState(
        when {
            !enabled -> videoSlabColor()
            playing -> MaterialTheme.colorScheme.tertiary
            else -> MaterialTheme.colorScheme.primary
        },
        label = "playContainer"
    )
    val content = when {
        !enabled -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
        playing -> MaterialTheme.colorScheme.onTertiary
        else -> MaterialTheme.colorScheme.onPrimary
    }
    Box(
        modifier = modifier
            .size(size)
            .clip(VideoMorphShape(morph))
            .background(container)
            .clickable(enabled = enabled, role = Role.Button, onClickLabel = label, onClick = onClick)
            .semantics {
                contentDescription = label
                selected = playing
            }
            .testTag(TAG_VIDEO_PLAY),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = if (playing) VideoGlyphs.Pause else VideoGlyphs.Play,
            contentDescription = null,
            tint = content,
            modifier = Modifier.size(if (size >= 56.dp) 32.dp else 28.dp)
        )
    }
}

// ---------------------------------------------------------------------------
// Tool panel
// ---------------------------------------------------------------------------

/**
 * The panel header's Reset, published by the active tool surface: the
 * surface decides whether anything differs from its defaults and what a
 * reset writes, the panel only paints the action.
 */
@Stable
internal class VideoPanelResetHost {
    var available by mutableStateOf(false)
    var enabled by mutableStateOf(false)
    var onReset: () -> Unit by mutableStateOf({})
}

internal val LocalVideoPanelReset = staticCompositionLocalOf<VideoPanelResetHost?> { null }

/** Publishes this surface's Reset to the hosting panel header, if any. */
@Composable
internal fun PublishVideoPanelReset(enabled: Boolean, onReset: () -> Unit) {
    val host = LocalVideoPanelReset.current ?: return
    SideEffect {
        host.available = true
        host.enabled = enabled
        host.onReset = onReset
    }
    DisposableEffect(host) {
        onDispose {
            host.available = false
            host.enabled = false
        }
    }
}

/**
 * One tool's floating card: the title with its subject (the clip or lane
 * it edits), the Reset the surface publishes, an optional trailing slot
 * (the mixer's links) and the scrolling body. [solid] drops the glass for
 * the opaque inspector card of tablet landscape.
 */
@Composable
internal fun VideoToolPanel(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    titleSize: TextUnit = 18.sp,
    contentPadding: Dp = 12.dp,
    solid: Boolean = false,
    scroll: Boolean = true,
    trailing: (@Composable RowScope.() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val shape = RoundedCornerShape(VideoStudio.CardCorner)
    val reset = remember { VideoPanelResetHost() }
    Column(
        modifier = modifier
            .clip(shape)
            .then(
                if (solid) Modifier.background(videoToolCardColor())
                else Modifier.videoToolCard(shape)
            )
            .then(if (scroll) Modifier.verticalScroll(rememberScrollState()) else Modifier)
            .padding(contentPadding)
            .testTag(TAG_VIDEO_PANEL),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 44.dp)
                .padding(start = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = title,
                    fontSize = titleSize,
                    lineHeight = 28.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                if (subtitle != null) {
                    Text(
                        text = subtitle,
                        fontSize = 14.sp,
                        lineHeight = 26.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                }
            }
            if (reset.available) {
                val label = stringResource(Res.string.editor_video_reset)
                val tint = if (reset.enabled) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.outline
                Row(
                    modifier = Modifier
                        .heightIn(min = 44.dp)
                        .clip(CircleShape)
                        .clickable(
                            enabled = reset.enabled,
                            role = Role.Button,
                            onClickLabel = label
                        ) { reset.onReset() }
                        .testTag(TAG_VIDEO_PANEL_RESET)
                        .padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(VideoGlyphs.Reset, null, tint = tint, modifier = Modifier.size(18.dp))
                    Text(label, style = MaterialTheme.typography.labelLarge, color = tint)
                }
            }
            trailing?.invoke(this)
        }
        androidx.compose.runtime.CompositionLocalProvider(LocalVideoPanelReset provides reset) {
            content()
        }
    }
}

// ---------------------------------------------------------------------------
// Panel controls
// ---------------------------------------------------------------------------

/**
 * A parameter selector chip: icon, name and current value on a pill; the
 * chosen one wears a 2 dp primary ring over an 8 % primary wash.
 */
@Composable
internal fun VideoValueChip(
    label: String,
    value: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    tag: String? = null
) {
    val background by animateColorAsState(
        if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
        else videoSlabColor(),
        label = "chipBackground"
    )
    val ring by animateColorAsState(
        if (selected) MaterialTheme.colorScheme.primary else Color.Transparent,
        label = "chipRing"
    )
    Row(
        modifier = modifier
            .heightIn(min = 48.dp)
            .clip(CircleShape)
            .background(background)
            .border(2.dp, ring, CircleShape)
            .clickable(enabled = enabled, role = Role.Tab, onClickLabel = label, onClick = onClick)
            .semantics { this.selected = selected }
            .then(if (tag != null) Modifier.testTag(tag) else Modifier)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        val ink = MaterialTheme.colorScheme.onSurface.copy(alpha = if (enabled) 1f else 0.38f)
        if (icon != null) Icon(icon, null, tint = ink, modifier = Modifier.size(18.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = ink,
            maxLines = 1,
            softWrap = false
        )
        Text(
            text = value,
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Normal),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            softWrap = false
        )
    }
}

/**
 * A tonal action in a panel's action grid — 52 dp, icon plus label; an
 * "on" toggle springs to a pill in the primary container, a destructive
 * one wears the error ink.
 */
@Composable
internal fun VideoTonalAction(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    on: Boolean? = null,
    danger: Boolean = false
) {
    val active = on == true
    val corner by animateDpAsState(
        if (active) 26.dp else 14.dp,
        spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMediumLow),
        label = "tonalCorner"
    )
    val container = when {
        active -> MaterialTheme.colorScheme.primaryContainer
        danger -> MaterialTheme.colorScheme.error.copy(alpha = 0.16f)
        else -> videoSlabColor()
    }
    val ink = when {
        !enabled -> MaterialTheme.colorScheme.outline
        active -> MaterialTheme.colorScheme.onPrimaryContainer
        danger -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.onSurface
    }
    Row(
        modifier = modifier
            .heightIn(min = 52.dp)
            .clip(RoundedCornerShape(corner))
            .background(container)
            .then(
                if (on != null) Modifier.semantics { selected = active } else Modifier
            )
            .clickable(enabled = enabled, role = Role.Button, onClickLabel = label, onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally)
    ) {
        if (icon != null) Icon(icon, null, tint = ink, modifier = Modifier.size(20.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = ink,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/**
 * A two-line option row: icon, title and supporting text on a tonal
 * slab; the chosen row springs to a pill in the primary container.
 * [trailing] adds square-joined side actions (Layers' Hide / Lock).
 */
@Composable
internal fun VideoOptionRow(
    title: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    supporting: String? = null,
    selected: Boolean = false,
    enabled: Boolean = true,
    onClick: (() -> Unit)? = null,
    role: Role = Role.Button,
    rowModifier: Modifier = Modifier,
    trailing: (@Composable RowScope.() -> Unit)? = null
) {
    val corner by animateDpAsState(
        if (selected) 24.dp else 16.dp,
        spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMediumLow),
        label = "rowCorner"
    )
    val container by animateColorAsState(
        if (selected) MaterialTheme.colorScheme.primaryContainer
        else videoSlabColor(),
        label = "rowContainer"
    )
    val ink = when {
        !enabled -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
        selected -> MaterialTheme.colorScheme.onPrimaryContainer
        else -> MaterialTheme.colorScheme.onSurface
    }
    val subInk = if (selected) MaterialTheme.colorScheme.onPrimaryContainer
    else MaterialTheme.colorScheme.onSurfaceVariant
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(VideoStudio.GroupGap)
    ) {
        Row(
            modifier = Modifier
                .weight(1f)
                .heightIn(min = 60.dp)
                .clip(RoundedCornerShape(corner))
                .background(container)
                .then(
                    if (onClick != null) {
                        Modifier.clickable(
                            enabled = enabled,
                            role = role,
                            onClickLabel = title,
                            onClick = onClick
                        )
                    } else Modifier
                )
                .semantics { this.selected = selected }
                .then(rowModifier)
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (icon != null) Icon(icon, null, tint = ink, modifier = Modifier.size(24.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelLarge.copy(fontSize = 14.sp),
                    color = ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (supporting != null) {
                    Text(
                        text = supporting,
                        style = MaterialTheme.typography.bodySmall,
                        color = subInk
                    )
                }
            }
        }
        trailing?.invoke(this)
    }
}

/** A square-joined side action beside a [VideoOptionRow]. */
@Composable
internal fun RowScope.VideoRowSideAction(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    shape: Shape,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    on: Boolean? = null,
    tint: Color = MaterialTheme.colorScheme.onSurface
) {
    Box(
        modifier = modifier
            .width(48.dp)
            .heightIn(min = 60.dp)
            .clip(shape)
            .background(videoSlabColor())
            .then(
                if (on != null) {
                    Modifier.toggleable(
                        value = on,
                        enabled = enabled,
                        role = Role.Switch,
                        onValueChange = { onClick() }
                    )
                } else {
                    Modifier.clickable(
                        enabled = enabled,
                        role = Role.Button,
                        onClickLabel = label,
                        onClick = onClick
                    )
                }
            )
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            icon, null,
            tint = if (enabled) tint else MaterialTheme.colorScheme.outline,
            modifier = Modifier.size(22.dp)
        )
    }
}

/**
 * A switch row — the whole row is one toggle target, the painted switch
 * is decorative so TalkBack and the keyboard meet exactly one control.
 */
@Composable
internal fun VideoStudioSwitchRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    supporting: String? = null,
    enabled: Boolean = true,
    tag: String? = null
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clip(RoundedCornerShape(16.dp))
            .toggleable(
                value = checked,
                enabled = enabled,
                role = Role.Switch,
                onValueChange = onCheckedChange
            )
            .then(if (tag != null) Modifier.testTag(tag) else Modifier)
            .padding(start = 12.dp, end = 8.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        val ink = MaterialTheme.colorScheme.onSurface.copy(alpha = if (enabled) 1f else 0.38f)
        Column(Modifier.weight(1f)) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge.copy(fontSize = 14.sp),
                color = ink
            )
            if (supporting != null) {
                Text(
                    text = supporting,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Switch(
            checked = checked,
            onCheckedChange = null,
            enabled = enabled,
            colors = SwitchDefaults.colors(
                uncheckedTrackColor = MaterialTheme.colorScheme.outlineVariant,
                uncheckedThumbColor = MaterialTheme.colorScheme.onSurfaceVariant,
                uncheckedBorderColor = Color.Transparent
            )
        )
    }
}

/** The panel's primary action — a 56 dp primary pill with a glyph. */
@Composable
internal fun VideoPrimaryAction(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    height: Dp = 56.dp,
    textSize: TextUnit = 16.sp
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = height)
            .clip(CircleShape)
            .background(
                if (enabled) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
            )
            .clickable(enabled = enabled, role = Role.Button, onClickLabel = label, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)
    ) {
        val ink = if (enabled) MaterialTheme.colorScheme.onPrimary
        else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
        if (icon != null) Icon(icon, null, tint = ink, modifier = Modifier.size(22.dp))
        Text(
            text = label,
            fontSize = textSize,
            fontWeight = FontWeight.Medium,
            color = ink,
            maxLines = 1
        )
    }
}

/** A 48 dp text action pill — the sheets' Hide / Done / Try again. */
@Composable
internal fun VideoTextAction(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    filled: Boolean = false,
    tonal: Boolean = false,
    enabled: Boolean = true
) {
    val container = when {
        filled -> MaterialTheme.colorScheme.primary
        tonal -> videoSlabColor()
        else -> Color.Transparent
    }
    val ink = when {
        !enabled -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
        filled -> MaterialTheme.colorScheme.onPrimary
        tonal -> MaterialTheme.colorScheme.onSurface
        else -> MaterialTheme.colorScheme.primary
    }
    Box(
        modifier = modifier
            .heightIn(min = 48.dp)
            .clip(CircleShape)
            .background(container)
            .clickable(enabled = enabled, role = Role.Button, onClickLabel = label, onClick = onClick)
            .padding(horizontal = if (filled || tonal) 20.dp else 16.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = ink, maxLines = 1)
    }
}

// ---------------------------------------------------------------------------
// Sheets
// ---------------------------------------------------------------------------

/**
 * The studio sheet: a bottom sheet with its handle on phones, a centred
 * 28 dp card on wider windows — both on the container role with the
 * 28 sp title. Body content scrolls inside.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun VideoStudioSheet(
    title: String,
    phone: Boolean,
    onDismiss: () -> Unit,
    tag: String,
    modifier: Modifier = Modifier,
    width: Dp = 520.dp,
    content: @Composable ColumnScope.() -> Unit
) {
    val body: @Composable ColumnScope.() -> Unit = {
        Text(
            text = title,
            fontSize = 28.sp,
            lineHeight = 36.sp,
            color = MaterialTheme.colorScheme.onSurface
        )
        content()
    }
    if (phone) {
        ModalBottomSheet(
            onDismissRequest = onDismiss,
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
            contentColor = MaterialTheme.colorScheme.onSurface,
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
        ) {
            Column(
                modifier = modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(start = 16.dp, end = 16.dp, bottom = 32.dp)
                    .testTag(tag),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                content = body
            )
        }
    } else {
        // The platform default narrows the card on short windows, which
        // wraps the catalogue's segment labels.
        BasicAlertDialog(
            onDismissRequest = onDismiss,
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Surface(
                shape = RoundedCornerShape(28.dp),
                color = MaterialTheme.colorScheme.surfaceContainer,
                contentColor = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.widthIn(max = width)
            ) {
                Column(
                    modifier = modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(24.dp)
                        .testTag(tag),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    content = body
                )
            }
        }
    }
}
