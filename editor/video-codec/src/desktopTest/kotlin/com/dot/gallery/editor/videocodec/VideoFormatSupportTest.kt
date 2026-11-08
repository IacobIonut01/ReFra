package com.dot.gallery.editor.videocodec

import com.dot.gallery.editor.core.model.ColorAdjustments
import com.dot.gallery.editor.core.model.MediaAsset
import com.dot.gallery.editor.core.model.PixelSize
import com.dot.gallery.editor.core.model.VideoClip
import com.dot.gallery.editor.core.model.VideoLayerTransform
import com.dot.gallery.editor.core.model.VideoSequence
import com.dot.gallery.editor.core.model.VideoTrack
import com.dot.gallery.editor.core.time.EditorTime
import com.dot.gallery.editor.core.time.FrameRate
import com.dot.gallery.editor.core.time.TimeRange
import java.io.File
import java.nio.Buffer
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.ShortBuffer
import java.nio.file.Files
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.runBlocking
import org.bytedeco.ffmpeg.global.avcodec.avcodec_find_decoder_by_name
import org.bytedeco.ffmpeg.global.avformat.av_find_input_format
import org.bytedeco.ffmpeg.global.avutil.AVCOL_PRI_BT709
import org.bytedeco.ffmpeg.global.avutil.AVCOL_TRC_BT709
import org.bytedeco.ffmpeg.global.avutil.AV_PIX_FMT_P010LE
import org.bytedeco.ffmpeg.global.avutil.AV_PIX_FMT_YUV420P
import org.bytedeco.ffmpeg.global.avutil.AV_PIX_FMT_YUV420P10LE
import org.bytedeco.ffmpeg.global.avutil.AV_PIX_FMT_YUV422P10LE
import org.bytedeco.ffmpeg.global.avutil.AV_PIX_FMT_YUVJ420P
import org.bytedeco.javacv.FFmpegFrameRecorder
import org.bytedeco.javacv.Frame

/**
 * Every major container and codec the editor opens, end to end: a real file is
 * written with the packaged FFmpeg, then probed, decoded to the expected colour
 * and, where it has sound, read as stereo PCM. HEVC, VC-1 and other codecs with
 * no portable encoder here are held to their decoder being packaged.
 */
class VideoFormatSupportTest {

    private lateinit var dir: File
    private val resolver = VideoSourceResolver { reference ->
        ResolvedVideoSource(reference, File(reference).name) {}
    }

    @BeforeTest
    fun setUp() {
        dir = Files.createTempDirectory("videoformats").toFile()
    }

    @AfterTest
    fun tearDown() {
        dir.deleteRecursively()
    }

    private class Spec(
        val file: String,
        val format: String,
        val video: String,
        val pixelFormat: Int = AV_PIX_FMT_YUV420P,
        val audio: String? = null,
        val width: Int = 320,
        val height: Int = 240,
        val channels: Int = 2,
        val videoOptions: Map<String, String> = emptyMap(),
        val formatOptions: Map<String, String> = emptyMap(),
        val rgb: Triple<Int, Int, Int> = Triple(220, 20, 20),
        val seconds: Int = 1
    )

    private fun write(spec: Spec): File {
        FfmpegSupport.require()
        val out = File(dir, spec.file)
        val recorder = FFmpegFrameRecorder(
            out.absolutePath, spec.width, spec.height,
            if (spec.audio != null) spec.channels else 0
        )
        recorder.format = spec.format
        recorder.videoCodecName = spec.video
        recorder.pixelFormat = spec.pixelFormat
        recorder.frameRate = FPS.toDouble()
        recorder.gopSize = FPS
        recorder.videoBitrate = 800_000
        spec.videoOptions.forEach { (key, value) -> recorder.setVideoOption(key, value) }
        spec.formatOptions.forEach { (key, value) -> recorder.setOption(key, value) }
        if (spec.audio != null) {
            recorder.audioCodecName = spec.audio
            recorder.sampleRate = SAMPLE_RATE
            recorder.audioBitrate = 128_000
        }
        try {
            recorder.start()
            val picture = planarPicture(spec)
            val depth = if (spec.pixelFormat in TEN_BIT) Frame.DEPTH_USHORT else Frame.DEPTH_UBYTE
            val pcm = ShortBuffer.allocate(SAMPLE_RATE / FPS * spec.channels)
            var sample = 0L
            repeat(FPS * spec.seconds) {
                picture.rewind()
                recorder.recordImage(spec.width, spec.height, depth, 1, spec.width, spec.pixelFormat, picture)
                if (spec.audio != null) {
                    pcm.clear()
                    repeat(SAMPLE_RATE / FPS) {
                        val value = (sin(2.0 * PI * 440.0 * sample / SAMPLE_RATE) * 8000.0)
                            .toInt().toShort()
                        repeat(spec.channels) { pcm.put(value) }
                        sample++
                    }
                    pcm.flip()
                    recorder.recordSamples(SAMPLE_RATE, spec.channels, pcm)
                }
            }
            recorder.stop()
        } finally {
            recorder.release()
        }
        return out
    }

    /**
     * One solid frame, already in the encoder's pixel format, so the file holds
     * exactly these samples with no conversion in the recorder.
     */
    private fun planarPicture(spec: Spec): Buffer {
        val (r, g, b) = spec.rgb
        val y = (16 + 0.257 * r + 0.504 * g + 0.098 * b).toInt()
        val u = (128 - 0.148 * r - 0.291 * g + 0.439 * b).toInt()
        val v = (128 + 0.439 * r - 0.368 * g - 0.071 * b).toInt()
        val luma = spec.width * spec.height
        val chromaHeight = if (spec.pixelFormat == AV_PIX_FMT_YUV422P10LE) spec.height else spec.height / 2
        val chroma = spec.width / 2 * chromaHeight
        if (spec.pixelFormat == AV_PIX_FMT_P010LE) {
            val samples = ByteBuffer.allocateDirect((luma + chroma * 2) * 2)
                .order(ByteOrder.nativeOrder()).asShortBuffer()
            repeat(luma) { samples.put((y shl 8).toShort()) }
            repeat(chroma) {
                samples.put((u shl 8).toShort())
                samples.put((v shl 8).toShort())
            }
            return samples
        }
        if (spec.pixelFormat in TEN_BIT) {
            val samples = ByteBuffer.allocateDirect((luma + chroma * 2) * 2)
                .order(ByteOrder.nativeOrder()).asShortBuffer()
            repeat(luma) { samples.put((y shl 2).toShort()) }
            repeat(chroma) { samples.put((u shl 2).toShort()) }
            repeat(chroma) { samples.put((v shl 2).toShort()) }
            return samples
        }
        val samples = ByteBuffer.allocateDirect(luma + chroma * 2)
        repeat(luma) { samples.put(y.toByte()) }
        repeat(chroma) { samples.put(u.toByte()) }
        repeat(chroma) { samples.put(v.toByte()) }
        return samples
    }

    private fun probe(file: File): FfmpegProbeResult = runBlocking {
        resolver.resolve(file.absolutePath).use { source ->
            FfmpegProbe.probe(source, file.absolutePath)
        }
    }

    /** The decoded centre pixel of the first frame, as (r, g, b). */
    private fun firstFrameRgb(file: File, probe: FfmpegProbeResult): Triple<Int, Int, Int> =
        FfmpegPixelConverter(probe.colorspace, probe.colorRange, probe.rawSize.height).use { converter ->
            FfmpegVideoReader(file.absolutePath, converter, 0, probe.videoStreamIndex).use { reader ->
                val frame = runBlocking { reader.frameAt(EditorTime.Zero, 320) }
                val argb = frame.pixels.argbAt(frame.pixels.size.width / 2, frame.pixels.size.height / 2)
                Triple((argb shr 16) and 0xFF, (argb shr 8) and 0xFF, argb and 0xFF)
            }
        }

    private fun assertOpens(spec: Spec) {
        val file = write(spec)
        val result = probe(file)
        assertEquals(PixelSize(spec.width, spec.height), result.rawSize, spec.file)
        assertTrue(result.info.asset.duration.ticks > 0L, "${spec.file} has no duration")
        val (r, g, b) = firstFrameRgb(file, result)
        assertTrue(r > 150 && g < 90 && b < 90, "${spec.file} decoded to ($r, $g, $b)")
        if (spec.audio != null) {
            assertEquals(2, result.info.audioChannels?.coerceAtLeast(2), spec.file)
            FfmpegPcmReader(file.absolutePath, result.audioStreamIndex).use { reader ->
                val samples = runBlocking { reader.read(0L, 4_800) }
                assertTrue(VideoTestFixtures.rms(samples) > 100.0, "${spec.file} read silence")
            }
        }
    }

    @Test fun mp4H264Aac() = assertOpens(Spec("a.mp4", "mp4", "libopenh264", audio = "aac"))

    @Test fun movH264Aac() = assertOpens(Spec("a.mov", "mov", "libopenh264", audio = "aac"))

    @Test fun m4vMpeg4Aac() = assertOpens(Spec("a.m4v", "mp4", "mpeg4", audio = "aac"))

    @Test fun threeGpH263Aac() =
        assertOpens(Spec("a.3gp", "3gp", "h263", audio = "aac", width = 352, height = 288))

    @Test fun mkvVp9Opus() = assertOpens(
        Spec(
            "a.mkv", "matroska", "libvpx-vp9", audio = "libopus",
            videoOptions = mapOf("deadline" to "realtime", "cpu-used" to "8")
        )
    )

    @Test fun webmVp8Opus() = assertOpens(
        Spec(
            "a.webm", "webm", "libvpx", audio = "libopus",
            videoOptions = mapOf("deadline" to "realtime", "cpu-used" to "8")
        )
    )

    @Test fun webmAv1Opus() = assertOpens(
        Spec(
            "b.webm", "webm", "libaom-av1", audio = "libopus",
            videoOptions = mapOf("usage" to "realtime", "cpu-used" to "8")
        )
    )

    @Test fun aviMpeg4Mp3() = assertOpens(Spec("a.avi", "avi", "mpeg4", audio = "libmp3lame"))

    @Test fun aviMjpeg() =
        assertOpens(Spec("b.avi", "avi", "mjpeg", pixelFormat = AV_PIX_FMT_YUVJ420P))

    @Test fun wmvWmv2Wma() = assertOpens(Spec("a.wmv", "asf", "wmv2", audio = "wmav2"))

    @Test fun flvFlv1Mp3() = assertOpens(Spec("a.flv", "flv", "flv", audio = "libmp3lame"))

    @Test fun tsMpeg2Mp2() = assertOpens(Spec("a.ts", "mpegts", "mpeg2video", audio = "mp2"))

    @Test fun mpgMpeg1Mp2() = assertOpens(Spec("a.mpg", "mpeg", "mpeg1video", audio = "mp2"))

    @Test fun movProRes10Bit() = assertOpens(
        Spec("b.mov", "mov", "prores_ks", pixelFormat = AV_PIX_FMT_YUV422P10LE, audio = "pcm_s16le")
    )

    /** Both start their timestamps well after zero, and camcorders write TS. */
    @Test
    fun containersThatDoNotStartAtZeroKeepFrameTimesFromZero() {
        listOf(
            Spec("seek.ts", "mpegts", "mpeg2video", seconds = 4),
            Spec("seek.mpg", "mpeg", "mpeg1video", seconds = 4)
        ).forEach { spec ->
            val file = write(spec)
            val result = probe(file)
            FfmpegPixelConverter(result.colorspace, result.colorRange, result.rawSize.height).use { converter ->
                FfmpegVideoReader(file.absolutePath, converter, 0, result.videoStreamIndex).use { reader ->
                    fun seconds(atUs: Long) = runBlocking {
                        reader.frameAt(EditorTime.fromMicroseconds(atUs), 160)
                    }.time.toMicrosecondsFloor() / 1_000_000.0
                    assertTrue(seconds(0L) < 0.1, "${spec.file} first frame is late")
                    assertTrue(abs(seconds(3_500_000L) - 3.5) < 0.1, "${spec.file} forward read")
                    assertTrue(abs(seconds(2_500_000L) - 2.5) < 0.1, "${spec.file} backward seek")
                    assertTrue(seconds(0L) < 0.1, "${spec.file} back to the start")
                }
            }
        }
    }

    @Test
    fun codecsWithoutAPortableEncoderHaveTheirDecodersAndDemuxers() {
        FfmpegSupport.require()
        listOf(
            "h264", "hevc", "vp8", "vp9", "mpeg4", "h263", "mpeg1video", "mpeg2video", "mjpeg",
            "wmv1", "wmv2", "wmv3", "vc1", "prores", "dnxhd", "theora", "flv", "rv40", "dvvideo",
            "aac", "mp3", "ac3", "eac3", "dca", "opus", "vorbis", "flac", "alac", "wmav2", "amrnb"
        ).forEach { name ->
            assertTrue(avcodec_find_decoder_by_name(name) != null, "no $name decoder")
        }
        listOf(
            "mov,mp4,m4a,3gp,3g2,mj2", "matroska,webm", "avi", "asf", "mpegts", "mpeg",
            "flv", "ogg", "mxf", "dv", "rm"
        ).forEach { name ->
            assertTrue(av_find_input_format(name) != null, "no $name demuxer")
        }
    }

    @Test
    fun surroundSoundOpensAndDownmixesToStereo() {
        val file = write(Spec("surround.mp4", "mp4", "libopenh264", audio = "aac", channels = 6))
        val result = probe(file)
        assertEquals(2, result.info.audioChannels)
        FfmpegPcmReader(file.absolutePath, result.audioStreamIndex).use { reader ->
            val samples = runBlocking { reader.read(0L, 4_800) }
            assertEquals(4_800 * 2, samples.size)
            assertTrue(VideoTestFixtures.rms(samples) > 100.0, "downmix is silent")
        }
    }

    @Test
    fun aFileThatStatesNoDurationIsMeasured() {
        val file = write(
            Spec(
                "live.mkv", "matroska", "libopenh264",
                formatOptions = mapOf("live" to "1")
            )
        )
        val seconds = probe(file).info.asset.duration.toMicrosecondsFloor() / 1_000_000.0
        assertTrue(abs(seconds - 1.0) < 0.2, "measured $seconds s for a 1 s file")
    }

    private fun hdrSpec(file: String, trc: String) = Spec(
        file, "matroska", "ffv1", pixelFormat = AV_PIX_FMT_YUV420P10LE,
        rgb = Triple(128, 128, 128),
        videoOptions = mapOf(
            "colorspace" to "bt2020nc",
            "color_primaries" to "bt2020",
            "color_trc" to trc
        )
    )

    @Test
    fun hlgVideoOpensToneMappedAndNeutral() {
        val plain = write(hdrSpec("sdr10.mkv", "bt709").let {
            Spec(
                it.file, it.format, it.video, it.pixelFormat, rgb = it.rgb,
                videoOptions = mapOf("colorspace" to "bt709", "color_primaries" to "bt709", "color_trc" to "bt709")
            )
        })
        val hlg = write(hdrSpec("hlg.mkv", "arib-std-b67"))
        val plainRgb = firstFrameRgb(plain, probe(plain))
        val (r, g, b) = firstFrameRgb(hlg, probe(hlg))
        assertTrue(abs(r - g) <= 12 && abs(g - b) <= 12, "grey tinted to ($r, $g, $b)")
        assertNotEquals(plainRgb, Triple(r, g, b), "HLG was not tone mapped")
    }

    @Test
    fun hdrExportIsTaggedAsSdr() {
        val file = write(hdrSpec("pq.mkv", "smpte2084"))
        val source = probe(file)
        val asset = MediaAsset.Video(
            id = "source", reference = file.absolutePath,
            size = source.rawSize,
            duration = source.info.asset.duration,
            frameRate = FrameRate(30), hasAudio = false
        )
        val sequence = VideoSequence(
            size = source.rawSize,
            frameRate = FrameRate(30),
            sources = persistentListOf(asset),
            tracks = persistentListOf(
                VideoTrack(
                    "track",
                    persistentListOf(
                        VideoClip(
                            "clip", "source", EditorTime.Zero,
                            TimeRange(EditorTime.Zero, EditorTime.fromMicroseconds(500_000))
                        )
                    )
                )
            )
        )
        val out = File(dir, "pq-export.mp4")
        runBlocking { FfmpegVideoExporter(resolver).encode(sequence, out) {} }
        val exported = probe(out)
        assertEquals(AVCOL_TRC_BT709, exported.colorTrc)
        assertEquals(AVCOL_PRI_BT709, exported.colorPrimaries)
    }

    /**
     * HEVC has no portable encoder here, so this uses a hardware one where the
     * machine has it and otherwise leaves HEVC to the decoder audit above.
     */
    private fun assertOpensWithHardwareHevc(makeSpec: (String) -> Spec, verify: (File) -> Unit) {
        listOf("hevc_nvenc", "hevc_mf").forEach { encoder ->
            val file = try {
                write(makeSpec(encoder))
            } catch (_: FFmpegFrameRecorder.Exception) {
                return@forEach
            }
            verify(file)
            return
        }
    }

    @Test
    fun hevcOpensWhenAHardwareEncoderCanWriteIt() = assertOpensWithHardwareHevc(
        { encoder -> Spec("hevc.mp4", "mp4", encoder, audio = "aac") }
    ) { file ->
        val result = probe(file)
        val (r, g, b) = firstFrameRgb(file, result)
        assertTrue(r > 150 && g < 90 && b < 90, "HEVC decoded to ($r, $g, $b)")
    }

    @Test
    fun tenBitHlgHevcLikeAPhoneClipOpensToneMapped() = assertOpensWithHardwareHevc(
        { encoder ->
            Spec(
                "hevc-hlg.mp4", "mp4", encoder, pixelFormat = AV_PIX_FMT_P010LE,
                rgb = Triple(128, 128, 128),
                videoOptions = mapOf(
                    "colorspace" to "bt2020nc",
                    "color_primaries" to "bt2020",
                    "color_trc" to "arib-std-b67"
                )
            )
        }
    ) { file ->
        val result = probe(file)
        val (r, g, b) = firstFrameRgb(file, result)
        assertTrue(abs(r - g) <= 12 && abs(g - b) <= 12, "grey tinted to ($r, $g, $b)")
    }

    private fun sequenceOf(file: File, result: FfmpegProbeResult, clip: VideoClip, muted: Boolean) =
        VideoSequence(
            size = result.rawSize,
            frameRate = FrameRate(30),
            sources = persistentListOf(
                MediaAsset.Video(
                    id = "source", reference = file.absolutePath,
                    size = result.rawSize,
                    duration = result.info.asset.duration,
                    frameRate = FrameRate(30), hasAudio = result.info.hasAudio
                )
            ),
            tracks = persistentListOf(VideoTrack("track", persistentListOf(clip))),
            sourceSoundMuted = muted
        )

    /** Muting the sound leaves the fast path, but no picture should change. */
    @Test
    fun anAudioOnlyEditExportsTheSamePictureAsACut() {
        val file = write(Spec("audio-edit.mp4", "mp4", "libopenh264", audio = "aac"))
        val result = probe(file)
        val clip = VideoClip(
            "clip", "source", EditorTime.Zero,
            TimeRange(EditorTime.Zero, EditorTime.fromMicroseconds(500_000))
        )
        val exporter = FfmpegVideoExporter(resolver)
        val cut = File(dir, "cut.mp4")
        val edited = File(dir, "edited.mp4")
        runBlocking {
            exporter.encode(sequenceOf(file, result, clip, muted = false), cut) {}
            exporter.encode(sequenceOf(file, result, clip, muted = true), edited) {}
        }
        val before = firstFrameRgb(cut, probe(cut))
        val after = firstFrameRgb(edited, probe(edited))
        assertEquals(before, after)
    }

    /** The Clip colour panel's edits used to make the export throw. */
    @Test
    fun aColourEditIsBakedIntoTheExport() {
        val file = write(Spec("graded.mp4", "mp4", "libopenh264", rgb = Triple(90, 90, 90)))
        val result = probe(file)
        val clip = VideoClip(
            "clip", "source", EditorTime.Zero,
            TimeRange(EditorTime.Zero, EditorTime.fromMicroseconds(300_000)),
            adjustments = ColorAdjustments(exposureStops = 1f)
        )
        val out = File(dir, "graded-out.mp4")
        runBlocking {
            FfmpegVideoExporter(resolver).encode(sequenceOf(file, result, clip, muted = false), out) {}
        }
        val before = firstFrameRgb(file, result).first
        val after = firstFrameRgb(out, probe(out)).first
        assertTrue(after > before + 20, "exposure was not applied: $before to $after")
    }

    @Test
    fun onlyAnUneditedUprightCanvasSizedClipPassesThrough() {
        val file = write(Spec("pass.mp4", "mp4", "libopenh264"))
        val result = probe(file)
        val clip = VideoClip(
            "clip", "source", EditorTime.Zero,
            TimeRange(EditorTime.Zero, EditorTime.fromMicroseconds(500_000))
        )
        val exporter = FfmpegVideoExporter(resolver)
        fun passes(candidate: VideoClip, canvas: PixelSize = result.rawSize, matrix: Int = 1) =
            exporter.passesThroughUntouched(candidate, result, canvas, 1, matrix)
        assertTrue(passes(clip))
        assertTrue(!passes(clip.copy(transform = VideoLayerTransform(scale = 1.5f))))
        assertTrue(!passes(clip.copy(opacity = 0.5f)))
        assertTrue(!passes(clip.copy(adjustments = ColorAdjustments(exposureStops = 0.5f))))
        assertTrue(!passes(clip, canvas = PixelSize(640, 480)))
        assertTrue(!passes(clip, matrix = 5))
    }

    /** Long enough to trip the native heap corruption this path used to cause. */
    @Test
    fun repeatedExportConversionsKeepTheHeapIntact() {
        val file = write(Spec("loop.mkv", "matroska", "ffv1", seconds = 1))
        val result = probe(file)
        FfmpegPixelConverter(result.colorspace, result.colorRange, result.rawSize.height).use { converter ->
            FfmpegVideoReader(file.absolutePath, converter, 0, result.videoStreamIndex).use { reader ->
                repeat(4) {
                    for (frame in 0 until 28) {
                        val at = EditorTime.fromMicroseconds(frame * 33_333L)
                        runBlocking { converter.toYuv420p(reader.rawFrameAt(at), 320, 240) }
                    }
                }
            }
        }
        repeat(2_000) { ByteBuffer.allocateDirect(1 shl 14) }
    }

    private companion object {
        const val FPS = 30
        const val SAMPLE_RATE = 48_000
        val TEN_BIT = setOf(AV_PIX_FMT_YUV420P10LE, AV_PIX_FMT_YUV422P10LE, AV_PIX_FMT_P010LE)
    }
}
