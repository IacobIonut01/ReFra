package com.dot.gallery.editor.render.video

import com.dot.gallery.editor.core.model.AudioEq
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * The per-lane EQ insert — a fixed-order biquad cascade the mixer runs on
 * a lane's summed stereo contribution: high-pass → low shelf → peaking
 * mid → high shelf. Coefficients are the RBJ audio-EQ cookbook at the
 * shared [VideoAudioMixer.SAMPLE_RATE] with deterministic corners
 * ([LOW_SHELF_HZ] / [MID_HZ] / [HIGH_SHELF_HZ]); a zero-value band is
 * simply absent from the cascade.
 *
 * Filter memory is per-channel transposed-direct-form-II state held by
 * the owning [VideoMixRun], so a contiguous playback/export run streams
 * without a click at window boundaries while a fresh run always starts
 * from rest — the same determinism rule the rest of the mixer follows.
 *
 * [responseDb] evaluates the cascade's true magnitude response at a
 * frequency — the EQ surface draws the actual filter curve, not an
 * invented arc. It reflects the stored band values regardless of
 * [AudioEq.bypass]; callers wanting the honest "what plays now" curve
 * honour [AudioEq.active] themselves.
 */
class AudioEqChain(eq: AudioEq, sampleRate: Long = VideoAudioMixer.SAMPLE_RATE) {

    /** One biquad per cascade stage per channel — (low..high order). */
    private val stages: List<Pair<Biquad, Biquad>> = buildList {
        if (eq.highPassHz > 0f) {
            add(biquadPair(highPass(eq.highPassHz, sampleRate)))
        }
        if (eq.lowDb != 0f) {
            add(biquadPair(lowShelf(LOW_SHELF_HZ, eq.lowDb, sampleRate)))
        }
        if (eq.midDb != 0f) {
            add(biquadPair(peaking(MID_HZ, eq.midDb, sampleRate)))
        }
        if (eq.highDb != 0f) {
            add(biquadPair(highShelf(HIGH_SHELF_HZ, eq.highDb, sampleRate)))
        }
    }

    /** Filters [frames] stereo pairs in place inside [interleaved]. */
    fun process(interleaved: FloatArray, frames: Int) {
        for ((left, right) in stages) {
            for (frame in 0 until frames) {
                val i = frame * 2
                interleaved[i] = left.step(interleaved[i])
                interleaved[i + 1] = right.step(interleaved[i + 1])
            }
        }
    }

    /**
     * The cascade's magnitude response in dB at [frequencyHz] — the
     * product of each stage's |H(e^{jω})|, empty chains reporting 0 dB.
     */
    fun responseDb(frequencyHz: Float): Float {
        if (stages.isEmpty() || frequencyHz <= 0f) return 0f
        val w = (2.0 * PI * frequencyHz / VideoAudioMixer.SAMPLE_RATE)
            .toFloat()
        val cosW = cos(w)
        val cos2W = cos(2f * w)
        var linear = 1f
        for ((stage, _) in stages) {
            linear *= stage.magnitudeSquared(cosW, cos2W)
        }
        return 10f * log10(linear.coerceAtLeast(1e-12f))
    }

    /**
     * One normalized RBJ biquad in transposed direct form II — z1/z2 are
     * the per-channel delay elements, so one instance serves one channel
     * for the whole run.
     */
    private class Biquad(
        val b0: Float, val b1: Float, val b2: Float,
        val a1: Float, val a2: Float
    ) {
        var z1 = 0f
        var z2 = 0f

        fun step(x: Float): Float {
            val y = b0 * x + z1
            z1 = b1 * x - a1 * y + z2
            z2 = b2 * x - a2 * y
            return y
        }

        /** |H(e^{jω})|² from the precomputed angle cosines. */
        fun magnitudeSquared(cosW: Float, cos2W: Float): Float {
            val num = b0 * b0 + b1 * b1 + b2 * b2 +
                2f * (b0 * b1 + b1 * b2) * cosW + 2f * b0 * b2 * cos2W
            val den = 1f + a1 * a1 + a2 * a2 +
                2f * (a1 + a1 * a2) * cosW + 2f * a2 * cos2W
            return num / den
        }
    }

    private class Coefficients(
        val b0: Float, val b1: Float, val b2: Float,
        val a1: Float, val a2: Float
    )

    companion object {
        /** The lane stack's fixed corners — the EQ curve's labelled anchors. */
        const val LOW_SHELF_HZ = 250f
        const val MID_HZ = 1_000f
        const val HIGH_SHELF_HZ = 4_000f

        /** The mid bell's width — deterministic, not a UI parameter. */
        private const val MID_Q = 1f

        /** The high-pass damping — a second-order Butterworth knee. */
        private const val HP_Q = 0.70710677f

        private fun biquadPair(c: Coefficients): Pair<Biquad, Biquad> =
            Biquad(c.b0, c.b1, c.b2, c.a1, c.a2) to
                Biquad(c.b0, c.b1, c.b2, c.a1, c.a2)

        private fun highPass(f0: Float, fs: Long): Coefficients {
            val w0 = (2.0 * PI * f0 / fs).toFloat()
            val cosW0 = cos(w0)
            val alpha = sin(w0) / (2f * HP_Q)
            val a0 = 1f + alpha
            return Coefficients(
                b0 = ((1f + cosW0) / 2f) / a0,
                b1 = (-(1f + cosW0)) / a0,
                b2 = ((1f + cosW0) / 2f) / a0,
                a1 = (-2f * cosW0) / a0,
                a2 = (1f - alpha) / a0
            )
        }

        private fun lowShelf(f0: Float, db: Float, fs: Long): Coefficients {
            val amplitude = 10.0.pow(db / 40.0).toFloat()
            val w0 = (2.0 * PI * f0 / fs).toFloat()
            val cosW0 = cos(w0)
            // Slope S = 1 — the shelf reaches its gain with a gentle knee.
            val alpha = sin(w0) * sqrt(2f) / 2f
            val twoSqrtAAlpha = 2f * sqrt(amplitude) * alpha
            val a0 = (amplitude + 1f) + (amplitude - 1f) * cosW0 +
                twoSqrtAAlpha
            return Coefficients(
                b0 = amplitude * ((amplitude + 1f) - (amplitude - 1f) * cosW0 +
                    twoSqrtAAlpha) / a0,
                b1 = 2f * amplitude * ((amplitude - 1f) -
                    (amplitude + 1f) * cosW0) / a0,
                b2 = amplitude * ((amplitude + 1f) - (amplitude - 1f) * cosW0 -
                    twoSqrtAAlpha) / a0,
                a1 = -2f * ((amplitude - 1f) + (amplitude + 1f) * cosW0) / a0,
                a2 = ((amplitude + 1f) + (amplitude - 1f) * cosW0 -
                    twoSqrtAAlpha) / a0
            )
        }

        private fun peaking(f0: Float, db: Float, fs: Long): Coefficients {
            val amplitude = 10.0.pow(db / 40.0).toFloat()
            val w0 = (2.0 * PI * f0 / fs).toFloat()
            val cosW0 = cos(w0)
            val alpha = sin(w0) / (2f * MID_Q)
            val a0 = 1f + alpha / amplitude
            return Coefficients(
                b0 = (1f + alpha * amplitude) / a0,
                b1 = (-2f * cosW0) / a0,
                b2 = (1f - alpha * amplitude) / a0,
                a1 = (-2f * cosW0) / a0,
                a2 = (1f - alpha / amplitude) / a0
            )
        }

        private fun highShelf(f0: Float, db: Float, fs: Long): Coefficients {
            val amplitude = 10.0.pow(db / 40.0).toFloat()
            val w0 = (2.0 * PI * f0 / fs).toFloat()
            val cosW0 = cos(w0)
            val alpha = sin(w0) * sqrt(2f) / 2f
            val twoSqrtAAlpha = 2f * sqrt(amplitude) * alpha
            val a0 = (amplitude + 1f) - (amplitude - 1f) * cosW0 +
                twoSqrtAAlpha
            return Coefficients(
                b0 = amplitude * ((amplitude + 1f) + (amplitude - 1f) * cosW0 +
                    twoSqrtAAlpha) / a0,
                b1 = -2f * amplitude * ((amplitude - 1f) +
                    (amplitude + 1f) * cosW0) / a0,
                b2 = amplitude * ((amplitude + 1f) + (amplitude - 1f) * cosW0 -
                    twoSqrtAAlpha) / a0,
                a1 = 2f * ((amplitude - 1f) - (amplitude + 1f) * cosW0) / a0,
                a2 = ((amplitude + 1f) - (amplitude - 1f) * cosW0 -
                    twoSqrtAAlpha) / a0
            )
        }
    }
}
