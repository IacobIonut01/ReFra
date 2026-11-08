/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.core.mediaview

import com.dot.gallery.core.ui.mediaview.ViewerActionBarConfig
import com.dot.gallery.core.ui.mediaview.ViewerQuickAction
import org.junit.Assert.assertEquals
import org.junit.Test

class ViewerActionBarConfigTest {

    @Test
    fun defaultOrderMatchesTheShippedViewerBar() {
        assertEquals(
            listOf(
                ViewerQuickAction.SHARE,
                ViewerQuickAction.VISUAL_SEARCH,
                ViewerQuickAction.COPY_TO_CLIPBOARD,
                ViewerQuickAction.FAVORITE,
                ViewerQuickAction.OPEN_AS,
                ViewerQuickAction.RESTORE,
                ViewerQuickAction.DOWNLOAD,
                ViewerQuickAction.RENAME,
                ViewerQuickAction.EDIT,
                ViewerQuickAction.TRASH,
            ),
            ViewerActionBarConfig().actions
        )
    }

    @Test
    fun sanitizedPreservesOrderAndDropsDuplicates() {
        val config = ViewerActionBarConfig(
            actions = listOf(
                ViewerQuickAction.EDIT,
                ViewerQuickAction.SHARE,
                ViewerQuickAction.EDIT,
                ViewerQuickAction.TRASH,
            )
        )

        assertEquals(
            listOf(ViewerQuickAction.EDIT, ViewerQuickAction.SHARE, ViewerQuickAction.TRASH),
            config.sanitized().actions
        )
    }

    @Test
    fun emptyConfigurationFallsBackToDefaults() {
        assertEquals(
            ViewerActionBarConfig.DEFAULT_ACTIONS,
            ViewerActionBarConfig(actions = emptyList()).sanitized().actions
        )
    }

    @Test
    fun removingActionsKeepsTheRemainingOrder() {
        val config = ViewerActionBarConfig.DEFAULT_ACTIONS - ViewerQuickAction.VISUAL_SEARCH

        assertEquals(
            ViewerActionBarConfig.DEFAULT_ACTIONS.filterNot {
                it == ViewerQuickAction.VISUAL_SEARCH
            },
            ViewerActionBarConfig(actions = config).sanitized().actions
        )
    }
}
