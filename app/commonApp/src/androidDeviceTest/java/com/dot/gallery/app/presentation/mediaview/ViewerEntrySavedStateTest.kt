/*
 * SPDX-FileCopyrightText: 2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.app.presentation.mediaview

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.MutableIntState
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.MediumTest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The viewer's saveable state belongs to one viewer entry. When the activity is recreated the
 * overlay viewer is gone but its saved values remain at the same composition position; a new
 * entry opened there must not pick them up.
 */
@MediumTest
@RunWith(AndroidJUnit4::class)
class ViewerEntrySavedStateTest {

    @get:Rule
    val rule = createComposeRule()

    @Test
    fun rememberSaveableValueIsDroppedForADifferentEntryKey() {
        var entryKey = "entry-a"
        var intState: MutableIntState? = null
        val restorationTester = StateRestorationTester(rule)
        restorationTester.setContent {
            ViewerEntrySavedStateScope(entryKey) {
                intState = rememberSaveable { mutableIntStateOf(0) }
            }
        }
        rule.waitForIdle()
        intState!!.intValue = 3

        entryKey = "entry-b"
        restorationTester.emulateSavedInstanceStateRestore()
        rule.waitForIdle()

        assertEquals(0, intState!!.intValue)
    }

    @Test
    fun rememberSaveableValueIsRestoredForTheSameEntryKey() {
        val entryKey = "entry-a"
        var intState: MutableIntState? = null
        val restorationTester = StateRestorationTester(rule)
        restorationTester.setContent {
            ViewerEntrySavedStateScope(entryKey) {
                intState = rememberSaveable { mutableIntStateOf(0) }
            }
        }
        rule.waitForIdle()
        intState!!.intValue = 3

        restorationTester.emulateSavedInstanceStateRestore()
        rule.waitForIdle()

        assertEquals(3, intState!!.intValue)
    }

    @Test
    fun pagerStateIsDroppedForADifferentEntryKey() {
        var entryKey = "entry-a"
        var pagerState: PagerState? = null
        var scope: CoroutineScope? = null
        val restorationTester = StateRestorationTester(rule)
        restorationTester.setContent {
            ViewerEntrySavedStateScope(entryKey) {
                scope = rememberCoroutineScope()
                val state = rememberPagerState(initialPage = 0) { 8 }
                pagerState = state
                HorizontalPager(state = state, modifier = Modifier.fillMaxSize()) {}
            }
        }
        rule.waitForIdle()
        scope!!.launch { pagerState!!.scrollToPage(3) }
        rule.waitUntil(timeoutMillis = 5_000) { pagerState!!.currentPage == 3 }

        entryKey = "entry-b"
        restorationTester.emulateSavedInstanceStateRestore()
        rule.waitForIdle()

        assertEquals(0, pagerState!!.currentPage)
    }

    @Test
    fun pagerStateIsRestoredForTheSameEntryKey() {
        val entryKey = "entry-a"
        var pagerState: PagerState? = null
        var scope: CoroutineScope? = null
        val restorationTester = StateRestorationTester(rule)
        restorationTester.setContent {
            ViewerEntrySavedStateScope(entryKey) {
                scope = rememberCoroutineScope()
                val state = rememberPagerState(initialPage = 0) { 8 }
                pagerState = state
                HorizontalPager(state = state, modifier = Modifier.fillMaxSize()) {}
            }
        }
        rule.waitForIdle()
        scope!!.launch { pagerState!!.scrollToPage(3) }
        rule.waitUntil(timeoutMillis = 5_000) { pagerState!!.currentPage == 3 }

        restorationTester.emulateSavedInstanceStateRestore()
        rule.waitForIdle()

        assertEquals(3, pagerState!!.currentPage)
    }
}
