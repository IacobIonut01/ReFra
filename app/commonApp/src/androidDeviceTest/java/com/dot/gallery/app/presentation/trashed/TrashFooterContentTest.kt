/*
 * SPDX-FileCopyrightText: 2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.app.presentation.trashed

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.MediumTest
import com.dot.gallery.app.presentation.trashed.components.TrashFooterContent
import com.dot.gallery.app.presentation.util.PreviewHost
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@MediumTest
@RunWith(AndroidJUnit4::class)
class TrashFooterContentTest {

    @get:Rule
    val rule = createComposeRule()

    /**
     * "Delete all permanently" wraps to two lines on narrow widths; the Row must still give
     * "Restore all" the same height instead of leaving it at the 48 dp minimum.
     */
    @Test
    fun footerButtonsShareTheSameHeight() {
        rule.setContent {
            PreviewHost {
                Box(modifier = androidx.compose.ui.Modifier.width(300.dp)) {
                    TrashFooterContent(hasMedia = true, onRestoreAll = {}, onDeleteAll = {})
                }
            }
        }

        val restore = rule.onNode(
            hasClickAction() and hasAnyDescendant(hasText("Restore all"))
        ).assertExists()
        val delete = rule.onNode(
            hasClickAction() and hasAnyDescendant(hasText("Delete all permanently"))
        ).assertExists()

        assertEquals(
            delete.fetchSemanticsNode().boundsInRoot.height.toDouble(),
            restore.fetchSemanticsNode().boundsInRoot.height.toDouble(),
            0.5,
        )
    }
}
