package com.dot.gallery.editor.ui.video

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.DragInteraction
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dot.gallery.editor.core.time.EditorTime
import com.dot.gallery.editor.core.time.TimeRange
import com.dot.gallery.editor.render.video.VideoEditorState
import com.dot.gallery.editor.ui.generated.resources.Res
import com.dot.gallery.editor.ui.generated.resources.editor_video_duration
import com.dot.gallery.editor.ui.generated.resources.editor_video_in_minus_frame
import com.dot.gallery.editor.ui.generated.resources.editor_video_in_plus_frame
import com.dot.gallery.editor.ui.generated.resources.editor_video_in_point
import com.dot.gallery.editor.ui.generated.resources.editor_video_out_minus_frame
import com.dot.gallery.editor.ui.generated.resources.editor_video_out_plus_frame
import com.dot.gallery.editor.ui.generated.resources.editor_video_out_point
import com.dot.gallery.editor.ui.generated.resources.editor_video_reset_trim
import com.dot.gallery.editor.ui.toEditorImageBitmap
import kotlin.math.abs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.stringResource

internal const val TAG_VIDEO_SPLIT = "videoEditor.split"
internal const val TAG_VIDEO_DELETE = "videoEditor.delete"
internal const val TAG_VIDEO_MOVE_EARLIER = "videoEditor.moveEarlier"
internal const val TAG_VIDEO_MOVE_LATER = "videoEditor.moveLater"
internal const val TAG_VIDEO_TRIM_SLIDER = "videoEditor.trim.rangeSlider"
internal const val TAG_VIDEO_TRIM_IN_MINUS = "videoEditor.trim.inMinus"
internal const val TAG_VIDEO_TRIM_IN_PLUS = "videoEditor.trim.inPlus"
internal const val TAG_VIDEO_TRIM_OUT_MINUS = "videoEditor.trim.outMinus"
internal const val TAG_VIDEO_TRIM_OUT_PLUS = "videoEditor.trim.outPlus"
internal const val TAG_VIDEO_TRIM_RESET = "videoEditor.trim.reset"

private val TRIM_STRIP_HEIGHT = 56.dp
private val TRIM_HANDLE_WIDTH = 6.dp
private const val TRIM_STRIP_CELLS = 8
private val TRIM_STEPPERS_SIDE_BY_SIDE = 330.dp

/**
 * The Trim panel: a dimmed source filmstrip under the primary selection
 * window and its two handles, In/Out steppers that move one frame at a
 * time, the resulting duration, and the panel-header Reset to the full
 * source.
 *
 * Dragging rides the *session* draft seam — `beginTrim`/`updateTrim`/
 * `commitTrim`/`cancelTrim` — so the slider, the timeline grips and the
 * undo stack all speak the same history language: one gesture is one undo
 * step, a cancel restores the captured sequence, and the sequence itself
 * (not a shadow copy) is what displays mid-drag.
 *
 * Drag outcomes are decided by the per-handle interaction stream, not
 * `onValueChangeFinished` (which M3 also fires on cancelled teardowns).
 * A finish with no drag in flight is the accessibility setProgress path:
 * it waits one composition frame so a queued Stop/Cancel still wins.
 */
@Composable
internal fun VideoTrimInspector(
    state: VideoEditorState,
    actions: VideoEditorActions,
    modifier: Modifier = Modifier
) {
    val sequence = state.sequence ?: return
    val clips = remember(sequence) { sequence.tracks.flatMap { it.clips } }
    val clip = clips.firstOrNull { it.id == state.selectedClipId } ?: return
    val source = sequence.sources.firstOrNull { it.id == clip.sourceId }
        ?: return
    val sourceDuration = source.duration
    val durationTicks = sourceDuration.ticks
    if (durationTicks <= 0L) return
    val rate = source.frameRate ?: sequence.frameRate
    val ticksPerFrame = rate.ticksPerFrame
    val minSegmentTicks = minOf(ticksPerFrame, durationTicks)

    val committedIn = clip.sourceRange.start.ticks.toFloat() / durationTicks
    val committedOut =
        clip.sourceRange.endExclusive.ticks.toFloat() / durationTicks

    val latestActions by rememberUpdatedState(actions)
    val startInteractionSource = remember { MutableInteractionSource() }
    val endInteractionSource = remember { MutableInteractionSource() }
    val scope = rememberCoroutineScope()
    // Whether this slider owns the session draft — grips can own it too.
    var ownsDraft by remember { mutableStateOf(false) }
    var dragInFlight by remember { mutableStateOf(false) }
    var finishSerial by remember { mutableStateOf(0) }

    fun pushRange(fractions: ClosedFloatingPointRange<Float>) {
        // An endpoint the user never moved keeps its committed tick —
        // the Float round-trip of a frame boundary can land one frame off.
        val snapped = snapTrimRange(
            sourceDuration, rate,
            fractions.start.toDouble(), fractions.endInclusive.toDouble(),
            fixedStart = clip.sourceRange.start
                .takeIf { fractions.start == committedIn },
            fixedEnd = clip.sourceRange.endExclusive
                .takeIf { fractions.endInclusive == committedOut }
        )
        if (snapped == clip.sourceRange) return
        if (!ownsDraft) {
            latestActions.beginTrim()
            ownsDraft = true
        }
        latestActions.updateTrim(snapped)
    }

    suspend fun collectHandle(source: MutableInteractionSource) {
        source.interactions.collect { interaction ->
            when (interaction) {
                is DragInteraction.Start -> {
                    dragInFlight = true
                    finishSerial++
                    if (!ownsDraft) {
                        latestActions.beginTrim()
                        ownsDraft = true
                    }
                }
                is DragInteraction.Stop -> {
                    dragInFlight = false
                    finishSerial++
                    if (ownsDraft) {
                        latestActions.commitTrim()
                        ownsDraft = false
                    }
                }
                is DragInteraction.Cancel -> {
                    dragInFlight = false
                    finishSerial++
                    if (ownsDraft) {
                        latestActions.cancelTrim()
                        ownsDraft = false
                    }
                }
            }
        }
    }

    LaunchedEffect(startInteractionSource) { collectHandle(startInteractionSource) }
    LaunchedEffect(endInteractionSource) { collectHandle(endInteractionSource) }

    // Leaving composition mid-gesture (layout flip, clip switch) drops the
    // draft — it belongs to this slider instance.
    DisposableEffect(Unit) {
        onDispose { if (ownsDraft) latestActions.cancelTrim() }
    }
    // The session dropped the draft externally (command, selection change)
    // — forget ownership so a trailing Stop does not commit against it.
    LaunchedEffect(state.isDrafting) {
        if (!state.isDrafting) ownsDraft = false
    }

    fun commit(range: TimeRange) {
        latestActions.trimSelected(range)
    }

    fun nudgeIn(frames: Long) {
        val newIn = (clip.sourceRange.start.ticks + frames * ticksPerFrame)
            .coerceIn(0L, clip.sourceRange.endExclusive.ticks - minSegmentTicks)
        if (newIn != clip.sourceRange.start.ticks) {
            commit(
                TimeRange(
                    EditorTime(newIn),
                    clip.sourceRange.endExclusive - EditorTime(newIn)
                )
            )
        }
    }

    fun nudgeOut(frames: Long) {
        val newOut =
            (clip.sourceRange.endExclusive.ticks + frames * ticksPerFrame)
                .coerceIn(
                    clip.sourceRange.start.ticks + minSegmentTicks,
                    durationTicks
                )
        if (newOut != clip.sourceRange.endExclusive.ticks) {
            commit(
                TimeRange(
                    clip.sourceRange.start,
                    EditorTime(newOut) - clip.sourceRange.start
                )
            )
        }
    }

    val inMinusEnabled = clip.sourceRange.start.ticks > 0L && !state.isDrafting
    val segmentRoom = clip.sourceRange.endExclusive.ticks -
        clip.sourceRange.start.ticks > minSegmentTicks && !state.isDrafting
    val outPlusEnabled =
        clip.sourceRange.endExclusive.ticks < durationTicks && !state.isDrafting

    val resetTrim = {
        val full = TimeRange(EditorTime.Zero, sourceDuration)
        if (full != clip.sourceRange) commit(full)
    }
    val canReset = clip.sourceRange.duration != sourceDuration && !state.isDrafting
    PublishVideoPanelReset(enabled = canReset, onReset = resetTrim)

    // Real decoded frames of this clip, nearest to each strip cell; a cell
    // with no frame yet stays a neutral well rather than a fake picture.
    val clipThumbnails = remember(state.thumbnails, clip.id) {
        state.thumbnails.filter { it.clipId == clip.id }
    }
    val frames by produceState<List<Pair<Long, ImageBitmap>>>(emptyList(), clipThumbnails) {
        value = withContext(Dispatchers.Default) {
            clipThumbnails.map { it.sourceTime.ticks to it.pixels.toEditorImageBitmap() }
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .videoSurfaceInset(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // A draft owned by another control (a timeline grip) locks this
        // slider — otherwise a second control could steal the open draft.
        val sliderEnabled =
            !state.opening && (!state.isDrafting || ownsDraft)
        val handleColor = if (sliderEnabled) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
        }
        val handle: @Composable () -> Unit = {
            Box(
                Modifier
                    .size(TRIM_HANDLE_WIDTH, 48.dp)
                    .background(handleColor, RoundedCornerShape(3.dp))
            )
        }
        RangeSlider(
            value = committedIn..committedOut,
            onValueChange = ::pushRange,
            onValueChangeFinished = {
                // Drag gestures are finished by the interaction stream;
                // only non-drag paths (accessibility setProgress,
                // tap-to-jump) commit here — one frame later so a queued
                // Stop/Cancel on the same gesture resolves first.
                if (!dragInFlight && ownsDraft) {
                    val serial = ++finishSerial
                    scope.launch {
                        withFrameNanos { }
                        if (serial == finishSerial && !dragInFlight &&
                            ownsDraft
                        ) {
                            latestActions.commitTrim()
                            ownsDraft = false
                        }
                    }
                }
            },
            startInteractionSource = startInteractionSource,
            endInteractionSource = endInteractionSource,
            enabled = sliderEnabled,
            startThumb = { handle() },
            endThumb = { handle() },
            track = { rangeSliderState ->
                Box(Modifier.fillMaxWidth().height(TRIM_STRIP_HEIGHT)) {
                    TrimFilmstrip(
                        frames = frames,
                        durationTicks = durationTicks,
                        modifier = Modifier.fillMaxSize()
                    )
                    Canvas(Modifier.fillMaxSize()) {
                        val border = 3.dp.toPx()
                        val lo = rangeSliderState.activeRangeStart * size.width
                        val hi = rangeSliderState.activeRangeEnd * size.width
                        if (hi > lo) {
                            drawRoundRect(
                                color = handleColor,
                                topLeft = Offset(lo, border / 2f),
                                size = Size(hi - lo, size.height - border),
                                cornerRadius = CornerRadius(8.dp.toPx()),
                                style = Stroke(width = border)
                            )
                        }
                    }
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = TRIM_STRIP_HEIGHT)
                .testTag(TAG_VIDEO_TRIM_SLIDER)
        )
        val inStepper: @Composable (Modifier) -> Unit = { stepperModifier ->
            TrimStepper(
                name = stringResource(Res.string.editor_video_in_point),
                value = formatVideoTimecode(clip.sourceRange.start),
                minusLabel = stringResource(Res.string.editor_video_in_minus_frame),
                plusLabel = stringResource(Res.string.editor_video_in_plus_frame),
                minusEnabled = inMinusEnabled,
                plusEnabled = segmentRoom,
                minusTag = TAG_VIDEO_TRIM_IN_MINUS,
                plusTag = TAG_VIDEO_TRIM_IN_PLUS,
                onMinus = { nudgeIn(-1) },
                onPlus = { nudgeIn(1) },
                modifier = stepperModifier
            )
        }
        val outStepper: @Composable (Modifier) -> Unit = { stepperModifier ->
            TrimStepper(
                name = stringResource(Res.string.editor_video_out_point),
                value = formatVideoTimecode(clip.sourceRange.endExclusive),
                minusLabel = stringResource(Res.string.editor_video_out_minus_frame),
                plusLabel = stringResource(Res.string.editor_video_out_plus_frame),
                minusEnabled = segmentRoom,
                plusEnabled = outPlusEnabled,
                minusTag = TAG_VIDEO_TRIM_OUT_MINUS,
                plusTag = TAG_VIDEO_TRIM_OUT_PLUS,
                onMinus = { nudgeOut(-1) },
                onPlus = { nudgeOut(1) },
                modifier = stepperModifier
            )
        }
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            // Two steppers side by side leave a narrow panel too little room
            // for the full timecode, so they stack there.
            if (maxWidth < TRIM_STEPPERS_SIDE_BY_SIDE) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    inStepper(Modifier.fillMaxWidth())
                    outStepper(Modifier.fillMaxWidth())
                }
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    inStepper(Modifier.weight(1f))
                    outStepper(Modifier.weight(1f))
                }
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(Res.string.editor_video_duration),
                style = MaterialTheme.typography.labelLarge.copy(fontSize = 14.sp),
                modifier = Modifier.weight(1f)
            )
            Text(
                text = formatVideoTimecode(clip.sourceRange.duration),
                style = MaterialTheme.typography.labelLarge.copy(fontSize = 14.sp)
            )
        }
        // Hosts without a panel header (the desktop inspector) keep the
        // reset in the surface itself.
        if (LocalVideoPanelReset.current == null) {
            VideoV4Command(
                label = stringResource(Res.string.editor_video_reset_trim),
                onClick = resetTrim,
                enabled = canReset,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(TAG_VIDEO_TRIM_RESET)
            )
        }
    }
}

/** The dimmed source strip behind the trim window. */
@Composable
private fun TrimFilmstrip(
    frames: List<Pair<Long, ImageBitmap>>,
    durationTicks: Long,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerLowest)
            .alpha(0.45f),
        horizontalArrangement = Arrangement.spacedBy(1.dp)
    ) {
        repeat(TRIM_STRIP_CELLS) { cell ->
            val at = durationTicks * (2 * cell + 1) / (2 * TRIM_STRIP_CELLS)
            val frame = frames.minByOrNull { abs(it.first - at) }?.second
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            ) {
                if (frame != null) {
                    Image(
                        bitmap = frame,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }
    }
}

/** A 64 dp slab: minus one frame, the named source time, plus one frame. */
@Composable
private fun TrimStepper(
    name: String,
    value: String,
    minusLabel: String,
    plusLabel: String,
    minusEnabled: Boolean,
    plusEnabled: Boolean,
    minusTag: String,
    plusTag: String,
    onMinus: () -> Unit,
    onPlus: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .heightIn(min = 64.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh),
        verticalAlignment = Alignment.CenterVertically
    ) {
        TrimStepButton(VideoGlyphs.Remove, minusLabel, minusEnabled, minusTag, onMinus)
        Column(
            modifier = Modifier.weight(1f),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = name,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
            Text(
                text = value,
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1,
                softWrap = false
            )
        }
        TrimStepButton(VideoGlyphs.Add, plusLabel, plusEnabled, plusTag, onPlus)
    }
}

@Composable
private fun TrimStepButton(
    icon: ImageVector,
    label: String,
    enabled: Boolean,
    tag: String,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(44.dp, 48.dp)
            .clip(CircleShape)
            .clickable(enabled = enabled, role = Role.Button, onClickLabel = label, onClick = onClick)
            .semantics { contentDescription = label }
            .testTag(tag),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            icon, null,
            tint = if (enabled) MaterialTheme.colorScheme.onSurface
            else MaterialTheme.colorScheme.outline,
            modifier = Modifier.size(20.dp)
        )
    }
}
