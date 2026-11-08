/*
 * SPDX-FileCopyrightText: 2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.core

import androidx.compose.ui.unit.dp
import com.dot.gallery.core.presentation.components.galleryDetailPaneFits
import com.dot.gallery.core.presentation.components.galleryDetailPaneActive
import com.dot.gallery.core.presentation.components.galleryMasterPaneWidth
import com.dot.gallery.core.presentation.components.galleryPaneFractionFor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GalleryPaneLayoutPolicyTest {
    @Test
    fun retainedMediaDoesNotLeaveABlankDetailPaneAfterDismissal() {
        assertFalse(galleryDetailPaneActive(true, false, false))
    }

    @Test
    fun detailPaneRemainsDuringTheViewerExitTransition() {
        assertTrue(galleryDetailPaneActive(true, false, true))
    }

    @Test
    fun storyOverlayDoesNotUseTheGalleryDetailPane() {
        assertFalse(galleryDetailPaneActive(false, true, true))
    }

    @Test
    fun compactWindowKeepsTheFullscreenViewer() {
        assertFalse(galleryDetailPaneFits(839.dp, 1_200.dp))
    }

    @Test
    fun shortLandscapeWindowKeepsTheFullscreenViewer() {
        assertFalse(galleryDetailPaneFits(1_200.dp, 479.dp))
    }

    @Test
    fun foldableBoundarySupportsBothPanes() {
        assertTrue(galleryDetailPaneFits(840.dp, 480.dp))
    }

    @Test
    fun referenceFoldableUsesFortyFivePercentForTheGallery() {
        assertEquals(937.dp * 0.45f, galleryMasterPaneWidth(937.dp))
    }

    @Test
    fun veryWideWindowCapsTheGalleryToKeepThePhotoProminent() {
        assertEquals(480.dp, galleryMasterPaneWidth(1_600.dp))
    }

    @Test
    fun galleryPaneNeverShrinksBelowItsMinimumWidth() {
        assertEquals(360.dp, galleryMasterPaneWidth(700.dp))
    }

    @Test
    fun defaultFractionKeepsTheReferenceGeometry() {
        assertEquals(galleryMasterPaneWidth(937.dp), galleryMasterPaneWidth(937.dp, 0.45f))
    }

    @Test
    fun draggedFractionMayExceedTheDefaultCapButLeavesTheViewerUsable() {
        // 1280 - 12 gutter - 360 minimum viewer.
        assertEquals(908.dp, galleryMasterPaneWidth(1_280.dp, 0.95f))
        assertEquals(640.dp, galleryMasterPaneWidth(1_280.dp, 0.5f))
    }

    @Test
    fun draggedFractionNeverShrinksTheGalleryBelowItsMinimum() {
        assertEquals(320.dp, galleryMasterPaneWidth(1_280.dp, 0.05f))
    }

    @Test
    fun fractionSavedOnAWiderWindowIsClampedOnANarrowerOne() {
        val fraction = galleryPaneFractionFor(1_280.dp, 900.dp)
        assertEquals(468.dp, galleryMasterPaneWidth(840.dp, fraction))
    }
}
