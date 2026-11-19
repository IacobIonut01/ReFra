package com.dot.gallery.editor.ui

import androidx.compose.runtime.Composable
import com.dot.gallery.editor.core.model.LensBlurMode
import com.dot.gallery.editor.core.model.NormalizedCrop
import com.dot.gallery.editor.core.model.PhotoBlendMode
import com.dot.gallery.editor.core.model.PhotoEdits
import com.dot.gallery.editor.core.model.PhotoGeometry
import com.dot.gallery.editor.core.model.PhotoLayerMask
import com.dot.gallery.editor.core.model.PhotoLayerTransform
import com.dot.gallery.editor.core.model.PhotoMapping
import com.dot.gallery.editor.core.model.PixelSize
import com.dot.gallery.editor.core.model.contrastAmount
import com.dot.gallery.editor.ui.generated.resources.Res
import com.dot.gallery.editor.ui.generated.resources.editor_amount
import com.dot.gallery.editor.ui.generated.resources.editor_angle
import com.dot.gallery.editor.ui.generated.resources.editor_blacks
import com.dot.gallery.editor.ui.generated.resources.editor_blur_centre
import com.dot.gallery.editor.ui.generated.resources.editor_blur_linear
import com.dot.gallery.editor.ui.generated.resources.editor_blur_radial
import com.dot.gallery.editor.ui.generated.resources.editor_channel_blue
import com.dot.gallery.editor.ui.generated.resources.editor_channel_green
import com.dot.gallery.editor.ui.generated.resources.editor_channel_red
import com.dot.gallery.editor.ui.generated.resources.editor_channel_rgb
import com.dot.gallery.editor.ui.generated.resources.editor_clarity
import com.dot.gallery.editor.ui.generated.resources.editor_contrast
import com.dot.gallery.editor.ui.generated.resources.editor_exposure
import com.dot.gallery.editor.ui.generated.resources.editor_feather
import com.dot.gallery.editor.ui.generated.resources.editor_flip_horizontal
import com.dot.gallery.editor.ui.generated.resources.editor_flip_vertical
import com.dot.gallery.editor.ui.generated.resources.editor_grain_size
import com.dot.gallery.editor.ui.generated.resources.editor_highlight_bokeh
import com.dot.gallery.editor.ui.generated.resources.editor_highlights
import com.dot.gallery.editor.ui.generated.resources.editor_midpoint
import com.dot.gallery.editor.ui.generated.resources.editor_noise_reduction
import com.dot.gallery.editor.ui.generated.resources.editor_perspective_horizontal
import com.dot.gallery.editor.ui.generated.resources.editor_perspective_scale
import com.dot.gallery.editor.ui.generated.resources.editor_perspective_vertical
import com.dot.gallery.editor.ui.generated.resources.editor_protect_skin
import com.dot.gallery.editor.ui.generated.resources.editor_retouch_black_white
import com.dot.gallery.editor.ui.generated.resources.editor_retouch_blue_tone
import com.dot.gallery.editor.ui.generated.resources.editor_retouch_hue
import com.dot.gallery.editor.ui.generated.resources.editor_retouch_skin_tone
import com.dot.gallery.editor.ui.generated.resources.editor_rotation
import com.dot.gallery.editor.ui.generated.resources.editor_roundness
import com.dot.gallery.editor.ui.generated.resources.editor_roughness
import com.dot.gallery.editor.ui.generated.resources.editor_saturation
import com.dot.gallery.editor.ui.generated.resources.editor_shadows
import com.dot.gallery.editor.ui.generated.resources.editor_sharpening
import com.dot.gallery.editor.ui.generated.resources.editor_strength
import com.dot.gallery.editor.ui.generated.resources.editor_straighten
import com.dot.gallery.editor.ui.generated.resources.editor_colour_hsl
import com.dot.gallery.editor.ui.generated.resources.editor_colour_wheels
import com.dot.gallery.editor.ui.generated.resources.editor_temperature
import com.dot.gallery.editor.ui.generated.resources.editor_tint
import com.dot.gallery.editor.ui.generated.resources.editor_texture
import com.dot.gallery.editor.ui.generated.resources.editor_tool_crop
import com.dot.gallery.editor.ui.generated.resources.editor_tool_curves
import com.dot.gallery.editor.ui.generated.resources.editor_tool_borders
import com.dot.gallery.editor.ui.generated.resources.editor_tool_colour_tone
import com.dot.gallery.editor.ui.generated.resources.editor_tool_develop
import com.dot.gallery.editor.ui.generated.resources.editor_tool_double_exposure
import com.dot.gallery.editor.ui.generated.resources.editor_tool_edges
import com.dot.gallery.editor.ui.generated.resources.editor_tool_lens_blur
import com.dot.gallery.editor.ui.generated.resources.editor_tool_looks
import com.dot.gallery.editor.ui.generated.resources.editor_tool_markup
import com.dot.gallery.editor.ui.generated.resources.editor_tool_posterize
import com.dot.gallery.editor.ui.generated.resources.editor_tool_remove_background
import com.dot.gallery.editor.ui.generated.resources.editor_tool_transform_layer
import com.dot.gallery.editor.ui.generated.resources.editor_tool_blend
import com.dot.gallery.editor.ui.generated.resources.editor_tool_mask
import com.dot.gallery.editor.ui.generated.resources.editor_transition
import com.dot.gallery.editor.ui.generated.resources.editor_vibrance
import com.dot.gallery.editor.ui.generated.resources.editor_whites
import com.dot.gallery.editor.ui.generated.resources.editor_develop_brightness
import com.dot.gallery.editor.ui.generated.resources.editor_develop_contrast
import com.dot.gallery.editor.ui.generated.resources.editor_develop_exposure
import com.dot.gallery.editor.ui.generated.resources.editor_develop_highlights
import com.dot.gallery.editor.ui.generated.resources.editor_develop_saturation
import com.dot.gallery.editor.ui.generated.resources.editor_develop_shadows
import com.dot.gallery.editor.ui.generated.resources.editor_develop_sharpen
import com.dot.gallery.editor.ui.generated.resources.editor_develop_temperature
import com.dot.gallery.editor.ui.generated.resources.editor_develop_tint
import com.dot.gallery.editor.ui.generated.resources.editor_develop_vibrance
import com.dot.gallery.editor.ui.generated.resources.editor_studio_edit_removed
import com.dot.gallery.editor.ui.generated.resources.editor_studio_edits_more
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import kotlin.math.roundToInt

/**
 * One editable recipe value the summary surfaces can name — shared by
 * the Studio tool sections' collapsed summaries and the History rows'
 * step subtitles. [snapshot] is the raw comparable the step diff reads;
 * [display] is the full formatted value and [summary] the compact signed
 * form the tool summaries prefer (falling back to [display]). [counted]
 * keeps rollup rows out of photoToolEditCount.
 */
internal class PhotoEditParam(
    val key: String,
    val tool: EditorToolId,
    val labelRes: StringResource,
    val snapshot: Any?,
    val display: String,
    val isDefault: Boolean,
    val titleRes: StringResource? = null,
    val displayRes: StringResource? = null,
    val scopeText: String? = null,
    val summary: String? = null,
    val counted: Boolean = true
)

private fun pct(fraction: Float): Float = fraction * 100f

/**
 * Every recipe parameter the editor can name, in a stable order — the
 * single source of truth the per-tool edit counts and summaries and the
 * history step subtitles all read. [sourceSize] only
 * sharpens the Crop row's output dimensions; without it the crop reports
 * its frame share instead.
 */
internal fun photoEditParams(
    edits: PhotoEdits,
    sourceSize: PixelSize? = null
): List<PhotoEditParam> {
    val params = ArrayList<PhotoEditParam>()
    val color = edits.color
    val finishing = edits.finishing
    val spatial = edits.spatial
    val retouch = edits.retouch
    val geometry = edits.geometry

    // Light — the six exposure/tone parameters.
    params += PhotoEditParam(
        key = "color.exposure", tool = EditorToolIds.PhotoLight,
        labelRes = Res.string.editor_exposure, snapshot = color.exposureStops,
        display = formatSignedDecimal(color.exposureStops) + " EV",
        summary = formatSignedCompact(color.exposureStops) + " EV",
        isDefault = color.exposureStops == 0f
    )
    params += PhotoEditParam(
        key = "color.contrast", tool = EditorToolIds.PhotoLight,
        labelRes = Res.string.editor_contrast, snapshot = color.contrast,
        display = formatDecimal(color.contrast) + "×",
        summary = formatSignedCompact(contrastAmount(color.contrast)),
        isDefault = color.contrast == 1f
    )
    params += PhotoEditParam(
        key = "color.saturation", tool = EditorToolIds.PhotoColour,
        labelRes = Res.string.editor_saturation, snapshot = color.saturation,
        display = formatDecimal(color.saturation) + "×",
        summary = formatSignedCompact((color.saturation - 1f) * 100f),
        isDefault = color.saturation == 1f
    )
    params += PhotoEditParam(
        key = "color.highlights", tool = EditorToolIds.PhotoLight,
        labelRes = Res.string.editor_highlights, snapshot = color.highlights,
        display = formatSignedCompact(color.highlights * 100),
        isDefault = color.highlights == 0f
    )
    params += PhotoEditParam(
        key = "color.shadows", tool = EditorToolIds.PhotoLight,
        labelRes = Res.string.editor_shadows, snapshot = color.shadows,
        display = formatSignedCompact(color.shadows * 100),
        isDefault = color.shadows == 0f
    )
    params += PhotoEditParam(
        key = "color.whites", tool = EditorToolIds.PhotoLight,
        labelRes = Res.string.editor_whites, snapshot = color.whites,
        display = formatSignedCompact(color.whites * 100),
        isDefault = color.whites == 0f
    )
    params += PhotoEditParam(
        key = "color.blacks", tool = EditorToolIds.PhotoLight,
        labelRes = Res.string.editor_blacks, snapshot = color.blacks,
        display = formatSignedCompact(color.blacks * 100),
        isDefault = color.blacks == 0f
    )

    // Curves — one row per channel.
    val curves = finishing.curves
    val curveChannels = listOf(
        Triple("rgb", Res.string.editor_channel_rgb, curves.master),
        Triple("red", Res.string.editor_channel_red, curves.red),
        Triple("green", Res.string.editor_channel_green, curves.green),
        Triple("blue", Res.string.editor_channel_blue, curves.blue)
    )
    for ((channel, channelRes, curve) in curveChannels) {
        params += PhotoEditParam(
            key = "curves.$channel", tool = EditorToolIds.PhotoCurves,
            labelRes = channelRes, titleRes = Res.string.editor_tool_curves,
            snapshot = curve, display = "✓", isDefault = curve.isNeutral
        )
    }

    // White balance.
    val wb = finishing.whiteBalance
    params += PhotoEditParam(
        key = "wb.temperature", tool = EditorToolIds.PhotoWhiteBalance,
        labelRes = Res.string.editor_temperature, snapshot = wb.temperatureKelvin,
        display = "${wb.temperatureKelvin.roundToInt()} K",
        isDefault = wb.temperatureKelvin == 6500f
    )
    params += PhotoEditParam(
        key = "wb.tint", tool = EditorToolIds.PhotoWhiteBalance,
        labelRes = Res.string.editor_tint, snapshot = wb.tint,
        display = formatSignedCompact(wb.tint),
        isDefault = wb.tint == 0f
    )

    // Colour grade — vibrance, the HSL and wheels rollups, skin protect.
    val grade = finishing.grade
    params += PhotoEditParam(
        key = "grade.vibrance", tool = EditorToolIds.PhotoColour,
        labelRes = Res.string.editor_vibrance, snapshot = grade.vibrance,
        display = formatSignedCompact(grade.vibrance * 100f),
        isDefault = grade.vibrance == 0f
    )
    params += PhotoEditParam(
        key = "grade.hsl", tool = EditorToolIds.PhotoColour,
        labelRes = Res.string.editor_colour_hsl, snapshot = grade.hsl,
        display = "✓", isDefault = grade.hslIsNeutral
    )
    params += PhotoEditParam(
        key = "grade.wheels", tool = EditorToolIds.PhotoColour,
        labelRes = Res.string.editor_colour_wheels,
        snapshot = listOf(grade.shadows, grade.midtones, grade.highlights),
        display = "✓", isDefault = grade.wheelsAreNeutral
    )
    params += PhotoEditParam(
        key = "grade.protectSkin", tool = EditorToolIds.PhotoColour,
        labelRes = Res.string.editor_protect_skin, snapshot = grade.protectSkin,
        display = "✓", isDefault = !grade.protectSkin
    )

    // Looks.
    val look = finishing.look
    params += PhotoEditParam(
        key = "finishing.look", tool = EditorToolIds.PhotoLooks,
        labelRes = photoLookLabelRes(look.id),
        titleRes = Res.string.editor_tool_looks,
        snapshot = look, display = formatDecimal(look.amount * 100) + "%",
        isDefault = look.isNeutral
    )

    // Vignette — one parameter per field, matching the panel's checks.
    val vignette = finishing.vignette
    params += PhotoEditParam(
        key = "vignette.amount", tool = EditorToolIds.PhotoVignette,
        labelRes = Res.string.editor_amount, snapshot = vignette.amount,
        display = formatSignedCompact(vignette.amount * 100),
        isDefault = vignette.amount == 0f
    )
    params += PhotoEditParam(
        key = "vignette.midpoint", tool = EditorToolIds.PhotoVignette,
        labelRes = Res.string.editor_midpoint, snapshot = vignette.midpoint,
        display = formatDecimal(vignette.midpoint * 100),
        isDefault = vignette.midpoint == 0.5f
    )
    params += PhotoEditParam(
        key = "vignette.feather", tool = EditorToolIds.PhotoVignette,
        labelRes = Res.string.editor_feather, snapshot = vignette.feather,
        display = formatDecimal(vignette.feather * 100),
        isDefault = vignette.feather == 0.5f
    )
    params += PhotoEditParam(
        key = "vignette.roundness", tool = EditorToolIds.PhotoVignette,
        labelRes = Res.string.editor_roundness, snapshot = vignette.roundness,
        display = formatSignedCompact(vignette.roundness * 100),
        isDefault = vignette.roundness == 0f
    )
    params += PhotoEditParam(
        key = "vignette.highlights", tool = EditorToolIds.PhotoVignette,
        labelRes = Res.string.editor_highlights, snapshot = vignette.highlights,
        display = formatDecimal(vignette.highlights * 100),
        isDefault = vignette.highlights == 0f
    )

    // Grain.
    val grain = finishing.grain
    params += PhotoEditParam(
        key = "grain.amount", tool = EditorToolIds.PhotoGrain,
        labelRes = Res.string.editor_amount, snapshot = grain.amount,
        display = formatDecimal(grain.amount * 100),
        isDefault = grain.amount == 0f
    )
    params += PhotoEditParam(
        key = "grain.size", tool = EditorToolIds.PhotoGrain,
        labelRes = Res.string.editor_grain_size, snapshot = grain.size,
        display = formatDecimal(grain.size * 100),
        isDefault = grain.size == 0.24f
    )
    params += PhotoEditParam(
        key = "grain.roughness", tool = EditorToolIds.PhotoGrain,
        labelRes = Res.string.editor_roughness, snapshot = grain.roughness,
        display = formatDecimal(grain.roughness * 100),
        isDefault = grain.roughness == 0.35f
    )

    // Details.
    val details = spatial.details
    params += PhotoEditParam(
        key = "details.texture", tool = EditorToolIds.PhotoDetail,
        labelRes = Res.string.editor_texture, snapshot = details.texture,
        display = formatSignedCompact(details.texture * 100),
        isDefault = details.texture == 0f
    )
    params += PhotoEditParam(
        key = "details.clarity", tool = EditorToolIds.PhotoDetail,
        labelRes = Res.string.editor_clarity, snapshot = details.clarity,
        display = formatSignedCompact(details.clarity * 100),
        isDefault = details.clarity == 0f
    )
    params += PhotoEditParam(
        key = "details.sharpening", tool = EditorToolIds.PhotoDetail,
        labelRes = Res.string.editor_sharpening, snapshot = details.sharpening,
        display = formatDecimal(details.sharpening * 100),
        isDefault = details.sharpening == 0f
    )
    params += PhotoEditParam(
        key = "details.noiseReduction", tool = EditorToolIds.PhotoDetail,
        labelRes = Res.string.editor_noise_reduction,
        snapshot = details.noiseReduction,
        display = formatDecimal(details.noiseReduction * 100),
        isDefault = details.noiseReduction == 0f
    )

    // Develop — one panel row plus the counted detail parameters.
    val develop = edits.develop
    params += PhotoEditParam(
        key = "develop", tool = EditorToolIds.PhotoDevelop,
        labelRes = Res.string.editor_tool_develop, snapshot = develop,
        display = "✓", isDefault = develop.isNeutral,
        counted = false
    )
    params += developParam("develop.temperature", Res.string.editor_develop_temperature,
        develop.temperatureKelvin, 5500f, "${develop.temperatureKelvin.roundToInt()} K")
    params += developParam("develop.tint", Res.string.editor_develop_tint,
        develop.tint, 0f, formatSignedCompact(develop.tint * 100))
    params += developParam("develop.exposure", Res.string.editor_develop_exposure,
        develop.exposure, 0f, formatSignedCompact(develop.exposure * 100))
    params += developParam("develop.brightness", Res.string.editor_develop_brightness,
        develop.brightness, 0f, formatSignedCompact(develop.brightness * 100))
    params += developParam("develop.contrast", Res.string.editor_develop_contrast,
        develop.contrast, 0f, formatSignedCompact(develop.contrast * 100))
    params += developParam("develop.shadows", Res.string.editor_develop_shadows,
        develop.shadows, 0f, formatSignedCompact(develop.shadows * 100))
    params += developParam("develop.highlights", Res.string.editor_develop_highlights,
        develop.highlights, 0f, formatSignedCompact(develop.highlights * 100))
    params += developParam("develop.sharpen", Res.string.editor_develop_sharpen,
        develop.sharpen, 0f, formatSignedCompact(develop.sharpen * 100))
    params += developParam("develop.saturation", Res.string.editor_develop_saturation,
        develop.saturation, 0f, formatSignedCompact(develop.saturation * 100))
    params += developParam("develop.vibrance", Res.string.editor_develop_vibrance,
        develop.vibrance, 0f, formatSignedCompact(develop.vibrance * 100))

    // Colour tone — one panel row plus the four counted sliders.
    val tone = retouch.tone
    params += PhotoEditParam(
        key = "retouch.tone", tool = EditorToolIds.PhotoColourTone,
        labelRes = Res.string.editor_tool_colour_tone, snapshot = tone,
        display = "✓", isDefault = tone.isNeutral,
        counted = false
    )
    params += toneParam("tone.skin", Res.string.editor_retouch_skin_tone, tone.skin)
    params += toneParam("tone.blue", Res.string.editor_retouch_blue_tone, tone.blue)
    params += toneParam("tone.hue", Res.string.editor_retouch_hue, tone.hue, scale = 180f, unitSuffix = "°")
    params += toneParam("tone.blackWhite", Res.string.editor_retouch_black_white, tone.blackWhite)

    // Effects.
    val effects = retouch.effects
    params += PhotoEditParam(
        key = "effects.posterize", tool = EditorToolIds.PhotoPosterize,
        labelRes = Res.string.editor_tool_posterize, snapshot = effects.posterize,
        display = formatDecimal(effects.posterize * 100),
        isDefault = effects.posterize == 0f
    )
    params += PhotoEditParam(
        key = "effects.edges", tool = EditorToolIds.PhotoEdges,
        labelRes = Res.string.editor_tool_edges, snapshot = effects.edges,
        display = formatDecimal(effects.edges * 100),
        isDefault = effects.edges == 0f
    )
    params += PhotoEditParam(
        key = "effects.borderWidth", tool = EditorToolIds.PhotoBorders,
        labelRes = Res.string.editor_tool_borders, snapshot = effects.borderWidth,
        display = formatDecimal(effects.borderWidth * 100),
        isDefault = effects.borderWidth == 0f
    )

    // Markup and background removal.
    val markup = retouch.markup
    params += PhotoEditParam(
        key = "retouch.markup", tool = EditorToolIds.PhotoMarkup,
        labelRes = Res.string.editor_tool_markup, snapshot = markup,
        display = (markup.strokes.size + markup.texts.size).toString(),
        isDefault = markup.isEmpty
    )
    params += PhotoEditParam(
        key = "retouch.matte", tool = EditorToolIds.PhotoCutout,
        labelRes = Res.string.editor_tool_remove_background,
        snapshot = retouch.matte, display = "✓",
        isDefault = retouch.matte == null
    )

    // Lens blur — mode plus the five controls the panel lists.
    val lensBlur = spatial.lensBlur
    if (!lensBlur.isNeutral) {
        params += PhotoEditParam(
            key = "lensBlur.mode", tool = EditorToolIds.PhotoLensBlur,
            labelRes = Res.string.editor_tool_lens_blur,
            displayRes = if (lensBlur.mode == LensBlurMode.Linear) {
                Res.string.editor_blur_linear
            } else {
                Res.string.editor_blur_radial
            },
            snapshot = lensBlur.mode, display = "",
            isDefault = false
        )
        params += PhotoEditParam(
            key = "lensBlur.strength", tool = EditorToolIds.PhotoLensBlur,
            labelRes = Res.string.editor_strength, snapshot = lensBlur.strength,
            display = formatDecimal(lensBlur.strength * 100),
            isDefault = false
        )
        params += PhotoEditParam(
            key = "lensBlur.transition", tool = EditorToolIds.PhotoLensBlur,
            labelRes = Res.string.editor_transition,
            snapshot = lensBlur.transition,
            display = formatDecimal(lensBlur.transition * 100),
            isDefault = lensBlur.transition == 0.6f
        )
        params += PhotoEditParam(
            key = "lensBlur.highlightBokeh", tool = EditorToolIds.PhotoLensBlur,
            labelRes = Res.string.editor_highlight_bokeh,
            snapshot = lensBlur.highlightBokeh,
            display = formatDecimal(lensBlur.highlightBokeh * 100),
            isDefault = lensBlur.highlightBokeh == 0.12f
        )
        params += PhotoEditParam(
            key = "lensBlur.center", tool = EditorToolIds.PhotoLensBlur,
            labelRes = Res.string.editor_blur_centre,
            snapshot = lensBlur.centerX to lensBlur.centerY,
            display = formatDecimal(lensBlur.centerX * 100) +
                " · " + formatDecimal(lensBlur.centerY * 100),
            isDefault = lensBlur.centerX == 0.5f && lensBlur.centerY == 0.5f
        )
        if (lensBlur.mode == LensBlurMode.Linear) {
            params += PhotoEditParam(
                key = "lensBlur.angle", tool = EditorToolIds.PhotoLensBlur,
                labelRes = Res.string.editor_angle,
                snapshot = lensBlur.angleDegrees,
                display = formatDegrees(lensBlur.angleDegrees),
                isDefault = lensBlur.angleDegrees == 0f
            )
        }
    }

    // Crop & geometry.
    val crop = geometry.crop
    params += PhotoEditParam(
        key = "geometry.crop", tool = EditorToolIds.PhotoCrop,
        labelRes = Res.string.editor_tool_crop, snapshot = crop,
        display = cropDisplay(geometry, sourceSize),
        isDefault = crop == NormalizedCrop()
    )
    val turns = geometry.quarterTurns.mod(4)
    params += PhotoEditParam(
        key = "geometry.rotation", tool = EditorToolIds.PhotoCrop,
        labelRes = Res.string.editor_rotation, snapshot = geometry.quarterTurns,
        display = "${turns * 90}°",
        isDefault = turns == 0
    )
    params += PhotoEditParam(
        key = "geometry.flipH", tool = EditorToolIds.PhotoCrop,
        labelRes = Res.string.editor_flip_horizontal,
        snapshot = geometry.flipHorizontal, display = "✓",
        isDefault = !geometry.flipHorizontal
    )
    params += PhotoEditParam(
        key = "geometry.flipV", tool = EditorToolIds.PhotoCrop,
        labelRes = Res.string.editor_flip_vertical,
        snapshot = geometry.flipVertical, display = "✓",
        isDefault = !geometry.flipVertical
    )
    val warp = geometry.warp
    params += PhotoEditParam(
        key = "geometry.straighten", tool = EditorToolIds.PhotoCrop,
        labelRes = Res.string.editor_straighten, snapshot = warp.rotationDegrees,
        display = formatDegrees(warp.rotationDegrees),
        isDefault = warp.rotationDegrees == 0f
    )
    params += PhotoEditParam(
        key = "geometry.perspectiveV", tool = EditorToolIds.PhotoPerspective,
        labelRes = Res.string.editor_perspective_vertical,
        snapshot = warp.verticalPerspective,
        display = formatSignedCompact(warp.verticalPerspective),
        isDefault = warp.verticalPerspective == 0f
    )
    params += PhotoEditParam(
        key = "geometry.perspectiveH", tool = EditorToolIds.PhotoPerspective,
        labelRes = Res.string.editor_perspective_horizontal,
        snapshot = warp.horizontalPerspective,
        display = formatSignedCompact(warp.horizontalPerspective),
        isDefault = warp.horizontalPerspective == 0f
    )
    params += PhotoEditParam(
        key = "geometry.perspectiveScale", tool = EditorToolIds.PhotoPerspective,
        labelRes = Res.string.editor_perspective_scale, snapshot = warp.scale,
        display = "${(warp.scale * 100f).roundToInt()}%",
        isDefault = warp.scale == 1f
    )

    // Compose tools — one counted parameter per layer scope.
    for (layer in edits.layers) {
        val scope = layer.name
        params += PhotoEditParam(
            key = "layer.${layer.id}.transform", tool = EditorToolIds.PhotoTransform,
            labelRes = Res.string.editor_tool_transform_layer,
            scopeText = scope, snapshot = layer.transform,
            display = "✓", isDefault = layer.transform == PhotoLayerTransform()
        )
        params += PhotoEditParam(
            key = "layer.${layer.id}.blend", tool = EditorToolIds.PhotoBlend,
            labelRes = Res.string.editor_tool_blend,
            scopeText = scope,
            snapshot = listOf(layer.blendMode, layer.opacity, layer.clipToBelow),
            display = "✓",
            isDefault = layer.blendMode == PhotoBlendMode.Normal &&
                layer.opacity == 1f && !layer.clipToBelow
        )
        params += PhotoEditParam(
            key = "layer.${layer.id}.mask", tool = EditorToolIds.PhotoLayerMask,
            labelRes = Res.string.editor_tool_mask,
            scopeText = scope, snapshot = layer.mask,
            display = "✓", isDefault = layer.mask == PhotoLayerMask()
        )
        params += PhotoEditParam(
            key = "layer.${layer.id}.doubleExposure",
            tool = EditorToolIds.PhotoDoubleExposure,
            labelRes = Res.string.editor_tool_double_exposure,
            scopeText = scope,
            snapshot = listOf(layer.blendMode, layer.opacity),
            display = "✓",
            isDefault = layer.blendMode == PhotoBlendMode.Normal &&
                layer.opacity == 1f
        )
    }

    return params
}

private fun cropDisplay(
    geometry: PhotoGeometry,
    sourceSize: PixelSize?
): String {
    val size = sourceSize ?: return "✓"
    // The panel's true output frame; without a source size only the
    // neutral-crop fallback remains.
    val output = PhotoMapping(size, 1, geometry).outputSize
    return "${output.width} × ${output.height}"
}

private fun developParam(
    key: String,
    labelRes: StringResource,
    value: Float,
    neutral: Float,
    display: String
) = PhotoEditParam(
    key = key, tool = EditorToolIds.PhotoDevelop, labelRes = labelRes,
    snapshot = value, display = display, isDefault = value == neutral
)

private fun toneParam(
    key: String,
    labelRes: StringResource,
    fraction: Float,
    scale: Float = 100f,
    unitSuffix: String = ""
) = PhotoEditParam(
    key = key, tool = EditorToolIds.PhotoColourTone, labelRes = labelRes,
    snapshot = fraction,
    display = formatSignedCompact(fraction * scale) + unitSuffix,
    isDefault = fraction == 0f
)

/** The resolved "Label value" text for one parameter. */
@Composable
private fun PhotoEditParam.resolvedText(): String {
    val value = displayRes?.let { stringResource(it) }
        ?: summary ?: display
    val name = resolvedTitle()
    return if (value.isEmpty()) name else "$name $value"
}

/** The "Scope · Title · Label" text without a value — for removed rows. */
@Composable
private fun PhotoEditParam.resolvedTitle(): String {
    val title = titleRes?.let { stringResource(it) }
    val label = stringResource(labelRes)
    return listOfNotNull(scopeText, title, label).joinToString(" · ")
}

/**
 * How many of [tool]'s parameters differ from their defaults — the
 * Studio section header's "N edits" badge.
 */
fun photoToolEditCount(tool: EditorToolId, edits: PhotoEdits): Int =
    photoEditParams(edits).count { it.tool == tool && it.counted && !it.isDefault }

/**
 * The collapsed section's one-line summary — up to two named edits then
 * "N more", or null when the tool has none. Values use the tools' own
 * display units ("Saturation +10", "Contrast +40").
 */
@Composable
fun photoToolEditSummary(
    tool: EditorToolId,
    edits: PhotoEdits
): String? {
    val changed = photoEditParams(edits).filter {
        it.tool == tool && it.counted && !it.isDefault
    }
    if (changed.isEmpty()) return null
    val named = StringBuilder()
    for (param in changed.take(2)) {
        if (named.isNotEmpty()) named.append(" · ")
        named.append(param.resolvedText())
    }
    val rest = changed.size - 2
    if (rest > 0) {
        named.append(" · ")
            .append(stringResource(Res.string.editor_studio_edits_more, rest))
    }
    return named.toString()
}

/**
 * The History row's subtitle — the same "Label value · … · N more"
 * format over the fields that changed between two recipes. Parameters
 * that disappeared entirely (a removed layer) report as removed.
 */
@Composable
fun photoEditsStepSummary(before: PhotoEdits, after: PhotoEdits): String? {
    val beforeParams = photoEditParams(before).associateBy { it.key }
    val afterParams = photoEditParams(after)
    val afterKeys = afterParams.mapTo(HashSet()) { it.key }
    val changed = afterParams.filter {
        beforeParams[it.key]?.snapshot != it.snapshot
    }
    val removed = beforeParams.values.filter {
        it.key !in afterKeys && !it.isDefault
    }
    if (changed.isEmpty() && removed.isEmpty()) return null
    val total = changed.size + removed.size
    val named = StringBuilder()
    var shown = 0
    for (param in changed) {
        if (shown == 2) break
        if (named.isNotEmpty()) named.append(" · ")
        named.append(param.resolvedText())
        shown++
    }
    for (param in removed) {
        if (shown == 2) break
        if (named.isNotEmpty()) named.append(" · ")
        named.append(param.resolvedTitle())
            .append(' ')
            .append(stringResource(Res.string.editor_studio_edit_removed))
        shown++
    }
    val rest = total - shown
    if (rest > 0) {
        named.append(" · ")
            .append(stringResource(Res.string.editor_studio_edits_more, rest))
    }
    return named.toString()
}
