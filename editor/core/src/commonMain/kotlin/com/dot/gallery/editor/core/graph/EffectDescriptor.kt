package com.dot.gallery.editor.core.graph

import com.dot.gallery.editor.core.time.EditorTime

/**
 * Stable identity and contract of an effect: [id] and [version] pin the
 * semantics, [inputCount] the required input arity, [haloPixels] the spatial
 * context a tiled backend must fetch, and [temporalRadius] how far in sequence
 * time the effect can look before and after the frame being rendered.
 *
 * Backends match on exact id + version; unknown or mismatched effects are
 * rejected, never silently skipped.
 */
data class EffectDescriptor(
    val id: String,
    val version: Int,
    val inputCount: Int,
    val haloPixels: Int = 0,
    val temporalRadius: EditorTime = EditorTime.Zero
) {
    init {
        require(id.isNotBlank()) { "effect id must be non-blank" }
        require(version > 0) { "effect version must be positive: $version" }
        require(inputCount >= 0) { "inputCount must be non-negative: $inputCount" }
        require(haloPixels >= 0) { "haloPixels must be non-negative: $haloPixels" }
        require(temporalRadius.ticks >= 0L) {
            "temporalRadius must be non-negative: ${temporalRadius.ticks}"
        }
    }

    companion object {
        /** Reads a media source through the render pipeline's source port. */
        val SourceImage = EffectDescriptor(id = "source.image", version = 1, inputCount = 0)

        /**
         * Ordered exposure, tonal highlights/shadows/whites/blacks, contrast
         * and saturation on straight RGBA. v2 appends the four tonal
         * parameters — a v2 recipe with neutral tones renders v1-identical
         * pixels, but the widened contract is versioned honestly.
         */
        val ColorBasic = EffectDescriptor(id = "color.basic", version = 2, inputCount = 1)

        /**
         * Pixel-exact photo geometry: normalized crop, quarter turns, flips.
         * v2 adds the continuous output-space warp (rotation, perspective,
         * scale) sampled bilinearly — a v2 recipe with a neutral warp
         * renders v1-identical pixels.
         */
        val GeometryPhoto = EffectDescriptor(id = "geometry.photo", version = 2, inputCount = 1)

        /**
         * Places one image layer onto the base output canvas: fit-inside
         * scale plus the layer's translation/scale/rotation/flips, sampled
         * bilinearly in linear light with the layer mask multiplying
         * sampled alpha. Out-of-domain points are transparent. v2 scales
         * the transform's translation — stored in full base-output
         * pixels — into the render's canvas resolution.
         */
        val PhotoLayerPlacement =
            EffectDescriptor(id = "photo.layer.place", version = 2, inputCount = 1)

        /**
         * Composites a placed layer over the backdrop below it: source-over
         * alpha composition with the layer's blend mode on linear RGB,
         * layer opacity and optional clip-to-below.
         */
        val PhotoComposite =
            EffectDescriptor(id = "photo.composite", version = 1, inputCount = 2)

        /**
         * The spatial stage over the composited output: the Details 3x3
         * local-contrast pass and the masked lens blur in linear light,
         * alpha preserved. The 17 px halo covers the full-strength blur
         * disk plus the Details pass's one-pixel neighbourhood, so a
         * combined banded render stays identical to a full-frame pass.
         */
        val PhotoSpatial =
            EffectDescriptor(
                id = "photo.spatial", version = 1, inputCount = 1,
                haloPixels = 17
            )

        /**
         * The finishing stage over the composited output: white balance,
         * tone curves (display-encoded), vignette and grain in linear light, alpha
         * preserved. Parameters carry the recipe plus the frame's
         * absolute origin and the full output size so vignette/grain
         * stay continuous across banded renders.
         */
        val PhotoFinishing =
            EffectDescriptor(id = "photo.finishing", version = 1, inputCount = 1)

        /**
         * The retouch stage over the finished output: colour tone, posterize,
         * edges, markup, background removal and the border. Its halo depends
         * on the recipe, so banded callers expand per band.
         */
        val PhotoRetouch =
            EffectDescriptor(id = "photo.retouch", version = 1, inputCount = 1)
    }
}
