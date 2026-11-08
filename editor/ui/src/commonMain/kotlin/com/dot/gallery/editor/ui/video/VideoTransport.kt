package com.dot.gallery.editor.ui.video

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
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
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.focusable
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dot.gallery.editor.core.time.EditorTime
import com.dot.gallery.editor.render.video.VideoEditorState
import com.dot.gallery.editor.ui.WorkbenchIconButton
import com.dot.gallery.editor.ui.generated.resources.Res
import com.dot.gallery.editor.ui.generated.resources.editor_undo
import com.dot.gallery.editor.ui.generated.resources.editor_redo
import com.dot.gallery.editor.ui.generated.resources.editor_video_exit_focus
import com.dot.gallery.editor.ui.generated.resources.editor_video_focus_canvas
import com.dot.gallery.editor.ui.generated.resources.editor_video_mute
import com.dot.gallery.editor.ui.generated.resources.editor_video_muted_note
import com.dot.gallery.editor.ui.generated.resources.editor_video_next_frame
import com.dot.gallery.editor.ui.generated.resources.editor_video_pause
import com.dot.gallery.editor.ui.generated.resources.editor_video_play
import com.dot.gallery.editor.ui.generated.resources.editor_video_prev_frame
import com.dot.gallery.editor.ui.generated.resources.editor_video_unmute
import com.dot.gallery.editor.ui.icons.EditorSymbols
import com.dot.gallery.editor.ui.icons.Fullscreen
import com.dot.gallery.editor.ui.icons.FullscreenExit
import com.dot.gallery.editor.ui.icons.KeyboardArrowLeft
import com.dot.gallery.editor.ui.icons.KeyboardArrowRight
import com.dot.gallery.editor.ui.icons.Pause
import com.dot.gallery.editor.ui.icons.PlayArrow
import com.dot.gallery.editor.ui.icons.Redo
import com.dot.gallery.editor.ui.icons.Undo
import com.dot.gallery.editor.ui.icons.VolumeOff
import com.dot.gallery.editor.ui.icons.VolumeUp
import org.jetbrains.compose.resources.stringResource

internal const val TAG_VIDEO_TRANSPORT = "videoEditor.transport"
internal const val TAG_VIDEO_PLAY = "videoEditor.play"
internal const val TAG_VIDEO_PREV_FRAME = "videoEditor.prevFrame"
internal const val TAG_VIDEO_NEXT_FRAME = "videoEditor.nextFrame"
internal const val TAG_VIDEO_MUTE = "videoEditor.mute"
internal const val TAG_VIDEO_TIMECODE = "videoEditor.timecode"
internal const val TAG_VIDEO_FOCUS = "videoEditor.focusCanvas"

/**
 * The transport strip — media controls under the preview: the mm:ss.mmm
 * elapsed/duration readout on the left, frame-step/play/next centred, the
 * preview-sound toggle and the Focus canvas control on the right. Arrow
 * keys step one frame while focused; Space toggles playback. The mute is a
 * monitor preference only — the muted note says the export keeps audio.
 *
 * Compact mode is the Figma V2 single 56 dp row: Previous 48, the 56 dp
 * filled Play circle, Next 48, then the 13 sp elapsed/duration readout —
 * Undo/Redo moved to the header, Focus to the dock.
 * Wide mode stays one row and only stacks at ≥1.5× font or under ~400dp.
 */
@Composable
internal fun VideoTransport(
    state: VideoEditorState,
    actions: VideoEditorActions,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
    focusRequester: FocusRequester? = null,
    canvasFocused: Boolean = false,
    onToggleFocus: () -> Unit = {},
    // The compact row's Focus toggle — the landscape canvas column has
    // no header menu to carry it.
    showFocus: Boolean = false,
    // Landscape's rail leaves no room for history; it rides this row.
    showHistory: Boolean = false
) {
    val sequence = state.sequence
    val hasFrames = sequence != null && sequence.duration.ticks > 0L

    fun step(delta: Long) {
        val seq = sequence ?: return
        val target = EditorTime(state.position.ticks + delta)
            .coerceIn(EditorTime.Zero, seq.duration)
        actions.seek(target)
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .then(
                if (focusRequester != null) {
                    Modifier.focusRequester(focusRequester)
                } else {
                    Modifier
                }
            )
            .onKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown || !hasFrames) {
                    return@onKeyEvent false
                }
                val ticksPerFrame = sequence.frameRate.ticksPerFrame
                when (event.key) {
                    Key.DirectionLeft -> {
                        step(-ticksPerFrame); true
                    }
                    Key.DirectionRight -> {
                        step(ticksPerFrame); true
                    }
                    Key.Spacebar -> {
                        if (state.playing) actions.pause() else actions.play()
                        true
                    }
                    else -> false
                }
            }
            .focusable()
            .testTag(TAG_VIDEO_TRANSPORT)
            .padding(horizontal = if (compact) 0.dp else 8.dp)
    ) {
        val stacked = !compact &&
            (LocalDensity.current.fontScale >= 1.5f || maxWidth < 400.dp)
        if (compact) {
            // The V4 phone transport: the circular Play control, the
            // elapsed/duration readout and the preview-sound toggle.
            // Frame stepping stays on the keyboard, the Trim inspector's
            // ±frame commands and the wide transport.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                CompactPlayButton(
                    state = state,
                    enabled = hasFrames && !state.opening,
                    actions = actions
                )
                Timecode(
                    state,
                    compact = true,
                    modifier = Modifier.weight(1f)
                )
                if (showHistory) {
                    WorkbenchIconButton(
                        icon = EditorSymbols.Undo,
                        label = stringResource(Res.string.editor_undo),
                        onClick = actions.undo,
                        enabled = state.canUndo && !state.isDrafting,
                        modifier = Modifier.testTag(TAG_VIDEO_UNDO)
                    )
                    WorkbenchIconButton(
                        icon = EditorSymbols.Redo,
                        label = stringResource(Res.string.editor_redo),
                        onClick = actions.redo,
                        enabled = state.canRedo && !state.isDrafting,
                        modifier = Modifier.testTag(TAG_VIDEO_REDO)
                    )
                }
                MuteButton(state, actions)
                if (showFocus) FocusButton(canvasFocused, onToggleFocus)
            }
        } else if (stacked) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    PrevButton(hasFrames) {
                        step(-sequence!!.frameRate.ticksPerFrame)
                    }
                    PlayButton(state, hasFrames, actions)
                    NextButton(hasFrames) {
                        step(sequence!!.frameRate.ticksPerFrame)
                    }
                    Spacer(Modifier.weight(1f))
                    MuteButton(state, actions)
                    FocusButton(canvasFocused, onToggleFocus)
                }
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 4.dp)
                ) {
                    Timecode(state)
                    if (state.muted && state.info?.hasAudio == true) {
                        MutedNote()
                    }
                }
            }
        } else {
            Box(modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                Column(
                    modifier = Modifier.align(Alignment.CenterStart)
                ) {
                    Timecode(state)
                    if (state.muted && state.info?.hasAudio == true) {
                        MutedNote()
                    }
                }
                Row(
                    modifier = Modifier.align(Alignment.Center),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    PrevButton(hasFrames) {
                        step(-sequence!!.frameRate.ticksPerFrame)
                    }
                    PlayButton(state, hasFrames, actions)
                    NextButton(hasFrames) {
                        step(sequence!!.frameRate.ticksPerFrame)
                    }
                }
                Row(
                    modifier = Modifier.align(Alignment.CenterEnd),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    MuteButton(state, actions)
                    FocusButton(canvasFocused, onToggleFocus)
                }
            }
        }
    }
}

/**
 * The compact transport's 56 dp Play/Pause slot — a flat 24 dp icon, no
 * disc, matching the Figma V2 phone transport. The slot stays 56 dp so
 * the primary action keeps the largest touch target of the row.
 */
@Composable
private fun CompactPlayButton(
    state: VideoEditorState,
    enabled: Boolean,
    actions: VideoEditorActions
) {
    val label = stringResource(
        if (state.playing) Res.string.editor_video_pause
        else Res.string.editor_video_play
    )
    Box(
        modifier = Modifier
            .size(48.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .clickable(
                enabled = enabled,
                role = Role.Button,
                onClickLabel = label,
                onClick = {
                    if (state.playing) actions.pause() else actions.play()
                }
            )
            .testTag(TAG_VIDEO_PLAY),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = if (state.playing) EditorSymbols.Pause
            else EditorSymbols.PlayArrow,
            contentDescription = label,
            tint = if (enabled) MaterialTheme.colorScheme.onSurface
            else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
            modifier = Modifier.size(24.dp)
        )
    }
}

@Composable
private fun PrevButton(
    enabled: Boolean,
    compact: Boolean = false,
    onClick: () -> Unit
) {
    WorkbenchIconButton(
        // The compact row carries the approved curved frame-step glyphs.
        icon = if (compact) EditorSymbols.Undo
        else EditorSymbols.KeyboardArrowLeft,
        label = stringResource(Res.string.editor_video_prev_frame),
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.testTag(TAG_VIDEO_PREV_FRAME)
    )
}

@Composable
private fun PlayButton(
    state: VideoEditorState,
    hasFrames: Boolean,
    actions: VideoEditorActions
) {
    WorkbenchIconButton(
        icon = if (state.playing) EditorSymbols.Pause
        else EditorSymbols.PlayArrow,
        label = stringResource(
            if (state.playing) Res.string.editor_video_pause
            else Res.string.editor_video_play
        ),
        onClick = {
            if (state.playing) actions.pause() else actions.play()
        },
        enabled = hasFrames && !state.opening,
        modifier = Modifier.testTag(TAG_VIDEO_PLAY)
    )
}

@Composable
private fun NextButton(
    enabled: Boolean,
    compact: Boolean = false,
    onClick: () -> Unit
) {
    WorkbenchIconButton(
        icon = if (compact) EditorSymbols.Redo
        else EditorSymbols.KeyboardArrowRight,
        label = stringResource(Res.string.editor_video_next_frame),
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.testTag(TAG_VIDEO_NEXT_FRAME)
    )
}

@Composable
private fun FocusButton(focused: Boolean, onClick: () -> Unit) {
    WorkbenchIconButton(
        icon = if (focused) EditorSymbols.FullscreenExit
        else EditorSymbols.Fullscreen,
        label = stringResource(
            if (focused) Res.string.editor_video_exit_focus
            else Res.string.editor_video_focus_canvas
        ),
        onClick = onClick,
        selected = focused,
        modifier = Modifier.testTag(TAG_VIDEO_FOCUS)
    )
}

@Composable
private fun Timecode(
    state: VideoEditorState,
    modifier: Modifier = Modifier,
    compact: Boolean = false
) {
    Text(
        text = formatVideoTimecode(state.position) + " / " +
            formatVideoTimecode(state.sequence?.duration ?: EditorTime.Zero),
        style = if (compact) {
            MaterialTheme.typography.labelLarge.copy(
                fontSize = 13.sp,
                fontFeatureSettings = "tnum"
            )
        } else {
            MaterialTheme.typography.labelLarge.copy(
                fontFeatureSettings = "tnum"
            )
        },
        // Enlarged type wraps the duration under the elapsed time
        // instead of clipping it on a phone row.
        maxLines = if (compact) 2 else 1,
        modifier = modifier
            .then(if (compact) Modifier else Modifier.padding(start = 8.dp))
            .testTag(TAG_VIDEO_TIMECODE)
    )
}

@Composable
private fun MutedNote(modifier: Modifier = Modifier) {
    Text(
        text = stringResource(Res.string.editor_video_muted_note),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier
    )
}

@Composable
private fun MuteButton(state: VideoEditorState, actions: VideoEditorActions) {
    WorkbenchIconButton(
        icon = if (state.muted) EditorSymbols.VolumeOff
        else EditorSymbols.VolumeUp,
        label = stringResource(
            if (state.muted) Res.string.editor_video_unmute
            else Res.string.editor_video_mute
        ),
        onClick = { actions.setMuted(!state.muted) },
        // No audio stream means there is nothing to monitor.
        enabled = state.info?.hasAudio == true,
        selected = state.muted,
        modifier = Modifier.testTag(TAG_VIDEO_MUTE)
    )
}
