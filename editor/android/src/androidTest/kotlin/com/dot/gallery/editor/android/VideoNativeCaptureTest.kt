package com.dot.gallery.editor.android

import android.app.UiAutomation
import android.content.ContentValues
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.graphics.Bitmap
import android.net.Uri
import android.os.SystemClock
import android.provider.MediaStore
import android.view.MotionEvent
import android.view.ViewGroup
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.dot.gallery.editor.core.time.EditorTime
import com.dot.gallery.editor.render.video.VideoEditorState
import com.dot.gallery.editor.videocodec.FfmpegSupport
import java.io.File
import java.io.FileInputStream
import kotlin.math.abs
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Native captures of the real [VideoEditorActivity]: full-screen
 * screenshots via [UiAutomation.takeScreenshot] (real SurfaceFlinger
 * frames, system bars included) across portrait, landscape and a 2x font
 * scale.
 *
 * Readiness is explicit, not elapsed time: the session has a sequence and
 * a decoded preview frame, the `videoEditor.preview` semantics node
 * exists, the requested configuration is live on the activity's
 * resources, the window has focus, and the accessibility stream is idle.
 * System animations are switched off for the batch so no app-transition
 * frame can leak the launcher into a capture; every device setting that
 * is touched is saved first and restored in tearDown.
 *
 * Also covers the pointer-cancel trim gesture — the Skiko test
 * dispatcher cannot inject a cancel event on desktop, so the real
 * `ACTION_CANCEL` path through the trim `RangeSlider` is verified here.
 */
@RunWith(AndroidJUnit4::class)
class VideoNativeCaptureTest {

    @get:Rule
    val composeRule = createEmptyComposeRule()

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val resolver = context.contentResolver
    private var sourceUri: Uri? = null
    private var fixtureFile: File? = null
    private var originalFontScale: String? = null
    private var originalNightMode: String? = null
    private val originalAnimScales = linkedMapOf<String, String>()

    private val animScaleKeys = listOf(
        "window_animation_scale",
        "transition_animation_scale",
        "animator_duration_scale"
    )

    @Before
    fun setUp() {
        FfmpegSupport.require()
        // The generated 'Sample coast.mp4' — staged into androidTest assets
        // by the stageVideoCaptureSample Gradle task — is a real panning
        // clip, so captures exercise actual moving footage through the
        // decode path rather than a synthetic colour band.
        fixtureFile = File(context.cacheDir, "Sample coast.mp4").apply {
            instrumentation.context.assets.open("sample-coast.mp4").use {
                input -> outputStream().use { input.copyTo(it) }
            }
        }

        val values = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, "Sample coast.mp4")
            put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
            put(MediaStore.Video.Media.IS_PENDING, 1)
            put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/EditorCaptureTest")
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
        sourceUri = uri

        originalFontScale = settingsGet("system", "font_scale")
        originalNightMode = parseNightMode(shell("cmd uimode night"))
        for (key in animScaleKeys) {
            originalAnimScales[key] = settingsGet("global", key)
            settingsPut("global", key, "0")
        }
    }

    @After
    fun tearDown() {
        val failures = mutableListOf<String>()
        fun restore(block: () -> Unit) {
            runCatching(block).onFailure { failures += it.message.toString() }
        }
        sourceUri?.let { resolver.delete(it, null, null) }
        fixtureFile?.delete()
        restore { settingsPut("system", "font_scale", originalFontScale ?: "1.0") }
        originalNightMode?.let { value ->
            restore {
                shell("cmd uimode night $value")
                check(parseNightMode(shell("cmd uimode night")) == value) {
                    "uimode night not restored to $value"
                }
            }
        }
        originalAnimScales.forEach { (key, value) ->
            restore { settingsPut("global", key, value) }
        }
        assertTrue(
            "device settings not restored: $failures",
            failures.isEmpty()
        )
    }

    /**
     * Runs a device shell command. [UiAutomation.executeShellCommand] does
     * not report an exit code, so every mutating call below is verified by
     * reading the setting back — a silently-failed write was what leaked
     * settings between captures before.
     */
    private fun shell(command: String): String =
        instrumentation.uiAutomation.executeShellCommand(command)
            .use { descriptor ->
                FileInputStream(descriptor.fileDescriptor)
                    .bufferedReader().readText()
            }

    private fun settingsGet(scope: String, key: String): String =
        shell("settings get $scope $key").trim()

    private fun settingsPut(scope: String, key: String, value: String) {
        if (value.isEmpty() || value == "null") {
            shell("settings delete $scope $key")
        } else {
            shell("settings put $scope $key $value")
            val got = settingsGet(scope, key)
            check(got == value) {
                "settings $scope/$key not applied: wanted $value, got $got"
            }
        }
    }

    private fun setNightModeChecked(night: Boolean) {
        val arg = if (night) "yes" else "no"
        shell("cmd uimode night $arg")
        check(parseNightMode(shell("cmd uimode night")) == arg) {
            "uimode night $arg not applied"
        }
    }

    private fun parseNightMode(output: String): String {
        val value = Regex("\\b(yes|no|auto|custom)\\b")
            .findAll(output).lastOrNull()?.value
        check(value != null) { "unparseable uimode output: $output" }
        return value
    }

    private fun launch(): ActivityScenario<VideoEditorActivity> =
        ActivityScenario.launch(
            VideoEditorIntents.create(
                context, sourceUri!!, VideoEditorActivity::class.java
            )
        )

    /** Pushes a font scale and waits for the activity config to adopt it. */
    private fun setFontScale(
        scenario: ActivityScenario<VideoEditorActivity>,
        target: Float
    ) {
        settingsPut("system", "font_scale", target.toString())
        composeRule.waitUntil(15_000) {
            var applied = false
            scenario.onActivity {
                applied = abs(
                    it.resources.configuration.fontScale - target
                ) < 0.01f
            }
            applied
        }
        // Configuration change recomposes; idle flushes LocalDensity.
        composeRule.waitForIdle()
    }

    /** Requests a night mode and waits for the resource mask to flip. */
    private fun setNightMode(
        scenario: ActivityScenario<VideoEditorActivity>,
        night: Boolean
    ) {
        setNightModeChecked(night)
        val wanted = if (night) {
            Configuration.UI_MODE_NIGHT_YES
        } else {
            Configuration.UI_MODE_NIGHT_NO
        }
        composeRule.waitUntil(15_000) {
            var applied = false
            scenario.onActivity {
                applied = it.resources.configuration.uiMode and
                    Configuration.UI_MODE_NIGHT_MASK == wanted
            }
            applied
        }
        composeRule.waitForIdle()
    }

    /**
     * Seeks to ~1 s — captures then show a real decoded mid-clip frame, a
     * moved playhead and a non-zero timecode — and waits for the four
     * filmstrip tiles of the selected clip, not just the preview node.
     */
    private fun seekAndSettle(
        scenario: ActivityScenario<VideoEditorActivity>
    ) {
        scenario.onActivity {
            it.viewModel.session.seek(EditorTime.fromMicroseconds(1_000_000))
        }
        composeRule.waitUntil(20_000) {
            val state = state(scenario)
            state != null && !state.seeking &&
                state.position == EditorTime.fromMicroseconds(1_000_000)
        }
        composeRule.waitUntil(30_000) {
            val state = state(scenario)
            state != null &&
                state.thumbnails.count { it.clipId == state.selectedClipId } >= 4
        }
        composeRule.waitForIdle()
        instrumentation.waitForIdleSync()
        instrumentation.uiAutomation.waitForIdle(300, 5_000)
    }

    private fun awaitReady(scenario: ActivityScenario<VideoEditorActivity>) {
        composeRule.waitUntil(30_000) {
            var met = false
            scenario.onActivity { activity ->
                val state = activity.viewModel.session.state.value
                met = state.sequence != null && !state.opening &&
                    !state.seeking && state.preview != null
            }
            met
        }
        // The compose tree must also have landed the preview node.
        composeRule.waitUntil(15_000) {
            composeRule.onAllNodes(hasTestTag("videoEditor.preview"))
                .fetchSemanticsNodes().isNotEmpty()
        }
        // Surface ownership: no capture before the editor actually owns the
        // foreground window, or the screenshot can be the launcher mid-
        // transition.
        composeRule.waitUntil(15_000) {
            var focused = false
            scenario.onActivity { focused = it.hasWindowFocus() }
            focused
        }
        composeRule.waitForIdle()
        instrumentation.waitForIdleSync()
        // Bounded quiet-period settle — no transitions remain (animations
        // are off), this only lets the composed frame reach SurfaceFlinger.
        instrumentation.uiAutomation.waitForIdle(300, 5_000)
    }

    private fun screenshot(
        name: String,
        scenario: ActivityScenario<VideoEditorActivity>
    ) {
        var focused = false
        scenario.onActivity { focused = it.hasWindowFocus() }
        assertTrue("editor lost window focus before $name", focused)
        val bitmap = instrumentation.uiAutomation.takeScreenshot()
        assertNotNull("takeScreenshot returned null", bitmap)
        assertTrue(bitmap!!.width > 0 && bitmap.height > 0)
        assertEditorSurface(bitmap, name)
        val dir = context.getExternalFilesDir("captures")!!
        dir.mkdirs()
        File(dir, "$name.png").outputStream().use {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
        bitmap.recycle()
    }

    /**
     * The preview letterboxes the decoded frame inside the always-dark
     * canvas colour, so a corner pixel inside the preview bounds is canvas,
     * never frame content. If a capture caught the launcher mid-transition
     * this pixel carries wallpaper or icons instead.
     */
    private fun assertEditorSurface(bitmap: Bitmap, name: String) {
        val preview = composeRule.onNodeWithTag("videoEditor.preview")
            .fetchSemanticsNode().boundsInWindow
        val px = (preview.left + 8f).toInt()
        val py = (preview.top + 8f).toInt()
        var maxLum = 0
        var maxSpread = 0
        for (dx in -6..6 step 6) {
            for (dy in -6..6 step 6) {
                val p = bitmap.getPixel(px + dx, py + dy)
                val r = p shr 16 and 0xFF
                val g = p shr 8 and 0xFF
                val b = p and 0xFF
                maxLum = maxOf(maxLum, r, g, b)
                maxSpread = maxOf(
                    maxSpread, abs(r - g), abs(g - b), abs(r - b)
                )
            }
        }
        assertTrue(
            "$name: preview corner at ($px,$py) is not the uniform dark " +
                "editor canvas (maxLum=$maxLum, maxSpread=$maxSpread) — " +
                "screenshot likely caught a foreign surface",
            maxLum < 90 && maxSpread < 48
        )
    }

    private fun state(
        scenario: ActivityScenario<VideoEditorActivity>
    ): VideoEditorState? {
        var current: VideoEditorState? = null
        scenario.onActivity { current = it.viewModel.session.state.value }
        return current
    }

    @Test
    fun capturePortraitDark() {
        launch().use { scenario ->
            setNightMode(scenario, night = true)
            setFontScale(scenario, 1.0f)
            awaitReady(scenario)
            composeRule.waitUntil(5_000) {
                scenario.state == Lifecycle.State.RESUMED
            }
            seekAndSettle(scenario)
            screenshot("android-video-portrait", scenario)
        }
    }

    @Test
    fun captureLandscape() {
        launch().use { scenario ->
            setNightMode(scenario, night = true)
            setFontScale(scenario, 1.0f)
            awaitReady(scenario)
            scenario.onActivity {
                it.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
            }
            composeRule.waitUntil(15_000) {
                var landscape = false
                scenario.onActivity {
                    landscape = it.resources.configuration.orientation ==
                        Configuration.ORIENTATION_LANDSCAPE &&
                        it.window.decorView.width > it.window.decorView.height
                }
                landscape
            }
            awaitReady(scenario)
            seekAndSettle(scenario)
            screenshot("android-video-landscape", scenario)
        }
    }

    @Test
    fun captureLargeFont() {
        launch().use { scenario ->
            setNightMode(scenario, night = true)
            setFontScale(scenario, 2.0f)
            awaitReady(scenario)
            seekAndSettle(scenario)
            screenshot("android-video-largefont", scenario)
        }
    }

    @Test
    fun pointerCancelRestoresTrimRange() {
        launch().use { scenario ->
            setFontScale(scenario, 1.0f)
            awaitReady(scenario)
            composeRule.onNodeWithTag("videoEditor.tab.trim").performClick()
            composeRule.waitForIdle()
            val slider = composeRule.onNodeWithTag("videoEditor.trim.rangeSlider")
            slider.performTouchInput {
                down(Offset(left + 8f, centerY))
                moveTo(Offset(width * 0.25f, centerY))
                moveTo(Offset(width * 0.3f, centerY))
                cancel()
            }
            composeRule.waitForIdle()
            // The cancelled drag committed nothing.
            val settled = state(scenario)
            assertFalse(settled?.isDirty == true)
            assertFalse(settled?.canUndo == true)
            assertTrue(
                settled?.sequence?.tracks?.single()?.clips?.single()
                    ?.sourceRange?.start == EditorTime.Zero
            )

            // A fresh drag still reaches the slider — while the pointer is
            // held the live draft disables Save (the screen's own
            // "draft in flight" signal), then releasing commits.
            slider.performTouchInput {
                down(Offset(left + 8f, centerY))
                moveTo(Offset(width * 0.25f, centerY))
                moveTo(Offset(width * 0.3f, centerY))
            }
            composeRule.waitUntil(10_000) {
                try {
                    composeRule.onNodeWithTag("videoEditor.save")
                        .assertIsNotEnabled()
                    true
                } catch (failure: AssertionError) {
                    false
                }
            }
            slider.performTouchInput { up() }
            composeRule.waitUntil(15_000) {
                state(scenario)?.isDirty == true
            }
        }
    }

    /**
     * A single injected batch — down/move/up with no intermediate waits —
     * commits exactly one trim. The commit reads the gesture's own draft
     * holder, so it does not depend on a recomposition landing between the
     * last move and the release.
     */
    @Test
    fun quickSingleBatchTrimDragCommitsExactlyOneEdit() {
        launch().use { scenario ->
            awaitReady(scenario)
            composeRule.onNodeWithTag("videoEditor.tab.trim").performClick()
            composeRule.waitForIdle()
            composeRule.onNodeWithTag("videoEditor.trim.rangeSlider")
                .performTouchInput {
                    down(Offset(left + 8f, centerY))
                    moveTo(Offset(width * 0.25f, centerY))
                    moveTo(Offset(width * 0.3f, centerY))
                    up()
                }
            composeRule.waitUntil(15_000) {
                state(scenario)?.isDirty == true
            }
            // Exactly one edit: a single undo fully restores the source.
            scenario.onActivity { it.viewModel.session.undo() }
            composeRule.waitUntil(10_000) {
                state(scenario)?.isDirty == false
            }
            assertFalse(state(scenario)?.canUndo == true)
        }
    }

    /**
     * A fast scrub while playing pauses for the gesture and resumes on
     * release; a cancelled scrub restores the start position and the play
     * intent. The capture happens at pointer down — before the first seek
     * — so the gesture can never "remember" the paused state it caused.
     *
     * Real [MotionEvent]s are dispatched straight to the content root on
     * the main thread: [performTouchInput] waits for Compose idle before
     * injecting, which never arrives while playback drives ~30 state
     * updates per second — the gesture would only land after
     * end-of-stream and could never observe `playing == true` —
     * and [UiAutomation.injectInputEvent] events were accepted but never
     * delivered to the activity on this emulator. Dispatching through the
     * view hierarchy still exercises the whole Compose pointer pipeline
     * (hit test → Initial pass → gesture coroutines).
     */
    @Test
    fun quickScrubWhilePlayingResumesAndCancelRestores() {
        launch().use { scenario ->
            awaitReady(scenario)
            val bounds = composeRule.onNodeWithTag("videoEditor.timeline")
                .fetchSemanticsNode().boundsInRoot
            val centerY = bounds.top + bounds.height / 2f
            fun x(fraction: Float) = bounds.left + bounds.width * fraction

            scenario.onActivity { it.viewModel.session.play() }
            composeRule.waitUntil(15_000) {
                val state = state(scenario)
                state?.playing == true && state.position.ticks > 0L
            }

            // Release gesture: down, move, up — release resumes playback.
            var downTime = SystemClock.uptimeMillis()
            dispatchTouch(scenario, MotionEvent.ACTION_DOWN, x(0.2f), centerY, downTime)
            dispatchTouch(scenario, MotionEvent.ACTION_MOVE, x(0.5f), centerY, downTime)
            composeRule.waitUntil(10_000) {
                state(scenario)?.playing == false
            }
            dispatchTouch(scenario, MotionEvent.ACTION_UP, x(0.5f), centerY, downTime)
            composeRule.waitUntil(15_000) {
                state(scenario)?.playing == true
            }

            // Cancel: the restore seeks back to the captured start — the
            // playhead must never be left near the cancelled drag's end
            // (0.95 of the 6 s clip ≈ 5.7 s) — and playback resumes.
            downTime = SystemClock.uptimeMillis()
            dispatchTouch(scenario, MotionEvent.ACTION_DOWN, x(0.5f), centerY, downTime)
            dispatchTouch(scenario, MotionEvent.ACTION_MOVE, x(0.95f), centerY, downTime)
            dispatchTouch(scenario, MotionEvent.ACTION_CANCEL, x(0.95f), centerY, downTime)
            composeRule.waitUntil(10_000) {
                val state = state(scenario)
                state != null && state.playing
            }
            composeRule.waitUntil(10_000) {
                val state = state(scenario)
                state != null &&
                    state.position < EditorTime.fromMicroseconds(5_000_000)
            }
        }
    }

    private fun dispatchTouch(
        scenario: ActivityScenario<VideoEditorActivity>,
        action: Int,
        x: Float,
        y: Float,
        downTime: Long
    ) {
        scenario.onActivity { activity ->
            val root = activity.findViewById<ViewGroup>(android.R.id.content)
                .getChildAt(0)
            val event = MotionEvent.obtain(
                downTime, SystemClock.uptimeMillis(), action, x, y, 0
            )
            root.dispatchTouchEvent(event)
            event.recycle()
        }
    }

}
