package com.dot.gallery.editor.android

import android.app.Activity
import android.app.Application
import android.app.Instrumentation
import android.content.ContentValues
import android.content.Intent
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.MediaStore
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.SavedStateHandle
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.dot.gallery.editor.core.project.VideoProjectCodec
import com.dot.gallery.editor.core.time.EDITOR_TICKS_PER_SECOND
import com.dot.gallery.editor.core.time.EditorTime
import com.dot.gallery.editor.render.video.VideoEditorResult
import com.dot.gallery.editor.render.video.VideoEditorState
import com.dot.gallery.editor.videocodec.FfmpegSupport
import java.io.File
import java.nio.ByteBuffer
import java.nio.ShortBuffer
import kotlin.math.PI
import kotlin.math.roundToInt
import kotlin.math.sin
import org.bytedeco.ffmpeg.global.avcodec.AV_CODEC_ID_AAC
import org.bytedeco.ffmpeg.global.avutil.AV_PIX_FMT_RGB24
import org.bytedeco.ffmpeg.global.avutil.AV_PIX_FMT_YUV420P
import org.bytedeco.javacv.FFmpegFrameRecorder
import org.bytedeco.javacv.Frame
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The external intent contract of [VideoEditorActivity], run against the
 * real exported activity inside this library's test APK with a real
 * `content://` MP4 published through MediaStore: bad requests fail with
 * [VideoEditorIntents.RESULT_ERROR], caller-planted SavedState extras are
 * not an authority, playback advances the session position through the
 * device clock, recreation keeps committed cuts, and a UI-driven
 * split/delete/save returns a granted, genuinely encoded copy.
 *
 * Same-UID caveat: the test APK instruments its own library, so the
 * `content://` grant flows are exercised but do not prove cross-UID
 * behaviour against a foreign caller.
 */
@RunWith(AndroidJUnit4::class)
class VideoEditorActivityIntentTest {

    // Unbound rule: the editor lives in the scenario-launched activity; the
    // "videoEditor.*" tags are :editor:ui-internal, hence the literals.
    @get:Rule
    val composeRule = createEmptyComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val resolver = context.contentResolver
    private val createdUris = mutableListOf<Uri>()
    private var fixtureFile: File? = null

    @Before
    fun setUp() {
        FfmpegSupport.require() // the whole run needs the packaged codecs
        fixtureFile = File(context.cacheDir, "video-intent-fixture.mp4")
            .apply { writeFixture(this) }
    }

    @After
    fun tearDown() {
        for (uri in createdUris) {
            resolver.delete(uri, null, null)
        }
        fixtureFile?.delete()
    }

    /**
     * A 4 s, 160x120, 30 fps H.264 + AAC clip — tinted frames over a quiet
     * 330 Hz stereo tone — encoded on-device by the packaged FFmpeg. Long
     * enough that a playback assertion never races end-of-stream, small
     * enough that encode time stays trivial.
     */
    private fun writeFixture(out: File) {
        val recorder = FFmpegFrameRecorder(out.absolutePath, WIDTH, HEIGHT, 2)
            .apply {
                format = "mp4"
                videoCodecName = "libopenh264"
                pixelFormat = AV_PIX_FMT_YUV420P
                frameRate = FPS.toDouble()
                gopSize = FPS
                videoBitrate = 200_000
                audioCodecName = "aac"
                audioCodec = AV_CODEC_ID_AAC
                sampleRate = SAMPLE_RATE
                audioChannels = 2
                audioBitrate = 64_000
            }
        val rgb = ByteBuffer.allocateDirect(WIDTH * HEIGHT * 3)
        val pcm = ShortBuffer.allocate(SAMPLE_RATE / FPS * 2)
        var sampleIndex = 0L
        try {
            recorder.start()
            repeat(FPS * SECONDS) { n ->
                rgb.clear()
                val tint = (48 + n) and 0xFF
                while (rgb.hasRemaining()) {
                    rgb.put(tint.toByte()); rgb.put(96.toByte()); rgb.put(32.toByte())
                }
                rgb.flip()
                recorder.recordImage(
                    WIDTH, HEIGHT, Frame.DEPTH_UBYTE, 3,
                    WIDTH * 3, AV_PIX_FMT_RGB24, rgb
                )
                pcm.clear()
                repeat(SAMPLE_RATE / FPS) { s ->
                    val v = (sin(
                        2.0 * PI * 330.0 * (sampleIndex + s) / SAMPLE_RATE
                    ) * 1200.0).roundToInt().toShort()
                    pcm.put(v); pcm.put(v)
                }
                pcm.flip()
                sampleIndex += SAMPLE_RATE / FPS
                recorder.recordSamples(SAMPLE_RATE, 2, pcm)
            }
            recorder.stop()
        } finally {
            recorder.release()
        }
    }

    /** Publishes the fixture MP4 through MediaStore as a real content:// source. */
    private fun insertSource(): Uri {
        val values = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, "video-intent-source.mp4")
            put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
            put(MediaStore.Video.Media.IS_PENDING, 1)
            put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/EditorIntentTest")
        }
        val uri = resolver.insert(
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values
        )!!
        resolver.openOutputStream(uri)!!.use {
            it.write(fixtureFile!!.readBytes())
        }
        values.clear()
        values.put(MediaStore.Video.Media.IS_PENDING, 0)
        resolver.update(uri, values, null, null)
        createdUris += uri
        return uri
    }

    private fun launchIntent(source: Uri): Intent =
        VideoEditorIntents.create(context, source, VideoEditorActivity::class.java)

    private fun awaitFinished(
        scenario: ActivityScenario<VideoEditorActivity>,
        timeoutMs: Long = 30_000
    ): Instrumentation.ActivityResult {
        composeRule.waitUntil(timeoutMs) {
            scenario.state == Lifecycle.State.DESTROYED
        }
        return scenario.result
    }

    private fun awaitSessionState(
        scenario: ActivityScenario<VideoEditorActivity>,
        timeoutMs: Long = 30_000,
        condition: (VideoEditorState) -> Boolean
    ) {
        composeRule.waitUntil(timeoutMs) {
            var met = false
            scenario.onActivity { activity ->
                met = condition(activity.viewModel.session.state.value)
            }
            met
        }
    }

    private fun awaitOpened(scenario: ActivityScenario<VideoEditorActivity>) {
        awaitSessionState(scenario) { it.sequence != null && !it.opening }
    }

    @Test
    fun missingDataIsAnErrorNotACancel() {
        val intent = Intent(context, VideoEditorActivity::class.java)
            .setAction(VideoEditorIntents.ACTION_EDIT)
        ActivityScenario.launchActivityForResult<VideoEditorActivity>(intent)
            .use { scenario ->
                val result = awaitFinished(scenario)
                assertEquals(VideoEditorIntents.RESULT_ERROR, result.resultCode)
                val parsed = VideoEditorIntents.parseResult(
                    result.resultCode, result.resultData
                )
                assertTrue(parsed is VideoEditorResult.Failed)
            }
    }

    @Test
    fun platformEditActionOpensTheEditor() {
        val source = insertSource()
        val intent = Intent(context, VideoEditorActivity::class.java)
            .setAction(Intent.ACTION_EDIT)
            .setDataAndType(source, "video/mp4")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        ActivityScenario.launchActivityForResult<VideoEditorActivity>(intent)
            .use { scenario ->
                // The standard platform action is accepted, not rejected.
                awaitOpened(scenario)
            }
    }

    @Test
    fun wrongActionAndExtraOutputAreRejected() {
        val source = insertSource()
        val wrongAction = Intent(context, VideoEditorActivity::class.java)
            .setAction(Intent.ACTION_VIEW)
            .setDataAndType(source, "video/mp4")
        ActivityScenario.launchActivityForResult<VideoEditorActivity>(wrongAction)
            .use { scenario ->
                assertEquals(
                    VideoEditorIntents.RESULT_ERROR,
                    awaitFinished(scenario).resultCode
                )
            }

        val withOutput = launchIntent(source)
            .putExtra(MediaStore.EXTRA_OUTPUT, Uri.parse("content://x/y"))
        ActivityScenario.launchActivityForResult<VideoEditorActivity>(withOutput)
            .use { scenario ->
                val result = awaitFinished(scenario)
                assertEquals(VideoEditorIntents.RESULT_ERROR, result.resultCode)
                assertTrue(
                    VideoEditorIntents.parseResult(
                        result.resultCode, result.resultData
                    ) is VideoEditorResult.Failed
                )
            }
    }

    @Test
    fun fileSchemeIsRejected() {
        val intent = Intent(context, VideoEditorActivity::class.java)
            .setAction(VideoEditorIntents.ACTION_EDIT)
            .setDataAndType(Uri.fromFile(fixtureFile), "video/mp4")
        ActivityScenario.launchActivityForResult<VideoEditorActivity>(intent)
            .use { scenario ->
                assertEquals(
                    VideoEditorIntents.RESULT_ERROR,
                    awaitFinished(scenario).resultCode
                )
            }
    }

    @Test
    fun missingReadGrantFailsBeforeOpen() {
        val source = insertSource()
        // Same request shape minus the read grant — the activity must fail
        // before the session ever touches the URI.
        val intent = Intent(context, VideoEditorActivity::class.java)
            .setAction(VideoEditorIntents.ACTION_EDIT)
            .setDataAndType(source, "video/mp4")
        ActivityScenario.launchActivityForResult<VideoEditorActivity>(intent)
            .use { scenario ->
                val result = awaitFinished(scenario)
                assertEquals(VideoEditorIntents.RESULT_ERROR, result.resultCode)
                assertTrue(
                    VideoEditorIntents.parseResult(
                        result.resultCode, result.resultData
                    ) is VideoEditorResult.Failed
                )
            }
    }

    @Test
    fun plantedSourceAndCutExtrasCannotHijackTheOpen() {
        // A "private" file the content:// caller cannot legitimately point
        // the editor at: if a planted SavedStateHandle extra won, the clip
        // count/duration would reflect it instead of the real source.
        val privateFile = File(context.cacheDir, "video-private.mp4")
            .apply { writeFixture(this) }
        try {
            val source = insertSource() // the real 4 s content:// source
            val intent = launchIntent(source)
                // SavedStateHandle merges intent extras; an untrusted caller
                // can therefore seed the persisted-state keys directly.
                .putExtra(
                    "editor.video.source", Uri.fromFile(privateFile).toString()
                )
                .putExtra(
                    "editor.video.cuts",
                    longArrayOf(0L, EDITOR_TICKS_PER_SECOND / 2)
                )
                .putExtra("editor.video.selectedIndex", 0)

            ActivityScenario.launchActivityForResult<VideoEditorActivity>(intent)
                .use { scenario ->
                    awaitOpened(scenario)
                    scenario.onActivity { activity ->
                        val state = activity.viewModel.session.state.value
                        val sequence = requireNotNull(state.sequence)
                        assertEquals(
                            source.toString(),
                            state.info!!.asset.reference
                        )
                        // Planted cuts are not an authority on a fresh
                        // launch — the session opens the full source clean.
                        val clips = sequence.tracks.single().clips
                        assertEquals(1, clips.size)
                        assertEquals(
                            EditorTime.Zero, clips.single().sourceRange.start
                        )
                        assertTrue(!state.isDirty)
                    }
                }
        } finally {
            privateFile.delete()
        }
    }

    /**
     * A process-death restore with an empty packed cut array is a real
     * document — "the user deleted every clip" — not a missing payload. A
     * fresh ViewModel over a SavedStateHandle seeded with the persisted
     * primitives replays the empty sequence as a dirty, undoable edit.
     */
    @Test
    fun freshVmRestoresEmptyCutListAsDeleteAll() {
        val source = insertSource()
        val savedState = SavedStateHandle(
            mapOf(
                "editor.video.source" to source.toString(),
                "editor.video.cuts" to longArrayOf(),
                "editor.video.selectedIndex" to -1,
                "editor.video.positionTicks" to 0L
            )
        )
        val viewModel = VideoEditorViewModel(
            context.applicationContext as Application, savedState
        )
        try {
            viewModel.start(source, persistable = false, restoring = true)
            composeRule.waitUntil(30_000) {
                val state = viewModel.session.state.value
                !state.opening && state.sequence != null && state.isDirty
            }
            val state = viewModel.session.state.value
            assertEquals(
                0, state.sequence!!.tracks.single().clips.size
            )
            assertEquals(EditorTime.Zero, state.position)
            assertTrue(state.canUndo)
        } finally {
            viewModel.session.close()
        }
    }

    /**
     * The real round trip the seeded-handle test above cannot cover: the
     * first ViewModel's own persistence must write the committed document —
     * an empty clip list with no selection — through the project file, and
     * SavedState must carry only the file's marker + checksum, never a
     * bulk payload. A fresh ViewModel over the same handle replays the
     * empty sequence as a dirty, undoable restore.
     */
    @Test
    fun deleteAllRoundTripsThroughPersistedState() {
        val source = insertSource()
        val savedState = SavedStateHandle()
        val first = VideoEditorViewModel(
            context.applicationContext as Application, savedState
        )
        try {
            first.start(source, persistable = false, restoring = false)
            composeRule.waitUntil(30_000) {
                val state = first.session.state.value
                !state.opening && state.sequence != null
            }
            first.session.deleteSelected()
            composeRule.waitUntil(30_000) {
                val state = first.session.state.value
                state.sequence?.tracks?.single()?.clips?.isEmpty() == true
            }
            // The debounced autosave lands the empty document in the
            // project file; SavedState then names it and pins its bytes.
            composeRule.waitUntil(10_000) {
                savedState.get<String>("editor.video.project") ==
                    "video-project.json" &&
                    savedState.get<String>("editor.video.projectChecksum") !=
                        null
            }
            val json = File(
                context.noBackupFilesDir, "editor/video-project.json"
            ).readText()
            val snapshot = VideoProjectCodec.decode(json)
            assertEquals(0, snapshot.sequence.tracks.single().clips.size)
            assertNull(snapshot.selectedClipId)
        } finally {
            first.session.close()
        }

        val restored = VideoEditorViewModel(
            context.applicationContext as Application, savedState
        )
        try {
            restored.start(source, persistable = false, restoring = true)
            composeRule.waitUntil(30_000) {
                val state = restored.session.state.value
                !state.opening &&
                    state.sequence?.tracks?.single()?.clips?.isEmpty() ==
                        true &&
                    state.isDirty
            }
            val state = restored.session.state.value
            assertNull(state.error)
            assertTrue(state.canUndo)
            restored.session.undo()
            composeRule.waitUntil(10_000) {
                val s = restored.session.state.value
                s.sequence?.tracks?.single()?.clips?.size == 1 && !s.isDirty
            }
        } finally {
            restored.session.close()
        }
    }

    @Test
    fun cleanBackIsCancelled() {
        val source = insertSource()
        ActivityScenario.launchActivityForResult<VideoEditorActivity>(
            launchIntent(source)
        ).use { scenario ->
            awaitOpened(scenario)
            Espresso.pressBackUnconditionally()
            val result = awaitFinished(scenario)
            assertEquals(Activity.RESULT_CANCELED, result.resultCode)
            assertEquals(
                VideoEditorResult.Cancelled,
                VideoEditorIntents.parseResult(
                    result.resultCode, result.resultData
                )
            )
        }
    }

    @Test
    fun playbackAdvancesPositionAndPausesViaUi() {
        val source = insertSource()
        ActivityScenario.launchActivityForResult<VideoEditorActivity>(
            launchIntent(source)
        ).use { scenario ->
            awaitOpened(scenario)
            // Real AudioTrack-backed playback: the position must advance on
            // the device clock, then the same button pauses it.
            composeRule.onNodeWithTag("videoEditor.play").performClick()
            awaitSessionState(scenario, 15_000) {
                it.playing && it.position.ticks > 0L
            }
            composeRule.onNodeWithTag("videoEditor.play").performClick()
            awaitSessionState(scenario, 10_000) { !it.playing }
        }
    }

    @Test
    fun recreationKeepsCommittedSplit() {
        val source = insertSource()
        ActivityScenario.launchActivityForResult<VideoEditorActivity>(
            launchIntent(source)
        ).use { scenario ->
            awaitOpened(scenario)
            scenario.onActivity { activity ->
                activity.viewModel.session.seek(
                    EditorTime.fromMicroseconds(1_000_000)
                )
            }
            awaitSessionState(scenario) { !it.seeking }
            // Compact keeps the clip commands in the dock's Tools →
            // Arrange surface — the timeline lane is only a selector.
            composeRule.onNodeWithTag("videoEditor.tools").performClick()
            composeRule.onNodeWithTag("videoEditor.tab.clips")
                .performScrollTo()
                .performClick()
            composeRule.onNodeWithTag("videoEditor.split").performClick()
            awaitSessionState(scenario) {
                it.sequence?.tracks?.single()?.clips?.size == 2
            }
            scenario.recreate()
            awaitSessionState(scenario) {
                it.sequence?.tracks?.single()?.clips?.size == 2 &&
                    it.isDirty
            }
        }
    }

    @Test
    fun editAndSaveReturnsGrantedMp4CopyAndLeavesSourceAlone() {
        val source = insertSource()
        val sourceBytesBefore =
            resolver.openInputStream(source)!!.use { it.readBytes() }
        val scenario = ActivityScenario.launchActivityForResult<VideoEditorActivity>(
            launchIntent(source)
        )
        try {
            awaitOpened(scenario)

            // Split at 2 s, then select and delete the second clip through
            // the clip strip — the remaining sequence is the first half.
            scenario.onActivity { activity ->
                activity.viewModel.session.seek(
                    EditorTime.fromMicroseconds(2_000_000)
                )
            }
            awaitSessionState(scenario) { !it.seeking }
            // Compact keeps the clip commands in the dock's Tools →
            // Arrange surface — the timeline lane is only a selector.
            composeRule.onNodeWithTag("videoEditor.tools").performClick()
            composeRule.onNodeWithTag("videoEditor.tab.clips")
                .performScrollTo()
                .performClick()
            composeRule.onNodeWithTag("videoEditor.split").performClick()
            awaitSessionState(scenario) {
                it.sequence?.tracks?.single()?.clips?.size == 2
            }
            var secondClipId: String? = null
            scenario.onActivity { activity ->
                val state = activity.viewModel.session.state.value
                secondClipId =
                    state.sequence!!.tracks.single().clips[1].id
            }
            // Clip selection in the Arrange sheet goes through its
            // roster rows — the lane tiles live on the timeline panel.
            composeRule
                .onNodeWithTag("videoEditor.clipRow." + secondClipId!!)
                .performScrollTo()
                .performClick()
            awaitSessionState(scenario) {
                it.selectedClipId == secondClipId
            }
            composeRule.onNodeWithTag("videoEditor.delete").performClick()
            awaitSessionState(scenario) {
                it.sequence?.tracks?.single()?.clips?.size == 1
            }

            composeRule.onNodeWithTag("videoEditor.save").performClick()
            // Real encode of ~2 s through the packaged FFmpeg takes a few
            // seconds on an emulator.
            awaitSessionState(scenario, 90_000) { it.exported != null }

            // Once the receipt lands the sequence is clean again, so the
            // external-editor convention may already have auto-finished
            // with RESULT_OK. If it is still up, closing returns the last
            // saved copy via the discard confirmation.
            val autoFinished = try {
                composeRule.waitUntil(10_000) {
                    scenario.state == Lifecycle.State.DESTROYED
                }
                true
            } catch (timeout: AssertionError) {
                false
            }
            if (!autoFinished) {
                Espresso.pressBackUnconditionally()
                val finishedByBack = try {
                    composeRule.waitUntil(10_000) {
                        scenario.state == Lifecycle.State.DESTROYED
                    }
                    true
                } catch (timeout: AssertionError) {
                    false
                }
                if (!finishedByBack) {
                    composeRule
                        .onNodeWithTag("videoEditor.discard.confirm")
                        .performClick()
                }
            }
            val result = awaitFinished(scenario)
            assertEquals(Activity.RESULT_OK, result.resultCode)

            val parsed = VideoEditorIntents.parseResult(
                result.resultCode, result.resultData
            )
            assertTrue("expected Saved, got $parsed",
                parsed is VideoEditorResult.Saved)
            val saved = (parsed as VideoEditorResult.Saved).video
            assertEquals("video/mp4", saved.mimeType)
            assertEquals(WIDTH, saved.size.width)
            assertEquals(HEIGHT, saved.size.height)
            // The kept half of a 4 s clip: honest duration, not a guess.
            assertTrue(
                "expected ~2 s, got ${saved.duration}",
                saved.duration.ticks in
                    (EDITOR_TICKS_PER_SECOND * 3 / 2)..
                        (EDITOR_TICKS_PER_SECOND * 5 / 2)
            )
            assertTrue(saved.reference != source.toString())
            assertTrue(
                result.resultData!!.flags and
                    Intent.FLAG_GRANT_READ_URI_PERMISSION != 0
            )

            // The published row opens through MediaStore and reports the
            // real encoded shape.
            val outputUri = Uri.parse(saved.reference)
            createdUris += outputUri
            val retriever = MediaMetadataRetriever()
            try {
                retriever.setDataSource(context, outputUri)
                val durationMs = retriever.extractMetadata(
                    MediaMetadataRetriever.METADATA_KEY_DURATION
                )!!.toLong()
                assertTrue(
                    "expected ~2000 ms, got $durationMs",
                    durationMs in 1500..2500
                )
                val hasAudio = retriever.extractMetadata(
                    MediaMetadataRetriever.METADATA_KEY_HAS_AUDIO
                )
                assertEquals("yes", hasAudio)
            } finally {
                retriever.release()
            }
            assertNotNull(resolver.openInputStream(outputUri))

            // The source bytes are untouched.
            val sourceAfter =
                resolver.openInputStream(source)!!.use { it.readBytes() }
            org.junit.Assert.assertArrayEquals(sourceBytesBefore, sourceAfter)
        } finally {
            scenario.close()
        }
    }

    private companion object {
        const val WIDTH = 160
        const val HEIGHT = 120
        const val FPS = 30
        const val SECONDS = 4
        const val SAMPLE_RATE = 48_000
    }
}
