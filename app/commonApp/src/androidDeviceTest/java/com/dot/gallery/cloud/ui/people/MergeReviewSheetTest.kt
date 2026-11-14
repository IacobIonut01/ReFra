/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.cloud.ui.people

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.MediumTest
import com.dot.gallery.cloud.core.PersonInfo
import com.dot.gallery.cloud.core.ProviderType
import com.dot.gallery.cloud.local.PersonMergeSuggestion
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@MediumTest
@RunWith(AndroidJUnit4::class)
class MergeReviewSheetTest {

    @get:Rule
    val rule = createComposeRule()

    private fun person(id: String, name: String) =
        PersonInfo(id = id, name = name, providerType = ProviderType.LOCAL_PEOPLE, serverConfigId = 0L)

    private val first = PersonMergeSuggestion(person("a", "Ana"), person("b", "Ana B"), 0.9f)
    private val second = PersonMergeSuggestion(person("c", "Dan"), person("d", "Dan C"), 0.8f)

    /**
     * Answering the last pair used to leave the sheet open over an empty list. It must
     * stay open while pairs remain, then hide and dismiss once the list empties.
     */
    @Test
    fun sheetDismissesOnlyAfterLastSuggestionIsAnswered() {
        var suggestions by mutableStateOf(listOf(first, second))
        var dismissCount by mutableIntStateOf(0)
        var showSheet by mutableStateOf(true)

        rule.setContent {
            if (showSheet) {
                MergeReviewSheet(
                    suggestions = suggestions,
                    onDismiss = { dismissCount++; showSheet = false },
                    onSamePerson = { answered -> suggestions = suggestions - answered },
                    onDifferent = { answered -> suggestions = suggestions - answered }
                )
            }
        }

        rule.onNodeWithText("Ana").assertExists()
        rule.onAllNodesWithText("Different")[0].performClick()
        rule.waitForIdle()
        assertEquals("sheet must stay open while pairs remain", 0, dismissCount)
        rule.onNodeWithText("Dan").assertExists()

        rule.onNodeWithText("Same person").performClick()
        rule.waitUntil("sheet dismissed after last answer", 5_000) { dismissCount == 1 }
        rule.waitForIdle()
        assertEquals(1, dismissCount)
        rule.onNodeWithText("Dan").assertDoesNotExist()
    }
}
