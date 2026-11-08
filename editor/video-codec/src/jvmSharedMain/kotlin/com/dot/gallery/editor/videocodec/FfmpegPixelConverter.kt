package com.dot.gallery.editor.videocodec

import com.dot.gallery.editor.core.model.PixelSize
import com.dot.gallery.editor.render.SrgbPixels
import com.dot.gallery.editor.render.video.UnsupportedVideoException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.min
import org.bytedeco.ffmpeg.avutil.AVFrame
import org.bytedeco.ffmpeg.global.avutil.AVCOL_RANGE_JPEG
import org.bytedeco.ffmpeg.global.avutil.AVCOL_RANGE_UNSPECIFIED
import org.bytedeco.ffmpeg.global.avutil.AVCOL_SPC_BT2020_CL
import org.bytedeco.ffmpeg.global.avutil.AVCOL_SPC_BT2020_NCL
import org.bytedeco.ffmpeg.global.avutil.AVCOL_SPC_BT470BG
import org.bytedeco.ffmpeg.global.avutil.AVCOL_SPC_BT709
import org.bytedeco.ffmpeg.global.avutil.AVCOL_SPC_RGB
import org.bytedeco.ffmpeg.global.avutil.AVCOL_SPC_SMPTE170M
import org.bytedeco.ffmpeg.global.avutil.AVCOL_SPC_SMPTE240M
import org.bytedeco.ffmpeg.global.avutil.AVCOL_SPC_UNSPECIFIED
import org.bytedeco.ffmpeg.global.avutil.AV_PIX_FMT_RGBA
import org.bytedeco.ffmpeg.global.avutil.AV_PIX_FMT_RGBA64LE
import org.bytedeco.ffmpeg.global.avutil.AV_PIX_FMT_YUV420P
import org.bytedeco.ffmpeg.global.avutil.av_frame_alloc
import org.bytedeco.ffmpeg.global.avutil.av_frame_free
import org.bytedeco.ffmpeg.global.avutil.av_image_fill_arrays
import org.bytedeco.ffmpeg.global.swscale.SWS_BILINEAR
import org.bytedeco.ffmpeg.global.swscale.SWS_CS_BT2020
import org.bytedeco.ffmpeg.global.swscale.SWS_CS_ITU601
import org.bytedeco.ffmpeg.global.swscale.SWS_CS_ITU709
import org.bytedeco.ffmpeg.global.swscale.SWS_CS_SMPTE240M
import org.bytedeco.ffmpeg.global.swscale.sws_freeContext
import org.bytedeco.ffmpeg.global.swscale.sws_getContext
import org.bytedeco.ffmpeg.global.swscale.sws_getCoefficients
import org.bytedeco.ffmpeg.global.swscale.sws_scale
import org.bytedeco.ffmpeg.global.swscale.sws_setColorspaceDetails
import org.bytedeco.ffmpeg.swscale.SwsContext
import org.bytedeco.javacpp.BytePointer
import org.bytedeco.javacpp.DoublePointer

/**
 * Colour-correct conversions around raw decoded [AVFrame]s. The grabber's own
 * COLOR-mode path runs `sws_scale` without `sws_setColorspaceDetails`, which
 * assumes BT.601 for everything — wrong for HD. This converter owns its
 * [SwsContext] and programs the coefficients from the frame's (or stream's)
 * `AVCOL_SPC`/`AVCOL_RANGE`, falling back to BT.709 for ≥720 lines and BT.601
 * below when unspecified.
 *
 * Frames tagged HDR or wide-gamut pass through [ColourToneMap] on the way to
 * sRGB, per frame, so a tag change mid-stream is handled like any other.
 *
 * Two directions, both bounded copies — no zero-copy claims, nothing native
 * escapes the call:
 * - [toSrgb]: YUV → packed RGBA → opaque ARGB [SrgbPixels], display rotation
 *   applied to the pixels for upright preview;
 * - [toYuv420p]: YUV (or RGB) → contiguous YUV420P [ByteBuffer] at the fixed
 *   [outputColorspace] matrix in MPEG (limited) range, ready for
 *   `FFmpegFrameRecorder.recordImage`.
 *
 * [outputColorspace] is resolved once from the probed stream matrix —
 * known BT.709/BT.470BG/SMPTE170M/SMPTE240M is preserved exactly, anything
 * else falls back to BT.709 for ≥[fallbackHeight] ≥720 lines and BT.601 below
 * — so the exporter tags exactly the matrix the planes were converted with,
 * including RGB inputs.
 *
 * Not thread-safe — confine to the decoder owner thread.
 */
class FfmpegPixelConverter(
    private val streamColorspace: Int,
    private val streamColorRange: Int,
    fallbackHeight: Int
) : AutoCloseable {

    /** The YUV matrix [toYuv420p] writes, as an `AVCOL_SPC_*` constant. */
    val outputColorspace: Int = when (streamColorspace) {
        AVCOL_SPC_BT709 -> AVCOL_SPC_BT709
        AVCOL_SPC_BT470BG -> AVCOL_SPC_BT470BG
        AVCOL_SPC_SMPTE170M -> AVCOL_SPC_SMPTE170M
        AVCOL_SPC_SMPTE240M -> AVCOL_SPC_SMPTE240M
        else ->
            if (fallbackHeight >= 720) AVCOL_SPC_BT709 else AVCOL_SPC_SMPTE170M
    }

    private val outputSwsCs = swsCoefficientsOf(outputColorspace)

    private var rgbaCtx: SwsContext? = null
    private var rgbaKey: Params? = null
    private var rgbaDst: AVFrame? = null
    private var rgbaBuf: BytePointer? = null
    private var rgbaBytes: ByteArray = ByteArray(0)
    private var yuvCtx: SwsContext? = null
    private var yuvKey: Params? = null
    private var yuvDst: AVFrame? = null
    private var yuvBuf: BytePointer? = null
    private var yuvBufBytes = 0
    // The composited-RGBA input path: a reusable wrapper frame plus the
    // packed byte buffer feeding it — no per-call native allocation.
    private var srgbFrame: AVFrame? = null
    private var srgbBuf: BytePointer? = null
    private var srgbBytes: ByteArray = ByteArray(0)
    private var srgbBufBytes = 0
    private var toneCtx: SwsContext? = null
    private var toneKey: Params? = null
    private var toneDst: AVFrame? = null
    private var toneBuf: BytePointer? = null
    private var toneSamples: ShortArray = ShortArray(0)
    private var closed = false

    /** Sentinel [Params.swsCs] value marking RGB input — no matrix applies. */
    private val RGB_SOURCE = -1

    private data class Params(
        val srcW: Int, val srcH: Int, val srcFmt: Int,
        val dstW: Int, val dstH: Int,
        val swsCs: Int, val srcRange: Int
    )

    /** Decoded frame → upright opaque sRGB, longest side bounded by [maxEdge]. */
    fun toSrgb(frame: AVFrame, maxEdge: Int, rotationDegrees: Int): SrgbPixels {
        check(!closed) { "converter is closed" }
        require(maxEdge > 0) { "maxEdge must be positive: $maxEdge" }
        val sw = frame.width()
        val sh = frame.height()
        val scale = min(1f, maxEdge.toFloat() / maxOf(sw, sh).toFloat())
        // RGB output needs no even alignment — preserve aspect at any size.
        val dw = (sw * scale).toInt().coerceIn(1, maxEdge)
        val dh = (sh * scale).toInt().coerceIn(1, maxEdge)
        val cs = swsColorspace(frame.colorspace(), sh)
        val range = if (effectiveRange(frame.color_range()) == AVCOL_RANGE_JPEG) 1 else 0

        val toneMap = ColourToneMap.forTags(frame.color_trc(), frame.color_primaries())
        if (toneMap != null) {
            val mapped = toneMappedArgb(frame, toneMap, dw, dh, cs, range)
            val (pixels, outW, outH) = rotateArgb(mapped, dw, dh, rotationDegrees)
            return SrgbPixels(PixelSize(outW, outH), pixels)
        }

        val key = Params(sw, sh, frame.format(), dw, dh, cs, range)
        var dst = rgbaDst
        var buf = rgbaBuf
        if (key != rgbaKey) {
            rgbaCtx?.let { sws_freeContext(it) }
            rgbaDst?.let { av_frame_free(it) }
            rgbaBuf?.close()
            rgbaCtx = null; rgbaDst = null; rgbaBuf = null
            val ctx = sws_getContext(
                sw, sh, frame.format(), dw, dh, AV_PIX_FMT_RGBA,
                SWS_BILINEAR, null, null, null as DoublePointer?
            ) ?: throw UnsupportedVideoException("cannot create pixel converter")
            try {
                if (cs != RGB_SOURCE) {
                    val rc = sws_setColorspaceDetails(
                        ctx, sws_getCoefficients(cs), range,
                        sws_getCoefficients(cs), 1, 0, 1 shl 16, 1 shl 16
                    )
                    if (rc < 0) {
                        throw IllegalStateException(
                            "sws_setColorspaceDetails failed: $rc"
                        )
                    }
                }
                buf = BytePointer(dw.toLong() * dh * 4)
                dst = av_frame_alloc()
                    ?: throw IllegalStateException("av_frame_alloc failed")
                dst.width(dw)
                dst.height(dh)
                dst.format(AV_PIX_FMT_RGBA)
                // align=1 keeps the destination packed: linesize == dw*4.
                val rc = av_image_fill_arrays(
                    dst.data(), dst.linesize(), buf, AV_PIX_FMT_RGBA, dw, dh, 1
                )
                if (rc < 0) {
                    throw IllegalStateException("av_image_fill_arrays failed: $rc")
                }
            } catch (t: Throwable) {
                dst?.let { av_frame_free(it) }
                buf?.close()
                sws_freeContext(ctx)
                throw t
            }
            rgbaCtx = ctx
            rgbaDst = dst
            rgbaBuf = buf
            rgbaKey = key
        }
        dst = rgbaDst!!
        buf = rgbaBuf!!
        val scaled = sws_scale(
            rgbaCtx, frame.data(), frame.linesize(), 0, sh,
            dst.data(), dst.linesize()
        )
        if (scaled != dh) {
            throw IllegalStateException("sws_scale produced $scaled rows, expected $dh")
        }
        val argb = IntArray(dw * dh)
        // One bulk native read per frame — per-pixel BytePointer.get is an
        // JNI call each and would cost millions of crossings.
        if (rgbaBytes.size != dw * dh * 4) rgbaBytes = ByteArray(dw * dh * 4)
        val bytes = rgbaBytes
        val stride = dst.linesize(0)
        if (stride == dw * 4) {
            buf.position(0).get(bytes, 0, bytes.size)
        } else {
            // Defensive: unexpected padding — bounded per-row bulk reads.
            var row = 0
            while (row < dh) {
                buf.position(row.toLong() * stride)
                    .get(bytes, row * dw * 4, dw * 4)
                row++
            }
        }
        for (i in argb.indices) {
            val o = i * 4
            argb[i] = 0xFF shl 24 or
                ((bytes[o].toInt() and 0xFF) shl 16) or
                ((bytes[o + 1].toInt() and 0xFF) shl 8) or
                (bytes[o + 2].toInt() and 0xFF)
        }
        val (pixels, outW, outH) = rotateArgb(argb, dw, dh, rotationDegrees)
        return SrgbPixels(PixelSize(outW, outH), pixels)
    }

    /**
     * Decoded frame → contiguous YUV420P (Y, U, V planes packed back to back)
     * in MPEG/limited range at the [outputColorspace] matrix — direct feed for
     * `recordImage(..., AV_PIX_FMT_YUV420P, buffer)` with no second swscale
     * inside the recorder. The matrix is explicit even for RGB sources so the
     * encoder tags match the planes, never an implicit 601.
     */
    fun toYuv420p(frame: AVFrame, outW: Int, outH: Int): ByteBuffer {
        check(!closed) { "converter is closed" }
        val sw = frame.width()
        val sh = frame.height()
        val cs = swsColorspace(frame.colorspace(), sh)
        val range = if (effectiveRange(frame.color_range()) == AVCOL_RANGE_JPEG) 1 else 0
        val toneMap = ColourToneMap.forTags(frame.color_trc(), frame.color_primaries())
        if (toneMap != null) {
            return srgbToYuv420p(
                toneMappedArgb(frame, toneMap, outW, outH, cs, range), outW, outH
            )
        }
        val key = Params(sw, sh, frame.format(), outW, outH, cs, range)
        if (key != yuvKey) {
            yuvCtx?.let { sws_freeContext(it) }
            yuvCtx = sws_getContext(
                sw, sh, frame.format(), outW, outH, AV_PIX_FMT_YUV420P,
                SWS_BILINEAR, null, null, null as DoublePointer?
            ) ?: throw UnsupportedVideoException("cannot create pixel converter")
            // RGB input carries no matrix — feed the output coefficients on
            // both sides; swscale only honours the dst side for RGB->YUV.
            val srcCs = if (cs == RGB_SOURCE) outputSwsCs else cs
            val rc = sws_setColorspaceDetails(
                yuvCtx, sws_getCoefficients(srcCs), range,
                sws_getCoefficients(outputSwsCs), 0, 0, 1 shl 16, 1 shl 16
            )
            if (rc < 0) {
                sws_freeContext(yuvCtx)
                yuvCtx = null
                throw IllegalStateException("sws_setColorspaceDetails failed: $rc")
            }
            yuvKey = key
        }
        val needed = outW * outH * 3 / 2
        if (yuvBuf == null || yuvBufBytes < needed) {
            yuvBuf?.close()
            yuvBuf = BytePointer(needed.toLong())
            yuvBufBytes = needed
        }
        // A persistent frame, like the RGBA path: per-call PointerPointer and
        // IntPointer destinations corrupted the native heap within a few dozen
        // frames, which ended the process mid-export.
        val dst = yuvDst
            ?: (av_frame_alloc() ?: throw IllegalStateException("av_frame_alloc failed"))
                .also { yuvDst = it }
        val rc = av_image_fill_arrays(
            dst.data(), dst.linesize(), yuvBuf!!.position(0),
            AV_PIX_FMT_YUV420P, outW, outH, 1
        )
        if (rc < 0) {
            throw IllegalStateException("av_image_fill_arrays failed: $rc")
        }
        val scaled = sws_scale(
            yuvCtx, frame.data(), frame.linesize(), 0, sh, dst.data(), dst.linesize()
        )
        if (scaled != outH) {
            throw IllegalStateException(
                "sws_scale produced $scaled rows, expected $outH"
            )
        }
        return yuvBuf!!.position(0).capacity(needed.toLong()).asBuffer()
    }

    /**
     * A composited sRGB frame (packed `0xAARRGGBB` ints, straight alpha —
     * the shared compositor's output) → contiguous YUV420P at
     * [outputColorspace] in MPEG/limited range, the same contract
     * [toYuv420p] fulfils for decoded frames. Alpha is dropped — the
     * compositor's canvas is opaque by the time it reaches the encoder.
     * The RGBA plane is wrapped in a lightweight [AVFrame] so the existing
     * colour-programmed swscale context converts it: the fixed output
     * matrix is explicit even for RGB input, never an implicit 601.
     */
    fun srgbToYuv420p(argb: IntArray, width: Int, height: Int): ByteBuffer {
        check(!closed) { "converter is closed" }
        require(width > 0 && height > 0) { "empty frame: ${width}x$height" }
        require(argb.size.toLong() == width.toLong() * height) {
            "pixel buffer holds ${argb.size} but ${width}x$height " +
                "needs ${width.toLong() * height}"
        }
        val needed = width * height * 4
        if (srgbBuf == null || srgbBufBytes < needed) {
            srgbBuf?.close()
            srgbBuf = BytePointer(needed.toLong())
            srgbBufBytes = needed
        }
        if (srgbBytes.size < needed) {
            srgbBytes = ByteArray(needed)
        }
        for (i in argb.indices) {
            val o = i * 4
            val c = argb[i]
            srgbBytes[o] = (c ushr 16).toByte()
            srgbBytes[o + 1] = (c ushr 8).toByte()
            srgbBytes[o + 2] = c.toByte()
            srgbBytes[o + 3] = (c ushr 24).toByte()
        }
        srgbBuf!!.position(0).put(srgbBytes, 0, needed)
        var frame = srgbFrame
        if (frame == null) {
            frame = av_frame_alloc()
                ?: throw IllegalStateException("av_frame_alloc failed")
            srgbFrame = frame
        }
        val rc = av_image_fill_arrays(
            frame.data(), frame.linesize(), srgbBuf,
            AV_PIX_FMT_RGBA, width, height, 1
        )
        if (rc < 0) {
            throw IllegalStateException("av_image_fill_arrays failed: $rc")
        }
        frame.width(width)
        frame.height(height)
        frame.format(AV_PIX_FMT_RGBA)
        // RGB source tagging: no matrix applies on input, full range —
        // toYuv420p resolves both through the same RGB branches.
        frame.colorspace(AVCOL_SPC_RGB)
        frame.color_range(AVCOL_RANGE_JPEG)
        return toYuv420p(frame, width, height)
    }

    /**
     * Scales [frame] to [dw]x[dh] as 16-bit R'G'B', keeping the precision a
     * 10-bit source carries, then maps that through [toneMap].
     */
    private fun toneMappedArgb(
        frame: AVFrame,
        toneMap: ColourToneMap,
        dw: Int,
        dh: Int,
        cs: Int,
        range: Int
    ): IntArray {
        val sw = frame.width()
        val sh = frame.height()
        val key = Params(sw, sh, frame.format(), dw, dh, cs, range)
        if (key != toneKey) {
            toneCtx?.let { sws_freeContext(it) }
            toneDst?.let { av_frame_free(it) }
            toneBuf?.close()
            toneCtx = null; toneDst = null; toneBuf = null; toneKey = null
            val ctx = sws_getContext(
                sw, sh, frame.format(), dw, dh, AV_PIX_FMT_RGBA64LE,
                SWS_BILINEAR, null, null, null as DoublePointer?
            ) ?: throw UnsupportedVideoException("cannot create pixel converter")
            toneCtx = ctx
            if (cs != RGB_SOURCE) {
                val rc = sws_setColorspaceDetails(
                    ctx, sws_getCoefficients(cs), range,
                    sws_getCoefficients(cs), 1, 0, 1 shl 16, 1 shl 16
                )
                if (rc < 0) {
                    throw IllegalStateException("sws_setColorspaceDetails failed: $rc")
                }
            }
            val buf = BytePointer(dw.toLong() * dh * 8)
            toneBuf = buf
            val dst = av_frame_alloc()
                ?: throw IllegalStateException("av_frame_alloc failed")
            toneDst = dst
            dst.width(dw)
            dst.height(dh)
            dst.format(AV_PIX_FMT_RGBA64LE)
            val rc = av_image_fill_arrays(
                dst.data(), dst.linesize(), buf, AV_PIX_FMT_RGBA64LE, dw, dh, 1
            )
            if (rc < 0) {
                throw IllegalStateException("av_image_fill_arrays failed: $rc")
            }
            toneKey = key
        }
        val dst = toneDst!!
        val scaled = sws_scale(
            toneCtx, frame.data(), frame.linesize(), 0, sh,
            dst.data(), dst.linesize()
        )
        if (scaled != dh) {
            throw IllegalStateException("sws_scale produced $scaled rows, expected $dh")
        }
        val count = dw * dh * 4
        if (toneSamples.size != count) toneSamples = ShortArray(count)
        toneBuf!!.position(0).capacity(count * 2L).asByteBuffer()
            .order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().get(toneSamples)
        val argb = IntArray(dw * dh)
        toneMap.apply(toneSamples, argb)
        return argb
    }

    /** The frame's own range wins; the stream tag is only a fallback. */
    private fun effectiveRange(range: Int): Int =
        if (range == AVCOL_RANGE_UNSPECIFIED) streamColorRange else range

    /** Sentinel [RGB_SOURCE] marks RGB sources — no colour matrix applies. */
    private fun swsColorspace(frameCs: Int, height: Int): Int {
        val cs = if (frameCs == AVCOL_SPC_UNSPECIFIED) streamColorspace else frameCs
        return when (cs) {
            AVCOL_SPC_RGB -> RGB_SOURCE
            else -> swsCoefficientsOf(cs, height)
        }
    }

    private fun rotateArgb(
        src: IntArray, w: Int, h: Int, degrees: Int
    ): Triple<IntArray, Int, Int> = when (((degrees % 360) + 360) % 360) {
        90 -> {
            val dst = IntArray(w * h)
            for (y in 0 until h) for (x in 0 until w) {
                // CCW 90: dst(x'=y, y'=w-1-x) over dst dims h×w
                dst[y + (w - 1 - x) * h] = src[x + y * w]
            }
            Triple(dst, h, w)
        }
        180 -> {
            val dst = IntArray(w * h)
            for (y in 0 until h) for (x in 0 until w) {
                dst[(w - 1 - x) + (h - 1 - y) * w] = src[x + y * w]
            }
            Triple(dst, w, h)
        }
        270 -> {
            val dst = IntArray(w * h)
            for (y in 0 until h) for (x in 0 until w) {
                // 270 CCW (90 CW): dst(x'=h-1-y, y'=x) over dst dims h×w
                dst[(h - 1 - y) + x * h] = src[x + y * w]
            }
            Triple(dst, h, w)
        }
        else -> Triple(src, w, h)
    }

    /** Idempotent: frees the live contexts and buffers; safe to repeat. */
    override fun close() {
        if (closed) return
        closed = true
        rgbaCtx?.let { sws_freeContext(it) }
        rgbaCtx = null
        rgbaDst?.let { av_frame_free(it) }
        rgbaDst = null
        rgbaBuf?.close()
        rgbaBuf = null
        yuvCtx?.let { sws_freeContext(it) }
        yuvCtx = null
        yuvDst?.let { av_frame_free(it) }
        yuvDst = null
        yuvBuf?.close()
        yuvBuf = null
        srgbFrame?.let { av_frame_free(it) }
        srgbFrame = null
        srgbBuf?.close()
        srgbBuf = null
        toneCtx?.let { sws_freeContext(it) }
        toneCtx = null
        toneDst?.let { av_frame_free(it) }
        toneDst = null
        toneBuf?.close()
        toneBuf = null
    }

    companion object {
        /** `AVCOL_SPC_*` → swscale coefficient constant, [height]-fallback for unspecified. */
        private fun swsCoefficientsOf(cs: Int, height: Int = -1): Int = when (cs) {
            AVCOL_SPC_BT709 -> SWS_CS_ITU709
            AVCOL_SPC_BT470BG, AVCOL_SPC_SMPTE170M -> SWS_CS_ITU601
            AVCOL_SPC_SMPTE240M -> SWS_CS_SMPTE240M
            AVCOL_SPC_BT2020_NCL, AVCOL_SPC_BT2020_CL -> SWS_CS_BT2020
            // Unspecified, or a matrix swscale has no table for: the size
            // convention is the closest honest guess.
            else -> if (height >= 720) SWS_CS_ITU709 else SWS_CS_ITU601
        }
    }
}
