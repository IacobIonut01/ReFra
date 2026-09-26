package com.dot.gallery.core.decoder.glide

import android.content.Context
import android.graphics.Bitmap
import com.bumptech.glide.load.Options
import com.bumptech.glide.load.ResourceDecoder
import com.bumptech.glide.load.engine.Resource
import com.bumptech.glide.load.engine.bitmap_recycle.BitmapPool
import com.bumptech.glide.load.resource.bitmap.BitmapResource
import com.dot.gallery.core.sandbox.SandboxedDecoderHolder
import com.dot.gallery.feature_node.presentation.util.printError

/**
 * HEIF/AVIF decoder operating on MimeInputStream so we can rely on MIME instead of header scan.
 * Skips when sandboxed decoding is enabled — [SandboxedHeifMimeDecoder] handles that path.
 */
class HeifMimeInputStreamDecoder(
    private val context: Context,
    private val bitmapPool: BitmapPool
) : ResourceDecoder<MimeInputStream, Bitmap> {
    private val core = HeifDecoderCore(bitmapPool, "HeifMimeDecoder")

    override fun handles(source: MimeInputStream, options: Options): Boolean {
        if (SandboxedDecoderHolder.isEnabled(context)) return false
        return HeifMime.isHeifMime(source.mimeType)
    }

    override fun decode(
        source: MimeInputStream,
        width: Int,
        height: Int,
        options: Options
    ): Resource<Bitmap>? {
        return try {
            val bytes = source.inputStream.readBytes()
            core.decodeBytes(bytes, width, height, source.mimeType).resource
        } catch (e: Throwable) {
            printError("decode.heif", "stream read failed mime=${source.mimeType}: ${e.message}", e)
            null
        }
    }
}
