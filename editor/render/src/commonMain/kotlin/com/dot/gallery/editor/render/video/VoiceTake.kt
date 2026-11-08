package com.dot.gallery.editor.render.video

import com.dot.gallery.editor.core.time.EditorTime

/**
 * A finalized voice-over take: [reference] resolves through the same
 * media pipeline as any imported audio (`file://` on both hosts), and
 * the audio facts are known from the recorder — no probe needed to
 * place the clip.
 */
data class VoiceTake(
    val reference: String,
    val displayName: String,
    val duration: EditorTime,
    val sampleRate: Int,
    val channels: Int
)
