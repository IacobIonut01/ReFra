package com.dot.gallery.feature_node.presentation.mediaview

import androidx.compose.runtime.staticCompositionLocalOf

data class MediaViewerVisualPolicy(
    val allowBlur: Boolean,
    val forceDarkBackground: Boolean = false,
) {
    fun usesDarkBackground(isDarkTheme: Boolean): Boolean =
        allowBlur || isDarkTheme || forceDarkBackground
}

val LocalMediaViewerVisualPolicy = staticCompositionLocalOf {
    MediaViewerVisualPolicy(allowBlur = false)
}
