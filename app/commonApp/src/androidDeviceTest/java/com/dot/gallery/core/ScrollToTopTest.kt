/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.core

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import androidx.test.ext.junit.runners.AndroidJUnit4

@RunWith(AndroidJUnit4::class)
class ScrollToTopTest {

    @get:Rule
    val rule = createComposeRule()

    private fun absolutePosition(
        state: LazyGridState,
        headerPx: Float,
        itemPx: Float,
        columns: Int,
    ): Float {
        val index = state.firstVisibleItemIndex
        val offset = state.firstVisibleItemScrollOffset.toFloat()
        return if (index == 0) offset
        else headerPx + ((index - 1) / columns) * itemPx + offset
    }

    private fun assertUnimodalDeltas(positions: List<Float>) {
        val deltas = positions.zipWithNext { prev, cur -> prev - cur }
        val peak = deltas.indices.maxByOrNull { deltas[it] } ?: return
        for (i in (peak + 1) until deltas.size) {
            assertTrue(
                "upward delta grew after the peak at frame $i: ${deltas[i - 1]} -> ${deltas[i]}",
                deltas[i] <= deltas[i - 1] + 2f
            )
        }
    }

    private fun driveAnimation(job: Job, sample: () -> Float): List<Float> {
        val positions = mutableListOf<Float>()
        var frames = 0
        while (!job.isCompleted && frames < 400) {
            rule.mainClock.advanceTimeByFrame()
            frames++
            positions += sample()
        }
        return positions
    }

    @Test
    fun gridScrollToTopAnimatesOverTheMeasuredDistance() {
        lateinit var gridState: LazyGridState
        lateinit var scope: CoroutineScope
        rule.setContent {
            scope = rememberCoroutineScope()
            gridState = rememberLazyGridState()
            LazyVerticalGrid(
                state = gridState,
                modifier = Modifier.height(800.dp),
                columns = GridCells.Fixed(2),
            ) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Box(Modifier.fillMaxWidth().height(1500.dp))
                }
                items(40) {
                    Box(Modifier.fillMaxWidth().height(120.dp))
                }
            }
        }
        rule.waitForIdle()
        rule.runOnUiThread { scope.launch { gridState.scrollToItem(13) } }
        rule.waitUntil(timeoutMillis = 5_000) { gridState.firstVisibleItemIndex == 13 }

        val headerPx = with(rule.density) { 1500.dp.toPx() }
        val itemPx = with(rule.density) { 120.dp.toPx() }
        rule.mainClock.autoAdvance = false
        lateinit var job: Job
        rule.runOnUiThread { job = scope.launch { gridState.animateOrJumpToTop() } }
        try {
            val positions = driveAnimation(job) {
                absolutePosition(gridState, headerPx, itemPx, columns = 2)
            }

            assertTrue(
                "animateOrJumpToTop did not finish within 400 frames " +
                    "(positions=${positions.size}, first=${positions.firstOrNull()}, " +
                    "last=${positions.lastOrNull()}, index=${gridState.firstVisibleItemIndex})",
                job.isCompleted
            )
            assertEquals(0, gridState.firstVisibleItemIndex)
            assertEquals(0, gridState.firstVisibleItemScrollOffset)
            assertUnimodalDeltas(positions)
        } finally {
            job.cancel()
            rule.mainClock.autoAdvance = true
        }
    }

    @Test
    fun listScrollToTopAnimatesOverTheMeasuredDistance() {
        lateinit var listState: LazyListState
        lateinit var scope: CoroutineScope
        rule.setContent {
            scope = rememberCoroutineScope()
            listState = rememberLazyListState()
            LazyColumn(state = listState, modifier = Modifier.height(800.dp)) {
                item { Box(Modifier.fillMaxWidth().height(1500.dp)) }
                items(40) { Box(Modifier.fillMaxWidth().height(120.dp)) }
            }
        }
        rule.waitForIdle()
        rule.runOnUiThread { scope.launch { listState.scrollToItem(13) } }
        rule.waitUntil(timeoutMillis = 5_000) { listState.firstVisibleItemIndex == 13 }

        val headerPx = with(rule.density) { 1500.dp.toPx() }
        val itemPx = with(rule.density) { 120.dp.toPx() }
        rule.mainClock.autoAdvance = false
        lateinit var job: Job
        rule.runOnUiThread { job = scope.launch { listState.animateOrJumpToTop() } }
        try {
            val positions = mutableListOf<Float>()
            var frames = 0
            while (!job.isCompleted && frames < 400) {
                rule.mainClock.advanceTimeByFrame()
                frames++
                val index = listState.firstVisibleItemIndex
                val offset = listState.firstVisibleItemScrollOffset.toFloat()
                positions += if (index == 0) offset else headerPx + (index - 1) * itemPx + offset
            }

            assertTrue(
                "animateOrJumpToTop did not finish within 400 frames " +
                    "(positions=${positions.size}, first=${positions.firstOrNull()}, " +
                    "last=${positions.lastOrNull()}, index=${listState.firstVisibleItemIndex})",
                job.isCompleted
            )
            assertEquals(0, listState.firstVisibleItemIndex)
            assertEquals(0, listState.firstVisibleItemScrollOffset)
            assertUnimodalDeltas(positions)
        } finally {
            job.cancel()
            rule.mainClock.autoAdvance = true
        }
    }
}
