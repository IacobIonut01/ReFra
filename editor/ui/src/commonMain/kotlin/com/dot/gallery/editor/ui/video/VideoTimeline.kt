package com.dot.gallery.editor.ui.video

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.dot.gallery.editor.core.model.MediaAsset
import com.dot.gallery.editor.core.model.VideoClip
import com.dot.gallery.editor.core.model.VideoSequence
import com.dot.gallery.editor.core.time.EditorTime
import com.dot.gallery.editor.core.time.TimeRange
import com.dot.gallery.editor.render.video.VideoEditorState
import com.dot.gallery.editor.ui.generated.resources.Res
import com.dot.gallery.editor.ui.generated.resources.editor_video_trim_end
import com.dot.gallery.editor.ui.generated.resources.editor_video_trim_start
import org.jetbrains.compose.resources.stringResource
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.roundToLong

internal const val TAG_VIDEO_TIMELINE = "videoEditor.timeline"
internal const val TAG_VIDEO_CLIP_STRIP = "videoEditor.clipStrip"
internal const val TAG_VIDEO_CLIP_PREFIX = "videoEditor.clip."
internal const val TAG_VIDEO_TILE_PREFIX = "videoEditor.timeline.tile."
internal const val TAG_VIDEO_EMPTY_UNDO = "videoEditor.emptyUndo"
internal const val TAG_VIDEO_EMPTY_ADD = "videoEditor.emptyAdd"
internal const val TAG_VIDEO_ZOOM_IN = "videoEditor.zoomIn"
internal const val TAG_VIDEO_ZOOM_OUT = "videoEditor.zoomOut"
internal const val TAG_VIDEO_ZOOM_FIT = "videoEditor.zoomFit"
internal const val TAG_VIDEO_TIMELINE_EXPAND = "videoEditor.timeline.expand"
internal const val TAG_VIDEO_TIMELINE_COLLAPSE = "videoEditor.timeline.collapse"
internal const val TAG_VIDEO_GRIP_START = "videoEditor.grip.start"
internal const val TAG_VIDEO_GRIP_END = "videoEditor.grip.end"

internal suspend fun zoomAroundPlayhead(
    state: VideoEditorState,
    viewport: VideoTimelineViewport,
    transform: (Float) -> Float
) {
    val sequence = state.sequence ?: return
    viewport.zoomTo(
        next = transform(viewport.zoom),
        anchorTime = state.position,
        totalTicks = sequence.duration.ticks,
        viewportWidthPx = viewport.lastViewportWidthPx
    )
}

/**
 * The clip's timeline bounds in ticks for this frame. With no open grip
 * draft it's the sequence's own range; during a draft every clip is frozen
 * at its drag-start bounds, except the owner whose dragged edge follows
 * the pointer by [GripDrag.edgeDeltaTicks].
 */
internal fun clipTimelineBounds(
    clip: VideoClip,
    grip: GripDrag?
): Pair<Long, Long> {
    val frozen = grip?.frozenBounds?.get(clip.id)
    val start = frozen?.first ?: clip.timelineRange.start.ticks
    val end = frozen?.second ?: clip.timelineRange.endExclusive.ticks
    if (grip == null || clip.id != grip.clipId) return start to end
    return if (grip.edgeLeft) {
        start + grip.edgeDeltaTicks.longValue to end
    } else {
        start to end + grip.edgeDeltaTicks.longValue
    }
}

/**
 * One live grip drag. Captured at pointer-down with the source range, the
 * snap grid, the pixels-per-tick scale and every clip's timeline bounds —
 * all frozen so the repacked draft can't feed back into the mapping. The
 * [owner] token is how a disposed or superseded grip knows the draft is
 * still (or no longer) its own before it commits or cancels.
 */
internal class GripDrag(
    val edgeLeft: Boolean,
    val owner: Any,
    val clipId: String,
    val baseRange: TimeRange,
    val pixelsPerTick: Float,
    val ticksPerFrame: Long,
    val minSegmentTicks: Long,
    val sourceTicks: Long,
    val frozenBounds: Map<String, Pair<Long, Long>>
) {
    var accumulatedPx = 0f

    /** Snapped+clamped offset of the dragged edge from its start, in ticks. */
    val edgeDeltaTicks = mutableLongStateOf(0L)

    /**
     * Accumulates [deltaPx] and returns the snapped, clamped source range —
     * always, so a drag that returns to its start republishes the original
     * range (the session no-ops identical values itself).
     */
    fun onDrag(deltaPx: Float): TimeRange {
        accumulatedPx += deltaPx
        val raw = (accumulatedPx / pixelsPerTick).roundToLong()
        return if (edgeLeft) {
            val snapped = floor(
                (baseRange.start.ticks + raw) / ticksPerFrame.toDouble()
            ).toLong() * ticksPerFrame
            val clamped = snapped.coerceIn(
                0L, baseRange.endExclusive.ticks - minSegmentTicks
            )
            edgeDeltaTicks.longValue = clamped - baseRange.start.ticks
            TimeRange(
                EditorTime(clamped),
                baseRange.endExclusive - EditorTime(clamped)
            )
        } else {
            val snapped = ceil(
                (baseRange.endExclusive.ticks + raw) / ticksPerFrame.toDouble()
            ).toLong() * ticksPerFrame
            val clamped = snapped.coerceIn(
                baseRange.start.ticks + minSegmentTicks, sourceTicks
            )
            edgeDeltaTicks.longValue = clamped - baseRange.endExclusive.ticks
            TimeRange(
                baseRange.start,
                EditorTime(clamped - baseRange.start.ticks)
            )
        }
    }
}

/**
 * A 48 dp hit target painted as a 6×24 accent bar on the clip edge. The
 * pointer handler is keyed on clip.id only; at drag start it freezes the
 * source range, the snap grid, the scale and the whole lane's clip bounds
 * into a [GripDrag] registered in [gripOwner] — a second finger or a
 * competing grip can never claim the same history. Finish and cancel run
 * only while this grip still owns the draft; dispose while owning cancels
 * it. The session owns the actual history — this calls
 * begin/update/commit/cancel on [actions].
 */
@Composable
internal fun TrimGrip(
    clip: VideoClip,
    source: MediaAsset.Video,
    sequence: VideoSequence,
    contentWidthPx: Float,
    left: Boolean,
    actions: VideoEditorActions,
    gripOwner: MutableState<GripDrag?>,
    modifier: Modifier = Modifier
) {
    val ownerToken = remember(clip.id, left) { Any() }
    // The gesture coroutine outlives compositions — read the current clip,
    // scale and sequence through state handles so a mid-drag publish never
    // mixes the frozen drag origin with stale references.
    val currentClip = rememberUpdatedState(clip)
    val currentSource = rememberUpdatedState(source)
    val currentSequence = rememberUpdatedState(sequence)
    val currentContentWidth = rememberUpdatedState(contentWidthPx)
    val gripLabel = stringResource(
        if (left) Res.string.editor_video_trim_start
        else Res.string.editor_video_trim_end
    )
    DisposableEffect(clip.id) {
        onDispose {
            // Only if this grip still owns the draft — a stale dispose must
            // never cancel another owner's gesture.
            if (gripOwner.value?.owner === ownerToken) {
                gripOwner.value = null
                actions.cancelTrim()
            }
        }
    }
    Box(
        modifier = modifier
            .size(width = 44.dp, height = 48.dp)
            .semantics { contentDescription = gripLabel }
            .pointerInput(clip.id) {
                detectHorizontalDragGestures(
                    onDragStart = {
                        // One draft per lane: another live grip wins.
                        if (gripOwner.value != null) return@detectHorizontalDragGestures
                        val seq = currentSequence.value
                        val src = currentSource.value
                        val ticksPerFrame = (
                            src.frameRate ?: seq.frameRate
                            ).ticksPerFrame.coerceAtLeast(1L)
                        actions.beginTrim()
                        gripOwner.value = GripDrag(
                            edgeLeft = left,
                            owner = ownerToken,
                            clipId = currentClip.value.id,
                            baseRange = currentClip.value.sourceRange,
                            pixelsPerTick = currentContentWidth.value /
                                seq.duration.ticks.toFloat(),
                            ticksPerFrame = ticksPerFrame,
                            minSegmentTicks = minOf(
                                ticksPerFrame, src.duration.ticks
                            ),
                            sourceTicks = src.duration.ticks,
                            frozenBounds = seq.tracks.flatMap { it.clips }
                                .associate {
                                    it.id to (
                                        it.timelineRange.start.ticks to
                                            it.timelineRange.endExclusive.ticks
                                        )
                                }
                        )
                    },
                    onDragEnd = {
                        if (gripOwner.value?.owner === ownerToken) {
                            gripOwner.value = null
                            actions.commitTrim()
                        }
                    },
                    onDragCancel = {
                        if (gripOwner.value?.owner === ownerToken) {
                            gripOwner.value = null
                            actions.cancelTrim()
                        }
                    },
                    onHorizontalDrag = { change, dragAmount ->
                        val drag = gripOwner.value
                            ?: return@detectHorizontalDragGestures
                        if (drag.owner !== ownerToken) {
                            return@detectHorizontalDragGestures
                        }
                        change.consume()
                        actions.updateTrim(drag.onDrag(dragAmount))
                    }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        // The 8 × 28 primary bar wears a 2 dp ring of the lane well so it
        // reads against the filmstrip under it.
        Box(
            Modifier
                .size(width = 12.dp, height = 32.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerLowest)
                .padding(2.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(MaterialTheme.colorScheme.primary)
        )
    }
}
