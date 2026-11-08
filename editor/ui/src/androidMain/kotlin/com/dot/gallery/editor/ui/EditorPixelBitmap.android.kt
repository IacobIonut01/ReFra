package com.dot.gallery.editor.ui

import androidx.compose.ui.graphics.asAndroidBitmap
import com.dot.gallery.editor.render.SrgbPixels
import com.github.panpf.sketch.Image
import com.github.panpf.sketch.asImage
import com.github.panpf.zoomimage.subsampling.TileBitmap

internal actual fun SrgbPixels.toEditorTileBitmap(): TileBitmap =
    toEditorImageBitmap().asAndroidBitmap()

internal actual fun SrgbPixels.toEditorSketchImage(): Image = toEditorTileBitmap().asImage()
