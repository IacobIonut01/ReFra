package com.dot.gallery.editor.ui

import com.dot.gallery.editor.render.SrgbPixels
import com.github.panpf.sketch.Image
import com.github.panpf.zoomimage.subsampling.TileBitmap

internal expect fun SrgbPixels.toEditorTileBitmap(): TileBitmap
internal expect fun SrgbPixels.toEditorSketchImage(): Image
