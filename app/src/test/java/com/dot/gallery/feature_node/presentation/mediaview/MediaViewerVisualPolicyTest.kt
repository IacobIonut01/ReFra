package com.dot.gallery.feature_node.presentation.mediaview

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaViewerVisualPolicyTest {

    @Test
    fun blurAlwaysUsesDarkBackground() {
        val policy = MediaViewerVisualPolicy(allowBlur = true)

        assertTrue(policy.usesDarkBackground(isDarkTheme = false))
        assertTrue(policy.usesDarkBackground(isDarkTheme = true))
    }

    @Test
    fun disabledBlurFollowsTheme() {
        val policy = MediaViewerVisualPolicy(allowBlur = false)

        assertFalse(policy.usesDarkBackground(isDarkTheme = false))
        assertTrue(policy.usesDarkBackground(isDarkTheme = true))
    }

    @Test
    fun groupedMemberResolvesToItsRepresentativePageAndExactMember() {
        assertEquals(
            MediaViewerInitialSelection(pageIndex = 1, memberId = 22L, found = true),
            resolveMediaViewerInitialSelection(
                mediaId = 22L,
                pagerMediaIds = listOf(10L, 20L, 30L),
                mediaGroupIds = mapOf(20L to listOf(20L, 21L, 22L)),
            ),
        )
    }

    @Test
    fun completedViewerDismissesInsteadOfShowingRandomMediaWhenTargetIsMissing() {
        assertTrue(
            shouldDismissMissingMediaTarget(
                isLoading = false,
                targetFound = false,
                hasMedia = true,
                isStandalone = false,
            )
        )
        assertFalse(
            shouldDismissMissingMediaTarget(
                isLoading = true,
                targetFound = false,
                hasMedia = true,
                isStandalone = false,
            )
        )
    }

    @Test
    fun sharedElementUsesCurrentPageWhenSettledPageLagsAfterFastScroll() {
        assertFalse(isMediaViewerSharedElementPage(page = 0, currentPage = 1))
        assertTrue(isMediaViewerSharedElementPage(page = 1, currentPage = 1))
    }

    @Test
    fun tapNavigationUsesLogicalThirdsAndKeepsBoundariesInTheCenter() {
        assertEquals(TapNavigationZone.Start, resolveTapNavigationZone(10f, 300, isRtl = false))
        assertEquals(TapNavigationZone.Center, resolveTapNavigationZone(100f, 300, isRtl = false))
        assertEquals(TapNavigationZone.Center, resolveTapNavigationZone(200f, 300, isRtl = false))
        assertEquals(TapNavigationZone.End, resolveTapNavigationZone(290f, 300, isRtl = false))
        assertEquals(TapNavigationZone.End, resolveTapNavigationZone(10f, 300, isRtl = true))
        assertEquals(TapNavigationZone.Start, resolveTapNavigationZone(290f, 300, isRtl = true))
        assertEquals(TapNavigationZone.Center, resolveTapNavigationZone(Float.NaN, 300, isRtl = false))
        assertEquals(TapNavigationZone.Center, resolveTapNavigationZone(10f, 0, isRtl = false))
    }

    @Test
    fun sideNavigationHandlesEligibleTapsImmediately() {
        assertTrue(
            shouldHandleTapImmediately(
                tapNavigationEnabled = true,
                zone = TapNavigationZone.Start,
                canNavigate = true,
            )
        )
        assertTrue(
            shouldHandleTapImmediately(
                tapNavigationEnabled = true,
                zone = TapNavigationZone.End,
                canNavigate = true,
            )
        )
        assertFalse(
            shouldHandleTapImmediately(
                tapNavigationEnabled = true,
                zone = TapNavigationZone.Center,
                canNavigate = true,
            )
        )
        assertFalse(
            shouldHandleTapImmediately(
                tapNavigationEnabled = false,
                zone = TapNavigationZone.End,
                canNavigate = true,
            )
        )
        assertFalse(
            shouldHandleTapImmediately(
                tapNavigationEnabled = true,
                zone = TapNavigationZone.End,
                canNavigate = false,
            )
        )
    }

    @Test
    fun tapNavigationTargetsAdjacentPagesWithoutWrapping() {
        assertEquals(1, resolveTapNavigationTarget(TapNavigationZone.Start, currentPage = 2, pageCount = 4))
        assertEquals(3, resolveTapNavigationTarget(TapNavigationZone.End, currentPage = 2, pageCount = 4))
        assertNull(resolveTapNavigationTarget(TapNavigationZone.Start, currentPage = 0, pageCount = 4))
        assertNull(resolveTapNavigationTarget(TapNavigationZone.End, currentPage = 3, pageCount = 4))
        assertNull(resolveTapNavigationTarget(TapNavigationZone.Center, currentPage = 2, pageCount = 4))
        assertNull(resolveTapNavigationTarget(TapNavigationZone.End, currentPage = 0, pageCount = 0))
    }

    @Test
    fun tapNavigationPromptWaitsForAnEligibleViewer() {
        assertTrue(
            isTapNavigationPromptEligible(
                tapNavigationEnabled = false,
                isStandalone = false,
                slideshowActive = false,
                pageCount = 2,
                initialPageSetup = true,
                isOrdinaryImage = true,
                viewerSettled = true,
            )
        )
        assertFalse(
            isTapNavigationPromptEligible(
                tapNavigationEnabled = true,
                isStandalone = false,
                slideshowActive = false,
                pageCount = 2,
                initialPageSetup = true,
                isOrdinaryImage = true,
                viewerSettled = true,
            )
        )
        assertFalse(
            isTapNavigationPromptEligible(
                tapNavigationEnabled = false,
                isStandalone = true,
                slideshowActive = false,
                pageCount = 2,
                initialPageSetup = true,
                isOrdinaryImage = true,
                viewerSettled = true,
            )
        )
        assertFalse(
            isTapNavigationPromptEligible(
                tapNavigationEnabled = false,
                isStandalone = false,
                slideshowActive = false,
                pageCount = 1,
                initialPageSetup = true,
                isOrdinaryImage = true,
                viewerSettled = true,
            )
        )
        assertFalse(
            isTapNavigationPromptEligible(
                tapNavigationEnabled = false,
                isStandalone = false,
                slideshowActive = false,
                pageCount = 2,
                initialPageSetup = true,
                isOrdinaryImage = false,
                viewerSettled = true,
            )
        )
        assertFalse(
            isTapNavigationPromptEligible(
                tapNavigationEnabled = false,
                isStandalone = false,
                slideshowActive = true,
                pageCount = 2,
                initialPageSetup = true,
                isOrdinaryImage = true,
                viewerSettled = true,
            )
        )
        assertFalse(
            isTapNavigationPromptEligible(
                tapNavigationEnabled = false,
                isStandalone = false,
                slideshowActive = false,
                pageCount = 2,
                initialPageSetup = true,
                isOrdinaryImage = true,
                viewerSettled = false,
            )
        )
    }
}
