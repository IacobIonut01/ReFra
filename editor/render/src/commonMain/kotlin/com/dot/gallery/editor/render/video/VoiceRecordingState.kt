package com.dot.gallery.editor.render.video

import com.dot.gallery.editor.core.time.EditorTime

/**
 * One live voice-over session in [VideoEditorState]: [phase] counts the
 * lead-in down then tracks the take; [anchor] is the timeline position
 * the take lands at; [level] is the polled input peak for the meter.
 * Null means no recording is in flight.
 */
data class VoiceRecordingState(
    val phase: Phase,
    val anchor: EditorTime,
    val countInMs: Long,
    val monitor: Boolean,
    val level: Float = 0f
) {
    enum class Phase {
        /** The lead-in — the timeline plays before capture starts. */
        CountIn,

        /** The microphone is capturing to the temp take. */
        Recording,

        /** Stop requested — the take is finalizing before import. */
        Finishing
    }
}
