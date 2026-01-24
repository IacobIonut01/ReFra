package com.dot.gallery.core.decoder.glide

import android.content.Context
import android.graphics.Bitmap
import com.bumptech.glide.load.Options
import com.bumptech.glide.load.ResourceDecoder
import com.bumptech.glide.load.Transformation
import com.bumptech.glide.load.engine.Resource
import com.bumptech.glide.load.engine.bitmap_recycle.ArrayPool
import com.bumptech.glide.load.engine.bitmap_recycle.BitmapPool
import com.bumptech.glide.load.resource.gif.GifDrawable
import com.bumptech.glide.load.resource.gif.GifDrawableResource
import com.bumptech.glide.load.resource.gif.GifBitmapProvider
import com.bumptech.glide.load.resource.UnitTransformation
import com.bumptech.glide.gifdecoder.GifDecoder
import com.bumptech.glide.gifdecoder.GifHeaderParser
import com.bumptech.glide.gifdecoder.StandardGifDecoder
import java.nio.ByteBuffer

/**
 * Decoder for encrypted GIF images that produces animated GifDrawable.
 * This ensures GIFs in the vault animate properly instead of showing static frames.
 */
class EncryptedGifDecoder(
    private val context: Context,
    private val bitmapPool: BitmapPool,
    private val arrayPool: ArrayPool
) : ResourceDecoder<EncryptedMediaStream, GifDrawable> {

    override fun handles(source: EncryptedMediaStream, options: Options): Boolean {
        if (source.isVideo) return false
        val mime = source.mimeType.lowercase()
        return mime == "image/gif"
    }

    override fun decode(
        source: EncryptedMediaStream,
        width: Int,
        height: Int,
        options: Options
    ): Resource<GifDrawable>? {
        val bytes = source.bytes
        val byteBuffer = ByteBuffer.wrap(bytes)
        
        // Parse GIF header
        val parser = GifHeaderParser()
        parser.setData(byteBuffer)
        val header = parser.parseHeader()
        
        if (header.numFrames <= 0 || header.status != GifDecoder.STATUS_OK) {
            return null
        }
        
        // Compute sample size for better performance on large GIFs
        val sampleSize = getSampleSize(header.width, header.height, width, height)
        
        // Create GIF decoder with proper provider
        val bitmapProvider = GifBitmapProvider(bitmapPool, arrayPool)
        val gifDecoder = StandardGifDecoder(bitmapProvider, header, byteBuffer, sampleSize)
        gifDecoder.setDefaultBitmapConfig(Bitmap.Config.ARGB_8888)
        gifDecoder.advance()
        
        val firstFrame = gifDecoder.nextFrame ?: return null
        
        // Use UnitTransformation (no transformation)
        val unitTransformation: Transformation<Bitmap> = UnitTransformation.get()
        
        val targetWidth = if (width > 0) width else header.width / sampleSize
        val targetHeight = if (height > 0) height else header.height / sampleSize
        
        val gifDrawable = GifDrawable(
            context,
            gifDecoder,
            unitTransformation,
            targetWidth,
            targetHeight,
            firstFrame
        )
        
        return GifDrawableResource(gifDrawable)
    }

    private fun getSampleSize(srcWidth: Int, srcHeight: Int, targetWidth: Int, targetHeight: Int): Int {
        if (targetWidth <= 0 || targetHeight <= 0) return 1
        
        val exactWidthScale = srcWidth.toFloat() / targetWidth
        val exactHeightScale = srcHeight.toFloat() / targetHeight
        val exactScale = minOf(exactWidthScale, exactHeightScale)
        
        // Round down to nearest power of 2
        val powerOfTwo = Integer.highestOneBit(exactScale.toInt().coerceAtLeast(1))
        return powerOfTwo.coerceIn(1, 4) // Limit sample size to reasonable range
    }
}
