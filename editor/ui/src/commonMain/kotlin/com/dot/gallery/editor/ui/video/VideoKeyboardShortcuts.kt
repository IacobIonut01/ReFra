package com.dot.gallery.editor.ui.video

import androidx.compose.ui.input.key.Key
import com.dot.gallery.editor.core.time.EditorTime
import com.dot.gallery.editor.render.video.VideoEditorState

/**
 * The video workspace's keyboard vocabulary. Modifier chords (undo,
 * redo, export) and Escape are read in the preview pass so they work from
 * any focused control; single keys are read in the bubbling pass, so a
 * focused text field or precision ruler consumes its own keys first.
 */
internal enum class VideoShortcut {
    Escape,
    Undo,
    Redo,
    Export,
    PlayPause,
    /** J — the engine has no reverse playback: pause and step back one second. */
    StepBackSecond,
    /** K — pause. */
    Pause,
    /** L — play forward. */
    Play,
    PreviousFrame,
    NextFrame,
    ForwardSecond,
    Split,
    Delete
}

/** Chords and Escape — consumed before focused children see them. */
internal fun videoChordShortcut(
    key: Key,
    meta: Boolean,
    shift: Boolean
): VideoShortcut? = when {
    key == Key.Escape -> VideoShortcut.Escape
    meta && key == Key.Z && !shift -> VideoShortcut.Undo
    meta && key == Key.Z && shift -> VideoShortcut.Redo
    meta && key == Key.Y -> VideoShortcut.Redo
    meta && key == Key.S -> VideoShortcut.Export
    else -> null
}

/** Single keys — only when no focused child consumed them. */
internal fun videoKeyShortcut(
    key: Key,
    meta: Boolean,
    shift: Boolean
): VideoShortcut? {
    if (meta) return null
    return when (key) {
        Key.Spacebar -> VideoShortcut.PlayPause
        Key.J -> VideoShortcut.StepBackSecond
        Key.K -> VideoShortcut.Pause
        Key.L -> VideoShortcut.Play
        Key.DirectionLeft ->
            if (shift) VideoShortcut.StepBackSecond else VideoShortcut.PreviousFrame
        Key.DirectionRight ->
            if (shift) VideoShortcut.ForwardSecond else VideoShortcut.NextFrame
        Key.S -> if (shift) null else VideoShortcut.Split
        Key.Delete, Key.Backspace -> VideoShortcut.Delete
        else -> null
    }
}

/**
 * Runs [shortcut] against the session callbacks. Returns false when the
 * state refuses it (nothing to undo, a live draft, no clip selected) so
 * the event keeps propagating.
 */
internal fun runVideoShortcut(
    shortcut: VideoShortcut,
    state: VideoEditorState,
    actions: VideoEditorActions,
    onExport: () -> Unit,
    onEscape: () -> Unit
): Boolean {
    val sequence = state.sequence
    fun seekBy(ticks: Long): Boolean {
        val seq = sequence ?: return false
        if (seq.duration.ticks <= 0L) return false
        actions.seek(
            EditorTime(state.position.ticks + ticks)
                .coerceIn(EditorTime.Zero, seq.duration)
        )
        return true
    }
    val editable = sequence != null && !state.isDrafting && !state.exporting &&
        !state.opening
    return when (shortcut) {
        VideoShortcut.Escape -> { onEscape(); true }
        VideoShortcut.Undo ->
            if (state.canUndo && !state.isDrafting) { actions.undo(); true } else false
        VideoShortcut.Redo ->
            if (state.canRedo && !state.isDrafting) { actions.redo(); true } else false
        VideoShortcut.Export ->
            if (editable && (sequence?.duration?.ticks ?: 0L) > 0L) {
                onExport(); true
            } else false
        VideoShortcut.PlayPause -> {
            if (sequence == null || state.opening) return false
            if (state.playing) actions.pause() else actions.play()
            true
        }
        VideoShortcut.Pause -> { if (state.playing) actions.pause(); sequence != null }
        VideoShortcut.Play -> {
            if (sequence == null || state.opening) return false
            if (!state.playing) actions.play()
            true
        }
        VideoShortcut.StepBackSecond -> {
            if (state.playing) actions.pause()
            seekBy(-EditorTime.fromMicroseconds(1_000_000).ticks)
        }
        VideoShortcut.ForwardSecond ->
            seekBy(EditorTime.fromMicroseconds(1_000_000).ticks)
        VideoShortcut.PreviousFrame ->
            seekBy(-(sequence?.frameRate?.ticksPerFrame ?: return false))
        VideoShortcut.NextFrame ->
            seekBy(sequence?.frameRate?.ticksPerFrame ?: return false)
        VideoShortcut.Split ->
            if (editable && state.selectedClipId != null) { actions.split(); true } else false
        VideoShortcut.Delete ->
            if (editable && state.selectedClipId != null) { actions.delete(); true } else false
    }
}
