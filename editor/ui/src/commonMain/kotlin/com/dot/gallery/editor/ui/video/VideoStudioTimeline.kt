package com.dot.gallery.editor.ui.video

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dot.gallery.editor.core.model.VideoClip
import com.dot.gallery.editor.core.model.VideoSequence
import com.dot.gallery.editor.core.model.VideoTrack
import com.dot.gallery.editor.core.model.VideoTrackKind
import com.dot.gallery.editor.core.time.EDITOR_TICKS_PER_SECOND
import com.dot.gallery.editor.core.time.EditorTime
import com.dot.gallery.editor.render.video.VideoEditorState
import com.dot.gallery.editor.render.video.VideoThumbnail
import com.dot.gallery.editor.ui.TAG_VIDEO_TIMELINE_EFFECT_PREFIX
import com.dot.gallery.editor.ui.generated.resources.Res
import com.dot.gallery.editor.ui.generated.resources.editor_video_add_audio_track
import com.dot.gallery.editor.ui.generated.resources.editor_video_add_track
import com.dot.gallery.editor.ui.generated.resources.editor_video_add_video_track
import com.dot.gallery.editor.ui.generated.resources.editor_video_arrange
import com.dot.gallery.editor.ui.generated.resources.editor_video_clip_a11y
import com.dot.gallery.editor.ui.generated.resources.editor_video_clip_label
import com.dot.gallery.editor.ui.generated.resources.editor_video_delete
import com.dot.gallery.editor.ui.generated.resources.editor_video_drop_new_track
import com.dot.gallery.editor.ui.generated.resources.editor_video_lane_audio
import com.dot.gallery.editor.ui.generated.resources.editor_video_lane_main
import com.dot.gallery.editor.ui.generated.resources.editor_video_lane_overlay
import com.dot.gallery.editor.ui.generated.resources.editor_video_lane_title
import com.dot.gallery.editor.ui.generated.resources.editor_video_new_audio_track_name
import com.dot.gallery.editor.ui.generated.resources.editor_video_track_delete
import com.dot.gallery.editor.ui.generated.resources.editor_video_track_move_down
import com.dot.gallery.editor.ui.generated.resources.editor_video_track_move_up
import com.dot.gallery.editor.ui.generated.resources.editor_video_track_options
import com.dot.gallery.editor.ui.generated.resources.editor_video_ruler_seek
import com.dot.gallery.editor.ui.generated.resources.editor_video_select_clip
import com.dot.gallery.editor.ui.generated.resources.editor_video_split
import com.dot.gallery.editor.ui.generated.resources.editor_video_timeline_zoom_fit
import com.dot.gallery.editor.ui.generated.resources.editor_video_timeline_zoom_in
import com.dot.gallery.editor.ui.generated.resources.editor_video_timeline_zoom_out
import com.dot.gallery.editor.ui.generated.resources.editor_video_trim_title
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import kotlin.math.roundToInt
import kotlin.math.roundToLong

internal const val TAG_VIDEO_COMMAND_TRIM = "videoEditor.command.trim"
internal const val TAG_VIDEO_COMMAND_ARRANGE = "videoEditor.command.arrange"
internal const val TAG_VIDEO_LANE_PREFIX = "videoEditor.lane."
internal const val TAG_VIDEO_LANE_MOVE_UP_SUFFIX = ".moveUp"
internal const val TAG_VIDEO_LANE_MOVE_DOWN_SUFFIX = ".moveDown"
internal const val TAG_VIDEO_LANE_DELETE_SUFFIX = ".delete"
internal const val TAG_VIDEO_ADD_TRACK = "videoEditor.addTrack"
internal const val TAG_VIDEO_ADD_TRACK_VIDEO = "videoEditor.addTrack.video"
internal const val TAG_VIDEO_ADD_TRACK_AUDIO = "videoEditor.addTrack.audio"
internal const val TAG_VIDEO_NEW_TRACK_ROW = "videoEditor.newTrackRow"

/**
 * Lane heights for one presentation: the roomy phone home, a phone tool's
 * strip, phone landscape and the tablets each give the lanes different
 * room — the proportions (main tallest, overlay and audio shorter) hold.
 */
internal class VideoLaneMetrics(val main: Dp, val overlay: Dp, val audio: Dp) {
    companion object {
        val PhoneHome = VideoLaneMetrics(88.dp, 56.dp, 56.dp)
        val PhoneTool = VideoLaneMetrics(44.dp, 28.dp, 36.dp)
        val PhoneLandscape = VideoLaneMetrics(31.dp, 22.dp, 27.dp)
        val Tablet = VideoLaneMetrics(60.dp, 40.dp, 42.dp)
    }

    fun heightOf(track: VideoTrack): Dp =
        if (track.kind == VideoTrackKind.Main) main else overlay

    /** The Add track row under the lanes — also the new-audio-lane drop target. */
    val addRow: Dp get() = minOf(audio, 32.dp)
}

private const val LANE_GAP_DP = 4
private const val CARD_PADDING_DP = 8
private const val COMMAND_GAP_DP = 6

/**
 * The natural height of a [VideoStudioTimeline] for [sequence] — card
 * padding, the command row when shown, the ruler and every lane it keeps
 * — so a phone can size the card to the lanes the sequence really has.
 */
internal fun videoStudioTimelineHeight(
    sequence: VideoSequence?,
    metrics: VideoLaneMetrics,
    showCommands: Boolean,
    showVideo: Boolean = true,
    showAudio: Boolean = true,
    videoFilter: ((VideoTrack) -> Boolean)? = null
): Dp {
    if (sequence == null) return 0.dp
    var height = (CARD_PADDING_DP * 2).dp + VideoStudio.Ruler
    if (showCommands) height += VideoStudio.Command + COMMAND_GAP_DP.dp
    if (showVideo) {
        sequence.displayLanes()
            .filter { videoFilter?.invoke(it) ?: true }
            .forEach { height += metrics.heightOf(it) + LANE_GAP_DP.dp }
    }
    if (showAudio) {
        sequence.audioTracks.forEach { _ -> height += metrics.audio + LANE_GAP_DP.dp }
    }
    if (showVideo || showAudio) height += metrics.addRow + LANE_GAP_DP.dp
    return height
}

/**
 * The timeline card: on a 28 dp card, the connected command group (Trim,
 * Split, Delete, Arrange) and the zoom group (−, Fit, +) above a fixed
 * 32 dp icon gutter beside one horizontally scrolled body — the ruler,
 * every lane and the playhead share one time origin. Clips are pure
 * fractions of the sequence duration; neighbouring clips join with small
 * corners, the selection springs to a 16 dp pill with a 3 dp primary
 * ring and grows its trim grips. Audio lanes paint their real waveform
 * in the tertiary family.
 *
 * The ruler owns [TAG_VIDEO_TIMELINE] with progress semantics and the
 * scrub gesture; clip tiles are the selection surfaces.
 */
@Composable
internal fun VideoStudioTimeline(
    state: VideoEditorState,
    actions: VideoEditorActions,
    thumbnails: Map<String, ImageBitmap>,
    viewport: VideoTimelineViewport,
    metrics: VideoLaneMetrics,
    modifier: Modifier = Modifier,
    showCommands: Boolean = true,
    commandLabels: Boolean = false,
    showVideo: Boolean = true,
    showAudio: Boolean = true,
    videoFilter: ((VideoTrack) -> Boolean)? = null,
    onOpenTrim: () -> Unit = {},
    onOpenArrange: () -> Unit = {}
) {
    val sequence = state.sequence ?: return
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(VideoStudio.CardCorner))
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .padding(CARD_PADDING_DP.dp),
        verticalArrangement = Arrangement.spacedBy(COMMAND_GAP_DP.dp)
    ) {
        if (showCommands) {
            VideoTimelineCommandRow(
                state = state,
                actions = actions,
                viewport = viewport,
                labels = commandLabels,
                onOpenTrim = onOpenTrim,
                onOpenArrange = onOpenArrange
            )
        }
        val lanes = if (showVideo) {
            sequence.displayLanes().filter { videoFilter?.invoke(it) ?: true }
        } else emptyList()
        val audioTracks = if (showAudio) sequence.audioTracks else emptyList()
        VideoTimelineLanes(
            state = state,
            actions = actions,
            thumbnails = thumbnails,
            viewport = viewport,
            metrics = metrics,
            lanes = lanes,
            audioTracks = audioTracks,
            addVideo = showVideo,
            addAudio = showAudio,
            modifier = Modifier.weight(1f, fill = false)
        )
    }
}

/** The command group on the left and the zoom group on the right. */
@Composable
private fun VideoTimelineCommandRow(
    state: VideoEditorState,
    actions: VideoEditorActions,
    viewport: VideoTimelineViewport,
    labels: Boolean,
    onOpenTrim: () -> Unit,
    onOpenArrange: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val sequence = state.sequence
    val clips = remember(sequence) { sequence?.tracks?.flatMap { it.clips }.orEmpty() }
    val selected = clips.any { it.id == state.selectedClipId }
    val position = state.position
    val canSplit = clips.any {
        position > it.timelineStart && position < it.timelineRange.endExclusive
    }
    val canEdit = sequence != null && !state.opening && !state.isDrafting
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            modifier = Modifier.weight(1f, fill = false).padding(end = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(VideoStudio.GroupGap)
        ) {
            val commands = listOf(
                VideoCommandSpec(
                    VideoGlyphs.Trim, stringResource(Res.string.editor_video_trim_title),
                    canEdit && selected, TAG_VIDEO_COMMAND_TRIM, onOpenTrim
                ),
                VideoCommandSpec(
                    VideoGlyphs.Split, stringResource(Res.string.editor_video_split),
                    canEdit && canSplit, TAG_VIDEO_SPLIT, actions.split
                ),
                VideoCommandSpec(
                    VideoGlyphs.Delete, stringResource(Res.string.editor_video_delete),
                    canEdit && selected, TAG_VIDEO_DELETE, actions.delete
                ),
                VideoCommandSpec(
                    VideoGlyphs.Arrange, stringResource(Res.string.editor_video_arrange),
                    canEdit, TAG_VIDEO_COMMAND_ARRANGE, onOpenArrange
                )
            )
            commands.forEachIndexed { index, command ->
                VideoGroupButton(
                    icon = command.icon,
                    label = command.label,
                    showLabel = labels,
                    enabled = command.enabled,
                    shape = videoConnectedShape(index, commands.size, VideoStudio.GroupEnd),
                    onClick = command.onClick,
                    modifier = Modifier.testTag(command.tag)
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(VideoStudio.GroupGap)) {
            val canZoomOut = viewport.zoom > VideoTimelineViewport.MIN_ZOOM + 0.001f
            val canZoomIn = viewport.zoom < VideoTimelineViewport.MAX_ZOOM - 0.001f
            VideoGroupButton(
                icon = VideoGlyphs.Remove,
                label = stringResource(Res.string.editor_video_timeline_zoom_out),
                enabled = canZoomOut,
                shape = videoConnectedShape(0, 3, VideoStudio.GroupEnd),
                onClick = {
                    scope.launch { zoomAroundPlayhead(state, viewport) { it / 2f } }
                },
                modifier = Modifier.testTag(TAG_VIDEO_ZOOM_OUT)
            )
            val fitLabel = stringResource(Res.string.editor_video_timeline_zoom_fit)
            Box(
                modifier = Modifier
                    .height(VideoStudio.Command)
                    .widthIn(min = 48.dp)
                    .clip(videoConnectedShape(1, 3, VideoStudio.GroupEnd))
                    .background(videoSlabColor())
                    .clickable(role = Role.Button, onClickLabel = fitLabel) {
                        scope.launch { viewport.fit() }
                    }
                    .semantics { contentDescription = fitLabel }
                    .testTag(TAG_VIDEO_ZOOM_FIT)
                    .padding(horizontal = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = videoZoomLabel(viewport.zoom),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1
                )
            }
            VideoGroupButton(
                icon = VideoGlyphs.Add,
                label = stringResource(Res.string.editor_video_timeline_zoom_in),
                enabled = canZoomIn,
                shape = videoConnectedShape(2, 3, VideoStudio.GroupEnd),
                onClick = {
                    scope.launch { zoomAroundPlayhead(state, viewport) { it * 2f } }
                },
                modifier = Modifier.testTag(TAG_VIDEO_ZOOM_IN)
            )
        }
    }
}

private class VideoCommandSpec(
    val icon: ImageVector,
    val label: String,
    val enabled: Boolean,
    val tag: String,
    val onClick: () -> Unit
)

/** One member of a connected 44 dp button group. */
@Composable
internal fun VideoGroupButton(
    icon: ImageVector,
    label: String,
    enabled: Boolean,
    shape: androidx.compose.ui.graphics.Shape,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    showLabel: Boolean = false,
    height: Dp = VideoStudio.Command
) {
    val ink = if (enabled) MaterialTheme.colorScheme.onSurface
    else MaterialTheme.colorScheme.outline
    Row(
        modifier = modifier
            .height(height)
            .widthIn(min = 44.dp)
            .clip(shape)
            .background(videoSlabColor())
            .clickable(enabled = enabled, role = Role.Button, onClickLabel = label, onClick = onClick)
            .semantics { contentDescription = label }
            .padding(horizontal = if (showLabel) 14.dp else 0.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally)
    ) {
        Icon(icon, null, tint = ink, modifier = Modifier.size(20.dp))
        if (showLabel) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = ink,
                maxLines = 1
            )
        }
    }
}

/**
 * The gutter, ruler, lanes and playhead. The gutter sits outside the
 * horizontal scroll so its icons stay put; everything else shares the
 * viewport's scroll origin, and one vertical scroll carries both halves
 * so a gutter icon never drifts from its lane.
 */
@Composable
private fun VideoTimelineLanes(
    state: VideoEditorState,
    actions: VideoEditorActions,
    thumbnails: Map<String, ImageBitmap>,
    viewport: VideoTimelineViewport,
    metrics: VideoLaneMetrics,
    lanes: List<VideoTrack>,
    audioTracks: List<com.dot.gallery.editor.core.model.AudioTrack>,
    addVideo: Boolean,
    addAudio: Boolean,
    modifier: Modifier = Modifier
) {
    val sequence = state.sequence ?: return
    val density = LocalDensity.current
    val gripOwner = remember { mutableStateOf<GripDrag?>(null) }
    val grip = gripOwner.value
    val dragState = remember { mutableStateOf<ClipLaneDrag?>(null) }
    val dragHost = remember { ClipDragHost(dragState) }
    val drag = dragState.value
    val newAudioName = stringResource(
        Res.string.editor_video_new_audio_track_name, sequence.audioTracks.size + 1
    )
    val showAddRow = addVideo || addAudio
    dragHost.slots = remember(lanes, audioTracks, metrics, density, showAddRow, addAudio) {
        timelineLaneSlots(lanes, audioTracks, metrics, density, showAddRow, addAudio)
    }
    dragHost.onDrop = { moved, target ->
        if (moved.kind == LaneDropKind.Audio) {
            actions.moveAudioClip(
                moved.clipId, target.trackId, EditorTime(target.startTicks), newAudioName
            )
        } else if (target.trackId != null) {
            actions.moveVideoClip(moved.clipId, target.trackId, EditorTime(target.startTicks))
        }
    }
    val canDrag = grip == null && !state.isDrafting && !state.opening
    Row(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(LANE_GAP_DP.dp)
    ) {
        VideoLaneGutter(
            state, actions, sequence, lanes, audioTracks, metrics,
            addVideo = addVideo, addAudio = addAudio, newAudioName = newAudioName
        )
        BoxWithConstraints(Modifier.weight(1f)) {
            val viewportWidthPx = with(density) { maxWidth.toPx() }
            if (viewportWidthPx <= 0f || sequence.duration.ticks <= 0L) {
                return@BoxWithConstraints
            }
            viewport.lastViewportWidthPx = viewportWidthPx
            val contentWidthPx = viewportWidthPx * viewport.zoom
            val geometry = TimelineGeometry(
                totalTicks = sequence.duration.ticks,
                contentWidthPx = contentWidthPx,
                scrollPx = viewport.scrollState.value.toFloat()
            )
            val pixelsPerTick = grip?.pixelsPerTick
                ?: contentWidthPx / geometry.totalTicks.toFloat()
            Box(
                Modifier
                    .fillMaxWidth()
                    .horizontalScroll(viewport.scrollState)
                    .width(with(density) { contentWidthPx.toDp() })
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(LANE_GAP_DP.dp)
                ) {
                    VideoStudioRuler(
                        state = state,
                        actions = actions,
                        geometry = geometry,
                        viewportWidthPx = viewportWidthPx,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(VideoStudio.Ruler)
                    )
                    var tileIndex = 0
                    lanes.forEachIndexed { laneIndex, track ->
                        val laneTileStart = tileIndex
                        tileIndex += track.clips.sumOf {
                            VideoThumbnail.sampleTimes(it.sourceRange).size
                        }
                        VideoClipLane(
                            track = track,
                            state = state,
                            actions = actions,
                            thumbnails = thumbnails,
                            geometry = geometry,
                            pixelsPerTick = pixelsPerTick,
                            gripOwner = gripOwner,
                            tileTagStart = laneTileStart,
                            compactBadges = metrics.heightOf(track) < 56.dp,
                            dragHost = dragHost.takeIf { canDrag },
                            slotIndex = laneIndex,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(metrics.heightOf(track))
                        )
                    }
                    audioTracks.forEachIndexed { index, track ->
                        VideoAudioLane(
                            track = track,
                            trackIndex = index,
                            state = state,
                            actions = actions,
                            pixelsPerTick = pixelsPerTick,
                            compact = metrics.audio < 40.dp,
                            dragHost = dragHost.takeIf { canDrag },
                            slotIndex = lanes.size + index,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(metrics.audio)
                        )
                    }
                    if (showAddRow) {
                        VideoNewTrackRow(
                            active = drag?.kind == LaneDropKind.Audio,
                            scrollPx = viewport.scrollState.value,
                            viewportWidthPx = viewportWidthPx,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(metrics.addRow)
                        )
                    }
                }
                ClipDropGhost(dragHost)
                VideoStudioPlayhead(state = state, geometry = geometry)
            }
        }
    }
}

/**
 * The lanes as drag targets, top to bottom in the lane column's pixel
 * space: video lanes (only overlays take drops), audio lanes, then the
 * Add track row, which takes audio drops as a new lane.
 */
private fun timelineLaneSlots(
    lanes: List<VideoTrack>,
    audioTracks: List<com.dot.gallery.editor.core.model.AudioTrack>,
    metrics: VideoLaneMetrics,
    density: Density,
    showAddRow: Boolean,
    addAudio: Boolean
): List<TimelineLaneSlot> = with(density) {
    val gap = LANE_GAP_DP.dp.toPx()
    var top = VideoStudio.Ruler.toPx() + gap
    buildList {
        lanes.forEach { track ->
            val height = metrics.heightOf(track).toPx()
            add(
                TimelineLaneSlot(
                    top, height, track.id,
                    if (track.kind == VideoTrackKind.Overlay) LaneDropKind.Overlay
                    else LaneDropKind.None,
                    track.locked,
                    track.clips.map {
                        Triple(it.id, it.timelineStart.ticks, it.timelineRange.endExclusive.ticks)
                    }
                )
            )
            top += height + gap
        }
        audioTracks.forEach { track ->
            val height = metrics.audio.toPx()
            add(
                TimelineLaneSlot(
                    top, height, track.id, LaneDropKind.Audio, track.locked,
                    track.clips.map {
                        Triple(it.id, it.timelineStart.ticks, it.timelineRange.endExclusive.ticks)
                    }
                )
            )
            top += height + gap
        }
        if (showAddRow) {
            add(
                TimelineLaneSlot(
                    top, metrics.addRow.toPx(), null,
                    if (addAudio) LaneDropKind.Audio else LaneDropKind.None,
                    locked = false, clips = emptyList()
                )
            )
        }
    }
}

/**
 * The lane-column half of the Add track row: empty at rest, and while an
 * audio clip is being dragged an outlined "drop for a new track" target
 * pinned to the visible part of the scrolled content.
 */
@Composable
private fun VideoNewTrackRow(
    active: Boolean,
    scrollPx: Int,
    viewportWidthPx: Float,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    Box(modifier.testTag(TAG_VIDEO_NEW_TRACK_ROW)) {
        if (!active) return@Box
        val shape = RoundedCornerShape(VideoStudio.LaneCorner)
        Box(
            modifier = Modifier
                .offset { IntOffset(scrollPx, 0) }
                .width(with(density) { viewportWidthPx.toDp() })
                .fillMaxHeight()
                .clip(shape)
                .border(1.dp, MaterialTheme.colorScheme.outline, shape),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = stringResource(Res.string.editor_video_drop_new_track),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
        }
    }
}

/**
 * One gutter icon per lane, in the lanes' order and heights, then the
 * Add track button. Every lane but Main opens a menu to move it among
 * the lanes of its kind or delete it.
 */
@Composable
private fun VideoLaneGutter(
    state: VideoEditorState,
    actions: VideoEditorActions,
    sequence: VideoSequence,
    lanes: List<VideoTrack>,
    audioTracks: List<com.dot.gallery.editor.core.model.AudioTrack>,
    metrics: VideoLaneMetrics,
    addVideo: Boolean,
    addAudio: Boolean,
    newAudioName: String
) {
    val editable = !state.opening && !state.isDrafting
    Column(
        modifier = Modifier
            .width(VideoStudio.Gutter)
            .padding(top = VideoStudio.Ruler + LANE_GAP_DP.dp),
        verticalArrangement = Arrangement.spacedBy(LANE_GAP_DP.dp)
    ) {
        lanes.forEach { track ->
            val name = when (track.kind) {
                VideoTrackKind.Main -> stringResource(Res.string.editor_video_lane_main)
                VideoTrackKind.Title -> stringResource(Res.string.editor_video_lane_title)
                else -> stringResource(Res.string.editor_video_lane_overlay)
            }
            val peers = sequence.tracks.filter { it.kind == track.kind }
            val peerIndex = peers.indexOfFirst { it.id == track.id }
            VideoGutterIcon(
                icon = if (track.kind == VideoTrackKind.Main) VideoGlyphs.Movie
                else VideoGlyphs.Overlay,
                name = "$name · ${sequence.laneLabel(track)}",
                dimmed = !track.visible,
                height = metrics.heightOf(track),
                tag = TAG_VIDEO_LANE_PREFIX + sequence.laneLabel(track),
                menu = if (track.kind == VideoTrackKind.Main) null else VideoLaneMenu(
                    enabled = editable && !track.locked,
                    canMoveUp = peerIndex > 0,
                    canMoveDown = peerIndex in 0 until peers.lastIndex,
                    onMove = { delta -> actions.moveTrack(track.id, delta) },
                    onDelete = { actions.removeTrack(track.id) }
                )
            )
        }
        audioTracks.forEachIndexed { index, track ->
            val voice = track.name.contains("voice", ignoreCase = true) ||
                track.clips.any { it.sourceId.contains("voice", ignoreCase = true) }
            val position = sequence.audioTracks.indexOfFirst { it.id == track.id }
            VideoGutterIcon(
                icon = if (voice) VideoGlyphs.Mic else VideoGlyphs.MusicNote,
                name = stringResource(Res.string.editor_video_lane_audio, index + 1) +
                    " · " + audioLaneLabel(index),
                dimmed = track.muted,
                height = metrics.audio,
                tag = TAG_VIDEO_LANE_PREFIX + audioLaneLabel(index),
                menu = VideoLaneMenu(
                    enabled = editable && !track.locked,
                    canMoveUp = position > 0,
                    canMoveDown = position in 0 until sequence.audioTracks.lastIndex,
                    onMove = { delta -> actions.moveAudioTrack(track.id, delta) },
                    onDelete = { actions.removeAudioTrack(track.id) }
                )
            )
        }
        if (addVideo || addAudio) {
            VideoAddTrackButton(
                enabled = editable,
                height = metrics.addRow,
                onAddVideo = if (addVideo) actions.addVideoTrack else null,
                onAddAudio = if (addAudio) {
                    { actions.addAudioTrack(newAudioName) }
                } else null
            )
        }
    }
}

/** A lane's gutter menu: move among same-kind lanes, or delete the lane. */
private class VideoLaneMenu(
    val enabled: Boolean,
    val canMoveUp: Boolean,
    val canMoveDown: Boolean,
    val onMove: (Int) -> Unit,
    val onDelete: () -> Unit
)

@Composable
private fun VideoGutterIcon(
    icon: ImageVector,
    name: String,
    dimmed: Boolean,
    height: Dp,
    tag: String,
    menu: VideoLaneMenu? = null
) {
    var open by remember { mutableStateOf(false) }
    val optionsLabel = stringResource(Res.string.editor_video_track_options, name)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(height)
            .clip(RoundedCornerShape(8.dp))
            .then(
                if (menu != null) {
                    Modifier.clickable(
                        enabled = menu.enabled,
                        role = Role.Button,
                        onClickLabel = optionsLabel
                    ) { open = true }
                } else Modifier
            )
            .semantics { contentDescription = name }
            .testTag(tag),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(
                alpha = if (dimmed) 0.38f else 1f
            ),
            modifier = Modifier.size(if (height < 32.dp) 16.dp else 20.dp)
        )
        if (menu != null) {
            DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                DropdownMenuItem(
                    text = { Text(stringResource(Res.string.editor_video_track_move_up)) },
                    leadingIcon = { Icon(VideoGlyphs.Up, null) },
                    enabled = menu.canMoveUp,
                    onClick = { open = false; menu.onMove(-1) },
                    modifier = Modifier.testTag("$tag$TAG_VIDEO_LANE_MOVE_UP_SUFFIX")
                )
                DropdownMenuItem(
                    text = { Text(stringResource(Res.string.editor_video_track_move_down)) },
                    leadingIcon = { Icon(VideoGlyphs.Down, null) },
                    enabled = menu.canMoveDown,
                    onClick = { open = false; menu.onMove(1) },
                    modifier = Modifier.testTag("$tag$TAG_VIDEO_LANE_MOVE_DOWN_SUFFIX")
                )
                DropdownMenuItem(
                    text = {
                        Text(
                            stringResource(Res.string.editor_video_track_delete),
                            color = MaterialTheme.colorScheme.error
                        )
                    },
                    leadingIcon = {
                        Icon(VideoGlyphs.Delete, null, tint = MaterialTheme.colorScheme.error)
                    },
                    onClick = { open = false; menu.onDelete() },
                    modifier = Modifier.testTag("$tag$TAG_VIDEO_LANE_DELETE_SUFFIX")
                )
            }
        }
    }
}

/** The gutter's Add track button with its Video / Audio track menu. */
@Composable
private fun VideoAddTrackButton(
    enabled: Boolean,
    height: Dp,
    onAddVideo: (() -> Unit)?,
    onAddAudio: (() -> Unit)?
) {
    var open by remember { mutableStateOf(false) }
    val label = stringResource(Res.string.editor_video_add_track)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(height)
            .clip(RoundedCornerShape(8.dp))
            .background(videoSlabColor())
            .clickable(enabled = enabled, role = Role.Button, onClickLabel = label) {
                open = true
            }
            .semantics { contentDescription = label }
            .testTag(TAG_VIDEO_ADD_TRACK),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = VideoGlyphs.Add,
            contentDescription = null,
            tint = if (enabled) MaterialTheme.colorScheme.onSurface
            else MaterialTheme.colorScheme.outline,
            modifier = Modifier.size(if (height < 32.dp) 16.dp else 20.dp)
        )
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            if (onAddVideo != null) {
                DropdownMenuItem(
                    text = { Text(stringResource(Res.string.editor_video_add_video_track)) },
                    leadingIcon = { Icon(VideoGlyphs.Overlay, null) },
                    onClick = { open = false; onAddVideo() },
                    modifier = Modifier.testTag(TAG_VIDEO_ADD_TRACK_VIDEO)
                )
            }
            if (onAddAudio != null) {
                DropdownMenuItem(
                    text = { Text(stringResource(Res.string.editor_video_add_audio_track)) },
                    leadingIcon = { Icon(VideoGlyphs.MusicNote, null) },
                    onClick = { open = false; onAddAudio() },
                    modifier = Modifier.testTag(TAG_VIDEO_ADD_TRACK_AUDIO)
                )
            }
        }
    }
}

private val STUDIO_INTERVALS_SECONDS = listOf(
    0.5, 1.0, 2.0, 5.0, 10.0, 15.0, 30.0, 60.0, 120.0, 300.0, 600.0,
    1800.0, 3600.0, 7200.0
)

/**
 * The 24 dp ruler: `m:ss` labels sitting just right of short ticks at an
 * interval whose labels never collide; the scrub surface with progress
 * semantics.
 */
@Composable
private fun VideoStudioRuler(
    state: VideoEditorState,
    actions: VideoEditorActions,
    geometry: TimelineGeometry,
    viewportWidthPx: Float,
    modifier: Modifier = Modifier
) {
    val duration = state.sequence?.duration ?: EditorTime.Zero
    val density = LocalDensity.current
    val textMeasurer = rememberTextMeasurer()
    val labelStyle = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp, lineHeight = 14.sp)
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    val tickColor = MaterialTheme.colorScheme.outline
    val seekLabel = stringResource(Res.string.editor_video_ruler_seek)
    val interval = remember(geometry.totalTicks, geometry.contentWidthPx, labelStyle, density) {
        val minSpacing = with(density) { 56.dp.toPx() }
        val pad = with(density) { 16.dp.toPx() }
        STUDIO_INTERVALS_SECONDS.map { (it * EDITOR_TICKS_PER_SECOND).toLong() }
            .firstOrNull { ticks ->
                val spacing = ticks.toDouble() / geometry.totalTicks * geometry.contentWidthPx
                val worst = textMeasurer.measure(
                    formatStudioRulerTime(EditorTime(geometry.totalTicks), ticks), labelStyle
                ).size.width
                spacing >= maxOf(minSpacing, worst + pad)
            } ?: (STUDIO_INTERVALS_SECONDS.last() * EDITOR_TICKS_PER_SECOND).toLong()
    }
    val labels = remember(geometry.totalTicks, interval, labelStyle, density) {
        val out = mutableListOf<Pair<Long, TextLayoutResult>>()
        var t = 0L
        while (t <= geometry.totalTicks) {
            out += t to textMeasurer.measure(formatStudioRulerTime(EditorTime(t), interval), labelStyle)
            t += interval
        }
        out
    }
    val live = LocalVideoLiveState.current
    val gesture = remember {
        ScrubGesture(
            isPlaying = { live().playing },
            pause = actions.pause,
            resume = actions.play,
            seek = actions.seek
        )
    }
    val geometryProvider = rememberUpdatedState(geometry)
    Box(
        modifier = modifier
            .testTag(TAG_VIDEO_TIMELINE)
            .semantics {
                contentDescription = seekLabel
                progressBarRangeInfo = ProgressBarRangeInfo(
                    current = if (duration.ticks > 0) {
                        state.position.ticks.toFloat() / duration.ticks
                    } else 0f,
                    range = 0f..1f
                )
                setProgress { value ->
                    actions.seek(
                        EditorTime(
                            (value.toDouble() * duration.ticks).roundToLong()
                                .coerceIn(0L, duration.ticks)
                        )
                    )
                    true
                }
            }
            .scrubGestureInput(gesture) { offset ->
                geometryProvider.value.contentXToTime(offset.x)
            }
            .drawWithContent {
                drawContent()
                val tickHeight = 8.dp.toPx()
                val pad = 4.dp.toPx()
                val viewStart = geometry.scrollPx - 64.dp.toPx()
                val viewEnd = geometry.scrollPx + viewportWidthPx
                labels.forEach { (tick, layout) ->
                    val x = (tick.toDouble() / geometry.totalTicks * geometry.contentWidthPx)
                        .toFloat()
                    if (x < viewStart || x > viewEnd) return@forEach
                    val tickX = x.coerceIn(0.5f, size.width - 0.5f)
                    drawLine(
                        color = tickColor,
                        start = Offset(tickX, size.height - tickHeight),
                        end = Offset(tickX, size.height),
                        strokeWidth = 1.dp.toPx()
                    )
                    val w = layout.size.width.toFloat()
                    val left = (x + pad).coerceAtMost(size.width - w)
                    if (left >= 0f) {
                        drawText(layout, color = labelColor, topLeft = Offset(left, 0f))
                    }
                }
            }
    )
}

/**
 * One video lane: the clips at their true timeline fractions over the
 * rounded lane well, plus the selected clip's two trim grips painted on
 * its edges.
 */
@Composable
private fun VideoClipLane(
    track: VideoTrack,
    state: VideoEditorState,
    actions: VideoEditorActions,
    thumbnails: Map<String, ImageBitmap>,
    geometry: TimelineGeometry,
    pixelsPerTick: Float,
    gripOwner: MutableState<GripDrag?>,
    tileTagStart: Int,
    compactBadges: Boolean = true,
    dragHost: ClipDragHost? = null,
    slotIndex: Int = -1,
    modifier: Modifier = Modifier
) {
    val sequence = state.sequence ?: return
    val density = LocalDensity.current
    val flatClips = remember(sequence) { sequence.tracks.flatMap { it.clips } }
    val draggedId = dragHost?.drag?.value?.clipId
    val dropKind = if (track.kind == VideoTrackKind.Overlay && !track.locked) {
        LaneDropKind.Overlay
    } else {
        LaneDropKind.None
    }
    val stripTag = if (track.kind == VideoTrackKind.Main) TAG_VIDEO_CLIP_STRIP
    else "$TAG_VIDEO_CLIP_STRIP.${track.id}"
    val grip = gripOwner.value
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(VideoStudio.LaneCorner))
            .background(MaterialTheme.colorScheme.surfaceContainerLowest)
            .testTag(stripTag)
    ) {
        var tileIndex = tileTagStart
        track.clips.forEachIndexed { laneIndex, clip ->
            val index = flatClips.indexOf(clip)
            val (startTicks, endTicks) = clipTimelineBounds(clip, grip)
            val startX = startTicks * pixelsPerTick
            val widthPx = (endTicks * pixelsPerTick - startX).coerceAtLeast(1f)
            val widthDp = with(density) { widthPx.toDp() }
            val selected = clip.id == state.selectedClipId
            val previous = track.clips.getOrNull(laneIndex - 1)
            val next = track.clips.getOrNull(laneIndex + 1)
            val joinedStart = previous != null &&
                previous.timelineRange.endExclusive == clip.timelineStart
            val joinedEnd = next != null &&
                next.timelineStart == clip.timelineRange.endExclusive
            val shape = if (selected) {
                RoundedCornerShape(VideoStudio.ClipSelected)
            } else {
                val s = if (joinedStart) VideoStudio.ClipJoin else VideoStudio.ClipOuter
                val e = if (joinedEnd) VideoStudio.ClipJoin else VideoStudio.ClipOuter
                RoundedCornerShape(topStart = s, bottomStart = s, topEnd = e, bottomEnd = e)
            }
            val selectLabel = stringResource(Res.string.editor_video_select_clip, index + 1)
            val clipName = stringResource(
                Res.string.editor_video_clip_a11y,
                index + 1,
                formatVideoTimecode(clip.sourceRange.duration),
                formatVideoTimecode(clip.sourceRange.start),
                formatVideoTimecode(clip.sourceRange.endExclusive)
            )
            val ring = MaterialTheme.colorScheme.primary
            Box(
                modifier = Modifier
                    .offset { IntOffset(startX.roundToInt(), 0) }
                    .width(widthDp)
                    .fillMaxHeight()
                    .padding(end = 2.dp)
                    .alpha(if (clip.id == draggedId) 0.4f else 1f)
                    .clip(shape)
                    .background(MaterialTheme.colorScheme.surfaceContainer)
                    .clipDragSource(
                        host = dragHost,
                        clipId = clip.id,
                        kind = dropKind,
                        slotIndex = slotIndex,
                        startTicks = clip.timelineStart.ticks,
                        durationTicks = clip.sourceRange.duration.ticks,
                        pixelsPerTick = pixelsPerTick,
                        ticksPerFrame = sequence.frameRate.ticksPerFrame,
                        enabled = grip == null
                    )
                    .clickable(onClickLabel = selectLabel) { actions.selectClip(clip.id) }
                    .semantics {
                        this.selected = selected
                        contentDescription = clipName
                    }
                    .testTag(TAG_VIDEO_CLIP_PREFIX + clip.id)
            ) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .alpha(if (track.visible && clip.visible) 1f else 0.3f)
                ) {
                    VideoClipFilmstrip(clip, thumbnails, tileIndex)
                }
                tileIndex += VideoThumbnail.sampleTimes(clip.sourceRange).size
                TimelineEffectStrip(
                    labels = clip.timelineEffects(),
                    compact = compactBadges,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(4.dp)
                        .testTag(TAG_VIDEO_TIMELINE_EFFECT_PREFIX + clip.id)
                )
                if (widthDp >= 72.dp) {
                    VideoClipLabelChip(
                        text = stringResource(Res.string.editor_video_clip_label, index + 1),
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(start = 10.dp, bottom = 3.dp, end = 4.dp)
                    )
                }
                if (selected) {
                    Spacer(
                        Modifier
                            .matchParentSize()
                            .border(3.dp, ring, shape)
                    )
                }
            }
        }
        // The grips ride outside the clipped tile so their 44 × 48 dp hit
        // targets can straddle the clip edge.
        track.clips.forEach { clip ->
            val selected = clip.id == state.selectedClipId
            val source = sequence.sources.firstOrNull { it.id == clip.sourceId }
            if (!selected || source == null) return@forEach
            val (startTicks, endTicks) = clipTimelineBounds(clip, grip)
            val startX = startTicks * pixelsPerTick
            val endX = endTicks * pixelsPerTick
            val widthDp = with(density) { (endX - startX).toDp() }
            val ownsLeft = grip?.clipId == clip.id && grip.edgeLeft
            val ownsRight = grip?.clipId == clip.id && !grip.edgeLeft
            val visible = widthDp >= 64.dp
            val inset = with(density) { 18.dp.toPx() }
            val gripWidth = with(density) { 44.dp.toPx() }
            if (visible || ownsLeft) {
                TrimGrip(
                    clip = clip,
                    source = source,
                    sequence = sequence,
                    contentWidthPx = geometry.contentWidthPx,
                    left = true,
                    actions = actions,
                    gripOwner = gripOwner,
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .offset { IntOffset((startX - inset).roundToInt(), 0) }
                        .testTag(TAG_VIDEO_GRIP_START)
                )
            }
            if (visible || ownsRight) {
                TrimGrip(
                    clip = clip,
                    source = source,
                    sequence = sequence,
                    contentWidthPx = geometry.contentWidthPx,
                    left = false,
                    actions = actions,
                    gripOwner = gripOwner,
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .offset {
                            IntOffset((endX - with(density) { 2.dp.toPx() } - gripWidth + inset).roundToInt(), 0)
                        }
                        .testTag(TAG_VIDEO_GRIP_END)
                )
            }
        }
    }
}

/** The clip's decoded filmstrip — real tiles, neutral wells until they land. */
@Composable
private fun VideoClipFilmstrip(
    clip: VideoClip,
    thumbnails: Map<String, ImageBitmap>,
    tileTagStart: Int
) {
    val samples = remember(clip.sourceRange) { VideoThumbnail.sampleTimes(clip.sourceRange) }
    Row(Modifier.fillMaxSize()) {
        samples.forEachIndexed { cell, sample ->
            val frame = thumbnails[videoTileKey(clip.id, sample)]
            if (frame != null) {
                Image(
                    bitmap = frame,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .testTag(TAG_VIDEO_TILE_PREFIX + (tileTagStart + cell))
                )
            } else {
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                )
            }
        }
    }
}

/** The small name chip on a clip's lower-left corner. */
@Composable
internal fun VideoClipLabelChip(text: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerLowest.copy(alpha = 0.72f))
            .padding(horizontal = 6.dp)
    ) {
        Text(
            text = text,
            fontSize = 11.sp,
            lineHeight = 14.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1
        )
    }
}

/**
 * The playhead: a 3 dp line with a hairline contrast ring and a cookie
 * cap on the ruler, painted after every clip so it is never hidden.
 */
@Composable
private fun BoxScope.VideoStudioPlayhead(state: VideoEditorState, geometry: TimelineGeometry) {
    val ink = MaterialTheme.colorScheme.onSurface
    val ring = MaterialTheme.colorScheme.surfaceContainerLowest
    val position = state.position
    Spacer(
        Modifier
            .matchParentSize()
            .drawWithContent {
                drawContent()
                val x = geometry.timeToX(position)
                val w = 3.dp.toPx()
                drawRect(
                    color = ring,
                    topLeft = Offset(x - w / 2f - 1.dp.toPx(), 0f),
                    size = Size(w + 2.dp.toPx(), size.height)
                )
                drawRect(
                    color = ink,
                    topLeft = Offset(x - w / 2f, 0f),
                    size = Size(w, size.height)
                )
                val cap = 15.dp.toPx()
                val outline = VideoCookieShape.createOutline(
                    Size(cap, cap), LayoutDirection.Ltr, this
                )
                if (outline is Outline.Generic) {
                    translate(left = x - cap / 2f, top = 0f) {
                        drawPath(outline.path, ink)
                    }
                }
            }
    )
}
