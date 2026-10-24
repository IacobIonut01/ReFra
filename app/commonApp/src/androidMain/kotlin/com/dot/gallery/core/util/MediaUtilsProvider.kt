package com.dot.gallery.core.util

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import com.dot.gallery.core.Settings
import android.graphics.drawable.Drawable
import androidx.core.graphics.drawable.toDrawable
import com.bumptech.glide.integration.compose.ExperimentalGlideComposeApi
import com.bumptech.glide.integration.compose.GlideImage
import com.bumptech.glide.integration.compose.placeholder
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.load.engine.GlideException
import com.bumptech.glide.load.resource.gif.GifDrawable
import com.bumptech.glide.request.RequestListener
import com.bumptech.glide.request.target.Target
import com.bumptech.glide.signature.ObjectKey
import com.dot.gallery.core.LocalEventHandler
import com.dot.gallery.core.LocalMediaDistributor
import com.dot.gallery.core.LocalMediaHandler
import com.dot.gallery.core.LocalMediaSelector
import com.dot.gallery.core.MediaDistributor
import com.dot.gallery.core.MediaHandler
import com.dot.gallery.core.MediaSelector
import com.dot.gallery.core.image.thumbnail.RefinedThumbnailKey
import com.dot.gallery.core.image.thumbnail.RefinedThumbnailLoadTracker
import com.dot.gallery.core.image.thumbnail.ThumbnailTelemetry
import com.dot.gallery.core.image.thumbnail.shouldUseMotionThumbnail
import android.content.Context
import androidx.compose.ui.platform.LocalContext
import com.github.panpf.sketch.AsyncImage
import com.github.panpf.sketch.request.ComposableImageRequest
import com.github.panpf.sketch.request.ImageRequest
import com.github.panpf.sketch.request.ImageResult
import com.github.panpf.sketch.request.Listener
import com.github.panpf.sketch.request.disallowAnimatedImage
import com.github.panpf.sketch.sketch
import com.github.panpf.sketch.state.IntColorDrawableStateImage
import com.dot.gallery.domain.util.getUri
import com.dot.gallery.core.presentation.components.LocalMediaPrefetcher
import com.dot.gallery.core.presentation.components.LocalMediaImageRenderer
import com.dot.gallery.core.presentation.components.MediaImageRenderer
import com.dot.gallery.domain.util.EventHandler
import com.dot.gallery.domain.model.Album
import com.dot.gallery.domain.model.Media
import com.dot.gallery.core.image.thumbnail.LocalThumbnailMotion
import com.dot.gallery.domain.util.isGif
import com.dot.gallery.core.presentation.components.MediaPrefetcher
import com.dot.gallery.feature.viewer.LocalViewerRuntime
import com.dot.gallery.feature.viewer.components.media.mediaPreviewOptions
import com.dot.gallery.injection.ComponentOwner




/**
 * Software-decoded image formats with no hardware decoder. They are expensive to decode, so the
 * timeline renderer avoids the extra 0.4x thumbnail pass for them (which would decode twice).
 */
private val HEAVY_CODEC_EXTENSIONS = listOf(
    ".heic", ".heif", ".avif", ".avis", ".jxl", ".tiff", ".tif", ".psd", ".jp2", ".j2k"
)

/**
 * Pixel bound for the cheap MOTION tier loaded during a fling (#1076 Phase 3). Small enough to
 * decode quickly (and to be served by the platform MediaStore thumbnail fast path) yet crisp
 * enough to avoid obvious blur on dense grids. Reused as the REFINED request's placeholder.
 */
private const val THUMBNAIL_MOTION_PX = 256
private val refinedThumbnailLoadTracker = RefinedThumbnailLoadTracker(capacity = 2_048)

private fun refinedThumbnailListener(onLoaded: () -> Unit) = object : RequestListener<Drawable> {
    override fun onLoadFailed(
        e: GlideException?,
        model: Any?,
        target: Target<Drawable>,
        isFirstResource: Boolean
    ): Boolean = false

    override fun onResourceReady(
        resource: Drawable,
        model: Any?,
        target: Target<Drawable>?,
        dataSource: DataSource,
        isFirstResource: Boolean
    ): Boolean {
        onLoaded()
        return false
    }
}

private fun refinedThumbnailSketchListener(onLoaded: () -> Unit) = object : Listener {
    override fun onSuccess(request: ImageRequest, result: ImageResult.Success) {
        onLoaded()
    }
}

/**
 * Default [MediaImageRenderer] that uses GlideImage with full caching,
 * thumbnail generation, GIF animation, and cache-invalidation signatures.
 *
 * The timeline keeps Glide: its thumbnail pipeline (MediaStore fast path, persistent
 * heavy-format store, encrypted/vault decoders) is the fastest cold-scroll path.
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
        // Phase 3 (#1076): scroll-aware tier. Completed refined loads never downgrade during motion.
        val isMoving = LocalThumbnailMotion.current?.value == true
        val signatureStr = signature?.toString() ?: ""
        val thumbnailKey = remember(model, signatureStr) {
            RefinedThumbnailKey(model = model, signature = signatureStr)
        }
        val hasLoadedRefined = remember(thumbnailKey) {
            mutableStateOf(refinedThumbnailLoadTracker.hasLoaded(thumbnailKey))
        }
        val refinedLoadListener = remember(thumbnailKey) {
            refinedThumbnailListener {
                refinedThumbnailLoadTracker.markLoaded(thumbnailKey)
                hasLoadedRefined.value = true
            }
        }
        val useMotionThumbnail = shouldUseMotionThumbnail(isMoving, hasLoadedRefined.value)
        // Animated formats stay static during motion; they may animate only once the list is idle.
        val isGif = allowGifAnimation && !isMoving && signatureStr.contains(".gif", ignoreCase = true)
        val isAnimatable = allowGifAnimation && !isMoving && (
            signatureStr.contains(".avif", ignoreCase = true) ||
            signatureStr.contains(".apng", ignoreCase = true)
        )
        // Heavy software codecs (no hardware decode) are expensive to decode. The idle refined pass
        // skips the extra thumbnail sub-request for them so they decode once, not twice.
        val isHeavyCodec = HEAVY_CODEC_EXTENSIONS.any { signatureStr.contains(it, ignoreCase = true) }
        val tier = if (useMotionThumbnail) "MOTION" else "REFINED"
        GlideImage(
            modifier = modifier,
            model = model,
            contentDescription = contentDescription,
            contentScale = contentScale,
            loading = placeholder(0x4D444444.toDrawable()),
            failure = placeholder(0x33444444.toDrawable()),
            requestBuilderTransform = {
                // #1276: AUTOMATIC, not ALL — ALL also source-caches the fetched InputStream for
                // LOCAL fetchers, copying each multi-MB original into the bounded disk cache and
                // evicting the decoded thumbnails it just wrote. Remote models still cache source.
                val base = it.centerCrop().diskCacheStrategy(DiskCacheStrategy.AUTOMATIC)
                // Cheap MOTION-tier sub-request: small, static, stable key. It is used both as the
                // standalone request during a fling and as the idle refined request's thumbnail
                // placeholder, so the same bitmap is decoded once and reused (no flicker/re-decode).
                var motion = base.clone().override(THUMBNAIL_MOTION_PX)
                if (signature != null) motion = motion.signature(ObjectKey(signatureStr))

                var request = if (useMotionThumbnail) {
                    // Moving and not yet refined: load only the cheap tier.
                    motion
                } else {
                    var refined = base
                    if (signature != null) refined = refined.signature(ObjectKey(signatureStr))
                    refined = refined.addListener(refinedLoadListener)
                    // Show the cached motion bitmap instantly while the refined image decodes.
                    if (!isHeavyCodec) refined = refined.thumbnail(motion)
                    refined
                }
                // Phase 1 (#1076): attach the bounded telemetry listener (staging/debug only;
                // null in release so this is a no-op there).
                ThumbnailTelemetry.listener(surface = "grid", tier = tier)?.let {
                    request = request.addListener(it)
                }
                if (isGif) {
                    request = request.decode(GifDrawable::class.java)
                } else if (isAnimatable) {
                    request = request.decode(Drawable::class.java)
                }
                request
            }
        )
    }
}

/**
 * Sketch 4 port of the grid renderer — same MOTION/REFINED tiers, telemetry and
 * signature-keyed invalidation as [GlideMediaImageRenderer]. Available as an alternative
 * for experiments; the timeline defaults to Glide for its faster thumbnail pipeline.
 *
 * GIF thumbnail animation is controlled by the [Settings.Misc.rememberAllowGifAnimation]
 * preference. The [signature] parameter (typically the Media or Album object) rides as a
 * request extra so it joins the cache key the way Glide's `ObjectKey` signature did —
 * change the signature, get a fresh decode — and feeds GIF filename detection via `toString()`.
 */
val SketchMediaImageRenderer = object : MediaImageRenderer {
    @Composable
    override fun RenderImage(
        modifier: Modifier,
        model: Any?,
        contentScale: ContentScale,
        contentDescription: String?,
        signature: Any?
    ) {
        val context = LocalContext.current
        val allowGifAnimation by Settings.Misc.rememberAllowGifAnimation()
        // Phase 3 (#1076): scroll-aware tier. Completed refined loads never downgrade during motion.
        val isMoving = LocalThumbnailMotion.current?.value == true
        val signatureStr = signature?.toString() ?: ""
        val thumbnailKey = remember(model, signatureStr) {
            RefinedThumbnailKey(model = model, signature = signatureStr)
        }
        val hasLoadedRefined = remember(thumbnailKey) {
            mutableStateOf(refinedThumbnailLoadTracker.hasLoaded(thumbnailKey))
        }
        val refinedLoadListener = remember(thumbnailKey) {
            refinedThumbnailSketchListener {
                refinedThumbnailLoadTracker.markLoaded(thumbnailKey)
                hasLoadedRefined.value = true
            }
        }
        val useMotionThumbnail = shouldUseMotionThumbnail(isMoving, hasLoadedRefined.value)
        // Animated formats stay static during motion; they may animate only once the list is idle.
        val animate = allowGifAnimation && !isMoving && (
            signatureStr.contains(".gif", ignoreCase = true) ||
            signatureStr.contains(".avif", ignoreCase = true) ||
            signatureStr.contains(".apng", ignoreCase = true)
        )
        // Heavy software codecs (no hardware decode) are expensive to decode. The idle refined pass
        // skips the extra thumbnail sub-request for them so they decode once, not twice.
        val isHeavyCodec = HEAVY_CODEC_EXTENSIONS.any { signatureStr.contains(it, ignoreCase = true) }
        val tier = if (useMotionThumbnail) "MOTION" else "REFINED"
        AsyncImage(
            modifier = modifier,
            contentDescription = contentDescription,
            contentScale = contentScale,
            request = ComposableImageRequest(model?.toString()) {
                placeholder(IntColorDrawableStateImage(0x4D444444))
                error(IntColorDrawableStateImage(0x33444444))
                if (signature != null) {
                    setExtra(key = "thumbnailSignature", value = signatureStr)
                }
                if (!animate) disallowAnimatedImage(true)
                if (useMotionThumbnail) {
                    // Moving and not yet refined: load only the cheap tier (small, static,
                    // stable key — the same bitmap doubles as the refined request's thumbnail).
                    size(THUMBNAIL_MOTION_PX, THUMBNAIL_MOTION_PX)
                } else {
                    addListener(refinedLoadListener)
                    // Show the cached motion bitmap instantly while the refined image decodes.
                    if (!isHeavyCodec) {
                        thumbnail(
                            ImageRequest(context, model?.toString()) {
                                size(THUMBNAIL_MOTION_PX, THUMBNAIL_MOTION_PX)
                                if (signature != null) {
                                    setExtra(key = "thumbnailSignature", value = signatureStr)
                                }
                                disallowAnimatedImage(true)
                            }
                        )
                    }
                }
                // Phase 1 (#1076): attach the bounded telemetry listener (staging/debug only;
                // null in release so this is a no-op there).
                ThumbnailTelemetry.sketchListener(surface = "grid", tier = tier)?.let {
                    addListener(it)
                }
            }
        )
    }
}


/**
 * Sketch-backed [MediaPrefetcher]: warms the viewer's low-res preview cache before the viewer
 * opens. The request comes from the same builder the viewer uses ([mediaPreviewOptions]), so the
 * prefetched entry is a cache hit for the shared-element thumbnail *and* the blurred backdrop
 * that the viewer derives from it (#1004).
 */
private fun sketchMediaPrefetcher(context: Context) = MediaPrefetcher { media ->
    context.sketch.enqueue(
        ImageRequest(context, media.getUri().toString()) {
            mediaPreviewOptions(media)
        }
    )
}

@Composable
fun SetupMediaProviders(
    eventHandler: EventHandler,
    mediaDistributor: MediaDistributor,
    mediaHandler: MediaHandler,
    mediaSelector: MediaSelector,
    mediaImageRenderer: MediaImageRenderer = GlideMediaImageRenderer,
    content: @Composable () -> Unit
): Unit {
    val context = LocalContext.current
    CompositionLocalProvider(
    LocalEventHandler provides eventHandler,
    LocalMediaDistributor provides mediaDistributor,
    LocalMediaHandler provides mediaHandler,
    LocalMediaSelector provides mediaSelector,
    LocalMediaImageRenderer provides mediaImageRenderer,
        LocalMediaPrefetcher provides sketchMediaPrefetcher(context),
        LocalViewerRuntime provides (context.applicationContext as ComponentOwner).component,
        content = content
    )
}
