package com.dot.gallery.editor.android

/**
 * JNI bridge to the packaged scanline JPEG encoder (`librefra_editor_jni`).
 *
 * Export feeds the full-resolution result one row band at a time, so a
 * lossless PNG of a camera photo lands an order of magnitude above the JPEG it
 * came from. This encoder writes the JPEG the source was created as, at the
 * source's own quality, without ever holding the output bitmap in memory.
 *
 * The bridge is compiled to stubs when no prebuilt libjpeg-turbo exists for the
 * ABI; [isAvailable] is false there and the export keeps the lossless PNG
 * fallback rather than failing.
 */
internal object JpegScanlineEncoder {

    @Volatile
    private var availability: Boolean? = null

    /**
     * Loads the native library once and reports whether this build carries a
     * real libjpeg-turbo. Safe to call from export I/O threads; a missing
     * library or a stub build is not an error (the caller falls back).
     */
    fun isAvailable(): Boolean {
        availability?.let { return it }
        val available = runCatching {
            AndroidEditorNativeLoader().load()
            nativeSelfTest()
        }.getOrDefault(false)
        availability = available
        return available
    }

    external fun nativeSelfTest(): Boolean

    external fun nativeOpen(fd: Int, width: Int, height: Int, quality: Int): Long

    external fun nativeWriteRows(handle: Long, argb: IntArray, rowCount: Int): Boolean

    external fun nativeFinish(handle: Long): Boolean

    external fun nativeAbort(handle: Long)
}
