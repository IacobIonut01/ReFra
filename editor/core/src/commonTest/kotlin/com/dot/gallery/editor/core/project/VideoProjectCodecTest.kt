package com.dot.gallery.editor.core.project

import com.dot.gallery.editor.core.model.AudioClip
import com.dot.gallery.editor.core.model.AudioDucking
import com.dot.gallery.editor.core.model.AudioEq
import com.dot.gallery.editor.core.model.AudioRouting
import com.dot.gallery.editor.core.model.AudioTrack
import com.dot.gallery.editor.core.model.ColorAdjustments
import com.dot.gallery.editor.core.model.MediaAsset
import com.dot.gallery.editor.core.model.PhotoBlendMode
import com.dot.gallery.editor.core.model.PixelSize
import com.dot.gallery.editor.core.model.VideoClip
import com.dot.gallery.editor.core.model.VideoLayerTransform
import com.dot.gallery.editor.core.model.VideoSequence
import com.dot.gallery.editor.core.model.VideoTrack
import com.dot.gallery.editor.core.model.VideoTrackKind
import com.dot.gallery.editor.core.time.EditorTime
import com.dot.gallery.editor.core.time.FrameRate
import com.dot.gallery.editor.core.time.TimeRange
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.collections.immutable.persistentListOf

class VideoProjectCodecTest {

    private fun us(micros: Long) = EditorTime.fromMicroseconds(micros)

    private fun source(
        id: String,
        reference: String = "content://media/$id",
        durationUs: Long = 4_000_000L,
        rotation: Int = 0,
        rate: FrameRate? = FrameRate(30)
    ) = MediaAsset.Video(
        id = id,
        reference = reference,
        size = PixelSize(320, 240),
        duration = us(durationUs),
        frameRate = rate,
        hasAudio = true,
        rotationDegrees = rotation
    )

    private fun clip(
        id: String,
        sourceId: String,
        startUs: Long,
        rangeStartUs: Long,
        durationUs: Long,
        transform: VideoLayerTransform = VideoLayerTransform(),
        opacity: Float = 1f,
        blendMode: PhotoBlendMode = PhotoBlendMode.Normal,
        visible: Boolean = true,
        adjustments: ColorAdjustments = ColorAdjustments()
    ) = VideoClip(
        id = id,
        sourceId = sourceId,
        timelineStart = us(startUs),
        sourceRange = TimeRange(us(rangeStartUs), us(durationUs)),
        adjustments = adjustments,
        transform = transform,
        opacity = opacity,
        blendMode = blendMode,
        visible = visible
    )

    private fun richSequence() = VideoSequence(
        size = PixelSize(640, 360),
        frameRate = FrameRate(30),
        sources = persistentListOf(
            source("src-1"),
            source(
                "src-2",
                reference = "content://media/\"quoted\"\\path/é",
                durationUs = 2_000_000L,
                rotation = 90,
                rate = null
            )
        ),
        tracks = persistentListOf(
            VideoTrack(
                id = "main",
                kind = VideoTrackKind.Main,
                clips = persistentListOf(
                    clip("c-1", "src-1", 0L, 0L, 1_000_000L),
                    clip("c-2", "src-2", 1_000_000L, 500_000L, 1_000_000L)
                )
            ),
            VideoTrack(
                id = "overlay",
                name = "Overlay 1",
                kind = VideoTrackKind.Overlay,
                visible = true,
                locked = true,
                clips = persistentListOf(
                    clip(
                        "c-3", "src-2", 250_000L, 0L, 1_000_000L,
                        transform = VideoLayerTransform(
                            translationX = -8.5f,
                            translationY = 48.25f,
                            scale = 0.65f,
                            rotationDegrees = 12.5f,
                            flipHorizontal = true
                        ),
                        opacity = 0.65f,
                        blendMode = PhotoBlendMode.Screen,
                        visible = false
                    )
                )
            )
        )
    )

    @Test
    fun `encode decode round-trips the whole document`() {
        val snapshot = VideoProjectSnapshot(
            sequence = richSequence(),
            selectedClipId = "c-3",
            position = us(750_000L)
        )
        val decoded = VideoProjectCodec.decode(VideoProjectCodec.encode(snapshot))
        assertEquals(snapshot, decoded)
    }

    @Test
    fun `encode is deterministic`() {
        val snapshot = VideoProjectSnapshot(
            sequence = richSequence(),
            selectedClipId = null,
            position = EditorTime.Zero
        )
        val first = VideoProjectCodec.encode(snapshot)
        val second = VideoProjectCodec.encode(snapshot)
        assertEquals(first, second)
        // And re-decoding the decoded form stays byte-identical.
        assertEquals(
            first,
            VideoProjectCodec.encode(VideoProjectCodec.decode(first))
        )
    }

    @Test
    fun `a null selection and zero position round-trip`() {
        val snapshot = VideoProjectSnapshot(
            sequence = VideoSequence(
                size = PixelSize(2, 2),
                frameRate = FrameRate(30),
                sources = persistentListOf(source("only")),
                tracks = persistentListOf(
                    VideoTrack("main", kind = VideoTrackKind.Main)
                )
            ),
            selectedClipId = null,
            position = EditorTime.Zero
        )
        assertEquals(
            snapshot,
            VideoProjectCodec.decode(VideoProjectCodec.encode(snapshot))
        )
    }

    @Test
    fun `string escaping survives quotes backslashes and control characters`() {
        val snapshot = VideoProjectSnapshot(
            sequence = VideoSequence(
                size = PixelSize(2, 2),
                frameRate = FrameRate(30),
                sources = persistentListOf(
                    source("s", reference = "we\"ird\\ref\nwith\ttab")
                ),
                tracks = persistentListOf(
                    VideoTrack("main", kind = VideoTrackKind.Main)
                )
            ),
            selectedClipId = null,
            position = EditorTime.Zero
        )
        val decoded = VideoProjectCodec.decode(VideoProjectCodec.encode(snapshot))
        assertEquals(snapshot, decoded)
    }

    @Test
    fun `defaults fill fields a newer writer may omit`() {
        // Minimal clip JSON — no recipe fields at all.
        val json = """
        {
          "version": 1,
          "size": {"width": 4, "height": 4},
          "frameRate": {"numerator": 30, "denominator": 1},
          "positionTicks": 0,
          "selectedClipId": null,
          "sources": [{
            "id": "s", "reference": "r", "width": 2, "height": 2,
            "durationTicks": 1000000, "frameRate": null,
            "hasAudio": false, "rotationDegrees": 0
          }],
          "tracks": [{
            "id": "main", "name": "Main", "kind": "Main",
            "visible": true, "locked": false,
            "clips": [{
              "id": "c", "sourceId": "s",
              "timelineStartTicks": 0, "sourceStartTicks": 0,
              "sourceDurationTicks": 1000000
            }]
          }]
        }
        """.trimIndent()
        val clip = VideoProjectCodec.decode(json).sequence
            .tracks[0].clips[0]
        assertEquals(VideoLayerTransform(), clip.transform)
        assertEquals(1f, clip.opacity)
        assertEquals(PhotoBlendMode.Normal, clip.blendMode)
        assertTrue(clip.visible)
        assertEquals(ColorAdjustments(), clip.adjustments)
    }

    @Test
    fun `unknown extra keys are ignored for forward compatibility`() {
        val base = VideoProjectCodec.encode(
            VideoProjectSnapshot(
                sequence = VideoSequence(
                    size = PixelSize(2, 2),
                    frameRate = FrameRate(30),
                    sources = persistentListOf(source("s")),
                    tracks = persistentListOf(
                        VideoTrack("main", kind = VideoTrackKind.Main)
                    )
                ),
                selectedClipId = null,
                position = EditorTime.Zero
            )
        )
        // Plant a forward-version field next to a real one.
        val withExtra = base.replace(
            "\"positionTicks\":0",
            "\"positionTicks\":0,\"futureField\":{\"nested\":[1,2,3]}"
        )
        assertEquals(
            VideoProjectCodec.decode(base),
            VideoProjectCodec.decode(withExtra)
        )
    }

    @Test
    fun `malformed inputs fail explicitly`() {
        assertFailsWith<IllegalArgumentException> {
            VideoProjectCodec.decode("")
        }
        assertFailsWith<IllegalArgumentException> {
            VideoProjectCodec.decode("not json")
        }
        assertFailsWith<IllegalArgumentException> {
            VideoProjectCodec.decode("[]")
        }
        assertFailsWith<IllegalArgumentException> {
            VideoProjectCodec.decode("""{"version":2}""")
        }
        // Missing required key.
        assertFailsWith<IllegalArgumentException> {
            VideoProjectCodec.decode("""{"version":1}""")
        }
        // Trailing garbage.
        assertFailsWith<IllegalArgumentException> {
            VideoProjectCodec.decode(
                VideoProjectCodec.encode(
                    VideoProjectSnapshot(
                        sequence = VideoSequence(
                            size = PixelSize(2, 2),
                            frameRate = FrameRate(30),
                            sources = persistentListOf(source("s")),
                            tracks = persistentListOf(
                                VideoTrack("main", kind = VideoTrackKind.Main)
                            )
                        ),
                        selectedClipId = null,
                        position = EditorTime.Zero
                    )
                ) + "  trailing"
            )
        }
        // A document that fails model invariants — two Main tracks.
        val badDocument = """
        {
          "version": 1,
          "size": {"width": 4, "height": 4},
          "frameRate": {"numerator": 30, "denominator": 1},
          "positionTicks": 0,
          "selectedClipId": null,
          "sources": [{
            "id": "s", "reference": "r", "width": 2, "height": 2,
            "durationTicks": 1000000, "frameRate": null,
            "hasAudio": false, "rotationDegrees": 0
          }],
          "tracks": [
            {"id": "m1", "name": "A", "kind": "Main", "visible": true,
             "locked": false, "clips": []},
            {"id": "m2", "name": "B", "kind": "Main", "visible": true,
             "locked": false, "clips": []}
          ]
        }
        """.trimIndent()
        assertFailsWith<IllegalArgumentException> {
            VideoProjectCodec.decode(badDocument)
        }
    }

    // ----- A1a: audio lanes, routing and the source-sound flag ---------

    private fun audioSequence() = richSequence().copy(
        audioSources = persistentListOf(
            MediaAsset.Audio(
                id = "song-1",
                reference = "content://audio/\"tune\"\\é",
                duration = us(6_000_000L),
                sampleRate = 44_100,
                channels = 1
            ),
            MediaAsset.Audio(
                id = "song-2",
                reference = "content://audio/voice",
                duration = us(3_000_000L),
                sampleRate = 48_000,
                channels = 2
            )
        ),
        audioTracks = persistentListOf(
            AudioTrack(
                id = "music",
                name = "Music bed",
                solo = true,
                gainDb = -3f,
                pan = 0.5f,
                locked = true,
                clips = persistentListOf(
                    AudioClip(
                        id = "ac-1",
                        sourceId = "song-1",
                        timelineStart = us(250_000L),
                        sourceRange = TimeRange(us(100_000L), us(2_000_000L)),
                        gainDb = -6.5f,
                        pan = -0.75f,
                        muted = true,
                        fadeIn = us(50_000L),
                        fadeOut = us(75_000L),
                        routing = AudioRouting(
                            leftGainDb = -12f,
                            rightGainDb = -3.25f,
                            linkChannels = false,
                            monoDownmix = true
                        )
                    )
                )
            ),
            AudioTrack(
                id = "voice",
                muted = true,
                clips = persistentListOf(
                    AudioClip(
                        id = "ac-2",
                        sourceId = "song-2",
                        timelineStart = us(1_000_000L),
                        sourceRange = TimeRange(us(0L), us(1_500_000L))
                    )
                )
            )
        ),
        sourceSoundMuted = true
    )

    @Test
    fun `audio sources tracks routing and the mute flag round-trip`() {
        val snapshot = VideoProjectSnapshot(
            sequence = audioSequence(),
            selectedClipId = "c-1",
            position = us(1_250_000L)
        )
        val decoded = VideoProjectCodec.decode(VideoProjectCodec.encode(snapshot))
        assertEquals(snapshot, decoded)
        // Determinism holds across the audio fields too.
        assertEquals(
            VideoProjectCodec.encode(snapshot),
            VideoProjectCodec.encode(decoded)
        )
    }

    @Test
    fun `a version 1 document decodes to empty audio lanes`() {
        // The pre-audio shape: no audioSources/audioTracks keys at all.
        val json = """
        {
          "version": 1,
          "size": {"width": 4, "height": 4},
          "frameRate": {"numerator": 30, "denominator": 1},
          "positionTicks": 0,
          "selectedClipId": null,
          "sources": [{
            "id": "s", "reference": "r", "width": 2, "height": 2,
            "durationTicks": 1000000, "frameRate": null,
            "hasAudio": false, "rotationDegrees": 0
          }],
          "tracks": [{
            "id": "main", "name": "Main", "kind": "Main",
            "visible": true, "locked": false, "clips": []
          }]
        }
        """.trimIndent()
        val decoded = VideoProjectCodec.decode(json).sequence
        assertEquals(0, decoded.audioSources.size)
        assertEquals(0, decoded.audioTracks.size)
        assertEquals(false, decoded.sourceSoundMuted)
        // Re-encoding writes the current version with the audio keys.
        val reencoded = VideoProjectCodec.encode(
            VideoProjectSnapshot(decoded, null, EditorTime.Zero)
        )
        assertTrue("\"version\":${VideoProjectCodec.VERSION}" in reencoded)
        assertTrue("\"audioSources\":[]" in reencoded)
        assertTrue("\"sourceSoundMuted\":false" in reencoded)
        // And the re-encoded form decodes back to the same document.
        assertEquals(
            decoded,
            VideoProjectCodec.decode(reencoded).sequence
        )
    }

    @Test
    fun `a version newer than the writer is rejected`() {
        val newer = VideoProjectCodec.VERSION + 1
        assertFailsWith<IllegalArgumentException> {
            VideoProjectCodec.decode(
                """{"version":$newer,"size":{"width":2,"height":2}}"""
            )
        }
        // Version zero never existed.
        assertFailsWith<IllegalArgumentException> {
            VideoProjectCodec.decode(
                """{"version":0,"size":{"width":2,"height":2}}"""
            )
        }
    }

    @Test
    fun `a malformed audio lane fails the model invariants`() {
        // The clip's source range overruns the declared audio source —
        // decode re-checks through the sequence's own validation.
        val json = """
        {
          "version": 2,
          "size": {"width": 4, "height": 4},
          "frameRate": {"numerator": 30, "denominator": 1},
          "positionTicks": 0,
          "selectedClipId": null,
          "sources": [{
            "id": "s", "reference": "r", "width": 2, "height": 2,
            "durationTicks": 1000000, "frameRate": null,
            "hasAudio": false, "rotationDegrees": 0
          }],
          "tracks": [],
          "audioSources": [{
            "id": "song", "reference": "r2",
            "durationTicks": 1000000, "sampleRate": 48000, "channels": 2
          }],
          "audioTracks": [{
            "id": "music", "name": "M", "muted": false, "solo": false,
            "gainDb": 0.0, "pan": 0.0, "locked": false,
            "clips": [{
              "id": "ac", "sourceId": "song",
              "timelineStartTicks": 0, "sourceStartTicks": 0,
              "sourceDurationTicks": 2000000
            }]
          }],
          "sourceSoundMuted": false
        }
        """.trimIndent()
        assertFailsWith<IllegalArgumentException> {
            VideoProjectCodec.decode(json)
        }
    }

    @Test
    fun `a restored selection that no longer exists is preserved for the caller to normalize`() {
        val snapshot = VideoProjectSnapshot(
            sequence = richSequence(),
            selectedClipId = "gone",
            position = us(1L)
        )
        // The codec does not editorialize — normalization is the session's
        // job (restoreProject validates the id against the sequence).
        val decoded = VideoProjectCodec.decode(VideoProjectCodec.encode(snapshot))
        assertEquals("gone", decoded.selectedClipId)
        assertNull(decoded.sequence.tracks.flatMap { it.clips }
            .firstOrNull { it.id == "gone" })
    }

    // ----- A1c: lane effects — EQ and sidechain ducking ----------------

    private fun effectedSequence(): VideoSequence {
        val base = audioSequence()
        return base.copy(
            audioTracks = persistentListOf(
                base.audioTracks[0].copy(
                    eq = AudioEq(
                        lowDb = -2f, midDb = 1.5f, highDb = 4f,
                        highPassHz = 90f, bypass = true
                    ),
                    ducking = AudioDucking(
                        enabled = true, triggerTrackId = "voice",
                        reductionDb = -18.5f, attackMs = 40f,
                        releaseMs = 750f
                    )
                ),
                base.audioTracks[1].copy(
                    eq = AudioEq(midDb = -3f)
                )
            )
        )
    }

    @Test
    fun `lane eq and ducking round-trip through version 4`() {
        val snapshot = VideoProjectSnapshot(
            sequence = effectedSequence(),
            selectedClipId = null,
            position = us(500_000L)
        )
        val decoded = VideoProjectCodec.decode(VideoProjectCodec.encode(snapshot))
        assertEquals(snapshot, decoded)
        val music = decoded.sequence.audioTracks[0]
        assertEquals(
            AudioEq(
                lowDb = -2f, midDb = 1.5f, highDb = 4f,
                highPassHz = 90f, bypass = true
            ),
            music.eq
        )
        assertEquals(
            AudioDucking(
                enabled = true, triggerTrackId = "voice",
                reductionDb = -18.5f, attackMs = 40f, releaseMs = 750f
            ),
            music.ducking
        )
        // Determinism across the new fields too.
        assertEquals(
            VideoProjectCodec.encode(snapshot),
            VideoProjectCodec.encode(decoded)
        )
    }

    @Test
    fun `a version 3 document decodes with neutral lane effects`() {
        // v3 lanes predate the inserts — no eq/ducking keys at all.
        val json = """
        {
          "version": 3,
          "size": {"width": 4, "height": 4},
          "frameRate": {"numerator": 30, "denominator": 1},
          "positionTicks": 0,
          "selectedClipId": null,
          "sources": [{
            "id": "s", "reference": "r", "width": 2, "height": 2,
            "durationTicks": 1000000, "frameRate": null,
            "hasAudio": true, "rotationDegrees": 0
          }],
          "tracks": [],
          "audioSources": [{
            "id": "song", "reference": "r2",
            "durationTicks": 1000000, "sampleRate": 48000, "channels": 2
          }],
          "audioTracks": [{
            "id": "music", "name": "M", "muted": false, "solo": false,
            "gainDb": -3.0, "pan": 0.25, "locked": false,
            "clips": [{
              "id": "ac", "sourceId": "song",
              "timelineStartTicks": 0, "sourceStartTicks": 0,
              "sourceDurationTicks": 500000
            }]
          }],
          "sourceSoundMuted": false,
          "masterGainDb": 0.0,
          "limiterEnabled": true
        }
        """.trimIndent()
        val decoded = VideoProjectCodec.decode(json).sequence
        val track = decoded.audioTracks.single()
        assertEquals(AudioEq(), track.eq)
        assertEquals(AudioDucking(), track.ducking)
        // Re-encoding upgrades in place — the effects keys appear.
        val reencoded = VideoProjectCodec.encode(
            VideoProjectSnapshot(decoded, null, EditorTime.Zero)
        )
        assertTrue("\"version\":${VideoProjectCodec.VERSION}" in reencoded)
        assertTrue("\"ducking\"" in reencoded)
        assertTrue("\"eq\"" in reencoded)
        assertEquals(
            decoded,
            VideoProjectCodec.decode(reencoded).sequence
        )
    }

    @Test
    fun `a duck pointing at a missing trigger fails decode`() {
        // The lane validates again inside the sequence — a dangling
        // sidechain reference is a malformed document, never neutralized.
        val json = """
        {
          "version": 4,
          "size": {"width": 4, "height": 4},
          "frameRate": {"numerator": 30, "denominator": 1},
          "positionTicks": 0,
          "selectedClipId": null,
          "sources": [{
            "id": "s", "reference": "r", "width": 2, "height": 2,
            "durationTicks": 1000000, "frameRate": null,
            "hasAudio": true, "rotationDegrees": 0
          }],
          "tracks": [],
          "audioSources": [{
            "id": "song", "reference": "r2",
            "durationTicks": 1000000, "sampleRate": 48000, "channels": 2
          }],
          "audioTracks": [{
            "id": "music", "name": "M", "muted": false, "solo": false,
            "gainDb": 0.0, "pan": 0.0, "locked": false,
            "eq": {"lowDb": 0.0, "midDb": 0.0, "highDb": 0.0,
                   "highPassHz": 0.0, "bypass": false},
            "ducking": {"enabled": true, "triggerTrackId": "ghost",
                        "reductionDb": -12.0,
                        "attackMs": 80.0, "releaseMs": 300.0},
            "clips": [{
              "id": "ac", "sourceId": "song",
              "timelineStartTicks": 0, "sourceStartTicks": 0,
              "sourceDurationTicks": 500000
            }]
          }],
          "sourceSoundMuted": false,
          "masterGainDb": 0.0,
          "limiterEnabled": true
        }
        """.trimIndent()
        assertFailsWith<IllegalArgumentException> {
            VideoProjectCodec.decode(json)
        }
    }
}
