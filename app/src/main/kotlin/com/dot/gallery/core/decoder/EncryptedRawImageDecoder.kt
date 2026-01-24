package com.dot.gallery.core.decoder

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.graphics.Movie
import android.graphics.drawable.AnimatedImageDrawable
import android.os.Build
import androidx.annotation.RequiresApi
import com.dot.gallery.BuildConfig
import com.dot.gallery.feature_node.data.data_source.KeychainHolder
import com.dot.gallery.feature_node.domain.model.Media.EncryptedMedia
import com.github.panpf.sketch.asImage
import com.github.panpf.sketch.decode.DecodeConfig
import com.github.panpf.sketch.decode.DecodeResult
import com.github.panpf.sketch.decode.Decoder
import com.github.panpf.sketch.decode.ImageInfo
import com.github.panpf.sketch.drawable.AnimatableDrawable
import com.github.panpf.sketch.drawable.MovieDrawable
import com.github.panpf.sketch.drawable.ScaledAnimatableDrawable
import com.github.panpf.sketch.fetch.FetchResult
import com.github.panpf.sketch.request.RequestContext
import com.github.panpf.sketch.request.animatedTransformation
import com.github.panpf.sketch.request.animationEndCallback
import com.github.panpf.sketch.request.animationStartCallback
import com.github.panpf.sketch.request.get
import com.github.panpf.sketch.request.repeatCount
import com.github.panpf.sketch.source.DataSource
import com.github.panpf.sketch.source.FileDataSource
import com.github.panpf.sketch.util.animatable2CompatCallbackOf
import com.github.panpf.sketch.util.safeToSoftware
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import java.nio.ByteBuffer

/**
 * Decoder for encrypted raw image formats (GIF, WebP, SVG, BMP).
 * 
 * - GIF: Always animated using MovieDrawable
 * - WebP: Animated using AnimatedImageDrawable on API 28+, static fallback otherwise
 * - SVG, BMP: Static bitmap decoding
 * 
 * This ensures raw image formats in the vault display properly in full-screen MediaView,
 * preserving animation for GIF and animated WebP files.
 */
class EncryptedRawImageDecoder(
    private val requestContext: RequestContext,
    private val dataSource: DataSource,
    private val mimeType: String,
) : Decoder {

    private val keychainHolder = KeychainHolder(requestContext.request.context)
    
    private var _imageInfo: ImageInfo? = null
    private var cachedBytes: ByteArray? = null
    
    override val imageInfo: ImageInfo
        get() {
            if (_imageInfo == null) {
                _imageInfo = readImageInfo()
            }
            return _imageInfo!!
        }
    
    private fun getDecryptedBytes(): ByteArray {
        if (cachedBytes == null) {
            val encryptedFile = dataSource.getFile()
            val encryptedMedia = with(keychainHolder) {
                encryptedFile.decryptKotlin<EncryptedMedia>()
            }
            cachedBytes = encryptedMedia.bytes
        }
        return cachedBytes!!
    }
    
    private fun readImageInfo(): ImageInfo {
        val bytes = getDecryptedBytes()
        return when (mimeType) {
            "image/gif" -> readGifImageInfo(bytes)
            "image/webp" -> readWebPImageInfo(bytes)
            else -> readBitmapImageInfo(bytes)
        }
    }
    
    @Suppress("DEPRECATION")
    private fun readGifImageInfo(bytes: ByteArray): ImageInfo {
        val movie = Movie.decodeByteArray(bytes, 0, bytes.size)
        return if (movie != null) {
            ImageInfo(movie.width(), movie.height(), mimeType)
        } else {
            // Fallback to BitmapFactory
            readBitmapImageInfo(bytes)
        }
    }
    
    private fun readWebPImageInfo(bytes: ByteArray): ImageInfo {
        return readBitmapImageInfo(bytes)
    }
    
    private fun readBitmapImageInfo(bytes: ByteArray): ImageInfo {
        val options = BitmapFactory.Options().apply {
            inJustDecodeBounds = true
        }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
        return ImageInfo(options.outWidth, options.outHeight, mimeType)
    }
    
    private fun isAnimatedWebP(bytes: ByteArray): Boolean {
        // WebP header: RIFF....WEBP
        // Check for VP8X chunk with animation flag
        if (bytes.size < 20) return false
        
        // Check RIFF header
        if (bytes[0] != 'R'.code.toByte() || 
            bytes[1] != 'I'.code.toByte() ||
            bytes[2] != 'F'.code.toByte() ||
            bytes[3] != 'F'.code.toByte()) return false
            
        // Check WEBP
        if (bytes[8] != 'W'.code.toByte() ||
            bytes[9] != 'E'.code.toByte() ||
            bytes[10] != 'B'.code.toByte() ||
            bytes[11] != 'P'.code.toByte()) return false
            
        // Check VP8X chunk (extended format that can contain animation)
        if (bytes[12] != 'V'.code.toByte() ||
            bytes[13] != 'P'.code.toByte() ||
            bytes[14] != '8'.code.toByte() ||
            bytes[15] != 'X'.code.toByte()) return false
            
        // Animation flag is bit 1 (0x02) of the flags byte at offset 16
        return (bytes[16].toInt() and 0x02) != 0
    }

    override fun decode(): DecodeResult {
        val bytes = getDecryptedBytes()
        
        return when (mimeType) {
            "image/gif" -> decodeGif(bytes)
            "image/webp" -> decodeWebP(bytes)
            else -> decodeStaticBitmap(bytes)
        }
    }
    
    @Suppress("DEPRECATION")
    private fun decodeGif(bytes: ByteArray): DecodeResult {
        val request = requestContext.request
        
        val movie = Movie.decodeByteArray(bytes, 0, bytes.size)
            ?: throw IllegalStateException("Failed to decode GIF movie")
        
        val decodeConfig = DecodeConfig(request, mimeType, isOpaque = movie.isOpaque)
        val config = decodeConfig.colorType.safeToSoftware()
        
        val movieDrawable = MovieDrawable(movie, config).apply {
            setRepeatCount(request.repeatCount ?: 0)
            setAnimatedTransformation(request.animatedTransformation)
        }
        
        val animatableDrawable = AnimatableDrawable(movieDrawable).apply {
            val onStart = request.animationStartCallback
            val onEnd = request.animationEndCallback
            if (onStart != null || onEnd != null) {
                @Suppress("OPT_IN_USAGE")
                GlobalScope.launch(Dispatchers.Main) {
                    registerAnimationCallback(animatable2CompatCallbackOf(onStart, onEnd))
                }
            }
        }
        
        val resize = requestContext.computeResize(imageInfo.size)
        return DecodeResult(
            image = animatableDrawable.asImage(),
            imageInfo = imageInfo,
            dataFrom = dataSource.dataFrom,
            resize = resize,
            transformeds = null,
            extras = null,
        )
    }
    
    private fun decodeWebP(bytes: ByteArray): DecodeResult {
        val isAnimated = isAnimatedWebP(bytes)
        
        return if (isAnimated && Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            decodeAnimatedWebP(bytes)
        } else {
            decodeStaticBitmap(bytes)
        }
    }
    
    @RequiresApi(Build.VERSION_CODES.P)
    private fun decodeAnimatedWebP(bytes: ByteArray): DecodeResult {
        val request = requestContext.request
        
        val source = ImageDecoder.createSource(ByteBuffer.wrap(bytes))
        val drawable = ImageDecoder.decodeDrawable(source) { decoder, info, _ ->
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
        }
        
        if (drawable is AnimatedImageDrawable) {
            drawable.repeatCount = request.repeatCount
                ?.takeIf { it != 0 }
                ?: AnimatedImageDrawable.REPEAT_INFINITE
            
            val scaledDrawable = ScaledAnimatableDrawable(drawable).apply {
                val onStart = request.animationStartCallback
                val onEnd = request.animationEndCallback
                if (onStart != null || onEnd != null) {
                    @Suppress("OPT_IN_USAGE")
                    GlobalScope.launch(Dispatchers.Main) {
                        registerAnimationCallback(animatable2CompatCallbackOf(onStart, onEnd))
                    }
                }
            }
            
            val resize = requestContext.computeResize(imageInfo.size)
            return DecodeResult(
                image = scaledDrawable.asImage(),
                imageInfo = imageInfo,
                dataFrom = dataSource.dataFrom,
                resize = resize,
                transformeds = null,
                extras = null,
            )
        } else {
            // Not animated, fall back to static decoding
            return decodeStaticBitmap(bytes)
        }
    }
    
    private fun decodeStaticBitmap(bytes: ByteArray): DecodeResult {
        val request = requestContext.request
        val decodeConfig = DecodeConfig(request, mimeType, isOpaque = false)
        val config = decodeConfig.colorType.safeToSoftware()
        
        val options = BitmapFactory.Options().apply {
            inPreferredConfig = config
        }
        
        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
            ?: throw IllegalStateException("Failed to decode bitmap for $mimeType")
        
        val resize = requestContext.computeResize(imageInfo.size)
        return DecodeResult(
            image = bitmap.asImage(),
            imageInfo = imageInfo,
            dataFrom = dataSource.dataFrom,
            resize = resize,
            transformeds = null,
            extras = null,
        )
    }

    class Factory : Decoder.Factory {

        override val key: String = "EncryptedRawImageDecoder"
        
        private val supportedMimeTypes = listOf(
            "image/gif",
            "image/webp",
            "image/svg+xml",
            "image/bmp"
        )

        override fun create(
            requestContext: RequestContext,
            fetchResult: FetchResult
        ): Decoder? {
            val mimeType = requestContext.request.extras?.get("realMimeType") as? String ?: return null
            if (mimeType !in supportedMimeTypes) return null
            val dataSource = fetchResult.dataSource as? FileDataSource ?: return null
            val path = dataSource.getFile().path
            return if (path.toString().contains(BuildConfig.APPLICATION_ID))
                EncryptedRawImageDecoder(requestContext, dataSource, mimeType)
            else null
        }

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            return other != null && this::class == other::class
        }

        override fun hashCode(): Int {
            return this::class.hashCode()
        }

        override fun toString(): String = "EncryptedRawImageDecoder"
    }
}
