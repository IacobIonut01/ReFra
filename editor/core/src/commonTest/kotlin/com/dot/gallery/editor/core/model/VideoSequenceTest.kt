package com.dot.gallery.editor.core.model

import com.dot.gallery.editor.core.time.EditorTime
import com.dot.gallery.editor.core.time.FrameRate
import com.dot.gallery.editor.core.time.TimeRange
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlinx.collections.immutable.persistentListOf

class VideoSequenceTest {

    private val rate = FrameRate(30)

    private fun video(id: String, duration: Long) = MediaAsset.Video(
        id = id,
        reference = "content://$id",
        size = PixelSize(16, 16),
        duration = EditorTime(duration),
        frameRate = rate,
        hasAudio = false
    )

    private fun clip(
        id: String,
        sourceId: String,
        timelineStart: Long,
        sourceStart: Long,
        duration: Long
    ) = VideoClip(
        id = id,
        sourceId = sourceId,
        timelineStart = EditorTime(timelineStart),
        sourceRange = TimeRange(EditorTime(sourceStart), EditorTime(duration))
    )

    private fun sequence(vararg tracks: VideoTrack) = VideoSequence(
        size = PixelSize(16, 16),
        frameRate = rate,
        sources = persistentListOf(video("src", 200)),
        tracks = persistentListOf(*tracks)
    )

    @Test
    fun `valid sequence constructs`() {
        sequence(VideoTrack("t", persistentListOf(clip("c", "src", 0, 0, 100))))
    }

    @Test
    fun `clip referencing unknown source rejected`() {
        assertFailsWith<IllegalArgumentException> {
            sequence(VideoTrack("t", persistentListOf(clip("c", "ghost", 0, 0, 10))))
        }
    }

    @Test
    fun `clip range beyond source duration rejected`() {
        assertFailsWith<IllegalArgumentException> {
            sequence(VideoTrack("t", persistentListOf(clip("c", "src", 0, 150, 60))))
        }
        assertFailsWith<IllegalArgumentException> {
            sequence(VideoTrack("t", persistentListOf(clip("c", "src", 0, 0, 300))))
        }
    }

    @Test
    fun `ids are unique across sources tracks and clips`() {
        assertFailsWith<IllegalArgumentException> {
            sequence(VideoTrack("src", persistentListOf(clip("c", "src", 0, 0, 10))))
        }
        assertFailsWith<IllegalArgumentException> {
            sequence(VideoTrack("t", persistentListOf(clip("src", "src", 0, 0, 10))))
        }
        assertFailsWith<IllegalArgumentException> {
            sequence(VideoTrack("t", persistentListOf(clip("t", "src", 0, 0, 10))))
        }
    }

    @Test
    fun `splitClip keeps left identity and remaps right source in point`() {
        val adjustments = ColorAdjustments(exposureStops = 1f)
        val original = clip("c", "src", 10, 40, 100).copy(adjustments = adjustments)
        val seq = sequence(VideoTrack("t", persistentListOf(original)))

        val split = seq.splitClip("t", "c", EditorTime(50), "c-right")

        val track = split.tracks.single()
        assertEquals(2, track.clips.size)
        val left = track.clips[0]
        val right = track.clips[1]
        assertEquals("c", left.id)
        assertEquals(TimeRange(EditorTime(40), EditorTime(40)), left.sourceRange)
        assertEquals(EditorTime(10), left.timelineStart)
        assertEquals("c-right", right.id)
        assertEquals(TimeRange(EditorTime(80), EditorTime(60)), right.sourceRange)
        assertEquals(EditorTime(50), right.timelineStart)
        // Contiguous on the timeline and in the source.
        assertEquals(left.timelineRange.endExclusive, right.timelineStart)
        assertEquals(left.sourceRange.endExclusive, right.sourceRange.start)
        assertEquals(adjustments, left.adjustments)
        assertEquals(adjustments, right.adjustments)
        // The input sequence is untouched.
        assertEquals(persistentListOf(original), seq.tracks.single().clips)
    }

    @Test
    fun `splitClip rejects boundary points unknown ids and duplicate new id`() {
        val seq = sequence(
            VideoTrack(
                "t",
                persistentListOf(
                    clip("c", "src", 0, 0, 100),
                    clip("other", "src", 200, 100, 50)
                )
            )
        )
        val before = seq

        assertFailsWith<IllegalArgumentException> {
            seq.splitClip("t", "c", EditorTime(0), "x")
        }
        assertFailsWith<IllegalArgumentException> {
            seq.splitClip("t", "c", EditorTime(100), "x")
        }
        assertFailsWith<IllegalArgumentException> {
            seq.splitClip("missing", "c", EditorTime(10), "x")
        }
        assertFailsWith<IllegalArgumentException> {
            seq.splitClip("t", "missing", EditorTime(10), "x")
        }
        assertFailsWith<IllegalArgumentException> {
            seq.splitClip("t", "c", EditorTime(10), "other")
        }

        assertEquals(before, seq)
    }

    @Test
    fun `trimClip anchors timeline start and follows source duration`() {
        val seq = sequence(
            VideoTrack(
                "t",
                persistentListOf(
                    clip("c", "src", 20, 50, 40),
                    clip("next", "src", 100, 0, 10)
                )
            )
        )

        // Shrink: timeline extent follows the new source duration.
        val shrunk = seq.trimClip("t", "c", TimeRange(EditorTime(50), EditorTime(25)))
        val shrunkClip = shrunk.tracks[0].clips[0]
        assertEquals(EditorTime(20), shrunkClip.timelineStart)
        assertEquals(TimeRange(EditorTime(20), EditorTime(25)), shrunkClip.timelineRange)
        // Extend into the free gap up to the next clip.
        val grown = seq.trimClip("t", "c", TimeRange(EditorTime(60), EditorTime(80)))
        assertEquals(TimeRange(EditorTime(20), EditorTime(80)), grown.tracks[0].clips[0].timelineRange)
        // Unrelated clips keep their identity.
        assertSame(seq.tracks[0].clips[1], shrunk.tracks[0].clips[1])
        assertSame(seq.tracks[0].clips[1], grown.tracks[0].clips[1])
    }

    @Test
    fun `trimClip rejects out-of-source and overlapping trims`() {
        val seq = sequence(
            VideoTrack(
                "t",
                persistentListOf(
                    clip("c", "src", 0, 0, 40),
                    clip("next", "src", 50, 100, 10)
                )
            )
        )
        val before = seq

        // Beyond the source's 200-tick duration.
        assertFailsWith<IllegalArgumentException> {
            seq.trimClip("t", "c", TimeRange(EditorTime(150), EditorTime(60)))
        }
        // Extending past the following clip's timeline start.
        assertFailsWith<IllegalArgumentException> {
            seq.trimClip("t", "c", TimeRange(EditorTime(0), EditorTime(60)))
        }
        assertFailsWith<IllegalArgumentException> {
            seq.trimClip("t", "missing", TimeRange(EditorTime(0), EditorTime(10)))
        }
        assertFailsWith<IllegalArgumentException> {
            seq.trimClip("missing", "c", TimeRange(EditorTime(0), EditorTime(10)))
        }

        assertEquals(before, seq)
    }

    @Test
    fun `duration is the furthest clip end or zero`() {
        val empty = sequence(VideoTrack("t"))
        assertEquals(EditorTime.Zero, empty.duration)

        val seq = sequence(
            VideoTrack(
                "a",
                persistentListOf(clip("c1", "src", 0, 0, 40))
            ),
            VideoTrack(
                "b",
                kind = VideoTrackKind.Overlay,
                clips = persistentListOf(
                    clip("c2", "src", 0, 0, 10),
                    clip("c3", "src", 10, 10, 30)
                )
            )
        )
        assertEquals(EditorTime(40), seq.duration)
    }

    @Test
    fun `rippleTrimClip repacks the track consecutively from zero`() {
        val adjustments = ColorAdjustments(contrast = 2f)
        val seq = sequence(
            VideoTrack(
                "t",
                persistentListOf(
                    clip("a", "src", 0, 0, 30),
                    clip("b", "src", 30, 40, 50).copy(adjustments = adjustments),
                    clip("c", "src", 80, 100, 20)
                )
            )
        )

        val trimmed = seq.rippleTrimClip("t", "b", TimeRange(EditorTime(40), EditorTime(10)))

        val clips = trimmed.tracks.single().clips
        assertEquals(3, clips.size)
        // Order, ids and adjustments preserved; the gap closes.
        assertEquals(listOf("a", "b", "c"), clips.map { it.id })
        assertEquals(EditorTime(0), clips[0].timelineStart)
        assertEquals(EditorTime(30), clips[1].timelineStart)
        assertEquals(EditorTime(40), clips[2].timelineStart)
        assertEquals(TimeRange(EditorTime(40), EditorTime(10)), clips[1].sourceRange)
        assertEquals(adjustments, clips[1].adjustments)
        assertEquals(EditorTime(60), trimmed.duration)
        // Source ranges of the untouched clips are unchanged.
        assertEquals(TimeRange(EditorTime(0), EditorTime(30)), clips[0].sourceRange)
        assertEquals(TimeRange(EditorTime(100), EditorTime(20)), clips[2].sourceRange)
        // Original sequence untouched.
        assertEquals(EditorTime(100), seq.duration)
    }

    @Test
    fun `rippleTrimClip rejects ids and out-of-source ranges unchanged`() {
        val seq = sequence(VideoTrack("t", persistentListOf(clip("c", "src", 0, 0, 40))))
        val before = seq

        assertFailsWith<IllegalArgumentException> {
            seq.rippleTrimClip("t", "missing", TimeRange(EditorTime(0), EditorTime(10)))
        }
        assertFailsWith<IllegalArgumentException> {
            seq.rippleTrimClip("missing", "c", TimeRange(EditorTime(0), EditorTime(10)))
        }
        // Past the 200-tick source duration — caught by global validation.
        assertFailsWith<IllegalArgumentException> {
            seq.rippleTrimClip("t", "c", TimeRange(EditorTime(150), EditorTime(60)))
        }

        assertEquals(before, seq)
    }

    @Test
    fun `removeClip closes the gap and empties the last clip cleanly`() {
        val seq = sequence(
            VideoTrack(
                "t",
                persistentListOf(
                    clip("a", "src", 0, 0, 30),
                    clip("b", "src", 30, 40, 50),
                    clip("c", "src", 80, 100, 20)
                )
            )
        )

        val removed = seq.removeClip("t", "b")
        val clips = removed.tracks.single().clips
        assertEquals(listOf("a", "c"), clips.map { it.id })
        assertEquals(EditorTime(0), clips[0].timelineStart)
        assertEquals(EditorTime(30), clips[1].timelineStart)
        assertEquals(EditorTime(50), removed.duration)

        val emptied = seq.removeClip("t", "a").removeClip("t", "b").removeClip("t", "c")
        assertEquals(0, emptied.tracks.single().clips.size)
        assertEquals(EditorTime.Zero, emptied.duration)

        val before = seq
        assertFailsWith<IllegalArgumentException> { seq.removeClip("t", "missing") }
        assertFailsWith<IllegalArgumentException> { seq.removeClip("missing", "a") }
        assertEquals(before, seq)
    }

    @Test
    fun `moveClip reorders and repacks around the target index`() {
        val seq = sequence(
            VideoTrack(
                "t",
                persistentListOf(
                    clip("a", "src", 0, 0, 30),
                    clip("b", "src", 30, 40, 50),
                    clip("c", "src", 80, 100, 20)
                )
            )
        )

        val moved = seq.moveClip("t", "a", 2)
        val clips = moved.tracks.single().clips
        assertEquals(listOf("b", "c", "a"), clips.map { it.id })
        // Repacked from zero in the new order.
        assertEquals(EditorTime(0), clips[0].timelineStart)
        assertEquals(EditorTime(50), clips[1].timelineStart)
        assertEquals(EditorTime(70), clips[2].timelineStart)
        // Same-index is a no-op returning the same sequence.
        assertEquals(seq, seq.moveClip("t", "a", 0))

        val before = seq
        assertFailsWith<IllegalArgumentException> { seq.moveClip("t", "a", -1) }
        assertFailsWith<IllegalArgumentException> { seq.moveClip("t", "a", 3) }
        assertFailsWith<IllegalArgumentException> { seq.moveClip("t", "missing", 0) }
        assertFailsWith<IllegalArgumentException> { seq.moveClip("missing", "a", 0) }
        assertEquals(before, seq)
    }

    // ----- V1a: tracks, kinds and compositing order ----------------------

    @Test
    fun `at most one Main track and no Audio lane yet`() {
        assertFailsWith<IllegalArgumentException> {
            sequence(
                VideoTrack("m1", kind = VideoTrackKind.Main),
                VideoTrack("m2", kind = VideoTrackKind.Main)
            )
        }
        assertFailsWith<IllegalArgumentException> {
            sequence(VideoTrack("a", kind = VideoTrackKind.Audio))
        }
        // Overlay and Title lanes are legal (title renders later).
        sequence(
            VideoTrack("m", kind = VideoTrackKind.Main),
            VideoTrack("o", kind = VideoTrackKind.Overlay),
            VideoTrack("t", kind = VideoTrackKind.Title)
        )
    }

    @Test
    fun `track fields default to the legacy shape`() {
        val track = VideoTrack("t", persistentListOf(clip("c", "src", 0, 0, 10)))
        assertEquals("t", track.name)
        assertEquals(VideoTrackKind.Main, track.kind)
        assertEquals(true, track.visible)
        assertEquals(false, track.locked)
    }

    @Test
    fun `activeVisualClips paints Main first then overlays in track order`() {
        val seq = VideoSequence(
            size = PixelSize(16, 16),
            frameRate = rate,
            sources = persistentListOf(video("src", 200), video("o", 200)),
            tracks = persistentListOf(
                VideoTrack(
                    "main", kind = VideoTrackKind.Main,
                    clips = persistentListOf(clip("m", "src", 0, 0, 100))
                ),
                VideoTrack(
                    "top", kind = VideoTrackKind.Overlay,
                    clips = persistentListOf(clip("hi", "o", 0, 0, 100))
                ),
                VideoTrack(
                    "middle", kind = VideoTrackKind.Overlay,
                    clips = persistentListOf(clip("lo", "o", 0, 0, 100))
                ),
                VideoTrack(
                    "hidden", kind = VideoTrackKind.Overlay, visible = false,
                    clips = persistentListOf(clip("off", "o", 0, 0, 100))
                ),
                VideoTrack(
                    "title", kind = VideoTrackKind.Title,
                    clips = persistentListOf(clip("tt", "o", 0, 0, 100))
                )
            )
        )
        // Track order, not lane height: "middle" is listed after "top", so
        // it paints above it — callers lay out overlay tracks top-first.
        assertEquals(
            listOf("m", "hi", "lo"),
            seq.activeVisualClips(EditorTime(50)).map { it.id }
        )
        // Outside any clip's range the answer is empty.
        assertEquals(emptyList(), seq.activeVisualClips(EditorTime(500)))
        // An invisible clip contributes nothing.
        val seqHiddenClip = VideoSequence(
            size = PixelSize(16, 16),
            frameRate = rate,
            sources = persistentListOf(video("src", 200)),
            tracks = persistentListOf(
                VideoTrack(
                    "main", kind = VideoTrackKind.Main,
                    clips = persistentListOf(
                        clip("m", "src", 0, 0, 100).copy(visible = false)
                    )
                )
            )
        )
        assertEquals(emptyList(), seqHiddenClip.activeVisualClips(EditorTime(50)))
    }

    @Test
    fun `addSource and addTrack validate on copy`() {
        val seq = sequence(VideoTrack("main", kind = VideoTrackKind.Main))
        val withSource = seq.addSource(video("extra", 100))
        assertEquals(listOf("src", "extra"), withSource.sources.map { it.id })
        val withTrack = withSource.addTrack(
            VideoTrack("ov", kind = VideoTrackKind.Overlay)
        )
        assertEquals(2, withTrack.tracks.size)
        // A second Main lane fails on the copy, original untouched.
        assertFailsWith<IllegalArgumentException> {
            seq.addTrack(VideoTrack("m2", kind = VideoTrackKind.Main))
        }
        assertEquals(1, seq.tracks.size)
        // A duplicate id anywhere in the shared space is rejected.
        assertFailsWith<IllegalArgumentException> {
            seq.addSource(video("main", 50))
        }
    }

    @Test
    fun `insertClip without ripple places exactly and rejects overlap`() {
        val seq = VideoSequence(
            size = PixelSize(16, 16),
            frameRate = rate,
            sources = persistentListOf(video("src", 500), video("o", 500)),
            tracks = persistentListOf(
                VideoTrack(
                    "ov", kind = VideoTrackKind.Overlay,
                    clips = persistentListOf(clip("a", "src", 0, 0, 30))
                )
            )
        )
        val placed = seq.insertClip(
            "ov", clip("b", "o", 50, 0, 20), ripple = false
        )
        assertEquals(
            listOf(0L, 50L),
            placed.tracks[0].clips.map { it.timelineStart.ticks }
        )
        // Sorted insertion before the existing clip.
        val before = seq.insertClip(
            "ov", clip("b", "o", 35, 0, 10), ripple = false
        )
        assertEquals(listOf("a", "b"), before.tracks[0].clips.map { it.id })
        // Overlapping placements are rejected — the track invariant holds.
        assertFailsWith<IllegalArgumentException> {
            seq.insertClip("ov", clip("b", "o", 20, 0, 20), ripple = false)
        }
        assertFailsWith<IllegalArgumentException> {
            seq.insertClip("missing", clip("b", "o", 0, 0, 10), ripple = false)
        }
    }

    @Test
    fun `insertClip with ripple shifts later clips and needs a boundary`() {
        val seq = sequence(
            VideoTrack(
                "t",
                persistentListOf(
                    clip("a", "src", 0, 0, 30),
                    clip("b", "src", 30, 40, 20)
                )
            )
        )
        // Insert at the packed boundary: b moves right by the new duration.
        val inserted = seq.insertClip(
            "t", clip("x", "src", 30, 0, 10), ripple = true
        )
        val clips = inserted.tracks[0].clips
        assertEquals(listOf("a", "x", "b"), clips.map { it.id })
        assertEquals(
            listOf(0L, 30L, 40L),
            clips.map { it.timelineStart.ticks }
        )
        // A straddling insertion point is rejected — split first.
        assertFailsWith<IllegalArgumentException> {
            seq.insertClip("t", clip("x", "src", 15, 0, 10), ripple = true)
        }
    }

    @Test
    fun `duplicateClip lands after the original, rippling Main lanes`() {
        val seq = VideoSequence(
            size = PixelSize(16, 16),
            frameRate = rate,
            sources = persistentListOf(video("src", 500), video("o", 500)),
            tracks = persistentListOf(
                VideoTrack(
                    "main", kind = VideoTrackKind.Main,
                    clips = persistentListOf(
                        clip("a", "src", 0, 0, 30),
                        clip("b", "src", 30, 40, 20)
                    )
                ),
                VideoTrack(
                    "ov", kind = VideoTrackKind.Overlay,
                    clips = persistentListOf(
                        clip("o1", "o", 0, 0, 30),
                        clip("o2", "o", 60, 0, 30)
                    )
                ),
                VideoTrack(
                    "packed", kind = VideoTrackKind.Overlay,
                    clips = persistentListOf(
                        clip("f1", "o", 0, 0, 30),
                        clip("f2", "o", 30, 0, 30)
                    )
                )
            )
        )
        // Main: duplicate of "a" ripples the copy + later clips right.
        val dupMain = seq.duplicateClip("main", "a", "a2")
        val main = dupMain.tracks[0].clips
        assertEquals(listOf("a", "a2", "b"), main.map { it.id })
        assertEquals(listOf(0L, 30L, 60L), main.map { it.timelineStart.ticks })
        // Overlay: the free slot after the original takes the copy.
        val dupFree = seq.duplicateClip("ov", "o1", "o1c")
        val ov = dupFree.tracks[1].clips
        assertEquals(listOf("o1", "o1c", "o2"), ov.map { it.id })
        assertEquals(30L, ov[1].timelineStart.ticks)
        // A copy that would overlap its neighbour is rejected — the
        // original document is untouched.
        assertFailsWith<IllegalArgumentException> {
            seq.duplicateClip("packed", "f1", "f1c")
        }
        assertFailsWith<IllegalArgumentException> {
            seq.duplicateClip("missing", "a", "x")
        }
        assertFailsWith<IllegalArgumentException> {
            seq.duplicateClip("main", "missing", "x")
        }
    }

    @Test
    fun `removeSource refuses a still-referenced source`() {
        val seq = VideoSequence(
            size = PixelSize(16, 16),
            frameRate = rate,
            sources = persistentListOf(video("src", 200), video("spare", 200)),
            tracks = persistentListOf(
                VideoTrack(
                    "t",
                    persistentListOf(clip("c", "src", 0, 0, 50))
                )
            )
        )
        val removed = seq.removeSource("spare")
        assertEquals(listOf("src"), removed.sources.map { it.id })
        assertFailsWith<IllegalArgumentException> { seq.removeSource("src") }
        assertFailsWith<IllegalArgumentException> { seq.removeSource("ghost") }
    }

    // ----- A1a: audio sources, tracks and mix model ----------------------

    private fun audio(id: String, duration: Long) = MediaAsset.Audio(
        id = id,
        reference = "content://audio/$id",
        duration = EditorTime(duration),
        sampleRate = 48_000,
        channels = 2
    )

    private fun aclip(
        id: String,
        sourceId: String,
        timelineStart: Long,
        sourceStart: Long,
        duration: Long
    ) = AudioClip(
        id = id,
        sourceId = sourceId,
        timelineStart = EditorTime(timelineStart),
        sourceRange = TimeRange(EditorTime(sourceStart), EditorTime(duration))
    )

    private fun audioSequence(
        vararg tracks: AudioTrack,
        sourceSoundMuted: Boolean = false
    ) = VideoSequence(
        size = PixelSize(16, 16),
        frameRate = rate,
        sources = persistentListOf(video("src", 200)),
        tracks = persistentListOf(
            VideoTrack("main", persistentListOf(clip("v", "src", 0, 0, 100)))
        ),
        audioSources = persistentListOf(audio("song", 200)),
        audioTracks = persistentListOf(*tracks),
        sourceSoundMuted = sourceSoundMuted
    )

    @Test
    fun `audio clip ids share the global id space`() {
        assertFailsWith<IllegalArgumentException> {
            audioSequence(
                AudioTrack("song", clips = persistentListOf())
            )
        }
        assertFailsWith<IllegalArgumentException> {
            audioSequence(
                AudioTrack(
                    "at",
                    clips = persistentListOf(aclip("src", "song", 0, 0, 10))
                )
            )
        }
        // An audio clip cannot point at a video source.
        assertFailsWith<IllegalArgumentException> {
            audioSequence(
                AudioTrack(
                    "at",
                    clips = persistentListOf(aclip("ac", "src", 0, 0, 10))
                )
            )
        }
        // A video clip cannot point at an audio source.
        assertFailsWith<IllegalArgumentException> {
            VideoSequence(
                size = PixelSize(16, 16),
                frameRate = rate,
                sources = persistentListOf(video("src", 200)),
                tracks = persistentListOf(
                    VideoTrack(
                        "main",
                        persistentListOf(clip("v", "song", 0, 0, 10))
                    )
                ),
                audioSources = persistentListOf(audio("song", 200))
            )
        }
    }

    @Test
    fun `audio clip range and recipe bounds are validated`() {
        assertFailsWith<IllegalArgumentException> {
            audioSequence(
                AudioTrack(
                    "at",
                    clips = persistentListOf(aclip("ac", "song", 0, 150, 60))
                )
            )
        }
        assertFailsWith<IllegalArgumentException> {
            aclip("ac", "song", 0, 0, 10).copy(gainDb = 13f)
        }
        assertFailsWith<IllegalArgumentException> {
            aclip("ac", "song", 0, 0, 10).copy(gainDb = Float.NaN)
        }
        assertFailsWith<IllegalArgumentException> {
            aclip("ac", "song", 0, 0, 10).copy(pan = -1.5f)
        }
        assertFailsWith<IllegalArgumentException> {
            aclip("ac", "song", 0, 0, 10).copy(
                fadeIn = EditorTime(11)
            )
        }
        assertFailsWith<IllegalArgumentException> {
            aclip("ac", "song", 0, 0, 10).copy(
                fadeOut = EditorTime(-1)
            )
        }
        assertFailsWith<IllegalArgumentException> {
            AudioRouting(leftGainDb = -61f)
        }
        // Channels are restricted to mono/stereo at the asset level.
        assertFailsWith<IllegalArgumentException> {
            MediaAsset.Audio("a", "ref", EditorTime(10), 48_000, 6)
        }
        // Track-level gain/pan share the bounds.
        assertFailsWith<IllegalArgumentException> {
            AudioTrack("at", gainDb = 20f)
        }
        assertFailsWith<IllegalArgumentException> {
            AudioTrack("at", pan = 2f)
        }
        // Overlapping clips on one lane are rejected by the track.
        assertFailsWith<IllegalArgumentException> {
            AudioTrack(
                "at",
                clips = persistentListOf(
                    aclip("a1", "song", 0, 0, 30),
                    aclip("a2", "song", 20, 0, 30)
                )
            )
        }
    }

    @Test
    fun `duration follows the furthest clip end across audio lanes`() {
        val seq = audioSequence(
            AudioTrack(
                "at",
                clips = persistentListOf(aclip("ac", "song", 0, 0, 180))
            )
        )
        // The video programme is 100 ticks; the song extends the document.
        assertEquals(EditorTime(180), seq.duration)
        val shorter = audioSequence(
            AudioTrack(
                "at",
                clips = persistentListOf(aclip("ac", "song", 0, 0, 40))
            )
        )
        assertEquals(EditorTime(100), shorter.duration)
    }

    @Test
    fun `audio insert move trim and remove keep lanes sorted`() {
        val seq = audioSequence(
            AudioTrack(
                "at",
                clips = persistentListOf(aclip("a1", "song", 0, 0, 30))
            )
        )
        // Insert lands sorted; overlap is rejected by the track invariant.
        val inserted = seq.insertAudioClip("at", aclip("a2", "song", 60, 0, 20))
        assertEquals(
            listOf("a1", "a2"),
            inserted.audioTracks[0].clips.map { it.id }
        )
        assertFailsWith<IllegalArgumentException> {
            seq.insertAudioClip("at", aclip("a2", "song", 20, 0, 20))
        }
        // Move re-sorts and keeps the clip's source range.
        val moved = inserted.moveAudioClip("at", "a2", EditorTime(35))
        assertEquals(
            listOf("a1", "a2"),
            moved.audioTracks[0].clips.map { it.id }
        )
        assertEquals(
            EditorTime(35),
            moved.audioTracks[0].clips[1].timelineStart
        )
        // Trim anchors the timeline start and follows the range duration.
        val trimmed = inserted.trimAudioClip(
            "at", "a2", TimeRange(EditorTime(10), EditorTime(15))
        )
        val clip2 = trimmed.audioTracks[0].clips[1]
        assertEquals(EditorTime(60), clip2.timelineStart)
        assertEquals(EditorTime(15), clip2.timelineRange.duration)
        // Remove leaves a gap — audio lanes are not repacked.
        val removed = inserted.removeAudioClip("at", "a1")
        assertEquals(
            EditorTime(60),
            removed.audioTracks[0].clips[0].timelineStart
        )
        assertFailsWith<IllegalArgumentException> {
            seq.removeAudioClip("at", "missing")
        }
        // Sources only leave when unreferenced.
        assertFailsWith<IllegalArgumentException> {
            inserted.removeAudioSource("song")
        }
        val freed = inserted.removeAudioClip("at", "a1")
            .removeAudioClip("at", "a2")
        assertEquals(0, freed.removeAudioSource("song").audioSources.size)
    }

    @Test
    fun `audio clips move across lanes and keep their recipe`() {
        val seq = audioSequence(
            AudioTrack(
                "a",
                clips = persistentListOf(aclip("a1", "song", 0, 10, 30).copy(gainDb = -6f))
            ),
            AudioTrack("b", clips = persistentListOf(aclip("b1", "song", 50, 0, 20)))
        )
        val moved = seq.moveAudioClipToTrack("a", "a1", "b", EditorTime(5))
        assertTrue(moved.audioTracks[0].clips.isEmpty())
        assertEquals(listOf("a1", "b1"), moved.audioTracks[1].clips.map { it.id })
        val landed = moved.audioTracks[1].clips[0]
        assertEquals(EditorTime(5), landed.timelineStart)
        assertEquals(TimeRange(EditorTime(10), EditorTime(30)), landed.sourceRange)
        assertEquals(-6f, landed.gainDb)
        // Same lane is a plain move; an overlap on the target is rejected.
        assertEquals(
            EditorTime(70),
            seq.moveAudioClipToTrack("a", "a1", "a", EditorTime(70))
                .audioTracks[0].clips[0].timelineStart
        )
        assertFailsWith<IllegalArgumentException> {
            seq.moveAudioClipToTrack("a", "a1", "b", EditorTime(40))
        }
        assertFailsWith<IllegalArgumentException> {
            seq.moveAudioClipToTrack("a", "a1", "ghost", EditorTime(0))
        }
    }

    @Test
    fun `audio lanes reorder and remove with their sidechains and sources`() {
        val base = audioSequence(
            AudioTrack("a", clips = persistentListOf(aclip("a1", "song", 0, 0, 30))),
            AudioTrack(
                "b",
                ducking = AudioDucking(enabled = true, triggerTrackId = "a")
            ),
            AudioTrack("c")
        )
        val reordered = base.moveAudioTrack("c", 0)
        assertEquals(listOf("c", "a", "b"), reordered.audioTracks.map { it.id })
        assertSame(base, base.moveAudioTrack("a", 0))
        assertFailsWith<IllegalArgumentException> { base.moveAudioTrack("a", 3) }

        val removed = base.removeAudioTrack("a")
        assertEquals(listOf("b", "c"), removed.audioTracks.map { it.id })
        assertEquals(false, removed.audioTracks[0].ducking.enabled)
        // The song was only on the removed lane, so it leaves too.
        assertTrue(removed.audioSources.isEmpty())
        assertEquals(1, base.removeAudioTrack("c").audioSources.size)
    }

    @Test
    fun `overlay clips move between overlay lanes but never touch Main`() {
        val seq = sequence(
            VideoTrack("main", persistentListOf(clip("m", "src", 0, 0, 100))),
            VideoTrack(
                "o1", persistentListOf(clip("x", "src", 10, 0, 20)),
                kind = VideoTrackKind.Overlay
            ),
            VideoTrack(
                "o2", persistentListOf(clip("y", "src", 40, 0, 20)),
                kind = VideoTrackKind.Overlay
            )
        )
        val moved = seq.moveClipToTrack("o1", "x", "o2", EditorTime(0))
        assertTrue(moved.tracks[1].clips.isEmpty())
        assertEquals(listOf("x", "y"), moved.tracks[2].clips.map { it.id })
        assertEquals(
            EditorTime(70),
            seq.moveClipToTrack("o1", "x", "o1", EditorTime(70)).tracks[1].clips[0].timelineStart
        )
        assertFailsWith<IllegalArgumentException> {
            seq.moveClipToTrack("o1", "x", "o2", EditorTime(45))
        }
        assertFailsWith<IllegalArgumentException> {
            seq.moveClipToTrack("o1", "x", "main", EditorTime(0))
        }
        assertFailsWith<IllegalArgumentException> {
            seq.moveClipToTrack("main", "m", "o1", EditorTime(50))
        }
    }

    @Test
    fun `overlay lanes swap and remove while Main stays put`() {
        val seq = sequence(
            VideoTrack("main", persistentListOf(clip("m", "src", 0, 0, 100))),
            VideoTrack("o1", kind = VideoTrackKind.Overlay),
            VideoTrack(
                "o2", persistentListOf(clip("y", "src", 40, 0, 20)),
                kind = VideoTrackKind.Overlay
            )
        )
        assertEquals(
            listOf("main", "o2", "o1"),
            seq.swapTracks("o1", "o2").tracks.map { it.id }
        )
        assertFailsWith<IllegalArgumentException> { seq.swapTracks("main", "o1") }
        assertEquals(listOf("main", "o2"), seq.removeTrack("o1").tracks.map { it.id })
        // The source is still on Main, so it stays.
        assertEquals(1, seq.removeTrack("o2").sources.size)
        assertFailsWith<IllegalArgumentException> { seq.removeTrack("main") }
    }

    @Test
    fun `audio recipe and header edits freeze placement`() {
        val seq = audioSequence(
            AudioTrack(
                "at",
                clips = persistentListOf(aclip("a1", "song", 10, 0, 30))
            )
        )
        val gained = seq.updateAudioClip("at", "a1") {
            it.copy(gainDb = -6f, pan = 0.5f)
        }
        assertEquals(-6f, gained.audioTracks[0].clips[0].gainDb)
        assertFailsWith<IllegalArgumentException> {
            seq.updateAudioClip("at", "a1") {
                it.copy(timelineStart = EditorTime(0))
            }
        }
        val soloed = seq.updateAudioTrack("at") { it.copy(solo = true) }
        assertEquals(true, soloed.audioTracks[0].solo)
        assertFailsWith<IllegalArgumentException> {
            seq.updateAudioTrack("at") { it.copy(id = "renamed-id") }
        }
    }

    @Test
    fun `activeAudioClips returns track and clip for every covered lane`() {
        val seq = audioSequence(
            AudioTrack(
                "muted-lane", muted = true,
                clips = persistentListOf(aclip("a1", "song", 0, 0, 30))
            ),
            AudioTrack(
                "live",
                clips = persistentListOf(aclip("a2", "song", 10, 0, 30))
            )
        )
        val active = seq.activeAudioClips(EditorTime(20))
        // Muted lanes still report — mute semantics live in the mixer.
        assertEquals(
            listOf("a1", "a2"),
            active.map { it.second.id }
        )
        assertEquals(
            listOf("muted-lane", "live"),
            active.map { it.first.id }
        )
        // Past the last clip's endExclusive nothing reports.
        assertEquals(emptyList(), seq.activeAudioClips(EditorTime(40)))
    }

    // ----- A1c: lane effects — EQ and sidechain ducking ----------------

    @Test
    fun `lane effect bounds are validated`() {
        // Ducking recipe bounds.
        assertFailsWith<IllegalArgumentException> {
            AudioDucking(reductionDb = -61f)
        }
        assertFailsWith<IllegalArgumentException> {
            AudioDucking(reductionDb = 1f)
        }
        assertFailsWith<IllegalArgumentException> {
            AudioDucking(attackMs = 0f)
        }
        assertFailsWith<IllegalArgumentException> {
            AudioDucking(attackMs = 2_001f)
        }
        assertFailsWith<IllegalArgumentException> {
            AudioDucking(releaseMs = Float.NaN)
        }
        assertFailsWith<IllegalArgumentException> {
            AudioDucking(releaseMs = 5_001f)
        }
        // EQ band and corner bounds.
        assertFailsWith<IllegalArgumentException> {
            AudioEq(lowDb = 13f)
        }
        assertFailsWith<IllegalArgumentException> {
            AudioEq(midDb = -13f)
        }
        assertFailsWith<IllegalArgumentException> {
            AudioEq(highDb = Float.NaN)
        }
        assertFailsWith<IllegalArgumentException> {
            AudioEq(highPassHz = -1f)
        }
        assertFailsWith<IllegalArgumentException> {
            AudioEq(highPassHz = 501f)
        }
    }

    @Test
    fun `an enabled duck requires another real lane as its trigger`() {
        fun seq(ducking: AudioDucking) = audioSequence(
            AudioTrack(
                "music",
                clips = persistentListOf(aclip("a1", "song", 0, 0, 30)),
                ducking = ducking
            ),
            AudioTrack(
                "voice",
                clips = persistentListOf(aclip("a2", "song", 40, 30, 30))
            )
        )
        // A disabled duck ignores the trigger field entirely — it is the
        // neutral "off" value, validated or not.
        seq(AudioDucking(enabled = false, triggerTrackId = "ghost"))
        // Enabled against a real other lane is the working recipe.
        seq(AudioDucking(enabled = true, triggerTrackId = "voice"))
        // An unknown trigger cannot produce the sidechain — reject it.
        assertFailsWith<IllegalArgumentException> {
            seq(AudioDucking(enabled = true, triggerTrackId = "ghost"))
        }
        // A lane can never duck to its own signal.
        assertFailsWith<IllegalArgumentException> {
            seq(AudioDucking(enabled = true, triggerTrackId = "music"))
        }
    }

    @Test
    fun `lane effect edits keep the placement invariants`() {
        val seq = audioSequence(
            AudioTrack(
                "music",
                clips = persistentListOf(aclip("a1", "song", 0, 0, 30))
            ),
            AudioTrack(
                "voice",
                clips = persistentListOf(aclip("a2", "song", 40, 30, 30))
            )
        )
        // Header edit carries the effects — a valid recipe lands.
        val updated = seq.updateAudioTrack("music") {
            it.copy(
                eq = AudioEq(lowDb = -2f, highPassHz = 90f),
                ducking = AudioDucking(enabled = true, triggerTrackId = "voice")
            )
        }
        assertEquals(-2f, updated.audioTracks[0].eq.lowDb)
        assertTrue(updated.audioTracks[0].ducking.enabled)
        // An edit that breaks the trigger reference is rejected — the
        // copy re-validates before it ever reaches history.
        assertFailsWith<IllegalArgumentException> {
            seq.updateAudioTrack("music") {
                it.copy(
                    ducking = AudioDucking(enabled = true, triggerTrackId = "gone")
                )
            }
        }
        // Removing the trigger lane invalidates the duck — the model
        // cannot hold a dangling sidechain.
        assertFailsWith<IllegalArgumentException> {
            updated.copy(
                audioTracks = updated.audioTracks.removeAt(1)
            )
        }
    }
}
