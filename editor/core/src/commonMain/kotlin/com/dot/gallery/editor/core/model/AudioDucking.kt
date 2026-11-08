package com.dot.gallery.editor.core.model

/**
 * Sidechain ducking on an [AudioTrack]: while the trigger lane's post-fader
 * signal is above the detector gate, this lane's contribution is pulled
 * down to [reductionDb] and held there; when the trigger falls quiet the
 * gain recovers to unity. [attackMs] is the one-pole time constant of the
 * pull-down, [releaseMs] of the recovery — the mixer's envelope is exact
 * and deterministic, carried per contiguous playback/export run.
 *
 * [triggerTrackId] names another audio track; the sequence validates the
 * reference exists (and is not self) whenever [enabled] is set. An empty
 * id with [enabled] false is the neutral "off" value — no sidechain.
 */
data class AudioDucking(
    val enabled: Boolean = false,
    val triggerTrackId: String = "",
    val reductionDb: Float = -12f,
    val attackMs: Float = 80f,
    val releaseMs: Float = 300f
) {
    init {
        require(
            reductionDb.isFinite() &&
                reductionDb in MIN_REDUCTION_DB..0f
        ) {
            "reductionDb must be finite and in [$MIN_REDUCTION_DB, 0]: " +
                reductionDb
        }
        require(attackMs.isFinite() && attackMs in MIN_MS..MAX_ATTACK_MS) {
            "attackMs must be finite and in [$MIN_MS, $MAX_ATTACK_MS]: " +
                attackMs
        }
        require(releaseMs.isFinite() && releaseMs in MIN_MS..MAX_RELEASE_MS) {
            "releaseMs must be finite and in [$MIN_MS, $MAX_RELEASE_MS]: " +
                releaseMs
        }
    }

    companion object {
        const val MIN_REDUCTION_DB = -60f
        const val MIN_MS = 1f
        const val MAX_ATTACK_MS = 2000f
        const val MAX_RELEASE_MS = 5000f
    }
}
