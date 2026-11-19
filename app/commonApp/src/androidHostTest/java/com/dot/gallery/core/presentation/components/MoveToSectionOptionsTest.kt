/*
 * SPDX-FileCopyrightText: 2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.core.presentation.components

import com.dot.gallery.domain.model.AlbumSection
import com.dot.gallery.domain.model.AlbumSectionType
import org.junit.Assert.assertEquals
import org.junit.Test

class MoveToSectionOptionsTest {

    private fun section(
        id: Long,
        label: String,
        type: AlbumSectionType,
        order: Int,
        isVisible: Boolean = true,
    ) = AlbumSection(
        id = id,
        label = label,
        type = type.value,
        displayOrder = order,
        isVisible = isVisible,
    )

    private val sections = listOf(
        section(1, "Common", AlbumSectionType.COMMON, 0),
        section(2, "Apps", AlbumSectionType.APPS, 1),
        section(3, "Other", AlbumSectionType.UNCATEGORIZED, 2),
        section(4, "Test", AlbumSectionType.CUSTOM, 3),
    )

    private val counts = mapOf(1L to 4, 2L to 14, 3L to 5)

    @Test
    fun newlyCreatedEmptySectionIsOfferedAsDestination() {
        assertEquals(
            listOf(
                MoveToSectionOption(1, "Common", 4),
                MoveToSectionOption(2, "Apps", 14),
                MoveToSectionOption(3, "Other", 5),
                MoveToSectionOption(4, "Test", 0),
            ),
            moveToSectionOptions(sections, counts)
        )
    }

    @Test
    fun disabledSectionsAreLeftOut() {
        val hidden = sections.map { it.copy(isVisible = it.id != 2L && it.id != 4L) }

        assertEquals(
            listOf(
                MoveToSectionOption(1, "Common", 4),
                MoveToSectionOption(3, "Other", 5),
            ),
            moveToSectionOptions(hidden, counts)
        )
    }
}
