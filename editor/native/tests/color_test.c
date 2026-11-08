#include "refra_editor.h"

#include <float.h>
#include <math.h>
#include <stdio.h>
#include <string.h>

static int failures = 0;

#define CHECK(cond)                                                        \
    do {                                                                   \
        if (!(cond)) {                                                     \
            fprintf(stderr, "FAIL %s:%d: %s\n", __FILE__, __LINE__, #cond); \
            failures++;                                                    \
        }                                                                  \
    } while (0)

#define CHECK_CLOSE(actual, expected)                                       \
    do {                                                                   \
        float a_ = (actual), e_ = (expected);                              \
        if (!(fabsf(a_ - e_) <= 1e-6f * (fabsf(e_) + 1.0f))) {             \
            fprintf(stderr, "FAIL %s:%d: %s (got %f, want %f)\n", __FILE__, \
                    __LINE__, #actual, (double)a_, (double)e_);            \
            failures++;                                                    \
        }                                                                  \
    } while (0)

static int run(const float *src, size_t src_count, float *dst,
               size_t dst_count, uint32_t w, uint32_t h, size_t ss,
               size_t ds, float e, float c, float s) {
    return refra_editor_color_rgba_f32(src, src_count, dst, dst_count, w, h,
                                       ss, ds, e, c, s, 0.0f, 0.0f, 0.0f, 0.0f);
}

/* Neutral exposure/contrast/saturation with the v2 tonal parameters. */
static int run_tone(const float *src, size_t src_count, float *dst,
                    size_t dst_count, uint32_t w, uint32_t h, float hi,
                    float sh, float wh, float bl) {
    size_t stride = (size_t)w * 4;
    return refra_editor_color_rgba_f32(src, src_count, dst, dst_count, w, h,
                                       stride, stride, 0.0f, 1.0f, 1.0f,
                                       hi, sh, wh, bl);
}

/* One 1x1 pixel: 2 rows x 2 cols used by the multi-pixel tests below. */
static void test_identity(void) {
    float src[8] = {0.25f, 0.5f, 0.75f, 1.0f, 0.1f, 0.2f, 0.3f, 0.0f};
    float dst[8] = {0};
    CHECK(run(src, 8, dst, 8, 2, 1, 8, 8, 0.0f, 1.0f, 1.0f) == 0);
    CHECK_CLOSE(dst[0], 0.25f);
    CHECK_CLOSE(dst[5], 0.2f);
    CHECK_CLOSE(dst[7], 0.0f);
}

static void test_exposure_doubles(void) {
    float src[4] = {0.25f, 0.5f, 0.1f, 0.5f};
    float dst[4] = {0};
    CHECK(run(src, 4, dst, 4, 1, 1, 4, 4, 1.0f, 1.0f, 1.0f) == 0);
    CHECK_CLOSE(dst[0], 0.5f);
    CHECK_CLOSE(dst[1], 1.0f);
    CHECK_CLOSE(dst[2], 0.2f);
    CHECK_CLOSE(dst[3], 0.5f); /* alpha unchanged */
}

static void test_contrast_pivot(void) {
    /* The pivot value is a fixed point of the contrast curve. */
    float src[4] = {0.18f, 0.18f, 0.18f, 1.0f};
    float dst[4] = {0};
    CHECK(run(src, 4, dst, 4, 1, 1, 4, 4, 0.0f, 2.0f, 1.0f) == 0);
    CHECK_CLOSE(dst[0], 0.18f);
    /* (0.4 - 0.18) * 2 + 0.18 = 0.62 */
    src[0] = 0.4f;
    CHECK(run(src, 4, dst, 4, 1, 1, 4, 4, 0.0f, 2.0f, 1.0f) == 0);
    CHECK_CLOSE(dst[0], 0.62f);
}

static void test_saturation_luma(void) {
    float src[4] = {0.8f, 0.4f, 0.2f, 1.0f};
    float dst[4] = {0};
    float luma = 0.2126f * 0.8f + 0.7152f * 0.4f + 0.0722f * 0.2f;
    CHECK(run(src, 4, dst, 4, 1, 1, 4, 4, 0.0f, 1.0f, 0.0f) == 0);
    CHECK_CLOSE(dst[0], luma);
    CHECK_CLOSE(dst[1], luma);
    CHECK_CLOSE(dst[2], luma);
    CHECK_CLOSE(dst[3], 1.0f);
}

static void test_out_of_range_and_alpha_zero(void) {
    /* HDR, negative and alpha-0 inputs are legal; no clipping. */
    float src[8] = {2.0f, -0.25f, 1.5f, 0.0f, -1.0f, 3.0f, 0.05f, 0.3f};
    float dst[8] = {0};
    CHECK(run(src, 8, dst, 8, 2, 1, 8, 8, -1.0f, 1.0f, 1.0f) == 0);
    CHECK_CLOSE(dst[0], 1.0f);   /* exposure -1 halves */
    CHECK_CLOSE(dst[1], -0.125f);
    CHECK_CLOSE(dst[3], 0.0f);
    CHECK_CLOSE(dst[4], -0.5f);
}

static void test_padded_strides(void) {
    /* 2x1 pixels with a two-float pad between rows; padding must be untouched. */
    float src[20], dst[20];
    uint32_t i;
    for (i = 0; i < 20; i++) {
        src[i] = 0.0f;
        dst[i] = 12345.0f;
    }
    src[0] = 0.5f;
    src[1] = 0.5f;
    src[2] = 0.5f;
    src[3] = 1.0f;
    src[10] = 0.25f;
    src[11] = 0.25f;
    src[12] = 0.25f;
    src[13] = 1.0f;
    CHECK(run(src, 20, dst, 20, 2, 2, 10, 10, 1.0f, 1.0f, 1.0f) == 0);
    CHECK_CLOSE(dst[0], 1.0f);
    CHECK_CLOSE(dst[10], 0.5f);
    /* padding untouched */
    CHECK_CLOSE(dst[8], 12345.0f);
    CHECK_CLOSE(dst[9], 12345.0f);
    CHECK_CLOSE(dst[18], 12345.0f);
}

static void test_in_place(void) {
    float buf[8] = {0.25f, 0.5f, 0.75f, 1.0f, 0.1f, 0.2f, 0.3f, 0.5f};
    CHECK(run(buf, 8, buf, 8, 2, 1, 8, 8, 1.0f, 1.0f, 1.0f) == 0);
    CHECK_CLOSE(buf[0], 0.5f);
    CHECK_CLOSE(buf[4], 0.2f);
    CHECK_CLOSE(buf[7], 0.5f);
}

static void test_invalid_args(void) {
    float src[8] = {0.5f, 0.5f, 0.5f, 1.0f, 0.5f, 0.5f, 0.5f, 1.0f};
    float dst[8] = {0};
    CHECK(run(NULL, 8, dst, 8, 2, 1, 8, 8, 0, 1, 1) == 1);
    CHECK(run(src, 8, NULL, 8, 2, 1, 8, 8, 0, 1, 1) == 1);
    CHECK(run(src, 8, dst, 8, 0, 1, 8, 8, 0, 1, 1) == 1);
    CHECK(run(src, 8, dst, 8, 2, 0, 8, 8, 0, 1, 1) == 1);
    /* stride smaller than a row */
    CHECK(run(src, 8, dst, 8, 2, 1, 7, 8, 0, 1, 1) == 1);
    CHECK(run(src, 8, dst, 8, 2, 1, 8, 7, 0, 1, 1) == 1);
    /* counts too small for the extent */
    CHECK(run(src, 7, dst, 8, 2, 1, 8, 8, 0, 1, 1) == 1);
    CHECK(run(src, 8, dst, 7, 2, 1, 8, 8, 0, 1, 1) == 1);
    /* 2 rows of stride 8 need (2-1)*8 + 8 = 16 floats */
    CHECK(run(src, 8, dst, 8, 2, 2, 8, 8, 0, 1, 1) == 1);
    /* overflow: stride huge makes (h-1)*stride overflow */
    CHECK(run(src, 8, dst, 8, 1, 4, SIZE_MAX - 3, 8, 0, 1, 1) == 1);
    /* parameter ranges */
    CHECK(run(src, 8, dst, 8, 2, 1, 8, 8, 10.5f, 1, 1) == 1);
    CHECK(run(src, 8, dst, 8, 2, 1, 8, 8, -10.5f, 1, 1) == 1);
    CHECK(run(src, 8, dst, 8, 2, 1, 8, 8, 0, 4.5f, 1) == 1);
    CHECK(run(src, 8, dst, 8, 2, 1, 8, 8, 0, 1, -0.1f) == 1);
    CHECK(run(src, 8, dst, 8, 2, 1, 8, 8, NAN, 1, 1) == 1);
    CHECK(run(src, 8, dst, 8, 2, 1, 8, 8, 0, INFINITY, 1) == 1);
}

static void test_overlap(void) {
    float buf[16] = {0};
    /* src == dst with different stride is rejected. */
    CHECK(run(buf, 16, buf, 16, 1, 1, 4, 8, 0, 1, 1) == 1);
    /* partial overlap of the touched extents is rejected. */
    CHECK(run(buf, 16, buf + 4, 12, 2, 1, 8, 8, 0, 1, 1) == 1);
    /* disjoint buffers are fine. */
    {
        float src[4] = {0.5f, 0.5f, 0.5f, 1.0f};
        float dst[4] = {0};
        CHECK(run(src, 4, dst, 4, 1, 1, 4, 4, 0, 1, 1) == 0);
        CHECK_CLOSE(dst[0], 0.5f);
    }
}

static void test_neutral_bit_identity(void) {
    /* Neutral stages are bypassed: every bit survives, including -0. */
    float src[8] = {1e-10f, -1e-10f, 3e-10f, 0.0f,
                    -0.0f, 0.5f, 1e-30f, 0.75f};
    float dst[8];
    memset(dst, 0xAB, sizeof dst);
    CHECK(run(src, 8, dst, 8, 2, 1, 8, 8, 0.0f, 1.0f, 1.0f) == 0);
    CHECK(memcmp(src, dst, sizeof src) == 0);

    /* Pure +1 exposure doubles tiny values exactly — no pivot rounding. */
    CHECK(run(src, 8, dst, 8, 2, 1, 8, 8, 1.0f, 1.0f, 1.0f) == 0);
    CHECK(dst[0] == 2e-10f && dst[1] == -2e-10f && dst[2] == 6e-10f);
    CHECK(memcmp(&src[3], &dst[3], sizeof(float)) == 0); /* alpha untouched */
    /* -0 * 2 is still exactly -0 (sign preserved). */
    CHECK(dst[4] == 0.0f && signbit(dst[4]));

    /* Float.MAX_VALUE passes identity through and stays finite. */
    {
        float huge[4] = {FLT_MAX, FLT_MAX, FLT_MAX, 1.0f};
        float out[4] = {0};
        CHECK(run(huge, 4, out, 4, 1, 1, 4, 4, 0.0f, 1.0f, 1.0f) == 0);
        CHECK(out[0] == FLT_MAX && isfinite(out[0]));
    }
}

static void test_byte_extent_overflow(void) {
    /* A float extent that fits size_t can still overflow the byte address
     * space; it must be rejected before any pixel is read. height=2 with
     * stride = SIZE_MAX/2 + 1 gives required = stride + 4 floats, which is
     * > SIZE_MAX / sizeof(float) but <= SIZE_MAX. */
    float src[4] = {0.5f, 0.5f, 0.5f, 1.0f};
    float dst[4] = {9.0f, 9.0f, 9.0f, 9.0f};
    size_t huge_stride = SIZE_MAX / 2 + 1;
    CHECK(run(src, SIZE_MAX, dst, SIZE_MAX, 1, 2,
              huge_stride, huge_stride, 0, 1, 1) == 1);
    CHECK(dst[0] == 9.0f && dst[3] == 9.0f);
}

static void test_invalid_pixels_no_partial_write(void) {
    float src[8] = {0.5f, 0.5f, 0.5f, 1.0f, 0.5f, 0.5f, 0.5f, 1.0f};
    float dst[8] = {7.0f, 7.0f, 7.0f, 7.0f, 7.0f, 7.0f, 7.0f, 7.0f};
    const float clean_dst[8] = {7.0f, 7.0f, 7.0f, 7.0f, 7.0f, 7.0f, 7.0f, 7.0f};
    /* non-finite source component on the first pixel */
    src[1] = NAN;
    CHECK(run(src, 8, dst, 8, 2, 1, 8, 8, 0, 1, 1) == 1);
    CHECK(memcmp(dst, clean_dst, sizeof dst) == 0);
    src[1] = 0.5f;
    /* alpha outside [0, 1] on the SECOND pixel — a later-pixel failure */
    src[7] = 1.5f;
    CHECK(run(src, 8, dst, 8, 2, 1, 8, 8, 0, 1, 1) == 1);
    CHECK(memcmp(dst, clean_dst, sizeof dst) == 0);
    src[7] = -0.01f;
    CHECK(run(src, 8, dst, 8, 2, 1, 8, 8, 0, 1, 1) == 1);
    CHECK(memcmp(dst, clean_dst, sizeof dst) == 0);
    src[7] = 1.0f;
    /* exposure +10 makes the LATER pixel compute non-finite: the probe pass
     * must reject before the write pass touches anything. */
    src[4] = 1e38f;
    CHECK(run(src, 8, dst, 8, 2, 1, 8, 8, 10.0f, 1, 1) == 1);
    CHECK(memcmp(dst, clean_dst, sizeof dst) == 0);
    src[4] = 0.5f;
}

static void test_stacked_order(void) {
    /* exposure then contrast then saturation is a fixed order:
     * in 0.3 with exposure +1 and contrast 2 -> ((0.6 - 0.18) * 2) + 0.18 = 1.02
     * on a grey pixel luma == r so saturation leaves it alone. */
    float src[4] = {0.3f, 0.3f, 0.3f, 1.0f};
    float dst[4] = {0};
    CHECK(run(src, 4, dst, 4, 1, 1, 4, 4, 1.0f, 2.0f, 0.5f) == 0);
    CHECK_CLOSE(dst[0], 1.02f);
    CHECK_CLOSE(dst[1], 1.02f);
    CHECK_CLOSE(dst[2], 1.02f);
    CHECK_CLOSE(dst[3], 1.0f);
}

static void test_tone_curve_examples(void) {
    /* The contract's eight defined expectations: a neutral-grey input has
     * luminance == channel value, and the tonal operation shifts it to the
     * documented output. (input, highlights, shadows, whites, blacks,
     * expected) */
    static const float cases[8][6] = {
        {0.25f, 0.0f, 1.0f, 0.0f, 0.0f, 0.390625f},
        {0.25f, 0.0f, -1.0f, 0.0f, 0.0f, 0.109375f},
        {0.75f, 1.0f, 0.0f, 0.0f, 0.0f, 0.890625f},
        {0.75f, -1.0f, 0.0f, 0.0f, 0.0f, 0.609375f},
        {0.0f, 0.0f, 0.0f, 0.0f, 1.0f, 0.125f},
        {0.25f, 0.0f, 0.0f, 0.0f, -1.0f, 0.14453125f},
        {1.0f, 0.0f, 0.0f, 1.0f, 0.0f, 1.125f},
        {1.0f, 0.0f, 0.0f, -1.0f, 0.0f, 0.875f}
    };
    int i;
    for (i = 0; i < 8; i++) {
        float src[4] = {cases[i][0], cases[i][0], cases[i][0], 1.0f};
        float dst[4] = {0};
        CHECK(run_tone(src, 4, dst, 4, 1, 1,
                       cases[i][1], cases[i][2], cases[i][3],
                       cases[i][4]) == 0);
        CHECK_CLOSE(dst[0], cases[i][5]);
        CHECK_CLOSE(dst[1], cases[i][5]);
        CHECK_CLOSE(dst[2], cases[i][5]);
        CHECK_CLOSE(dst[3], 1.0f);
    }
}

static void test_tone_preserves_channels_and_alpha(void) {
    /* The offset is added equally to RGB — channel differences survive
     * exactly — and alpha, including fully transparent, is copied. */
    float src[8] = {0.4f, 0.2f, 0.6f, 1.0f, 0.1f, 0.05f, 0.02f, 0.0f};
    float dst[8] = {0};
    CHECK(run_tone(src, 8, dst, 8, 2, 1, 0.0f, 0.6f, 0.0f, 0.5f) == 0);
    CHECK_CLOSE(dst[0] - src[0], dst[1] - src[1]);
    CHECK_CLOSE(dst[0] - src[0], dst[2] - src[2]);
    CHECK(memcmp(&src[3], &dst[3], sizeof(float)) == 0);
    CHECK(memcmp(&src[7], &dst[7], sizeof(float)) == 0);
}

static void test_tone_param_validation_atomic(void) {
    /* Non-finite or out-of-range tonal parameters are rejected before any
     * destination write. */
    float src[4] = {0.5f, 0.5f, 0.5f, 1.0f};
    float dst[4] = {7.0f, 7.0f, 7.0f, 7.0f};
    CHECK(run_tone(src, 4, dst, 4, 1, 1, NAN, 0.0f, 0.0f, 0.0f) == 1);
    CHECK(dst[0] == 7.0f && dst[3] == 7.0f);
    CHECK(run_tone(src, 4, dst, 4, 1, 1, 0.0f, 1.5f, 0.0f, 0.0f) == 1);
    CHECK(dst[0] == 7.0f && dst[3] == 7.0f);
    CHECK(run_tone(src, 4, dst, 4, 1, 1, 0.0f, 0.0f, -1.5f, 0.0f) == 1);
    CHECK(dst[0] == 7.0f && dst[3] == 7.0f);
    CHECK(run_tone(src, 4, dst, 4, 1, 1, 0.0f, 0.0f, 0.0f, INFINITY) == 1);
    CHECK(dst[0] == 7.0f && dst[3] == 7.0f);
    CHECK(run_tone(src, 4, dst, 4, 1, 1, -1.01f, 0.0f, 0.0f, 0.0f) == 1);
    CHECK(dst[0] == 7.0f && dst[3] == 7.0f);
}

static void test_tone_grid_monotone_and_bounded(void) {
    /* All 81 tonal corner/neutral combinations over a luminance ramp:
     * output luminance is monotone nondecreasing and stays bounded on the
     * SDR domain (whites may extend to 1.125, never unbounded). */
    static const float corners[3] = {-1.0f, 0.0f, 1.0f};
    int hi_i, sh_i, wh_i, bl_i;
    for (hi_i = 0; hi_i < 3; hi_i++) {
        for (sh_i = 0; sh_i < 3; sh_i++) {
            for (wh_i = 0; wh_i < 3; wh_i++) {
                for (bl_i = 0; bl_i < 3; bl_i++) {
                    float src[64 * 4];
                    float dst[64 * 4];
                    float prev = -1.0f;
                    int x;
                    for (x = 0; x < 64; x++) {
                        float y = (float)x / 63.0f;
                        src[x * 4] = y;
                        src[x * 4 + 1] = y;
                        src[x * 4 + 2] = y;
                        src[x * 4 + 3] = 1.0f;
                    }
                    CHECK(refra_editor_color_rgba_f32(
                        src, sizeof(src) / sizeof(float), dst,
                        sizeof(dst) / sizeof(float), 64, 1, 256, 256,
                        0.0f, 1.0f, 1.0f,
                        corners[hi_i], corners[sh_i],
                        corners[wh_i], corners[bl_i]) == 0);
                    for (x = 0; x < 64; x++) {
                        CHECK(dst[x * 4] >= prev - 1e-6f);
                        CHECK(dst[x * 4] >= -1e-6f);
                        CHECK(dst[x * 4] <= 1.125f + 1e-6f);
                        prev = dst[x * 4];
                    }
                }
            }
        }
    }
}

int main(void) {
    test_identity();
    test_exposure_doubles();
    test_contrast_pivot();
    test_saturation_luma();
    test_out_of_range_and_alpha_zero();
    test_padded_strides();
    test_in_place();
    test_invalid_args();
    test_overlap();
    test_neutral_bit_identity();
    test_byte_extent_overflow();
    test_invalid_pixels_no_partial_write();
    test_stacked_order();
    test_tone_curve_examples();
    test_tone_preserves_channels_and_alpha();
    test_tone_param_validation_atomic();
    test_tone_grid_monotone_and_bounded();
    if (failures == 0) {
        printf("color_test: all checks passed\n");
        return 0;
    }
    fprintf(stderr, "color_test: %d check(s) failed\n", failures);
    return 1;
}
