/*
 * SPDX-FileCopyrightText: 2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.feature.help

import com.dot.gallery.feature.help.domain.HelpRoute
import com.dot.gallery.feature.help.domain.HelpSearchIndex
import com.dot.gallery.feature.help.domain.HelpSearchItem
import com.dot.gallery.feature.help.domain.HelpSearchKind
import com.dot.gallery.feature.help.domain.HelpTipMatch
import com.dot.gallery.feature.help.search.HelpFuzzyMatcher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Photo search showed help tips for almost any query because the fuzzy matcher accepts loose
 * hits ("dog" against "debug"). Tips now need a strong match there, and the Settings search
 * offers settings and tips only.
 */
class SettingsSearchAndTipMatchTest {

    private fun tip(id: String, title: String, keywords: String = "") = HelpSearchItem(
        kind = HelpSearchKind.TIP,
        id = id,
        title = title,
        subtitle = "",
        keywords = keywords,
        icon = null,
        route = HelpRoute.Tip(id),
    )

    private fun item(kind: HelpSearchKind, id: String) = HelpSearchItem(
        kind = kind, id = id, title = id, subtitle = "", keywords = "", icon = null, route = null,
    )

    private val tips = listOf(
        tip("vault", "Hide photos in the vault", "private secure lock"),
        tip("debug", "Developer debug logs", "bug report"),
        tip("trash", "Restore from trash", "deleted recover bin"),
    )

    @Test
    fun everyQueryWordMustStartAWordOfTheTip() {
        assertTrue(HelpTipMatch.isStrongMatch(tips[0], "vault"))
        assertTrue(HelpTipMatch.isStrongMatch(tips[0], "hide vau"))
        assertTrue(HelpTipMatch.isStrongMatch(tips[2], "recover"))
        assertFalse(HelpTipMatch.isStrongMatch(tips[0], "vault beach"))
        // Inside a word is not a match: "ault" is not the start of "vault".
        assertFalse(HelpTipMatch.isStrongMatch(tips[0], "ault"))
    }

    @Test
    fun shortQueriesNeverMatch() {
        assertFalse(HelpTipMatch.isStrongMatch(tips[0], "hi"))
        assertEquals(emptyList<HelpSearchItem>(), HelpTipMatch.strongMatches(tips, "  "))
    }

    @Test
    fun looseFuzzyHitsAreDroppedFromPhotoSearch() {
        // A typical photo query: the fuzzy matcher still finds something, the strong rule does not.
        for (query in listOf("dog", "sunset at the beach", "debu g")) {
            val fuzzy = HelpFuzzyMatcher.search(tips, query, limit = 50)
            assertEquals("query '$query'", emptyList<String>(), HelpTipMatch.strongMatches(fuzzy, query).map { it.id })
        }
        val vault = HelpTipMatch.strongMatches(HelpFuzzyMatcher.search(tips, "vault", limit = 50), "vault")
        assertEquals(listOf("vault"), vault.map { it.id })
    }

    @Test
    fun settingsSearchKeepsSettingsAndTipsOnly() {
        val index = listOf(
            item(HelpSearchKind.SETTING, HelpSearchIndex.SETTINGS_ROOT_ID),
            item(HelpSearchKind.SETTING, "toggle_amoled"),
            item(HelpSearchKind.TIP, "vault"),
            item(HelpSearchKind.QUICK_ACTION, "vault#open"),
            item(HelpSearchKind.CHANGELOG, "release_600"),
        )
        assertEquals(
            listOf("toggle_amoled", "vault"),
            HelpSearchIndex.forSettingsSearch(index).map { it.id },
        )
    }
}
