#ifndef REFRA_EDITOR_H
#define REFRA_EDITOR_H

#include <stddef.h>
#include <stdint.h>

/* Versioned C ABI for the ReFra editor native kernels. Bump the version on any
 * signature or semantics change.
 *
 * v2: refra_editor_color_rgba_f32 appends highlights/shadows/whites/blacks —
 * the tonal stage between exposure and contrast. */
#define REFRA_EDITOR_ABI_VERSION 2

#ifdef __cplusplus
extern "C" {
#endif

/* Ordered colour adjustment over linear-light straight-alpha RGBA32F:
 * exposure (rgb *= 2^exposure_stops), then the tonal operation (a shared
 * luminance offset added equally to r, g and b), then contrast about the
 * 0.18 pivot (rgb = (rgb - 0.18) * contrast + 0.18), then Rec.709-luma
 * saturation (luma = 0.2126 r + 0.7152 g + 0.0722 b; rgb = luma +
 * (rgb - luma) * s). Alpha passes through unchanged; scene colour is never
 * clamped.
 *
 * The tonal offset t(u) - u with u = clamp(luminance, 0, 1) applies its
 * scalar stages in the fixed order shadows, highlights, blacks, whites:
 *   shadows: t += shadows * t * (1-t)^2
 *   highlights: t += highlights * t^2 * (1-t)
 *   blacks:   t += 0.125 * blacks * (1-t)^3      (blacks > 0)
 *             t += blacks * t * (1-t)^3          (blacks < 0)
 *   whites:   t += 0.125 * whites * t^3
 * A stage with a zero parameter performs no arithmetic. Whites can push
 * the result above 1; darkening blacks keep nonnegative luminance
 * nonnegative.
 *
 * src and dst are float buffers; counts and strides are measured in floats.
 * The touched extent of a buffer is (height - 1) * stride + width * 4 floats
 * and must fit the corresponding count. Strides may exceed width * 4 (padded
 * rows); padding is never read or written.
 *
 * Returns 0 on success, 1 on invalid input. Invalid input is rejected before
 * any destination write. In-place processing is allowed only when src == dst
 * with identical strides; any other overlap between the touched extents is
 * rejected.
 *
 * exposure_stops must be finite in [-10, 10]; contrast and saturation finite
 * in [0, 4]; highlights, shadows, whites and blacks finite in [-1, 1].
 * Source components must be finite with alpha in [0, 1]; a computation that
 * would produce a non-finite component fails without writing.
 */
int refra_editor_color_rgba_f32(
    const float *src,
    size_t src_count,
    float *dst,
    size_t dst_count,
    uint32_t width,
    uint32_t height,
    size_t src_stride,
    size_t dst_stride,
    float exposure_stops,
    float contrast,
    float saturation,
    float highlights,
    float shadows,
    float whites,
    float blacks);

#ifdef __cplusplus
}
#endif

#endif /* REFRA_EDITOR_H */
