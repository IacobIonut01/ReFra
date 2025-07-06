package com.dot.gallery.core.util

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import com.dot.gallery.core.LocalEventHandler
import com.dot.gallery.core.LocalMediaDistributor
import com.dot.gallery.core.LocalMediaHandler
import com.dot.gallery.core.LocalMediaSelector
import com.dot.gallery.core.MediaDistributor
import com.dot.gallery.core.MediaHandler
import com.dot.gallery.core.MediaSelector
import com.dot.gallery.feature_node.domain.util.EventHandler

@Composable
fun SetupMediaProviders(
    eventHandler: EventHandler,
    mediaDistributor: MediaDistributor,
    mediaHandler: MediaHandler,
    mediaSelector: MediaSelector,
    content: @Composable () -> Unit
) = CompositionLocalProvider(
    LocalEventHandler provides eventHandler,
    LocalMediaDistributor provides mediaDistributor,
    LocalMediaHandler provides mediaHandler,
    LocalMediaSelector provides mediaSelector,
    content = content
)
