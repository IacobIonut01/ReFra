package com.dot.gallery.editor.render.video

import com.dot.gallery.editor.core.model.AudioDucking
import com.dot.gallery.editor.core.model.AudioEq
import com.dot.gallery.editor.core.model.AudioTrack

/**
 * The nullable-field header update for one [AudioTrack] — fader, pan,
 * mute/solo, lock and the lane's [AudioEq]/[AudioDucking] inserts each
 * write their own field inside one history step. Id and the clip list
 * are never editable here; the model's own validation enforces that
 * anyway.
 */
data class AudioTrackEdit(
    val name: String? = null,
    val gainDb: Float? = null,
    val pan: Float? = null,
    val muted: Boolean? = null,
    val solo: Boolean? = null,
    val locked: Boolean? = null,
    val eq: AudioEq? = null,
    val ducking: AudioDucking? = null
) {
    fun applyTo(track: AudioTrack): AudioTrack = track.copy(
        name = name ?: track.name,
        gainDb = gainDb ?: track.gainDb,
        pan = pan ?: track.pan,
        muted = muted ?: track.muted,
        solo = solo ?: track.solo,
        locked = locked ?: track.locked,
        eq = eq ?: track.eq,
        ducking = ducking ?: track.ducking
    )
}
