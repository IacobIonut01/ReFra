package com.dot.gallery.editor.android

/**
 * Availability probe for the JPEG XL encoder.
 *
 * The encoder itself comes from `jxl-coder` (libjxl packaged as a shared
 * library); it is a whole-image encoder — libjxl takes one full frame — so
 * the export path renders into a single bitmap before handing it over. The
 * library is loaded here once so the Save menu only lists JPEG XL when this
 * device actually carries a working `libjxlcoder`.
 */
internal object JxlBitmapEncoder {

    @Volatile
    private var availability: Boolean? = null

    fun isAvailable(): Boolean {
        availability?.let { return it }
        val available = runCatching {
            System.loadLibrary("jxlcoder")
        }.isSuccess
        availability = available
        return available
    }
}
