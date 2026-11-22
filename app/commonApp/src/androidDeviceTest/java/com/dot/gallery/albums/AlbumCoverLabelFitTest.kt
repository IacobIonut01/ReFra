/*
 * SPDX-FileCopyrightText: 2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.albums

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.MediumTest
import com.dot.gallery.app.presentation.util.PreviewHost
import com.dot.gallery.feature.albums.ui.components.AlbumCoverLabel
import com.dot.gallery.feature.albums.ui.components.AlbumCoverLabelTag
import com.dot.gallery.feature.albums.ui.components.AlbumCoverSizeLineMinWidth
import com.dot.gallery.feature.albums.ui.components.AlbumLabelCountTag
import com.dot.gallery.feature.albums.ui.components.AlbumLabelSizeTag
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Issue 1448: the on-cover label must fit inside the cover at every album grid size (1 to 7
 * columns) on phone and tablet widths, at the default and a large font size. The count must stay
 * fully visible beside an ellipsized name, and the size line shows only on wide enough covers.
 */
@MediumTest
@RunWith(AndroidJUnit4::class)
class AlbumCoverLabelFitTest {

    @get:Rule
    val rule = createComposeRule()

    private var cover by mutableStateOf(100.dp)
    private var fontScale by mutableStateOf(1f)

    private fun show() {
        rule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
                PreviewHost {
                    Box(Modifier.size(cover).testTag(COVER)) {
                        AlbumCoverLabel(
                            name = "WhatsApp Images from the summer trip",
                            count = 2045,
                            sizeBytes = 1_932_735_283,
                            coverWidth = cover,
                            modifier = Modifier.align(Alignment.BottomStart),
                        )
                    }
                }
            }
        }
    }

    /** The cover width the album grid gives each column on a window [windowWidth] wide. */
    private fun coverWidth(windowWidth: Dp, columns: Int): Dp =
        (windowWidth - GridPadding * 2) / columns - CellPadding * 2

    @Test
    fun labelFitsAtEveryGridSize() {
        show()
        for (window in listOf(360.dp, 412.dp, 840.dp)) {
            for (columns in 1..7) {
                for (scale in listOf(1f, 1.3f)) {
                    cover = coverWidth(window, columns)
                    fontScale = scale
                    rule.waitForIdle()
                    val where = "window $window, $columns columns, cover $cover, font x$scale"

                    val box = rule.onNodeWithTag(COVER).getUnclippedBoundsInRoot()
                    val label = rule.onNodeWithTag(AlbumCoverLabelTag, useUnmergedTree = true)
                        .getUnclippedBoundsInRoot()
                    assertTrue("label taller than the cover: $where", label.top >= box.top)
                    assertTrue("label leaves the cover: $where", label.bottom <= box.bottom + Slack)
                    assertTrue("label wider than the cover: $where", label.right <= box.right + Slack)
                    assertTrue(
                        "label covers more than 60% of the photo: $where",
                        label.bottom - label.top <= cover * 0.6f,
                    )

                    val count = rule.onNodeWithTag(AlbumLabelCountTag, useUnmergedTree = true)
                        .getUnclippedBoundsInRoot()
                    assertTrue("count cut off: $where", count.right <= label.right + Slack)

                    val sizeLines = rule.onAllNodesWithTag(AlbumLabelSizeTag, useUnmergedTree = true)
                        .fetchSemanticsNodes().size
                    assertEquals(
                        "size line at $where",
                        if (cover >= AlbumCoverSizeLineMinWidth) 1 else 0,
                        sizeLines,
                    )
                }
            }
        }
    }

    private companion object {
        const val COVER = "AlbumCoverLabelFitTest.Cover"
        val GridPadding = 8.dp
        val CellPadding = 8.dp
        val Slack = 0.5.dp
    }
}
