package com.dot.gallery.location

import com.dot.gallery.feature_node.presentation.location.GalleryCameraPosition
import com.dot.gallery.feature_node.presentation.location.GalleryMapLoadState
import com.dot.gallery.feature_node.presentation.location.GalleryMapState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GalleryMapStateTest {
    @Test
    fun styleRequestStartsTimeoutBeforeMapAttaches() {
        val state = GalleryMapState(GalleryCameraPosition())

        state.loadStyle("https://example.com/style")

        assertEquals(GalleryMapLoadState.LOADING, state.loadState)
        assertTrue(state.loadAttempt > 0L)
        state.onMapLoadTimedOut(state.loadAttempt)
        assertEquals(GalleryMapLoadState.FAILED, state.loadState)
    }

    @Test
    fun staleTimeoutDoesNotFailCurrentRequest() {
        val state = GalleryMapState(GalleryCameraPosition())
        state.loadStyle("https://example.com/first")
        val firstAttempt = state.loadAttempt
        state.loadStyle("https://example.com/second")

        state.onMapLoadTimedOut(firstAttempt)

        assertEquals(GalleryMapLoadState.LOADING, state.loadState)
        state.onMapLoadTimedOut(state.loadAttempt)
        assertEquals(GalleryMapLoadState.FAILED, state.loadState)
    }

    @Test
    fun retryWithoutAttachedMapStartsNewAttemptAndRequestsNewView() {
        val state = GalleryMapState(GalleryCameraPosition())
        state.loadStyle("https://example.com/style")
        state.onMapLoadFailed()
        val failedAttempt = state.loadAttempt

        state.retryStyle()

        assertEquals(GalleryMapLoadState.LOADING, state.loadState)
        assertTrue(state.loadAttempt > failedAttempt)
        assertEquals(1L, state.mapViewGeneration)
    }
}
