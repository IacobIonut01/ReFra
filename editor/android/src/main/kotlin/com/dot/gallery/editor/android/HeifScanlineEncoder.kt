package com.dot.gallery.editor.android

/**
 * JNI bridge to the packaged libheif tiled (grid-image) encoder.
 *
 * The export hands over one 512×512 tile row at a time, so a full-resolution
 * HEIC is assembled without ever holding the whole output bitmap. The
 * library is compiled to stubs when no prebuilt libheif/x265/aom exists for
 * the ABI; [isAvailable] is false there and the format is not offered.
 */
internal object HeifScanlineEncoder {

    /** libheif compression target for `.heic` stills (HEVC). */
    const val FORMAT_HEIC = 0

    @Volatile
    private var availability: Boolean? = null

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

    /** Opens a grid encoder writing to [fd]. Returns a handle, or 0 on failure. */
    external fun nativeOpen(
        fd: Int,
        width: Int,
        height: Int,
        tileWidth: Int,
        tileHeight: Int,
        format: Int,
        quality: Int
    ): Long

    /** Encodes one [tileW]x[tileH] tile of packed-ARGB [argb] at grid ([tileX],[tileY]). */
    external fun nativeEncodeTile(
        handle: Long,
        argb: IntArray,
        tileW: Int,
        tileH: Int,
        tileX: Int,
        tileY: Int
    ): Boolean

    /** Writes the assembled HEIF file and releases the encoder. */
    external fun nativeFinish(handle: Long): Boolean

    /** Releases an unfinished encoder without writing a file tail. */
    external fun nativeAbort(handle: Long)
}
