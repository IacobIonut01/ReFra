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
                isPartial = false,
                targetFound = false,
                hasMedia = true,
                isStandalone = false,
            )
        )
        assertFalse(
            shouldDismissMissingMediaTarget(
                isLoading = true,
                isPartial = false,
                targetFound = false,
                hasMedia = true,
                isStandalone = false,
            )
        )
    }

    @Test
    fun partialViewerDataDoesNotTriggerMissingTargetDismiss() {
        // A partial state still has batches in flight — the tapped item may simply
        // not be loaded yet, so it must not dismiss the viewer (#1069 hardening).
        assertFalse(
            shouldDismissMissingMediaTarget(
                isLoading = false,
                isPartial = true,
                targetFound = false,
                hasMedia = true,
                isStandalone = false,
            )
        )
    }

    @Test
    fun slideshowWaitsForMediaLoadingBeforeExitingAnEmptyPlaylist() {
        assertFalse(
            shouldExitEmptySlideshow(
                isActive = true,
                isLoading = true,
                hasItems = false,
            )
        )
        assertTrue(
            shouldExitEmptySlideshow(
                isActive = true,
                isLoading = false,
                hasItems = false,
            )
        )
        assertFalse(
            shouldExitEmptySlideshow(
                isActive = true,
                isLoading = false,
                hasItems = true,
            )
        )
        assertFalse(
            shouldExitEmptySlideshow(
                isActive = false,
                isLoading = false,
                hasItems = false,
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

    @Test
    fun deepAlbumTargetResolvesItsExactPagerIndex() {
        val pagerMediaIds = (1L..1200L).toList()

        assertEquals(
            MediaViewerInitialSelection(pageIndex = 949, memberId = null, found = true),
            resolveMediaViewerInitialSelection(
                mediaId = 950L,
                pagerMediaIds = pagerMediaIds,
                mediaGroupIds = emptyMap(),
            ),
        )
    }

    @Test
    fun groupedMemberBeyondPage250ResolvesToRepresentativeAndExactMember() {
        val pagerMediaIds = (1L..600L).filter { it != 554L && it != 555L }

        assertEquals(
            MediaViewerInitialSelection(pageIndex = 399, memberId = 555L, found = true),
            resolveMediaViewerInitialSelection(
                mediaId = 555L,
                pagerMediaIds = pagerMediaIds,
                mediaGroupIds = mapOf(400L to listOf(400L, 554L, 555L)),
            ),
        )
    }

    @Test
    fun viewerContentIsReadyWhenLoadedSelectionAlreadyMatchesInitialPage() {
        assertTrue(
            isMediaViewerContentReady(
                selectionApplied = false,
                isLoading = false,
                targetFound = true,
                currentPage = 5,
                initialPage = 5,
            )
        )
    }

    @Test
    fun viewerContentIsNotReadyWhileLoadingMissingOrMisaligned() {
        assertFalse(
            isMediaViewerContentReady(
                selectionApplied = false,
                isLoading = true,
                targetFound = true,
                currentPage = 5,
                initialPage = 5,
            )
        )
        assertFalse(
            isMediaViewerContentReady(
                selectionApplied = false,
                isLoading = false,
                targetFound = false,
                currentPage = 5,
                initialPage = 5,
            )
        )
        assertFalse(
            isMediaViewerContentReady(
                selectionApplied = false,
                isLoading = false,
                targetFound = true,
                currentPage = 3,
                initialPage = 5,
            )
        )
    }

    @Test
    fun viewerContentStaysReadyAfterAppliedSelectionEvenWhenUserSwiped() {
        assertTrue(
            isMediaViewerContentReady(
                selectionApplied = true,
                isLoading = false,
                targetFound = true,
                currentPage = 7,
                initialPage = 5,
            )
        )
    }

    @Test
    fun surrogateStaysHiddenOnceTheViewerIsSettled() {
        assertFalse(
            isViewerSurrogateVisible(
                contentReady = true,
                transitionRunning = false,
                dismissActive = false,
                dismissedVisualHidden = false,
            )
        )
    }

    @Test
    fun surrogateStaysVisibleWhileMediaLoadsOrDuringTransitionAndDismiss() {
        assertTrue(
            isViewerSurrogateVisible(
                contentReady = false,
                transitionRunning = false,
                dismissActive = false,
                dismissedVisualHidden = false,
            )
        )
        assertTrue(
            isViewerSurrogateVisible(
                contentReady = true,
                transitionRunning = true,
                dismissActive = false,
                dismissedVisualHidden = false,
            )
        )
        assertTrue(
            isViewerSurrogateVisible(
                contentReady = true,
                transitionRunning = false,
                dismissActive = true,
                dismissedVisualHidden = false,
            )
        )
    }

    @Test
    fun surrogateStaysHiddenWhenAReturnFlightOwnsTheVisual() {
        assertFalse(
            isViewerSurrogateVisible(
                contentReady = false,
                transitionRunning = true,
                dismissActive = true,
                dismissedVisualHidden = true,
            )
        )
    }

    @Test
    fun secondPressInsideTheDoubleTapWindowIsDetected() {
        assertTrue(
            isSecondTapPress(
                downUptime = 1200,
                lastTapUpUptime = 1000,
                doubleTapMinMillis = 40,
                doubleTapTimeoutMillis = 300,
            )
        )
        assertFalse(
            isSecondTapPress(
                downUptime = 1020,
                lastTapUpUptime = 1000,
                doubleTapMinMillis = 40,
                doubleTapTimeoutMillis = 300,
            )
        )
        assertFalse(
            isSecondTapPress(
                downUptime = 1400,
                lastTapUpUptime = 1000,
                doubleTapMinMillis = 40,
                doubleTapTimeoutMillis = 300,
            )
        )
        // First gesture ever — the unset sentinel must not produce a hit.
        assertFalse(
            isSecondTapPress(
                downUptime = 5000,
                lastTapUpUptime = Long.MIN_VALUE,
                doubleTapMinMillis = 40,
                doubleTapTimeoutMillis = 300,
            )
        )
    }

    @Test
    fun onlyAQuickStationaryReleaseCountsAsACleanTap() {
        assertTrue(
            isCleanTap(
                upUptime = 1100,
                downUptime = 1000,
                dragDistance = 3f,
                longPressTimeoutMillis = 500,
                touchSlop = 18f,
            )
        )
        assertFalse(
            isCleanTap(
                upUptime = 1600,
                downUptime = 1000,
                dragDistance = 3f,
                longPressTimeoutMillis = 500,
                touchSlop = 18f,
            )
        )
        assertFalse(
            isCleanTap(
                upUptime = 1100,
                downUptime = 1000,
                dragDistance = 40f,
                longPressTimeoutMillis = 500,
                touchSlop = 18f,
            )
        )
    }
}
