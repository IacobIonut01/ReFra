package com.dot.gallery.core.decoder

import android.os.Build
import androidx.annotation.RequiresApi
import com.github.panpf.sketch.ComponentRegistry
import com.github.panpf.sketch.decode.Decoder
import com.github.panpf.sketch.decode.internal.ImageDecoderAnimatedDecoder
import com.github.panpf.sketch.fetch.FetchResult
import com.github.panpf.sketch.request.RequestContext
import com.github.panpf.sketch.request.disallowAnimatedImage
import com.github.panpf.sketch.source.DataSource

/**
 * Extension function to add APNG animated image support to Sketch.
 * Uses Android's ImageDecoder (API 28+) which natively decodes APNG
 * into AnimatedImageDrawable.
 */
@RequiresApi(Build.VERSION_CODES.P)
fun ComponentRegistry.Builder.supportApng(): ComponentRegistry.Builder = apply {
    add(SketchApngDecoder.Factory())
}

/**
 * Decoder for Animated PNG (APNG) images using Android's ImageDecoder.
 *
 * APNG files are PNG files that contain animation frames via the `acTL`
 * (Animation Control) chunk. Android's ImageDecoder natively produces
 * AnimatedImageDrawable for APNG, which the base
 * [ImageDecoderAnimatedDecoder] handles properly.
 *
 * Detection: checks for PNG signature + presence of `acTL` chunk in
 * the header bytes, OR the `image/apng` MIME type.
 */
@RequiresApi(Build.VERSION_CODES.P)
class SketchApngDecoder(
    requestContext: RequestContext,
    dataSource: DataSource,
) : ImageDecoderAnimatedDecoder(requestContext, dataSource) {

    companion object {
        const val SORT_WEIGHT = 14 // Higher priority than GIF/WebP decoders (15)

        // PNG file signature: 8 bytes
        private val PNG_SIGNATURE = byteArrayOf(
            0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A
        )

        // "acTL" chunk type that indicates APNG animation control
        private val ACTL_CHUNK = byteArrayOf(0x61, 0x63, 0x54, 0x4C)

        /**
         * Checks if the byte array starts with the PNG signature.
         */
        fun ByteArray.isPng(): Boolean {
            if (size < PNG_SIGNATURE.size) return false
            return PNG_SIGNATURE.indices.all { this[it] == PNG_SIGNATURE[it] }
        }

        /**
         * Checks if the byte array is an APNG (has PNG signature and
         * contains an acTL chunk before the first IDAT chunk).
         *
         * PNG chunk layout: 4-byte length + 4-byte type + data + 4-byte CRC.
         * We scan chunks starting after the 8-byte PNG signature.
         */
        fun ByteArray.isApng(): Boolean {
            if (!isPng()) return false
            var offset = 8 // Skip PNG signature
            while (offset + 8 <= size) {
                // Read chunk length (big-endian)
                val length = ((this[offset].toInt() and 0xFF) shl 24) or
                        ((this[offset + 1].toInt() and 0xFF) shl 16) or
                        ((this[offset + 2].toInt() and 0xFF) shl 8) or
                        (this[offset + 3].toInt() and 0xFF)

                val typeOffset = offset + 4
                if (typeOffset + 4 > size) break

                // Check for acTL chunk → APNG
                if (this[typeOffset] == ACTL_CHUNK[0] &&
                    this[typeOffset + 1] == ACTL_CHUNK[1] &&
                    this[typeOffset + 2] == ACTL_CHUNK[2] &&
                    this[typeOffset + 3] == ACTL_CHUNK[3]
                ) {
                    return true
                }

                // Check for IDAT chunk → stop searching (acTL must come before IDAT)
                if (this[typeOffset] == 0x49.toByte() && // 'I'
                    this[typeOffset + 1] == 0x44.toByte() && // 'D'
                    this[typeOffset + 2] == 0x41.toByte() && // 'A'
                    this[typeOffset + 3] == 0x54.toByte()    // 'T'
                ) {
                    return false
                }

                // Move to the next chunk: 4 (length) + 4 (type) + length (data) + 4 (CRC)
                offset += 4 + 4 + length + 4
            }
            return false
        }
    }

    class Factory : Decoder.Factory {

        override val key: String = "SketchApngDecoder"
        override val sortWeight: Int = SORT_WEIGHT

        override fun create(
            requestContext: RequestContext,
            fetchResult: FetchResult
        ): SketchApngDecoder? {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return null
            if (requestContext.request.disallowAnimatedImage == true) return null
            if (!isApplicable(fetchResult)) return null
            return SketchApngDecoder(requestContext, fetchResult.dataSource)
        }

        private fun isApplicable(fetchResult: FetchResult): Boolean {
            // Check MIME type first (fast path)
            val mimeType = fetchResult.dataFrom?.toString() ?: ""
            if (mimeType == "image/apng") return true
            // Check header bytes for APNG signature (acTL chunk)
            return fetchResult.headerBytes.isApng()
        }

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            return other != null && this::class == other::class
        }

        override fun hashCode(): Int {
            return this::class.hashCode()
        }

        override fun toString(): String = "SketchApngDecoder"
    }
}
