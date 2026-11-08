package com.dot.gallery.editor.core.model

/**
 * A three-band tone stack plus high-pass on an [AudioTrack]: a low shelf,
 * a peaking mid and a high shelf (fixed corner frequencies owned by the
 * mixer — 250 Hz / 1 kHz / 4 kHz), each in [-12, +12] dB, plus a
 * Butterworth high-pass at [highPassHz] (0 disables it, bounded at
 * 500 Hz). [bypass] short-circuits the whole chain without losing the
 * stored values — the honest audition toggle.
 *
 * [active] is the mixer's cheap check: nothing to do when the chain is
 * bypassed or every stage is at unity. Filter state lives per contiguous
 * playback/export run, so a recipe edit mid-run resets cleanly rather
 * than ringing.
 */
data class AudioEq(
    val lowDb: Float = 0f,
    val midDb: Float = 0f,
    val highDb: Float = 0f,
    val highPassHz: Float = 0f,
    val bypass: Boolean = false
) {
    init {
        validateBand(lowDb, "lowDb")
        validateBand(midDb, "midDb")
        validateBand(highDb, "highDb")
        require(
            highPassHz.isFinite() &&
                highPassHz in 0f..MAX_HIGH_PASS_HZ
        ) {
            "highPassHz must be finite and in [0, $MAX_HIGH_PASS_HZ]: " +
                highPassHz
        }
    }

    /** Whether the chain can change a sample — false is a pass-through. */
    val active: Boolean
        get() = !bypass && (
            lowDb != 0f || midDb != 0f || highDb != 0f || highPassHz > 0f
            )

    companion object {
        const val MIN_BAND_DB = -12f
        const val MAX_BAND_DB = 12f
        const val MAX_HIGH_PASS_HZ = 500f

        private fun validateBand(value: Float, name: String) {
            require(value.isFinite() && value in MIN_BAND_DB..MAX_BAND_DB) {
                "$name must be finite and in " +
                    "[$MIN_BAND_DB, $MAX_BAND_DB]: $value"
            }
        }
    }
}
