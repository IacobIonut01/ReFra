@file:OptIn(ExperimentalMaterial3Api::class)

package com.dot.gallery.editor.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SheetValue
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.LookaheadScope
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking

/**
 * Regression coverage for [EditorSheetLayout]: the compact photo sheets
 * replaced Material3's `BottomSheetScaffold`, whose placement read the
 * sheet offset unguarded and crashed with "The offset was read before
 * being initialized" whenever a pass measured before the anchors
 * initialised. These tests pin the guarantees the replacement makes:
 * pre-layout commands are kept, removal and resize never read an unset
 * offset, and nested scrolling still drags the sheet.
 */
@OptIn(ExperimentalTestApi::class)
class EditorSheetLayoutTest {

    private val allValues =
        setOf(SheetValue.Hidden, SheetValue.PartiallyExpanded, SheetValue.Expanded)

    private fun sheetContent(tag: String = "sheet-body"): @Composable ColumnScope.() -> Unit = {
        Box(
            Modifier
                .fillMaxWidth()
                .height(300.dp)
                .background(MaterialTheme.colorScheme.surface)
                .testTag(tag)
        )
    }

    @Test
    fun expandBeforeFirstLayoutInsideLookaheadSettlesExpanded() =
        runDesktopComposeUiTest(width = 400, height = 800) {
            var density = 1f
            val state = EditorSheetState(SheetValue.Hidden, allValues)
            // Issued before composition and the first frame: the request
            // must be kept as the pending target, not thrown away or crash.
            runBlocking { state.expand() }
            setContent {
                MaterialTheme {
                    density = LocalDensity.current.density
                    LookaheadScope {
                        EditorSheetLayout(
                            state = state,
                            peekHeight = 100.dp,
                            swipeEnabled = true,
                            sheet = sheetContent(),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            Box(Modifier.fillMaxSize())
                        }
                    }
                }
            }
            waitForIdle()
            assertEquals(SheetValue.Expanded, state.currentValue)
            // Expanded anchor = host height - sheet height = (800 - 300) dp.
            assertEquals(500f * density, state.offsetOrNull!!, 1f)
        }

    @Test
    fun offsetIsNullUntilTheFirstLayout() = runDesktopComposeUiTest(width = 400, height = 800) {
        var preLayoutOffset: Float? = 0f
        var captured = false
        val state = EditorSheetState(SheetValue.Hidden, allValues)
        setContent {
            MaterialTheme {
                // Composition always precedes the first measure: the first
                // composition read is the pre-layout value.
                if (!captured) {
                    preLayoutOffset = state.offsetOrNull
                    captured = true
                }
                EditorSheetLayout(
                    state = state,
                    peekHeight = 100.dp,
                    swipeEnabled = true,
                    sheet = sheetContent(),
                    modifier = Modifier.fillMaxSize()
                ) {
                    Box(Modifier.fillMaxSize())
                }
            }
        }
        kotlin.test.assertNull(preLayoutOffset)
        waitForIdle()
        assertNotNull(state.offsetOrNull)
    }

    @Test
    fun recomposeAndResizeKeepTheExpandedAnchor() =
        runDesktopComposeUiTest(width = 400, height = 800) {
            var density = 1f
            val state = EditorSheetState(SheetValue.Expanded, allValues)
            var show by mutableStateOf(true)
            var hostHeight by mutableIntStateOf(800)
            setContent {
                MaterialTheme {
                    density = LocalDensity.current.density
                    Box(Modifier.size(400.dp, hostHeight.dp)) {
                        if (show) {
                            EditorSheetLayout(
                                state = state,
                                peekHeight = 100.dp,
                                swipeEnabled = true,
                                sheet = sheetContent(),
                                modifier = Modifier.fillMaxSize()
                            ) {
                                Box(Modifier.fillMaxSize())
                            }
                        }
                    }
                }
            }
            waitForIdle()
            assertEquals(SheetValue.Expanded, state.currentValue)
            // (800 - 300) dp.
            assertEquals(500f * density, state.offsetOrNull!!, 1f)

            // Leave composition while Expanded, then return under a
            // shorter host: the same state must re-anchor without ever
            // reading an unset offset.
            runOnUiThread { show = false }
            waitForIdle()
            runOnUiThread {
                hostHeight = 600
                show = true
            }
            waitForIdle()
            assertEquals(SheetValue.Expanded, state.currentValue)
            // (600 - 300) dp.
            assertEquals(300f * density, state.offsetOrNull!!, 1f)
            onNodeWithTag("sheet-body").assertIsDisplayed()
        }

    @Test
    fun nestedScrollDragCollapsesTheSheet() =
        runDesktopComposeUiTest(width = 400, height = 800) {
            val state = EditorSheetState(SheetValue.Expanded, allValues)
            setContent {
                MaterialTheme {
                    EditorSheetLayout(
                        state = state,
                        peekHeight = 100.dp,
                        swipeEnabled = true,
                        sheet = {
                            Column(
                                Modifier
                                    .fillMaxWidth()
                                    .height(400.dp)
                                    .verticalScroll(rememberScrollState())
                                    .testTag("sheet-scroll")
                            ) {
                                // Shorter than the viewport: the body
                                // cannot scroll, so every drag delta
                                // reaches the sheet as overscroll.
                                Column(Modifier.fillMaxWidth().height(200.dp)) {}
                            }
                        },
                        modifier = Modifier.fillMaxSize()
                    ) {
                        Box(Modifier.fillMaxSize())
                    }
                }
            }
            waitForIdle()
            assertEquals(SheetValue.Expanded, state.currentValue)
            val expandedOffset = state.offsetOrNull!!

            // Pull down inside the scrolling sheet body while it sits at
            // the top: the body cannot scroll up, so the whole delta
            // overshoots into the sheet and collapses it toward the
            // lower anchors. No idle gap before release — a paused
            // pointer reports zero fling velocity and the sheet settles
            // back on the nearest anchor.
            onNodeWithTag("sheet-scroll").performTouchInput {
                down(center)
                repeat(8) { moveBy(Offset(0f, 40f)) }
                up()
            }
            waitForIdle()
            assertTrue(
                state.currentValue != SheetValue.Expanded,
                "nested-scroll drag should leave Expanded, was ${state.currentValue} " +
                    "offset=${state.offsetOrNull} expandedWas=$expandedOffset"
            )
            assertTrue(
                (state.offsetOrNull ?: Float.NaN) > expandedOffset,
                "sheet offset should grow past the Expanded anchor"
            )
        }

    @Test
    fun expansionGestureNeverScrollsTheSheetBody() =
        runDesktopComposeUiTest(width = 400, height = 800) {
            // The card body is a verticalScroll taller than the expanded
            // sheet: the gesture that expands the sheet must not leak its
            // overshoot into the body's scroll position.
            val state = EditorSheetState(SheetValue.PartiallyExpanded, allValues)
            var scrollState: androidx.compose.foundation.ScrollState? = null
            setContent {
                MaterialTheme {
                    val scroll = rememberScrollState()
                    scrollState = scroll
                    EditorSheetLayout(
                        state = state,
                        peekHeight = 100.dp,
                        swipeEnabled = true,
                        sheet = {
                            Column(Modifier.fillMaxWidth().height(600.dp)) {
                                Box(
                                    Modifier
                                        .fillMaxWidth()
                                        .height(40.dp)
                                        .background(MaterialTheme.colorScheme.surface)
                                )
                                Column(
                                    Modifier
                                        .fillMaxWidth()
                                        .weight(1f)
                                        .verticalScroll(scroll)
                                        .testTag("sheet-scroll")
                                ) {
                                    Box(Modifier.fillMaxWidth().height(1400.dp))
                                }
                            }
                        },
                        modifier = Modifier.fillMaxSize()
                    ) {
                        Box(Modifier.fillMaxSize())
                    }
                }
            }
            waitForIdle()
            val scroll = scrollState!!
            val expandedOffset = run {
                // Expanded anchor = host - sheet height = 800 - 600 = 200 px.
                200f
            }

            // (a) A long swipe up from peek: the sheet travels to Expanded
            // and the whole gesture stays the sheet's — the body scroll
            // must remain at 0.
            onRoot().performTouchInput {
                down(Offset(200f, 770f))
                repeat(12) { moveBy(Offset(0f, -60f), delayMillis = 20) }
                up()
            }
            mainClock.advanceTimeBy(900)
            waitForIdle()
            assertEquals(SheetValue.Expanded, state.currentValue)
            assertEquals(0, scroll.value, "expansion gesture scrolled the body")

            // (b) A fresh swipe up on the already-expanded sheet scrolls
            // the body like any other scroll gesture.
            onRoot().performTouchInput {
                down(Offset(200f, 600f))
                repeat(8) { moveBy(Offset(0f, -40f), delayMillis = 20) }
                up()
            }
            mainClock.advanceTimeBy(900)
            waitForIdle()
            assertTrue(
                scroll.value > 0,
                "swipe on expanded sheet should scroll the body, was ${scroll.value}"
            )

            // (c) Down swipes inside the body scroll it back to the top
            // first; once it cannot scroll further the leftover deltas
            // collapse the sheet. The body's scroll position is unknown
            // after (b)'s fling, so swipe until it reads 0, then keep
            // pulling to collapse.
            repeat(5) {
                if (scroll.value == 0) return@repeat
                onRoot().performTouchInput {
                    down(Offset(200f, 600f))
                    repeat(7) { moveBy(Offset(0f, 50f), delayMillis = 20) }
                    up()
                }
                mainClock.advanceTimeBy(500)
                waitForIdle()
            }
            assertEquals(0, scroll.value, "down swipe did not return the body to top")
            onRoot().performTouchInput {
                down(Offset(200f, 600f))
                repeat(10) { moveBy(Offset(0f, 50f), delayMillis = 20) }
                up()
            }
            mainClock.advanceTimeBy(900)
            waitForIdle()
            assertTrue(
                state.currentValue != SheetValue.Expanded ||
                    (state.offsetOrNull ?: Float.NaN) > expandedOffset,
                "down swipe should have collapsed the sheet, was " +
                    "${state.currentValue} offset=${state.offsetOrNull}"
            )
        }

    @Test
    fun emptyAnchorsLeaveTheOffsetUnset() =
        runDesktopComposeUiTest(width = 400, height = 800) {
            // A zero peek over an empty sheet yields no anchors at all:
            // the layout must skip the anchor update instead of
            // throwing, park the sheet at the bottom edge, and keep
            // offsetOrNull null.
            val state = EditorSheetState(
                SheetValue.PartiallyExpanded,
                setOf(SheetValue.PartiallyExpanded, SheetValue.Expanded)
            )
            setContent {
                MaterialTheme {
                    EditorSheetLayout(
                        state = state,
                        peekHeight = 0.dp,
                        swipeEnabled = true,
                        sheet = {},
                        modifier = Modifier.fillMaxSize()
                    ) {
                        Box(Modifier.fillMaxSize())
                    }
                }
            }
            waitForIdle()
            assertNull(state.offsetOrNull)
        }
}
