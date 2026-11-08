package com.dot.gallery.editor.ui

import android.graphics.Bitmap
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.dot.gallery.editor.render.SrgbPixels

internal actual fun SrgbPixels.toEditorImageBitmap(): ImageBitmap =
    Bitmap.createBitmap(
        copyPixels(), size.width, size.height, Bitmap.Config.ARGB_8888
    ).asImageBitmap()
