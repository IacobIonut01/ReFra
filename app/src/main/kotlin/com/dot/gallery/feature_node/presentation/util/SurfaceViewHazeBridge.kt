package com.dot.gallery.feature_node.presentation.util

import android.graphics.Bitmap
import android.graphics.Rect
import android.graphics.RenderEffect
import android.graphics.Shader
import android.os.Handler
import android.os.HandlerThread
import android.view.PixelCopy
import android.view.SurfaceView
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.IntSize
import kotlinx.coroutines.delay
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Finds the first [SurfaceView] descendant in a view hierarchy.
 */
private fun View.findSurfaceView(): SurfaceView? {
    if (this is SurfaceView) return this
    if (this is ViewGroup) {
        for (i in 0 until childCount) {
            val found = getChildAt(i).findSurfaceView()
            if (found != null) return found
        }
    }
    return null
}

/**
 * Captures a [SurfaceView]'s content via [PixelCopy] into a tiny bitmap (~48 px wide)
 * at high frequency (~50 ms). The bitmap is so small (~7 KB) that PixelCopy is near-free.
 *
 * @param view       View (or parent) whose first SurfaceView descendant is captured.
 * @param enabled    Toggle capture on/off.
 * @param captureWidth Target width in pixels. Height is derived from aspect ratio.
 * @param intervalMs Milliseconds between capture attempts.
 */
@Composable
fun rememberSurfaceCapture(
    view: View?,
    enabled: Boolean = true,
    captureWidth: Int = 48,
    intervalMs: Long = 50L
): State<ImageBitmap?> {
    val state = remember { mutableStateOf<ImageBitmap?>(null) }

    val pixelCopyThread = remember {
        HandlerThread("PixelCopyThread").apply { start() }
    }
    val pixelCopyHandler = remember(pixelCopyThread) {
        Handler(pixelCopyThread.looper)
    }
    DisposableEffect(Unit) {
        onDispose { pixelCopyThread.quitSafely() }
    }

    LaunchedEffect(view, enabled) {
        if (view == null || !enabled) {
            state.value = null
            return@LaunchedEffect
        }

        val surfaceView = view.findSurfaceView() ?: return@LaunchedEffect
        val capturing = AtomicBoolean(false)
        var reusableBitmap: Bitmap? = null

        while (true) {
            if (!capturing.get()) {
                val w = surfaceView.width
                val h = surfaceView.height
                if (w > 0 && h > 0) {
                    val destW = captureWidth
                    val destH = (captureWidth * h.toFloat() / w).toInt().coerceAtLeast(1)

                    val dest = reusableBitmap?.takeIf { it.width == destW && it.height == destH }
                        ?: Bitmap.createBitmap(destW, destH, Bitmap.Config.ARGB_8888).also {
                            reusableBitmap = it
                        }

                    capturing.set(true)
                    try {
                        PixelCopy.request(
                            surfaceView,
                            Rect(0, 0, w, h),
                            dest,
                            { result ->
                                if (result == PixelCopy.SUCCESS) {
                                    state.value = dest.asImageBitmap()
                                }
                                capturing.set(false)
                            },
                            pixelCopyHandler
                        )
                    } catch (_: Exception) {
                        capturing.set(false)
                    }
                }
            }
            delay(intervalMs)
        }
    }

    return state
}

/**
 * Renders a captured [ImageBitmap] (from [rememberSurfaceCapture]) as a blurred background.
 *
 * The tiny bitmap is stretched to fill and then smoothed by a GPU [RenderEffect] blur shader,
 * producing a real-time frosted-glass effect without overlaying the live SurfaceView.
 *
 * @param bitmap     The captured surface bitmap (typically 48 px wide).
 * @param blurRadius Blur radius in pixels for the [RenderEffect] shader.
 * @param modifier   Modifier applied to the blur container.
 */
@Composable
fun SurfaceBlurBackground(
    bitmap: ImageBitmap?,
    modifier: Modifier = Modifier,
    blurRadius: Float = 28f
) {
    if (bitmap == null) return
    Box(
        modifier = modifier
            .fillMaxSize()
            .graphicsLayer {
                renderEffect = RenderEffect
                    .createBlurEffect(blurRadius, blurRadius, Shader.TileMode.CLAMP)
                    .asComposeRenderEffect()
            }
            .drawBehind {
                drawImage(
                    image = bitmap,
                    dstSize = IntSize(size.width.toInt(), size.height.toInt())
                )
            }
    )
}
