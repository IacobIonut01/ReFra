package com.dot.gallery.editor.ui

import androidx.compose.ui.graphics.ImageBitmap
import com.dot.gallery.editor.core.model.MAX_PHOTO_IMAGE_LAYERS
import com.dot.gallery.editor.core.model.PixelSize

/**
 * The shared AGSL/SkSL source generator for the runtime-shader preview —
 * both platforms compile the exact string [photoShaderSource] returns, so
 * the colour/geometry/composite contract is defined once, not per platform.
 *
 * Contract: `sourceImage` is a child shader over the *upright* source
 * bitmap; `eval` delivers premultiplied sRGB because the child is an
 * ordinary bitmap shader. Output pixels map back through flips, quarter
 * turns and the pixel crop exactly like
 * [com.dot.gallery.editor.core.model.PhotoMapping.sourcePointPacked] with
 * orientation 1 — `cropRect` is (left, top, width, height) in source
 * pixels, `outputSize` is the post-rotation output frame. With an active
 * warp (`warpActive` != 0) the output pixel first inverts the continuous
 * warp exactly like
 * [com.dot.gallery.editor.core.model.PhotoWarpMapping.sourcePoint] — on
 * the uncropped plane of `planeSize`, the crop being the window at
 * `planeOffset`: `warpScale` is the mapping's effective scale, `warpCos`/`warpSin` the
 * rotation cosine/sine, `perspectiveK` the horizontal/vertical keystone
 * times .0035. Degenerate or out-of-frame points paint transparent; valid
 * points sample the four integer neighbours through the child shader —
 * out-of-bounds neighbours are transparent — and each neighbour is
 * unpremultiplied, decoded and colour-adjusted before the straight-RGBA
 * bilinear mix, preserving the render-graph colour→geometry order.
 * Colour order matches `BasicColorEffect` v2: exposure gain, the tonal
 * operation (highlights/shadows/whites/blacks as a shared luminance
 * offset), contrast around 0.18, then Rec.709-luma saturation, all in
 * linear light.
 *
 * With [layerCount] > 0 the generated shader additionally declares
 * `layerImageN`/`layerMaskN` child shaders and per-layer uniforms
 * (`layerSourceSizeN`, `layerTranslationN`, `layerScaleN`,
 * `layerRotCosN`/`layerRotSinN`, `layerFlipHN`/`layerFlipVN`,
 * `layerOpacityN`, `layerBlendModeN`, `layerClipN`) plus the shared
 * `referenceOutputSize` (the full-resolution base output the stored
 * translations are expressed in). Each layer inverse-maps the output
 * pixel exactly like
 * [com.dot.gallery.editor.core.model.PhotoLayerMapping.sourcePoint] with
 * orientation 1 — translation scaled by `outputSize/referenceOutputSize`,
 * fit = min(output/source), effective scale = fit*scale, inverse
 * rotation, then flips — samples the four source neighbours into
 * straight linear RGBA, multiplies alpha by the mask texture sampled at
 * the same continuous coordinate, and composites bottom-to-top through a
 * direct port of `compositeLayerPixel` (blend modes: 0 Normal, 1 Multiply,
 * 2 Screen, 3 Overlay, 4 W3C SoftLight; alpha terms clamped only).
 * The spatial stage's Details pass — the edge-clamped 3x3 box over the
 * composited linear result with the same gains and order as the CPU
 * processor — then the finishing stage (white balance, tone curves,
 * look and vignette) run before the single sRGB encode + premultiply. Grain and
 * lens blur are not approximated: the shader path declines to draw
 * while the recipe's grain amount or lens-blur strength is non-zero
 * and the CPU preview owns the canvas instead.
 */
internal fun photoShaderSource(layerCount: Int): String {
    require(layerCount in 0..MAX_PHOTO_IMAGE_LAYERS) {
        "photo shader composites at most $MAX_PHOTO_IMAGE_LAYERS " +
            "layers: $layerCount"
    }
    return buildString {
    append(SHADER_UNIFORMS)
    if (layerCount > 0) {
        append("uniform float2 referenceOutputSize;\n")
    }
    for (i in 0 until layerCount) {
        append(
            "uniform shader layerImage$i;\n" +
                "uniform shader layerMask$i;\n" +
                "uniform float2 layerSourceSize$i;\n" +
                "uniform float2 layerTranslation$i;\n" +
                "uniform float layerScale$i;\n" +
                "uniform float layerRotCos$i;\n" +
                "uniform float layerRotSin$i;\n" +
                "uniform int layerFlipH$i;\n" +
                "uniform int layerFlipV$i;\n" +
                "uniform float layerOpacity$i;\n" +
                "uniform int layerBlendMode$i;\n" +
                "uniform int layerClip$i;\n"
        )
    }
    append(SHADER_HELPERS)
    append(BASE_PIXEL)
    if (layerCount > 0) {
        append(COMPOSITE_HELPERS)
        for (i in 0 until layerCount) {
            append(layerFunctions(i))
        }
    }
    append(compositeAt(layerCount))
    append(SPATIAL_FUNCTIONS)
    append(
        "half4 main(float2 p) {\n" +
            "    float2 o = floor(p * outputSize / paintSize);\n" +
            "    o = clamp(o, float2(0.0), outputSize - float2(1.0));\n" +
            "    return finishPixel(applyFinishing(o, " +
            "applySpatial(o, compositeAt(o))));\n}\n"
    )
    }
}

private val SHADER_UNIFORMS = """
uniform shader sourceImage;
uniform float2 paintSize;
uniform float2 outputSize;
uniform float4 cropRect;
uniform int turns;
uniform int flipH;
uniform int flipV;
uniform float2 sourceSize;
uniform int warpActive;
uniform float warpCos;
uniform float warpSin;
uniform float warpScale;
uniform float2 perspectiveK;
uniform float2 planeSize;
uniform float2 planeOffset;
uniform float exposureGain;
uniform float contrast;
uniform float saturation;
uniform float highlights;
uniform float shadows;
uniform float whites;
uniform float blacks;
uniform int wbActive;
uniform float3 wbGain;
uniform float gradeVibrance;
uniform int gradeProtectSkin;
uniform int gradeHslActive;
uniform float3 gradeHsl0;
uniform float3 gradeHsl1;
uniform float3 gradeHsl2;
uniform float3 gradeHsl3;
uniform float3 gradeHsl4;
uniform float3 gradeHsl5;
uniform float3 gradeHsl6;
uniform float3 gradeHsl7;
uniform int gradeWheelsActive;
uniform float3 gradeShadows;
uniform float3 gradeMidtones;
uniform float3 gradeHighlights;
uniform int curvesActive;
${curveUniforms("M")}
${curveUniforms("R")}
${curveUniforms("G")}
${curveUniforms("B")}
uniform int lookId;
uniform float lookAmount;
uniform int vigActive;
uniform float vigAmount;
uniform float vigMidpoint;
uniform float vigFeather;
uniform float vigRoundness;
uniform float vigHighlights;
uniform int detActive;
uniform float detTexture;
uniform float detClarity;
uniform float detSharpen;
uniform float detNoise;
"""

private val SHADER_HELPERS = """
float decodeSrgb(float c) { return c <= 0.04045 ? c / 12.92 : pow((c + 0.055) / 1.055, 2.4); }
float encodeSrgb(float c) { c = clamp(c, 0.0, 1.0); return c <= 0.0031308 ? 12.92 * c : 1.055 * pow(c, 1.0 / 2.4) - 0.055; }
float toneOffset(float luminance, float h, float s, float w, float b) {
    float u = clamp(luminance, 0.0, 1.0);
    float t = u;
    if (s != 0.0) { float inv = 1.0 - t; t += s * t * inv * inv; }
    if (h != 0.0) { t += h * t * t * (1.0 - t); }
    if (b > 0.0) { float inv = 1.0 - t; t += 0.125 * b * inv * inv * inv; }
    else if (b < 0.0) { float inv = 1.0 - t; t += b * t * inv * inv * inv; }
    if (w != 0.0) { t += 0.125 * w * t * t * t; }
    return t - u;
}
// Unpremultiply, decode and colour-adjust one premultiplied sRGB sample;
// returns linear-light straight RGBA (alpha unmodified).
float4 colourAdjusted(half4 pixel) {
    float a = pixel.a;
    float3 s = a > 0.0 ? float3(pixel.rgb) / a : float3(0.0);
    float3 rgb = float3(decodeSrgb(s.r), decodeSrgb(s.g), decodeSrgb(s.b));
    if (exposureGain != 1.0) rgb *= exposureGain;
    if (highlights != 0.0 || shadows != 0.0 || whites != 0.0 || blacks != 0.0) {
        float y = dot(rgb, float3(0.2126, 0.7152, 0.0722));
        rgb += float3(toneOffset(y, highlights, shadows, whites, blacks));
    }
    if (contrast != 1.0) rgb = (rgb - float3(0.18)) * contrast + float3(0.18);
    if (saturation != 1.0) {
        float luma = dot(rgb, float3(0.2126, 0.7152, 0.0722));
        rgb = float3(luma) + (rgb - float3(luma)) * saturation;
    }
    return float4(rgb, a);
}
// The monotone tone curves, evaluated in the display encoding: the same
// polynomial segments and unit-slope extension as the CPU processor.
float curveEncode(float c) { return c <= 0.0031308 ? 12.92 * c : 1.055 * pow(c, 1.0 / 2.4) - 0.055; }
${curveEval("M")}
${curveEval("R")}
${curveEval("G")}
${curveEval("B")}
// The settled look transforms — the same linear-RGB grades as the CPU
// processor, with Rec.709 luma throughout and no clamping. `id` is the
// explicit shader id from PhotoShaderFinishing (never an enum ordinal);
// a neutral look (0 or amount 0) skips all arithmetic like the CPU gate.
float3 lookTint(float3 rgb, float3 gains, float saturation, float lift, float contrast) {
    float3 t = rgb * gains;
    float l = dot(t, float3(0.2126, 0.7152, 0.0722));
    return (float3(l) + (t - float3(l)) * saturation - float3(0.18)) *
        contrast + float3(0.18) + float3(lift);
}
float3 lookTarget(int id, float3 rgb) {
    float luma = dot(rgb, float3(0.2126, 0.7152, 0.0722));
    if (id == 1) {
        // Clean: gentle saturation, then contrast around 0.18.
        return (float3(luma) + (rgb - float3(luma)) * 1.08 -
            float3(0.18)) * 1.06 + float3(0.18);
    }
    if (id == 2) return lookTint(rgb, float3(1.0), 1.35, 0.0, 1.10); // Vivid
    if (id == 3) return lookTint(rgb, float3(1.04, 1.0, 0.98), 0.72, 0.045, 0.90); // Pastel
    if (id == 4) return lookTint(rgb, float3(1.02, 1.0, 1.03), 1.12, 0.0, 1.18); // Chrome
    if (id == 5) {
        // WarmFade: warm channel gains, desaturate, fade to lifted base.
        float3 warm = rgb * float3(1.08, 1.02, 0.90);
        float l = dot(warm, float3(0.2126, 0.7152, 0.0722));
        return (float3(l) + (warm - float3(l)) * 0.90) * 0.88 +
            float3(0.035);
    }
    if (id == 6) {
        // Cool: cool channel gains, slightly boosted saturation.
        float3 cool = rgb * float3(0.92, 1.01, 1.10);
        float l = dot(cool, float3(0.2126, 0.7152, 0.0722));
        return float3(l) + (cool - float3(l)) * 1.04;
    }
    if (id == 7) return lookTint(rgb, float3(1.10, 1.03, 0.86), 1.06, 0.01, 1.02); // Golden
    if (id == 8) return lookTint(rgb, float3(1.12, 0.99, 0.80), 0.95, 0.015, 1.04); // Desert
    if (id == 9) return lookTint(rgb, float3(0.94, 1.08, 0.93), 1.05, 0.0, 1.03); // Forest
    if (id == 10) return lookTint(rgb, float3(1.10, 0.95, 1.01), 0.98, 0.02, 0.98); // Rose
    if (id == 11) return lookTint(rgb, float3(0.86, 0.97, 1.16), 1.0, 0.02, 1.02); // Blue hour
    if (id == 12) {
        // Cinematic: base contrast, then luma-weighted split-tone —
        // cool shadows toward warm highlights.
        float3 base = (rgb - float3(0.18)) * 1.08 + float3(0.18);
        float w = smoothstep(0.35, 0.75,
            dot(base, float3(0.2126, 0.7152, 0.0722)));
        return mix(base * float3(0.92, 1.04, 1.10),
            base * float3(1.08, 1.02, 0.92), w);
    }
    if (id == 13) {
        // Teal & orange: teal shadows into orange highlights.
        float w = smoothstep(0.30, 0.70, luma);
        return mix(rgb * float3(0.92, 1.02, 1.10),
            rgb * float3(1.10, 1.0, 0.90), w);
    }
    if (id == 14) {
        // Cinema green: green shadows into warm highlights.
        float3 base = (rgb - float3(0.18)) * 1.06 + float3(0.18);
        float w = smoothstep(0.30, 0.70,
            dot(base, float3(0.2126, 0.7152, 0.0722)));
        return mix(base * float3(0.95, 1.05, 0.98),
            base * float3(1.06, 1.02, 0.94), w);
    }
    if (id == 15) return lookTint(rgb, float3(1.06, 0.98, 0.86), 0.82, 0.05, 0.90); // Vintage
    if (id == 16) return lookTint(rgb, float3(1.02, 1.0, 0.99), 0.88, 0.06, 0.86); // Fade
    if (id == 17) return lookTint(rgb, float3(1.0), 0.80, 0.08, 0.82); // Matte
    if (id == 18) return lookTint(rgb, float3(1.02, 1.0, 0.98), 0.35, 0.01, 1.25); // Bleach
    if (id == 19) {
        // Sepia: luma into the warm paper ramp.
        return float3(luma * 1.07 + 0.03, luma * 0.93 + 0.02,
            luma * 0.74 + 0.01);
    }
    if (id == 20) return float3(luma); // Mono
    if (id == 21) {
        // Noir: monochrome with a harder contrast.
        float c = (luma - 0.18) * 1.30 + 0.18;
        return float3(c);
    }
    return rgb;
}
// The colour grade's helpers and pixel math — vibrance, HSL bands, tonal
// wheels — in the same order and equations as the CPU processor.
float gradeHue(float3 c, float high, float chroma) {
    float hue;
    if (high == c.r) hue = 60.0 * (c.g - c.b) / chroma;
    else if (high == c.g) hue = 60.0 * ((c.b - c.r) / chroma + 2.0);
    else hue = 60.0 * ((c.r - c.g) / chroma + 4.0);
    return hue < 0.0 ? hue + 360.0 : hue;
}
float3 gradeHslAt(float hue) {
    if (hue < 30.0) return mix(gradeHsl0, gradeHsl1, hue / 30.0);
    if (hue < 60.0) return mix(gradeHsl1, gradeHsl2, (hue - 30.0) / 30.0);
    if (hue < 120.0) return mix(gradeHsl2, gradeHsl3, (hue - 60.0) / 60.0);
    if (hue < 180.0) return mix(gradeHsl3, gradeHsl4, (hue - 120.0) / 60.0);
    if (hue < 220.0) return mix(gradeHsl4, gradeHsl5, (hue - 180.0) / 40.0);
    if (hue < 280.0) return mix(gradeHsl5, gradeHsl6, (hue - 220.0) / 60.0);
    if (hue < 320.0) return mix(gradeHsl6, gradeHsl7, (hue - 280.0) / 40.0);
    return mix(gradeHsl7, gradeHsl0, (hue - 320.0) / 40.0);
}
float3 applyGrade(float3 rgb) {
    const float3 lumaWeights = float3(0.2126, 0.7152, 0.0722);
    if (gradeVibrance != 0.0) {
        float high = max(rgb.r, max(rgb.g, rgb.b));
        if (high > 0.0) {
            float chroma = high - min(rgb.r, min(rgb.g, rgb.b));
            float push = gradeVibrance * (1.0 - clamp(chroma / high, 0.0, 1.0));
            if (gradeProtectSkin != 0 && chroma > 0.0) {
                float hue = gradeHue(rgb, high, chroma);
                float skin = clamp(1.0 - abs(hue - 25.0) / 25.0, 0.0, 1.0);
                push *= 1.0 - 0.65 * skin;
            }
            float luma = dot(rgb, lumaWeights);
            rgb = float3(luma) + (rgb - float3(luma)) * (1.0 + push);
        }
    }
    if (gradeHslActive != 0) {
        float high = max(rgb.r, max(rgb.g, rgb.b));
        float chroma = high - min(rgb.r, min(rgb.g, rgb.b));
        if (high > 0.0 && chroma > 0.0) {
            float hue = gradeHue(rgb, high, chroma);
            float weight = min(1.0, chroma / high * 2.5);
            float3 delta = gradeHslAt(hue) * weight;
            float angle = delta.x * 0.5235988;
            float c = cos(angle);
            float s = sin(angle) * 0.57735026;
            float k = (1.0 - c) / 3.0;
            float3 turned = float3(
                rgb.r * (c + k) + rgb.g * (k - s) + rgb.b * (k + s),
                rgb.r * (k + s) + rgb.g * (c + k) + rgb.b * (k - s),
                rgb.r * (k - s) + rgb.g * (k + s) + rgb.b * (c + k));
            float luma = dot(turned, lumaWeights);
            rgb = (float3(luma) + (turned - float3(luma)) * (1.0 + delta.y)) *
                pow(2.0, delta.z);
        }
    }
    if (gradeWheelsActive != 0) {
        float tone = sqrt(clamp(dot(rgb, lumaWeights), 0.0, 1.0));
        float shadowMask = (1.0 - tone) * (1.0 - tone);
        float highlightMask = tone * tone;
        float midMask = 1.0 - shadowMask - highlightMask;
        float3 stops = gradeShadows * shadowMask + gradeMidtones * midMask +
            gradeHighlights * highlightMask;
        rgb *= float3(pow(2.0, stops.r), pow(2.0, stops.g), pow(2.0, stops.b));
    }
    return rgb;
}
// The finishing stage — white balance, grade, curves, look, vignette in the same
// order and equations as the CPU processor. `o` is the integer output
// coordinate, so the vignette mask is resolution-true like the full
// frame the CPU evaluates. Grain is deliberately absent: the runtime
// shader draws only while grain amount is 0 — a mismatched noise hash
// would be a dishonest approximation.
float4 applyFinishing(float2 o, float4 straight) {
    float3 rgb = straight.rgb;
    if (wbActive != 0) rgb *= wbGain;
    rgb = applyGrade(rgb);
    if (curvesActive != 0) {
        if (curveMActive != 0 || curveRActive != 0) {
            rgb.r = decodeSrgb(curveEvalR(curveEvalM(curveEncode(rgb.r))));
        }
        if (curveMActive != 0 || curveGActive != 0) {
            rgb.g = decodeSrgb(curveEvalG(curveEvalM(curveEncode(rgb.g))));
        }
        if (curveMActive != 0 || curveBActive != 0) {
            rgb.b = decodeSrgb(curveEvalB(curveEvalM(curveEncode(rgb.b))));
        }
    }
    if (lookId != 0 && lookAmount != 0.0) {
        rgb = mix(rgb, lookTarget(lookId, rgb), lookAmount);
    }
    if (vigActive != 0) {
        float2 nf = float2(
            (2.0 * (o.x + 0.5) / outputSize.x - 1.0) *
                max(1.0, outputSize.x / outputSize.y),
            (2.0 * (o.y + 0.5) / outputSize.y - 1.0) *
                max(1.0, outputSize.y / outputSize.x));
        float round_ = length(nf);
        float square = max(abs(nf.x), abs(nf.y));
        float dist = mix(square, round_, (vigRoundness + 1.0) * 0.5);
        float st = 0.15 + vigMidpoint * 0.65;
        float en = min(1.5, st + 0.05 + vigFeather * 0.65);
        float m = smoothstep(st, en, dist);
        float stops = vigAmount * 2.0;
        float y = max(0.0, dot(rgb, float3(0.2126, 0.7152, 0.0722)));
        float prot = stops < 0.0
            ? 1.0 - vigHighlights * smoothstep(0.6, 1.0, y)
            : 1.0;
        rgb *= pow(2.0, stops * m * prot);
    }
    return float4(rgb, straight.a);
}
// Encode straight linear RGBA once and premultiply for the output surface.
half4 finishPixel(float4 straight) {
    float3 encoded = float3(
        encodeSrgb(straight.r), encodeSrgb(straight.g), encodeSrgb(straight.b));
    return half4(encoded * straight.a, straight.a);
}
// One bilinear neighbour of the warped source point; pixels outside the
// upright source are transparent, matching the CPU path's no-edge-clamp.
float4 warpNeighbour(float2 corner) {
    if (corner.x < 0.0 || corner.x > sourceSize.x - 1.0 ||
        corner.y < 0.0 || corner.y > sourceSize.y - 1.0) {
        return float4(0.0);
    }
    return colourAdjusted(sourceImage.eval(corner + float2(0.5)));
}
"""

// The base recipe pixel as straight linear RGBA — geometry and colour but
// no encode, so layers can composite on the linear result before the
// single finishing pass. `o` is the clamped integer output coordinate.
private const val BASE_PIXEL = """
float4 basePixel(float2 o) {
    if (warpActive == 0) {
        // Rigid path: one nearest sample, bit-exact with the CPU mapping.
        if (flipH != 0) o.x = outputSize.x - 1.0 - o.x;
        if (flipV != 0) o.y = outputSize.y - 1.0 - o.y;
        float2 c = o;
        if (turns == 1) c = float2(o.y, cropRect.w - 1.0 - o.x);
        else if (turns == 2) c = float2(cropRect.z - 1.0 - o.x, cropRect.w - 1.0 - o.y);
        else if (turns == 3) c = float2(cropRect.z - 1.0 - o.y, o.x);
        return colourAdjusted(
            sourceImage.eval(c + cropRect.xy + float2(0.5)));
    }
    // Inverse warp on the plane (the uncropped rigid frame) — the same
    // equations as PhotoWarpMapping.sourcePoint, evaluated per pixel in
    // float. The crop is the window at planeOffset.
    float w = planeSize.x;
    float h = planeSize.y;
    float2 pc = (o + planeOffset + float2(0.5) - planeSize * 0.5) / warpScale;
    float rx = warpCos * pc.x + warpSin * pc.y;
    float ry = -warpSin * pc.x + warpCos * pc.y;
    float nx = rx / (w * 0.5);
    float ny = ry / (h * 0.5);
    float d = 1.0 - perspectiveK.x * nx - perspectiveK.y * ny;
    if (d <= 0.05) return float4(0.0);
    float sx = nx / d;
    float sy = ny / d;
    float2 rigid = float2(
        (sx + 1.0) * w * 0.5 - 0.5, (sy + 1.0) * h * 0.5 - 0.5);
    if (rigid.x < -0.5 || rigid.x > w - 0.5 ||
        rigid.y < -0.5 || rigid.y > h - 0.5) {
        return float4(0.0);
    }
    // Continuous rigid chain on the full frame: un-flip, un-turn.
    if (flipH != 0) rigid.x = w - 1.0 - rigid.x;
    if (flipV != 0) rigid.y = h - 1.0 - rigid.y;
    float2 source = rigid;
    if (turns == 1) source = float2(rigid.y, sourceSize.y - 1.0 - rigid.x);
    else if (turns == 2) source = float2(sourceSize.x - 1.0 - rigid.x, sourceSize.y - 1.0 - rigid.y);
    else if (turns == 3) source = float2(sourceSize.x - 1.0 - rigid.y, rigid.x);
    float2 corner = floor(source);
    float2 t = source - corner;
    float4 top = mix(
        warpNeighbour(corner),
        warpNeighbour(corner + float2(1.0, 0.0)), t.x);
    float4 bottom = mix(
        warpNeighbour(corner + float2(0.0, 1.0)),
        warpNeighbour(corner + float2(1.0, 1.0)), t.x);
    return mix(top, bottom, t.y);
}
"""

// Direct port of compositeLayerPixel / blendChannel: straight-alpha
// linear source-over with the per-channel blend, alpha terms clamped only.
private const val COMPOSITE_HELPERS = """
float blendChannelValue(int mode, float cb, float cs) {
    if (mode == 0) return cs;
    if (mode == 1) return cb * cs;
    if (mode == 2) return cb + cs - cb * cs;
    if (mode == 3) {
        return cb <= 0.5 ? 2.0 * cb * cs
            : 1.0 - 2.0 * (1.0 - cb) * (1.0 - cs);
    }
    if (cs <= 0.5) {
        return cb - (1.0 - 2.0 * cs) * cb * (1.0 - cb);
    }
    float d = cb <= 0.25 ? ((16.0 * cb - 12.0) * cb + 4.0) * cb : sqrt(cb);
    return cb + (2.0 * cs - 1.0) * (d - cb);
}
float compositeChannelValue(
    int mode, float srcA, float ab, float ao, float cb, float cs
) {
    return (srcA * (1.0 - ab) * cs +
        srcA * ab * blendChannelValue(mode, cb, cs) +
        ab * (1.0 - srcA) * cb) / ao;
}
float4 compositePixel(
    float4 dst, float4 cs, int mode, float opacity, int clip
) {
    float ab = clamp(dst.a, 0.0, 1.0);
    float srcA = cs.a * opacity;
    if (clip != 0) srcA *= ab;
    srcA = clamp(srcA, 0.0, 1.0);
    if (srcA <= 0.0) return dst;
    float ao = clamp(srcA + ab * (1.0 - srcA), 0.0, 1.0);
    return float4(
        compositeChannelValue(mode, srcA, ab, ao, dst.r, cs.r),
        compositeChannelValue(mode, srcA, ab, ao, dst.g, cs.g),
        compositeChannelValue(mode, srcA, ab, ao, dst.b, cs.b),
        ao);
}
"""

// The composited recipe at one output coordinate — the same chain
// `main` used to run inline: base pixel, then every visible layer's
// sample composited bottom-to-top. Factored into a function because
// the Details 3x3 taps the composite result at neighbour coordinates.
private fun compositeAt(layerCount: Int): String = buildString {
    append("float4 compositeAt(float2 o) {\n    float4 accum = basePixel(o);\n")
    for (i in 0 until layerCount) {
        append(
            "    accum = compositePixel(accum, layerSample$i(o), " +
                "layerBlendMode$i, layerOpacity$i, layerClip$i);\n"
        )
    }
    append("    return accum;\n}\n")
}

// The spatial stage's shader-supported slice — the Details 3x3 box over
// the composited output, edge-clamped by clamping each tap's coordinate,
// with the same gains and order as the CPU processor: noise-reduction
// lerp, the high band lifted by texture+sharpening, the luma-local band
// by clarity. Lens blur is deliberately absent: its per-pixel variable
// radius over the Details result would multiply the composite chain by
// the 13 taps and the recipe's own mask — the runtime shader declines
// the whole spatial stage while lens blur strength is non-zero and the
// CPU preview draws it honestly.
private const val SPATIAL_FUNCTIONS = """
float4 applySpatial(float2 o, float4 straight) {
    if (detActive == 0) return straight;
    float2 lo = float2(0.0);
    float2 hi = outputSize - float2(1.0);
    float3 blur = (
        compositeAt(clamp(o + float2(-1.0, -1.0), lo, hi)).rgb +
        compositeAt(clamp(o + float2( 0.0, -1.0), lo, hi)).rgb +
        compositeAt(clamp(o + float2( 1.0, -1.0), lo, hi)).rgb +
        compositeAt(clamp(o + float2(-1.0,  0.0), lo, hi)).rgb +
        straight.rgb +
        compositeAt(clamp(o + float2( 1.0,  0.0), lo, hi)).rgb +
        compositeAt(clamp(o + float2(-1.0,  1.0), lo, hi)).rgb +
        compositeAt(clamp(o + float2( 0.0,  1.0), lo, hi)).rgb +
        compositeAt(clamp(o + float2( 1.0,  1.0), lo, hi)).rgb
    ) / 9.0;
    float3 rgb = straight.rgb;
    float3 high = rgb - blur;
    float local = dot(rgb, float3(0.2126, 0.7152, 0.0722)) -
        dot(blur, float3(0.2126, 0.7152, 0.0722));
    float3 nr = mix(rgb, blur, detNoise * 0.72);
    return float4(
        nr + high * (detTexture * 0.38 + detSharpen * 0.9) +
            float3(local * detClarity * 0.65),
        straight.a);
}
"""

// The per-layer sample functions: inverse PhotoLayerMapping (orientation
// 1 — layer previews are already upright), transparent neighbours outside
// the layer, straight-linear bilinear, alpha modulated by the mask
// texture at the same continuous upright coordinate.
private fun layerFunctions(index: Int): String = """
float4 layerColour$index(float2 corner) {
    if (corner.x < 0.0 || corner.x > layerSourceSize$index.x - 1.0 ||
        corner.y < 0.0 || corner.y > layerSourceSize$index.y - 1.0) {
        return float4(0.0);
    }
    half4 p = layerImage$index.eval(corner + float2(0.5));
    float a = p.a;
    float3 s = a > 0.0 ? float3(p.rgb) / a : float3(0.0);
    return float4(decodeSrgb(s.r), decodeSrgb(s.g), decodeSrgb(s.b), a);
}
float layerMaskAlpha$index(float2 corner) {
    float2 c = clamp(corner, float2(0.0), layerSourceSize$index - float2(1.0));
    return layerMask$index.eval(c + float2(0.5)).a;
}
float4 layerSample$index(float2 o) {
    float2 t = layerTranslation$index * outputSize / referenceOutputSize;
    float2 centre = outputSize * 0.5 + t;
    float effective = min(outputSize.x / layerSourceSize$index.x,
        outputSize.y / layerSourceSize$index.y) * layerScale$index;
    float2 d = (o + float2(0.5) - centre) / effective;
    float w = layerSourceSize$index.x;
    float h = layerSourceSize$index.y;
    float u = layerRotCos$index * d.x + layerRotSin$index * d.y + w * 0.5 - 0.5;
    float v = -layerRotSin$index * d.x + layerRotCos$index * d.y + h * 0.5 - 0.5;
    if (layerFlipH$index != 0) u = w - 1.0 - u;
    if (layerFlipV$index != 0) v = h - 1.0 - v;
    if (u < -0.5 || u > w - 0.5 || v < -0.5 || v > h - 0.5) return float4(0.0);
    float2 corner = floor(float2(u, v));
    float2 f = float2(u, v) - corner;
    float4 top = mix(
        layerColour$index(corner),
        layerColour$index(corner + float2(1.0, 0.0)), f.x);
    float4 bottom = mix(
        layerColour$index(corner + float2(0.0, 1.0)),
        layerColour$index(corner + float2(1.0, 1.0)), f.x);
    float4 straight = mix(top, bottom, f.y);
    float maskA = mix(
        mix(layerMaskAlpha$index(corner),
            layerMaskAlpha$index(corner + float2(1.0, 0.0)), f.x),
        mix(layerMaskAlpha$index(corner + float2(0.0, 1.0)),
            layerMaskAlpha$index(corner + float2(1.0, 1.0)), f.x),
        f.y);
    straight.a *= maskA;
    return straight;
}
"""

/**
 * Compiles a [PhotoShaderRenderer] compositing [layers] over [base], or
 * returns null when the platform has no runtime-shader backend (Android
 * below API 33). [layers] is the recipe's visible stack in bottom-to-top
 * order — the renderer records that signature and declines frames whose
 * visible layers no longer match. [referenceSourceSize] is the document's
 * full-resolution upright source size: layer translations are stored in
 * those output pixels and scaled per frame against the reference output
 * derived from the live geometry. Shader compile/runtime errors are
 * reported through [onFailure] and surface as null here / false from
 * draw — the caller then uses the CPU pipeline.
 */
internal expect fun createCompositePhotoShaderRenderer(
    base: ImageBitmap,
    layers: List<PhotoShaderLayerSource>,
    referenceSourceSize: PixelSize,
    onFailure: (Throwable) -> Unit
): PhotoShaderRenderer?

/**
 * The base-image-only convenience form — the composite renderer with an
 * empty layer stack, keeping the pre-layers call sites and tests working.
 */
internal fun createPhotoShaderRenderer(
    source: ImageBitmap,
    onFailure: (Throwable) -> Unit
): PhotoShaderRenderer? = createCompositePhotoShaderRenderer(
    source, emptyList(),
    PixelSize(source.width, source.height), onFailure
)

/**
 * The factory [PhotoEditorContent] uses for base-only documents —
 * production keeps this as [createPhotoShaderRenderer]; tests swap in a
 * failing fake to exercise the CPU-fallback path.
 */
internal var photoShaderRendererFactory:
    (ImageBitmap, (Throwable) -> Unit) -> PhotoShaderRenderer? =
    { source, onFailure -> createPhotoShaderRenderer(source, onFailure) }

/**
 * The factory [PhotoEditorContent] uses — layered documents compile the
 * composite shader; a base-only document delegates to
 * [photoShaderRendererFactory] so the existing fallback injection keeps
 * working. Tests swap this var to drive the layered paths.
 */
internal var compositePhotoShaderRendererFactory: (
    ImageBitmap,
    List<PhotoShaderLayerSource>,
    PixelSize,
    (Throwable) -> Unit
) -> PhotoShaderRenderer? =
    { base, layers, referenceSourceSize, onFailure ->
        if (layers.isEmpty()) {
            photoShaderRendererFactory(base, onFailure)
        } else {
            createCompositePhotoShaderRenderer(
                base, layers, referenceSourceSize, onFailure
            )
        }
    }

/** Slots for one curve's segments: a 16-point curve has 15, packed in float4s. */
internal const val PHOTO_SHADER_CURVE_SEGMENTS = 16

private fun curveUniforms(name: String) = """
uniform int curve${name}Active;
uniform float4 curve${name}Ends;
uniform float4 curve${name}Starts[4];
uniform float4 curve${name}Coefficients[16];
""".trim()

// One function per curve: runtime effects index uniform arrays only with loop
// counters and constants, so an array can't arrive as a parameter.
private fun curveEval(name: String) = """
float curveEval$name(float x) {
    if (curve${name}Active == 0) return x;
    if (x <= curve${name}Ends.x) return curve${name}Ends.y + min(x, 0.0);
    if (x >= curve${name}Ends.z) return curve${name}Ends.w + max(x - 1.0, 0.0);
    float4 c = curve${name}Coefficients[0];
    float t = x - curve${name}Starts[0].x;
    for (int i = 0; i < 4; i++) {
        for (int j = 0; j < 4; j++) {
            float start = curve${name}Starts[i][j];
            if (start <= x) {
                c = curve${name}Coefficients[i * 4 + j];
                t = x - start;
            }
        }
    }
    return c.x + t * (c.y + t * (c.z + t * c.w));
}
""".trim()
