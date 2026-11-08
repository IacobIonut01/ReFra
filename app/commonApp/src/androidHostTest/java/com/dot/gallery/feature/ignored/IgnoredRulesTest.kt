/*
 * SPDX-FileCopyrightText: 2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.feature.ignored

import com.dot.gallery.domain.model.IgnoredAlbum
import com.dot.gallery.feature.ignored.ui.IgnoredRuleKind
import com.dot.gallery.feature.ignored.ui.customLabel
import com.dot.gallery.feature.ignored.ui.displayTitle
import com.dot.gallery.feature.ignored.ui.kind
import com.dot.gallery.feature.ignored.ui.toRuleWrite
import com.dot.gallery.feature.ignored.ui.setup.IgnoreMethod
import com.dot.gallery.feature.ignored.ui.setup.IgnoredSetupUiState
import com.dot.gallery.feature.ignored.ui.setup.PatternError
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure-JVM tests for the rules the ignore sheet writes and how the list labels them. `Album`
 * holds an `android.net.Uri` that can't be built on the host JVM, so these work on album ids.
 */
class IgnoredRulesTest {

    @Test
    fun `picked albums are stored as an album list with a fresh id`() {
        val write = IgnoredSetupUiState(selectedAlbumIds = listOf(2)).toRuleWrite { 99L }

        assertNull(write.remove)
        assertEquals(99L, write.save.id)
        assertEquals(listOf(2L), write.save.albumIds)
        assertNull(write.save.wildcard)
        assertNull(write.save.label)
        assertEquals(IgnoredAlbum.ALBUMS_AND_TIMELINE, write.save.location)
    }

    @Test
    fun `a pattern rule stores only the wildcard and a trimmed name`() {
        val state = IgnoredSetupUiState(
            method = IgnoreMethod.PATTERN,
            regex = ".*/Telegram/.*",
            selectedAlbumIds = listOf(1),
            name = "  Chats  ",
        )

        val rule = state.toRuleWrite { 7L }.save

        assertEquals(".*/Telegram/.*", rule.wildcard)
        assertTrue(rule.albumIds.isEmpty())
        assertEquals("Chats", rule.label)
    }

    @Test
    fun `editing a legacy single rule on the same album keeps its row`() {
        val legacy = IgnoredAlbum(id = 2, label = "Single #1", location = IgnoredAlbum.ALBUMS_ONLY)
        val state = IgnoredSetupUiState(
            editing = legacy,
            selectedAlbumIds = listOf(2),
            location = IgnoredAlbum.TIMELINE_ONLY,
        )

        val write = state.toRuleWrite { error("no new id expected") }

        assertNull(write.remove)
        assertEquals(2L, write.save.id)
        assertTrue(write.save.albumIds.isEmpty())
        assertEquals(IgnoredAlbum.TIMELINE_ONLY, write.save.location)
    }

    @Test
    fun `changing the album of a legacy single rule replaces its row`() {
        val legacy = IgnoredAlbum(id = 2)
        val state = IgnoredSetupUiState(editing = legacy, selectedAlbumIds = listOf(2, 1))

        val write = state.toRuleWrite { 50L }

        assertEquals(legacy, write.remove)
        assertEquals(50L, write.save.id)
        assertEquals(listOf(2L, 1L), write.save.albumIds)
    }

    @Test
    fun `turning a legacy single rule into a pattern replaces its row`() {
        val legacy = IgnoredAlbum(id = 2)
        val state = IgnoredSetupUiState(editing = legacy, method = IgnoreMethod.PATTERN, regex = ".*")

        val write = state.toRuleWrite { 51L }

        assertEquals(legacy, write.remove)
        assertEquals(51L, write.save.id)
        assertEquals(".*", write.save.wildcard)
    }

    @Test
    fun `editing a multi album rule keeps its id`() {
        val rule = IgnoredAlbum(id = 1234, albumIds = listOf(1, 2))
        val state = IgnoredSetupUiState(editing = rule, selectedAlbumIds = listOf(1))

        val write = state.toRuleWrite { error("no new id expected") }

        assertNull(write.remove)
        assertEquals(1234L, write.save.id)
        assertEquals(listOf(1L), write.save.albumIds)
    }

    @Test
    fun `pattern errors cover invalid and reused patterns, but not the edited rule`() {
        val existing = IgnoredAlbum(id = 5, wildcard = ".*Screenshots.*")
        assertEquals(PatternError.INVALID, IgnoredSetupUiState(method = IgnoreMethod.PATTERN, regex = "(*").patternError)
        assertEquals(
            PatternError.IN_USE,
            IgnoredSetupUiState(method = IgnoreMethod.PATTERN, regex = ".*Screenshots.*", ignoredAlbums = listOf(existing)).patternError
        )
        assertNull(
            IgnoredSetupUiState(
                method = IgnoreMethod.PATTERN,
                regex = ".*Screenshots.*",
                ignoredAlbums = listOf(existing),
                editing = existing
            ).patternError
        )
    }

    @Test
    fun `albums hidden by another rule are disabled, the edited rule's own albums are not`() {
        val legacy = IgnoredAlbum(id = 2)
        val multi = IgnoredAlbum(id = 900, albumIds = listOf(1))
        val pattern = IgnoredAlbum(id = 901, wildcard = ".*Telegram.*")
        val state = IgnoredSetupUiState(ignoredAlbums = listOf(legacy, multi, pattern))

        assertTrue(state.isIgnoredElsewhere(2))
        assertTrue(state.isIgnoredElsewhere(1))
        assertFalse(state.isIgnoredElsewhere(3))
        assertFalse(state.isIgnoredElsewhere(901))
        assertFalse(state.copy(editing = multi).isIgnoredElsewhere(1))
    }

    @Test
    fun `continue needs a selection or a valid pattern`() {
        assertFalse(IgnoredSetupUiState().canContinue)
        assertTrue(IgnoredSetupUiState(selectedAlbumIds = listOf(1)).canContinue)
        assertFalse(IgnoredSetupUiState(method = IgnoreMethod.PATTERN, regex = "(").canContinue)
        assertTrue(IgnoredSetupUiState(method = IgnoreMethod.PATTERN, regex = ".*").canContinue)
    }

    @Test
    fun `generated labels of the old setup are not shown as names`() {
        assertNull(IgnoredAlbum(id = 1, label = "Single #3").customLabel)
        assertNull(IgnoredAlbum(id = 1, label = "Regex #12").customLabel)
        assertEquals("Trips", IgnoredAlbum(id = 1, label = "Trips").customLabel)
        assertEquals(
            "Camera, Screenshots",
            IgnoredAlbum(id = 1, label = "Multiple #1", albumIds = listOf(1, 2), matchedAlbums = listOf("Camera", "Screenshots"))
                .displayTitle("Unknown")
        )
        assertEquals(".*x.*", IgnoredAlbum(id = 1, label = "Regex #1", wildcard = ".*x.*").displayTitle("Unknown"))
        assertEquals("Unknown", IgnoredAlbum(id = 1).displayTitle("Unknown"))
    }

    @Test
    fun `rule kinds follow the stored shape`() {
        assertEquals(IgnoredRuleKind.SINGLE, IgnoredAlbum(id = 1).kind)
        assertEquals(IgnoredRuleKind.SINGLE, IgnoredAlbum(id = 9, albumIds = listOf(1)).kind)
        assertEquals(IgnoredRuleKind.SEVERAL, IgnoredAlbum(id = 9, albumIds = listOf(1, 2)).kind)
        assertEquals(IgnoredRuleKind.PATTERN, IgnoredAlbum(id = 9, wildcard = ".*").kind)
    }
}
