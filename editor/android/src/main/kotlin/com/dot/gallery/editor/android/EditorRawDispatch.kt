package com.dot.gallery.editor.android

import android.graphics.Bitmap
import com.dot.gallery.editor.core.model.PhotoRawDevelop
import com.dot.gallery.editor.core.model.PixelSize

/**
 * The RAW decoder a host may install. The editor library carries no LibRaw of its
 * own, so without a host a RAW source is rejected like any other unsupported file.
 * Calls are blocking, and failures return null or false instead of throwing.
 */
interface EditorRawDecoder {
    /** The upright developed size, or null when [data] is not a readable RAW. */
    fun size(data: ByteArray): PixelSize?

    /**
     * Decodes [data] to upright pixels at full size, or half size when [halfSize]
     * is set. The develop's tone stage is included.
     */
    fun demosaic(data: ByteArray, develop: PhotoRawDevelop, halfSize: Boolean): Bitmap?

    /** Re-tones an already decoded base with [develop]'s tone stage. */
    fun applyTone(bitmap: Bitmap, develop: PhotoRawDevelop): Bitmap?

    /** Streams a TIFF of [bits] per sample straight from the decoder to [fd]. */
    fun exportTiff(data: ByteArray, develop: PhotoRawDevelop, fd: Int, bits: Int): Boolean
}

object EditorRawDispatch {
    @Volatile
    var decoder: EditorRawDecoder? = null
}
