package com.dot.gallery.editor.core.model

import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.persistentListOf

/**
 * An ordered lane of non-overlapping clips. Clip ids are unique within the
 * track and clips must already be sorted by [VideoClip.timelineStart] — the
 * constructor rejects unsorted or overlapping input rather than re-ordering it.
 * [kind] selects the lane's role; [visible] gates compositing (not audio on
 * the Main lane — visibility is a preview/export visual toggle only),
 * [locked] blocks edits, and [name] is the user-facing label defaulting to
 * the id so a legacy track reads exactly as before.
 */
data class VideoTrack(
    val id: String,
    val clips: PersistentList<VideoClip> = persistentListOf(),
    val name: String = id,
    val kind: VideoTrackKind = VideoTrackKind.Main,
    val visible: Boolean = true,
    val locked: Boolean = false
) {
    init {
        require(id.isNotBlank()) { "track id must be non-blank" }
        require(clips.map { it.id }.toSet().size == clips.size) {
            "clip ids must be unique within a track"
        }
        for (index in 1 until clips.size) {
            val previous = clips[index - 1].timelineRange
            val current = clips[index].timelineRange
            require(current.start >= previous.endExclusive) {
                "clips must be sorted and non-overlapping: ${clips[index - 1].id} ends at " +
                    "${previous.endExclusive.ticks}, ${clips[index].id} starts at ${current.start.ticks}"
            }
        }
    }
}
