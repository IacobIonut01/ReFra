package com.dot.gallery.app.presentation.albums.components

import com.dot.gallery.feature.albums.ui.components.albumThumbnailPresentation
import com.dot.gallery.feature.albums.ui.components.AlbumThumbnailPresentation
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
}
