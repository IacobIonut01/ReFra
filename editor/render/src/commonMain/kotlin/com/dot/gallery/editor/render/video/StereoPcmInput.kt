package com.dot.gallery.editor.render.video

/**
 * A 48 kHz stereo PCM16 input feeding the mix — one open source reader
 * adapted to a windowed pull. Implementations serve `[start, start+frames)`
 * in *source* sample coordinates and leave uncovered positions silent;
 * the FfmpegPcmReader-shaped readers already honour that contract.
 */
fun interface StereoPcmInput {

    /** Fills `out[0 .. frames*2)` with interleaved L/R s16 from [start]. */
    suspend fun read(start: Long, frames: Int, out: ShortArray)
}
