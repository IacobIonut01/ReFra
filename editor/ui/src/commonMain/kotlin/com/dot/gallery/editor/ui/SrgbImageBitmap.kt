package com.dot.gallery.editor.ui

import androidx.compose.ui.graphics.ImageBitmap
import com.dot.gallery.editor.render.SrgbPixels

/**
 * Converts the editor's straight-alpha sRGB pixels to a display bitmap.
 * Expensive — call off the UI thread (the content composable runs it inside
 * `produceState` on `Dispatchers.Default`).
 */
internal expect fun SrgbPixels.toEditorImageBitmap(): ImageBitmap
