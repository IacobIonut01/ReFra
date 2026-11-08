package com.dot.gallery.editor.ui.video

import com.dot.gallery.editor.ui.rememberEditorSettingsStore
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.dot.gallery.editor.ui.generated.resources.Res
import com.dot.gallery.editor.ui.generated.resources.editor_video_move_clip_track_down
import com.dot.gallery.editor.ui.generated.resources.editor_video_move_clip_track_up
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.roundToLong
import org.jetbrains.compose.resources.stringResource

internal const val TAG_VIDEO_DROP_GHOST = "videoEditor.dropGhost"

/** Which clips a lane takes from a timeline drag. */
internal enum class LaneDropKind { None, Overlay, Audio }

/**
 * One timeline lane as the clip drag sees it, in the lane column's pixel
 * space. A null [trackId] is the new-track row under the last lane — an
 * audio drop there creates a lane. [clips] hold every placement as
 * (id, start, end) ticks.
 */
internal class TimelineLaneSlot(
    val top: Float,
    val height: Float,
    val trackId: String?,
    val accepts: LaneDropKind,
    val locked: Boolean,
    val clips: List<Triple<String, Long, Long>>
)

/**
 * One live clip drag. The origin, scale and snap grid are frozen at the
 * long-press so a publish mid-gesture can't move the mapping; [dx]/[dy]
 * follow the pointer. Nothing reaches the session until the drop.
 */
internal class ClipLaneDrag(
    val clipId: String,
    val kind: LaneDropKind,
    val originSlot: Int,
    val originStartTicks: Long,
    val durationTicks: Long,
    val pixelsPerTick: Float,
    val ticksPerFrame: Long,
    val snapPx: Float
) {
    var dx by mutableFloatStateOf(0f)
    var dy by mutableFloatStateOf(0f)
}

/**
 * Where a drag would land: the lane under the clip's centre, the start
 * snapped to the frame grid (and to the lane start or a neighbour's edge
 * within [ClipLaneDrag.snapPx]). [valid] means the lane takes this kind,
 * is unlocked and has room; [changed] that the drop moves anything.
 */
internal data class ClipDropTarget(
    val slot: Int,
    val trackId: String?,
    val startTicks: Long,
    val valid: Boolean,
    val changed: Boolean
)

internal fun resolveClipDrop(
    drag: ClipLaneDrag,
    slots: List<TimelineLaneSlot>
): ClipDropTarget? {
    val origin = slots.getOrNull(drag.originSlot) ?: return null
    if (drag.pixelsPerTick <= 0f) return null
    val centre = origin.top + origin.height / 2f + drag.dy
    val slotIndex = slots.indices.minByOrNull { index ->
        val slot = slots[index]
        when {
            centre < slot.top -> slot.top - centre
            centre > slot.top + slot.height -> centre - slot.top - slot.height
            else -> 0f
        }
    } ?: return null
    val slot = slots[slotIndex]
    val rawStart = drag.originStartTicks + (drag.dx / drag.pixelsPerTick).roundToLong()
    val frame = drag.ticksPerFrame.coerceAtLeast(1L)
    var start = ((rawStart.toDouble() / frame).roundToLong() * frame).coerceAtLeast(0L)
    val others = slot.clips.filter { it.first != drag.clipId }
    val magnets = buildList {
        add(0L)
        others.forEach { (_, s, e) ->
            add(e)
            add(s - drag.durationTicks)
        }
    }.filter { it >= 0L }
    magnets.minByOrNull { abs(it - rawStart) }?.let { magnet ->
        if (abs(magnet - rawStart) * drag.pixelsPerTick <= drag.snapPx) start = magnet
    }
    val end = start + drag.durationTicks
    val valid = slot.accepts == drag.kind && !slot.locked &&
        others.none { (_, s, e) -> start < e && s < end }
    val changed = slotIndex != drag.originSlot || start != drag.originStartTicks
    return ClipDropTarget(slotIndex, slot.trackId, start, valid, changed)
}

/**
 * The timeline's shared drag state: the live [drag], the lanes it can
 * land on (refreshed every composition) and the drop sink. One drag at a
 * time — a second long-press while one runs is ignored.
 */
internal class ClipDragHost(val drag: MutableState<ClipLaneDrag?>) {
    var slots: List<TimelineLaneSlot> = emptyList()
    var onDrop: (ClipLaneDrag, ClipDropTarget) -> Unit = { _, _ -> }

    fun drop(drag: ClipLaneDrag) {
        val target = resolveClipDrop(drag, slots) ?: return
        if (target.valid && target.changed) onDrop(drag, target)
    }

    /**
     * Moves a clip to the nearest lane of its kind above ([delta] < 0) or
     * below at the same start — the accessibility stand-in for the drag.
     * Returns whether a lane took it.
     */
    fun nudge(
        clipId: String,
        kind: LaneDropKind,
        slotIndex: Int,
        startTicks: Long,
        durationTicks: Long,
        delta: Int
    ): Boolean {
        val origin = slots.getOrNull(slotIndex) ?: return false
        val range = if (delta < 0) (slotIndex - 1 downTo 0) else (slotIndex + 1 until slots.size)
        val targetIndex = range.firstOrNull { slots[it].accepts == kind } ?: return false
        val target = slots[targetIndex]
        val probe = ClipLaneDrag(
            clipId, kind, slotIndex, startTicks, durationTicks,
            pixelsPerTick = 1f, ticksPerFrame = 1L, snapPx = 0f
        ).apply {
            dy = target.top + target.height / 2f - (origin.top + origin.height / 2f)
        }
        val resolved = resolveClipDrop(probe, slots) ?: return false
        if (!resolved.valid || !resolved.changed) return false
        onDrop(probe, resolved)
        return true
    }
}

/** A drag source's placement, captured fresh at each long-press. */
private data class DragOrigin(
    val slotIndex: Int,
    val startTicks: Long,
    val durationTicks: Long,
    val pixelsPerTick: Float,
    val ticksPerFrame: Long
)

/**
 * Makes a clip tile draggable across lanes: long-press picks it up, the
 * drag follows the finger and the drop goes through [ClipDragHost.drop].
 * Also exposes "move to track above/below" accessibility actions. A null
 * [host], negative [slotIndex] or disabled source leaves the tile as is.
 */
@Composable
internal fun Modifier.clipDragSource(
    host: ClipDragHost?,
    clipId: String,
    kind: LaneDropKind,
    slotIndex: Int,
    startTicks: Long,
    durationTicks: Long,
    pixelsPerTick: Float,
    ticksPerFrame: Long,
    enabled: Boolean
): Modifier {
    if (host == null || slotIndex < 0 || kind == LaneDropKind.None) return this
    val latest by rememberUpdatedState(
        DragOrigin(slotIndex, startTicks, durationTicks, pixelsPerTick, ticksPerFrame)
    )
    // Edge magnets follow the "Snap while dragging" setting; drops stay
    // frame-accurate either way.
    val snapEnabled = rememberEditorSettingsStore().settings.videoSnap
    val snapPx = if (snapEnabled) with(LocalDensity.current) { 10.dp.toPx() } else 0f
    DisposableEffect(host, clipId, enabled) {
        onDispose {
            if (host.drag.value?.clipId == clipId) host.drag.value = null
        }
    }
    val upLabel = stringResource(Res.string.editor_video_move_clip_track_up)
    val downLabel = stringResource(Res.string.editor_video_move_clip_track_down)
    if (!enabled) return this
    return this
        .semantics {
            customActions = listOf(
                CustomAccessibilityAction(upLabel) {
                    host.nudge(clipId, kind, slotIndex, startTicks, durationTicks, -1)
                },
                CustomAccessibilityAction(downLabel) {
                    host.nudge(clipId, kind, slotIndex, startTicks, durationTicks, 1)
                }
            )
        }
        .pointerInput(host, clipId) {
            detectDragGesturesAfterLongPress(
                onDragStart = {
                    if (host.drag.value != null) return@detectDragGesturesAfterLongPress
                    val origin = latest
                    host.drag.value = ClipLaneDrag(
                        clipId, kind, origin.slotIndex, origin.startTicks,
                        origin.durationTicks, origin.pixelsPerTick,
                        origin.ticksPerFrame, snapPx
                    )
                },
                onDragEnd = {
                    val drag = host.drag.value
                    if (drag?.clipId == clipId) {
                        host.drag.value = null
                        host.drop(drag)
                    }
                },
                onDragCancel = {
                    if (host.drag.value?.clipId == clipId) host.drag.value = null
                },
                onDrag = { change, amount ->
                    val drag = host.drag.value
                    if (drag?.clipId == clipId) {
                        change.consume()
                        drag.dx += amount.x
                        drag.dy += amount.y
                    }
                }
            )
        }
}

/**
 * The drop preview: an outline at the snapped landing slot — primary
 * where the drop lands, error where the lane refuses it.
 */
@Composable
internal fun ClipDropGhost(host: ClipDragHost) {
    val drag = host.drag.value ?: return
    val target = resolveClipDrop(drag, host.slots) ?: return
    val slot = host.slots[target.slot]
    val density = LocalDensity.current
    val ink = if (target.valid) MaterialTheme.colorScheme.primary
    else MaterialTheme.colorScheme.error
    val shape = RoundedCornerShape(VideoStudio.ClipSelected)
    val widthPx = (drag.durationTicks * drag.pixelsPerTick).coerceAtLeast(1f)
    Box(
        Modifier
            .offset {
                IntOffset(
                    (target.startTicks * drag.pixelsPerTick).roundToInt(),
                    slot.top.roundToInt()
                )
            }
            .size(
                width = with(density) { widthPx.toDp() },
                height = with(density) { slot.height.toDp() }
            )
            .clip(shape)
            .background(ink.copy(alpha = 0.22f))
            .border(3.dp, ink, shape)
            .testTag(TAG_VIDEO_DROP_GHOST)
    )
}
