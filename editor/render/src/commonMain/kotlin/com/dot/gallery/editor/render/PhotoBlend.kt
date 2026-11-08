package com.dot.gallery.editor.render

import com.dot.gallery.editor.core.model.PhotoBlendMode
import kotlin.math.sqrt

/**
 * Shared layer compositing for every CPU consumer (render-graph effect
 * and streaming exporter): straight-alpha linear RGBA in `dst`, one
 * source-over equation with a per-channel blend function.
 *
 * The effective source alpha is `csA * opacity` — additionally multiplied
 * by the backdrop alpha when [clipToBelow]. The equation is the W3C
 * compositing form:
 * `ao = as + ab*(1-as)`; `premul = as*(1-ab)*Cs + as*ab*Blend(Cb,Cs) +
 * ab*(1-as)*Cb`; `Co = premul/ao`.
 *
 * Only alpha terms are clamped to `[0, 1]` — linear RGB channels keep
 * their scene range. A fully transparent effective source returns early,
 * so a neutral transparent layer preserves the backdrop bits exactly.
 */
internal fun compositeLayerPixel(
    dst: FloatArray,
    index: Int,
    csR: Float,
    csG: Float,
    csB: Float,
    csA: Float,
    mode: PhotoBlendMode,
    opacity: Float,
    clipToBelow: Boolean
) {
    val ab = dst[index + ALPHA_OFFSET].coerceIn(0f, 1f)
    var effective = csA * opacity
    if (clipToBelow) effective *= ab
    val sourceAlpha = effective.coerceIn(0f, 1f)
    if (sourceAlpha <= 0f) return
    val outputAlpha = (sourceAlpha + ab * (1f - sourceAlpha)).coerceIn(0f, 1f)
    dst[index] = compositeChannel(mode, sourceAlpha, ab, outputAlpha, dst[index], csR)
    dst[index + 1] = compositeChannel(mode, sourceAlpha, ab, outputAlpha, dst[index + 1], csG)
    dst[index + 2] = compositeChannel(mode, sourceAlpha, ab, outputAlpha, dst[index + 2], csB)
    dst[index + ALPHA_OFFSET] = outputAlpha
}

private fun compositeChannel(
    mode: PhotoBlendMode,
    sourceAlpha: Float,
    backdropAlpha: Float,
    outputAlpha: Float,
    cb: Float,
    cs: Float
): Float =
    (sourceAlpha * (1f - backdropAlpha) * cs +
        sourceAlpha * backdropAlpha * blendChannel(mode, cb, cs) +
        backdropAlpha * (1f - sourceAlpha) * cb) / outputAlpha

/** The per-channel blend functions — alpha never enters them. */
internal fun blendChannel(mode: PhotoBlendMode, cb: Float, cs: Float): Float =
    when (mode) {
        PhotoBlendMode.Normal -> cs
        PhotoBlendMode.Multiply -> cb * cs
        PhotoBlendMode.Screen -> cb + cs - cb * cs
        PhotoBlendMode.Overlay ->
            if (cb <= .5f) 2f * cb * cs else 1f - 2f * (1f - cb) * (1f - cs)
        PhotoBlendMode.SoftLight ->
            if (cs <= .5f) {
                cb - (1f - 2f * cs) * cb * (1f - cb)
            } else {
                val d = if (cb <= .25f) ((16f * cb - 12f) * cb + 4f) * cb else sqrt(cb)
                cb + (2f * cs - 1f) * (d - cb)
            }
    }

private const val ALPHA_OFFSET = 3
