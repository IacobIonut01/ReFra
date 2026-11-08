package com.dot.gallery.editor.render.video

import com.dot.gallery.editor.core.model.AudioClip
import com.dot.gallery.editor.core.model.AudioRouting
import com.dot.gallery.editor.core.time.EditorTime

/**
 * The nullable-field recipe update for one [AudioClip] — the mixer and
 * inspector write only the rows they own, so a gain drag and a fade
 * drag can drive the same open draft without stomping each other.
 * Placement fields (id, source, timeline range) are never editable
 * here; the model's own validation enforces that anyway.
 */
data class AudioClipEdit(
    val gainDb: Float? = null,
    val pan: Float? = null,
    val muted: Boolean? = null,
    val fadeIn: EditorTime? = null,
    val fadeOut: EditorTime? = null,
    val routing: AudioRouting? = null
) {
    fun applyTo(clip: AudioClip): AudioClip = clip.copy(
        gainDb = gainDb ?: clip.gainDb,
        pan = pan ?: clip.pan,
        muted = muted ?: clip.muted,
        fadeIn = fadeIn ?: clip.fadeIn,
        fadeOut = fadeOut ?: clip.fadeOut,
        routing = routing ?: clip.routing
    )
}
