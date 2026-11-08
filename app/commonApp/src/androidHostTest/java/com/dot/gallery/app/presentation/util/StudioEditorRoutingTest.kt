package com.dot.gallery.app.presentation.util

import com.dot.gallery.domain.settings.EDITOR_BUILTIN
import com.dot.gallery.domain.settings.EDITOR_STUDIO
import com.dot.gallery.domain.settings.SettingsKeys
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class StudioEditorRoutingTest {

    @Test
    fun videosDefaultToTheNewEditorWhilePhotosKeepTheirDefault() {
        assertEquals(EDITOR_STUDIO, SettingsKeys.defaultVideoEditor.default)
        assertEquals(EDITOR_BUILTIN, SettingsKeys.defaultImageEditor.default)
    }

    @Test
    fun everyVideoTypeOpensInTheVideoEditor() {
        listOf(
            "video/mp4", "video/quicktime", "video/x-matroska", "video/webm", "video/avi",
            "video/x-msvideo", "video/x-ms-wmv", "video/3gpp", "video/mp2t", "VIDEO/MP4"
        ).forEach { mime ->
            assertEquals(
                mime,
                "com.dot.gallery.editor.GalleryVideoEditorActivity",
                studioEditorActivity(mime)
            )
        }
    }

    @Test
    fun photosAndOtherTypesKeepTheirExistingRoutes() {
        assertEquals("com.dot.gallery.editor.GalleryEditorActivity", studioEditorActivity("image/jpeg"))
        assertEquals("com.dot.gallery.editor.GalleryEditorActivity", studioEditorActivity("image/x-adobe-dng"))
        assertNull(studioEditorActivity("image/heic"))
        assertNull(studioEditorActivity("audio/mpeg"))
    }
}
