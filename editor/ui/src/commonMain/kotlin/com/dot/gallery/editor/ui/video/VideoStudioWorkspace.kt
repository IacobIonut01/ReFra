package com.dot.gallery.editor.ui.video

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dot.gallery.editor.core.model.VideoTrackKind
import com.dot.gallery.editor.render.video.VideoEditorState
import com.dot.gallery.editor.render.video.VideoExportResult
import com.dot.gallery.editor.render.video.VideoInsertion
import com.dot.gallery.editor.ui.ToolActionBar
import com.dot.gallery.editor.ui.ToolHeaderActions
import com.dot.gallery.editor.ui.EditorToolGroup
import com.dot.gallery.editor.ui.EditorToolId
import com.dot.gallery.editor.ui.EditorToolIds
import com.dot.gallery.editor.ui.TAG_VIDEO_REGION_HEADER
import com.dot.gallery.editor.ui.TAG_VIDEO_REGION_PREVIEW
import com.dot.gallery.editor.ui.TAG_VIDEO_REGION_TIMELINE
import com.dot.gallery.editor.ui.TAG_VIDEO_REGION_TRANSPORT
import com.dot.gallery.editor.ui.TAG_VIDEO_V3_INLINE_INSPECTOR
import com.dot.gallery.editor.ui.generated.resources.Res
import com.dot.gallery.editor.ui.generated.resources.editor_undo
import com.dot.gallery.editor.ui.generated.resources.editor_video_add_media
import com.dot.gallery.editor.ui.generated.resources.editor_video_clip_label
import com.dot.gallery.editor.ui.generated.resources.editor_video_close_editor
import com.dot.gallery.editor.ui.generated.resources.editor_video_close_tool
import com.dot.gallery.editor.ui.generated.resources.editor_video_reset
import com.dot.gallery.editor.ui.generated.resources.editor_video_empty_sequence
import com.dot.gallery.editor.ui.generated.resources.editor_video_exit_focus
import org.jetbrains.compose.resources.stringResource

/** Which presentation a window gets: the phone stack or the one large layout. */
internal enum class VideoStudioForm { Phone, Large }

/**
 * Compact windows get the phone stack; every other window, landscape
 * phones, tablets and desktop windows alike, gets the large layout.
 */
internal fun videoStudioFormFor(
    layout: VideoLayout,
    widthDp: Float,
    heightDp: Float = 0f
): VideoStudioForm =
    if (layout == VideoLayout.Compact) VideoStudioForm.Phone else VideoStudioForm.Large

/** Which lanes a tool's timeline strip keeps on a phone. */
internal enum class VideoLaneMode { All, Video, Audio, None }

internal fun videoToolGroup(tool: EditorToolId): EditorToolGroup? =
    videoToolEntries().firstOrNull { it.descriptor.id == tool }?.descriptor?.group

internal fun videoToolLaneMode(tool: EditorToolId?): VideoLaneMode = when {
    tool == null -> VideoLaneMode.All
    tool == EditorToolIds.VideoLayers || tool == EditorToolIds.VideoAddMedia ||
        tool == EditorToolIds.VideoMixer || tool == EditorToolIds.VideoRouting ||
        tool == EditorToolIds.VideoDucking || tool == EditorToolIds.VideoEq ||
        tool == EditorToolIds.VideoVoice -> VideoLaneMode.None
    videoToolGroup(tool) == EditorToolGroup.Audio -> VideoLaneMode.Audio
    else -> VideoLaneMode.Video
}

/**
 * Tools whose result is judged by ear or by numbers rather than on the
 * picture: they get the space and the player docks small. Everything else
 * (trim, arrange, transform, colour, layers) is preview-first — the player
 * keeps the largest box the window allows.
 */
private val TOOL_FIRST = setOf(
    EditorToolIds.VideoMixer, EditorToolIds.VideoEq, EditorToolIds.VideoDucking,
    EditorToolIds.VideoRouting, EditorToolIds.VideoClipAudio, EditorToolIds.VideoVoice,
    EditorToolIds.VideoAddMedia, EditorToolIds.VideoAddAudio
)

internal fun videoToolIsToolFirst(tool: EditorToolId?): Boolean = tool in TOOL_FIRST

/** The dock destination a tool or open sheet lights. */
internal fun videoDockItemFor(tool: EditorToolId?, sheetGroup: EditorToolGroup?): VideoDockItem? {
    if (sheetGroup != null) {
        return if (sheetGroup == EditorToolGroup.Audio) VideoDockItem.Audio else VideoDockItem.Tools
    }
    return when {
        tool == null -> null
        tool == EditorToolIds.VideoAddMedia -> VideoDockItem.Media
        tool == EditorToolIds.VideoLayers -> VideoDockItem.Layers
        videoToolGroup(tool) == EditorToolGroup.Audio -> VideoDockItem.Audio
        else -> VideoDockItem.Tools
    }
}

/**
 * Everything one studio composition needs — state, decoded media, the
 * workspace view state and the callbacks the content wires to the
 * session and its own sheets.
 */
internal class VideoStudioArgs(
    val state: VideoEditorState,
    val actions: VideoEditorActions,
    val form: VideoStudioForm,
    val activeTool: EditorToolId?,
    val sheetGroup: EditorToolGroup?,
    val moreOpen: Boolean,
    val canvasFocused: Boolean,
    val previewBitmap: ImageBitmap?,
    val thumbnails: Map<String, ImageBitmap>,
    val viewport: VideoTimelineViewport,
    val previewFocus: FocusRequester,
    val exportEnabled: Boolean,
    val onOpenExport: ((VideoExportResult) -> Unit)?,
    val onClose: () -> Unit,
    val onExport: () -> Unit,
    val onMore: () -> Unit,
    val onCloseTool: () -> Unit,
    val onSelectTool: (EditorToolId) -> Unit,
    val onDock: (VideoDockItem) -> Unit,
    val onToggleFocus: () -> Unit,
    val onAddMedia: () -> Unit,
    val onShowHistory: () -> Unit = {},
    /** The dock shows the audio tools in place of its four destinations. */
    val audioDock: Boolean = false,
    val onAudioBack: () -> Unit = {},
    val onSettings: () -> Unit = {}
) {
    val hasClips: Boolean
        get() = state.sequence?.tracks?.any { it.clips.isNotEmpty() } == true

    /** The tool card shows only while a tool is open, on every form. */
    val panelTool: EditorToolId?
        get() = activeTool

    val showPanel: Boolean
        get() = panelTool != null && (hasClips || panelTool == EditorToolIds.VideoAddMedia)
}

/** The studio workspace for the window's [VideoStudioArgs.form]. */
@Composable
internal fun VideoStudioWorkspace(args: VideoStudioArgs) {
    // One Reset slot per open tool: the tool publishes it, the tool bar shows it.
    val resetHost = remember(args.activeTool) { VideoPanelResetHost() }
    androidx.compose.runtime.CompositionLocalProvider(LocalVideoPanelReset provides resetHost) {
    Box(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
    ) {
        if (args.canvasFocused && args.hasClips) {
            VideoFocusStudio(args)
        } else {
            when (args.form) {
                VideoStudioForm.Phone -> VideoPhoneStudio(args)
                VideoStudioForm.Large -> VideoLargeStudio(args)
            }
        }
    }
    }
}

// ---------------------------------------------------------------------------
// Phone portrait
// ---------------------------------------------------------------------------

@Composable
private fun VideoPhoneStudio(args: VideoStudioArgs) {
    val tool = args.activeTool
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val heightDp = maxHeight
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp)
                .padding(bottom = 12.dp)
        ) {
            StudioHeader(args, Modifier.padding(top = 4.dp, bottom = 4.dp))
            if (tool != null) StudioToolBar(args)
            if (!args.hasClips) {
                VideoStudioStage(
                    args = args,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(vertical = 4.dp)
                )
                if (args.showPanel) {
                    StudioPanel(args, Modifier.fillMaxWidth().weight(1.4f).padding(bottom = 8.dp))
                }
            } else if (tool == null) {
                val natural = videoStudioTimelineHeight(
                    args.state.sequence, VideoLaneMetrics.PhoneHome, showCommands = true
                )
                VideoStudioStage(
                    args,
                    Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .heightIn(min = 120.dp)
                        .padding(top = 4.dp)
                )
                StudioTransport(args, big = true, Modifier.padding(vertical = 4.dp))
                VideoStudioTimeline(
                    state = args.state,
                    actions = args.actions,
                    thumbnails = args.thumbnails,
                    viewport = args.viewport,
                    metrics = VideoLaneMetrics.PhoneHome,
                    showCommands = true,
                    onOpenTrim = { args.onSelectTool(EditorToolIds.VideoTrim) },
                    onOpenArrange = { args.onSelectTool(EditorToolIds.VideoArrange) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = maxOf(natural, heightDp * 0.3f).coerceAtMost(heightDp * 0.52f))
                        .testTag(TAG_VIDEO_REGION_TIMELINE)
                )
                Spacer(Modifier.height(8.dp))
            } else {
                // Preview-first tools keep a watchable frame; tool-first ones
                // dock it small so the card gets the height.
                val stageHeight = if (videoToolIsToolFirst(tool)) {
                    (heightDp * 0.12f).coerceIn(90.dp, 150.dp)
                } else {
                    (heightDp * 0.22f).coerceIn(140.dp, 300.dp)
                }
                VideoStudioStage(
                    args,
                    Modifier
                        .fillMaxWidth()
                        .height(stageHeight)
                        .padding(top = 4.dp)
                )
                StudioTransport(args, big = false, Modifier.padding(vertical = 4.dp))
                StudioToolLanes(args, VideoLaneMetrics.PhoneTool, Modifier.padding(bottom = 8.dp))
                StudioPanel(args, Modifier.fillMaxWidth().weight(1f).padding(bottom = 8.dp))
            }
            if (args.hasClips && tool == null) Spacer(Modifier.weight(0.001f))
            StudioDock(args, Modifier)
        }
    }
}

/** A phone tool's lane strip — only the lanes the tool acts on. */
@Composable
private fun StudioToolLanes(
    args: VideoStudioArgs,
    metrics: VideoLaneMetrics,
    modifier: Modifier = Modifier
) {
    val mode = videoToolLaneMode(args.activeTool)
    if (mode == VideoLaneMode.None || !args.hasClips) return
    VideoStudioTimeline(
        state = args.state,
        actions = args.actions,
        thumbnails = args.thumbnails,
        viewport = args.viewport,
        metrics = metrics,
        showCommands = false,
        showVideo = mode == VideoLaneMode.Video || mode == VideoLaneMode.All,
        showAudio = mode == VideoLaneMode.Audio || mode == VideoLaneMode.All,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(max = 200.dp)
            .testTag(TAG_VIDEO_REGION_TIMELINE)
    )
}

// ---------------------------------------------------------------------------
// Large windows
// ---------------------------------------------------------------------------

@Composable
private fun VideoLargeStudio(args: VideoStudioArgs) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        // A phone on its side is too short to stack the preview over the
        // timeline: it gets the side-by-side form.
        if (maxHeight < VideoShortWindow) {
            VideoShortLargeStudio(args, maxWidth)
            return@BoxWithConstraints
        }
        val widthDp = maxWidth
        val heightDp = maxHeight
        val toolFirst = videoToolIsToolFirst(args.activeTool)
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            StudioDock(args, Modifier, vertical = true)
            Column(Modifier.weight(1f).fillMaxHeight()) {
                StudioHeader(args, Modifier.height(48.dp))
                if (!args.hasClips) {
                    VideoStudioStage(args, Modifier.fillMaxWidth().weight(1f).padding(top = 4.dp))
                } else {
                    VideoLandscapeWorkColumn(args, heightDp, compactStage = toolFirst)
                }
            }
            if (args.showPanel) {
                // The tool card: the tool bar on top, then its controls.
                Column(
                    Modifier
                        .width(
                            if (toolFirst) (widthDp * 0.6f).coerceIn(320.dp, 600.dp)
                            else (widthDp * 0.385f).coerceIn(260.dp, 360.dp)
                        )
                        .fillMaxHeight()
                ) {
                    StudioPanel(args, Modifier.fillMaxWidth().weight(1f), padding = 12.dp)
                }
            } else if (args.sheetGroup != null) {
                StudioCataloguePane(
                    args,
                    Modifier.width((widthDp * 0.385f).coerceIn(300.dp, 400.dp)).fillMaxHeight()
                )
            }
        }
    }
}


/** Below this height the large layout puts the preview beside the timeline. */
private val VideoShortWindow = 480.dp

/**
 * The large layout on a short window (a phone on its side): the header
 * across the top, then the dock rail, the preview with its transport, and
 * a side pane. With no tool open the pane is the timeline; with a tool it
 * is the tool bar, the lanes the tool acts on and the tool's controls.
 */
@Composable
private fun VideoShortLargeStudio(args: VideoStudioArgs, widthDp: Dp) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(8.dp)
    ) {
        StudioHeader(args, Modifier.height(48.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            StudioDock(args, Modifier, vertical = true)
            Column(Modifier.weight(1f).fillMaxHeight()) {
                VideoStudioStage(args, Modifier.fillMaxWidth().weight(1f))
                if (args.hasClips) {
                    StudioTransport(
                        args, big = false, Modifier,
                        timeSize = 18, showTotal = false, showFocus = true
                    )
                }
            }
            if (args.hasClips || args.showPanel || args.sheetGroup != null) {
                Column(
                    Modifier
                        .width((widthDp * 0.42f).coerceIn(300.dp, 440.dp))
                        .fillMaxHeight()
                ) {
                    if (args.sheetGroup != null && !args.showPanel) {
                        StudioCataloguePane(args, Modifier.fillMaxWidth().weight(1f))
                    } else if (args.showPanel) {
                        // No lane strip here: the short pane keeps its height for the controls.
                        StudioPanel(args, Modifier.fillMaxWidth().weight(1f), padding = 12.dp)
                    } else {
                        VideoStudioTimeline(
                            state = args.state,
                            actions = args.actions,
                            thumbnails = args.thumbnails,
                            viewport = args.viewport,
                            metrics = VideoLaneMetrics.PhoneTool,
                            showCommands = true,
                            onOpenTrim = { args.onSelectTool(EditorToolIds.VideoTrim) },
                            onOpenArrange = { args.onSelectTool(EditorToolIds.VideoArrange) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                                .testTag(TAG_VIDEO_REGION_TIMELINE)
                        )
                    }
                }
            }
        }
    }
}
/**
 * The phone-landscape work column scrolls as one: a scroll that starts on
 * the timeline first shrinks the preview (16:9 kept, frame by frame) down
 * to a docked thumbnail, handing its height to the timeline, then scrolls
 * the lanes; scrolling back down restores the preview once the lanes are at
 * their top. [compactStage] starts the column collapsed for tool-first tools.
 */
@Composable
private fun ColumnScope.VideoLandscapeWorkColumn(
    args: VideoStudioArgs,
    windowHeight: Dp,
    compactStage: Boolean
) {
    val density = LocalDensity.current
    val stageMax = windowHeight * 0.48f
    val stageMin = VideoLandscapeStageMin
    val rangePx = with(density) { (stageMax - stageMin).coerceAtLeast(0.dp).toPx() }
    val collapse = rememberVideoStageCollapse(rangePx, startCollapsed = compactStage)
    val stageHeight = with(density) {
        (stageMax.toPx() - collapse.offset.coerceAtMost(rangePx)).toDp()
    }
    Column(
        Modifier
            .fillMaxWidth()
            .weight(1f)
            .nestedScroll(collapse)
            .testTag(TAG_VIDEO_LANDSCAPE_COLUMN)
    ) {
        VideoStudioStage(
            args,
            Modifier
                .fillMaxWidth()
                .height(stageHeight)
                .padding(top = 4.dp)
        )
        StudioTransport(args, big = false, Modifier, timeSize = 18, showTotal = false, showFocus = true)
        VideoStudioTimeline(
            state = args.state,
            actions = args.actions,
            thumbnails = args.thumbnails,
            viewport = args.viewport,
            metrics = VideoLaneMetrics.Tablet,
            // The tall large layout has room for the command row (split,
            // zoom, trim), as the phone home does.
            showCommands = true,
            onOpenTrim = { args.onSelectTool(EditorToolIds.VideoTrim) },
            onOpenArrange = { args.onSelectTool(EditorToolIds.VideoArrange) },
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .testTag(TAG_VIDEO_REGION_TIMELINE)
        )
    }
}

internal const val TAG_VIDEO_LANDSCAPE_COLUMN = "videoEditor.landscapeColumn"

/** The docked thumbnail the landscape preview collapses to. */
internal val VideoLandscapeStageMin = 72.dp

/**
 * Collapses the landscape preview ahead of the timeline's own scroll and
 * restores it after: upward drags shrink [offset] first, downward drags the
 * lanes leave unconsumed grow it back. [offset] is in px, 0..[rangePx].
 */
internal class VideoStageCollapse(rangePx: Float, initial: Float) : NestedScrollConnection {
    var rangePx by mutableFloatStateOf(rangePx)
    var offset by mutableFloatStateOf(initial.coerceIn(0f, rangePx))
        private set

    /** Shrinks (negative [dy]) or grows the preview; returns the consumed part. */
    fun consume(dy: Float): Float {
        val next = (offset - dy).coerceIn(0f, rangePx)
        val used = offset - next
        offset = next
        return used
    }

    override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset =
        if (available.y < 0f) Offset(0f, consume(available.y)) else Offset.Zero

    override fun onPostScroll(
        consumed: Offset,
        available: Offset,
        source: NestedScrollSource
    ): Offset = if (available.y > 0f) Offset(0f, consume(available.y)) else Offset.Zero
}

@Composable
private fun rememberVideoStageCollapse(rangePx: Float, startCollapsed: Boolean): VideoStageCollapse {
    val collapse = remember(startCollapsed) {
        VideoStageCollapse(rangePx, if (startCollapsed) rangePx else 0f)
    }
    collapse.rangePx = rangePx
    return collapse
}

// ---------------------------------------------------------------------------
// Focus
// ---------------------------------------------------------------------------

/**
 * Focus on the preview: the stage takes the window, the transport floats
 * on a glass card at the foot and a glass exit button rides the frame.
 */
@Composable
private fun VideoFocusStudio(args: VideoStudioArgs) {
    val phone = args.form == VideoStudioForm.Phone
    Box(Modifier.fillMaxSize().padding(if (phone) 0.dp else 12.dp)) {
        VideoStudioStage(
            args,
            Modifier
                .fillMaxSize()
                .padding(bottom = if (phone) 120.dp else 0.dp, top = if (phone) 40.dp else 0.dp),
            corner = 16.dp
        )
        val shape = RoundedCornerShape(VideoStudio.CardCorner)
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(start = 12.dp, end = 12.dp, bottom = 16.dp)
                .widthIn(max = 560.dp)
                .fillMaxWidth()
                .clip(shape)
                .videoGlass(shape)
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            StudioTransport(args, big = true, Modifier, timeSize = if (phone) 18 else 28)
        }
    }
}

// ---------------------------------------------------------------------------
// Shared pieces
// ---------------------------------------------------------------------------

@Composable
private fun StudioHeader(args: VideoStudioArgs, modifier: Modifier = Modifier, title: Boolean = false) {
    val phoneTool = args.activeTool != null
    val toolTitle = args.activeTool?.let { stringResource(videoToolDescriptor(it).title) }
    VideoStudioHeader(
        state = args.state,
        actions = args.actions,
        closeLabel = if (phoneTool && toolTitle != null) {
            stringResource(Res.string.editor_video_close_tool, toolTitle)
        } else {
            stringResource(Res.string.editor_video_close_editor)
        },
        closeTag = if (phoneTool) TAG_VIDEO_CONTEXT_BACK else TAG_VIDEO_BACK,
        onClose = if (phoneTool) args.onCloseTool else args.onClose,
        title = if (title) args.state.info?.displayName.orEmpty() else null,
        exportEnabled = args.exportEnabled,
        onExport = args.onExport,
        moreOpen = args.moreOpen,
        onMore = args.onMore,
        toolOpen = args.activeTool != null,
        onShowHistory = args.onShowHistory,
        toolActions = if (args.form == VideoStudioForm.Large && args.activeTool != null) {
            { StudioHeaderToolActions(args) }
        } else null,
        modifier = modifier.testTag(TAG_VIDEO_REGION_HEADER)
    )
}

/**
 * The open tool's bar under the header, the same one the photo editor
 * uses: the Reset the tool publishes, the tool's name over its subject,
 * and Done. Done closes the tool and keeps its changes.
 */
@Composable
private fun StudioToolBar(args: VideoStudioArgs, modifier: Modifier = Modifier) {
    val tool = args.activeTool ?: return
    val reset = LocalVideoPanelReset.current
    val resetLabel = stringResource(Res.string.editor_video_reset)
    ToolActionBar(
        title = stringResource(videoToolDescriptor(tool).title),
        subtitle = panelSubtitle(args, tool),
        resetLabel = if (reset?.available == true) resetLabel else null,
        resetEnabled = reset?.enabled == true && !args.state.isDrafting,
        onReset = reset?.let { host -> { host.onReset() } },
        resetTag = TAG_VIDEO_PANEL_RESET,
        doneEnabled = true,
        onDone = args.onCloseTool,
        barTag = TAG_VIDEO_TOOL_ACTIONS,
        doneTag = TAG_VIDEO_TOOL_DONE,
        modifier = modifier
    )
}

internal const val TAG_VIDEO_TOOL_ACTIONS = "videoEditor.tool.actions"

@Composable
private fun StudioTransport(
    args: VideoStudioArgs,
    big: Boolean,
    modifier: Modifier = Modifier,
    timeSize: Int = if (big) 20 else 18,
    showTotal: Boolean = big,
    showFocus: Boolean = false
) {
    Box(modifier.fillMaxWidth().testTag(TAG_VIDEO_REGION_TRANSPORT)) {
        VideoStudioTransport(
            state = args.state,
            actions = args.actions,
            big = big,
            timeSize = timeSize.sp,
            showTotal = showTotal,
            showFocus = showFocus,
            canvasFocused = args.canvasFocused,
            onToggleFocus = args.onToggleFocus,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun StudioDock(
    args: VideoStudioArgs,
    modifier: Modifier,
    vertical: Boolean = false
) {
    VideoStudioDock(
        selected = videoDockItemFor(args.activeTool, args.sheetGroup),
        enabled = args.state.sequence != null && !args.state.opening,
        mediaEnabled = args.actions.mediaPickerAvailable,
        onSelect = args.onDock,
        vertical = vertical,
        state = args.state,
        audioMode = args.audioDock,
        activeTool = args.activeTool,
        onSelectTool = args.onSelectTool,
        onAudioBack = args.onAudioBack,
        modifier = modifier
    )
}

/** The panel subject — the clip or lane the tool edits. */
@Composable
private fun panelSubtitle(args: VideoStudioArgs, tool: EditorToolId): String? {
    val state = args.state
    val sequence = state.sequence ?: return null
    return when (videoToolLaneMode(tool)) {
        VideoLaneMode.Video -> {
            val clip = state.selectedVideoClip() ?: return null
            val index = sequence.tracks.flatMap { it.clips }.indexOf(clip)
            stringResource(Res.string.editor_video_clip_label, index + 1)
        }
        VideoLaneMode.Audio -> {
            if (tool == EditorToolIds.VideoAddAudio) return null
            state.selectedAudioClip()?.let { (_, clip) ->
                state.audioSourceInfos[clip.sourceId]?.displayName ?: clip.sourceId
            }
        }
        else -> null
    }
}

@Composable
private fun StudioPanel(
    args: VideoStudioArgs,
    modifier: Modifier,
    titleSize: Int = 18,
    padding: Dp = 12.dp,
    solid: Boolean = false
) {
    val tool = args.panelTool ?: return
    if (!args.showPanel) return
    Box(modifier.testTag(TAG_VIDEO_V3_INLINE_INSPECTOR)) {
    VideoToolPanel(
        // The phone's tool bar names the tool; the large card names it itself.
        title = if (args.form == VideoStudioForm.Large) stringResource(videoToolDescriptor(tool).title) else null,
        subtitle = if (args.form == VideoStudioForm.Large) panelSubtitle(args, tool) else null,
        titleSize = titleSize.sp,
        contentPadding = padding,
        solid = solid,
        scroll = tool != EditorToolIds.VideoLayers,
        trailing = if (tool == EditorToolIds.VideoMixer) {
            { VideoMixerLinks(args.form, args.onSelectTool) }
        } else null,
        modifier = Modifier.fillMaxSize()
    ) {
        VideoToolContent(tool, args.state, args.actions)
    }
    }
}

/** The mixer header's Channels · EQ · Ducking links. */
@Composable
private fun VideoMixerLinks(form: VideoStudioForm, onSelect: (EditorToolId) -> Unit) {
    val links = listOf(
        Triple(EditorToolIds.VideoRouting, VideoGlyphs.Routing, TAG_AUDIO_ACTION_CHANNELS),
        Triple(EditorToolIds.VideoEq, VideoGlyphs.Eq, TAG_AUDIO_ACTION_EQ),
        Triple(EditorToolIds.VideoDucking, VideoGlyphs.Ducking, TAG_AUDIO_ACTION_DUCKING)
    )
    Row(horizontalArrangement = Arrangement.spacedBy(VideoStudio.GroupGap)) {
        links.forEachIndexed { index, (id, icon, tag) ->
            VideoGroupButton(
                icon = icon,
                label = stringResource(videoToolDescriptor(id).title),
                showLabel = form == VideoStudioForm.Large,
                enabled = true,
                shape = videoConnectedShape(index, links.size, VideoStudio.GroupEnd),
                onClick = { onSelect(id) },
                modifier = Modifier.testTag(tag)
            )
        }
    }
}

/**
 * The stage: the decoded frame fitted to the sequence's aspect inside a
 * rounded frame (rounder while playing), status strips at its foot and
 * the focus exit while focused — or the empty-sequence card.
 */
@Composable
internal fun VideoStudioStage(
    args: VideoStudioArgs,
    modifier: Modifier = Modifier,
    corner: Dp? = null
) {
    BoxWithConstraints(modifier.testTag(TAG_VIDEO_REGION_PREVIEW), contentAlignment = Alignment.Center) {
        if (!args.hasClips) {
            VideoEmptyStage(args, Modifier.fillMaxSize())
            return@BoxWithConstraints
        }
        val size = args.state.sequence?.size
        val aspect = if (size != null && size.width > 0 && size.height > 0) {
            size.width.toFloat() / size.height
        } else 16f / 9f
        var w = maxWidth
        var h = w / aspect
        if (h > maxHeight) {
            h = maxHeight
            w = h * aspect
        }
        val radius by animateDpAsState(
            corner ?: if (args.state.playing) 36.dp else VideoStudio.CardCorner,
            label = "stageCorner"
        )
        val shape = RoundedCornerShape(radius)
        Box(
            Modifier
                .size(w, h)
                .clip(shape)
                .background(MaterialTheme.colorScheme.surfaceContainerLowest)
        ) {
            VideoPreview(
                state = args.state,
                actions = args.actions,
                bitmap = args.previewBitmap,
                focusRequester = args.previewFocus,
                compact = args.form == VideoStudioForm.Phone,
                modifier = Modifier.fillMaxSize()
            )
            VideoStatusStrips(
                state = args.state,
                actions = args.actions,
                onOpenExport = args.onOpenExport,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .background(MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.92f))
            )
            if (args.canvasFocused) {
                VideoGlassIconButton(
                    icon = VideoGlyphs.CloseFullscreen,
                    label = stringResource(Res.string.editor_video_exit_focus),
                    onClick = args.onToggleFocus,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(12.dp)
                        .testTag(TAG_VIDEO_FOCUS)
                )
            }
        }
    }
}

/** The empty sequence: a tertiary cookie, the line and Add media / Undo. */
@Composable
private fun VideoEmptyStage(args: VideoStudioArgs, modifier: Modifier) {
    val state = args.state
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(VideoStudio.CardCorner))
            .background(MaterialTheme.colorScheme.surfaceContainerLowest)
            .padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                Modifier
                    .size(72.dp)
                    .clip(VideoCookieShape)
                    .background(MaterialTheme.colorScheme.tertiaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    VideoGlyphs.Movie, null,
                    tint = MaterialTheme.colorScheme.onTertiaryContainer,
                    modifier = Modifier.size(32.dp)
                )
            }
            Text(
                text = stringResource(Res.string.editor_video_empty_sequence),
                fontSize = 22.sp,
                lineHeight = 28.sp
            )
            VideoStatusStrips(state = state, actions = args.actions, onOpenExport = args.onOpenExport)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (args.actions.mediaPickerAvailable) {
                    VideoTextAction(
                        label = stringResource(Res.string.editor_video_add_media),
                        onClick = args.onAddMedia,
                        filled = true,
                        enabled = state.sequence != null && !state.importing && !state.opening,
                        modifier = Modifier.testTag(TAG_VIDEO_EMPTY_ADD)
                    )
                }
                VideoTextAction(
                    label = stringResource(Res.string.editor_undo),
                    onClick = args.actions.undo,
                    tonal = true,
                    enabled = state.canUndo && !state.isDrafting,
                    modifier = Modifier.testTag(TAG_VIDEO_EMPTY_UNDO)
                )
            }
        }
    }
}

/**
 * The tools list in the large layout's side pane, where the photo editor
 * shows its catalogue too. A bottom sheet on a short landscape window
 * would leave room for two rows.
 */
@Composable
private fun StudioCataloguePane(args: VideoStudioArgs, modifier: Modifier) {
    val shape = RoundedCornerShape(VideoStudio.CardCorner)
    Column(
        modifier
            .clip(shape)
            .videoToolCard(shape)
            .padding(top = 8.dp)
            .testTag(TAG_VIDEO_CATALOGUE_SHEET)
    ) {
        VideoCatalogueList(
            state = args.state,
            initialGroup = args.sheetGroup.takeIf { it != EditorToolGroup.Edit },
            activeTool = args.activeTool,
            onSelect = args.onSelectTool,
            onHistory = args.onShowHistory,
            onSettings = args.onSettings,
            // The rail's selected Tools item already names the pane.
            showTitle = false
        )
    }
}

/** Reset and Done in the large header, where the photo editor puts them too. */
@Composable
private fun StudioHeaderToolActions(args: VideoStudioArgs) {
    val reset = LocalVideoPanelReset.current
    ToolHeaderActions(
        resetLabel = if (reset?.available == true) stringResource(Res.string.editor_video_reset) else null,
        resetEnabled = reset?.enabled == true && !args.state.isDrafting,
        onReset = reset?.let { host -> { host.onReset() } },
        resetTag = TAG_VIDEO_PANEL_RESET,
        doneEnabled = true,
        onDone = args.onCloseTool,
        doneTag = TAG_VIDEO_TOOL_DONE
    )
}
