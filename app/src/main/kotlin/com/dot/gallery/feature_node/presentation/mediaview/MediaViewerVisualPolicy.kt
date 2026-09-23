package com.dot.gallery.feature_node.presentation.mediaview

import androidx.compose.runtime.staticCompositionLocalOf

data class MediaViewerVisualPolicy(
    val allowBlur: Boolean,
    val forceDarkBackground: Boolean = false,
    // False when the user disabled "Animate media items" — viewer chrome, flights and tweens
    // all resolve instantly instead of animating.
    val animationsEnabled: Boolean = true,
) {
    fun usesDarkBackground(isDarkTheme: Boolean): Boolean =
        allowBlur || isDarkTheme || forceDarkBackground
}

val LocalMediaViewerVisualPolicy = staticCompositionLocalOf {
    MediaViewerVisualPolicy(allowBlur = false)
}
