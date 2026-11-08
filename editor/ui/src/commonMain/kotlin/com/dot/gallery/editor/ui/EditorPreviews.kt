package com.dot.gallery.editor.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.dot.gallery.editor.core.model.MediaAsset
import com.dot.gallery.editor.core.model.PhotoDocument
import com.dot.gallery.editor.core.model.PixelSize
import com.dot.gallery.editor.render.SrgbPixels
import com.dot.gallery.editor.render.photo.PhotoEditorState
import com.dot.gallery.editor.render.photo.PhotoInfo
import org.jetbrains.compose.ui.tooling.preview.Preview

/**
 * Preview fixture — a procedural gradient, not a photograph. Test-only
 * imagery lives in test source sets; production never falls back to samples.
 */
private fun previewPixels(): SrgbPixels {
    val w = 96
    val h = 72
    val pixels = IntArray(w * h) { i ->
        val x = i % w
        val y = i / w
        val r = (x * 255 / (w - 1))
        val g = (y * 255 / (h - 1))
        val b = 160
        (0xFF shl 24) or (r shl 16) or (g shl 8) or b
    }
    return SrgbPixels(PixelSize(w, h), pixels)
}

private fun previewState(): PhotoEditorState {
    val info = PhotoInfo(
        reference = "preview://sample",
        displayName = "sample.jpg",
        rawSize = PixelSize(96, 72),
        orientation = 1
    )
    val asset = MediaAsset.Image("preview://sample", "preview://sample", PixelSize(96, 72))
    return PhotoEditorState(
        info = info,
        document = PhotoDocument(asset),
        preview = previewPixels(),
        originalPreview = null,
        isDirty = false
    )
}

private val previewActions = PhotoEditorActions(
    beginEdit = {},
    updateEdits = {},
    commitEdit = {},
    cancelEdit = {},
    applyEdits = { _, _ -> },
    undo = {},
    redo = {},
    reset = {},
    saveCopy = {},
    cancelExport = {},
    retryOpen = {},
    retryPreview = {},
    clearError = {},
    close = {}
)

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun PhotoEditorPortraitPreview() {
    EditorTheme {
        PhotoEditorContent(
            state = previewState(),
            actions = previewActions,
            modifier = Modifier
        )
    }
}

@Preview(widthDp = 800, heightDp = 360)
@Composable
private fun PhotoEditorLandscapePreview() {
    EditorTheme {
        PhotoEditorContent(
            state = previewState(),
            actions = previewActions,
            modifier = Modifier
        )
    }
}

@Preview(widthDp = 1280, heightDp = 800)
@Composable
private fun PhotoEditorDesktopPreview() {
    EditorTheme(darkTheme = false) {
        PhotoEditorContent(
            state = previewState(),
            actions = previewActions,
            modifier = Modifier
        )
    }
}
