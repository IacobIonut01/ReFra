package com.dot.gallery.editor.render.video

/**
 * A real-clock PCM sink the playback engine drives. [playedFrames] is the
 * number of sample frames actually consumed by the output device — the
 * playback clock — not the number written. Writers are expected to be
 * bounded and cancellable; a sink that cannot accept audio must throw
 * rather than silently dropping samples, so a broken output is an explicit
 * failure, not a muted playback.
 */
interface VideoAudioOutput : AutoCloseable {

    /** Sample frames the device has actually played since [start]. */
    val playedFrames: Long

    /** Prepares the sink for [sampleRate]/[channels] PCM16 output. */
    fun start(sampleRate: Int, channels: Int)

    /** Writes [count] interleaved samples starting at [offset]. */
    suspend fun write(samples: ShortArray, offset: Int, count: Int)

    /** Stops playback and discards buffered audio immediately. */
    fun pauseAndFlush()

    override fun close()
}
