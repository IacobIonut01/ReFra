package com.dot.gallery.editor.ui

import androidx.compose.ui.graphics.asSkiaBitmap
import com.dot.gallery.editor.render.SrgbPixels
import com.github.panpf.sketch.Image
import com.github.panpf.sketch.asImage
import com.github.panpf.zoomimage.subsampling.TileBitmap

internal actual fun SrgbPixels.toEditorTileBitmap(): TileBitmap =
    toEditorImageBitmap().asSkiaBitmap()

internal actual fun SrgbPixels.toEditorSketchImage(): Image = toEditorTileBitmap().asImage()
