package com.dot.gallery.app.presentation.util

import com.dot.gallery.domain.settings.EDITOR_BUILTIN
import com.dot.gallery.domain.settings.EDITOR_STUDIO
import com.dot.gallery.domain.settings.SettingsKeys
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class StudioEditorRoutingTest {

    @Test
    fun photosAndVideosDefaultToTheNewEditor() {
        assertEquals(EDITOR_STUDIO, SettingsKeys.defaultImageEditor.default)
        assertEquals(EDITOR_STUDIO, SettingsKeys.defaultVideoEditor.default)
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

    @Test
    fun staleBuiltinImageDefaultResolvesToStudio() {
        // A "builtin" written before the studio option existed is the old
        // default, not a deliberate choice — upgraded installs get the new editor.
        assertEquals(EDITOR_STUDIO, resolveImageEditorChoice(EDITOR_BUILTIN, explicitChoice = false))
    }

    @Test
    fun explicitBuiltinChoiceStaysBuiltin() {
        assertEquals(EDITOR_BUILTIN, resolveImageEditorChoice(EDITOR_BUILTIN, explicitChoice = true))
    }

    @Test
    fun otherStoredEditorsResolveUnchanged() {
        assertEquals(EDITOR_STUDIO, resolveImageEditorChoice(EDITOR_STUDIO, explicitChoice = false))
        assertEquals("com.example.editor", resolveImageEditorChoice("com.example.editor", explicitChoice = false))
    }
}
