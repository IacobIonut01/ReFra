package com.dot.gallery.editor.render.video

/**
 * A bounded chunk of interleaved PCM audio. The buffer is private: [of]
 * copies its input, [copySamples] is the only way back out, and [fromOwned]
 * lets a producer that just computed the buffer skip the copy.
 *
 * [startSample] is the first sample's index on the audio clock (sample
 * frames at [sampleRate]); `samples.size / channels` is the chunk's length
 * in sample frames.
 */
class PcmAudio private constructor(
    val startSample: Long,
    val sampleRate: Int,
    val channels: Int,
    private val backing: ShortArray
) {
    init {
        require(startSample >= 0L) { "startSample must be non-negative: $startSample" }
        require(sampleRate > 0) { "sampleRate must be positive: $sampleRate" }
        require(channels in 1..2) { "channels must be 1 or 2: $channels" }
        require(backing.size % channels == 0) {
            "interleaved sample count ${backing.size} is not a multiple of $channels channels"
        }
    }

    /** Chunk length in per-channel sample frames. */
    val frames: Int get() = backing.size / channels

    /** Defensive copy of the interleaved samples. */
    fun copySamples(): ShortArray = backing.copyOf()

    companion object {
        /** Wraps a copy of [samples] — the caller keeps its buffer. */
        fun of(
            startSample: Long,
            sampleRate: Int,
            channels: Int,
            samples: ShortArray
        ): PcmAudio = PcmAudio(startSample, sampleRate, channels, samples.copyOf())

        /** Wraps [samples] without copying — the caller transfers ownership. */
        fun fromOwned(
            startSample: Long,
            sampleRate: Int,
            channels: Int,
            samples: ShortArray
        ): PcmAudio = PcmAudio(startSample, sampleRate, channels, samples)
    }
}
