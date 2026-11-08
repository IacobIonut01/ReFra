package com.dot.gallery.editor.videocodec

import kotlin.math.abs

/**
 * Bounded PCM-peak summarizer for timeline waveforms: streams the source
 * through [FfmpegPcmReader] in fixed windows and keeps only the loudest
 * sample per bucket, so memory stays O(buckets + chunk) regardless of
 * clip length. Each returned value is the absolute peak amplitude in
 * [0, 1] over that bucket's samples — quiet material draws quiet, never
 * renormalized up to look busy. A bucket the source does not cover stays
 * 0.
 */
object FfmpegWaveform {

    /** Decode window per read — ~85 ms at 48 kHz, cheap and cancellable. */
    private const val CHUNK_FRAMES = 4_096

    /**
     * Reads [reader] from sample 0 to [totalSamples] and folds the peak
     * amplitude into [buckets] equal bins. [totalSamples] comes from the
     * probe (`duration.samplesAtOrAfter(OUTPUT_RATE)`); a source that
     * runs short simply leaves trailing buckets silent.
     */
    suspend fun peaks(
        reader: FfmpegPcmReader,
        totalSamples: Long,
        buckets: Int
    ): FloatArray {
        require(buckets > 0) { "buckets must be positive: $buckets" }
        val out = FloatArray(buckets)
        if (totalSamples <= 0L) return out
        var position = 0L
        while (position < totalSamples) {
            val count =
                minOf(CHUNK_FRAMES.toLong(), totalSamples - position).toInt()
            val data = reader.read(position, count)
            val frames = data.size / FfmpegPcmReader.OUTPUT_CHANNELS
            for (f in 0 until frames) {
                val l = abs(data[f * 2].toInt())
                val r = abs(data[f * 2 + 1].toInt())
                val peak = maxOf(l, r)
                val bucket = ((position + f) * buckets / totalSamples)
                    .toInt().coerceAtMost(buckets - 1)
                val norm = peak.toFloat() / Short.MAX_VALUE
                if (norm > out[bucket]) out[bucket] = norm
            }
            position += count
        }
        return out
    }
}
