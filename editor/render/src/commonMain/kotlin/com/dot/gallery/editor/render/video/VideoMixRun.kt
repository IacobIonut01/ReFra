package com.dot.gallery.editor.render.video

import com.dot.gallery.editor.core.model.AudioDucking
import com.dot.gallery.editor.core.model.AudioEq
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.abs

/**
 * The per-run DSP memory behind [VideoAudioMixer.mix] — one instance per
 * contiguous playback or export session, so biquad delay elements and
 * ducking envelopes stream across windows without a boundary click while
 * a seek or a fresh export always starts from rest. Caller (the audio
 * plan) owns the lifecycle; keyed by `audioTracks` index and rebuilt when
 * the lane's recipe changed mid-run.
 */
class VideoMixRun() {

    internal val eqChains = HashMap<Int, Pair<AudioEq, AudioEqChain>>()
    internal val duckers = HashMap<Int, Pair<AudioDucking, DuckState>>()

    /** The lane's live chain — rebuilt when the recipe changed mid-run. */
    internal fun eqFor(trackIndex: Int, eq: AudioEq): AudioEqChain {
        val existing = eqChains[trackIndex]
        if (existing != null && existing.first == eq) return existing.second
        return AudioEqChain(eq).also { eqChains[trackIndex] = eq to it }
    }

    /** The lane's ducking state — same rebuild-on-recipe-change rule. */
    internal fun duckerFor(trackIndex: Int, ducking: AudioDucking): DuckState {
        val existing = duckers[trackIndex]
        if (existing != null && existing.first == ducking) return existing.second
        return DuckState(ducking).also { duckers[trackIndex] = ducking to it }
    }

    /**
     * One lane's sidechain envelope: a peak detector on the trigger's
     * post-fader contribution with an instant attack and a fixed short
     * release (it holds through word gaps), feeding a one-pole gain that
     * falls toward the reduction floor over [AudioDucking.attackMs] and
     * recovers to unity over [AudioDucking.releaseMs]. The trigger buffer
     * is the lane as rendered — a muted or soloed-out trigger passes
     * silence and simply never engages the duck.
     */
    internal class DuckState(ducking: AudioDucking) {
        private var envelope = 0f
        private var gain = 1f
        private val floor = 10.0.pow(ducking.reductionDb / 20.0).toFloat()
        private val attackCoef = pole(ducking.attackMs)
        private val releaseCoef = pole(ducking.releaseMs)
        private val detectCoef = pole(DETECT_RELEASE_MS)

        /**
         * Multiplies [frames] stereo pairs in [target] (interleaved from
         * index 0) by the running duck gain — [trigger] is the sidechain
         * buffer (null means a silent/absent trigger, so the gain only
         * ever releases toward unity).
         */
        fun process(
            target: FloatArray,
            frames: Int,
            trigger: FloatArray?
        ) {
            var env = envelope
            var g = gain
            for (f in 0 until frames) {
                val peak = if (trigger != null) {
                    max(abs(trigger[f * 2]), abs(trigger[f * 2 + 1]))
                } else {
                    0f
                }
                env = max(peak, env * detectCoef)
                val aim = if (env > GATE_LEVEL) floor else 1f
                // One-pole: attack pulls down, release opens back up.
                val coef = if (aim < g) attackCoef else releaseCoef
                g = aim + (g - aim) * coef
                target[f * 2] *= g
                target[f * 2 + 1] *= g
            }
            envelope = env
            gain = g
        }

        companion object {
            /**
             * The detector's release — a 25 ms one-pole keeps the envelope
             * open through speech gaps without hanging across silence.
             */
            private const val DETECT_RELEASE_MS = 25f

            /**
             * The sidechain gate — contributions below ≈ −75 dBFS never
             * trigger a duck at all.
             */
            private const val GATE_LEVEL = 1.78e-4f

            /** The exact one-pole coefficient for a millisecond constant. */
            private fun pole(ms: Float): Float =
                exp((-1.0 / (ms * (VideoAudioMixer.SAMPLE_RATE / 1000.0))))
                    .toFloat()
        }
    }
}
