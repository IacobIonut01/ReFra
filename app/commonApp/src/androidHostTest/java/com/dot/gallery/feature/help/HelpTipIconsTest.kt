/*
 * SPDX-FileCopyrightText: 2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.feature.help

import com.dot.gallery.feature.help.domain.HelpCategory
import com.dot.gallery.feature.help.domain.HelpRepository
import org.junit.Assert.assertTrue
import org.junit.Test

/** The tips list of a help category shows each tip's icon, so no two tips may share one. */
class HelpTipIconsTest {

    @Test
    fun tipsInACategoryHaveDistinctIcons() {
        val clashes = HelpCategory.entries.mapNotNull { category ->
            val tips = HelpRepository.getTipsByCategory(category)
            val duplicated = tips
                .groupBy { it.icon.vector?.name ?: it.icon.toString() }
                .filterValues { it.size > 1 }
            if (duplicated.isEmpty()) null
            else "$category: " + duplicated.entries.joinToString { (icon, shared) ->
                "$icon -> ${shared.joinToString { it.id }}"
            }
        }
        assertTrue(clashes.joinToString("\n"), clashes.isEmpty())
    }

    @Test
    fun everyTipHasAVectorIcon() {
        val missing = HelpRepository.getAllTips().filter { it.icon.vector == null }.map { it.id }
        assertTrue("Tips without an icon: $missing", missing.isEmpty())
    }
}
