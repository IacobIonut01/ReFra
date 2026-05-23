package com.dot.gallery.core.decoder

import com.awxkee.jxlcoder.JxlAnimatedImage
import com.awxkee.jxlcoder.JxlCoder
import com.dot.gallery.core.decoder.SketchJxlDecoder.Factory.Companion.JXL_MIMETYPE
import com.github.panpf.sketch.ComponentRegistry
import com.github.panpf.sketch.asImage
import com.github.panpf.sketch.decode.Decoder
import com.github.panpf.sketch.decode.ImageInfo
import com.github.panpf.sketch.drawable.ScaledAnimatableDrawable
import com.github.panpf.sketch.fetch.FetchResult
import com.github.panpf.sketch.request.ImageData
import com.github.panpf.sketch.request.RequestContext
import com.github.panpf.sketch.request.animationEndCallback
import com.github.panpf.sketch.request.animationStartCallback
import com.github.panpf.sketch.request.get
import com.github.panpf.sketch.request.repeatCount
import com.github.panpf.sketch.source.DataSource
import com.github.panpf.sketch.util.animatable2CompatCallbackOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import okio.buffer

fun ComponentRegistry.Builder.supportAnimatedJxlDecoder(): ComponentRegistry.Builder = apply {
    add(SketchAnimatedJxlDecoder.Factory())
}

class SketchAnimatedJxlDecoder(
    private val requestContext: RequestContext,
    private val dataSource: DataSource,
) : Decoder {

    class Factory : Decoder.Factory {

        override val key: String
            get() = "AnimatedJxlDecoder"

        override val sortWeight: Int = 0

        override fun create(requestContext: RequestContext, fetchResult: FetchResult): Decoder? {
            val mimeType = requestContext.request.extras?.get("realMimeType") as String? ?: return null
            return if (mimeType.contains(JXL_MIMETYPE)) {
                SketchAnimatedJxlDecoder(requestContext, fetchResult.dataSource)
            } else {
                null
            }
        }

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            return other is Factory
        }

        override fun hashCode(): Int {
            return this@Factory::class.hashCode()
        }

        override fun toString(): String = key
    }

    override suspend fun decode(): ImageData {
        val bytes = dataSource.openSource().use { src ->
            src.buffer().readByteArray()
        }

        val animatedImage = JxlAnimatedImage(bytes)

        if (animatedImage.numberOfFrames <= 1) {
            animatedImage.close()
            // Not animated, delegate to static decoding
            return dataSource.withCustomDecoder(
                requestContext = requestContext,
                mimeType = JXL_MIMETYPE,
                getSize = JxlCoder::getSize,
                decodeSampled = JxlCoder::decodeSampled
            )
        }

        // Build a lazy drawable that decodes one frame at a time.
        // The JxlAnimatedImage is NOT closed here – the drawable retains it
        // for on-demand frame decoding; native memory is freed by finalize().
        val request = requestContext.request
        val drawable = JxlAnimationDrawable(animatedImage).apply {
            isOneShot = request.repeatCount?.let { it == 1 } ?: false
        }

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

        val imageInfo = ImageInfo(
            width = animatedImage.getWidth(),
            height = animatedImage.getHeight(),
            mimeType = JXL_MIMETYPE,
        )
        val resize = requestContext.computeResize(imageInfo.size)
        return ImageData(
            image = scaledDrawable.asImage(),
            imageInfo = imageInfo,
            dataFrom = dataSource.dataFrom,
            resize = resize,
            transformeds = null,
            extras = null,
        )
    }

    override suspend fun getImageInfo(): ImageInfo {
        return dataSource.getImageInfo(
            requestContext = requestContext,
            mimeType = JXL_MIMETYPE,
            getSize = JxlCoder::getSize
        )
    }

}
