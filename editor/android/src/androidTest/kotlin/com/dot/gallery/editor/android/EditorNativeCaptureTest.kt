package com.dot.gallery.editor.android

import android.app.UiAutomation
import android.content.ContentValues
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.graphics.Bitmap
import android.net.Uri
import android.provider.MediaStore
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTouchHeightIsEqualTo
import androidx.compose.ui.test.assertTouchWidthIsEqualTo
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.dot.gallery.editor.render.photo.PhotoEditorState
import java.io.File
import java.io.FileInputStream
import kotlin.math.abs
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Native captures of the real editor activity: full-screen screenshots via
 * [UiAutomation.takeScreenshot] (real SurfaceFlinger frames, system bars
 * included) across portrait, landscape, a 2x font scale and the light
 * scheme.
 *
 * Readiness is explicit, not elapsed time: the session has a document, the
 * `editor.image` semantics node exists, the requested configuration
 * (font scale / night mask / orientation) is live on the activity's
 * resources, the window has focus, and the accessibility stream is idle.
 * System animations are switched off for the batch so no app-transition
 * frame can leak the launcher into a capture; every device setting that is
 * touched is saved first and restored in tearDown.
 *
 * Also covers the pointer-cancel gesture path — the Skiko test dispatcher
 * cannot inject a cancel event (InputDispatcher.enqueueCancel is a no-op on
 * desktop), so the real `ACTION_CANCEL` path is verified here instead.
 */
@RunWith(AndroidJUnit4::class)
class EditorNativeCaptureTest {

    @get:Rule
    val composeRule = createEmptyComposeRule()

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val resolver = context.contentResolver
    private var sourceUri: Uri? = null
    private val createdUris = mutableListOf<Uri>()
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
        sourceUri = insertImage("editor-capture-source.jpg")

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
        for (uri in createdUris) {
            resolver.delete(uri, null, null)
        }
        sourceUri = null
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

    /** Publishes the bundled JPEG through MediaStore as a real content:// source. */
    private fun insertImage(displayName: String): Uri {
        val bytes = instrumentation.context.assets
            .open("editor_sample.jpg").use { it.readBytes() }
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, displayName)
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            put(MediaStore.Images.Media.IS_PENDING, 1)
            put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/EditorCaptureTest")
        }
        val uri = resolver.insert(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values
        )!!
        resolver.openOutputStream(uri)!!.use { it.write(bytes) }
        values.clear()
        values.put(MediaStore.Images.Media.IS_PENDING, 0)
        resolver.update(uri, values, null, null)
        createdUris += uri
        return uri
    }

    private fun launch(): ActivityScenario<EditorActivity> =
        ActivityScenario.launch(
            EditorIntents.create(context, sourceUri!!, EditorActivity::class.java)
        )

    /** Pushes a font scale and waits for the activity config to adopt it. */
    private fun setFontScale(
        scenario: ActivityScenario<EditorActivity>,
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
        scenario: ActivityScenario<EditorActivity>,
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

    private fun awaitReady(scenario: ActivityScenario<EditorActivity>) {
        composeRule.waitUntil(30_000) {
            var met = false
            scenario.onActivity { activity ->
                val state = activity.viewModel.session.state.value
                met = state.document != null && !state.opening && !state.rendering
            }
            met
        }
        // The compose tree must also have landed the image node.
        composeRule.waitUntil(15_000) {
            composeRule.onAllNodes(hasTestTag("editor.image"))
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
        scenario: ActivityScenario<EditorActivity>,
        expectSurround: Boolean = true
    ) {
        var focused = false
        scenario.onActivity { focused = it.hasWindowFocus() }
        assertTrue("editor lost window focus before $name", focused)
        val bitmap = instrumentation.uiAutomation.takeScreenshot()
        assertNotNull("takeScreenshot returned null", bitmap)
        assertTrue(bitmap!!.width > 0 && bitmap.height > 0)
        // The Layers surface fits the composite to its preview region —
        // no letterbox exists for the surround heuristic to sample, so
        // window-focus plus the semantics assertions are the guard there.
        if (expectSurround) {
            assertEditorSurface(bitmap, name)
        }
        val dir = context.getExternalFilesDir("captures")!!
        dir.mkdirs()
        File(dir, "$name.png").outputStream().use {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
        bitmap.recycle()
    }

    /**
     * The canvas surround is always the uniform near-black #141517 in every
     * theme. Sample a point between the canvas edge and the photo (using the
     * real semantics bounds so orientation doesn't matter) — if a capture
     * caught the launcher mid-transition this pixel carries wallpaper or
     * icons instead.
     *
     * Every letterbox/pillarbox band plus the canvas corners is a candidate:
     * a selected layer's outline can extend past the base bounds into one
     * band, but a foreign surface fails all of them.
     */
    private fun assertEditorSurface(bitmap: Bitmap, name: String) {
        val canvas = composeRule.onNodeWithTag("editor.canvas")
            .fetchSemanticsNode().boundsInWindow
        val image = composeRule.onNodeWithTag("editor.image")
            .fetchSemanticsNode().boundsInWindow
        val cx = ((canvas.left + canvas.right) / 2f).toInt()
        val cy = ((canvas.top + canvas.bottom) / 2f).toInt()
        val candidates = buildList {
            if (image.top - canvas.top > 16) {
                add(cx to ((canvas.top + image.top) / 2f).toInt())
            }
            if (canvas.bottom - image.bottom > 16) {
                add(cx to ((image.bottom + canvas.bottom) / 2f).toInt())
            }
            if (image.left - canvas.left > 16) {
                add(((canvas.left + image.left) / 2f).toInt() to cy)
            }
            if (canvas.right - image.right > 16) {
                add(((image.right + canvas.right) / 2f).toInt() to cy)
            }
            add((canvas.left + 10f).toInt() to (canvas.top + 10f).toInt())
            add((canvas.right - 10f).toInt() to (canvas.top + 10f).toInt())
            add((canvas.left + 10f).toInt() to (canvas.bottom - 10f).toInt())
            add((canvas.right - 10f).toInt() to (canvas.bottom - 10f).toInt())
        }
        val detail = StringBuilder()
        for ((px, py) in candidates) {
            var maxLum = 0
            var maxSpread = 0
            for (dx in -8..8 step 8) {
                for (dy in -8..8 step 8) {
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
            detail.append("($px,$py lum=$maxLum spread=$maxSpread) ")
            if (maxLum < 90 && maxSpread < 48) {
                return
            }
        }
        org.junit.Assert.fail(
            "$name: no uniform dark canvas surround found — $detail" +
                "screenshot likely caught a foreign surface"
        )
    }

    /**
     * Opens the Light tool through the catalogue — the collapsed home has
     * no sliders until Tools → Light lands them in the context pane.
     */
    private fun openLightTool() {
        composeRule.onNodeWithTag("editor.catalogue").performClick()
        composeRule.onNodeWithTag("editor.category.light").performClick()
        composeRule.waitForIdle()
    }

    private fun state(scenario: ActivityScenario<EditorActivity>): PhotoEditorState? {
        var current: PhotoEditorState? = null
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
            screenshot("android-portrait", scenario)
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
            screenshot("android-landscape", scenario)
        }
    }

    @Test
    fun captureLargeFont() {
        launch().use { scenario ->
            setNightMode(scenario, night = true)
            setFontScale(scenario, 2.0f)
            awaitReady(scenario)
            // A real tool open, so the 2x pass reviews context controls —
            // the collapsed home would exercise nothing but the header.
            openLightTool()
            screenshot("android-largefont", scenario)
        }
    }

    @Test
    fun captureLight() {
        launch().use { scenario ->
            setNightMode(scenario, night = false)
            setFontScale(scenario, 1.0f)
            awaitReady(scenario)
            screenshot("android-light", scenario)
        }
    }

    @Test
    fun pointerCancelRestoresOriginal() {
        launch().use { scenario ->
            setFontScale(scenario, 1.0f)
            awaitReady(scenario)
            // The collapsed home exposes no sliders — open the Light tool
            // through the catalogue first.
            openLightTool()
            val slider = composeRule.onNodeWithTag("editor.slider.exposure")
            slider.performTouchInput {
                down(center)
                moveTo(center + Offset(center.x * 0.4f, 0f))
                moveTo(centerRight)
                cancel()
            }
            // The cancelled interaction restores the recipe; the tool's
            // outer draft (isDrafting) stays open for the tool's lifetime.
            composeRule.waitUntil(10_000) {
                state(scenario)?.let { !it.interactionActive } == true
            }
            val settled = state(scenario)
            assertEquals(
                0f,
                settled?.document?.adjustments?.exposureStops ?: -1f,
                0.001f
            )
            assertFalse(settled?.canUndo == true)

            // And a fresh drag still commits normally afterwards.
            slider.performTouchInput {
                down(center)
                moveTo(center + Offset(center.x * 0.4f, 0f))
                moveTo(centerRight)
                up()
            }
            composeRule.waitUntil(10_000) {
                state(scenario)?.let {
                    !it.interactionActive &&
                        it.document?.adjustments?.exposureStops != 0f
                } == true
            }
        }
    }

    @Test
    fun layersSurfaceShowsTheImportedLayerSelected() {
        launch().use { scenario ->
            setFontScale(scenario, 1.0f)
            awaitReady(scenario)
            // A second real content image through the same
            // session.addImageLayer seam the OpenDocument result drives —
            // never the system picker UI.
            val layerSource = insertImage("editor-capture-layer.jpg")
            scenario.onActivity {
                it.viewModel.session.addImageLayer(layerSource.toString())
            }
            composeRule.waitUntil(15_000) {
                state(scenario)?.let {
                    !it.importingLayer &&
                        (it.document?.layers?.size ?: 0) == 1
                } == true
            }
            val layerId = requireNotNull(
                state(scenario)?.document?.layers?.single()?.id
            )

            // The collapsed Layers inspector: the imported row selects,
            // the canvas draws its outline, and Add/Done keep their
            // canonical touch targets at 1x.
            composeRule.onNodeWithTag("editor.layers").performClick()
            composeRule.waitForIdle()
            composeRule.onNodeWithTag("editor.layer.row.$layerId")
                .performClick()
                .assertIsSelected()
                .assertTouchHeightIsEqualTo(64.dp)
            composeRule.onNodeWithTag("editor.layer.base").assertIsDisplayed()
            composeRule.onNodeWithTag("editor.layer.selection").assertIsDisplayed()
            composeRule.onNodeWithTag("editor.layer.add")
                .assertIsDisplayed()
                .assertTouchHeightIsEqualTo(48.dp)
            composeRule.onNodeWithTag("editor.layers.done")
                .assertIsDisplayed()
                .assertTouchWidthIsEqualTo(72.dp)
                .assertTouchHeightIsEqualTo(48.dp)
            screenshot("android-layers", scenario, expectSurround = false)
        }
    }
}
