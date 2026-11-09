/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.feature.viewer.components.media

import android.os.Build
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.ScreenRotationAlt
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.dot.gallery.cloud.core.CloudTrace
import com.dot.gallery.cloud.core.CloudUri
import com.dot.gallery.cloud.image.CloudSubsamplingMode
import com.dot.gallery.cloud.image.resolveCloudSubsamplingMode
import com.dot.gallery.cloud.image.shouldLoadCloudOriginal
import com.dot.gallery.core.Settings
import com.dot.gallery.core.decoder.EncryptedRegionDecoder
import com.dot.gallery.core.decoder.FullImageRegionDecoder
import com.dot.gallery.core.decoder.HeifDebug
import com.dot.gallery.core.decoder.HeifRegionDecoder
import com.dot.gallery.core.decoder.JxlRegionDecoder
import com.dot.gallery.core.decoder.NativeRawDecoder
import com.dot.gallery.core.decoder.RawDevelopStore
import com.dot.gallery.core.decoder.RawRegionDecoder
import com.dot.gallery.core.decoder.format.ImageFormatSniffer
import com.dot.gallery.core.decoder.format.TiffImageDecoder
import com.dot.gallery.core.decoder.isAnimatedAvif
import com.dot.gallery.core.decoder.isAnimatedWebp
import com.dot.gallery.core.ml.CutoutHelper
import com.dot.gallery.core.presentation.components.util.LocalBatteryStatus
import com.dot.gallery.core.presentation.components.util.ProvideBatteryStatus
import com.dot.gallery.core.util.HdrCapabilities
import com.dot.gallery.domain.model.Media
import com.dot.gallery.domain.settings.LONG_PRESS_ACTION_CUTOUT
import com.dot.gallery.domain.settings.LONG_PRESS_ACTION_ROTATE
import com.dot.gallery.domain.util.asSubsamplingImage
import com.dot.gallery.domain.util.getUri
import com.dot.gallery.feature.viewer.LocalMediaViewerVisualPolicy
import com.dot.gallery.feature.viewer.LocalViewerRuntime
import com.dot.gallery.feature.viewer.generated.resources.Res
import com.dot.gallery.feature.viewer.generated.resources.cutout_init_failed
import com.dot.gallery.feature.viewer.generated.resources.cutout_no_object
import com.dot.gallery.feature.viewer.generated.resources.cutout_refining
import com.dot.gallery.feature.viewer.generated.resources.media_load_failed_tap_to_retry
import com.dot.gallery.feature.viewer.generated.resources.rotate
import org.jetbrains.compose.resources.stringResource
import com.github.panpf.sketch.AsyncImage
import com.github.panpf.sketch.PainterState
import com.github.panpf.sketch.rememberAsyncImagePainter
import com.github.panpf.sketch.rememberAsyncImageState
import com.github.panpf.sketch.request.ComposableImageRequest
import com.github.panpf.sketch.resize.Precision
import com.github.panpf.zoomimage.ZoomImage
import com.github.panpf.zoomimage.compose.subsampling.ComposeTileImage
import com.github.panpf.zoomimage.compose.subsampling.SubsamplingState
import com.github.panpf.zoomimage.compose.zoom.Transform
import com.github.panpf.zoomimage.compose.zoom.ZoomableState
import com.github.panpf.zoomimage.compose.zoom.mouseZoom
import com.github.panpf.zoomimage.compose.zoom.zoomable
import com.github.panpf.zoomimage.rememberSketchZoomState
import com.github.panpf.zoomimage.subsampling.SubsamplingImage
import com.github.panpf.zoomimage.util.IntSizeCompat
import com.github.panpf.zoomimage.util.isNotEmpty
import com.github.panpf.zoomimage.zoom.ContentScaleCompat
import com.github.panpf.zoomimage.zoom.ScalesCalculator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.cancellation.CancellationException
import kotlin.math.roundToInt
import kotlin.time.Duration.Companion.milliseconds
import com.dot.gallery.core.util.or
import com.dot.gallery.core.presentation.components.shape
import com.dot.gallery.core.presentation.util.add
import com.dot.gallery.core.presentation.util.clear
import com.dot.gallery.core.presentation.util.remove
import com.dot.gallery.data.data_source.KeychainHolder
import com.dot.gallery.core.presentation.components.util.swipe
import com.dot.gallery.domain.util.isApng
import com.dot.gallery.domain.util.isCloud
import com.dot.gallery.domain.util.isEncrypted
import com.dot.gallery.domain.util.isGif
import com.dot.gallery.domain.util.isHeif
import com.dot.gallery.domain.util.isJp2
import com.dot.gallery.domain.util.isJxl
import com.dot.gallery.domain.util.isPsd
import com.dot.gallery.domain.util.isSvg
import com.dot.gallery.domain.util.isTiff
import com.dot.gallery.domain.util.isVideo
import com.dot.gallery.core.ui.util.rememberedDerivedState
import com.dot.gallery.domain.util.isRaw
import com.dot.gallery.core.util.rememberFeedbackManager
import com.dot.gallery.domain.model.idLessKey
import com.dot.gallery.domain.util.isAvif


// Extended max zoom: allow zooming in until each source pixel is shown at this many screen pixels,
// i.e. up to 5000% of the image's native (1:1) resolution. Double-tap zoom is unaffected — it still
// toggles between the fit scale and the default dynamic mediumScale (see ExtendedZoomScalesCalculator).
private const val EXTENDED_MAX_NATIVE_SCALE = 50f

// Frame-animated raster formats (GIF/animated WebP/APNG) can't be tiled/subsampled, so their base
// painter is decoded at native resolution for crisp zoom — clamped to this long-edge to bound the
// per-frame bitmap memory on pathologically large files.
private const val ANIMATED_MAX_DIM = 4096

/**
 * Keeps zoomimage's default dynamic minScale/mediumScale (so double-tap behaves normally) but raises
 * the pinch ceiling (maxScale) to [maxNativeMultiple] times the image's native 1:1 resolution.
 *
 * At scale == contentOriginSize / contentSize the content is displayed at its native pixel density,
 * so multiplying that by [maxNativeMultiple] yields "N× native". When the native size is unknown
 * (no subsampling), the painter is already native, so 1.0 is used as the native scale.
 */
private class ExtendedZoomScalesCalculator(
    private val maxNativeMultiple: Float,
) : ScalesCalculator {

    private val base = ScalesCalculator.Dynamic

    @Suppress("DEPRECATION")
    override fun calculate(
        containerSize: IntSizeCompat,
        contentSize: IntSizeCompat,
        contentOriginSize: IntSizeCompat,
        contentScale: ContentScaleCompat,
        minScale: Float,
        initialScale: Float,
    ): ScalesCalculator.Result {
        val baseResult = base.calculate(
            containerSize = containerSize,
            contentSize = contentSize,
            contentOriginSize = contentOriginSize,
            contentScale = contentScale,
            minScale = minScale,
            initialScale = initialScale,
        )
        val nativeScale = if (contentOriginSize.isNotEmpty() && contentSize.isNotEmpty()) {
            maxOf(
                contentOriginSize.width / contentSize.width.toFloat(),
                contentOriginSize.height / contentSize.height.toFloat(),
            )
        } else {
            1f
        }
        val extendedMax = maxOf(
            baseResult.maxScale,
            baseResult.mediumScale,
            nativeScale * maxNativeMultiple,
        )
        return ScalesCalculator.Result(
            minScale = baseResult.minScale,
            mediumScale = baseResult.mediumScale,
            maxScale = extendedMax,
        )
    }
}

@Composable
fun <T : Media> BlurredMediaBackground(
    media: T,
    uiEnabled: Boolean,
    // Gesture-driven fade for the dismiss drag — applied instantly (no tween) so the
    // backdrop dissolves as the card drops. Multiplies the internal animated alpha.
    gestureAlpha: Float = 1f,
) {
    ProvideBatteryStatus {
        val allowBlur = LocalMediaViewerVisualPolicy.current.allowBlur
        val animationsEnabled = LocalMediaViewerVisualPolicy.current.animationsEnabled
        val isPowerSavingMode = LocalBatteryStatus.current.isPowerSavingMode
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && allowBlur && !isPowerSavingMode) {
            // Derived once from the preview the grid already prefetched, blurred on the CPU and
            // down to a few dozen pixels: the backdrop is one stretched textured quad, not a
            // fullscreen `.blur(100.dp)` RenderEffect pass per frame. That is what lets it stay
            // on screen while the shared-element open transition re-measures this box every
            // frame — the pass it replaces used to be skipped for the whole transition, which
            // made the blur fade in only after the photo was already there.
            val backdrop = rememberMediaBackdrop(media)
            // Already available on the first composition (the common case): start at full alpha
            // so the backdrop arrives together with the media, no fade. A backdrop that lands a
            // frame or two late — a cold preview, or cloud media still being fetched — fades in.
            val backdropAlpha = remember(media.id) {
                Animatable(if (backdrop != null) BACKDROP_ALPHA else 0f)
            }
            LaunchedEffect(uiEnabled, backdrop) {
                val target = if (uiEnabled) BACKDROP_ALPHA else 0f
                // "Animate media items" off — the backdrop fades in/out instantly.
                if (animationsEnabled) {
                    backdropAlpha.animateTo(target, tween(BACKDROP_FADE_MILLIS))
                } else {
                    backdropAlpha.snapTo(target)
                }
            }
            if (backdrop != null) {
                Image(
                    bitmap = backdrop,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    filterQuality = FilterQuality.Low,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            alpha = backdropAlpha.value * gestureAlpha
                        },
                )
            }
        }
    }
}

// JXL is exempt from the "animated stays smooth" rule: unlike the frame-animated rasters
// (GIF/APNG/animated WebP, which BitmapRegionDecoder genuinely cannot tile), JXL still
// subsamples through JxlRegionDecoder — which self-rejects animated codestreams — and an
// animated painter animates under the canvas-drawn pixel-perfect path just like under
// ZoomImage, because the painter owns its frames, not the renderer.
fun resolveUsePixelPerfect(
    disableSmoothing: Boolean,
    isJxl: Boolean,
    isAnimated: Boolean,
    isAnimatedRaster: Boolean,
): Boolean = disableSmoothing && !isAnimatedRaster && (isJxl || !isAnimated)

/**
 * A still AVIF and an AVIF sequence share `image/avif`. Only `image/avis` or a sniffed
 * `avis` brand is a sequence, and the platform animates those on API 31+. APNG and JXL
 * stay animated on every API, matching the painters that already play them.
 */
fun viewerMarksAnimated(
    isApng: Boolean,
    isJxl: Boolean,
    isAvisMime: Boolean,
    sniffedAnimatedAvif: Boolean,
    isHeifSequenceMime: Boolean,
    apiAtLeast31: Boolean,
): Boolean = isApng || isJxl ||
    ((isAvisMime || sniffedAnimatedAvif || isHeifSequenceMime) && apiAtLeast31)

/**
 * Still HEIC/HEIF and still AVIF share one region decoder. A sequence must stay on the
 * base painter: tiling it would freeze a high-resolution layer over the animation.
 */
fun viewerUsesHeifTiles(isHeif: Boolean, isAvif: Boolean, isAnimated: Boolean): Boolean =
    (isHeif || isAvif) && !isAnimated

@Stable
@Composable
fun <T : Media> ZoomablePagerImage(
    modifier: Modifier = Modifier,
    media: T,
    rotationDisabled: Boolean,
    onImageRotated: (newRotation: Int) -> Unit,
    onItemClick: (Offset) -> Unit,
    onImmediateTap: (Offset) -> Boolean = { false },
    onSwipeDown: () -> Unit,
    swipeToDismissEnabled: Boolean = true,
    onSubsamplingLoadingChange: (Boolean) -> Unit = {},
    onZoomTransformChange: (Transform) -> Unit = {},
    onLoadFailed: () -> Unit = {},
    // Fires once the full-resolution image (not the low-res preview) is on screen, so callers
    // can hold back competing decodes such as the HDR gain-map probe until then.
    onFullImageLoaded: () -> Unit = {},
    onCutoutStateChanged: (Boolean) -> Unit = {},
    onCutoutController: (CutoutController?) -> Unit = {},
    isSelected: Boolean = true,
    uiVisible: Boolean = true,
    // When false (e.g. during a slideshow) both the long-press cut-out gesture and the
    // background subject-suggestion detection are disabled.
    cutoutEnabled: Boolean = true,
    cutoutState: CutoutState = rememberCutoutState(),
) {
    val feedbackManager = rememberFeedbackManager()
    // Keyed by id (not the whole media object) so a same-id reload after an in-place overwrite
    // does NOT wipe an applied visual rotation: the persisted rotation must be held on-screen
    // until the freshly-encoded (baked-in) bytes are loaded (see the seamless-swap effect below).
    var isRotating by rememberSaveable(media.id) { mutableStateOf(false) }
    var currentRotation by rememberSaveable(media.id) { mutableIntStateOf(0) }
    val animationsEnabled = LocalMediaViewerVisualPolicy.current.animationsEnabled
    val rotationAnimation by animateFloatAsState(
        targetValue = if (isRotating) 90f else 0f,
        animationSpec = if (animationsEnabled) spring() else snap(),
        label = "rotationAnimation"
    )
    val zoomState = rememberSketchZoomState()
    val currentOnZoomTransformChange by rememberUpdatedState(onZoomTransformChange)
    val currentOnImmediateTap by rememberUpdatedState(onImmediateTap)
    LaunchedEffect(zoomState.zoomable, isSelected) {
        if (isSelected) {
            snapshotFlow { zoomState.zoomable.userTransform }.collect {
                currentOnZoomTransformChange(it)
            }
        }
    }
    // Raise the maximum (pinch) zoom to 5000% of native while leaving double-tap on the normal
    // dynamic mediumScale. See ExtendedZoomScalesCalculator.
    LaunchedEffect(zoomState) {
        zoomState.zoomable.setScalesCalculator(ExtendedZoomScalesCalculator(EXTENDED_MAX_NATIVE_SCALE))
    }
    val scope = rememberCoroutineScope()

    val context = LocalContext.current
    val viewerRuntime = LocalViewerRuntime.current
        ?: error("LocalViewerRuntime is not provided - wrap the tree in SetupMediaProviders")
    val cutoutNoObjectText = stringResource(Res.string.cutout_no_object)
    val cutoutInitFailedText = stringResource(Res.string.cutout_init_failed)
    val mediaUri = remember(media) {
        media.getUri().toString()
    }
    val isCloudMedia = remember(media) { media.isCloud }
    // A content-version token folded into the Sketch cache key so the memory and
    // disk result caches are tied to the current file bytes. After editing or
    // overwriting an image its URI is unchanged, so without this the viewer kept
    // serving the stale original from cache while the Glide grid (keyed on
    // media.toString) had already refreshed.
    val mediaVersion = remember(media) { "${media.timestamp}:${media.size}" }
    val isEncrypted = remember(media) {
        media.isEncrypted
    }
    val isJxl = remember(media) { media.isJxl }
    // Cutout/subject-suggestion decode via BitmapFactory, which cannot decode TIFF/PSD/JP2 at all —
    // so the feature can never work for them and running it just wastes work (and, for large 16-bit
    // TIFFs, greedily reads metadata → OOM). Disable it for those formats.
    val cutoutSupported = remember(media) { !(media.isTiff || media.isPsd || media.isJp2) }
    // image/avif covers stills and sequences. Sniff the ftyp brand the way animated WebP
    // does; cloud and encrypted files stay on the mime type, because sniffing would mean
    // a download or a decrypt. image/avis is a sequence without a sniff.
    var sniffedAnimatedAvif by remember(media) { mutableStateOf(false) }
    LaunchedEffect(media) {
        if (!media.isAvif || media.mimeType == "image/avis" || media.isEncrypted || media.isCloud) {
            sniffedAnimatedAvif = false
            return@LaunchedEffect
        }
        sniffedAnimatedAvif = withContext(Dispatchers.IO) {
            runCatching {
                context.contentResolver.openInputStream(media.getUri())?.use { ins ->
                    val head = ByteArray(256)
                    val n = ins.read(head)
                    n > 0 && isAnimatedAvif(head.copyOf(n))
                } ?: false
            }.getOrDefault(false)
        }
    }
    val isAnimated = remember(media, sniffedAnimatedAvif) {
        viewerMarksAnimated(
            isApng = media.isApng,
            isJxl = media.isJxl,
            isAvisMime = media.mimeType == "image/avis",
            sniffedAnimatedAvif = sniffedAnimatedAvif,
            isHeifSequenceMime = media.isHeif && media.mimeType.endsWith("-sequence"),
            apiAtLeast31 = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S,
        )
    }
    // Frame-animated raster formats that CANNOT be tiled/subsampled: GIF, animated WebP, APNG.
    // BitmapRegionDecoder can't decode GIF, and tiling a moving image would freeze a hi-res layer
    // over the animation. The fix is a full-resolution (capped) base decode per frame.
    // Animated WebP shares mime image/webp with static WebP, so it needs a cheap header sniff;
    // cloud/encrypted webp default to false (sniffing would require download/decrypt).
    var isAnimatedWebpState by remember(media) { mutableStateOf(false) }
    LaunchedEffect(media) {
        if (media.mimeType == "image/webp" && !media.isEncrypted && !media.isCloud) {
            isAnimatedWebpState = withContext(Dispatchers.IO) {
                runCatching {
                    context.contentResolver.openInputStream(media.getUri())?.use { ins ->
                        val head = ByteArray(64)
                        val n = ins.read(head)
                        n > 0 && isAnimatedWebp(head.copyOf(n))
                    } ?: false
                }.getOrDefault(false)
            }
        }
    }
    val isAnimatedRaster = remember(media, isAnimatedWebpState) {
        media.isGif || media.isApng || isAnimatedWebpState
    }
    // Pixel-perfect (nearest-neighbor) rendering for pixel art: when enabled we draw the image
    // with FilterQuality.None so zooming shows crisp square pixels instead of a bilinear-smoothed
    // blur. ZoomImage scales via a GPU graphicsLayer (always linearly filtered), so the setting is
    // honored through a dedicated canvas-transform draw path (see PixelPerfectZoomImage) rather than
    // ZoomImage. Animated images keep the default smooth path — except JXL (see
    // resolveUsePixelPerfect), whose blanket "animated" flag used to hide it forever.
    val disableSmoothing by Settings.Misc.rememberDisableSmoothing()
    val usePixelPerfect = resolveUsePixelPerfect(
        disableSmoothing = disableSmoothing,
        isJxl = isJxl,
        isAnimated = isAnimated,
        isAnimatedRaster = isAnimatedRaster,
    )
    val filterQuality = if (disableSmoothing) FilterQuality.None else DrawScope.DefaultFilterQuality
    // MediaStore sometimes mislabels a standard JPEG/PNG/WebP/GIF/BMP as RAW/TIFF/PSD/JP2,
    // so media.isRaw/isTiff/… are true and a custom region decoder would be attached that can't
    // decode the real bytes — leaving zoom stuck on the low-res base ("magnified thumbnail"). Only
    // for those already-special formats, sniff the header once (cached per media) on IO; if the
    // bytes are a natively-decodable image, force the generic platform BitmapRegionDecoder path.
    // Plain images never reach this probe (zero extra I/O per viewer page).
    val mightBeSpecial = remember(media) {
        media.isTiff || media.isRaw || media.isPsd || media.isJp2 || media.isSvg
    }
    var forceStandard by remember(media) { mutableStateOf(false) }
    LaunchedEffect(media, mightBeSpecial) {
        if (mightBeSpecial && !media.isEncrypted && !media.isCloud) {
            forceStandard = withContext(Dispatchers.IO) {
                runCatching {
                    context.contentResolver.openInputStream(media.getUri())?.use { ins ->
                        val head = ByteArray(32)
                        val n = ins.read(head)
                        n > 0 && ImageFormatSniffer.isNativelyDecodable(head.copyOf(n))
                    } ?: false
                }.getOrDefault(false)
            }
        }
    }
    // Region decoder for formats Android's BitmapRegionDecoder can't subsample (PSD/JP2/TIFF/SVG).
    // Without this they only show the screen-resolution base painter and look blurry when zoomed.
    // RAW develop recipe (per-image, session-scoped). Reading it here makes the region decoder
    // rebuild whenever the Develop sheet tunes the recipe, live re-rendering the zoomed image.
    val rawParams = if (media.isRaw) RawDevelopStore.paramsFor(media.id) else null
    val customRegionFactory = remember(media, rawParams, forceStandard) {
        when {
            // Mislabeled standard image: keep the platform BitmapRegionDecoder (generic branch).
            forceStandard -> null
            media.isPsd -> FullImageRegionDecoder.forPsd()
            media.isJp2 -> FullImageRegionDecoder.forJp2()
            // The TIFF region decoder full-decodes the file into one bitmap for cropping; on a large
            // 16-bit TIFF (the file IS the raster) that reads 150+ MB and OOMs. Only attach it for
            // files that fit the heap; oversized TIFFs keep the downsampled base painter (no zoom
            // subsampling) instead of crashing.
            media.isTiff && media.size <= TiffImageDecoder.MAX_BYTES.toLong() ->
                FullImageRegionDecoder.forTiff()
            media.isSvg -> FullImageRegionDecoder.forSvg()
            // RAW: demosaic via LibRaw for crisp zoom; falls back to embedded preview when the
            // native lib is unavailable (ABI without prebuilt libs).
            media.isRaw && NativeRawDecoder.isAvailable && rawParams != null ->
                RawRegionDecoder.forRaw(rawParams)
            else -> null
        }
    }

    var imageRequestAttempt by rememberSaveable(media.id) { mutableIntStateOf(0) }

    // Fast low-res preview painter, shown until full image loads
    val previewImageState = rememberAsyncImageState()
    val previewPainter = rememberAsyncImagePainter(
        request = ComposableImageRequest(mediaUri) {
            crossfade(false)
            if (isCloudMedia) {
                resize(width = MEDIA_PREVIEW_SIZE, height = MEDIA_PREVIEW_SIZE, precision = Precision.LESS_PIXELS)
                setExtra(key = "mediaVersion", value = mediaVersion)
                if (isEncrypted) {
                    setExtra(key = "mediaKeyPreviewEnc", value = media.idLessKey)
                }
            } else {
                // Same request as the tap prefetch and the opening animation, so all three share
                // one cached decode — and HEIC takes the platform-thumbnail path (#1385).
                mediaPreviewOptions(media)
            }
            // Only a retry needs a distinct key; keeping attempt 0 out preserves the shared entry.
            if (imageRequestAttempt > 0) {
                setExtra(key = "viewerLoadAttempt", value = imageRequestAttempt)
            }
        },
        state = previewImageState,
        contentScale = ContentScale.Fit,
        filterQuality = filterQuality
    )

    // Full-res painter with state tracking
    val fullImageState = rememberAsyncImageState()
    val fullPainter = rememberAsyncImagePainter(
        request = ComposableImageRequest(mediaUri) {
            if (isEncrypted || isAnimated || isAnimatedRaster) {
                transitionFactory(viewerCrossfadeTransition(animationsEnabled))
            }
            // Animated raster (GIF/animated WebP/APNG) can't be subsampled, so decode the base frame
            // at native resolution (capped) instead of the default layout/screen size — otherwise a
            // hi-res GIF looks blurry when zoomed. LESS_PIXELS precision never upscales, so
            // small GIFs still decode at native size and only oversized ones are downsampled.
            if (isAnimatedRaster) {
                resize(width = ANIMATED_MAX_DIM, height = ANIMATED_MAX_DIM, precision = Precision.LESS_PIXELS)
            }
            if (!isCloudMedia) {
                setExtra("realMimeType", media.mimeType)
            }
            setExtra(key = "mediaVersion", value = mediaVersion)
            setExtra(key = "viewerLoadAttempt", value = imageRequestAttempt)
            if (isEncrypted) {
                setExtra(key = "mediaKeyPreviewEnc", value = media.idLessKey)
            }
        },
        state = fullImageState,
        contentScale = ContentScale.Fit,
        filterQuality = filterQuality
    )

    val isFullImageLoaded by rememberedDerivedState(media) {
        fullImageState.painterState is PainterState.Success
    }
    val currentOnFullImageLoaded by rememberUpdatedState(onFullImageLoaded)
    LaunchedEffect(isFullImageLoaded) {
        if (isFullImageLoaded) currentOnFullImageLoaded()
    }
    val imageLoadFailed by rememberedDerivedState(
        previewImageState.painterState,
        fullImageState.painterState,
    ) {
        previewImageState.painterState is PainterState.Error &&
            fullImageState.painterState is PainterState.Error
    }
    LaunchedEffect(imageLoadFailed, imageRequestAttempt) {
        if (imageLoadFailed) onLoadFailed()
    }

    // ── Seamless swap after an in-place rotation overwrite ──
    // When the underlying bytes change (the overwrite persisted a rotated re-encode), the file
    // content version changes while a visual rotation is still applied. Hold that rotation until
    // the new (baked-in) full image finishes loading, then snap the transform back to 0 in one
    // step so the page shows the correct orientation without an un-rotated flash or a double
    // rotation. While waiting we keep the full painter on-screen (it retains the previous bytes,
    // shown correctly rotated) instead of switching to the reloading preview.
    var pendingRotatedReload by remember(media.id) { mutableStateOf(false) }
    var lastContentVersion by remember(media.id) { mutableStateOf(mediaVersion) }
    LaunchedEffect(mediaVersion) {
        if (mediaVersion != lastContentVersion) {
            lastContentVersion = mediaVersion
            if (currentRotation % 360 != 0) pendingRotatedReload = true
        }
    }
    LaunchedEffect(pendingRotatedReload) {
        if (!pendingRotatedReload) return@LaunchedEffect
        // Let the request key change propagate (Loading) before watching for the new Success.
        delay(50.milliseconds)
        withTimeoutOrNull(8000.milliseconds) {
            snapshotFlow { fullImageState.painterState }.first { it is PainterState.Success }
        }
        zoomState.zoomable.rotate(0)
        currentRotation = 0
        onImageRotated(0)
        pendingRotatedReload = false
    }

    val activePainter = remember(isFullImageLoaded, pendingRotatedReload) {
        if (isFullImageLoaded || pendingRotatedReload) fullPainter else previewPainter
    }

    val cloudSubsamplingMode = remember(media, customRegionFactory, isAnimated, isAnimatedRaster) {
        resolveCloudSubsamplingMode(
            isJxl = isJxl,
            hasCustomRegionDecoder = customRegionFactory != null,
            isHeif = media.isHeif,
            isSpecialFormat = mightBeSpecial,
            isAnimated = isAnimated,
            isAnimatedRaster = isAnimatedRaster,
            isAvif = media.isAvif,
        )
    }

    // Subsampling is set up the same way for both the smooth and pixel-perfect paths so that
    // high-resolution images retain native detail when zoomed. The difference is only in how the
    // tiles are drawn: PixelPerfectZoomImage draws them via a canvas transform with
    // FilterQuality.None (nearest-neighbor) instead of the smoothed default.
    LaunchedEffect(media) {
        HeifDebug.d(
            "viewer media='${media.label}' mime='${media.mimeType}' isHeif=${media.isHeif} " +
                "isAvif=${media.isAvif} isJxl=$isJxl isAnimated=$isAnimated " +
                "isAnimatedRaster=$isAnimatedRaster " +
                "customRegion=${customRegionFactory != null} " +
                "encrypted=$isEncrypted cloud=$isCloudMedia usePixelPerfect=$usePixelPerfect " +
                "isFullImageLoaded=$isFullImageLoaded"
        )
    }
    // A local image/avif file can still be an `avis` sequence. The sniff starts false, so
    // the still path attaches tiles, then flips. Drop those tiles once the brand is known.
    // image/avis never enters that path, and cloud/encrypted files are not sniffed.
    LaunchedEffect(media.id, sniffedAnimatedAvif) {
        if (sniffedAnimatedAvif) {
            zoomState.setSubsamplingImage(null as SubsamplingImage?)
        }
    }
    if (isEncrypted) {
        val keychainHolder = remember { KeychainHolder(context) }
        LaunchedEffect(media, isFullImageLoaded, zoomState.subsampling) {
            zoomState.setSubsamplingImage(media.asSubsamplingImage(context))
        }
        LaunchedEffect(zoomState.subsampling, media) {
            zoomState.subsampling.setRegionDecoders(
                listOf(
                    EncryptedRegionDecoder.Factory(
                        keychainHolder
                    )
                )
            )
        }
    } else if (isCloudMedia) {
        LaunchedEffect(media, isSelected, cloudSubsamplingMode) {
            // Upgrade the selected cloud photo from its lightweight preview to the original;
            // neighbouring pager pages stay preview-only until selected.
            if (!shouldLoadCloudOriginal(isSelected, cloudSubsamplingMode)) return@LaunchedEffect
            val parsed = CloudUri.parse(media.getUri().toString()) ?: return@LaunchedEffect
            // remoteId may contain slashes (SMB/NFS/WebDAV paths like "Photos/IMG.jpg"); pathSegments
            // .first() would truncate it to the folder and request the directory as the original.
            val remoteId = parsed.remoteId
            val configId = parsed.configId
            // Signal that the full-size original is being fetched for subsampling so the UI can show
            // a subtle loading indicator. try/finally guarantees the flag is cleared on success,
            // failure, or cancellation (e.g. swiping to another page mid-download).
            onSubsamplingLoadingChange(true)
            try {
                val cloudSource = viewerRuntime.cloudImageSource(
                    parsed.providerType,
                    remoteId,
                    configId
                )
                zoomState.setSubsamplingImage(SubsamplingImage(imageSource = cloudSource))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                CloudTrace.w("Unable to load cloud original '$remoteId'", e)
            } finally {
                onSubsamplingLoadingChange(false)
            }
        }
        LaunchedEffect(zoomState.subsampling, media, cloudSubsamplingMode) {
            when (cloudSubsamplingMode) {
                CloudSubsamplingMode.JXL -> zoomState.subsampling.setRegionDecoders(
                    listOf(JxlRegionDecoder.Factory())
                )
                CloudSubsamplingMode.CUSTOM -> zoomState.subsampling.setRegionDecoders(
                    listOfNotNull(customRegionFactory)
                )
                CloudSubsamplingMode.HEIF -> zoomState.subsampling.setRegionDecoders(
                    listOf(HeifRegionDecoder.Factory(hdrDisplay = HdrCapabilities.isHdrDisplay(context)))
                )
                CloudSubsamplingMode.NONE,
                CloudSubsamplingMode.PLATFORM -> zoomState.subsampling.setRegionDecoders(emptyList())
            }
        }
    } else if (isJxl) {
        // Android's BitmapRegionDecoder can't decode JXL, so enable subsampling backed by a
        // JxlCoder region decoder for high-resolution zoom. Animated JXL is rejected by the
        // decoder and falls back to the animated base painter.
        LaunchedEffect(media, isFullImageLoaded, zoomState.subsampling) {
            zoomState.setSubsamplingImage(media.asSubsamplingImage(context))
        }
        LaunchedEffect(zoomState.subsampling, media) {
            zoomState.subsampling.setRegionDecoders(listOf(JxlRegionDecoder.Factory()))
        }
    } else if (customRegionFactory != null) {
        // PSD/JP2/TIFF/SVG: no native BitmapRegionDecoder support, so subsample via a
        // full-decode-then-crop (PSD/JP2/TIFF) or high-res render (SVG) region decoder.
        LaunchedEffect(media, isFullImageLoaded, zoomState.subsampling) {
            zoomState.setSubsamplingImage(media.asSubsamplingImage(context))
        }
        LaunchedEffect(zoomState.subsampling, media) {
            zoomState.subsampling.setRegionDecoders(listOf(customRegionFactory))
        }
    } else if (viewerUsesHeifTiles(media.isHeif, media.isAvif, isAnimated)) {
        // HEIC/HEIF and still AVIF share one container. Hardware BitmapRegionDecoder, then
        // libheif full-decode-then-crop, keeps zoom at the file's pixel size. The generic
        // branch has no software fallback and fails on HDR, 10-bit, and grid HEICs.
        // Sequences stay on the base painter.
        LaunchedEffect(media, isFullImageLoaded, zoomState.subsampling) {
            HeifDebug.d("HEIF branch: setSubsamplingImage for '${media.label}' (isFullImageLoaded=$isFullImageLoaded)")
            zoomState.setSubsamplingImage(media.asSubsamplingImage(context))
        }
        LaunchedEffect(zoomState.subsampling, media) {
            HeifDebug.d("HEIF branch: setRegionDecoders(HeifRegionDecoder) for '${media.label}'")
            zoomState.subsampling.setRegionDecoders(
                listOf(HeifRegionDecoder.Factory(hdrDisplay = HdrCapabilities.isHdrDisplay(context)))
            )
        }
    } else if (!isAnimated && !isAnimatedRaster) {
        // Animated raster (GIF/animated WebP/APNG) is intentionally excluded: BitmapRegionDecoder
        // can't tile a moving image, so subsampling would do nothing but waste work. Their crisp
        // zoom comes from the native-resolution base decode above.
        LaunchedEffect(media, isFullImageLoaded, zoomState.subsampling) {
            zoomState.setSubsamplingImage(media.asSubsamplingImage(context))
        }
    }

    // Track the swipe-to-dismiss drag offset so the cutout overlay can translate in lockstep with
    // the image (the swipe modifier only offsets the image itself).
    var swipeOffsetY by remember { mutableIntStateOf(0) }
    val imageModifier = Modifier
        .fillMaxSize()
        .pointerInput(Unit) {
            awaitEachGesture {
                val down = awaitFirstDown(
                    requireUnconsumed = false,
                    pass = PointerEventPass.Initial,
                )
                var isTap = true
                var tracked = down
                while (tracked.pressed) {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    if (event.changes.count { it.pressed } > 1) isTap = false
                    val next = event.changes.firstOrNull { it.id == down.id }
                    if (next == null) {
                        isTap = false
                        break
                    }
                    tracked = next
                    if ((tracked.position - down.position).getDistance() > viewConfiguration.touchSlop) {
                        isTap = false
                    }
                }
                if (isTap &&
                    tracked.uptimeMillis - down.uptimeMillis < viewConfiguration.longPressTimeoutMillis &&
                    currentOnImmediateTap(tracked.position)
                ) {
                    tracked.consume()
                }
            }
        }
        .swipe(
            enabled = swipeToDismissEnabled,
            onSwipeDown = onSwipeDown,
            onOffset = { swipeOffsetY = it.y },
        )
        .graphicsLayer {
            rotationZ = if (isRotating) rotationAnimation else 0f
        }
        .then(modifier)

    // Rotate the image 90° (visual step; MediaViewScreen persists it via the rotate chip).
    val rotateImage: () -> Unit = {
        if (!rotationDisabled) {
            scope.launch {
                isRotating = true
                feedbackManager.vibrate()
                currentRotation += 90
                onImageRotated(currentRotation)
                // The delay covers the 90° rotate tween; with animations off there is
                // nothing to wait for.
                if (animationsEnabled) delay(350)
                zoomState.zoomable.rotate(currentRotation)
                isRotating = false
            }
        }
    }

    // --- Subject cutout (on-device MobileSAM) ---
    val modelManager = remember { viewerRuntime.modelManager }
    // User-chosen long-press action. When it is cutout, Rotate is offered as an on-screen pill.
    val longPressAction = Settings.Misc.rememberLongPressAction()

    // Pulsing glow for the cutout contour. The infinite animation is only created while it's
    // actually needed (a cutout is active). An unconditional rememberInfiniteTransition would run a
    // continuous per-frame animation and recomposition on every composed pager page — including
    // neighbours that briefly compose during a fling — adding avoidable swipe jank.
    val needsGlow = cutoutState.isActive && animationsEnabled
    val glowRadius = if (needsGlow) {
        val infiniteTransition = rememberInfiniteTransition(label = "glowTransition")
        val animatedGlow by infiniteTransition.animateFloat(
            initialValue = 2f,
            targetValue = 6f,
            animationSpec = infiniteRepeatable(
                animation = tween(1200, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "glowRadius"
        )
        animatedGlow
    } else {
        2f
    }

    DisposableEffect(media) {
        onDispose {
            cutoutState.dismiss()
        }
    }

    BackHandler(enabled = cutoutState.isActive) {
        cutoutState.dismiss()
    }

    LaunchedEffect(cutoutState.isActive, isSelected) {
        if (isSelected) {
            onCutoutStateChanged(cutoutState.isActive)
        }
    }


    // Start a cutout session seeded at the given screen-space point.
    val startCutoutAt: (Offset) -> Unit = { offset ->
        if (!cutoutState.isActive && !cutoutState.isProcessing) {
            scope.launch {
                cutoutState.isProcessing = true
                try {
                    feedbackManager.vibrate()
                    val contentPoint = zoomState.zoomable.touchPointToContentPoint(offset)

                    val session = CutoutHelper.CutoutSession(context, media, modelManager)
                    val initOk = session.initAndRunEncoder()

                    if (initOk) {
                        val contentSize = zoomState.zoomable.contentSize
                        val scaleX = if (contentSize.width > 0) session.widthOrig.toFloat() / contentSize.width.toFloat() else 1f
                        val scaleY = if (contentSize.height > 0) session.heightOrig.toFloat() / contentSize.height.toFloat() else 1f

                        val scaledX = contentPoint.x * scaleX
                        val scaledY = contentPoint.y * scaleY

                        val initialPoint = CutoutHelper.PromptPoint(x = scaledX, y = scaledY, isPositive = true)
                        val pointsList = listOf(initialPoint)
                        val result = session.runDecoder(pointsList)

                        if (result != null) {
                            // Only hoist session into state once we have a valid mask
                            cutoutState.initSession(session, pointsList)
                            feedbackManager.vibrate()
                            cutoutState.updateResult(result, null)
                        } else {
                            // No mask found — clean up without touching cutoutState
                            session.close()
                            Toast.makeText(context, cutoutNoObjectText, Toast.LENGTH_SHORT).show()
                        }
                    } else {
                        session.close()
                        Toast.makeText(context, cutoutInitFailedText, Toast.LENGTH_SHORT).show()
                    }
                } finally {
                    // Always reset — even on OOM, ONNX crash, or coroutine cancellation
                    cutoutState.isProcessing = false
                }
            }
        }
    }

    // Tap handler: while a cutout session is active, add/remove a prompt point; otherwise pass through.
    val onImageTap: (Offset) -> Unit = { offset ->
        val session = cutoutState.session
        if (session != null) {
            val displayRect = zoomState.zoomable.contentDisplayRect
            val isInsideImage = offset.x >= displayRect.left &&
                    offset.x <= displayRect.right &&
                    offset.y >= displayRect.top &&
                    offset.y <= displayRect.bottom

            if (isInsideImage && (cutoutState.activeTool == ZoomablePagerImagePointTool.ADD || cutoutState.activeTool == ZoomablePagerImagePointTool.REMOVE)) {
                val contentPoint = zoomState.zoomable.touchPointToContentPoint(offset)
                val contentSize = zoomState.zoomable.contentSize
                val scaleX = if (contentSize.width > 0) session.widthOrig.toFloat() / contentSize.width.toFloat() else 1f
                val scaleY = if (contentSize.height > 0) session.heightOrig.toFloat() / contentSize.height.toFloat() else 1f

                val scaledX = contentPoint.x * scaleX
                val scaledY = contentPoint.y * scaleY

                if (scaledX in 0f..session.widthOrig.toFloat() && scaledY in 0f..session.heightOrig.toFloat()) {
                    val newPoint = CutoutHelper.PromptPoint(
                        x = scaledX,
                        y = scaledY,
                        isPositive = cutoutState.activeTool == ZoomablePagerImagePointTool.ADD
                    )
                    val previousPoints = cutoutState.promptPoints
                    val updatedPoints = cutoutState.promptPoints + newPoint
                    cutoutState.pushPoints(updatedPoints)

                    scope.launch {
                        cutoutState.isProcessing = true
                        try {
                            val res = session.runDecoder(updatedPoints)
                            val newCache = cutoutState.result?.let { Pair(previousPoints, it) }
                            cutoutState.updateResult(res, newCache)
                        } finally {
                            cutoutState.isProcessing = false
                        }
                    }
                }
            }
        } else {
            // No active session: pass the tap to the viewer.
            onItemClick(offset)
        }
    }

    // Configurable long-press action (default: rotate). Disabled entirely when cut-out is off
    // (e.g. during a slideshow).
    val onImageLongPress: (Offset) -> Unit = { offset ->
        if (cutoutEnabled) {
            when (longPressAction) {
                // Cut-out can't run on TIFF/PSD/JP2 (BitmapFactory can't decode them); rotate instead.
                LONG_PRESS_ACTION_CUTOUT -> if (cutoutSupported) startCutoutAt(offset) else rotateImage()
                LONG_PRESS_ACTION_ROTATE -> rotateImage()
                else -> Unit
            }
        }
    }

    // Undo/redo: re-run the decoder for the target point set unless the result was cached.
    val navigateHistory: (Int) -> Unit = { delta ->
        val pts = cutoutState.navigateHistory(delta)
        if (pts != null) {
            scope.launch {
                cutoutState.isProcessing = true
                try {
                    val res = cutoutState.session?.runDecoder(pts.second)
                    val newCache = cutoutState.result?.let { Pair(pts.first, it) }
                    cutoutState.updateResult(res, newCache)
                } finally {
                    cutoutState.isProcessing = false
                }
            }
        }
    }

    // Run an export action (copy/share/save) on the current mask, then end the session.
    val runRefinedAction: (suspend (android.graphics.Bitmap) -> Unit) -> Unit = { action ->
        val bmp = cutoutState.result?.bitmap
        if (bmp != null) {
            scope.launch {
                action(bmp)
                cutoutState.dismiss()
            }
        }
    }

    // Controls surfaced in the MediaViewScreen bottom bar (replacing the quick actions) while active.
    val cutoutController = remember(cutoutState) {
        CutoutController(
            state = cutoutState,
            onToolChange = { cutoutState.activeTool = it },
            onReset = { cutoutState.clearPoints() },
            onUndo = { navigateHistory(-1) },
            onRedo = { navigateHistory(1) },
            onCopy = { runRefinedAction { CutoutHelper.copyToClipboard(context, it) } },
            onShare = { runRefinedAction { CutoutHelper.shareCutout(context, it) } },
            onSave = { runRefinedAction { CutoutHelper.saveToGallery(context, it) } },
            onClose = { cutoutState.dismiss() },
        )
    }

    // Publish the controller upward only for the selected page and only while active; clear on
    // deselect/dispose so the screen bottom bar swaps back to the quick actions.
    LaunchedEffect(cutoutState.isActive, isSelected) {
        onCutoutController(if (isSelected && cutoutState.isActive) cutoutController else null)
    }
    DisposableEffect(Unit) {
        onDispose { if (isSelected) onCutoutController(null) }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        if (usePixelPerfect) {
            // Pixel-art path: nearest-neighbor rendering with subsampling.
            PixelPerfectZoomImage(
                zoomable = zoomState.zoomable,
                subsampling = zoomState.subsampling,
                painter = activePainter,
                modifier = imageModifier,
                contentDescription = media.label,
                onTap = onImageTap,
                onLongPress = onImageLongPress,
            )
        } else {
            ZoomImage(
                zoomState = zoomState,
                painter = activePainter,
                modifier = imageModifier,
                onTap = onImageTap,
                onLongPress = onImageLongPress,
                alignment = Alignment.Center,
                contentDescription = media.label,
                scrollBar = null
            )
        }

        if (imageLoadFailed) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.92f)),
                contentAlignment = Alignment.Center,
            ) {
                MediaViewActionPill(
                    icon = Icons.Outlined.Refresh,
                    label = stringResource(Res.string.media_load_failed_tap_to_retry),
                    onClick = { imageRequestAttempt++ },
                )
            }
        }

        // Cutout mask + animated contour + prompt markers (controls live in the screen bottom bar).
        CutoutOverlay(
            state = cutoutState,
            zoomState = zoomState,
            glowRadius = glowRadius,
            translationY = swipeOffsetY.toFloat()
        )

        // Processing / refinement indicator
        if (cutoutState.isProcessing) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.3f))
                    .pointerInput(Unit) {
                        detectTapGestures(onTap = {})
                    },
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                    Text(
                        text = stringResource(Res.string.cutout_refining),
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White
                    )
                }
            }
        }

        val chromeVisible = isSelected && uiVisible && !cutoutState.isActive && !cutoutState.isProcessing

        if (longPressAction == LONG_PRESS_ACTION_CUTOUT) {
            // Long-press does cutout → offer Rotate as a pill.
            AnimatedVisibility(
                visible = chromeVisible && !rotationDisabled,
                enter = if (animationsEnabled) fadeIn() else EnterTransition.None,
                exit = if (animationsEnabled) fadeOut() else ExitTransition.None,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(bottom = 96.dp)
            ) {
                MediaViewActionPill(
                    icon = Icons.Outlined.ScreenRotationAlt,
                    label = stringResource(Res.string.rotate),
                    onClick = rotateImage
                )
            }
        }
    }
}

/**
 * Small pill button matching the media viewer's motion-photo / rotate chip style, used for viewer
 * actions such as retrying an image load or rotating when cutout owns the long-press gesture.
 */
@Composable
private fun MediaViewActionPill(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .clip(CircleShape)
            .background(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(0.85f),
                shape = CircleShape
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp)
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * A drop-in replacement for [com.github.panpf.zoomimage.ZoomImage] that renders with
 * nearest-neighbor filtering (no smoothing) so pixel art stays crisp when zoomed, **while still
 * using subsampling** to keep native detail on high-resolution images.
 *
 * Why a custom composable instead of `ZoomImage`: `ZoomImage` applies its zoom via a GPU
 * `graphicsLayer` and draws its subsampling tiles with a hardcoded, private paint. Neither can be
 * told to skip smoothing. Here we instead reproduce the same [ZoomableState.transform] directly on
 * the draw canvas — canvas transforms honor the per-draw [FilterQuality], exactly like the
 * library's own Android View implementation which draws tiles with a `Matrix` + `Paint`. We reuse
 * [ZoomableState] only for gesture handling and read [SubsamplingState]'s public tile snapshots to
 * draw them ourselves.
 *
 * Coordinate spaces (mirrors `ZoomImage`):
 * - the base [painter] is in *content* coordinates (its own pixel size); the transform maps
 *   content -> screen.
 * - subsampling tiles ([TileSnapshot.srcRect]) are in *origin* (full-resolution) coordinates, so
 *   they are additionally scaled by contentSize/contentOriginSize before the same transform.
 */
@Composable
private fun PixelPerfectZoomImage(
    zoomable: ZoomableState,
    subsampling: SubsamplingState,
    painter: Painter,
    modifier: Modifier,
    contentDescription: String?,
    onTap: (Offset) -> Unit,
    onLongPress: (Offset) -> Unit,
) {
    zoomable.setContentScale(ContentScale.Fit)
    zoomable.setAlignment(Alignment.Center)
    zoomable.setLayoutDirection(LocalLayoutDirection.current)
    val intrinsicSize = painter.intrinsicSize
    val contentSize = remember(intrinsicSize) {
        if (intrinsicSize.isSpecified) {
            IntSize(intrinsicSize.width.roundToInt(), intrinsicSize.height.roundToInt())
        } else {
            IntSize.Zero
        }
    }
    zoomable.setContentSize(contentSize)

    BoxWithConstraints(modifier = modifier.mouseZoom(zoomable)) {
        val density = LocalDensity.current
        val containerSize = remember(density, maxWidth, maxHeight) {
            IntSize(
                width = with(density) { maxWidth.toPx() }.roundToInt(),
                height = with(density) { maxHeight.toPx() }.roundToInt()
            )
        }
        zoomable.setContainerSize(containerSize)

        Box(
            Modifier
                .matchParentSize()
                .clipToBounds()
                .zoomable(
                    zoomable = zoomable,
                    userSetupContentSize = true,
                    onLongPress = { onLongPress(it) },
                    onTap = { onTap(it) }
                )
                .drawWithContent {
                    val transform = zoomable.transform
                    val drawSize = if (contentSize.width > 0 && contentSize.height > 0) {
                        Size(contentSize.width.toFloat(), contentSize.height.toFloat())
                    } else {
                        size
                    }
                    withTransform({
                        translate(transform.offsetX, transform.offsetY)
                        scale(
                            scaleX = transform.scaleX,
                            scaleY = transform.scaleY,
                            pivot = Offset(
                                x = transform.scaleOriginX * size.width,
                                y = transform.scaleOriginY * size.height
                            )
                        )
                        rotate(
                            degrees = transform.rotation,
                            pivot = Offset(
                                x = transform.rotationOriginX * size.width,
                                y = transform.rotationOriginY * size.height
                            )
                        )
                    }) {
                        // Base image (content coords). Visible until/where tiles cover it.
                        with(painter) {
                            draw(size = drawSize)
                        }
                        // Subsampling tiles (origin coords -> content coords), drawn crisp.
                        drawSubsamplingTiles(subsampling, zoomable)
                    }
                }
                .semantics {
                    if (contentDescription != null) {
                        this.contentDescription = contentDescription
                        this.role = Role.Image
                    }
                }
        )
    }
}

/**
 * Draws [SubsamplingState]'s foreground/background tiles with [FilterQuality.None]. Must be called
 * inside a [withTransform] block that already maps content coordinates to screen (see
 * [PixelPerfectZoomImage]); this only adds the origin->content scale that the library's
 * `firstScaleByContentSize` layer normally applies.
 */
private fun DrawScope.drawSubsamplingTiles(
    subsampling: SubsamplingState,
    zoomable: ZoomableState,
) {
    val foregroundTiles = subsampling.foregroundTiles
    if (foregroundTiles.isEmpty()) return
    val loadRect = subsampling.imageLoadRect
    if (loadRect.width <= 0 || loadRect.height <= 0) return
    val contentSize = zoomable.contentSize
    val originSize = zoomable.contentOriginSize
    if (contentSize.width <= 0 || contentSize.height <= 0) return
    if (originSize.width <= 0 || originSize.height <= 0) return

    val originToContentX = contentSize.width.toFloat() / originSize.width
    val originToContentY = contentSize.height.toFloat() / originSize.height

    fun drawTile(tile: com.github.panpf.zoomimage.subsampling.TileSnapshot) {
        val srcRect = tile.srcRect
        // Skip tiles outside the currently loaded area (matches the library's overlap check).
        if (srcRect.right <= loadRect.left || srcRect.left >= loadRect.right ||
            srcRect.bottom <= loadRect.top || srcRect.top >= loadRect.bottom
        ) return
        val tileImage = tile.tileImage
        if (tileImage == null || tileImage.isRecycled) return
        val bitmap = (tileImage as? ComposeTileImage)?.bitmap ?: return
        val dstLeft = (srcRect.left * originToContentX).roundToInt()
        val dstTop = (srcRect.top * originToContentY).roundToInt()
        val dstRight = (srcRect.right * originToContentX).roundToInt()
        val dstBottom = (srcRect.bottom * originToContentY).roundToInt()
        drawImage(
            image = bitmap,
            srcOffset = IntOffset.Zero,
            srcSize = IntSize(bitmap.width, bitmap.height),
            dstOffset = IntOffset(dstLeft, dstTop),
            dstSize = IntSize(dstRight - dstLeft, dstBottom - dstTop),
            alpha = tile.alpha / 255f,
            filterQuality = FilterQuality.None,
        )
    }

    // Background tiles first (lower sample sizes), then the sharp foreground tiles on top.
    subsampling.backgroundTiles.forEach { drawTile(it) }
    foregroundTiles.forEach { drawTile(it) }
}
