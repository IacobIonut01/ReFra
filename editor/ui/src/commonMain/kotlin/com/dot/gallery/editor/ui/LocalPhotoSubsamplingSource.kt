package com.dot.gallery.editor.ui

import androidx.compose.runtime.compositionLocalOf
import com.github.panpf.zoomimage.subsampling.ImageSource

/**
 * Host-provided factory that turns a photo source reference into a ZoomImage
 * [ImageSource] the canvas can subsample — `ContentImageSource` for Android
 * `content://`/`file://` references, `FileImageSource` for desktop paths.
 * A null factory (or a null result) simply keeps deep zoom on the preview
 * bitmap; nothing else changes.
 */
@Deprecated("PhotoEditorScreen now provides pixel-backed readers through its session.")
val LocalPhotoSubsamplingSource =
    compositionLocalOf<(String) -> ImageSource?> { { null } }
