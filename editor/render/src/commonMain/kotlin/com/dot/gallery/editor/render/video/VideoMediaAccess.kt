package com.dot.gallery.editor.render.video

import com.dot.gallery.editor.core.time.EditorTime

/**
 * Platform port for video sources: probe metadata, bounded frame reads and
 * new-copy export. Implementations resolve host references (`content://`,
 * `file://`, plain paths) into seekable media before decoding; the source is
 * never modified and every export writes a fresh artifact whose publication
 * is reported through `onPublished` exactly once — the same commit-receipt
 * contract as the photo exporter.
 *
 * [close] retires resources retained across calls (a resolver's cached
 * staging, for example); handles already handed to an in-flight decode or
 * export unwind with that call's own cleanup, so close never pulls a file
 * out from under FFmpeg.
 */
interface VideoMediaAccess : AutoCloseable {

    /** Probes container metadata; throws [UnsupportedVideoException] when out of scope. */
    suspend fun probe(reference: String): VideoInfo

    /**
     * The frame containing [at] on the *timeline-mapped source* clock — the
     * caller maps sequence time to source time itself. Decoded pixels are
     * bounded to [maxEdge] on the longest side, rotation already applied.
     */
    suspend fun frame(reference: String, at: EditorTime, maxEdge: Int): VideoFrame

    /**
     * Probes an audio-only source (music, voice-over); throws
     * [UnsupportedVideoException] when the file is out of scope or the host
     * cannot read audio sources. The default makes "no audio import"
     * explicit — a host without the probe fails loudly at the seam, never
     * silently.
     */
    suspend fun probeAudio(reference: String): AudioInfo =
        throw UnsupportedVideoException(
            "audio sources are not supported by this host"
        )

    /**
     * A1b recording seam: captures a microphone take and probes it like
     * [probeAudio]. Explicitly unavailable until the recorder ships — the
     * default throws so a Record affordance can never silently write
     * nothing; hosts that implement it override.
     */
    suspend fun recordAudio(): AudioInfo =
        throw UnsupportedVideoException(
            "audio recording is not supported by this host"
        )

    /**
     * Bounded PCM-peak summary for one audio-bearing source: [buckets]
     * absolute peak amplitudes in [0, 1] spread evenly across the
     * source's duration — the timeline waveform's only data. Memory is
     * O(buckets) regardless of clip length; hosts with no audio decode
     * keep the throwing default so a lane can never draw a fake shape.
     */
    suspend fun waveform(reference: String, buckets: Int): FloatArray =
        throw UnsupportedVideoException(
            "waveform summaries are not supported by this host"
        )

    /**
     * Writes the sequence as a new H.264/AAC MP4 copy. Single-track,
     * single-source sequences only in this scope; anything else must be
     * rejected with [UnsupportedVideoException] or [IllegalArgumentException],
     * never silently dropped.
     */
    suspend fun export(
        sequence: com.dot.gallery.editor.core.model.VideoSequence,
        onPublished: (VideoExportResult) -> Unit = {},
        onProgress: (Float) -> Unit
    ): VideoExportResult
}
