@file:androidx.annotation.OptIn(markerClass = [androidx.media3.common.util.UnstableApi::class])

package com.dot.gallery.feature.viewer.components.video

import android.content.Context
import android.os.Handler
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.Renderer
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector
import androidx.media3.exoplayer.video.MediaCodecVideoRenderer
import androidx.media3.exoplayer.video.VideoRendererEventListener

/**
 * Renderers factory for viewer players. The viewer swaps the video output while a codec is
 * running (SurfaceView/TextureView switches and the #967 inset rebind). The emulator's goldfish
 * decoders accept `MediaCodec.setOutputSurface` but then fail every following
 * `queueInputBuffer` with "MediaCodec already in error", so for those codecs the video renderer
 * releases and re-creates the codec on a surface change instead.
 */
class ViewerRenderersFactory(context: Context) : DefaultRenderersFactory(context) {

    override fun buildVideoRenderers(
        context: Context,
        extensionRendererMode: Int,
        mediaCodecSelector: MediaCodecSelector,
        enableDecoderFallback: Boolean,
        eventHandler: Handler,
        eventListener: VideoRendererEventListener,
        allowedVideoJoiningTimeMs: Long,
        out: ArrayList<Renderer>
    ) {
        super.buildVideoRenderers(
            context,
            extensionRendererMode,
            mediaCodecSelector,
            enableDecoderFallback,
            eventHandler,
            eventListener,
            allowedVideoJoiningTimeMs,
            out
        )
        val index = out.indexOfFirst { it is MediaCodecVideoRenderer }
        if (index < 0) return
        val builder = MediaCodecVideoRenderer.Builder(context)
            .setCodecAdapterFactory(codecAdapterFactory)
            .setMediaCodecSelector(mediaCodecSelector)
            .setAllowedJoiningTimeMs(allowedVideoJoiningTimeMs)
            .setEnableDecoderFallback(enableDecoderFallback)
            .setEventHandler(eventHandler)
            .setEventListener(eventListener)
            .setMaxDroppedFramesToNotify(MAX_DROPPED_VIDEO_FRAME_COUNT_TO_NOTIFY)
        out[index] = SurfaceSafeVideoRenderer(builder)
    }

    private class SurfaceSafeVideoRenderer(builder: Builder) : MediaCodecVideoRenderer(builder) {
        override fun codecNeedsSetOutputSurfaceWorkaround(name: String): Boolean =
            codecMishandlesSetOutputSurface(name) || super.codecNeedsSetOutputSurfaceWorkaround(name)
    }
}

/** Codecs known to enter an error state after `MediaCodec.setOutputSurface` while running. */
fun codecMishandlesSetOutputSurface(codecName: String): Boolean =
    codecName.contains(".goldfish.", ignoreCase = true)
