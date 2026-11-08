package com.dot.gallery.editor.ui

import com.dot.gallery.editor.core.model.PhotoSpatialEffects

/**
 * Uniform marshalling for the spatial stage of the runtime shader:
 * turns a [PhotoSpatialEffects] recipe into the scalar values the
 * generated shader consumes. Only the Details pass reaches the shader —
 * the lens blur's variable-radius 13-tap disk over the Details result
 * has no honest fixed-cost shader form, so [PhotoShaderRenderer] hosts
 * gate the shader off while its strength is non-zero and let the CPU
 * preview draw it exactly.
 */
internal class PhotoShaderSpatial(spatial: PhotoSpatialEffects) {

    val detailsActive = !spatial.details.isNeutral
    val detailTexture = spatial.details.texture
    val detailClarity = spatial.details.clarity
    val detailSharpening = spatial.details.sharpening
    val detailNoiseReduction = spatial.details.noiseReduction

    /** True while the recipe needs the CPU path — the gate hosts check. */
    val lensBlurActive = !spatial.lensBlur.isNeutral
}
