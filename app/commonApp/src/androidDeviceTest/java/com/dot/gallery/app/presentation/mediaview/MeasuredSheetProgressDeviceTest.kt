/*
 * SPDX-FileCopyrightText: 2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.app.presentation.mediaview

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.MediumTest
import com.composables.core.BottomSheet
import com.composables.core.BottomSheetState
import com.composables.core.SheetDetent
import com.composables.core.rememberBottomSheetState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@MediumTest
@RunWith(AndroidJUnit4::class)
class MeasuredSheetProgressDeviceTest {

    @get:Rule
    val rule = createComposeRule()

    @Composable
    private fun rememberTestSheetState(): Pair<BottomSheetState, SheetDetent> {
        val imageOnly = remember { SheetDetent("imageOnly") { _, _ -> 80.dp } }
        val state = rememberBottomSheetState(
            initialDetent = imageOnly,
            detents = listOf(imageOnly, SheetDetent.FullyExpanded),
        )
        return state to imageOnly
    }

    /**
     * Documents the library contract the viewer fix relies on: a sheet state whose anchors
     * were never measured reports a raw progress of 1 (the NaN fraction fallback), while
     * [measuredProgress] reads 0 so chrome does not see a phantom "fully opened" frame.
     */
    @Test
    fun unmeasuredSheetStateReportsRawProgressOne() {
        var sheetState: BottomSheetState? = null
        var imageOnly: SheetDetent? = null
        rule.setContent {
            val (state, detent) = rememberTestSheetState()
            sheetState = state
            imageOnly = detent
        }
        rule.waitForIdle()
        val state = requireNotNull(sheetState)
        val detent = requireNotNull(imageOnly)
        assertEquals(1f, state.progress(detent, SheetDetent.FullyExpanded), 0f)
        assertEquals(0f, state.measuredProgress(detent, SheetDetent.FullyExpanded), 0f)
    }

    @Test
    fun measuredSheetStateReportsRealProgress() {
        var sheetState: BottomSheetState? = null
        var imageOnly: SheetDetent? = null
        rule.setContent {
            Box(Modifier.fillMaxSize()) {
                val (state, detent) = rememberTestSheetState()
                sheetState = state
                imageOnly = detent
                BottomSheet(state = state) {
                    // Taller than the imageOnly detent: FullyExpanded resolves to the
                    // content height, so an 80.dp box would give both detents the same
                    // anchor and progress() would read 1 even though the sheet is measured.
                    Box(Modifier.fillMaxWidth().height(400.dp))
                }
            }
        }
        rule.waitForIdle()
        val state = requireNotNull(sheetState)
        val detent = requireNotNull(imageOnly)
        assertTrue("sheet offset should be measured", state.offset > 0f)
        assertEquals(0f, state.measuredProgress(detent, SheetDetent.FullyExpanded), 0f)
    }
}
