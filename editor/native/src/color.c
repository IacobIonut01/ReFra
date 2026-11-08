#include "refra_editor.h"

#include <math.h>

#define COMPONENTS_PER_PIXEL ((size_t)4)
#define CONTRAST_PIVOT 0.18f
#define LUMA_R 0.2126f
#define LUMA_G 0.7152f
#define LUMA_B 0.0722f

/* required = (height - 1) * stride + row_floats, with overflow checked. */
static int compute_required(uint32_t height, size_t stride, size_t row_floats,
                            size_t *required) {
    size_t rows = (size_t)height - 1;
    size_t span;
    if (stride != 0 && rows > SIZE_MAX / stride) return 0;
    span = rows * stride;
    if (row_floats > SIZE_MAX - span) return 0;
    *required = span + row_floats;
    return 1;
}

/* The tonal operation's luminance offset — the exact port of the Kotlin
 * reference (render/ToneOffset.kt): scalar stages in the fixed order
 * shadows, highlights, blacks, whites, each skipped at zero. */
static float tone_offset(float luminance, float highlights, float shadows,
                         float whites, float blacks) {
    float u = luminance < 0.0f ? 0.0f : (luminance > 1.0f ? 1.0f : luminance);
    float t = u;
    if (shadows != 0.0f) {
        float inv = 1.0f - t;
        t += shadows * t * inv * inv;
    }
    if (highlights != 0.0f) {
        t += highlights * t * t * (1.0f - t);
    }
    if (blacks > 0.0f) {
        float inv = 1.0f - t;
        t += 0.125f * blacks * inv * inv * inv;
    } else if (blacks < 0.0f) {
        float inv = 1.0f - t;
        t += blacks * t * inv * inv * inv;
    }
    if (whites != 0.0f) {
        t += 0.125f * whites * t * t * t;
    }
    return t - u;
}

static void compute_pixel(float r, float g, float b, float exposure_gain,
                          float contrast, float saturation,
                          float highlights, float shadows, float whites,
                          float blacks,
                          float *out_r, float *out_g, float *out_b) {
    /* Neutral stages perform no arithmetic: (r - 0.18) * 1 + 0.18 rounds
     * away very small values and -0, so a fully neutral adjustment must
     * leave the input bits untouched. */
    if (exposure_gain != 1.0f) {
        r *= exposure_gain;
        g *= exposure_gain;
        b *= exposure_gain;
    }
    if (highlights != 0.0f || shadows != 0.0f || whites != 0.0f ||
        blacks != 0.0f) {
        float y = LUMA_R * r + LUMA_G * g + LUMA_B * b;
        float delta = tone_offset(y, highlights, shadows, whites, blacks);
        r += delta;
        g += delta;
        b += delta;
    }
    if (contrast != 1.0f) {
        r = (r - CONTRAST_PIVOT) * contrast + CONTRAST_PIVOT;
        g = (g - CONTRAST_PIVOT) * contrast + CONTRAST_PIVOT;
        b = (b - CONTRAST_PIVOT) * contrast + CONTRAST_PIVOT;
    }
    if (saturation != 1.0f) {
        float luma = LUMA_R * r + LUMA_G * g + LUMA_B * b;
        r = luma + (r - luma) * saturation;
        g = luma + (g - luma) * saturation;
        b = luma + (b - luma) * saturation;
    }
    *out_r = r;
    *out_g = g;
    *out_b = b;
}

/* Validates source pixels (finite rgb, alpha in [0, 1]) and that the computed
 * result stays finite, without touching dst. Returns 0 when the pass fails. */
static int validate_and_probe(const float *src, uint32_t width, uint32_t height,
                              size_t src_stride, float exposure_gain,
                              float contrast, float saturation,
                              float highlights, float shadows, float whites,
                              float blacks) {
    uint32_t x, y;
    for (y = 0; y < height; y++) {
        const float *row = src + (size_t)y * src_stride;
        for (x = 0; x < width; x++) {
            const float *px = row + (size_t)x * COMPONENTS_PER_PIXEL;
            float r = px[0], g = px[1], b = px[2], a = px[3];
            float or, og, ob;
            if (!isfinite(r) || !isfinite(g) || !isfinite(b) ||
                !isfinite(a) || a < 0.0f || a > 1.0f) {
                return 0;
            }
            compute_pixel(r, g, b, exposure_gain, contrast, saturation,
                          highlights, shadows, whites, blacks,
                          &or, &og, &ob);
            if (!isfinite(or) || !isfinite(og) || !isfinite(ob)) {
                return 0;
            }
        }
    }
    return 1;
}

int refra_editor_color_rgba_f32(const float *src, size_t src_count, float *dst,
                                size_t dst_count, uint32_t width,
                                uint32_t height, size_t src_stride,
                                size_t dst_stride, float exposure_stops,
                                float contrast, float saturation,
                                float highlights, float shadows, float whites,
                                float blacks) {
    size_t row_floats, src_required, dst_required;
    float exposure_gain;
    uint32_t x, y;

    if (src == NULL || dst == NULL) return 1;
    if (width == 0 || height == 0) return 1;
    if (!isfinite(exposure_stops) || exposure_stops < -10.0f ||
        exposure_stops > 10.0f) return 1;
    if (!isfinite(contrast) || contrast < 0.0f || contrast > 4.0f) return 1;
    if (!isfinite(saturation) || saturation < 0.0f || saturation > 4.0f) return 1;
    if (!isfinite(highlights) || highlights < -1.0f || highlights > 1.0f)
        return 1;
    if (!isfinite(shadows) || shadows < -1.0f || shadows > 1.0f) return 1;
    if (!isfinite(whites) || whites < -1.0f || whites > 1.0f) return 1;
    if (!isfinite(blacks) || blacks < -1.0f || blacks > 1.0f) return 1;

    if ((size_t)width > SIZE_MAX / COMPONENTS_PER_PIXEL) return 1;
    row_floats = (size_t)width * COMPONENTS_PER_PIXEL;
    if (src_stride < row_floats || dst_stride < row_floats) return 1;

    if (!compute_required(height, src_stride, row_floats, &src_required)) return 1;
    if (!compute_required(height, dst_stride, row_floats, &dst_required)) return 1;
    if (src_required > src_count || dst_required > dst_count) return 1;

    /* Byte extents must be validated before any pointer arithmetic: a count
     * that fits size_t floats can still overflow the address space once
     * multiplied by sizeof(float), making src + extent UB. */
    if (src_required > SIZE_MAX / sizeof(float) ||
        dst_required > SIZE_MAX / sizeof(float)) return 1;
    {
        size_t src_bytes = src_required * sizeof(float);
        size_t dst_bytes = dst_required * sizeof(float);
        uintptr_t src_first = (uintptr_t)src;
        uintptr_t dst_first = (uintptr_t)dst;
        uintptr_t src_end, dst_end;
        if (src_bytes > UINTPTR_MAX - src_first ||
            dst_bytes > UINTPTR_MAX - dst_first) return 1;
        src_end = src_first + src_bytes;
        dst_end = dst_first + dst_bytes;

        if (src == dst) {
            if (src_stride != dst_stride) return 1;
        } else if (src_first < dst_end && dst_first < src_end) {
            /* Half-open extents overlap. */
            return 1;
        }
    }

    exposure_gain = powf(2.0f, exposure_stops);
    if (!isfinite(exposure_gain)) return 1;

    if (!validate_and_probe(src, width, height, src_stride, exposure_gain,
                            contrast, saturation, highlights, shadows, whites,
                            blacks)) {
        return 1;
    }

    for (y = 0; y < height; y++) {
        const float *srow = src + (size_t)y * src_stride;
        float *drow = dst + (size_t)y * dst_stride;
        for (x = 0; x < width; x++) {
            const float *spx = srow + (size_t)x * COMPONENTS_PER_PIXEL;
            float *dpx = drow + (size_t)x * COMPONENTS_PER_PIXEL;
            compute_pixel(spx[0], spx[1], spx[2], exposure_gain, contrast,
                          saturation, highlights, shadows, whites, blacks,
                          &dpx[0], &dpx[1], &dpx[2]);
            dpx[3] = spx[3];
        }
    }
    return 0;
}
