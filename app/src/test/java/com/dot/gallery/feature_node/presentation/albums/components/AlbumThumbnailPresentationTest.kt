package com.dot.gallery.feature_node.presentation.albums.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AlbumThumbnailPresentationTest {

    @Test
    fun lockedAlbumUsesPlaceholderWithoutMediaRequest() {
        val presentation = albumThumbnailPresentation(isLocked = true)

        assertEquals(AlbumThumbnailPresentation.LOCKED_PLACEHOLDER, presentation)
        assertFalse(presentation.allowsMediaRequest)
    }

    @Test
    fun unlockedAlbumUsesMediaPresentation() {
        val presentation = albumThumbnailPresentation(isLocked = false)

        assertEquals(AlbumThumbnailPresentation.MEDIA, presentation)
        assertTrue(presentation.allowsMediaRequest)
    }

    @Test
    fun coverPickerStaysWhileSheetIsOpen() {
        assertTrue(
            nextAlbumCoverPickerState(currentlySelecting = true, sheetVisible = true)
        )
    }

    @Test
    fun dismissingSheetLeavesCoverPicker() {
        assertFalse(
            nextAlbumCoverPickerState(currentlySelecting = true, sheetVisible = false)
        )
    }

    @Test
    fun finishingSystemPickerLeavesCoverPicker() {
        assertFalse(
            nextAlbumCoverPickerState(
                currentlySelecting = true,
                sheetVisible = true,
                pickerFinished = true
            )
        )
    }
}
