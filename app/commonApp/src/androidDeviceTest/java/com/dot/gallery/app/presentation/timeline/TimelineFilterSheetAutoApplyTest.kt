/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.app.presentation.timeline

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.MediumTest
import com.dot.gallery.core.ui.util.AppBottomSheetState
import com.dot.gallery.core.ui.util.rememberAppBottomSheetState
import com.dot.gallery.domain.model.LastSort
import com.dot.gallery.domain.model.MediaTypeFilter
import com.dot.gallery.domain.model.TimelineFilter
import com.dot.gallery.domain.util.OrderType
import com.dot.gallery.feature.timeline.DefaultTimelineSort
import com.dot.gallery.feature.timeline.TimelineFilterSheet
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** The timeline controls apply filters as they are tapped and save the order once, on close. */
@MediumTest
@RunWith(AndroidJUnit4::class)
class TimelineFilterSheetAutoApplyTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun filterAppliesOnTapAndSortIsSavedWhenTheSheetCloses() {
        var filter by mutableStateOf(TimelineFilter())
        val commits = mutableListOf<LastSort>()
        lateinit var sheetState: AppBottomSheetState
        lateinit var scope: CoroutineScope
        composeRule.setContent {
            MaterialTheme {
                sheetState = rememberAppBottomSheetState()
                scope = rememberCoroutineScope()
                LaunchedEffect(Unit) { sheetState.show() }
                TimelineFilterSheet(
                    sheetState = sheetState,
                    filter = filter,
                    sort = DefaultTimelineSort,
                    availableYears = listOf(2025),
                    availableAlbums = emptyList(),
                    indexProgress = null,
                    onFilterChange = { filter = it },
                    onSortCommit = { commits += it },
                )
            }
        }

        composeRule.onNodeWithText("Videos").performClick()
        composeRule.runOnIdle { assertEquals(MediaTypeFilter.VIDEOS, filter.mediaType) }
        composeRule.onAllNodesWithText("Apply").assertCountEquals(0)

        composeRule.onNodeWithText("Oldest first").performClick()
        composeRule.runOnIdle { assertTrue(commits.isEmpty()) }

        composeRule.runOnIdle { scope.launch { sheetState.hide() } }
        composeRule.waitUntil(timeoutMillis = 5_000) { commits.isNotEmpty() }
        assertEquals(listOf(DefaultTimelineSort.copy(orderType = OrderType.Ascending)), commits)
    }
}
