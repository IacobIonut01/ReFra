package com.dot.gallery.editor.desktop

import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The workbench's file routing — a picked/arg'd path lands on the photo or
 * video surface by container extension, case-insensitively; unknown
 * extensions default to photo, whose open-error UI reports honestly.
 */
class EditorFileKindTest {

    @Test
    fun videoExtensionsRouteToVideo() {
        listOf("mp4", "mov", "webm", "mkv", "MP4", "Mov").forEach { ext ->
            assertEquals(
                EditorFileKind.Video,
                editorFileKind(Path.of("/tmp/clip.$ext")),
                "$ext should route to video"
            )
        }
    }

    @Test
    fun photoExtensionsRouteToPhoto() {
        listOf("jpg", "png", "heic", "JPG", "dng").forEach { ext ->
            assertEquals(
                EditorFileKind.Photo,
                editorFileKind(Path.of("/tmp/shot.$ext")),
                "$ext should route to photo"
            )
        }
    }

    @Test
    fun unknownOrMissingExtensionDefaultsToPhoto() {
        assertEquals(EditorFileKind.Photo, editorFileKind(Path.of("/tmp/blob.xyz")))
        assertEquals(EditorFileKind.Photo, editorFileKind(Path.of("/tmp/noext")))
    }
}
