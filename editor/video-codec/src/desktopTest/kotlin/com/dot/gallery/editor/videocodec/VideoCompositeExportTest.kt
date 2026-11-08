package com.dot.gallery.editor.videocodec

import com.dot.gallery.editor.core.model.MediaAsset
import com.dot.gallery.editor.core.model.PixelSize
import com.dot.gallery.editor.core.model.VideoClip
import com.dot.gallery.editor.core.model.VideoLayerTransform
import com.dot.gallery.editor.core.model.VideoSequence
import com.dot.gallery.editor.core.model.VideoTrack
import com.dot.gallery.editor.core.model.VideoTrackKind
import com.dot.gallery.editor.core.time.EditorTime
import com.dot.gallery.editor.core.time.FrameRate
import com.dot.gallery.editor.core.time.TimeRange
import com.dot.gallery.editor.render.video.UnsupportedVideoException
import java.io.File
import java.nio.file.Files
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toPersistentList
import kotlinx.coroutines.test.runTest
import org.bytedeco.ffmpeg.global.avcodec.AV_CODEC_ID_AAC
import org.bytedeco.ffmpeg.global.avcodec.AV_CODEC_ID_H264

/**
 * The composited export path: a two-source sequence — a Main lane under a
 * scaled Overlay lane — encoded through [FfmpegVideoExporter] and verified
 * by reopening the written MP4. Frames prove the overlay pixels landed,
 * the audio stream proves only the Main lane's PCM was written, and both
 * sources stay byte-identical.
 */
class VideoCompositeExportTest {

    private lateinit var dir: File
    private lateinit var mainFixture: File
    private lateinit var overlayFixture: File
    private val resolver = VideoSourceResolver { reference ->
        ResolvedVideoSource(reference, File(reference).name) {}
    }
    private lateinit var exporter: FfmpegVideoExporter

    @BeforeTest
    fun setUp() {
        dir = Files.createTempDirectory("videocomposite").toFile()
        mainFixture = VideoTestFixtures.createFixture(dir, "main.mp4")
        overlayFixture = VideoTestFixtures.createFixture(dir, "overlay.mp4")
        exporter = FfmpegVideoExporter(resolver)
    }

    private fun us(micros: Long) = EditorTime.fromMicroseconds(micros)

    private fun asset(id: String, file: File, duration: EditorTime) =
        MediaAsset.Video(
            id = id,
            reference = file.absolutePath,
            size = PixelSize(
                VideoTestFixtures.WIDTH, VideoTestFixtures.HEIGHT
            ),
            duration = duration,
            frameRate = FrameRate(30),
            hasAudio = true
        )

    /**
     * Main plays the full colour programme [0,4s); an Overlay lane drops a
     * white second ([3s,4s) of its own source) at half scale over timeline
     * [2s,3s) — the output shows a centred white block on the blue band.
     */
    private fun compositeSequence(): VideoSequence {
        val duration = us(4_000_000)
        return VideoSequence(
            size = PixelSize(
                VideoTestFixtures.WIDTH, VideoTestFixtures.HEIGHT
            ),
            frameRate = FrameRate(30),
            sources = persistentListOf(
                asset("src-main", mainFixture, duration),
                asset("src-overlay", overlayFixture, duration)
            ),
            tracks = persistentListOf(
                VideoTrack(
                    "main", kind = VideoTrackKind.Main,
                    clips = persistentListOf(
                        VideoClip(
                            "clip-main", "src-main", EditorTime.Zero,
                            TimeRange(EditorTime.Zero, duration)
                        )
                    )
                ),
                VideoTrack(
                    "overlay", kind = VideoTrackKind.Overlay,
                    clips = persistentListOf(
                        VideoClip(
                            "clip-overlay", "src-overlay", us(2_000_000),
                            TimeRange(us(3_000_000), us(1_000_000)),
                            transform = VideoLayerTransform(scale = 0.5f)
                        )
                    )
                )
            )
        )
    }

    @Test
    fun `overlay pixels land and only Main audio is written`() = runTest {
        val mainHash = VideoTestFixtures.sha256(mainFixture)
        val overlayHash = VideoTestFixtures.sha256(overlayFixture)
        val sequence = compositeSequence()

        // The plan's audio contract: only the Main lane's sources feed
        // PCM — the overlay's embedded audio is never touched.
        assertEquals(
            setOf("src-main"),
            CompositeVideoPlan.validate(sequence).audioSourceIds
        )

        val out = File(dir, "composite.mp4")
        exporter.encode(sequence, out) {}
        assertTrue(out.isFile && out.length() > 0)

        // Reopen-verify: composited pixels are upright on the sequence
        // canvas (rotation 0), audio present from the Main lane.
        resolver.resolve(out.absolutePath).use { staged ->
            FfmpegExportVerification.verify(
                staged, sequence.size, 0, sequence.duration,
                expectAudio = true
            )
            val probe = FfmpegProbe.probe(staged, out.absolutePath)
            assertEquals(AV_CODEC_ID_H264, probe.videoCodecId)
            assertEquals(AV_CODEC_ID_AAC, probe.audioCodecId)
            assertEquals(sequence.size, probe.rawSize)
            assertEquals(0, probe.info.asset.rotationDegrees)
        }

        // Pixels: inside [2s,3s) the canvas centre is the overlay's white
        // band scaled to the middle quarter; the corners keep the Main
        // lane's blue. Before the overlay starts the frame is pure Main.
        val outProbe = resolver.resolve(out.absolutePath).use {
            FfmpegProbe.probe(it, out.absolutePath)
        }
        FfmpegPixelConverter(
            outProbe.colorspace, outProbe.colorRange, outProbe.rawSize.height
        ).use { conv ->
            FfmpegVideoReader(out.absolutePath, conv, 0).use { reader ->
                val mid = reader.frameAt(us(2_500_000), 320).pixels.copyPixels()
                val center = mid[120 * 320 + 160]
                // White band over blue → bright neutral centre.
                val r = (center shr 16) and 0xFF
                val g = (center shr 8) and 0xFF
                val b = center and 0xFF
                assertTrue(
                    minOf(r, g, b) > 150 &&
                        maxOf(r, g, b) - minOf(r, g, b) < 80,
                    "overlay centre not white-ish: r=$r g=$g b=$b"
                )
                VideoTestFixtures.assertDominant(mid[0], 0, 0, 255)
                val early = reader.frameAt(us(500_000), 320).pixels.copyPixels()
                VideoTestFixtures.assertDominant(early[120 * 320 + 160], 255, 0, 0)
            }
        }

        // Audio: the [2.5s,2.6s) window is the Main lane's 880 Hz half —
        // if the overlay's stream had leaked in, the mix would still read
        // as one dominant tone only when both are 880; the plan assertion
        // above is the real proof the overlay PCM reader never opened.
        FfmpegPcmReader(out.absolutePath).use { pcm ->
            val window = pcm.read(120_000L, 4_800)
            assertTrue(
                VideoTestFixtures.risingCrossings(window) in 86..90,
                "main-lane audio window is not 880 Hz"
            )
        }

        // Neither source was written to.
        assertEquals(mainHash, VideoTestFixtures.sha256(mainFixture))
        assertEquals(overlayHash, VideoTestFixtures.sha256(overlayFixture))
    }

    @Test
    fun `unsupported composite shapes fail loudly`() = runTest {
        val duration = us(4_000_000)
        val base = compositeSequence()

        // A Title lane cannot be rendered — never silently dropped.
        val withTitle = base.copy(
            tracks = (base.tracks + VideoTrack(
                "titles", kind = VideoTrackKind.Title,
                clips = persistentListOf(
                    VideoClip(
                        "t1", "src-main", EditorTime.Zero,
                        TimeRange(EditorTime.Zero, duration)
                    )
                )
            )).toPersistentList()
        )
        assertFailsWith<UnsupportedVideoException> {
            exporter.encode(withTitle, File(dir, "title.mp4")) {}
        }

        // Odd canvas dimensions cannot express 4:2:0 — rejected up front.
        val oddCanvas = base.copy(size = PixelSize(321, 240))
        assertFailsWith<UnsupportedVideoException> {
            exporter.encode(oddCanvas, File(dir, "odd.mp4")) {}
        }

        // An empty document has nothing to encode.
        val empty = base.copy(
            tracks = persistentListOf(
                VideoTrack("main", kind = VideoTrackKind.Main)
            )
        )
        assertFailsWith<IllegalArgumentException> {
            exporter.encode(empty, File(dir, "empty.mp4")) {}
        }
    }

    @Test
    fun `a single-source sequence with an overlay recipe composites too`() =
        runTest {
            // One source can still need the compositor: a Main clip with
            // a non-identity recipe is composited rather than dropped.
            val duration = us(4_000_000)
            val sequence = VideoSequence(
                size = PixelSize(
                    VideoTestFixtures.WIDTH, VideoTestFixtures.HEIGHT
                ),
                frameRate = FrameRate(30),
                sources = persistentListOf(
                    asset("src-main", mainFixture, duration)
                ),
                tracks = persistentListOf(
                    VideoTrack(
                        "main", kind = VideoTrackKind.Main,
                        clips = persistentListOf(
                            VideoClip(
                                "clip-main", "src-main", EditorTime.Zero,
                                TimeRange(EditorTime.Zero, duration),
                                transform = VideoLayerTransform(scale = 0.5f)
                            )
                        )
                    )
                )
            )
            val out = File(dir, "main-scaled.mp4")
            exporter.encode(sequence, out) {}
            val outProbe = resolver.resolve(out.absolutePath).use {
                FfmpegProbe.probe(it, out.absolutePath)
            }
            FfmpegPixelConverter(
                outProbe.colorspace, outProbe.colorRange,
                outProbe.rawSize.height
            ).use { conv ->
                FfmpegVideoReader(out.absolutePath, conv, 0).use { reader ->
                    val frame =
                        reader.frameAt(us(500_000), 320).pixels.copyPixels()
                    // Centre: the source's red band, scaled to the middle.
                    VideoTestFixtures.assertDominant(
                        frame[120 * 320 + 160], 255, 0, 0
                    )
                    // Corner: the compositor's transparent surround baked
                    // to black by the opaque encoder input.
                    val corner = frame[0] and 0xFFFFFF
                    val channels = intArrayOf(
                        (corner shr 16) and 0xFF,
                        (corner shr 8) and 0xFF,
                        corner and 0xFF
                    )
                    assertTrue(
                        channels.max() < 80,
                        "surround not black: ${channels.toList()}"
                    )
                }
            }
        }
}
