package com.dot.gallery.editor.videocodec

/**
 * 64-bit played-frames clock over a wrapping device counter (e.g.
 * `AudioTrack.getPlaybackHeadPosition`, an unsigned 32-bit frame index that
 * also restarts at zero after a flush).
 *
 * Read + wrap detection + last/base update are a single synchronised
 * operation — the producer and the video-clock reader run on different
 * threads, and a torn read could fake a wrap and jump billions of frames.
 */
class PlaybackHeadCounter(private val wrapPeriod: Long = 1L shl 32) {

    private val lock = Any()
    private var wraps = 0L
    private var last = 0L
    private var base = 0L

    /** Fold the raw device position into a monotone 64-bit played count. */
    fun update(raw: Long): Long = synchronized(lock) {
        if (raw < last) wraps++
        last = raw
        wraps * wrapPeriod + raw - base
    }

    /** Called after (re)start: the count restarts at zero from [raw]. */
    fun reset(raw: Long) = synchronized(lock) {
        wraps = 0L
        last = raw
        base = raw
    }

    /**
     * Called after a flush: the hardware restarted at [raw], so shift the
     * base such that the count continues from [playedTotal] without rewinding.
     */
    fun rebaseTo(raw: Long, playedTotal: Long) = synchronized(lock) {
        base = wraps * wrapPeriod + raw - playedTotal
        last = raw
    }
}
