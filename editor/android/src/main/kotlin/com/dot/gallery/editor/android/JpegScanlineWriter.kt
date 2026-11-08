package com.dot.gallery.editor.android

import com.dot.gallery.editor.core.model.PixelSize
import com.dot.gallery.editor.render.photo.ScanlineSink

/**
 * [ScanlineSink] over the native streaming JPEG encoder. Rows arrive one band
 * at a time from the strip exporter; they are batched into a fixed buffer and
 * handed to libjpeg as a block, so peak memory stays `O(row + batch)` and the
 * full-resolution output is never materialised.
 *
 * [finish] is the only call that produces a valid file tail — [close] without
 * it aborts the encoder, leaving a deliberately truncated file the caller
 * deletes.
 */
internal class JpegScanlineWriter(
    fd: Int,
    private val size: PixelSize,
    quality: Int
) : ScanlineSink {

    private var handle = JpegScanlineEncoder.nativeOpen(
        fd, size.width, size.height, quality
    )
    private val rowBuffer = IntArray(size.width * BATCH_ROWS)
    private var bufferedRows = 0
    private var rowsWritten = 0
    private var finished = false
    private var closed = false

    init {
        check(handle != 0L) { "JPEG encoder could not be opened" }
    }

    override fun writeRow(argb: IntArray, offset: Int) {
        check(!closed) { "writer is closed" }
        check(!finished) { "writer is already finished" }
        check(rowsWritten < size.height) {
            "too many rows: image height is ${size.height}"
        }
        argb.copyInto(
            destination = rowBuffer,
            destinationOffset = bufferedRows * size.width,
            startIndex = offset,
            endIndex = offset + size.width
        )
        bufferedRows += 1
        rowsWritten += 1
        if (bufferedRows == BATCH_ROWS) flushRows()
    }

    private fun flushRows() {
        if (bufferedRows == 0) return
        val written = JpegScanlineEncoder.nativeWriteRows(handle, rowBuffer, bufferedRows)
        bufferedRows = 0
        check(written) { "JPEG row write failed" }
    }

    /**
     * Completes the file. Fails unless exactly `size.height` rows were written;
     * on failure the encoder is already released, so the caller can only
     * discard the staged output.
     */
    override fun finish() {
        check(!closed) { "writer is closed" }
        check(!finished) { "writer is already finished" }
        check(rowsWritten == size.height) {
            "expected ${size.height} rows but wrote $rowsWritten"
        }
        flushRows()
        val completed = JpegScanlineEncoder.nativeFinish(handle)
        handle = 0L
        finished = true
        check(completed) { "JPEG encoder failed to finish" }
    }

    /** Aborts an unfinished encode; never writes a valid tail. */
    override fun close() {
        if (closed) return
        closed = true
        if (!finished && handle != 0L) {
            JpegScanlineEncoder.nativeAbort(handle)
            handle = 0L
        }
    }

    private companion object {
        /** Rows per native call: bounded buffer, far fewer JNI crossings. */
        const val BATCH_ROWS = 32
    }
}
