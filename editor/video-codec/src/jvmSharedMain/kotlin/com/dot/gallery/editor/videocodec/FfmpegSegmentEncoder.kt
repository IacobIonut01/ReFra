package com.dot.gallery.editor.videocodec

import com.dot.gallery.editor.render.video.UnsupportedVideoException
import java.nio.ByteBuffer
import org.bytedeco.ffmpeg.avcodec.AVCodecContext
import org.bytedeco.ffmpeg.avcodec.AVPacket
import org.bytedeco.ffmpeg.avutil.AVFrame
import org.bytedeco.ffmpeg.avutil.AVRational
import org.bytedeco.ffmpeg.global.avcodec.av_packet_free
import org.bytedeco.ffmpeg.global.avcodec.AV_CODEC_FLAG_GLOBAL_HEADER
import org.bytedeco.ffmpeg.global.avcodec.AV_CODEC_ID_H264
import org.bytedeco.ffmpeg.global.avcodec.AV_CODEC_ID_HEVC
import org.bytedeco.ffmpeg.global.avcodec.av_packet_unref
import org.bytedeco.ffmpeg.global.avcodec.avcodec_alloc_context3
import org.bytedeco.ffmpeg.global.avcodec.avcodec_find_encoder_by_name
import org.bytedeco.ffmpeg.global.avcodec.avcodec_free_context
import org.bytedeco.ffmpeg.global.avcodec.avcodec_open2
import org.bytedeco.ffmpeg.global.avcodec.avcodec_receive_packet
import org.bytedeco.ffmpeg.global.avcodec.avcodec_send_frame
import org.bytedeco.ffmpeg.global.avutil.AVMEDIA_TYPE_VIDEO
import org.bytedeco.ffmpeg.global.avutil.AV_PIX_FMT_YUV420P
import org.bytedeco.ffmpeg.global.avutil.av_frame_alloc
import org.bytedeco.ffmpeg.global.avutil.av_frame_free
import org.bytedeco.ffmpeg.global.avutil.av_frame_get_buffer
import org.bytedeco.ffmpeg.global.avutil.av_frame_make_writable
import org.bytedeco.javacpp.BytePointer

/**
 * Raw `avcodec` encoder for the remuxer's boundary spans — the short run of
 * frames between a clip's cut point and the next keyframe, re-encoded so the
 * rest of the clip can be packet-copied untouched. Unlike the
 * `FFmpegFrameRecorder` path this produces bare packets with caller-assigned
 * timestamps: they are muxed into the same stream as copied source packets,
 * so the encoded span must carry the source codec and the output stream's
 * time base.
 *
 * Encoding is B-frame-free ([AVCodecContext.max_b_frames] = 0), so emitted
 * packets have `dts == pts` and stay monotonic against the copied tail.
 * `GLOBAL_HEADER` is set purely so the encoder's parameter sets land in
 * `extradata` for [parameterSets] — the muxed stream keeps the source's
 * `codecpar` and parameter sets are injected in-band at each span boundary
 * ([FfmpegRemuxer] prepends them to each span's first packet).
 *
 * Extradata parsing knows `avcC` (H.264) and `hvcC` (HEVC) layouts only; any
 * other extradata shape yields no parameter sets and the caller decides
 * whether splicing without them is safe (it is not — refuse instead).
 */
internal class FfmpegSegmentEncoder(
    codecName: String,
    width: Int,
    height: Int,
    frameRateNum: Int,
    frameRateDen: Int,
    bitRate: Long,
    private val packetTimeBase: AVRational
) : AutoCloseable {

    private val frame: AVFrame
    private val packet: AVPacket
    private val ctx: AVCodecContext

    /** Codec of the emitted packets — must equal the copied stream's. */
    val codecId: Int

    /**
     * Parameter-set NAL units (SPS/PPS for H.264, VPS/SPS/PPS for HEVC)
     * parsed from the encoder's `extradata`, for in-band injection at span
     * boundaries. Empty when the extradata layout is unrecognised.
     */
    val parameterSets: List<ByteArray>

    init {
        FfmpegSupport.require()
        val codec = avcodec_find_encoder_by_name(codecName)
            ?: throw UnsupportedVideoException("encoder $codecName is unavailable")
        codecId = codec.id()
        ctx = avcodec_alloc_context3(codec)
            ?: throw IllegalStateException("avcodec_alloc_context3 failed")
        ctx.codec_type(AVMEDIA_TYPE_VIDEO)
        ctx.width(width)
        ctx.height(height)
        ctx.pix_fmt(AV_PIX_FMT_YUV420P)
        ctx.time_base(packetTimeBase)
        if (frameRateNum > 0 && frameRateDen > 0) {
            ctx.framerate(AVRational().num(frameRateNum).den(frameRateDen))
        }
        ctx.bit_rate(bitRate)
        // The span is one independent run: a huge GOP keeps the encoder from
        // scheduling a second keyframe inside it — the only IDR is the first
        // frame, which is what the splice needs.
        ctx.gop_size(Int.MAX_VALUE)
        ctx.max_b_frames(0)
        ctx.flags(ctx.flags() or AV_CODEC_FLAG_GLOBAL_HEADER)
        val opened = avcodec_open2(ctx, codec, null as org.bytedeco.ffmpeg.avutil.AVDictionary?)
        if (opened < 0) {
            avcodec_free_context(ctx)
            throw UnsupportedVideoException(
                "encoder $codecName refused to open: $opened"
            )
        }
        frame = av_frame_alloc() ?: run {
            avcodec_free_context(ctx)
            throw IllegalStateException("av_frame_alloc failed")
        }
        frame.width(width)
        frame.height(height)
        frame.format(AV_PIX_FMT_YUV420P)
        if (av_frame_get_buffer(frame, 32) < 0) {
            av_frame_free(frame)
            avcodec_free_context(ctx)
            throw IllegalStateException("av_frame_get_buffer failed")
        }
        packet = org.bytedeco.ffmpeg.global.avcodec.av_packet_alloc()
            ?: run {
                av_frame_free(frame)
                avcodec_free_context(ctx)
                throw IllegalStateException("av_packet_alloc failed")
            }
        parameterSets = readExtradata()?.let { parseParameterSets(codecId, it) }
            .orEmpty()
    }

    private fun readExtradata(): ByteArray? {
        val size = ctx.extradata_size()
        val data = ctx.extradata() ?: return null
        if (size <= 0) return null
        val out = ByteArray(size)
        data.position(0).capacity(size.toLong()).get(out)
        return out
    }

    /**
     * Encodes one tightly packed YUV420P frame ([yuv] is Y `w*h`, then U and
     * V `w*h/4` each — the [FfmpegPixelConverter.toYuv420p] layout) stamped
     * at [ptsTb] in [packetTimeBase] units. Every produced packet reaches
     * [sink] in decode order before this call returns.
     */
    fun encode(yuv: ByteBuffer, ptsTb: Long, sink: (AVPacket) -> Unit) {
        if (av_frame_make_writable(frame) < 0) {
            throw IllegalStateException("av_frame_make_writable failed")
        }
        fillPlane(yuv, frame.data(0), frame.linesize(0), width(), height())
        fillPlane(yuv, frame.data(1), frame.linesize(1), width() / 2, height() / 2)
        fillPlane(yuv, frame.data(2), frame.linesize(2), width() / 2, height() / 2)
        frame.pts(ptsTb)
        val sent = avcodec_send_frame(ctx, frame)
        if (sent < 0) {
            throw IllegalStateException("avcodec_send_frame failed: $sent")
        }
        drain(sink)
    }

    /** Flushes the encoder; remaining packets reach [sink]. */
    fun flush(sink: (AVPacket) -> Unit) {
        val sent = avcodec_send_frame(ctx, null as AVFrame?)
        if (sent < 0) {
            throw IllegalStateException("avcodec_send_frame(flush) failed: $sent")
        }
        drain(sink)
    }

    private fun drain(sink: (AVPacket) -> Unit) {
        while (true) {
            val received = avcodec_receive_packet(ctx, packet)
            if (received < 0) {
                if (received == EAGAIN || received == AVERROR_EOF_VALUE) return
                throw IllegalStateException(
                    "avcodec_receive_packet failed: $received"
                )
            }
            try {
                sink(packet)
            } finally {
                av_packet_unref(packet)
            }
        }
    }

    private fun fillPlane(
        src: ByteBuffer, dst: BytePointer, linesize: Int, w: Int, h: Int
    ) {
        var row = 0
        while (row < h) {
            val dstRow = BytePointer(dst).position(row.toLong() * linesize)
            for (x in 0 until w) {
                dstRow.put(x.toLong(), src.get())
            }
            row++
        }
    }

    private fun width() = frame.width()
    private fun height() = frame.height()

    override fun close() {
        av_packet_free(packet)
        av_frame_free(frame)
        avcodec_free_context(ctx)
    }

    companion object {
        // FFmpeg returns AVERROR(EAGAIN) = -11 and AVERROR_EOF on all
        // platforms the packaged runtime targets.
        private const val EAGAIN = -11
        private const val AVERROR_EOF_VALUE = -0x20464F46 // -('EOF ' as tag)

        /**
         * The encoder name that produces packets for [codecId], or null when
         * no packaged encoder matches — H.264 always has `libopenh264`; HEVC
         * depends on an optional encoder (`libx265` is not bundled, platform
         * `hevc_mediacodec` may exist on Android).
         */
        fun encoderNameFor(codecId: Int): String? = when (codecId) {
            AV_CODEC_ID_H264 -> "libopenh264"
                .takeIf { avcodec_find_encoder_by_name(it) != null }
            AV_CODEC_ID_HEVC ->
                listOf("libx265", "hevc_mediacodec").firstOrNull {
                    avcodec_find_encoder_by_name(it) != null
                }
            else -> null
        }

        /**
         * Parameter-set NAL units inside a codec `extradata` blob: `avcC`
         * layout for H.264 (configurationVersion 1, 4-byte lengths), `hvcC`
         * array layout for HEVC. Any other shape returns empty — the caller
         * cannot inject what it cannot parse.
         */
        fun parseParameterSets(codecId: Int, extradata: ByteArray): List<ByteArray> =
            when (codecId) {
                AV_CODEC_ID_H264 -> parseAvcC(extradata)
                AV_CODEC_ID_HEVC -> parseHvcC(extradata)
                else -> emptyList()
            }

        private fun u16(data: ByteArray, off: Int): Int =
            ((data[off].toInt() and 0xFF) shl 8) or (data[off + 1].toInt() and 0xFF)

        private fun parseAvcC(d: ByteArray): List<ByteArray> {
            // AVCDecoderConfigurationRecord: byte 0 is the version, bytes 5+
            // carry numSPS, [len16][sps]* then numPPS, [len16][pps]*.
            if (d.size < 8 || d[0].toInt() != 1) return emptyList()
            val out = ArrayList<ByteArray>(4)
            var off = 5
            val spsCount = d[off].toInt() and 0x1F; off++
            repeat(spsCount) {
                if (off + 2 > d.size) return emptyList()
                val len = u16(d, off); off += 2
                if (off + len > d.size) return emptyList()
                out += d.copyOfRange(off, off + len); off += len
            }
            if (off >= d.size) return emptyList()
            val ppsCount = d[off].toInt() and 0xFF; off++
            repeat(ppsCount) {
                if (off + 2 > d.size) return emptyList()
                val len = u16(d, off); off += 2
                if (off + len > d.size) return emptyList()
                out += d.copyOfRange(off, off + len); off += len
            }
            return out
        }

        private fun parseHvcC(d: ByteArray): List<ByteArray> {
            // HEVCDecoderConfigurationRecord: byte 22 is numOfArrays; each
            // array is [completeness<<7 | nalType][numNalus16][len16][nal]*.
            if (d.size < 23 || d[0].toInt() != 1) return emptyList()
            val out = ArrayList<ByteArray>(6)
            var off = 22
            val arrays = d[off].toInt() and 0xFF; off++
            repeat(arrays) {
                if (off + 3 > d.size) return emptyList()
                off++ // array header (completeness + nal_unit_type)
                val nalus = u16(d, off); off += 2
                repeat(nalus) {
                    if (off + 2 > d.size) return emptyList()
                    val len = u16(d, off); off += 2
                    if (off + len > d.size) return emptyList()
                    out += d.copyOfRange(off, off + len); off += len
                }
            }
            return out
        }
    }
}
