/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.core

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import androidx.test.ext.junit.runners.AndroidJUnit4

@RunWith(AndroidJUnit4::class)
class KeepAtTopOnPrependTest {

    @get:Rule
    val rule = createComposeRule()

    @Composable
    private fun TestGrid(
        keys: SnapshotStateList<String>,
        columns: Int,
        state: LazyGridState,
    ) {
        KeepAtTopOnPrepend(state, keys.firstOrNull())
        LazyVerticalGrid(
            state = state,
            modifier = Modifier.height(600.dp),
            columns = GridCells.Fixed(columns),
        ) {
            items(keys, key = { it }) { key ->
                Box(Modifier.fillMaxWidth().height(100.dp).testTag(key))
            }
        }
    }

    private fun keys() = mutableStateListOf(*('b'..'z').map { it.toString() }.toTypedArray())

    @Test
    fun prependAtTheVeryTopKeepsTheGridAtTheTop() {
        val keys = keys()
        lateinit var gridState: LazyGridState
        rule.setContent {
            gridState = rememberLazyGridState()
            TestGrid(keys, columns = 3, state = gridState)
        }
        rule.waitForIdle()

        rule.runOnIdle { keys.add(0, "a") }
        rule.waitForIdle()

        assertEquals(0, gridState.firstVisibleItemIndex)
        assertEquals(0, gridState.firstVisibleItemScrollOffset)
        rule.onNodeWithTag("a").assertIsDisplayed()
    }

    @Test
    fun prependWhileScrolledKeepsThePreviouslyFirstItemFirst() {
        val keys = keys()
        lateinit var gridState: LazyGridState
        lateinit var scope: CoroutineScope
        rule.setContent {
            scope = rememberCoroutineScope()
            gridState = rememberLazyGridState()
            TestGrid(keys, columns = 1, state = gridState)
        }
        rule.waitForIdle()
        rule.runOnUiThread { scope.launch { gridState.scrollToItem(6) } }
        rule.waitUntil(timeoutMillis = 5_000) { gridState.firstVisibleItemIndex == 6 }
        assertEquals(6, gridState.firstVisibleItemIndex)

        rule.runOnIdle { keys.add(0, "a") }
        rule.waitForIdle()

        assertEquals(7, gridState.firstVisibleItemIndex)
        rule.onNodeWithTag("a").assertDoesNotExist()
    }
}
