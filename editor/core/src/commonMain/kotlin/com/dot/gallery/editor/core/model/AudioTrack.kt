package com.dot.gallery.editor.core.model

import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.persistentListOf

/**
 * An ordered lane of non-overlapping [AudioClip]s — an independent music,
 * voice-over or effects lane with its own [gainDb], [pan], [muted] and
 * [solo]. Clip ids are unique within the track and clips must already be
 * sorted by [AudioClip.timelineStart] — the constructor rejects unsorted or
 * overlapping input rather than re-ordering it.
 *
 * Mix semantics: [muted] excludes the whole lane; when any audio track is
 * solo, only solo tracks are audible; [locked] blocks edits.
 *
 * Track effects: [eq] is the lane's post-fader insert (bypass/neutral by
 * default) and [ducking] pulls this lane down while another lane's signal
 * plays — the sidechain trigger the sequence validates by id.
 */
data class AudioTrack(
    val id: String,
    val name: String = id,
    val clips: PersistentList<AudioClip> = persistentListOf(),
    val muted: Boolean = false,
    val solo: Boolean = false,
    val gainDb: Float = 0f,
    val pan: Float = 0f,
    val locked: Boolean = false,
    val eq: AudioEq = AudioEq(),
    val ducking: AudioDucking = AudioDucking()
) {
    init {
        require(id.isNotBlank()) { "audio track id must be non-blank" }
        AudioRouting.validateGain(gainDb, "gainDb")
        require(pan.isFinite() && pan in -1f..1f) {
            "track pan must be in [-1, 1]: $pan"
        }
        require(clips.map { it.id }.toSet().size == clips.size) {
            "clip ids must be unique within a track"
        }
        for (index in 1 until clips.size) {
            val previous = clips[index - 1].timelineRange
            val current = clips[index].timelineRange
            require(current.start >= previous.endExclusive) {
                "clips must be sorted and non-overlapping: " +
                    "${clips[index - 1].id} ends at ${previous.endExclusive.ticks}, " +
                    "${clips[index].id} starts at ${current.start.ticks}"
            }
        }
    }
}
