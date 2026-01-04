package com.dot.gallery.feature_node.presentation.util

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import com.dot.gallery.core.DefaultEventHandler
import com.dot.gallery.core.LocalEventHandler
import com.dot.gallery.ui.theme.GalleryTheme

@Composable
fun PreviewHost(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    CompositionLocalProvider(LocalEventHandler provides DefaultEventHandler()) {
        GalleryTheme(
            darkTheme = darkTheme,
            content = content
        )
    }
}