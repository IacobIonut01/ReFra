/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.feature.viewer.insight

import android.content.ComponentCallbacks2
import android.content.Context
import android.content.res.Configuration
import android.graphics.Bitmap
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext
import com.dot.gallery.core.insight.CodeFormat
import com.dot.gallery.core.insight.CodeScanNative
import com.dot.gallery.core.insight.FramePool
import com.dot.gallery.core.insight.ImageAnalyzer
import com.dot.gallery.core.insight.Insight
import com.dot.gallery.core.insight.InsightKey
import com.dot.gallery.core.insight.ZxingCodeAnalyzer
import com.dot.gallery.core.logging.printDebug
import com.dot.gallery.core.logging.printError
import com.dot.gallery.core.logging.printWarn
import com.dot.gallery.core.ui.settings.rememberPreference
import com.dot.gallery.domain.model.Media
import com.dot.gallery.domain.settings.CODE_SCAN_MODE_ON_TAP
import com.dot.gallery.domain.settings.SettingsKeys
import com.dot.gallery.domain.util.getUri
import com.dot.gallery.domain.util.isCloud
import com.dot.gallery.domain.util.isImage
import com.dot.gallery.domain.util.isJp2
import com.dot.gallery.domain.util.isPsd
import com.dot.gallery.domain.util.isTiff
import com.dot.gallery.feature.viewer.components.media.HEIF_PLATFORM_PREVIEW_EXTRA
import com.dot.gallery.feature.viewer.components.media.mediaPreviewOptions
import com.dot.gallery.feature.viewer.components.media.mediaPreviewVersion
import com.github.panpf.sketch.asBitmapOrNull
import com.github.panpf.sketch.cache.CachePolicy
import com.github.panpf.sketch.request.ImageRequest
import com.github.panpf.sketch.sketch
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.ensureActive
import kotlin.coroutines.coroutineContext

private const val TAG = "insight.codes"

/** Long side of the larger copy decoded for the thorough pass. */
private const val DETAIL_SIDE = FramePool.DEFAULT_MAX_SIDE

/** The insight key for [media]: its id plus the same version the viewer's preview cache uses. */
fun insightKey(media: Media): InsightKey = InsightKey(media.id, mediaPreviewVersion(media))

/**
 * Whether the viewer should look for codes in [media]. Videos, animated images and formats the
 * preview pipeline cannot hand over as a bitmap are skipped.
 */
fun insightSupported(media: Media): Boolean =
    media.isImage && !(media.isTiff || media.isPsd || media.isJp2)

/**
 * Reads photos for the viewer's analyzers.
 *
 * The quick pass uses the low-res preview from Sketch's memory cache, the bitmap the viewer is
 * already showing, so it usually costs no decode at all. The thorough pass decodes a larger copy
 * with every cache disabled (nothing is written to disk, which matters for vault media), reads it
 * once and recycles it. Cloud media never triggers a download: both passes use its preview.
 */
class ViewerInsightEngine(
    context: Context,
    private val analyzers: List<ImageAnalyzer>,
) : InsightEngine<Media> {

    private val appContext = context.applicationContext
    private val pool = FramePool()

    override suspend fun analyze(target: Media, thorough: Boolean): List<Insight> {
        if (analyzers.isEmpty()) return emptyList()
        val useDetail = thorough && !target.isCloud
        val bitmap = (if (useDetail) decodeDetail(target) else loadPreview(target)) ?: return emptyList()
        try {
            coroutineContext.ensureActive()
            val start = System.nanoTime()
            val frame = pool.fill(bitmap) ?: return emptyList()
            val results = ArrayList<Insight>(0)
            for (analyzer in analyzers) {
                coroutineContext.ensureActive()
                results += analyzer.analyze(frame, thorough)
            }
            printDebug(
                TAG,
                "scan ${if (thorough) "thorough" else "quick"} ${bitmap.width}x${bitmap.height} " +
                    "-> ${frame.width}x${frame.height} config=${bitmap.config} found=${results.size} " +
                    "in ${(System.nanoTime() - start) / 1_000_000} ms",
            )
            return results
        } finally {
            if (useDetail) bitmap.recycle()
        }
    }

    override fun release() {
        pool.release()
    }

    private suspend fun loadPreview(media: Media): Bitmap? = execute("Preview for code scan failed") {
        ImageRequest(appContext, media.getUri().toString()) {
            mediaPreviewOptions(media)
        }
    }

    private suspend fun decodeDetail(media: Media): Bitmap? = execute("Detail decode for code scan failed") {
        ImageRequest(appContext, media.getUri().toString()) {
            mediaPreviewOptions(media, DETAIL_SIDE, DETAIL_SIDE)
            // The thorough pass wants real pixels, not the platform HEIF thumbnail.
            setExtra(key = HEIF_PLATFORM_PREVIEW_EXTRA, value = false)
            memoryCachePolicy(CachePolicy.DISABLED)
            resultCachePolicy(CachePolicy.DISABLED)
        }
    }

    private suspend inline fun execute(failure: String, request: () -> ImageRequest): Bitmap? =
        try {
            appContext.sketch.execute(request()).image?.asBitmapOrNull()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            printWarn(TAG, failure, ctx = mapOf("error" to e.toString()))
            null
        }
}

/** The viewer's insight controller, or null when code scanning is off. */
val LocalInsightController = staticCompositionLocalOf<InsightController<Media>?> { null }

/** Whether scanning waits for the Scan button rather than running when a photo settles. */
val LocalInsightOnTapMode = staticCompositionLocalOf { false }

/**
 * Creates the controller for one viewer screen while code scanning is enabled. With the setting
 * off this returns null and nothing (not even the native library) is loaded.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@Composable
fun rememberViewerInsightController(): InsightController<Media>? {
    val enabled by rememberPreference(SettingsKeys.codeScanEnabled)
    val formatGroups by rememberPreference(SettingsKeys.codeScanFormats)
    val context = LocalContext.current
    if (!enabled) return null
    val codeAnalyzer = remember { ZxingCodeAnalyzer() }
    LaunchedEffect(formatGroups) {
        codeAnalyzer.formatMask = CodeFormat.maskForGroups(formatGroups)
    }
    val controller = remember(context) {
        val dispatcher: CoroutineDispatcher = Dispatchers.Default.limitedParallelism(1)
        InsightController(
            engine = ViewerInsightEngine(context, listOf(codeAnalyzer)),
            dispatcher = dispatcher,
            onError = { printError(TAG, "Code scan failed", it) },
        )
    }
    DisposableEffect(controller) {
        val callbacks = object : ComponentCallbacks2 {
            override fun onTrimMemory(level: Int) {
                @Suppress("DEPRECATION")
                if (level >= ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW) controller.trimMemory()
            }

            override fun onConfigurationChanged(newConfig: Configuration) = Unit

            @Deprecated("Deprecated in Java")
            override fun onLowMemory() = controller.trimMemory()
        }
        context.applicationContext.registerComponentCallbacks(callbacks)
        onDispose {
            context.applicationContext.unregisterComponentCallbacks(callbacks)
            controller.dispose()
        }
    }
    return controller
}

/** True when the reader is usable on this device (the stub library reports false). */
fun codeScanAvailable(): Boolean = CodeScanNative.isAvailable

/** Whether the mode setting asks for scanning only on request. */
@Composable
fun rememberCodeScanOnTap(): Boolean {
    val mode by rememberPreference(SettingsKeys.codeScanMode)
    return mode == CODE_SCAN_MODE_ON_TAP
}
