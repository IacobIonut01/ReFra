/*
 * SPDX-FileCopyrightText: 2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.app.presentation.albums

import androidx.compose.ui.unit.dp
import com.dot.gallery.feature.albums.ui.components.albumCoverLabelMetrics
import com.dot.gallery.feature.albums.ui.util.formatSizeShort
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Issue 1448: on-cover album labels scale with the cell and round sizes to one decimal. */
class AlbumLabelMetricsTest {

    @Test
    fun sizesUseOneDecimal() {
        assertEquals("1.2 GB", formatSizeShort(1_288_490_189))
        assertEquals("4.7 GB", formatSizeShort(5_081_234_567))
        assertEquals("17.6 MB", formatSizeShort(18_402_918))
        assertEquals("1.0 KB", formatSizeShort(1_024))
        assertEquals("512 B", formatSizeShort(512))
        assertEquals("0 B", formatSizeShort(0))
    }

    @Test
    fun roundingCarriesIntoTheWholePart() {
        // 1.96 MB must read 2.0 MB, not 1.10 MB.
        assertEquals("2.0 MB", formatSizeShort(2_055_209))
    }

    @Test
    fun textScalesWithTheCoverWithinLimits() {
        assertEquals(10f, albumCoverLabelMetrics(40.dp).nameSize.value, 0.001f)
        assertEquals(11.6f, albumCoverLabelMetrics(116.dp).nameSize.value, 0.001f)
        assertEquals(16f, albumCoverLabelMetrics(380.dp).nameSize.value, 0.001f)
    }

    @Test
    fun sizeLineDropsOnlyOnSmallCovers() {
        assertFalse(albumCoverLabelMetrics(66.dp).showSize)
        assertFalse(albumCoverLabelMetrics(79.dp).showSize)
        assertTrue(albumCoverLabelMetrics(80.dp).showSize)
        assertTrue(albumCoverLabelMetrics(182.dp).showSize)
    }
}
