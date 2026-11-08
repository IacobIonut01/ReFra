package com.dot.gallery.editor.render

import com.dot.gallery.editor.core.graph.EffectParameters
import com.dot.gallery.editor.core.model.ColorAdjustments
import com.dot.gallery.editor.core.model.PhotoFinishing
import com.dot.gallery.editor.core.model.PhotoLook

/**
 * A look thumbnail straight off the shared processor: the source pixels
 * are decoded to linear, run through a neutral [PhotoFinishing]
 * carrying only [look], and re-encoded to sRGB — the UI's preset tiles
 * paint exactly what the recipe stage applies, never a fabricated
 * gradient. Suspends; per-row cancellation lives inside the conversion
 * and processing calls.
 */
suspend fun renderPhotoLookPreview(
    source: SrgbPixels,
    look: PhotoLook
): SrgbPixels {
    val linear = source.toLinearFrame()
    val out = processPhotoFinishing(
        linear,
        PhotoFinishing(look = look),
        originY = 0,
        fullSize = linear.size
    )
    return out.toSrgbPixels()
}

/**
 * A user preset's thumbnail: the source graded by [adjustments] through
 * the same CPU colour stage the canvas uses, then finished with [look].
 * A preset captures both stages, so the tile shows exactly what applying
 * it writes — never a generic look swatch.
 */
suspend fun renderPhotoPresetPreview(
    source: SrgbPixels,
    adjustments: ColorAdjustments,
    look: PhotoLook
): SrgbPixels {
    val graded = BasicColorEffect().render(
        listOf(source.toLinearFrame()),
        EffectParameters.Color(adjustments)
    )
    val out = processPhotoFinishing(
        graded,
        PhotoFinishing(look = look),
        originY = 0,
        fullSize = graded.size
    )
    return out.toSrgbPixels()
}
