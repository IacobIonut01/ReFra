package com.dot.gallery.editor.core.model

/**
 * How a clip's source frame lands on the sequence canvas before its
 * [VideoLayerTransform] applies. The canvas fit factor multiplies the
 * transform's scale, so `effective = fit * transform.scale` in every mode:
 *
 * - [Fit] — `fit = min(W/w, H/h)`: the whole frame stays visible inside
 *   the canvas, mismatched edges transparent (letterbox).
 * - [Fill] — `fit = max(W/w, H/h)`: the frame covers the canvas, the
 *   overflow centre-cropped (cover).
 * - [Crop] — `fit = 1`: source pixels are drawn at native scale, so the
 *   canvas is a crop window and the transform's translation/scale pan and
 *   zoom it.
 */
enum class VideoContentMode { Fit, Fill, Crop }
