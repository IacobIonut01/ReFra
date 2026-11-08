package com.dot.gallery.editor.core.project

import com.dot.gallery.editor.core.model.AudioClip
import com.dot.gallery.editor.core.model.AudioDucking
import com.dot.gallery.editor.core.model.AudioEq
import com.dot.gallery.editor.core.model.AudioRouting
import com.dot.gallery.editor.core.model.AudioTrack
import com.dot.gallery.editor.core.model.ColorAdjustments
import com.dot.gallery.editor.core.model.MediaAsset
import com.dot.gallery.editor.core.model.PhotoBlendMode
import com.dot.gallery.editor.core.model.PixelSize
import com.dot.gallery.editor.core.model.VideoClip
import com.dot.gallery.editor.core.model.VideoContentMode
import com.dot.gallery.editor.core.model.VideoLayerTransform
import com.dot.gallery.editor.core.model.VideoSequence
import com.dot.gallery.editor.core.model.VideoTrack
import com.dot.gallery.editor.core.model.VideoTrackKind
import com.dot.gallery.editor.core.time.EditorTime
import com.dot.gallery.editor.core.time.FrameRate
import com.dot.gallery.editor.core.time.TimeRange
import kotlinx.collections.immutable.toPersistentList

/**
 * Deterministic JSON codec for a [VideoProjectSnapshot] — the multi-source
 * sequence document plus selection and playhead, never decoded media. The
 * document model carries no serialization dependency, so this is a small
 * hand-rolled writer/parser pair: keys land in declaration order, numbers
 * round-trip through their shortest representation, and the same snapshot
 * always encodes to the same bytes.
 *
 * Decoding is strict about shape and version but ignores unknown keys, so
 * a document written by a newer build still restores what it can. Every
 * model invariant is re-checked by the rebuilt [VideoSequence]'s own
 * validation — a malformed or corrupt project throws
 * [IllegalArgumentException], never yields a half-loaded document.
 */
object VideoProjectCodec {

    /**
     * The current format. Version 2 added `audioSources`, `audioTracks`
     * and `sourceSoundMuted`; version 3 adds the per-clip `audioMuted`
     * detach flag and the output-level `masterGainDb`/`limiterEnabled`
     * pair; version 4 adds the per-track `eq` and `ducking` effects.
     * Older files decode with the neutral defaults; a newer file
     * read by an older build fails on the version check — never
     * silently dropping audio lanes.
     */
    const val VERSION = 4

    /** The oldest readable format — pre-audio documents still restore. */
    const val MIN_VERSION = 1

    fun encode(snapshot: VideoProjectSnapshot): String {
        val out = ProjectJson.Writer()
        encodeSnapshot(out, snapshot)
        return out.toString()
    }

    fun decode(json: String): VideoProjectSnapshot {
        val value = ProjectJson.parse(json)
        val root = value as? ProjectJson.Object
            ?: throw IllegalArgumentException("project root must be an object")
        val version = root.reqLong("version")
        require(version in MIN_VERSION.toLong()..VERSION.toLong()) {
            "unsupported project version: $version"
        }
        return decodeSnapshot(root)
    }

    // ----- encoding -----------------------------------------------------

    private fun encodeSnapshot(out: ProjectJson.Writer, s: VideoProjectSnapshot) {
        out.beginObject()
        out.key("version").value(VERSION)
        out.key("size"); encodeSize(out, s.sequence.size)
        out.key("frameRate"); encodeFrameRate(out, s.sequence.frameRate)
        out.key("positionTicks").value(s.position.ticks)
        out.key("selectedClipId").stringOrNull(s.selectedClipId)
        out.key("sources").beginArray()
        for (source in s.sequence.sources) encodeSource(out, source)
        out.endArray()
        out.key("tracks").beginArray()
        for (track in s.sequence.tracks) encodeTrack(out, track)
        out.endArray()
        out.key("audioSources").beginArray()
        for (source in s.sequence.audioSources) encodeAudioSource(out, source)
        out.endArray()
        out.key("audioTracks").beginArray()
        for (track in s.sequence.audioTracks) encodeAudioTrack(out, track)
        out.endArray()
        out.key("sourceSoundMuted").value(s.sequence.sourceSoundMuted)
        out.key("masterGainDb").value(s.sequence.masterGainDb)
        out.key("limiterEnabled").value(s.sequence.limiterEnabled)
        out.endObject()
    }

    private fun encodeSource(out: ProjectJson.Writer, source: MediaAsset.Video) {
        out.beginObject()
        out.key("id").value(source.id)
        out.key("reference").value(source.reference)
        out.key("width").value(source.size.width)
        out.key("height").value(source.size.height)
        out.key("durationTicks").value(source.duration.ticks)
        out.key("frameRate")
        source.frameRate?.let { encodeFrameRate(out, it) } ?: out.nullValue()
        out.key("hasAudio").value(source.hasAudio)
        out.key("rotationDegrees").value(source.rotationDegrees)
        out.endObject()
    }

    private fun encodeTrack(out: ProjectJson.Writer, track: VideoTrack) {
        out.beginObject()
        out.key("id").value(track.id)
        out.key("name").value(track.name)
        out.key("kind").value(track.kind.name)
        out.key("visible").value(track.visible)
        out.key("locked").value(track.locked)
        out.key("clips").beginArray()
        for (clip in track.clips) encodeClip(out, clip)
        out.endArray()
        out.endObject()
    }

    private fun encodeClip(out: ProjectJson.Writer, clip: VideoClip) {
        out.beginObject()
        out.key("id").value(clip.id)
        out.key("sourceId").value(clip.sourceId)
        out.key("timelineStartTicks").value(clip.timelineStart.ticks)
        out.key("sourceStartTicks").value(clip.sourceRange.start.ticks)
        out.key("sourceDurationTicks").value(clip.sourceRange.duration.ticks)
        out.key("adjustments"); encodeAdjustments(out, clip.adjustments)
        out.key("transform"); encodeTransform(out, clip.transform)
        out.key("contentMode").value(clip.contentMode.name)
        out.key("opacity").value(clip.opacity)
        out.key("blendMode").value(clip.blendMode.name)
        out.key("visible").value(clip.visible)
        out.key("audioMuted").value(clip.audioMuted)
        out.endObject()
    }

    private fun encodeAdjustments(out: ProjectJson.Writer, a: ColorAdjustments) {
        out.beginObject()
        out.key("exposureStops").value(a.exposureStops)
        out.key("contrast").value(a.contrast)
        out.key("saturation").value(a.saturation)
        out.key("highlights").value(a.highlights)
        out.key("shadows").value(a.shadows)
        out.key("whites").value(a.whites)
        out.key("blacks").value(a.blacks)
        out.endObject()
    }

    private fun encodeTransform(out: ProjectJson.Writer, t: VideoLayerTransform) {
        out.beginObject()
        out.key("translationX").value(t.translationX)
        out.key("translationY").value(t.translationY)
        out.key("scale").value(t.scale)
        out.key("rotationDegrees").value(t.rotationDegrees)
        out.key("flipHorizontal").value(t.flipHorizontal)
        out.key("flipVertical").value(t.flipVertical)
        out.endObject()
    }

    private fun encodeAudioSource(out: ProjectJson.Writer, source: MediaAsset.Audio) {
        out.beginObject()
        out.key("id").value(source.id)
        out.key("reference").value(source.reference)
        out.key("durationTicks").value(source.duration.ticks)
        out.key("sampleRate").value(source.sampleRate)
        out.key("channels").value(source.channels)
        out.endObject()
    }

    private fun encodeAudioTrack(out: ProjectJson.Writer, track: AudioTrack) {
        out.beginObject()
        out.key("id").value(track.id)
        out.key("name").value(track.name)
        out.key("muted").value(track.muted)
        out.key("solo").value(track.solo)
        out.key("gainDb").value(track.gainDb)
        out.key("pan").value(track.pan)
        out.key("locked").value(track.locked)
        out.key("eq"); encodeEq(out, track.eq)
        out.key("ducking"); encodeDucking(out, track.ducking)
        out.key("clips").beginArray()
        for (clip in track.clips) encodeAudioClip(out, clip)
        out.endArray()
        out.endObject()
    }

    private fun encodeEq(out: ProjectJson.Writer, eq: AudioEq) {
        out.beginObject()
        out.key("lowDb").value(eq.lowDb)
        out.key("midDb").value(eq.midDb)
        out.key("highDb").value(eq.highDb)
        out.key("highPassHz").value(eq.highPassHz)
        out.key("bypass").value(eq.bypass)
        out.endObject()
    }

    private fun encodeDucking(out: ProjectJson.Writer, ducking: AudioDucking) {
        out.beginObject()
        out.key("enabled").value(ducking.enabled)
        out.key("triggerTrackId").value(ducking.triggerTrackId)
        out.key("reductionDb").value(ducking.reductionDb)
        out.key("attackMs").value(ducking.attackMs)
        out.key("releaseMs").value(ducking.releaseMs)
        out.endObject()
    }

    private fun encodeAudioClip(out: ProjectJson.Writer, clip: AudioClip) {
        out.beginObject()
        out.key("id").value(clip.id)
        out.key("sourceId").value(clip.sourceId)
        out.key("timelineStartTicks").value(clip.timelineStart.ticks)
        out.key("sourceStartTicks").value(clip.sourceRange.start.ticks)
        out.key("sourceDurationTicks").value(clip.sourceRange.duration.ticks)
        out.key("gainDb").value(clip.gainDb)
        out.key("pan").value(clip.pan)
        out.key("muted").value(clip.muted)
        out.key("fadeInTicks").value(clip.fadeIn.ticks)
        out.key("fadeOutTicks").value(clip.fadeOut.ticks)
        out.key("routing"); encodeRouting(out, clip.routing)
        out.endObject()
    }

    private fun encodeRouting(out: ProjectJson.Writer, routing: AudioRouting) {
        out.beginObject()
        out.key("leftGainDb").value(routing.leftGainDb)
        out.key("rightGainDb").value(routing.rightGainDb)
        out.key("linkChannels").value(routing.linkChannels)
        out.key("monoDownmix").value(routing.monoDownmix)
        out.endObject()
    }

    private fun encodeSize(out: ProjectJson.Writer, size: PixelSize) {
        out.beginObject()
        out.key("width").value(size.width)
        out.key("height").value(size.height)
        out.endObject()
    }

    private fun encodeFrameRate(out: ProjectJson.Writer, rate: FrameRate) {
        out.beginObject()
        out.key("numerator").value(rate.numerator)
        out.key("denominator").value(rate.denominator)
        out.endObject()
    }

    // ----- decoding -----------------------------------------------------

    private fun decodeSnapshot(root: ProjectJson.Object): VideoProjectSnapshot {
        val size = decodeSize(root.reqObj("size"))
        val frameRate = decodeFrameRate(root.reqObj("frameRate"))
        val sources = root.reqArray("sources").values.map { any ->
            decodeSource(any as? ProjectJson.Object ?: malformed("source"))
        }
        val tracks = root.reqArray("tracks").values.map { any ->
            decodeTrack(any as? ProjectJson.Object ?: malformed("track"))
        }
        // Version-1 documents predate audio — absent arrays restore empty.
        val audioSources = (root.optArray("audioSources")?.values ?: emptyList())
            .map { any ->
                decodeAudioSource(any as? ProjectJson.Object ?: malformed("audio source"))
            }
        val audioTracks = (root.optArray("audioTracks")?.values ?: emptyList())
            .map { any ->
                decodeAudioTrack(any as? ProjectJson.Object ?: malformed("audio track"))
            }
        val sequence = VideoSequence(
            size = size,
            frameRate = frameRate,
            sources = sources.toPersistentList(),
            tracks = tracks.toPersistentList(),
            audioSources = audioSources.toPersistentList(),
            audioTracks = audioTracks.toPersistentList(),
            sourceSoundMuted = root.optBoolean("sourceSoundMuted") ?: false,
            masterGainDb = root.optFloat("masterGainDb") ?: 0f,
            limiterEnabled = root.optBoolean("limiterEnabled") ?: true
        )
        val position = EditorTime(root.reqLong("positionTicks"))
        val selected = root.optString("selectedClipId")
        return VideoProjectSnapshot(sequence, selected, position)
    }

    private fun decodeSource(obj: ProjectJson.Object): MediaAsset.Video = MediaAsset.Video(
        id = obj.reqString("id"),
        reference = obj.reqString("reference"),
        size = PixelSize(obj.reqInt("width"), obj.reqInt("height")),
        duration = EditorTime(obj.reqLong("durationTicks")),
        frameRate = obj.optObj("frameRate")?.let(::decodeFrameRate),
        hasAudio = obj.reqBoolean("hasAudio"),
        rotationDegrees = obj.optInt("rotationDegrees") ?: 0
    )

    private fun decodeTrack(obj: ProjectJson.Object): VideoTrack = VideoTrack(
        id = obj.reqString("id"),
        name = obj.optString("name") ?: obj.reqString("id"),
        kind = obj.reqString("kind").let { name ->
            VideoTrackKind.entries.firstOrNull { it.name == name }
                ?: throw IllegalArgumentException("unknown track kind: $name")
        },
        visible = obj.optBoolean("visible") ?: true,
        locked = obj.optBoolean("locked") ?: false,
        clips = obj.reqArray("clips").values.map { any ->
            decodeClip(any as? ProjectJson.Object ?: malformed("clip"))
        }.toPersistentList()
    )

    private fun decodeClip(obj: ProjectJson.Object): VideoClip = VideoClip(
        id = obj.reqString("id"),
        sourceId = obj.reqString("sourceId"),
        timelineStart = EditorTime(obj.reqLong("timelineStartTicks")),
        sourceRange = TimeRange(
            EditorTime(obj.reqLong("sourceStartTicks")),
            EditorTime(obj.reqLong("sourceDurationTicks"))
        ),
        adjustments = obj.optObj("adjustments")?.let(::decodeAdjustments)
            ?: ColorAdjustments(),
        transform = obj.optObj("transform")?.let(::decodeTransform)
            ?: VideoLayerTransform(),
        contentMode = obj.optString("contentMode")?.let { name ->
            VideoContentMode.entries.firstOrNull { it.name == name }
                ?: throw IllegalArgumentException("unknown content mode: $name")
        } ?: VideoContentMode.Fit,
        opacity = obj.optFloat("opacity") ?: 1f,
        blendMode = obj.optString("blendMode")?.let { name ->
            PhotoBlendMode.entries.firstOrNull { it.name == name }
                ?: throw IllegalArgumentException("unknown blend mode: $name")
        } ?: PhotoBlendMode.Normal,
        visible = obj.optBoolean("visible") ?: true,
        audioMuted = obj.optBoolean("audioMuted") ?: false
    )

    private fun decodeAudioSource(obj: ProjectJson.Object): MediaAsset.Audio =
        MediaAsset.Audio(
            id = obj.reqString("id"),
            reference = obj.reqString("reference"),
            duration = EditorTime(obj.reqLong("durationTicks")),
            sampleRate = obj.reqInt("sampleRate"),
            channels = obj.reqInt("channels")
        )

    private fun decodeAudioTrack(obj: ProjectJson.Object): AudioTrack = AudioTrack(
        id = obj.reqString("id"),
        name = obj.optString("name") ?: obj.reqString("id"),
        muted = obj.optBoolean("muted") ?: false,
        solo = obj.optBoolean("solo") ?: false,
        gainDb = obj.optFloat("gainDb") ?: 0f,
        pan = obj.optFloat("pan") ?: 0f,
        locked = obj.optBoolean("locked") ?: false,
        // Pre-v4 tracks carry no effects — neutral inserts.
        eq = obj.optObj("eq")?.let(::decodeEq) ?: AudioEq(),
        ducking = obj.optObj("ducking")?.let(::decodeDucking) ?: AudioDucking(),
        clips = obj.reqArray("clips").values.map { any ->
            decodeAudioClip(any as? ProjectJson.Object ?: malformed("audio clip"))
        }.toPersistentList()
    )

    private fun decodeEq(obj: ProjectJson.Object) = AudioEq(
        lowDb = obj.optFloat("lowDb") ?: 0f,
        midDb = obj.optFloat("midDb") ?: 0f,
        highDb = obj.optFloat("highDb") ?: 0f,
        highPassHz = obj.optFloat("highPassHz") ?: 0f,
        bypass = obj.optBoolean("bypass") ?: false
    )

    private fun decodeDucking(obj: ProjectJson.Object) = AudioDucking(
        enabled = obj.optBoolean("enabled") ?: false,
        triggerTrackId = obj.optString("triggerTrackId") ?: "",
        reductionDb = obj.optFloat("reductionDb") ?: -12f,
        attackMs = obj.optFloat("attackMs") ?: 80f,
        releaseMs = obj.optFloat("releaseMs") ?: 300f
    )

    private fun decodeAudioClip(obj: ProjectJson.Object): AudioClip = AudioClip(
        id = obj.reqString("id"),
        sourceId = obj.reqString("sourceId"),
        timelineStart = EditorTime(obj.reqLong("timelineStartTicks")),
        sourceRange = TimeRange(
            EditorTime(obj.reqLong("sourceStartTicks")),
            EditorTime(obj.reqLong("sourceDurationTicks"))
        ),
        gainDb = obj.optFloat("gainDb") ?: 0f,
        pan = obj.optFloat("pan") ?: 0f,
        muted = obj.optBoolean("muted") ?: false,
        fadeIn = EditorTime(obj.optLong("fadeInTicks") ?: 0L),
        fadeOut = EditorTime(obj.optLong("fadeOutTicks") ?: 0L),
        routing = obj.optObj("routing")?.let(::decodeRouting) ?: AudioRouting()
    )

    private fun decodeRouting(obj: ProjectJson.Object) = AudioRouting(
        leftGainDb = obj.optFloat("leftGainDb") ?: 0f,
        rightGainDb = obj.optFloat("rightGainDb") ?: 0f,
        linkChannels = obj.optBoolean("linkChannels") ?: true,
        monoDownmix = obj.optBoolean("monoDownmix") ?: false
    )

    private fun decodeAdjustments(obj: ProjectJson.Object) = ColorAdjustments(
        exposureStops = obj.optFloat("exposureStops") ?: 0f,
        contrast = obj.optFloat("contrast") ?: 1f,
        saturation = obj.optFloat("saturation") ?: 1f,
        highlights = obj.optFloat("highlights") ?: 0f,
        shadows = obj.optFloat("shadows") ?: 0f,
        whites = obj.optFloat("whites") ?: 0f,
        blacks = obj.optFloat("blacks") ?: 0f
    )

    private fun decodeTransform(obj: ProjectJson.Object) = VideoLayerTransform(
        translationX = obj.optFloat("translationX") ?: 0f,
        translationY = obj.optFloat("translationY") ?: 0f,
        scale = obj.optFloat("scale") ?: 1f,
        rotationDegrees = obj.optFloat("rotationDegrees") ?: 0f,
        flipHorizontal = obj.optBoolean("flipHorizontal") ?: false,
        flipVertical = obj.optBoolean("flipVertical") ?: false
    )

    private fun decodeSize(obj: ProjectJson.Object) = PixelSize(
        obj.reqInt("width"),
        obj.reqInt("height")
    )

    private fun decodeFrameRate(obj: ProjectJson.Object) = FrameRate(
        obj.reqInt("numerator"),
        obj.reqInt("denominator")
    )

    private fun malformed(what: String): Nothing =
        throw IllegalArgumentException("malformed $what in project")
}
