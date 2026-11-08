package com.dot.gallery.editor.android

import com.dot.gallery.editor.core.model.PixelSize
import com.dot.gallery.editor.render.photo.ScanlineSink
import kotlin.math.min

/**
 * [ScanlineSink] over libheif's tiled grid encoder.
 *
 * Rows arrive one band at a time from the strip exporter; [TILE] of them are
 * buffered and then handed to the encoder as one grid row of 512×512 tiles.
 * Peak memory is one tile row (`width × 512 × 4` bytes) plus one tile,
 * independent of the photo's height, so a 50 MP HEIC exports without the
 * whole frame ever being in RAM.
 *
 * [finish] is the only call that writes a valid file tail — [close] without
 * it aborts the encoder, leaving a truncated file the caller deletes.
 */
internal class HeifScanlineWriter(
    fd: Int,
    private val size: PixelSize,
    quality: Int
) : ScanlineSink {

    // The availability probe loads the shared library; without it the JNI
    // call would throw UnsatisfiedLinkError instead of a clear failure.
    private var handle = if (HeifScanlineEncoder.isAvailable()) {
        HeifScanlineEncoder.nativeOpen(
            fd,
            size.width,
            size.height,
            TILE,
            TILE,
            HeifScanlineEncoder.FORMAT_HEIC,
            quality
        )
    } else {
        0L
    }
    private val rows = IntArray(size.width * TILE)
    private val tilePixels = IntArray(TILE * TILE)
    private var bufferedRows = 0
    private var rowsWritten = 0
    private var tileRow = 0
    private var finished = false
    private var closed = false

    init {
        check(handle != 0L) { "HEIF encoder could not be opened" }
    }

    override fun writeRow(argb: IntArray, offset: Int) {
        check(!closed) { "writer is closed" }
        check(!finished) { "writer is already finished" }
        check(rowsWritten < size.height) {
            "too many rows: image height is ${size.height}"
        }
        argb.copyInto(
            destination = rows,
            destinationOffset = bufferedRows * size.width,
            startIndex = offset,
            endIndex = offset + size.width
        )
        bufferedRows += 1
        rowsWritten += 1
        if (bufferedRows == TILE) flushTileRow()
    }

    /** Encodes the buffered band as one grid row, one tile per column. */
    private fun flushTileRow() {
        if (bufferedRows == 0) return
        val bandRows = bufferedRows
        var column = 0
        var x = 0
        while (x < size.width) {
            val tileWidth = min(TILE, size.width - x)
            for (y in 0 until bandRows) {
                val source = y * size.width + x
                rows.copyInto(
                    destination = tilePixels,
                    destinationOffset = y * tileWidth,
                    startIndex = source,
                    endIndex = source + tileWidth
                )
            }
            val encoded = HeifScanlineEncoder.nativeEncodeTile(
                handle, tilePixels, tileWidth, bandRows, column, tileRow
            )
            check(encoded) { "HEIF tile ($column,$tileRow) failed" }
            column += 1
            x += TILE
        }
        tileRow += 1
        bufferedRows = 0
    }

    /**
     * Completes the file. Fails unless exactly `size.height` rows were written;
     * the encoder is released either way.
     */
    override fun finish() {
        check(!closed) { "writer is closed" }
        check(!finished) { "writer is already finished" }
        check(rowsWritten == size.height) {
            "expected ${size.height} rows but wrote $rowsWritten"
        }
        flushTileRow()
        val completed = HeifScanlineEncoder.nativeFinish(handle)
        handle = 0L
        finished = true
        check(completed) { "HEIF encoder failed to finish" }
    }

    /** Aborts an unfinished encode; never writes a valid tail. */
    override fun close() {
        if (closed) return
        closed = true
        if (!finished && handle != 0L) {
            HeifScanlineEncoder.nativeAbort(handle)
            handle = 0L
        }
    }

    private companion object {
        /** libheif grid cell size; every tile in the grid is this size. */
        const val TILE = 512
    }
}
