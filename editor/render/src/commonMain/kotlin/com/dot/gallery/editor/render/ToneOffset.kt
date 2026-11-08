package com.dot.gallery.editor.render

/**
 * The `color.basic` tonal operation, shared exactly by the Kotlin reference
 * ([BasicColorEffect]), the C kernel and the runtime shader. Given the
 * post-exposure luminance it returns the signed offset that is added
 * equally to R, G and B — channel differences and alpha are preserved and
 * no global clamp is applied, so signed/HDR scene colour passes through.
 *
 * Scalar stages run in the fixed order shadows → highlights → blacks →
 * whites; each is monotone on [0, 1] so their composition never inverts
 * tonal order. Blacks darkening stays nonnegative for nonnegative input —
 * the asymmetry with whites is deliberate.
 */
internal fun toneOffset(
    luminance: Float,
    highlights: Float,
    shadows: Float,
    whites: Float,
    blacks: Float,
): Float {
    val u = luminance.coerceIn(0f, 1f)
    var t = u
    if (shadows != 0f) {
        val inv = 1f - t
        t += shadows * t * inv * inv
    }
    if (highlights != 0f) {
        t += highlights * t * t * (1f - t)
    }
    if (blacks > 0f) {
        val inv = 1f - t
        t += 0.125f * blacks * inv * inv * inv
    } else if (blacks < 0f) {
        val inv = 1f - t
        t += blacks * t * inv * inv * inv
    }
    if (whites != 0f) {
        t += 0.125f * whites * t * t * t
    }
    return t - u
}
