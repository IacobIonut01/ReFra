package com.dot.gallery.editor.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import com.dot.gallery.editor.core.model.PixelSize
import com.dot.gallery.editor.render.SrgbPixels
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The editor's glass tokens: the colours the blurred canvas and floating
 * panels are built from. Derived from the host [MaterialTheme] so light and
 * dark schemes each get their own values.
 */
@Immutable
class EditorGlassColors(
    val backdropScrim: Color,
    val canvasGlass: Color,
    val panelGlass: Color,
    val chromeTint: Color
)

@Composable
fun editorGlassColors(): EditorGlassColors {
    val scheme = MaterialTheme.colorScheme
    val dark = scheme.surface.luminance() < 0.5f
    return EditorGlassColors(
        backdropScrim = scheme.surface.copy(alpha = if (dark) 0.72f else 0.78f),
        canvasGlass = scheme.surfaceContainerLowest.copy(alpha = 0.85f),
        panelGlass = if (dark) {
            Color(0xFF0B0B0B).copy(alpha = 0.88f)
        } else {
            scheme.surfaceContainerLow.copy(alpha = 0.88f)
        },
        chromeTint = scheme.surfaceContainerHigh.copy(alpha = 0.60f)
    )
}

/**
 * The editor canvas backdrop: a heavily downsampled copy of the photo being
 * edited, cover-scaled to fill, under the backdrop scrim. With no pixels —
 * or with the host's blur preference off — the area is a plain surface.
 */
@Composable
fun EditorBlurredBackdrop(
    pixels: SrgbPixels?,
    modifier: Modifier = Modifier
) {
    val surface = MaterialTheme.colorScheme.surface
    val scrim = editorGlassColors().backdropScrim
    val blurEnabled = LocalEditorUiPreferences.current.blurEnabled
    val bitmap by produceState<ImageBitmap?>(null, pixels) {
        value = pixels?.let {
            withContext(Dispatchers.Default) {
                backdropThumbnail(it).toEditorImageBitmap()
            }
        }
    }
    Box(modifier = modifier.background(surface)) {
        val image = bitmap
        if (blurEnabled && image != null) {
            Image(
                bitmap = image,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                filterQuality = FilterQuality.High,
                modifier = Modifier.fillMaxSize()
            )
            Box(Modifier.fillMaxSize().background(scrim))
        }
    }
}

/**
 * A box-averaged copy of [pixels] no larger than [maxSide] on its long side
 * (the receiver itself when it already fits). A 24 px thumbnail upscaled with
 * bilinear filtering reads as a heavy blur at a fraction of the full frame's
 * cost.
 */
internal fun backdropThumbnail(pixels: SrgbPixels, maxSide: Int = 24): SrgbPixels {
    require(maxSide > 0) { "maxSide must be positive: $maxSide" }
    val srcW = pixels.size.width
    val srcH = pixels.size.height
    val longest = max(srcW, srcH)
    if (longest <= maxSide) return pixels
    val scale = maxSide.toFloat() / longest
    val outW = max(1, (srcW * scale).roundToInt())
    val outH = max(1, (srcH * scale).roundToInt())
    return srgbRegionSample(pixels, 0, 0, srcW, srcH, outW, outH)
}

/**
 * Box-averages the source rect `[srcX, srcX + srcW) × [srcY, srcY + srcH)`
 * of [pixels] down to `outW × outH` pixels. Each output pixel weights the
 * source pixels its rect overlaps by coverage area, so the result is a
 * true area average at any scale — downsample or upsample alike.
 */
internal fun srgbRegionSample(
    pixels: SrgbPixels,
    srcX: Int,
    srcY: Int,
    srcW: Int,
    srcH: Int,
    outW: Int,
    outH: Int
): SrgbPixels {
    require(outW > 0 && outH > 0) { "output must be positive: ${outW}x$outH" }
    require(srcX >= 0 && srcY >= 0 && srcW > 0 && srcH > 0 &&
        srcX + srcW <= pixels.size.width && srcY + srcH <= pixels.size.height
    ) { "source rect $srcX,$srcY ${srcW}x$srcH outside ${pixels.size}" }
    val src = pixels.copyPixels()
    val frameW = pixels.size.width
    val out = IntArray(outW * outH)
    for (oy in 0 until outH) {
        val y0 = srcY + oy * srcH / outH.toFloat()
        val y1 = srcY + (oy + 1) * srcH / outH.toFloat()
        val syStart = y0.toInt().coerceIn(srcY, srcY + srcH - 1)
        val syEnd = ceil(y1.toDouble()).toInt()
            .coerceIn(syStart + 1, srcY + srcH)
        for (ox in 0 until outW) {
            val x0 = srcX + ox * srcW / outW.toFloat()
            val x1 = srcX + (ox + 1) * srcW / outW.toFloat()
            val sxStart = x0.toInt().coerceIn(srcX, srcX + srcW - 1)
            val sxEnd = ceil(x1.toDouble()).toInt()
                .coerceIn(sxStart + 1, srcX + srcW)
            var alpha = 0f
            var red = 0f
            var green = 0f
            var blue = 0f
            var weight = 0f
            for (sy in syStart until syEnd) {
                val wy = min(y1, (sy + 1).toFloat()) - max(y0, sy.toFloat())
                if (wy <= 0f) continue
                val row = sy * frameW
                for (sx in sxStart until sxEnd) {
                    val wx = min(x1, (sx + 1).toFloat()) - max(x0, sx.toFloat())
                    if (wx <= 0f) continue
                    val w = wx * wy
                    val p = src[row + sx]
                    alpha += ((p ushr 24) and 0xFF) * w
                    red += ((p ushr 16) and 0xFF) * w
                    green += ((p ushr 8) and 0xFF) * w
                    blue += (p and 0xFF) * w
                    weight += w
                }
            }
            fun channel(sum: Float) =
                (sum / weight).roundToInt().coerceIn(0, 255)
            out[oy * outW + ox] =
                (channel(alpha) shl 24) or
                    (channel(red) shl 16) or
                    (channel(green) shl 8) or
                    channel(blue)
        }
    }
    return SrgbPixels(PixelSize(outW, outH), out)
}
