package com.dot.gallery.core.util

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import com.dot.gallery.core.Settings
import android.graphics.drawable.ColorDrawable
import androidx.core.graphics.drawable.toDrawable
import com.bumptech.glide.integration.compose.ExperimentalGlideComposeApi
import com.bumptech.glide.integration.compose.GlideImage
import com.bumptech.glide.integration.compose.placeholder
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.load.resource.gif.GifDrawable
import com.bumptech.glide.signature.ObjectKey
import com.dot.gallery.core.LocalEventHandler
import com.dot.gallery.core.LocalMediaDistributor
import com.dot.gallery.core.LocalMediaHandler
import com.dot.gallery.core.LocalMediaSelector
import com.dot.gallery.core.MediaDistributor
import com.dot.gallery.core.MediaHandler
import com.dot.gallery.core.MediaSelector
import com.dot.gallery.core.presentation.components.LocalMediaImageRenderer
import com.dot.gallery.core.presentation.components.MediaImageRenderer
import com.dot.gallery.feature_node.domain.util.EventHandler

/**
 * Default [MediaImageRenderer] that uses GlideImage with full caching,
 * thumbnail generation, GIF animation, and cache-invalidation signatures.
 *
 * GIF thumbnail animation is controlled by the [Settings.Misc.rememberAllowGifAnimation]
 * preference. The [signature] parameter (typically the Media or Album object) is used both
 * for Glide cache invalidation and for GIF filename detection via `toString()`.
 */
@OptIn(ExperimentalGlideComposeApi::class)
val GlideMediaImageRenderer = object : MediaImageRenderer {
    @Composable
    override fun RenderImage(
        modifier: Modifier,
        model: Any?,
        contentScale: ContentScale,
        contentDescription: String?,
        signature: Any?
    ) {
        val allowGifAnimation by Settings.Misc.rememberAllowGifAnimation()
        val signatureStr = signature?.toString() ?: ""
        val isGif = allowGifAnimation && signatureStr.contains(".gif", ignoreCase = true)
        GlideImage(
            modifier = modifier,
            model = model,
            contentDescription = contentDescription,
            contentScale = contentScale,
            loading = placeholder(0x4D444444.toDrawable()),
            failure = placeholder(0x33444444.toDrawable()),
            requestBuilderTransform = {
                var request = it.centerCrop().diskCacheStrategy(DiskCacheStrategy.ALL)
                request = request.thumbnail(request.clone().sizeMultiplier(0.4f))
                if (signature != null) {
                    request = request.signature(ObjectKey(signatureStr))
                }
                if (isGif) {
                    request = request.decode(GifDrawable::class.java)
                }
                request
            }
        )
    }
}

@Composable
fun SetupMediaProviders(
    eventHandler: EventHandler,
    mediaDistributor: MediaDistributor,
    mediaHandler: MediaHandler,
    mediaSelector: MediaSelector,
    mediaImageRenderer: MediaImageRenderer = GlideMediaImageRenderer,
    content: @Composable () -> Unit
) = CompositionLocalProvider(
    LocalEventHandler provides eventHandler,
    LocalMediaDistributor provides mediaDistributor,
    LocalMediaHandler provides mediaHandler,
    LocalMediaSelector provides mediaSelector,
    LocalMediaImageRenderer provides mediaImageRenderer,
    content = content
)
