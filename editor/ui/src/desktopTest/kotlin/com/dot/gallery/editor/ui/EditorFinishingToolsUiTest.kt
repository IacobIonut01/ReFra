package com.dot.gallery.editor.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.assertTouchHeightIsEqualTo
import androidx.compose.ui.test.assertTouchWidthIsEqualTo
import androidx.compose.ui.test.click
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.dot.gallery.editor.core.model.PhotoCurves
import com.dot.gallery.editor.core.model.PhotoEdits
import com.dot.gallery.editor.core.model.PhotoFinishing
import com.dot.gallery.editor.core.model.PhotoGrain
import com.dot.gallery.editor.core.model.PhotoToneCurve
import com.dot.gallery.editor.core.model.PhotoVignette
import com.dot.gallery.editor.core.model.PhotoWhiteBalance
import com.dot.gallery.editor.core.model.toEdits
import com.dot.gallery.editor.render.photo.PhotoEditorSession
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.collections.immutable.persistentListOf

/**
 * The four finishing tools end to end on a live session: the accessible
 * free-point curve editor (44 dp targets, SetProgress, one-history-step
 * drags, Back/Apply transaction semantics), the white-balance presets and
 * rulers, the tabbed vignette/grain surfaces with their shared ruler and
 * settings rows, the Edits-pane rows a non-neutral finishing lists, and
 * the grain -> CPU-preview gating contract.
 */
@OptIn(ExperimentalTestApi::class)
class EditorFinishingToolsUiTest {

    private var session: PhotoEditorSession? = null

    @AfterTest
    fun tearDown() {
        session?.close()
        session = null
    }

    private fun ComposeUiTest.liveSession(
        widthDp: Float = 412f,
        heightDp: Float = 832f,
        fontScale: Float = 1f
    ): PhotoEditorSession {
        val s = openedSession(samplePixels())
        session = s
        setContent {
            TestEditorRoot {
                CompositionLocalProvider(
                    LocalDensity provides
                        Density(LocalDensity.current.density, fontScale)
                ) {
                    Box(Modifier.size(widthDp.dp, heightDp.dp).fillMaxSize()) {
                        PhotoEditorScreen(
                            session = s,
                            onClose = {},
                            onRetryOpen = { s.open("fixture://editor_sample.jpg") }
                        )
                    }
                }
            }
        }
        awaitImage(s)
        return s
    }

    private fun finishing(s: PhotoEditorSession) =
        s.state.value.document?.finishing

    /** Drags the adjustment sheet from its 220 dp peek to Expanded —
     * below-fold settings rows are only on-screen once it is open. */
    private fun ComposeUiTest.expandToolSheet() {
        onNodeWithTag(TAG_V3_SHEET_HANDLE, useUnmergedTree = true)
            .performTouchInput {
                down(center)
                moveBy(Offset(0f, -400f))
                up()
            }
        waitForIdle()
    }

    // ── Curves ────────────────────────────────────────────────────────

    @Test
    fun curvesToolOpensOnTheIdentityWithAccessibleEndPoints() =
        runDesktopComposeUiTest(width = 412, height = 832) {
        val s = liveSession()
        openPhotoTool(TAG_CATEGORY_CURVES)
        waitUntil(timeoutMillis = 15_000) { s.state.value.toolEditing }

        // Channel tabs: RGB (master) plus the three channels.
        onNodeWithTag("editor.curves.tab.rgb").assertIsDisplayed()
            .assertIsSelected()
        onNodeWithTag("editor.curves.tab.red").assertIsDisplayed()
        onNodeWithTag("editor.curves.tab.green").assertIsDisplayed()
        onNodeWithTag("editor.curves.tab.blue").assertIsDisplayed()
        onNodeWithTag("editor.curves.editor").assertIsDisplayed()
        onNodeWithTag(TAG_CURVES_READOUT)
            .assertTextEquals("Tap the curve to add a point")
        onNodeWithTag(TAG_CURVES_REMOVE).assertIsNotEnabled()
        onNodeWithTag("editor.curves.reset").assertIsNotEnabled()

        // The two end points are 44 dp SetProgress-able targets reporting
        // their output over 0..1.
        listOf(0, 1).forEach { i ->
            val node = onNodeWithTag("editor.curves.point.$i")
            node.assertIsDisplayed()
                .assertTouchWidthIsEqualTo(44.dp)
                .assertTouchHeightIsEqualTo(44.dp)
            assertEquals(
                ProgressBarRangeInfo(current = i.toFloat(), range = 0f..1f, steps = 0),
                node.fetchSemanticsNode().config[SemanticsProperties.ProgressBarRangeInfo]
            )
            node.fetchSemanticsNode().config[SemanticsActions.SetProgress]
        }

        // The header owns the commit; the reset rides above the curve field,
        // never over it.
        val editorBounds = onNodeWithTag("editor.curves.editor").getUnclippedBoundsInRoot()
        val reset = onNodeWithTag("editor.curves.reset").getUnclippedBoundsInRoot()
        assertTrue(
            reset.bottom <= editorBounds.top,
            "reset must sit above the curve field: $reset vs $editorBounds"
        )
        onNodeWithTag(TAG_TOOL_APPLY).assertIsDisplayed()
    }

    @Test
    fun tappingTheCurveAddsOnePointAsOneUndoStep() =
        runDesktopComposeUiTest(width = 412, height = 832) {
        val s = liveSession()
        openPhotoTool(TAG_CATEGORY_CURVES)
        waitUntil(timeoutMillis = 15_000) { s.state.value.toolEditing }

        // The card settles first: a touch aimed at a moving card lands elsewhere.
        waitForIdle()
        onNodeWithTag("editor.curves.editor").performTouchInput {
            click(Offset(width / 2f, height / 2f))
        }
        waitUntil(timeoutMillis = 15_000) {
            finishing(s)?.curves?.master?.points?.size == 3
        }
        // A point tapped onto the identity stays on it: no pixel changes yet.
        assertEquals(true, finishing(s)?.curves?.master?.isNeutral)
        onNodeWithTag(TAG_CURVES_READOUT).assertTextEquals("In 128 · Out 128")
        onNodeWithTag(TAG_CURVES_REMOVE).assertIsEnabled()

        onNodeWithTag(TAG_UNDO).performClick()
        waitUntil(timeoutMillis = 15_000) {
            finishing(s)?.curves?.master?.points?.size == 2
        }
    }

    @Test
    fun tappingAnExistingPointSelectsItWithoutAnUndoStep() =
        runDesktopComposeUiTest(width = 412, height = 832) {
        val s = liveSession()
        openPhotoTool(TAG_CATEGORY_CURVES)
        waitUntil(timeoutMillis = 15_000) { s.state.value.toolEditing }

        onNodeWithTag("editor.curves.editor").performTouchInput {
            click(Offset(width - 2f, 2f))
        }
        waitForIdle()
        onNodeWithTag(TAG_CURVES_READOUT).assertTextEquals("In 255 · Out 255")
        assertEquals(2, finishing(s)?.curves?.master?.points?.size)
        onNodeWithTag(TAG_UNDO).assertIsNotEnabled()
    }

    @Test
    fun curveDragCommitsAsOneUndoStep() =
        runDesktopComposeUiTest(width = 412, height = 832) {
        val s = liveSession()
        openPhotoTool(TAG_CATEGORY_CURVES)
        waitUntil(timeoutMillis = 15_000) { s.state.value.toolEditing }

        // Add a midpoint, then pull it upward across many move events.
        onNodeWithTag("editor.curves.editor").performTouchInput {
            swipe(
                Offset(width / 2f, height / 2f),
                Offset(width / 2f, height / 4f),
                durationMillis = 300
            )
        }
        waitUntil(timeoutMillis = 15_000) {
            val point = finishing(s)?.curves?.master?.points?.getOrNull(1)
            point != null && point.y > 0.6f
        }
        onNodeWithTag(TAG_UNDO).performClick()
        waitUntil(timeoutMillis = 15_000) {
            finishing(s)?.curves?.master == PhotoToneCurve()
        }
    }

    @Test
    fun pullingAPointOffTheGraphRemovesIt() =
        runDesktopComposeUiTest(width = 412, height = 832) {
        val s = liveSession()
        openPhotoTool(TAG_CATEGORY_CURVES)
        waitUntil(timeoutMillis = 15_000) { s.state.value.toolEditing }
        onNodeWithTag("editor.curves.preset.soft").performClick()
        waitUntil(timeoutMillis = 15_000) {
            finishing(s)?.curves?.master?.points?.size == 4
        }

        val graph = onNodeWithTag("editor.curves.editor")
        val soft = finishing(s)!!.curves.master
        graph.performTouchInput {
            val start = Offset(soft.points[1].x * width, (1f - soft.points[1].y) * height)
            down(start)
            moveTo(start + Offset(0f, height + 200f))
        }
        waitForIdle()
        onNodeWithTag(TAG_CURVES_READOUT).assertTextEquals("Release to remove point")
        graph.performTouchInput { up() }
        waitUntil(timeoutMillis = 15_000) {
            finishing(s)?.curves?.master?.points?.size == 3
        }
        // The removal is one step: undo brings the point back where it was.
        onNodeWithTag(TAG_UNDO).performClick()
        waitUntil(timeoutMillis = 15_000) { finishing(s)?.curves?.master == soft }
    }

    @Test
    fun endPointsCannotBePulledOff() =
        runDesktopComposeUiTest(width = 412, height = 832) {
        val s = liveSession()
        openPhotoTool(TAG_CATEGORY_CURVES)
        waitUntil(timeoutMillis = 15_000) { s.state.value.toolEditing }

        onNodeWithTag("editor.curves.editor").performTouchInput {
            swipe(Offset(2f, height - 2f), Offset(2f, height + 300f), durationMillis = 200)
        }
        waitForIdle()
        assertEquals(2, finishing(s)?.curves?.master?.points?.size)
    }

    @Test
    fun pointSetProgressMovesOutputAndResetClears() =
        runDesktopComposeUiTest(width = 412, height = 832) {
        val s = liveSession()
        openPhotoTool(TAG_CATEGORY_CURVES)
        waitUntil(timeoutMillis = 15_000) { s.state.value.toolEditing }

        onNodeWithTag("editor.curves.point.0").performSemanticsAction(
            SemanticsActions.SetProgress
        ) { setProgress -> setProgress(0.2f) }
        waitUntil(timeoutMillis = 15_000) {
            finishing(s)?.curves?.master?.points?.first()?.y == 51f / 255f
        }
        onNodeWithTag("editor.curves.reset").assertIsEnabled().performClick()
        waitUntil(timeoutMillis = 15_000) {
            finishing(s)?.curves?.master == PhotoToneCurve()
        }
    }

    @Test
    fun curveChannelTabEditsItsOwnCurve() =
        runDesktopComposeUiTest(width = 412, height = 832) {
        val s = liveSession()
        openPhotoTool(TAG_CATEGORY_CURVES)
        waitUntil(timeoutMillis = 15_000) { s.state.value.toolEditing }

        onNodeWithTag("editor.curves.tab.red").performClick()
        onNodeWithTag("editor.curves.tab.red").assertIsSelected()
        onNodeWithTag("editor.curves.point.1").performSemanticsAction(
            SemanticsActions.SetProgress
        ) { setProgress -> setProgress(0.6f) }
        waitUntil(timeoutMillis = 15_000) {
            finishing(s)?.curves?.red?.points?.last()?.y == 153f / 255f
        }
        // The other channels stay neutral.
        assertEquals(true, finishing(s)?.curves?.master?.isNeutral)
        assertEquals(true, finishing(s)?.curves?.green?.isNeutral)
        assertEquals(true, finishing(s)?.curves?.blue?.isNeutral)
    }

    @Test
    fun presetsApplyToTheOpenChannelAndMarkThemselves() =
        runDesktopComposeUiTest(width = 412, height = 832) {
        val s = liveSession()
        openPhotoTool(TAG_CATEGORY_CURVES)
        waitUntil(timeoutMillis = 15_000) { s.state.value.toolEditing }

        onNodeWithTag("editor.curves.preset.fade").performScrollTo().performClick()
        waitUntil(timeoutMillis = 15_000) {
            finishing(s)?.curves?.master?.points?.first()?.y == 34f / 255f
        }
        onNodeWithTag("editor.curves.preset.fade").assertIsSelected()
        onNodeWithTag("editor.curves.preset.linear").performScrollTo().performClick()
        waitUntil(timeoutMillis = 15_000) {
            finishing(s)?.curves?.master == PhotoToneCurve()
        }
        onNodeWithTag("editor.curves.preset.linear").assertIsSelected()
    }

    @Test
    fun autoStretchesToTheChannelRangeWhateverCurveIsOpen() =
        runDesktopComposeUiTest(width = 412, height = 832) {
        val s = liveSession()
        openPhotoTool(TAG_CATEGORY_CURVES)
        waitUntil(timeoutMillis = 15_000) { s.state.value.toolEditing }

        onNodeWithTag("editor.curves.preset.auto").performScrollTo().performClick()
        waitUntil(timeoutMillis = 15_000) {
            finishing(s)?.curves?.master != PhotoToneCurve()
        }
        val first = finishing(s)!!.curves.master
        assertEquals(2, first.points.size)
        // Auto reads what reaches the curves, so applying it twice lands on
        // the same curve instead of stretching its own result.
        onNodeWithTag("editor.curves.preset.fade").performScrollTo().performClick()
        waitUntil(timeoutMillis = 15_000) {
            finishing(s)?.curves?.master?.points?.first()?.y == 34f / 255f
        }
        onNodeWithTag("editor.curves.preset.auto").performScrollTo().performClick()
        waitUntil(timeoutMillis = 15_000) { finishing(s)?.curves?.master == first }
    }

    @Test
    fun wideSheetAddsSteppersAndAPresetGrid() =
        runDesktopComposeUiTest(width = 800, height = 1280) {
        val s = liveSession(widthDp = 800f, heightDp = 1280f)
        openPhotoTool(TAG_CATEGORY_CURVES)
        waitUntil(timeoutMillis = 15_000) { s.state.value.toolEditing }

        onNodeWithTag("editor.curves.stepper.input.value").assertTextEquals("–")
        onNodeWithTag("editor.curves.stepper.input.plus").assertIsNotEnabled()
        onNodeWithTag("editor.curves.editor").performTouchInput {
            click(Offset(width / 2f, height / 2f))
        }
        waitUntil(timeoutMillis = 15_000) {
            finishing(s)?.curves?.master?.points?.size == 3
        }
        val before = finishing(s)!!.curves.master.points[1]
        onNodeWithTag("editor.curves.stepper.output.plus").assertIsEnabled().performClick()
        waitUntil(timeoutMillis = 15_000) {
            finishing(s)?.curves?.master?.points?.get(1)?.y != before.y
        }
        assertEquals(before.x, finishing(s)!!.curves.master.points[1].x)
    }

    @Test
    fun backCancelsTheWholeFinishingTransaction() =
        runDesktopComposeUiTest(width = 412, height = 832) {
        val s = liveSession()
        openPhotoTool(TAG_CATEGORY_CURVES)
        waitUntil(timeoutMillis = 15_000) { s.state.value.toolEditing }
        onNodeWithTag("editor.curves.point.1").performSemanticsAction(
            SemanticsActions.SetProgress
        ) { setProgress -> setProgress(0.6f) }
        waitUntil(timeoutMillis = 15_000) {
            finishing(s)?.curves?.master?.points?.last()?.y == 153f / 255f
        }

        onNodeWithTag(TAG_BACK).performClick()
        waitUntil(timeoutMillis = 15_000) { !s.state.value.toolEditing }
        assertEquals(true, finishing(s)?.isNeutral)
    }

    // ── White balance ─────────────────────────────────────────────────

    @Test
    fun whiteBalancePresetsAndRulersReflectTheRecipe() =
        runDesktopComposeUiTest(width = 412, height = 832) {
        val s = liveSession()
        openPhotoTool(TAG_CATEGORY_WHITE_BALANCE)
        waitUntil(timeoutMillis = 15_000) { s.state.value.toolEditing }

        // All five presets paint; Auto = neutral matches the untouched
        // recipe and starts selected. The strip scrolls horizontally at
        // this width, so each chip is scrolled into view before it is
        // measured.
        for (key in listOf(
            "auto", "daylight", "cloudy", "tungsten", "fluorescent"
        )) {
            onNodeWithTag("editor.wb.preset.$key")
                .performScrollTo()
                .assertIsDisplayed()
        }
        onNodeWithTag("editor.wb.preset.auto").assertIsSelected()

        // Tungsten lands 3200 K / 0 through one applyEdits.
        onNodeWithTag("editor.wb.preset.tungsten")
            .performScrollTo()
            .performClick()
        waitUntil(timeoutMillis = 15_000) {
            finishing(s)?.whiteBalance ==
                PhotoWhiteBalance(3200f, 0f)
        }
        onNodeWithTag("editor.wb.preset.tungsten")
            .performScrollTo()
            .assertIsSelected()
        onNodeWithTag("editor.value.temperature")
            .assertIsDisplayed()
            .assertTextHas("3200")

        // The temperature ruler speaks kelvin — SetProgress commits a
        // step the preset chips re-evaluate against (5500 = Daylight).
        onNodeWithTag("editor.slider.temperature").performSemanticsAction(
            SemanticsActions.SetProgress
        ) { setProgress -> setProgress(5500f) }
        waitUntil(timeoutMillis = 15_000) {
            finishing(s)?.whiteBalance?.temperatureKelvin == 5500f
        }
        onNodeWithTag("editor.wb.preset.daylight")
            .performScrollTo()
            .assertIsSelected()

        // Tint is an independent -100..100 field.
        onNodeWithTag("editor.slider.tint").performSemanticsAction(
            SemanticsActions.SetProgress
        ) { setProgress -> setProgress(-30f) }
        waitUntil(timeoutMillis = 15_000) {
            finishing(s)?.whiteBalance?.tint == -30f
        }
        // Both visible per-parameter resets stay honest.
        onNodeWithTag("editor.reset.temperature").assertIsDisplayed()
        onNodeWithTag("editor.reset.tint").assertIsDisplayed()
    }

    // ── Vignette ──────────────────────────────────────────────────────

    @Test
    fun vignetteTabsDriveTheSharedRulerAndRowsReflectEveryField() =
        runDesktopComposeUiTest(width = 412, height = 832) {
        val s = liveSession()
        openPhotoTool(TAG_CATEGORY_VIGNETTE)
        waitUntil(timeoutMillis = 15_000) { s.state.value.toolEditing }
        expandToolSheet()

        // Five tabs, Amount selected by default; five settings rows
        // mirror the whole recipe — the sheet peeks, so rows past the
        // fold scroll into view first.
        for (key in listOf(
            "vignetteAmount", "vignetteMidpoint", "vignetteFeather",
            "vignetteRoundness", "vignetteHighlights"
        )) {
            onNodeWithTag("editor.vignette.tab.$key").assertIsDisplayed()
            onNodeWithTag("editor.vignette.row.$key")
                .performScrollTo()
                .assertIsDisplayed()
        }
        onNodeWithTag("editor.vignette.tab.vignetteAmount")
            .assertIsSelected()
        onNodeWithTag("editor.vignette.row.vignetteAmount")
            .assertIsSelected()
        // The shared ruler spans the active tab's -100..100.
        val rulerInfo = onNodeWithTag("editor.slider.vignetteAmount")
            .fetchSemanticsNode()
            .config[SemanticsProperties.ProgressBarRangeInfo]
        assertEquals(-100f..100f, rulerInfo.range)
        onNodeWithTag("editor.vignette.row.vignetteMidpoint")
            .assertTextHas("50")

        // -40 on the Amount ruler lands amount -.4.
        onNodeWithTag("editor.slider.vignetteAmount")
            .performSemanticsAction(SemanticsActions.SetProgress) {
                it(-40f)
            }
        waitUntil(timeoutMillis = 15_000) {
            finishing(s)?.vignette?.amount == -0.4f
        }
        onNodeWithTag("editor.vignette.row.vignetteAmount")
            .assertTextHas("-40")

        // A row tap selects the tab and re-points the shared ruler.
        onNodeWithTag("editor.vignette.row.vignetteRoundness")
            .performScrollTo()
            .performClick()
        onNodeWithTag("editor.vignette.tab.vignetteRoundness")
            .assertIsSelected()
        onNodeWithTag("editor.slider.vignetteRoundness")
            .assertIsDisplayed()
    }

    // ── Grain ─────────────────────────────────────────────────────────

    @Test
    fun grainTabsDriveTheSharedRulerAndRowsReflectEveryField() =
        runDesktopComposeUiTest(width = 412, height = 832) {
        val s = liveSession()
        openPhotoTool(TAG_CATEGORY_GRAIN)
        waitUntil(timeoutMillis = 15_000) { s.state.value.toolEditing }
        expandToolSheet()

        for (key in listOf("grainAmount", "grainSize", "grainRoughness")) {
            onNodeWithTag("editor.grain.tab.$key").assertIsDisplayed()
            onNodeWithTag("editor.grain.row.$key")
                .performScrollTo()
                .assertIsDisplayed()
        }
        onNodeWithTag("editor.grain.tab.grainAmount").assertIsSelected()
        // Size and Roughness rows report their display-domain defaults.
        onNodeWithTag("editor.grain.row.grainSize").assertTextHas("24")
        onNodeWithTag("editor.grain.row.grainRoughness")
            .assertTextHas("35")

        onNodeWithTag("editor.grain.tab.grainRoughness").performClick()
        onNodeWithTag("editor.slider.grainRoughness")
            .performSemanticsAction(SemanticsActions.SetProgress) {
                it(80f)
            }
        waitUntil(timeoutMillis = 15_000) {
            finishing(s)?.grain?.roughness == 0.8f
        }
        onNodeWithTag("editor.grain.row.grainRoughness")
            .assertTextHas("80")
    }

    /**
     * The shader-gating contract end to end: an active grain recipe
     * hands the canvas to the CPU preview — the runtime shader draws
     * nothing while grain is non-zero, and the CPU pipeline keeps
     * publishing the edited frame. Removing the amount returns the
     * canvas to the shader.
     */
    @Test
    fun activeGrainHandsTheCanvasToTheCpuPreview() =
        runDesktopComposeUiTest(width = 412, height = 832) {
        val draws = java.util.concurrent.atomic.AtomicInteger()
        // A pure fake — the desktop Skia factory is not required to
        // produce a renderer in the headless harness.
        photoShaderRendererFactory = { _, _ ->
            object : PhotoShaderRenderer {
                override fun draw(
                    scope: androidx.compose.ui.graphics.drawscope.DrawScope,
                    edits: PhotoEdits
                ): Boolean {
                    draws.incrementAndGet()
                    return true
                }

                override fun close() {}
            }
        }
        try {
            val s = liveSession()
            // Let the shader own a few frames first.
            waitUntil(timeoutMillis = 15_000) { draws.get() > 0 }
            val shaderDraws = draws.get()

            runOnUiThread {
                s.applyEdits(
                    "Grain",
                    PhotoEdits(
                        finishing = PhotoFinishing(
                            grain = PhotoGrain(amount = 0.4f)
                        )
                    )
                )
            }
            // The CPU preview republishes the grain recipe while the
            // shader's draw count stays frozen — no approximated noise.
            val before = s.state.value.preview
            waitUntil(timeoutMillis = 15_000) {
                val st = s.state.value
                st.document?.finishing?.grain?.amount == 0.4f &&
                    st.preview != null && st.preview !== before
            }
            assertEquals(
                shaderDraws, draws.get(),
                "shader drew a frame while grain was active"
            )

            // Back to neutral: the shader resumes ownership.
            runOnUiThread {
                s.applyEdits("Reset", PhotoEdits())
            }
            waitUntil(timeoutMillis = 15_000) {
                s.state.value.document?.finishing?.isNeutral == true &&
                    draws.get() > shaderDraws
            }
        } finally {
            photoShaderRendererFactory =
                { source, onFailure ->
                    createPhotoShaderRenderer(source, onFailure)
                }
        }
    }

    // ── Edits summary + reachability ──────────────────────────────────

    @Test
    fun editsPaneListsEveryNonNeutralFinishingField() =
        runDesktopComposeUiTest(width = 412, height = 832) {
        val s = liveSession()
        runOnUiThread {
            s.applyEdits(
                "Finish",
                PhotoEdits(
                    finishing = PhotoFinishing(
                        curves = PhotoCurves(
                            master = PhotoToneCurve.ofQuarterOrdinates(
                    listOf(0f, .3f, .55f, .8f, 1f)
                )
                        ),
                        whiteBalance = PhotoWhiteBalance(3200f, 12f),
                        vignette = PhotoVignette(amount = -.4f),
                        grain = PhotoGrain(amount = .3f)
                    )
                )
            )
        }
        waitUntil(timeoutMillis = 15_000) {
            s.state.value.document?.finishing?.isNeutral == false
        }

        // The Edits pane rides behind a context header's More menu.
        openPhotoTool(TAG_CATEGORY_LIGHT)
        waitUntil(timeoutMillis = 15_000) { s.state.value.toolEditing }
        openPhonePane(TAG_PANEL_EDITS)

        onNodeWithText("Curves · RGB").assertIsDisplayed()
        onNodeWithText("Temperature").assertIsDisplayed()
        onNodeWithText("3200 K").assertIsDisplayed()
        onNodeWithText("Tint").assertIsDisplayed()
        onNodeWithText("+12").assertIsDisplayed()
        onNodeWithText("-40").assertIsDisplayed()
        // Grain amount 30 lists as an unsigned display value.
        onNodeWithText("30.00").assertIsDisplayed()
        // Fields still at their defaults list no row at all.
        onNodeWithText("Roughness").assertDoesNotExist()
        onNodeWithText("Size").assertDoesNotExist()
        onNodeWithText("Midpoint").assertDoesNotExist()
    }

    @Test
    fun finishingSurfacesStayReachableAt2x() =
        runDesktopComposeUiTest(width = 412, height = 832) {
        val s = liveSession(fontScale = 2f)
        openPhotoTool(TAG_CATEGORY_VIGNETTE)
        waitUntil(timeoutMillis = 15_000) { s.state.value.toolEditing }

        // Each control stays reachable by scrolling the sheet; enlarged
        // text need not fit both ends of the inspector simultaneously.
        onNodeWithTag(TAG_V3_SHEET_HANDLE, useUnmergedTree = true)
            .performTouchInput {
                down(center)
                moveBy(Offset(0f, -400f))
                up()
            }
        onNodeWithTag("editor.vignette.row.vignetteHighlights")
            .performScrollTo()
            .assertIsDisplayed()
        onNodeWithTag("editor.slider.vignetteAmount")
            .performScrollTo()
            .assertIsDisplayed()
    }
}
