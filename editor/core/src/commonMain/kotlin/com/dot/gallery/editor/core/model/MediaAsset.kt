package com.dot.gallery.editor.core.model

import com.dot.gallery.editor.core.time.EditorTime
import com.dot.gallery.editor.core.time.FrameRate

/**
 * A media source the document references. [reference] is an opaque host-owned
 * handle (URI, path, cloud reference); the editor never interprets it.
 * Dimensions live on the visual subtypes — an [Audio] source has no extent.
 */
sealed interface MediaAsset {

    val id: String
    val reference: String

    data class Image(
        override val id: String,
        override val reference: String,
        val size: PixelSize
    ) : MediaAsset {
        init {
            require(id.isNotBlank()) { "asset id must be non-blank" }
            require(reference.isNotBlank()) { "asset reference must be non-blank" }
        }
    }

    /**
     * [frameRate] is the source's average cadence when known — null when the
     * container reports none or when it is not exactly representable in
     * ticks; the editor must not invent an integer rate. [rotationDegrees] is
     * the container display rotation in counterclockwise degrees, restricted
     * to multiples of 90 (EXIF-orientation equivalent).
     */
    data class Video(
        override val id: String,
        override val reference: String,
        val size: PixelSize,
        val duration: EditorTime,
        val frameRate: FrameRate?,
        val hasAudio: Boolean,
        val rotationDegrees: Int = 0
    ) : MediaAsset {
        init {
            require(id.isNotBlank()) { "asset id must be non-blank" }
            require(reference.isNotBlank()) { "asset reference must be non-blank" }
            require(duration.ticks > 0L) { "video duration must be positive: ${duration.ticks}" }
            require(rotationDegrees % 90 == 0) {
                "video rotation must be a multiple of 90: $rotationDegrees"
            }
        }
    }

    /**
     * An audio-only source (music, voice-over, sound effect) placed on an
     * [AudioTrack]. [sampleRate] is the source's native rate — the mix
     * resamples to 48 kHz; [channels] is 1 (mono) or 2 (stereo), anything
     * wider is refused at probe time rather than silently downmixed.
     */
    data class Audio(
        override val id: String,
        override val reference: String,
        val duration: EditorTime,
        val sampleRate: Int,
        val channels: Int
    ) : MediaAsset {
        init {
            require(id.isNotBlank()) { "asset id must be non-blank" }
            require(reference.isNotBlank()) { "asset reference must be non-blank" }
            require(duration.ticks > 0L) { "audio duration must be positive: ${duration.ticks}" }
            require(sampleRate > 0) { "audio sample rate must be positive: $sampleRate" }
            require(channels in 1..2) { "audio channels must be 1 or 2: $channels" }
        }
    }
}
