package com.dot.gallery.editor.videocodec

import com.dot.gallery.editor.core.time.EditorTime
import com.dot.gallery.editor.core.time.TimeRange
import com.dot.gallery.editor.render.video.UnsupportedVideoException
import java.io.File
import java.io.IOException
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.ensureActive
import org.bytedeco.ffmpeg.avcodec.AVCodec
import org.bytedeco.ffmpeg.avcodec.AVCodecParameters
import org.bytedeco.ffmpeg.avcodec.AVPacket
import org.bytedeco.ffmpeg.avcodec.AVPacketSideData
import org.bytedeco.ffmpeg.avformat.AVFormatContext
import org.bytedeco.ffmpeg.avformat.AVIOContext
import org.bytedeco.ffmpeg.avformat.AVInputFormat
import org.bytedeco.ffmpeg.avformat.AVOutputFormat
import org.bytedeco.ffmpeg.avformat.AVStream
import org.bytedeco.ffmpeg.avutil.AVDictionary
import org.bytedeco.ffmpeg.avutil.AVRational
import org.bytedeco.ffmpeg.global.avcodec.AV_CODEC_ID_AAC
import org.bytedeco.ffmpeg.global.avcodec.AV_CODEC_ID_H264
import org.bytedeco.ffmpeg.global.avcodec.AV_CODEC_ID_HEVC
import org.bytedeco.ffmpeg.global.avcodec.AV_PKT_DATA_DISPLAYMATRIX
import org.bytedeco.ffmpeg.global.avcodec.AV_PKT_FLAG_KEY
import org.bytedeco.ffmpeg.global.avcodec.av_new_packet
import org.bytedeco.ffmpeg.global.avcodec.av_packet_alloc
import org.bytedeco.ffmpeg.global.avcodec.av_packet_free
import org.bytedeco.ffmpeg.global.avcodec.av_packet_side_data_get
import org.bytedeco.ffmpeg.global.avcodec.av_packet_side_data_new
import org.bytedeco.ffmpeg.global.avcodec.av_packet_unref
import org.bytedeco.ffmpeg.global.avcodec.avcodec_parameters_copy
import org.bytedeco.ffmpeg.global.avformat.AVIO_FLAG_WRITE
import org.bytedeco.ffmpeg.global.avformat.AVSEEK_FLAG_BACKWARD
import org.bytedeco.ffmpeg.global.avformat.av_interleaved_write_frame
import org.bytedeco.ffmpeg.global.avformat.av_read_frame
import org.bytedeco.ffmpeg.global.avformat.av_seek_frame
import org.bytedeco.ffmpeg.global.avformat.av_write_trailer
import org.bytedeco.ffmpeg.global.avformat.avformat_alloc_context
import org.bytedeco.ffmpeg.global.avformat.avformat_alloc_output_context2
import org.bytedeco.ffmpeg.global.avformat.avformat_close_input
import org.bytedeco.ffmpeg.global.avformat.avformat_find_stream_info
import org.bytedeco.ffmpeg.global.avformat.avformat_free_context
import org.bytedeco.ffmpeg.global.avformat.avformat_new_stream
import org.bytedeco.ffmpeg.global.avformat.avformat_open_input
import org.bytedeco.ffmpeg.global.avformat.avformat_transfer_internal_stream_timing_info
import org.bytedeco.ffmpeg.global.avformat.avformat_write_header
import org.bytedeco.ffmpeg.global.avformat.avio_close
import org.bytedeco.ffmpeg.global.avformat.avio_open
import org.bytedeco.ffmpeg.global.avutil.AV_NOPTS_VALUE
import org.bytedeco.ffmpeg.global.avutil.AV_TIME_BASE
import org.bytedeco.ffmpeg.global.avutil.av_dict_free
import org.bytedeco.ffmpeg.global.avutil.av_dict_set
import org.bytedeco.ffmpeg.global.avutil.av_display_rotation_set
import org.bytedeco.ffmpeg.global.avutil.av_rescale_q
import org.bytedeco.javacpp.IntPointer
import org.bytedeco.javacpp.PointerPointer

/**
 * Packet-level exporter — the fast path for single-source cut sequences
 * ([SingleSourcePlan]-eligible timelines: trims and cuts, no visual edits).
 * Instead of decoding and re-encoding every frame it copies the compressed
 * source packets, re-encoding only the span between an unaligned cut point
 * and the next keyframe — the "keep the unchanged parts" smart-render
 * contract:
 *
 * - each clip splits into a head span `[start, firstKeyframe >= start)`
 *   decoded and re-encoded through [FfmpegSegmentEncoder] (a GOP at most),
 *   and a copy span `[firstKeyframe, end)` muxed packet-for-packet — lossless
 *   and I/O-bound;
 * - a keyframe-aligned start skips the head span entirely, so a cut whose
 *   boundaries already sit on GOP edges is a pure remux;
 * - audio (AAC only) is copied with per-packet granularity — every AAC
 *   packet is self-contained.
 *
 * Splice rules that keep the muxed stream legal:
 * - the output stream carries the source `codecpar` (codec, avcC/hvcC
 *   extradata). The first packet of every span gets that span codec's
 *   parameter sets prepended in-band — after an encoded span the decoder
 *   must not reach a copied IDR while still holding the encoder's SPS/PPS;
 * - copied timestamps are rebased onto the packed output timeline. B-frame
 *   reorder can drop a copied DTS below the previous packet's: the whole
 *   remaining stream (pts and dts alike) shifts forward by the deficit, so
 *   the only artifact is the head span's last frame held a couple of frames
 *   longer. The accumulated shift is reported as [Outcome.slackTicks] so the
 *   caller's duration check tolerates it;
 * - a clip's first keyframe is found by a backward seek; head frames in a
 *   B-frame GOP can trail the keyframe in decode order, so the scan keeps
 *   collecting `pts < keyframe` packets until the *following* keyframe.
 *
 * Everything in [export] is thrown up to the caller on any mismatch — the
 * exporter's contract is to catch and fall back to a full re-encode, so a
 * remux limitation never produces a corrupt file silently.
 */
internal object FfmpegRemuxer {

    /** What the caller needs to verify a remuxed file. */
    class Outcome(
        /** Codec actually written — the source's, not the re-encode H.264. */
        val videoCodecId: Int,
        /** Extra output duration from splice timestamp shifts, in ticks. */
        val slackTicks: Long
    )

    /**
     * Whether [probe]'s streams can ride the packet path into an MP4:
     * H.264 or HEVC video (the codecs an MP4 muxer accepts verbatim here),
     * and either no audio or AAC (self-contained packets). Anything else
     * must take the decode+encode path.
     */
    fun supports(probe: FfmpegProbeResult): Boolean =
        (probe.videoCodecId == AV_CODEC_ID_H264 ||
            probe.videoCodecId == AV_CODEC_ID_HEVC) &&
            probe.videoStreamIndex >= 0 &&
            (probe.audioStreamIndex < 0 || probe.audioCodecId == AV_CODEC_ID_AAC)

    /**
     * Writes [clipRanges] (source-domain media ranges, packed in order) of
     * [source] into [outputFile] as MP4. Throws [IOException] /
     * [UnsupportedVideoException] on any layout this path cannot honour —
     * the caller retries with a full re-encode.
     */
    suspend fun export(
        source: ResolvedVideoSource,
        probe: FfmpegProbeResult,
        clipRanges: List<TimeRange>,
        outputFile: File,
        onProgress: (Float) -> Unit
    ): Outcome {
        FfmpegSupport.require()
        val ic = avformat_alloc_context()
            ?: throw IOException("avformat_alloc_context failed")
        try {
            var rc = avformat_open_input(
                ic, source.path, null as AVInputFormat?, null as AVDictionary?
            )
            if (rc < 0) throw IOException("avformat_open_input failed: $rc")
            rc = avformat_find_stream_info(ic, null as AVDictionary?)
            if (rc < 0) throw IOException("avformat_find_stream_info failed: $rc")

            val vIn = ic.streams(probe.videoStreamIndex)
                ?: throw IOException("missing video stream ${probe.videoStreamIndex}")
            val aIn = if (probe.audioStreamIndex >= 0) {
                ic.streams(probe.audioStreamIndex)
            } else null
            val startUs = ic.start_time()
                .takeIf { it != AV_NOPTS_VALUE && it > 0L } ?: 0L

            val oc = AVFormatContext(null)
            rc = avformat_alloc_output_context2(
                oc, null as AVOutputFormat?, "mp4", outputFile.absolutePath
            )
            if (rc < 0) throw IOException("avformat_alloc_output_context2: $rc")
            try {
                val vOut = addStream(oc, vIn)
                val aOut = aIn?.let { addStream(oc, it) }
                ensureDisplayRotation(
                    vOut.codecpar(), probe.info.asset.rotationDegrees
                )
                val pb = AVIOContext(null)
                rc = avio_open(pb, outputFile.absolutePath, AVIO_FLAG_WRITE)
                if (rc < 0) throw IOException("avio_open failed: $rc")
                oc.pb(pb)
                val opts = AVDictionary(null)
                av_dict_set(opts, "movflags", "+faststart+negative_cts_offsets", 0)
                rc = avformat_write_header(oc, opts)
                av_dict_free(opts)
                if (rc < 0) throw IOException("avformat_write_header failed: $rc")

                val video = OutStream(vIn, vOut)
                val audio = aIn?.let { inSt -> OutStream(inSt, aOut!!) }
                val muxer = Muxer(
                    source, probe, ic, oc, video, audio, startUs
                )
                try {
                    clipRanges.forEachIndexed { index, range ->
                        coroutineContext.ensureActive()
                        muxer.writeClip(range)
                        onProgress((index + 1).toFloat() / clipRanges.size * 0.9f)
                    }
                    coroutineContext.ensureActive()
                    rc = av_write_trailer(oc)
                    if (rc < 0) {
                        throw IOException("av_write_trailer failed: $rc")
                    }
                    return muxer.outcome()
                } finally {
                    muxer.close()
                }
            } finally {
                muxerClose(oc)
            }
        } finally {
            avformat_close_input(ic)
        }
    }

    /** One output stream's timestamp bookkeeping. */
    private class OutStream(inSt: AVStream, val out: AVStream) {
        val inTb: AVRational = inSt.time_base()
        val outTb: AVRational = out.time_base()
        val index: Int = out.index()
        var base = 0L
        var lastDts = -1L
        var slack = 0L
    }

    private class Muxer(
        private val source: ResolvedVideoSource,
        private val probe: FfmpegProbeResult,
        private val ic: AVFormatContext,
        private val oc: AVFormatContext,
        private val video: OutStream,
        private val audio: OutStream?,
        private val containerStartUs: Long
    ) {
        private val vIndex = probe.videoStreamIndex
        private val aIndex = probe.audioStreamIndex
        private val pkt = av_packet_alloc()
            ?: throw IllegalStateException("av_packet_alloc failed")

        // Decode/encode machinery exists only when an unaligned cut appears.
        private var converter: FfmpegPixelConverter? = null
        private var reader: FfmpegVideoReader? = null
        private var encoder: FfmpegSegmentEncoder? = null

        /** Copied-span decoder needs the source's param sets back in-band. */
        private var sourceParamSets: List<ByteArray>? = null
        private var sourceParamSetsRead = false
        private var afterEncodedSpan = false

        fun outcome(): Outcome {
            val slackUs = tbToUs(video.slack, video.outTb)
            return Outcome(probe.videoCodecId, EditorTime.fromMicroseconds(slackUs).ticks)
        }

        suspend fun writeClip(range: TimeRange) {
            val startSrcUs = range.start.toMicrosecondsFloor()
            val endSrcUs = range.endExclusive.toMicrosecondsFloor()
            val vSlackBefore = video.slack
            val aSlackBefore = audio?.slack ?: 0L

            // Pass 1 — seek to the last keyframe at-or-before the cut, then
            // scan forward: first key packet >= start is the copy point,
            // packets in [start, key) are the re-encode span. B-frames
            // displayed before the key decode after it, so the scan only
            // ends at the *next* keyframe (or a sanity bound).
            var headKfSrcUs: Long? = null
            val headFrames = ArrayList<Long>(64)
            val startTb = usToTb(startSrcUs + containerStartUs, video.inTb)
            if (av_seek_frame(ic, vIndex, startTb, AVSEEK_FLAG_BACKWARD) < 0) {
                throw IOException("av_seek_frame to clip start failed")
            }
            while (av_read_frame(ic, pkt) >= 0) {
                coroutineContext.ensureActive()
                if (pkt.stream_index() != vIndex) {
                    av_packet_unref(pkt); continue
                }
                val pUs = packetSrcUs(pkt, video.inTb)
                if (pUs < startSrcUs) {
                    av_packet_unref(pkt); continue
                }
                if (pUs >= endSrcUs) {
                    val key = isKey(pkt)
                    av_packet_unref(pkt)
                    if (key || pUs > endSrcUs + SCAN_BOUND_US) break
                    continue
                }
                val kfUs = headKfSrcUs
                if (kfUs == null) {
                    if (isKey(pkt)) headKfSrcUs = pUs else headFrames += pUs
                } else if (pUs < kfUs) {
                    headFrames += pUs
                } else if (isKey(pkt) || pUs > kfUs + SCAN_BOUND_US) {
                    av_packet_unref(pkt)
                    break // next GOP — the head span is complete
                }
                av_packet_unref(pkt)
            }
            headFrames.sort()

            // Pass 2 — the head span, decoded and re-encoded. A clip can be
            // entirely inside one GOP (no keyframe before its end): then the
            // whole clip lands here.
            if (headFrames.isNotEmpty()) {
                encodeHead(headFrames, startSrcUs)
            }

            // Pass 3 — packet copy of [headKf, end).
            if (headKfSrcUs != null && headKfSrcUs < endSrcUs) {
                copyVideoSpan(headKfSrcUs, startSrcUs, endSrcUs)
            }

            // Pass 4 — audio, per-packet boundaries.
            audio?.let { copyAudioSpan(it, startSrcUs, endSrcUs) }

            video.base += usToTb(endSrcUs - startSrcUs, video.outTb) +
                (video.slack - vSlackBefore)
            audio?.let {
                it.base += usToTb(endSrcUs - startSrcUs, it.outTb) +
                    (it.slack - aSlackBefore)
            }
        }

        /** Decodes and re-encodes the [frameUs] source frames of one span. */
        private suspend fun encodeHead(frameUs: List<Long>, startSrcUs: Long) {
            val enc = ensureEncoder()
            val conv = converter ?: FfmpegPixelConverter(
                probe.colorspace, probe.colorRange, probe.rawSize.height
            ).also { converter = it }
            val rdr = reader ?: FfmpegVideoReader(
                source.path, conv, 0, probe.videoStreamIndex,
                HardwareDecode.select(probe)
            ).also { reader = it }
            var inject = enc.parameterSets
            for (pUs in frameUs) {
                coroutineContext.ensureActive()
                val raw = rdr.rawFrameAt(EditorTime.fromMicroseconds(pUs))
                val yuv = conv.toYuv420p(
                    raw, probe.rawSize.width, probe.rawSize.height
                )
                val relTb = usToTb(pUs - startSrcUs, video.outTb)
                val nals = inject
                inject = emptyList()
                enc.encode(yuv, relTb) { out ->
                    writePacket(
                        video, out,
                        relPts = out.pts(), relDts = out.dts(),
                        relDuration = out.duration(), injectNals = nals
                    )
                }
            }
            enc.flush { out ->
                writePacket(
                    video, out,
                    relPts = out.pts(), relDts = out.dts(),
                    relDuration = out.duration(), injectNals = emptyList()
                )
            }
            afterEncodedSpan = true
        }

        /** Packet-copies video frames with pts in [kfUs, endUs). */
        private suspend fun copyVideoSpan(
            kfUs: Long, startSrcUs: Long, endSrcUs: Long
        ) {
            val clipStartInTb = usToTb(
                startSrcUs + containerStartUs, video.inTb
            )
            seekTb(usToTb(kfUs + containerStartUs, video.inTb), vIndex)
            var firstCopied = true
            while (av_read_frame(ic, pkt) >= 0) {
                coroutineContext.ensureActive()
                if (pkt.stream_index() != vIndex) {
                    av_packet_unref(pkt); continue
                }
                val pUs = packetSrcUs(pkt, video.inTb)
                if (pUs >= endSrcUs) {
                    val key = isKey(pkt)
                    av_packet_unref(pkt)
                    if (key || pUs > endSrcUs + SCAN_BOUND_US) break
                    continue
                }
                if (pUs < kfUs) {
                    av_packet_unref(pkt); continue
                }
                // An encoded span wrote this stream last: the copied IDR
                // only decodes under the SOURCE parameter sets — inject
                // them in-band before it. The splice is what makes the
                // remux correct; without it the copied GOP hits the
                // encoder's stale SPS/PPS.
                val inject = if (firstCopied && afterEncodedSpan) {
                    firstCopied = false
                    ensureSourceParamSets()
                } else {
                    firstCopied = false
                    emptyList()
                }
                writePacket(
                    video, pkt,
                    relPts = av_rescale_q(
                        pkt.pts() - clipStartInTb, video.inTb, video.outTb
                    ),
                    relDts = av_rescale_q(
                        (pkt.dts().takeIf { it != AV_NOPTS_VALUE }
                            ?: pkt.pts()) - clipStartInTb,
                        video.inTb, video.outTb
                    ),
                    relDuration = av_rescale_q(
                        pkt.duration(), video.inTb, video.outTb
                    ),
                    injectNals = inject
                )
                av_packet_unref(pkt)
            }
        }

        /** Copies AAC packets overlapping [startUs, endUs). */
        private suspend fun copyAudioSpan(
            stream: OutStream, startSrcUs: Long, endSrcUs: Long
        ) {
            seekTb(usToTb(startSrcUs + containerStartUs, stream.inTb), aIndex)
            while (av_read_frame(ic, pkt) >= 0) {
                coroutineContext.ensureActive()
                if (pkt.stream_index() != aIndex) {
                    av_packet_unref(pkt); continue
                }
                val p = pkt.pts().takeIf { it != AV_NOPTS_VALUE } ?: pkt.dts()
                val pUs = tbToUs(p, stream.inTb) - containerStartUs
                val endUs = pUs + tbToUs(
                    pkt.duration().coerceAtLeast(0L), stream.inTb
                )
                if (endUs <= startSrcUs) {
                    av_packet_unref(pkt); continue
                }
                if (pUs >= endSrcUs) {
                    av_packet_unref(pkt); break
                }
                // A packet straddling the cut keeps its payload and clamps
                // its start to the cut — a few ms of stretch, never a hole.
                val relPtsUs = (pUs - startSrcUs).coerceAtLeast(0L)
                writePacket(
                    stream, pkt,
                    relPts = usToTb(relPtsUs, stream.outTb),
                    relDts = usToTb(relPtsUs, stream.outTb),
                    relDuration = av_rescale_q(
                        pkt.duration().coerceAtLeast(0L),
                        stream.inTb, stream.outTb
                    ),
                    injectNals = emptyList()
                )
                av_packet_unref(pkt)
            }
        }

        /**
         * Stamps and muxes [pkt] — copied or freshly encoded — whose
         * timestamps are clip-relative [relPts]/[relDts]/[relDuration] in
         * output time base units. The packet's payload and flags are kept;
         * when [injectNals] is non-empty a fresh packet is built carrying
         * the parameter-set NALs (4-byte length + bytes) ahead of the
         * payload, so a span boundary never meets a decoder holding the
         * wrong SPS/PPS. The caller unrefs [pkt] after this call (a
         * consumed packet unrefs to a no-op).
         */
        private fun writePacket(
            stream: OutStream, pkt: AVPacket,
            relPts: Long, relDts: Long, relDuration: Long,
            injectNals: List<ByteArray>
        ) {
            var outDts = stream.base + stream.slack + relDts
            var outPts = stream.base + stream.slack + relPts
            if (outDts <= stream.lastDts) {
                // Copied B-frame streams rebased onto the splice can land
                // under the last written DTS — the muxer demands monotonic,
                // so the remainder of the stream shifts by the deficit.
                // PTS moves with it: the span keeps its shape and the only
                // artifact is a sub-GOP display hold at the cut.
                val delta = stream.lastDts + 1 - outDts
                stream.slack += delta
                outDts += delta
                outPts += delta
            }
            if (outPts < outDts) outPts = outDts
            stream.lastDts = outDts

            var target = pkt
            var owned: AVPacket? = null
            if (injectNals.isNotEmpty()) {
                val extra = injectNals.sumOf { it.size + NAL_LENGTH_SIZE }
                val created = av_packet_alloc()
                    ?: throw IOException("av_packet_alloc failed")
                if (av_new_packet(created, extra + pkt.size()) < 0) {
                    av_packet_free(created)
                    throw IOException("av_new_packet failed")
                }
                val buf = created.data()
                    .capacity((extra + pkt.size()).toLong()).asBuffer()
                for (nal in injectNals) {
                    buf.putInt(nal.size)
                    buf.put(nal)
                }
                val bytes = ByteArray(pkt.size())
                pkt.data().capacity(pkt.size().toLong()).asBuffer().get(bytes)
                buf.put(bytes)
                created.flags(pkt.flags())
                target = created
                owned = created
            }
            target.pts(outPts)
            target.dts(outDts)
            target.duration(relDuration.coerceAtLeast(0L))
            target.stream_index(stream.index)
            target.time_base(stream.outTb)
            val rc = av_interleaved_write_frame(oc, target)
            owned?.let { av_packet_free(it) }
            if (rc < 0) throw IOException("av_interleaved_write_frame: $rc")
        }

        private fun seekTb(tb: Long, streamIndex: Int) {
            if (av_seek_frame(ic, streamIndex, tb, AVSEEK_FLAG_BACKWARD) < 0) {
                throw IOException("av_seek_frame($tb) failed")
            }
        }

        private fun ensureEncoder(): FfmpegSegmentEncoder =
            encoder ?: run {
                val name = FfmpegSegmentEncoder.encoderNameFor(probe.videoCodecId)
                    ?: throw UnsupportedVideoException(
                        "no packaged encoder for codec ${probe.videoCodecId}"
                    )
                val fpsNum: Int
                val fpsDen: Int
                val avg = probeAvgFrameRate()
                if (avg != null) { fpsNum = avg.first; fpsDen = avg.second }
                else { fpsNum = 30; fpsDen = 1 }
                val bitRate = probeBitrate().takeIf { it > 0L } ?: (
                    probe.rawSize.width.toLong() * probe.rawSize.height *
                        fpsNum.toLong() / fpsDen * 12 / 100
                    ).coerceIn(1_000_000L, 30_000_000L)
                FfmpegSegmentEncoder(
                    name, probe.rawSize.width, probe.rawSize.height,
                    fpsNum, fpsDen, bitRate, video.outTb
                ).also {
                    if (it.codecId != probe.videoCodecId) {
                        throw UnsupportedVideoException(
                            "encoder $name produces codec ${it.codecId}, " +
                                "not ${probe.videoCodecId}"
                        )
                    }
                    encoder = it
                }
            }

        private fun probeAvgFrameRate(): Pair<Int, Int>? {
            val r = ic.streams(vIndex)?.avg_frame_rate() ?: return null
            return if (r.num() > 0 && r.den() > 0) r.num() to r.den() else null
        }

        private fun probeBitrate(): Long =
            ic.streams(vIndex)?.codecpar()?.bit_rate() ?: 0L

        private fun ensureSourceParamSets(): List<ByteArray> {
            if (!sourceParamSetsRead) {
                sourceParamSetsRead = true
                val par = ic.streams(vIndex)?.codecpar()
                val size = par?.extradata_size() ?: 0
                val data = par?.extradata()
                sourceParamSets = if (data != null && size > 0) {
                    val bytes = ByteArray(size)
                    data.position(0).capacity(size.toLong()).get(bytes)
                    FfmpegSegmentEncoder.parseParameterSets(
                        probe.videoCodecId, bytes
                    )
                } else emptyList()
            }
            return sourceParamSets?.takeIf { it.isNotEmpty() }
                ?: throw UnsupportedVideoException(
                    "cannot splice after re-encode: source parameter sets " +
                        "are unparseable"
                )
        }

        private fun packetSrcUs(pkt: AVPacket, tb: AVRational): Long {
            val p = pkt.pts().takeIf { it != AV_NOPTS_VALUE } ?: pkt.dts()
            return tbToUs(p, tb) - containerStartUs
        }

        private fun isKey(pkt: AVPacket): Boolean =
            pkt.flags() and AV_PKT_FLAG_KEY != 0

        fun close() {
            av_packet_free(pkt)
            reader?.close()
            converter?.close()
            encoder?.close()
        }
    }

    /** [Muxer.close] after writes — kept outside the return path. */
    private fun muxerClose(oc: AVFormatContext) {
        oc.pb()?.let { avio_close(it) }
        avformat_free_context(oc)
    }

    private fun addStream(oc: AVFormatContext, inSt: AVStream): AVStream {
        val out = avformat_new_stream(oc, null as AVCodec?)
            ?: throw IOException("avformat_new_stream failed")
        if (avcodec_parameters_copy(out.codecpar(), inSt.codecpar()) < 0) {
            throw IOException("avcodec_parameters_copy failed")
        }
        out.codecpar().codec_tag(0)
        // Picks the muxer's preferred time base and carries frame-rate and
        // duration metadata over — flag -1 is AVFMT_TBCF_AUTO.
        avformat_transfer_internal_stream_timing_info(
            oc.oformat(), out, inSt, -1
        )
        return out
    }

    /**
     * The copied `codecpar` normally already carries the source's display
     * matrix as coded side data; when it does not (older par-copy paths) the
     * rotation would silently drop, so it is written explicitly.
     */
    private fun ensureDisplayRotation(
        par: AVCodecParameters, rotation: Int
    ) {
        if (rotation % 360 == 0) return
        val existing = av_packet_side_data_get(
            par.coded_side_data(), par.nb_coded_side_data(),
            AV_PKT_DATA_DISPLAYMATRIX
        )
        if (existing != null) return
        val sd = par.coded_side_data()
        val holder = if (sd != null) {
            PointerPointer<AVPacketSideData>(sd)
        } else {
            PointerPointer(1)
        }
        val count = IntPointer(par.nb_coded_side_data())
        val entry = av_packet_side_data_new(
            holder, count, AV_PKT_DATA_DISPLAYMATRIX, 9L * 4, 0
        ) ?: throw IOException("cannot allocate display matrix side data")
        par.coded_side_data(AVPacketSideData(holder.get()))
        par.nb_coded_side_data(count.get())
        av_display_rotation_set(
            org.bytedeco.javacpp.IntPointer(entry.data()),
            rotation.toDouble()
        )
    }

    private fun tbToUs(ts: Long, tb: AVRational): Long =
        av_rescale_q(ts, tb, TIME_BASE_US)

    private fun usToTb(us: Long, tb: AVRational): Long =
        av_rescale_q(us, TIME_BASE_US, tb)

    private val TIME_BASE_US = AVRational().num(1).den(AV_TIME_BASE)

    private const val NAL_LENGTH_SIZE = 4
    private const val SCAN_BOUND_US = 120_000_000L
}
