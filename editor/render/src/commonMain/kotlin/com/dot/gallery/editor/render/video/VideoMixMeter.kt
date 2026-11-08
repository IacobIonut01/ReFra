package com.dot.gallery.editor.render.video

import kotlin.math.sqrt

/**
 * Per-window energy accumulator [VideoAudioMixer.mix] fills on request —
 * the mixer already touches every contributed sample, so metering adds
 * only arithmetic, never a second decode pass. The caller allocates one
 * meter per output window and converts it with [snapshot]; lane sums are
 * indexed by `audioTracks` position, matching [VideoLevels.lanes].
 *
 * Lane energy measures the post-fader contribution — clip gain, fades,
 * pan and routing are already folded into the value added to the mix,
 * so a muted or soloed-out lane meters zero, matching what the listener
 * hears.
 */
class VideoMixMeter(laneCount: Int) {

    var frames = 0
        internal set

    /** Stereo sum-of-squares for the virtual Main-lane input. */
    internal val sourceSum = DoubleArray(2)

    /** Stereo sum-of-squares per audio lane, in audioTracks order. */
    internal val laneSum = Array(laneCount) { DoubleArray(2) }

    /** Converts the accumulated energy into per-channel RMS levels. */
    fun snapshot(): VideoLevels {
        val n = frames.coerceAtLeast(1)
        fun rms(sum: DoubleArray) = VideoLevels.Stereo(
            sqrt(sum[0] / n).toFloat(),
            sqrt(sum[1] / n).toFloat()
        )
        return VideoLevels(
            sourceSound = rms(sourceSum),
            lanes = laneSum.map(::rms)
        )
    }
}
