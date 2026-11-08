package com.dot.gallery.editor.io

import com.dot.gallery.editor.core.model.PixelSize
import com.dot.gallery.editor.render.photo.ScanlineSink
import java.io.OutputStream
import java.util.zip.CRC32
import java.util.zip.Deflater
import java.util.zip.DeflaterOutputStream

/**
 * A streaming PNG encoder writing an 8-bit RGBA (`color type 6`) image one
 * row at a time through [ScanlineSink]. Compressed data is emitted as
 * bounded 64 KiB `IDAT` chunks, so peak memory stays `O(rowBytes + 64 KiB)`
 * regardless of image height.
 *
 * The stream carries an `sRGB` rendering-intent chunk: exported pixels are
 * sRGB and no colour profile is copied from the source.
 *
 * [finish] completes the file (final IDAT + IEND) and is only legal after
 * exactly `size.height` rows. [close] releases the deflater; it deliberately
 * does **not** write a valid tail, so a cancelled export leaves a truncated
 * file the caller can recognise and delete. The caller owns [output] — it is
 * flushed on [finish] but never closed here.
 */
class StreamingPngWriter(
    private val output: OutputStream,
    private val size: PixelSize
) : ScanlineSink {

    private val rowBuffer = ByteArray(checkedRowBytes(size.width))
    private val crc = CRC32()
    private val deflater = Deflater()
    private val idat = ChunkedIdatOutput()
    private val deflateStream = DeflaterOutputStream(idat, deflater)

    private var rowsWritten = 0
    private var finished = false
    private var closed = false

    init {
        // The deflater already holds native state; if the header write throws
        // during construction the caller never receives a writer to close, so
        // release it here.
        try {
            writeHeader()
        } catch (t: Throwable) {
            closed = true
            deflater.end()
            throw t
        }
    }

    private fun writeHeader() {
        output.write(PNG_SIGNATURE)
        val ihdr = ByteArray(13)
        putInt(ihdr, 0, size.width)
        putInt(ihdr, 4, size.height)
        ihdr[8] = 8   // bit depth
        ihdr[9] = 6   // colour type RGBA
        ihdr[10] = 0  // compression
        ihdr[11] = 0  // filter
        ihdr[12] = 0  // interlace
        writeChunk(TYPE_IHDR, ihdr, 0, ihdr.size)
        writeChunk(TYPE_SRGB, byteArrayOf(0), 0, 1)
    }

    /**
     * Writes one ARGB row as filter-type-0 RGBA samples. [offset] selects the
     * row's first pixel inside [argb], which must hold at least
     * `offset + size.width` entries.
     */
    override fun writeRow(argb: IntArray, offset: Int) {
        check(!closed) { "writer is closed" }
        check(!finished) { "writer is already finished" }
        check(rowsWritten < size.height) {
            "too many rows: image height is ${size.height}"
        }
        require(offset >= 0) { "offset must be non-negative: $offset" }
        require(offset.toLong() + size.width <= argb.size.toLong()) {
            "row of ${size.width} pixels does not fit at offset $offset " +
                "in a ${argb.size}-element array"
        }
        val row = rowBuffer
        for (x in 0 until size.width) {
            val argbPixel = argb[offset + x]
            val base = 1 + x * 4
            row[base] = (argbPixel shr 16).toByte()
            row[base + 1] = (argbPixel shr 8).toByte()
            row[base + 2] = argbPixel.toByte()
            row[base + 3] = (argbPixel shr 24).toByte()
        }
        deflateStream.write(row)
        rowsWritten += 1
    }

    /**
     * Completes the PNG. Fails unless exactly `size.height` rows were written;
     * on failure the caller can still [close] and discard the output.
     */
    override fun finish() {
        check(!closed) { "writer is closed" }
        check(!finished) { "writer is already finished" }
        check(rowsWritten == size.height) {
            "expected ${size.height} rows but wrote $rowsWritten"
        }
        try {
            deflateStream.finish()
            idat.emitTail()
            writeChunk(TYPE_IEND, EMPTY, 0, 0)
            output.flush()
            finished = true
        } finally {
            deflater.end()
            // A failed finalize is terminal: the deflater is gone, so a
            // subsequent writeRow/finish must reject rather than corrupt.
            if (!finished) closed = true
        }
    }

    /** Releases the deflater; does not complete the PNG or close [output]. */
    override fun close() {
        if (closed) return
        closed = true
        deflater.end()
    }

    /** Accumulates deflate output and emits bounded IDAT chunks. */
    private inner class ChunkedIdatOutput : OutputStream() {
        private val buffer = ByteArray(IDAT_CHUNK_BYTES)
        private var length = 0

        override fun write(b: Int) {
            buffer[length] = b.toByte()
            length += 1
            if (length == buffer.size) emit()
        }

        override fun write(b: ByteArray, off: Int, len: Int) {
            var offset = off
            var remaining = len
            while (remaining > 0) {
                val n = minOf(remaining, buffer.size - length)
                System.arraycopy(b, offset, buffer, length, n)
                length += n
                offset += n
                remaining -= n
                if (length == buffer.size) emit()
            }
        }

        private fun emit() {
            writeChunk(TYPE_IDAT, buffer, 0, length)
            length = 0
        }

        fun emitTail() {
            if (length > 0) emit()
        }

        // Deliberately no tail emission on close: a cancelled export must
        // leave a truncated file, never a silently valid one.
        override fun close() = Unit
    }

    private fun writeChunk(type: Int, data: ByteArray, offset: Int, length: Int) {
        writeInt(output, length)
        val typeBytes = byteArrayOf(
            (type shr 24).toByte(), (type shr 16).toByte(),
            (type shr 8).toByte(), type.toByte()
        )
        output.write(typeBytes)
        if (length > 0) output.write(data, offset, length)
        crc.reset()
        crc.update(typeBytes, 0, 4)
        if (length > 0) crc.update(data, offset, length)
        writeInt(output, crc.value.toInt())
    }

    private companion object {
        val PNG_SIGNATURE =
            byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)
        val EMPTY = ByteArray(0)
        const val IDAT_CHUNK_BYTES = 65_536

        // Chunk type codes as big-endian ASCII ints.
        const val TYPE_IHDR = 0x49484452
        const val TYPE_SRGB = 0x73524742
        const val TYPE_IDAT = 0x49444154
        const val TYPE_IEND = 0x49454E44

        fun checkedRowBytes(width: Int): Int {
            require(width.toLong() * 4 + 1 <= Int.MAX_VALUE.toLong()) {
                "row too wide to buffer: $width"
            }
            return width * 4 + 1
        }

        fun putInt(target: ByteArray, offset: Int, value: Int) {
            target[offset] = (value shr 24).toByte()
            target[offset + 1] = (value shr 16).toByte()
            target[offset + 2] = (value shr 8).toByte()
            target[offset + 3] = value.toByte()
        }

        fun writeInt(output: OutputStream, value: Int) {
            output.write(value shr 24)
            output.write(value shr 16)
            output.write(value shr 8)
            output.write(value)
        }
    }
}
