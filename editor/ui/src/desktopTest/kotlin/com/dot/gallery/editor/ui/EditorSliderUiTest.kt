package com.dot.gallery.editor.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The ruler slab's track band and interaction contract: each track kind
 * paints its band inside the slab, the step buttons and readout pill keep
 * their writes, and drag/setProgress land through the same callbacks.
 */
@OptIn(ExperimentalTestApi::class)
class EditorSliderUiTest {

    private class Probe {
        val events = mutableListOf<String>()
        var applied = mutableListOf<Float>()
        var opened = 0
        var reset = 0

        @Composable
        fun slab(
            track: EditorSliderTrack? = null,
            trackGradient: List<Color>? = null
        ) = EditorRulerSlab(
            label = "Exposure",
            value = 0f,
            display = "0.0 EV",
            accent = false,
            range = -1f..1f,
            step = 0.1f,
            summary = "Exposure 0.0 EV",
            description = "Exposure slider",
            enabled = true,
            interactionActive = false,
            identity = "probe",
            sliderTag = "probe.slider",
            valueTag = "probe.value",
            stepKey = "probe",
            track = track,
            trackGradient = trackGradient,
            onOpenEntry = { opened++ },
            onBeginInteraction = { events += "begin" },
            onUpdateInteraction = { events += "update:${it}" },
            onCommitInteraction = { events += "commit" },
            onCancelInteraction = { events += "cancel" },
            onApplyStep = { applied += it },
            onResetValue = { reset++ },
            resetLabel = "Reset Exposure",
            modifier = Modifier.testTag("probe.slab")
        )
    }

    /** The band's sample points: just inside its rounded ends, mid-band height. */
    private fun bandSamples(width: Int, height: Int): Triple<Int, Int, Int> {
        // 412 dp wide → the band insets 60 dp at each end; it rides under
        // the tick ladder, ~38 dp into the row — ≈0.75 of the 73 dp slab
        // (17 dp pill overlap + 56 dp capsule).
        val left = (width * 0.18f).roundToInt()
        val right = (width * 0.82f).roundToInt()
        val y = (height * 0.75f).roundToInt()
        return Triple(left, right, y)
    }

    /** A captured pixel as ARGB, with its channels readable as floats. */
    private class BandPixel(val argb: Int) {
        val red get() = ((argb ushr 16) and 0xFF) / 255f
        val green get() = ((argb ushr 8) and 0xFF) / 255f
        val blue get() = (argb and 0xFF) / 255f
        val luma get() = 0.2126f * red + 0.7152f * green + 0.0722f * blue
    }

    private fun pixel(image: ImageBitmap, x: Int, y: Int) =
        BandPixel(image.toPixelMap()[x, y].toArgb())

    @Test
    fun tonalTrackPaintsTheBandAndKeepsTheSlabLive() =
        runDesktopComposeUiTest(width = 412, height = 240) {
            val probe = Probe()
            setContent {
                TestEditorRoot {
                    probe.slab(track = EditorSliderTrack.Tonal.Exposure)
                }
            }

            onNodeWithTag("probe.slab").assertIsDisplayed()
            val image = onNodeWithTag("probe.slab").captureToImage()
            val (leftX, rightX, bandY) = bandSamples(image.width, image.height)
            val left = pixel(image, leftX, bandY)
            val right = pixel(image, rightX, bandY)
            // Exposure's ramp falls to near-black on the left and rises
            // to white on the right — unmistakably not the capsule fill.
            assertTrue(
                left.red + left.green + left.blue <
                    right.red + right.green + right.blue - 0.5f,
                "tonal band left=${left.red},${left.green},${left.blue} " +
                    "right=${right.red},${right.green},${right.blue}"
            )

            onNodeWithTag("editor.step.plus.probe").performClick()
            assertEquals(listOf(0.1f), probe.applied)
            onNodeWithTag("probe.value").performClick()
            waitUntil(timeoutMillis = 5_000) { probe.opened >= 1 }
            onNodeWithTag("probe.value").performTouchInput {
                down(center)
                up()
                advanceEventTime(80)
                down(center)
                up()
            }
            waitUntil(timeoutMillis = 5_000) { probe.reset >= 1 }
        }

    @Test
    fun gradientTrackPaintsTheBand() =
        runDesktopComposeUiTest(width = 412, height = 240) {
            val probe = Probe()
            setContent {
                TestEditorRoot {
                    probe.slab(
                        trackGradient = listOf(Color(1f, 0f, 0f), Color(0f, 0f, 1f))
                    )
                }
            }

            onNodeWithTag("probe.slab").assertIsDisplayed()
            val image = onNodeWithTag("probe.slab").captureToImage()
            val (leftX, rightX, bandY) = bandSamples(image.width, image.height)
            val left = pixel(image, leftX, bandY)
            val right = pixel(image, rightX, bandY)
            assertTrue(left.red > 0.5f && left.blue < 0.5f, "left not red: $left")
            assertTrue(right.blue > 0.5f && right.red < 0.5f, "right not blue: $right")

            onNodeWithTag("editor.step.minus.probe").performClick()
            assertEquals(listOf(-0.1f), probe.applied)
            onNodeWithTag("probe.value").performClick()
            waitUntil(timeoutMillis = 5_000) { probe.opened >= 1 }
        }

    @Test
    fun dragEmitsBeginSnappedUpdatesAndCommit() =
        runDesktopComposeUiTest(width = 412, height = 240) {
            val probe = Probe()
            setContent {
                TestEditorRoot { probe.slab() }
            }

            onNodeWithTag("probe.slider").assertIsDisplayed()
            onNodeWithTag("probe.slider").performTouchInput {
                down(center)
                moveTo(centerRight)
                up()
            }

            assertEquals("begin", probe.events.firstOrNull())
            assertEquals("commit", probe.events.lastOrNull())
            assertTrue("cancel" !in probe.events)
            val updates = probe.events.filter { it.startsWith("update:") }
            assertTrue(updates.isNotEmpty(), "drag emitted no updates")
            updates.forEach { event ->
                val v = event.removePrefix("update:").toFloat()
                assertTrue(v in -1f..1f, "update $v outside range")
                val snapped = (v / 0.1f).roundToInt() / 10f
                assertTrue(
                    abs(v - snapped) < 0.001f,
                    "update $v is not on the 0.1 step"
                )
            }
            assertTrue(
                updates.any { it.removePrefix("update:").toFloat() > 0.5f },
                "drag never reached the right side: $updates"
            )
        }

    @Test
    fun valueTapOpensEntryAndDoubleTapResets() =
        runDesktopComposeUiTest(width = 412, height = 240) {
            val probe = Probe()
            setContent {
                TestEditorRoot { probe.slab() }
            }

            // The value's combinedClickable holds a lone tap until the
            // double-tap window closes — space the taps inside it.
            onNodeWithTag("probe.value").performTouchInput {
                down(center)
                up()
                advanceEventTime(80)
                down(center)
                up()
            }
            waitUntil(timeoutMillis = 5_000) { probe.reset >= 1 }

            onNodeWithTag("probe.value").performClick()
            waitUntil(timeoutMillis = 5_000) { probe.opened >= 1 }
        }

    @Test
    fun setProgressAppliesOneSteppedValue() =
        runDesktopComposeUiTest(width = 412, height = 240) {
            val probe = Probe()
            setContent {
                TestEditorRoot { probe.slab() }
            }

            onNodeWithTag("probe.slider").performSemanticsAction(
                SemanticsActions.SetProgress
            ) { setProgress -> setProgress(0.5f) }

            assertEquals(listOf(0.5f), probe.applied)
            assertTrue(probe.events.isEmpty(), "setProgress opened a gesture")
        }
}
