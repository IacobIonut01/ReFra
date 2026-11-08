package com.dot.gallery.editor.render.photo

/**
 * Row-at-a-time encoded-image destination. [writeRow] receives one output row
 * of packed ARGB pixels starting at [offset]; [finish] is called once after
 * the final row. Implementations reject extra or short rows and must not emit
 * a well-formed tail on [close] without [finish] — a cancelled export leaves a
 * deliberately truncated file for the caller to delete.
 *
 * The caller owns the sink's lifecycle and the underlying output.
 */
interface ScanlineSink : AutoCloseable {

    fun writeRow(argb: IntArray, offset: Int = 0)

    fun finish()

    override fun close()
}
