/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.docs.capture

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.ComposeTimeoutException
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.dot.gallery.docs.DOCS_SCENE_ROOT_TAG
import com.dot.gallery.docs.DocsScene
import java.io.File
import java.io.FileOutputStream
import kotlin.math.abs
import kotlin.math.roundToInt
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Drives the shared scene registry through deterministic captures for the docs
 * site. Usage:
 *
 *   adb shell am instrument -w \
 *     -e scene settings-list[,other-id] \
 *     -e device phone[,tablet] \
 *     -e state default \
 *     -e theme light[,dark,amoled] \
 *     -e class com.dot.gallery.docs.capture.DocsCaptureTest \
 *     com.dot.gallery.docs.capture.test/androidx.test.runner.AndroidJUnitRunner
 *
 * Renders each (scene, device preset, state, theme) in the shared
 * [com.dot.gallery.docs.DocsSceneHost], captures the `docs-scene-root` node at
 * the scene's logical size, writes WebP (quality 90) under
 * `<external-files>/docs-out/` and records it in `docs-manifest.json`
 * fragments merged by (theme, device, state).
 */
@RunWith(AndroidJUnit4::class)
class DocsCaptureTest {

    @get:Rule
    val activityRule = createAndroidComposeRule<DocsCaptureActivity>()

    private data class Job(
        val scene: DocsScene,
        val preset: CapturePreset,
        val state: String?,
        val theme: String,
    )

    @Test
    fun capture() {
        val args = InstrumentationRegistry.getArguments()
        check(args.getString("jobs") == null) {
            "Use scene, device, state, theme filters"
        }
        val sceneFilter = args.getString("scene")
        val deviceFilter = args.getString("device")
        val stateFilter = args.getString("state")?.takeIf { it.isNotBlank() }
        val themes = args.getString("theme")?.split(',')?.map { it.trim() }
            ?.filter { it.isNotEmpty() } ?: listOf("light")

        val window = hostWindowDp()
        val jobs = mutableListOf<Job>()
        val skipped = mutableListOf<String>()
        for (scene in DocsSceneRegistry.select(sceneFilter)) {
            val wanted = when {
                scene.presentation == "component" -> listOf(CapturePreset.PHONE)
                else -> CapturePreset.parse(deviceFilter)
            }
            for (preset in wanted) {
                // A preset is capturable only when its logical canvas fits the
                // host window: Modifier.size coerces into the display's
                // constraints, so an oversized preset would sit at the window
                // size forever and burn the 10s size gate (ComposeTimeout).
                val widthDp = if (scene.presentation == "component") scene.widthDp else preset.widthDp
                val heightDp = if (scene.presentation == "component") scene.heightDp else preset.heightDp
                val fits = widthDp <= window.widthDp + FIT_SLACK_DP &&
                    heightDp <= window.heightDp + FIT_SLACK_DP
                if (!fits) {
                    skipped += "${scene.id}:${preset.id}"
                    continue
                }
                for (state in DocsSceneRegistry.statesOf(scene, stateFilter)) {
                    for (theme in themes.filter { it in scene.themes }) {
                        jobs += Job(scene, preset, state, theme)
                    }
                }
            }
        }
        check(jobs.isNotEmpty()) {
            val windowText = "${window.widthDp}x${window.heightDp}dp"
            val presetText = if (sceneFilter.isNullOrBlank()) "every matched scene" else "'$sceneFilter'"
            val wantedText = CapturePreset.parse(deviceFilter).joinToString { it.id }
            "DocsCaptureTest matched no capturable presets. The host window is $windowText and " +
                "scene preset(s) $wantedText of $presetText need a larger display. " +
                "Run this preset on a matching tablet/foldable emulator, or pass an explicit " +
                "device filter that fits (e.g. device=phone)."
        }
        if (skipped.isNotEmpty()) {
            Log.w(TAG, "skipped presets larger than the ${window.widthDp}x${window.heightDp}dp " +
                "host window: $skipped — capture them on a matching emulator")
        }

        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val outDir = File(context.getExternalFilesDir(null), "docs-out")
        val writer = DocsManifestWriter(outDir)

        jobs.forEach { job -> runJob(job, outDir, writer) }
        writer.write()
    }

    @OptIn(ExperimentalTestApi::class)
    private fun runJob(job: Job, outDir: File, writer: DocsManifestWriter) {
        val scene = job.scene
        val component = scene.presentation == "component"
        val widthDp = if (component) scene.widthDp else job.preset.widthDp
        val heightDp = if (component) scene.heightDp else job.preset.heightDp
        val state = job.state
        val theme = job.theme
        val relFile =
            "${scene.id}/$theme/${job.preset.id}-${state ?: "default"}.webp"
        Log.i(TAG, "capture ${scene.id} state=$state theme=$theme preset=${job.preset.id}")

        activityRule.activityRule.scenario.onActivity {
            it.renderScene(
                widthDp = widthDp,
                heightDp = heightDp,
                theme = theme,
                scene = scene,
                state = state,
            )
        }
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()

        // The frame tag carries this job's (scene, state, theme) identity, so a
        // stale node left over from a previous same-size job can never match.
        val frameKey = "docs-frame:${scene.id}:${state ?: "default"}:${theme}"
        val node = activityRule.onNode(
            hasTestTag(DOCS_SCENE_ROOT_TAG) and hasAnyAncestor(hasTestTag(frameKey)),
            useUnmergedTree = true,
        )
        // A frozen clock never drives the first layout of a freshly set
        // composition — let it advance until THIS job's tagged root exists and
        // has been measured at the job's logical size, then run one more
        // settled frame before freezing so infinite demo animations cannot
        // stall the run.
        val expectedW = (widthDp * activityRule.density.density).roundToInt()
        val expectedH = (heightDp * activityRule.density.density).roundToInt()
        activityRule.mainClock.autoAdvance = true
        try {
            activityRule.waitUntil(timeoutMillis = SIZE_TIMEOUT_MS) {
                runCatching {
                    val bounds = node.fetchSemanticsNode().boundsInRoot
                    abs(bounds.width - expectedW) <= SIZE_TOLERANCE_PX &&
                        abs(bounds.height - expectedH) <= SIZE_TOLERANCE_PX
                }.getOrDefault(false)
            }
        } catch (timeout: ComposeTimeoutException) {
            // Surface the measured vs expected geometry so a size failure reads
            // as a layout problem in the scene (or an unfit preset), not as a
            // generic 10s timeout.
            val measured = runCatching { node.fetchSemanticsNode().boundsInRoot }
                .getOrNull()?.let { "${it.width.roundToInt()}x${it.height.roundToInt()}px" }
                ?: "node not found"
            error(
                "scene root for $relFile never measured ${expectedW}x${expectedH}px " +
                    "(measured: $measured within ${SIZE_TIMEOUT_MS}ms)"
            )
        }
        node.assertIsDisplayed()
        scene.requiredReadyTag?.let { tag ->
            // Scenes like the map declare real-readiness through a tagged node
            // that appears only after an external signal (fully-rendered map
            // frame). autoAdvance is still on, so the clock keeps pumping.
            activityRule.waitUntil(timeoutMillis = READY_TAG_TIMEOUT_MS) {
                activityRule.onAllNodesWithTag(tag, useUnmergedTree = true)
                    .fetchSemanticsNodes().isNotEmpty()
            }
        }
        if (scene.settleMillis > 0L) {
            // Async decoders (Sketch full-res load, ExoPlayer first frame, GL
            // surface warm-up) are not composition-bound — waitForIdle would
            // return before they land. Let the real clock drive them for the
            // scene's settle budget, then freeze as usual.
            Thread.sleep(scene.settleMillis)
            activityRule.waitForIdle()
        }
        if (state != null && state in scene.swipedStates) {
            // Scroll the content under the top bar so the capture shows its blurred state.
            node.performTouchInput {
                swipeUp(startY = height * 0.75f, endY = height * 0.35f, durationMillis = 400L)
            }
            activityRule.waitForIdle()
        }
        activityRule.mainClock.autoAdvance = false
        activityRule.mainClock.advanceTimeByFrame()
        activityRule.waitForIdle()
        val bounds = node.fetchSemanticsNode().boundsInRoot
        Log.i(TAG, "node bounds $relFile -> ${bounds.width}x${bounds.height}px")
        val bitmap = if (scene.captureWindow) {
            captureWindowBitmap(node, relFile)
        } else {
            node.captureToImage().asAndroidBitmap()
        }

        assertNotBlank(bitmap, relFile)
        assertAspect(widthDp, heightDp, bitmap, relFile)

        val file = File(outDir, relFile)
        file.parentFile?.mkdirs()
        val format = if (android.os.Build.VERSION.SDK_INT >= 30) {
            Bitmap.CompressFormat.WEBP_LOSSY
        } else {
            @Suppress("DEPRECATION")
            Bitmap.CompressFormat.WEBP
        }
        FileOutputStream(file).use { stream ->
            bitmap.compress(format, 90, stream)
        }
        writer.record(
            scene,
            job.preset.id,
            state,
            theme,
            relFile,
            bitmap.width,
            bitmap.height,
            widthDp,
            heightDp,
        )
    }

    /**
     * Modal scenes (ModalBottomSheet/Dialog) live in a separate window, so a
     * node-scoped `captureToImage` would photograph an empty host. Instead take
     * the full physical screenshot via UiAutomation and crop it to the tagged
     * root's on-screen rect: `boundsInWindow` (relative to the host window)
     * offset by the decor view's `locationOnScreen`.
     */
    private fun captureWindowBitmap(
        node: androidx.compose.ui.test.SemanticsNodeInteraction,
        relFile: String,
    ): Bitmap {
        val instr = InstrumentationRegistry.getInstrumentation()
        instr.waitForIdleSync()
        val winBounds = node.fetchSemanticsNode().boundsInWindow
        val loc = IntArray(2)
        activityRule.activityRule.scenario.onActivity { activity ->
            activity.window.decorView.getLocationOnScreen(loc)
        }
        val left = (loc[0] + winBounds.left).roundToInt()
        val top = (loc[1] + winBounds.top).roundToInt()
        val right = (loc[0] + winBounds.right).roundToInt()
        val bottom = (loc[1] + winBounds.bottom).roundToInt()
        val full = instr.uiAutomation.takeScreenshot()
            ?: error("UiAutomation.takeScreenshot() returned null for $relFile")
        check(left >= 0 && top >= 0 && right <= full.width && bottom <= full.height) {
            "scene root rect $left,$top-$right,$bottom is outside the physical " +
                "screenshot ${full.width}x${full.height} for $relFile"
        }
        // createBitmap aliases the source when the crop covers it entirely
        // (scene rect == physical frame) — recycling `full` would then recycle
        // the returned bitmap and the blank-check would hit a dead buffer.
        val cropped = Bitmap.createBitmap(full, left, top, right - left, bottom - top)
        if (cropped !== full) full.recycle()
        return cropped
    }

    private fun assertNotBlank(bitmap: Bitmap, relFile: String) {
        // Uniformity gate, mirroring the live publisher's PNG lattice: sample
        // a ~64x64 grid and require at least one pixel to differ from the first.
        val w = bitmap.width
        val h = bitmap.height
        val first = bitmap.getPixel(0, 0)
        val stepX = maxOf(1, w / 64)
        val stepY = maxOf(1, h / 64)
        var y = 0
        var different = false
        while (y < h && !different) {
            var x = 0
            while (x < w) {
                if (bitmap.getPixel(x, y) != first) {
                    different = true
                    break
                }
                x += stepX
            }
            y += stepY
        }
        check(different) {
            "captured frame $relFile is uniform — refusing to ship a blank image"
        }
    }

    private fun assertAspect(widthDp: Int, heightDp: Int, bitmap: Bitmap, relFile: String) {
        val expectedWidth = (bitmap.height.toDouble() * widthDp / heightDp).roundToInt()
        check(abs(bitmap.width - expectedWidth) <= 2) {
            "captured frame $relFile is ${bitmap.width}x${bitmap.height}, " +
                "expected aspect ${widthDp}x$heightDp (~${expectedWidth}px wide)"
        }
    }

    companion object {
        private const val TAG = "DocsCapture"
        private const val SIZE_TIMEOUT_MS = 10_000L
        private const val READY_TAG_TIMEOUT_MS = 20_000L
        private const val SIZE_TOLERANCE_PX = 2f

        /**
         * Slack for the preset-vs-window fit check: a preset is capturable when
         * its logical size does not exceed the host window by more than this
         * many dp (the size gate tolerates ±2px, and some displays offer a dp
         * width a fraction below the preset's, e.g. the Pixel 6's 411.4dp
         * against the 412dp phone preset).
         */
        private const val FIT_SLACK_DP = 8f
    }

    private data class WindowDp(val widthDp: Float, val heightDp: Float)

    /**
     * Logical size of the capture activity's window. The host is pinned to
     * the preset's dp size, but [Modifier.size] coerces into the incoming
     * constraints, so presets larger than this window can never measure at
     * their logical size on the current display.
     */
    private fun hostWindowDp(): WindowDp {
        val density = activityRule.density.density
        val px = IntArray(2)
        activityRule.activityRule.scenario.onActivity { activity ->
            val decor = activity.window.decorView
            px[0] = decor.width
            px[1] = decor.height
        }
        check(px[0] > 0 && px[1] > 0) {
            "capture window is not laid out (width=${px[0]} height=${px[1]})"
        }
        return WindowDp(px[0] / density, px[1] / density)
    }
}
