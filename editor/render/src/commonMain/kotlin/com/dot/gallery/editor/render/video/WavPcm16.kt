package com.dot.gallery.editor.render.video

/**
 * Byte-level helpers for the PCM16 WAV files the voice-over recorders
 * write — the canonical 44-byte RIFF header, little-endian throughout.
 * Kept platform-free so the Android and desktop recorders emit the
 * identical format the PCM readers already decode.
 */
object WavPcm16 {

    /** The fixed header length of the format the recorders emit. */
    const val HEADER_BYTES = 44

    /**
     * The 44-byte PCM RIFF header for [dataBytes] of interleaved s16
     * samples at [sampleRate] Hz over [channels] channels. A writer can
     * emit this with a placeholder size and rewrite the same bytes once
     * the take length is known.
     */
    fun header(dataBytes: Int, sampleRate: Int, channels: Int): ByteArray {
        val out = ByteArray(HEADER_BYTES)
        val byteRate = sampleRate * channels * 2
        val blockAlign = channels * 2
        out.putAscii(0, "RIFF")
        out.putIntLe(4, 36 + dataBytes)
        out.putAscii(8, "WAVE")
        out.putAscii(12, "fmt ")
        out.putIntLe(16, 16)
        out.putShortLe(20, 1) // PCM
        out.putShortLe(22, channels)
        out.putIntLe(24, sampleRate)
        out.putIntLe(28, byteRate)
        out.putShortLe(32, blockAlign)
        out.putShortLe(34, 16)
        out.putAscii(36, "data")
        out.putIntLe(40, dataBytes)
        return out
    }

    private fun ByteArray.putAscii(offset: Int, text: String) {
        for (i in text.indices) {
            this[offset + i] = text[i].code.toByte()
        }
    }

    private fun ByteArray.putIntLe(offset: Int, value: Int) {
        this[offset] = (value and 0xFF).toByte()
        this[offset + 1] = (value ushr 8 and 0xFF).toByte()
        this[offset + 2] = (value ushr 16 and 0xFF).toByte()
        this[offset + 3] = (value ushr 24 and 0xFF).toByte()
    }

    private fun ByteArray.putShortLe(offset: Int, value: Int) {
        this[offset] = (value and 0xFF).toByte()
        this[offset + 1] = (value ushr 8 and 0xFF).toByte()
    }
}
