package com.dot.gallery.editor.core.model

import com.dot.gallery.editor.core.time.EditorTime
import com.dot.gallery.editor.core.time.FrameRate
import com.dot.gallery.editor.core.time.TimeRange
import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toPersistentList

/**
 * An immutable multi-track video document. Ids are unique across the whole
 * sequence — sources, audio sources, tracks, audio tracks and clips share
 * one id space so no reference can be ambiguous. Every clip must reference a
 * declared entry of the matching kind — [VideoClip.sourceId] into [sources],
 * [AudioClip.sourceId] into [audioSources] — and its source range must lie
 * inside that source's duration. At most one [VideoTrackKind.Main] lane
 * exists; [VideoTrackKind.Audio] lanes stay rejected inside [tracks] because
 * audio lives in the separate [audioTracks] list (a different clip model).
 * Every track's clips are sorted and non-overlapping (enforced by the track
 * constructors); clips on *different* tracks freely overlap — that is what
 * compositing and mixing are for.
 *
 * [sourceSoundMuted] drops the Main lane's embedded audio from the mix —
 * the document-level switch the mixer honours; individual audio lanes mute
 * through their own [AudioTrack.muted]/[AudioClip.muted].
 *
 * [masterGainDb] is the output gain applied to the accumulated mix in
 * [-60, 12] — it lands on the float sum before the limiter, so every
 * input hears it equally. [limiterEnabled] is the hard full-scale
 * protection: on, the mixer's output ceiling is a windowed peak
 * normalization that preserves the waveform; off, peaks hard-clip at
 * the s16 boundary.
 *
 * Validation failures throw [IllegalArgumentException] and leave the original
 * sequence untouched; these are library invariants, and the UI boundary is
 * responsible for turning them into user-facing failures.
 */
data class VideoSequence(
    val size: PixelSize,
    val frameRate: FrameRate,
    val sources: PersistentList<MediaAsset.Video>,
    val tracks: PersistentList<VideoTrack>,
    val audioSources: PersistentList<MediaAsset.Audio> =
        persistentListOf(),
    val audioTracks: PersistentList<AudioTrack> = persistentListOf(),
    val sourceSoundMuted: Boolean = false,
    val masterGainDb: Float = 0f,
    val limiterEnabled: Boolean = true
) {
    init {
        AudioRouting.validateGain(masterGainDb, "masterGainDb")
        val seenIds = HashSet<String>()
        for (source in sources) {
            require(seenIds.add(source.id)) { "duplicate id in sequence: ${source.id}" }
        }
        for (source in audioSources) {
            require(seenIds.add(source.id)) { "duplicate id in sequence: ${source.id}" }
        }
        val sourcesById = HashMap<String, MediaAsset.Video>(sources.size)
        for (source in sources) {
            sourcesById[source.id] = source
        }
        val audioSourcesById =
            HashMap<String, MediaAsset.Audio>(audioSources.size)
        for (source in audioSources) {
            audioSourcesById[source.id] = source
        }
        var mainTracks = 0
        for (track in tracks) {
            require(seenIds.add(track.id)) { "duplicate id in sequence: ${track.id}" }
            when (track.kind) {
                VideoTrackKind.Main -> mainTracks++
                VideoTrackKind.Audio -> throw IllegalArgumentException(
                    "audio lanes live in audioTracks, not tracks: ${track.id}"
                )
                else -> Unit
            }
            for (clip in track.clips) {
                require(seenIds.add(clip.id)) { "duplicate id in sequence: ${clip.id}" }
                val source = sourcesById[clip.sourceId]
                    ?: throw IllegalArgumentException(
                        "clip ${clip.id} references unknown source ${clip.sourceId}"
                    )
                require(clip.sourceRange.endExclusive <= source.duration) {
                    "clip ${clip.id} source range exceeds source duration " +
                        "${source.duration.ticks}"
                }
            }
        }
        for (track in audioTracks) {
            require(seenIds.add(track.id)) { "duplicate id in sequence: ${track.id}" }
            for (clip in track.clips) {
                require(seenIds.add(clip.id)) { "duplicate id in sequence: ${clip.id}" }
                val source = audioSourcesById[clip.sourceId]
                    ?: throw IllegalArgumentException(
                        "audio clip ${clip.id} references unknown source " +
                            clip.sourceId
                    )
                require(clip.sourceRange.endExclusive <= source.duration) {
                    "audio clip ${clip.id} source range exceeds source duration " +
                        "${source.duration.ticks}"
                }
            }
        }
        for (track in audioTracks) {
            val ducking = track.ducking
            if (!ducking.enabled) continue
            val trigger = audioTracks.firstOrNull {
                it.id == ducking.triggerTrackId
            } ?: throw IllegalArgumentException(
                "audio track ${track.id} ducks to unknown trigger " +
                    ducking.triggerTrackId
            )
            require(trigger.id != track.id) {
                "audio track ${track.id} cannot duck to itself"
            }
        }
        require(mainTracks <= 1) {
            "at most one Main track is allowed (found $mainTracks)"
        }
    }

    /** The [VideoTrackKind.Main] lane, or null when the sequence has none. */
    val mainTrack: VideoTrack?
        get() = tracks.firstOrNull { it.kind == VideoTrackKind.Main }

    /** Declared source by id, or null when absent (validation guarantees presence for clips). */
    fun sourceById(id: String): MediaAsset.Video? =
        sources.firstOrNull { it.id == id }

    /** Declared audio source by id, or null when absent. */
    fun audioSourceById(id: String): MediaAsset.Audio? =
        audioSources.firstOrNull { it.id == id }

    /**
     * The (track, clip) audio placements covering sequence time [at], in
     * [audioTracks] order — every active clip including muted ones; the
     * mixer owns mute/solo/gain semantics and needs the track alongside
     * the clip to compute them. Clips never overlap within a track, so at
     * most one clip per track lands in the result.
     */
    fun activeAudioClips(at: EditorTime): List<Pair<AudioTrack, AudioClip>> {
        val result = ArrayList<Pair<AudioTrack, AudioClip>>(audioTracks.size)
        for (track in audioTracks) {
            for (clip in track.clips) {
                if (at in clip.timelineRange) {
                    result.add(track to clip)
                    break
                }
            }
        }
        return result
    }

    /**
     * The clips composited at sequence time [at]: the Main clip first (when
     * its track is visible), then every visible [VideoTrackKind.Overlay]
     * track's covering clip in track-list order — bottom to top, exactly
     * the compositor's paint order. [VideoTrackKind.Title] clips are
     * ignored until title rendering exists. A clip with `visible = false`
     * contributes nothing.
     */
    fun activeVisualClips(at: EditorTime): List<VideoClip> {
        val result = ArrayList<VideoClip>(tracks.size)
        for (track in tracks) {
            if (!track.visible) continue
            if (track.kind != VideoTrackKind.Main &&
                track.kind != VideoTrackKind.Overlay
            ) continue
            for (clip in track.clips) {
                if (clip.visible && at in clip.timelineRange) {
                    result.add(clip)
                    break // clips never overlap within a track
                }
            }
        }
        return result
    }

    /**
     * Appends [source] to [sources]. The source id must be unique across the
     * whole id space (sources, tracks and clips share it).
     */
    fun addSource(source: MediaAsset.Video): VideoSequence =
        copy(sources = sources.add(source))

    /**
     * Appends [track] to [tracks]. Kind rules apply on copy: a second Main
     * or an Audio lane is rejected.
     */
    fun addTrack(track: VideoTrack): VideoSequence =
        copy(tracks = tracks.add(track))

    /**
     * Inserts [clip] into [trackId], keeping the track sorted by
     * [VideoClip.timelineStart].
     *
     * `ripple = false` places the clip exactly where its [timelineStart]
     * says — the rebuilt track's own validation rejects an overlap, and an
     * insertion strictly inside an existing clip is allowed only when the
     * caller has already split that clip (a straddling placement is an
     * overlap and fails the same way).
     *
     * `ripple = true` shifts every existing clip whose [timelineStart] is
     * at or after the insertion point right by the new clip's duration —
     * the packed-timeline insert. An existing clip strictly containing the
     * insertion point is rejected: the caller must split first, so the
     * split and the shift stay explicit.
     */
    fun insertClip(
        trackId: String,
        clip: VideoClip,
        ripple: Boolean
    ): VideoSequence {
        val trackIndex = tracks.indexOfFirst { it.id == trackId }
        require(trackIndex >= 0) { "unknown track: $trackId" }
        val track = tracks[trackIndex]
        val point = clip.timelineStart
        val shifted = track.clips.map { existing ->
            if (!ripple) {
                existing
            } else {
                val range = existing.timelineRange
                require(point !in range || point == range.start) {
                    "insert point ${point.ticks} falls inside clip " +
                        "${existing.id}; split it first"
                }
                if (point <= range.start) {
                    existing.copy(timelineStart = range.start + clip.sourceRange.duration)
                } else {
                    existing
                }
            }
        }.toPersistentList()
        var insertIndex = shifted.size
        for (index in shifted.indices) {
            if (shifted[index].timelineStart > point) {
                insertIndex = index
                break
            }
            insertIndex = index + 1
        }
        return copy(
            tracks = tracks.replacingAt(
                trackIndex,
                track.copy(clips = shifted.addingAt(insertIndex, clip))
            )
        )
    }

    /**
     * Duplicates [clipId] within [trackId] as [newClipId], placed
     * immediately after the original's end. On a Main (packed) lane the
     * later clips ripple right; on any other lane the copy only lands when
     * the slot is free — otherwise the rebuilt track's overlap check
     * rejects it.
     */
    fun duplicateClip(
        trackId: String,
        clipId: String,
        newClipId: String
    ): VideoSequence {
        val trackIndex = tracks.indexOfFirst { it.id == trackId }
        require(trackIndex >= 0) { "unknown track: $trackId" }
        val track = tracks[trackIndex]
        val original = track.clips.firstOrNull { it.id == clipId }
            ?: throw IllegalArgumentException("unknown clip: $clipId")
        val copy = original.copy(
            id = newClipId,
            timelineStart = original.timelineRange.endExclusive
        )
        return insertClip(
            trackId, copy, ripple = track.kind == VideoTrackKind.Main
        )
    }

    /**
     * Removes [sourceId] — only while no clip references it. A still-used
     * source is rejected: dropping it would corrupt the document rather
     * than preserve it.
     */
    fun removeSource(sourceId: String): VideoSequence {
        require(sources.any { it.id == sourceId }) { "unknown source: $sourceId" }
        val used = tracks.any { track ->
            track.clips.any { it.sourceId == sourceId }
        }
        require(!used) { "source $sourceId is still referenced by a clip" }
        return copy(
            sources = sources.filterNot { it.id == sourceId }.toPersistentList()
        )
    }

    /** Appends [source] to [audioSources]; ids stay globally unique. */
    fun addAudioSource(source: MediaAsset.Audio): VideoSequence =
        copy(audioSources = audioSources.add(source))

    /** Appends [track] to [audioTracks]. */
    fun addAudioTrack(track: AudioTrack): VideoSequence =
        copy(audioTracks = audioTracks.add(track))

    /**
     * Inserts [clip] into audio [trackId], keeping the track sorted by
     * [AudioClip.timelineStart]. Audio lanes are not packed — the clip
     * lands exactly where its placement says, and an overlap is rejected
     * by the rebuilt track's own validation.
     */
    fun insertAudioClip(trackId: String, clip: AudioClip): VideoSequence {
        val trackIndex = audioTracks.indexOfFirst { it.id == trackId }
        require(trackIndex >= 0) { "unknown audio track: $trackId" }
        val track = audioTracks[trackIndex]
        var insertIndex = track.clips.size
        for (index in track.clips.indices) {
            if (track.clips[index].timelineStart > clip.timelineStart) {
                insertIndex = index
                break
            }
        }
        return copy(
            audioTracks = audioTracks.replacingAt(
                trackIndex, track.copy(clips = track.clips.addingAt(insertIndex, clip))
            )
        )
    }

    /** Removes [clipId] from audio [trackId] — gaps are left in place. */
    fun removeAudioClip(trackId: String, clipId: String): VideoSequence {
        val trackIndex = audioTracks.indexOfFirst { it.id == trackId }
        require(trackIndex >= 0) { "unknown audio track: $trackId" }
        val track = audioTracks[trackIndex]
        val clipIndex = track.clips.indexOfFirst { it.id == clipId }
        require(clipIndex >= 0) { "unknown audio clip: $clipId" }
        return copy(
            audioTracks = audioTracks.replacingAt(
                trackIndex,
                track.copy(clips = track.clips.removingAt(clipIndex))
            )
        )
    }

    /**
     * Reposition [clipId] to [newStart] on audio [trackId] and re-sort the
     * lane; an overlap with a neighbour is rejected by the rebuilt track.
     */
    fun moveAudioClip(
        trackId: String,
        clipId: String,
        newStart: EditorTime
    ): VideoSequence {
        val trackIndex = audioTracks.indexOfFirst { it.id == trackId }
        require(trackIndex >= 0) { "unknown audio track: $trackId" }
        val track = audioTracks[trackIndex]
        val clipIndex = track.clips.indexOfFirst { it.id == clipId }
        require(clipIndex >= 0) { "unknown audio clip: $clipId" }
        val moved = track.clips[clipIndex].copy(timelineStart = newStart)
        val sorted = track.clips.replacingAt(clipIndex, moved)
            .sortedBy { it.timelineStart.ticks }
            .toPersistentList()
        return copy(
            audioTracks = audioTracks.replacingAt(
                trackIndex, track.copy(clips = sorted)
            )
        )
    }

    /**
     * Re-points an audio clip's source range — [AudioClip.timelineStart]
     * stays anchored and the clip's timeline duration follows the new
     * range, so a following overlap is rejected by the rebuilt track.
     */
    fun trimAudioClip(
        trackId: String,
        clipId: String,
        sourceRange: TimeRange
    ): VideoSequence {
        val trackIndex = audioTracks.indexOfFirst { it.id == trackId }
        require(trackIndex >= 0) { "unknown audio track: $trackId" }
        val track = audioTracks[trackIndex]
        val clipIndex = track.clips.indexOfFirst { it.id == clipId }
        require(clipIndex >= 0) { "unknown audio clip: $clipId" }
        val newClips =
            track.clips.replacingAt(clipIndex, track.clips[clipIndex].copy(sourceRange = sourceRange))
        return copy(
            audioTracks = audioTracks.replacingAt(
                trackIndex, track.copy(clips = newClips)
            )
        )
    }

    /**
     * Rewrites [clipId]'s non-timing mix recipe through [update] — gain,
     * pan, mute, fades and routing are free to change; the placement
     * fields (id, sourceId, timelineStart, sourceRange) must come back
     * untouched, same contract as [updateClip].
     */
    fun updateAudioClip(
        trackId: String,
        clipId: String,
        update: (AudioClip) -> AudioClip
    ): VideoSequence {
        val trackIndex = audioTracks.indexOfFirst { it.id == trackId }
        require(trackIndex >= 0) { "unknown audio track: $trackId" }
        val track = audioTracks[trackIndex]
        val clipIndex = track.clips.indexOfFirst { it.id == clipId }
        require(clipIndex >= 0) { "unknown audio clip: $clipId" }
        val clip = track.clips[clipIndex]
        val updated = update(clip)
        require(
            updated.id == clip.id && updated.sourceId == clip.sourceId &&
                updated.timelineStart == clip.timelineStart &&
                updated.sourceRange == clip.sourceRange
        ) {
            "a recipe edit cannot move audio clip $clipId in time or source"
        }
        return copy(
            audioTracks = audioTracks.replacingAt(
                trackIndex,
                track.copy(clips = track.clips.replacingAt(clipIndex, updated))
            )
        )
    }

    /**
     * Rewrites [trackId]'s header through [update] — name, mute, solo,
     * gain, pan and lock are free to change; id and the clip list must
     * come back untouched, same contract as [updateTrack].
     */
    fun updateAudioTrack(
        trackId: String,
        update: (AudioTrack) -> AudioTrack
    ): VideoSequence {
        val trackIndex = audioTracks.indexOfFirst { it.id == trackId }
        require(trackIndex >= 0) { "unknown audio track: $trackId" }
        val track = audioTracks[trackIndex]
        val updated = update(track)
        require(updated.id == track.id && updated.clips == track.clips) {
            "a header edit cannot change audio track $trackId's id or clips"
        }
        return copy(audioTracks = audioTracks.replacingAt(trackIndex, updated))
    }

    /**
     * Moves audio [clipId] from lane [fromTrackId] onto lane [toTrackId]
     * at [newStart] — the cross-lane drag. Source range and mix recipe
     * travel with the clip; within one lane this is [moveAudioClip]. An
     * overlap on the target lane is rejected by the rebuilt track.
     */
    fun moveAudioClipToTrack(
        fromTrackId: String,
        clipId: String,
        toTrackId: String,
        newStart: EditorTime
    ): VideoSequence {
        if (fromTrackId == toTrackId) return moveAudioClip(fromTrackId, clipId, newStart)
        val from = audioTracks.firstOrNull { it.id == fromTrackId }
            ?: throw IllegalArgumentException("unknown audio track: $fromTrackId")
        require(audioTracks.any { it.id == toTrackId }) {
            "unknown audio track: $toTrackId"
        }
        val clip = from.clips.firstOrNull { it.id == clipId }
            ?: throw IllegalArgumentException("unknown audio clip: $clipId")
        return removeAudioClip(fromTrackId, clipId)
            .insertAudioClip(toTrackId, clip.copy(timelineStart = newStart))
    }

    /**
     * Reorders audio lane [trackId] to [targetIndex] — the A-lane numbering
     * and mixer strip order follow; every clip stays where it is in time.
     */
    fun moveAudioTrack(trackId: String, targetIndex: Int): VideoSequence {
        val trackIndex = audioTracks.indexOfFirst { it.id == trackId }
        require(trackIndex >= 0) { "unknown audio track: $trackId" }
        require(targetIndex in audioTracks.indices) {
            "target index $targetIndex out of bounds for ${audioTracks.size} audio tracks"
        }
        if (targetIndex == trackIndex) return this
        val track = audioTracks[trackIndex]
        return copy(
            audioTracks = audioTracks.removingAt(trackIndex).addingAt(targetIndex, track)
        )
    }

    /**
     * Removes audio lane [trackId] with its clips. Lanes that ducked to it
     * lose the now-dangling sidechain (ducking switches off, the rest of
     * its settings stay), and audio sources only the removed clips
     * referenced leave with them.
     */
    fun removeAudioTrack(trackId: String): VideoSequence {
        val trackIndex = audioTracks.indexOfFirst { it.id == trackId }
        require(trackIndex >= 0) { "unknown audio track: $trackId" }
        val removed = audioTracks[trackIndex]
        val remaining = audioTracks.removingAt(trackIndex).map { track ->
            if (track.ducking.triggerTrackId == trackId) {
                track.copy(
                    ducking = track.ducking.copy(enabled = false, triggerTrackId = "")
                )
            } else {
                track
            }
        }.toPersistentList()
        val stillUsed = remaining.flatMapTo(HashSet()) { t -> t.clips.map { it.sourceId } }
        val orphaned = removed.clips.mapTo(HashSet()) { it.sourceId } - stillUsed
        return copy(
            audioTracks = remaining,
            audioSources = audioSources.filterNot { it.id in orphaned }.toPersistentList()
        )
    }

    /** Removes [sourceId] — only while no audio clip references it. */
    fun removeAudioSource(sourceId: String): VideoSequence {
        require(audioSources.any { it.id == sourceId }) {
            "unknown audio source: $sourceId"
        }
        val used = audioTracks.any { track ->
            track.clips.any { it.sourceId == sourceId }
        }
        require(!used) {
            "audio source $sourceId is still referenced by a clip"
        }
        return copy(
            audioSources = audioSources
                .filterNot { it.id == sourceId }
                .toPersistentList()
        )
    }

    /**
     * Splits a clip at [at] in sequence time. The split point must lie strictly
     * inside the clip's timeline placement. The left part keeps [clipId] and the
     * source in-point; the right part gets [newClipId] and a source in-point
     * advanced by the split delta, so the two remain contiguous. [newClipId]
     * must be unique across the whole sequence.
     */
    fun splitClip(
        trackId: String,
        clipId: String,
        at: EditorTime,
        newClipId: String
    ): VideoSequence {
        val trackIndex = tracks.indexOfFirst { it.id == trackId }
        require(trackIndex >= 0) { "unknown track: $trackId" }
        val track = tracks[trackIndex]
        val clipIndex = track.clips.indexOfFirst { it.id == clipId }
        require(clipIndex >= 0) { "unknown clip: $clipId" }
        val clip = track.clips[clipIndex]
        val placement = clip.timelineRange
        require(at > placement.start && at < placement.endExclusive) {
            "split point must lie strictly inside the clip: ${at.ticks}"
        }
        val leftDuration = at - clip.timelineStart
        val rightDuration = clip.sourceRange.duration - leftDuration
        val left = clip.copy(sourceRange = TimeRange(clip.sourceRange.start, leftDuration))
        val right = clip.copy(
            id = newClipId,
            timelineStart = at,
            sourceRange = TimeRange(clip.sourceRange.start + leftDuration, rightDuration)
        )
        val newClips = track.clips
            .removingAt(clipIndex)
            .addingAt(clipIndex, left)
            .addingAt(clipIndex + 1, right)
        return copy(tracks = tracks.replacingAt(trackIndex, track.copy(clips = newClips)))
    }

    /**
     * Re-points a clip's source range. [VideoClip.timelineStart] stays anchored;
     * the clip's timeline duration follows the new source range length. Trims
     * that exceed the source duration or overlap the following clip are
     * rejected.
     */
    fun trimClip(trackId: String, clipId: String, sourceRange: TimeRange): VideoSequence {
        val trackIndex = tracks.indexOfFirst { it.id == trackId }
        require(trackIndex >= 0) { "unknown track: $trackId" }
        val track = tracks[trackIndex]
        val clipIndex = track.clips.indexOfFirst { it.id == clipId }
        require(clipIndex >= 0) { "unknown clip: $clipId" }
        val clip = track.clips[clipIndex]
        val newClips = track.clips.replacingAt(clipIndex, clip.copy(sourceRange = sourceRange))
        return copy(tracks = tracks.replacingAt(trackIndex, track.copy(clips = newClips)))
    }

    /**
     * Total sequence duration — the furthest clip end across visual and
     * audio lanes, or zero when the sequence has no clips. Music running
     * past the last video frame extends the export, it is never trimmed
     * away silently.
     */
    val duration: EditorTime
        get() {
            var end = tracks.maxOfOrNull { track ->
                track.clips.maxOfOrNull { it.timelineRange.endExclusive }
                    ?: EditorTime.Zero
            } ?: EditorTime.Zero
            for (track in audioTracks) {
                val trackEnd = track.clips.maxOfOrNull {
                    it.timelineRange.endExclusive
                } ?: continue
                if (trackEnd > end) end = trackEnd
            }
            return end
        }

    /**
     * Ripple variant of [trimClip] for packed timelines: the clip's source
     * range is replaced and every clip in the track is re-laid consecutively
     * from 0, so the timeline duration follows the new source range with no
     * gaps. Clip ids, order and adjustments are preserved; the global
     * source-range validation still applies (a range past the source's
     * duration is rejected by the rebuilt sequence).
     */
    fun rippleTrimClip(
        trackId: String,
        clipId: String,
        sourceRange: TimeRange
    ): VideoSequence {
        val trackIndex = tracks.indexOfFirst { it.id == trackId }
        require(trackIndex >= 0) { "unknown track: $trackId" }
        val track = tracks[trackIndex]
        val clipIndex = track.clips.indexOfFirst { it.id == clipId }
        require(clipIndex >= 0) { "unknown clip: $clipId" }
        val transformed = track.clips.replacingAt(
            clipIndex, track.clips[clipIndex].copy(sourceRange = sourceRange)
        )
        return copy(
            tracks = tracks.replacingAt(
                trackIndex, track.copy(clips = repack(transformed))
            )
        )
    }

    /**
     * Rewrites [clipId]'s non-timing recipe through [update] — transform,
     * content mode, opacity, blend mode, visibility and colour
     * adjustments are all free to change, but the placement fields
     * (id, sourceId, timelineStart, sourceRange) must come back
     * untouched: moving a clip in time is a structural op, not a recipe
     * edit, and silently dropping the change would corrupt undo diffs.
     */
    fun updateClip(
        trackId: String,
        clipId: String,
        update: (VideoClip) -> VideoClip
    ): VideoSequence {
        val trackIndex = tracks.indexOfFirst { it.id == trackId }
        require(trackIndex >= 0) { "unknown track: $trackId" }
        val track = tracks[trackIndex]
        val clipIndex = track.clips.indexOfFirst { it.id == clipId }
        require(clipIndex >= 0) { "unknown clip: $clipId" }
        val clip = track.clips[clipIndex]
        val updated = update(clip)
        require(
            updated.id == clip.id && updated.sourceId == clip.sourceId &&
                updated.timelineStart == clip.timelineStart &&
                updated.sourceRange == clip.sourceRange
        ) {
            "a recipe edit cannot move clip $clipId in time or source"
        }
        return copy(
            tracks = tracks.replacingAt(
                trackIndex,
                track.copy(clips = track.clips.replacingAt(clipIndex, updated))
            )
        )
    }

    /**
     * Rewrites [trackId]'s header through [update] — name, visibility and
     * lock are free to change; id, kind and the clip list must come back
     * untouched (lane structure is edited through the clip ops).
     */
    fun updateTrack(
        trackId: String,
        update: (VideoTrack) -> VideoTrack
    ): VideoSequence {
        val trackIndex = tracks.indexOfFirst { it.id == trackId }
        require(trackIndex >= 0) { "unknown track: $trackId" }
        val track = tracks[trackIndex]
        val updated = update(track)
        require(
            updated.id == track.id && updated.kind == track.kind &&
                updated.clips == track.clips
        ) {
            "a header edit cannot change track $trackId's id, kind or clips"
        }
        return copy(tracks = tracks.replacingAt(trackIndex, updated))
    }

    /**
     * Moves [clipId] from lane [fromTrackId] to [toTrackId] at [newStart] —
     * free placement between unpacked lanes of the same kind. The packed
     * Main lane takes no part (its order changes through [moveClip]); an
     * overlap on the target lane is rejected by the rebuilt track.
     */
    fun moveClipToTrack(
        fromTrackId: String,
        clipId: String,
        toTrackId: String,
        newStart: EditorTime
    ): VideoSequence {
        val fromIndex = tracks.indexOfFirst { it.id == fromTrackId }
        require(fromIndex >= 0) { "unknown track: $fromTrackId" }
        val toIndex = tracks.indexOfFirst { it.id == toTrackId }
        require(toIndex >= 0) { "unknown track: $toTrackId" }
        val from = tracks[fromIndex]
        val to = tracks[toIndex]
        require(from.kind != VideoTrackKind.Main && to.kind != VideoTrackKind.Main) {
            "the packed Main lane takes no free placement"
        }
        require(from.kind == to.kind) {
            "clips move only between lanes of one kind: ${from.kind} → ${to.kind}"
        }
        val clip = from.clips.firstOrNull { it.id == clipId }
            ?: throw IllegalArgumentException("unknown clip: $clipId")
        val source = from.copy(clips = from.clips.filterNot { it.id == clipId }.toPersistentList())
        val target = if (fromIndex == toIndex) source else to
        val placed = (target.clips + clip.copy(timelineStart = newStart))
            .sortedBy { it.timelineStart.ticks }
            .toPersistentList()
        return copy(
            tracks = tracks
                .replacingAt(fromIndex, source)
                .replacingAt(toIndex, target.copy(clips = placed))
        )
    }

    /**
     * Swaps the positions of two lanes of the same kind — lane numbering
     * and the overlay paint order follow, every other lane keeps its slot.
     * Main is never swapped: it stays the composite's base.
     */
    fun swapTracks(firstId: String, secondId: String): VideoSequence {
        val first = tracks.indexOfFirst { it.id == firstId }
        require(first >= 0) { "unknown track: $firstId" }
        val second = tracks.indexOfFirst { it.id == secondId }
        require(second >= 0) { "unknown track: $secondId" }
        if (first == second) return this
        val a = tracks[first]
        val b = tracks[second]
        require(a.kind != VideoTrackKind.Main && a.kind == b.kind) {
            "only lanes of one non-Main kind swap: ${a.kind} ↔ ${b.kind}"
        }
        return copy(tracks = tracks.replacingAt(first, b).replacingAt(second, a))
    }

    /**
     * Removes the non-Main lane [trackId] with its clips; video sources
     * only the removed clips referenced leave with them.
     */
    fun removeTrack(trackId: String): VideoSequence {
        val trackIndex = tracks.indexOfFirst { it.id == trackId }
        require(trackIndex >= 0) { "unknown track: $trackId" }
        val removed = tracks[trackIndex]
        require(removed.kind != VideoTrackKind.Main) { "the Main lane cannot be removed" }
        val remaining = tracks.removingAt(trackIndex)
        val stillUsed = remaining.flatMapTo(HashSet()) { t -> t.clips.map { it.sourceId } }
        val orphaned = removed.clips.mapTo(HashSet()) { it.sourceId } - stillUsed
        return copy(
            tracks = remaining,
            sources = sources.filterNot { it.id in orphaned }.toPersistentList()
        )
    }

    /**
     * Removes [clipId] from [trackId] and re-packs the remaining clips
     * consecutively from 0 (ripple delete — no gap is left). Deleting the
     * last clip leaves an empty track of duration zero.
     */
    fun removeClip(trackId: String, clipId: String): VideoSequence {
        val trackIndex = tracks.indexOfFirst { it.id == trackId }
        require(trackIndex >= 0) { "unknown track: $trackId" }
        val track = tracks[trackIndex]
        val clipIndex = track.clips.indexOfFirst { it.id == clipId }
        require(clipIndex >= 0) { "unknown clip: $clipId" }
        return copy(
            tracks = tracks.replacingAt(
                trackIndex,
                track.copy(clips = repack(track.clips.removingAt(clipIndex)))
            )
        )
    }

    /**
     * Moves [clipId] to [targetIndex] within [trackId] and re-packs the
     * track consecutively from 0. Source ranges, ids and adjustments travel
     * with the clip.
     */
    fun moveClip(trackId: String, clipId: String, targetIndex: Int): VideoSequence {
        val trackIndex = tracks.indexOfFirst { it.id == trackId }
        require(trackIndex >= 0) { "unknown track: $trackId" }
        val track = tracks[trackIndex]
        val clipIndex = track.clips.indexOfFirst { it.id == clipId }
        require(clipIndex >= 0) { "unknown clip: $clipId" }
        require(targetIndex in track.clips.indices) {
            "target index $targetIndex out of bounds for ${track.clips.size} clips"
        }
        if (targetIndex == clipIndex) return this
        val clip = track.clips[clipIndex]
        val reordered = track.clips
            .removingAt(clipIndex)
            .addingAt(targetIndex, clip)
        return copy(
            tracks = tracks.replacingAt(
                trackIndex, track.copy(clips = repack(reordered))
            )
        )
    }

    /** Re-lays clips consecutively from zero, preserving order and content. */
    private fun repack(clips: PersistentList<VideoClip>): PersistentList<VideoClip> {
        var cursor = EditorTime.Zero
        return clips.map { clip ->
            val placed =
                if (clip.timelineStart == cursor) clip
                else clip.copy(timelineStart = cursor)
            cursor += clip.sourceRange.duration
            placed
        }.toPersistentList()
    }
}
