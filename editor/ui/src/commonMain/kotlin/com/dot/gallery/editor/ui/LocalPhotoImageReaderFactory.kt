package com.dot.gallery.editor.ui

import androidx.compose.runtime.staticCompositionLocalOf
import com.dot.gallery.editor.core.model.PhotoDocument
import com.dot.gallery.editor.render.photo.PhotoImageReader

internal val LocalPhotoImageReaderFactory =
    staticCompositionLocalOf<(suspend (PhotoDocument) -> PhotoImageReader)?> { null }
