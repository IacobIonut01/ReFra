package com.dot.gallery.editor.core.model

/**
 * Details recipe in model units — the UI speaks -100..100 / 0..100:
 * [texture] lifts or softens fine detail, [clarity] lifts or softens
 * midtone micro-contrast, [sharpening] re-adds the unsharp band,
 * [noiseReduction] leans the pixel toward its 3x3 neighbourhood mean.
 * All four at zero is the neutral, bit-preserving recipe.
 */
data class PhotoDetails(
    val texture: Float = 0f,
    val clarity: Float = 0f,
    val sharpening: Float = 0f,
    val noiseReduction: Float = 0f
) {
    init {
        require(texture.isFinite() && texture in -1f..1f) {
            "details texture must be finite inside -1..1: $texture"
        }
        require(clarity.isFinite() && clarity in -1f..1f) {
            "details clarity must be finite inside -1..1: $clarity"
        }
        require(sharpening.isFinite() && sharpening in 0f..1f) {
            "details sharpening must be finite inside 0..1: $sharpening"
        }
        require(noiseReduction.isFinite() && noiseReduction in 0f..1f) {
            "details noiseReduction must be finite inside 0..1: $noiseReduction"
        }
    }

    val isNeutral: Boolean
        get() = texture == 0f && clarity == 0f && sharpening == 0f &&
            noiseReduction == 0f
}
