package com.dot.gallery.editor.core.model

/**
 * Per-channel output routing for an [AudioClip]: independent left/right gain
 * applied after panning in the mix. [linkChannels] is the editing coupling —
 * when true, a UI that changes one channel's gain is expected to mirror the
 * change to the other; the mixer honours the two stored values regardless.
 * [monoDownmix] collapses a stereo input to a single channel (L+R)/2 before
 * gain and pan — it is a no-op on an already-mono source.
 *
 * Gains are decibels in [MIN_GAIN_DB, MAX_GAIN_DB]; a stored value must be
 * finite — a NaN or infinite gain would poison the float accumulator
 * silently.
 */
data class AudioRouting(
    val leftGainDb: Float = 0f,
    val rightGainDb: Float = 0f,
    val linkChannels: Boolean = true,
    val monoDownmix: Boolean = false
) {
    init {
        validateGain(leftGainDb, "leftGainDb")
        validateGain(rightGainDb, "rightGainDb")
    }

    companion object {
        const val MIN_GAIN_DB = -60f
        const val MAX_GAIN_DB = 12f

        /** The shared decibel bound for clip, track and routing gains. */
        internal fun validateGain(value: Float, name: String) {
            require(value.isFinite() && value in MIN_GAIN_DB..MAX_GAIN_DB) {
                "$name must be finite and in [$MIN_GAIN_DB, $MAX_GAIN_DB]: $value"
            }
        }
    }
}
