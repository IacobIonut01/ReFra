package com.dot.gallery.editor.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.dot.gallery.editor.core.model.ColorAdjustments
import com.dot.gallery.editor.core.model.LensBlurMode
import com.dot.gallery.editor.core.model.NormalizedCrop
import com.dot.gallery.editor.core.model.PhotoGeometry
import com.dot.gallery.editor.core.model.PhotoMapping
import com.dot.gallery.editor.render.photo.PhotoEditorState
import com.dot.gallery.editor.ui.generated.resources.Res
import com.dot.gallery.editor.ui.generated.resources.editor_angle
import com.dot.gallery.editor.ui.generated.resources.editor_blacks
import com.dot.gallery.editor.ui.generated.resources.editor_blur_centre
import com.dot.gallery.editor.ui.generated.resources.editor_blur_linear
import com.dot.gallery.editor.ui.generated.resources.editor_blur_radial
import com.dot.gallery.editor.ui.generated.resources.editor_clarity
import com.dot.gallery.editor.ui.generated.resources.editor_contrast
import com.dot.gallery.editor.ui.generated.resources.editor_amount
import com.dot.gallery.editor.ui.generated.resources.editor_channel_blue
import com.dot.gallery.editor.ui.generated.resources.editor_channel_green
import com.dot.gallery.editor.ui.generated.resources.editor_channel_red
import com.dot.gallery.editor.ui.generated.resources.editor_channel_rgb
import com.dot.gallery.editor.ui.generated.resources.editor_exposure
import com.dot.gallery.editor.ui.generated.resources.editor_feather
import com.dot.gallery.editor.ui.generated.resources.editor_flip_horizontal
import com.dot.gallery.editor.ui.generated.resources.editor_flip_vertical
import com.dot.gallery.editor.ui.generated.resources.editor_grain_size
import com.dot.gallery.editor.ui.generated.resources.editor_highlight_bokeh
import com.dot.gallery.editor.ui.generated.resources.editor_highlights
import com.dot.gallery.editor.ui.generated.resources.editor_midpoint
import com.dot.gallery.editor.ui.generated.resources.editor_no_edits
import com.dot.gallery.editor.ui.generated.resources.editor_noise_reduction
import com.dot.gallery.editor.ui.generated.resources.editor_output_info
import com.dot.gallery.editor.ui.generated.resources.editor_perspective_horizontal
import com.dot.gallery.editor.ui.generated.resources.editor_perspective_scale
import com.dot.gallery.editor.ui.generated.resources.editor_perspective_vertical
import com.dot.gallery.editor.ui.generated.resources.editor_reset_edits
import com.dot.gallery.editor.ui.generated.resources.editor_rotation
import com.dot.gallery.editor.ui.generated.resources.editor_roughness
import com.dot.gallery.editor.ui.generated.resources.editor_roundness
import com.dot.gallery.editor.ui.generated.resources.editor_saturation
import com.dot.gallery.editor.ui.generated.resources.editor_shadows
import com.dot.gallery.editor.ui.generated.resources.editor_sharpening
import com.dot.gallery.editor.ui.generated.resources.editor_strength
import com.dot.gallery.editor.ui.generated.resources.editor_straighten
import com.dot.gallery.editor.ui.generated.resources.editor_colour_hsl
import com.dot.gallery.editor.ui.generated.resources.editor_colour_wheels
import com.dot.gallery.editor.ui.generated.resources.editor_temperature
import com.dot.gallery.editor.ui.generated.resources.editor_vibrance
import com.dot.gallery.editor.ui.generated.resources.editor_texture
import com.dot.gallery.editor.ui.generated.resources.editor_tint
import com.dot.gallery.editor.ui.generated.resources.editor_tool_crop
import com.dot.gallery.editor.ui.generated.resources.editor_tool_curves
import com.dot.gallery.editor.ui.generated.resources.editor_tool_borders
import com.dot.gallery.editor.ui.generated.resources.editor_tool_colour_tone
import com.dot.gallery.editor.ui.generated.resources.editor_tool_develop
import com.dot.gallery.editor.ui.generated.resources.editor_tool_edges
import com.dot.gallery.editor.ui.generated.resources.editor_tool_lens_blur
import com.dot.gallery.editor.ui.generated.resources.editor_tool_markup
import com.dot.gallery.editor.ui.generated.resources.editor_tool_posterize
import com.dot.gallery.editor.ui.generated.resources.editor_tool_remove_background
import com.dot.gallery.editor.ui.generated.resources.editor_tool_looks
import com.dot.gallery.editor.ui.generated.resources.editor_transition
import com.dot.gallery.editor.ui.generated.resources.editor_whites
import org.jetbrains.compose.resources.stringResource
import kotlin.math.roundToInt

/**
 * The Edits pane — every committed recipe value, plainly listed, plus the
 * one-step Reset. Read-only rows: a summary of the current recipe, not a
 * fabricated history stack.
 */
@Composable
internal fun PhotoEditsPanel(
    state: PhotoEditorState,
    actions: PhotoEditorActions,
    modifier: Modifier = Modifier
) {
    val doc = state.document
    val hasEdits = doc != null &&
        (doc.adjustments != ColorAdjustments() ||
            doc.geometry != PhotoGeometry() ||
            !doc.spatial.isNeutral ||
            !doc.finishing.isNeutral)
    Column(modifier = modifier.fillMaxWidth().padding(16.dp)) {
        if (!hasEdits) {
            Text(
                text = stringResource(Res.string.editor_no_edits),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            val adj = doc!!.adjustments
            if (adj.exposureStops != 0f) {
                EditRow(
                    label = stringResource(Res.string.editor_exposure),
                    value = formatSignedDecimal(adj.exposureStops) + " EV"
                )
            }
            if (adj.contrast != 1f) {
                EditRow(
                    label = stringResource(Res.string.editor_contrast),
                    value = formatDecimal(adj.contrast) + "×"
                )
            }
            if (adj.saturation != 1f) {
                EditRow(
                    label = stringResource(Res.string.editor_saturation),
                    value = formatDecimal(adj.saturation) + "×"
                )
            }
            if (adj.highlights != 0f) {
                EditRow(
                    label = stringResource(Res.string.editor_highlights),
                    value = formatSignedCompact(adj.highlights * 100)
                )
            }
            if (adj.shadows != 0f) {
                EditRow(
                    label = stringResource(Res.string.editor_shadows),
                    value = formatSignedCompact(adj.shadows * 100)
                )
            }
            if (adj.whites != 0f) {
                EditRow(
                    label = stringResource(Res.string.editor_whites),
                    value = formatSignedCompact(adj.whites * 100)
                )
            }
            if (adj.blacks != 0f) {
                EditRow(
                    label = stringResource(Res.string.editor_blacks),
                    value = formatSignedCompact(adj.blacks * 100)
                )
            }
            val finishing = doc.finishing
            val curves = finishing.curves
            val curveChannels = listOf(
                Res.string.editor_channel_rgb to curves.master,
                Res.string.editor_channel_red to curves.red,
                Res.string.editor_channel_green to curves.green,
                Res.string.editor_channel_blue to curves.blue
            )
            for ((channelRes, channelCurve) in curveChannels) {
                if (!channelCurve.isNeutral) {
                    EditRow(
                        label = stringResource(Res.string.editor_tool_curves) +
                            " · " + stringResource(channelRes),
                        value = "✓"
                    )
                }
            }
            val wb = finishing.whiteBalance
            if (wb.temperatureKelvin != 6500f) {
                EditRow(
                    label = stringResource(Res.string.editor_temperature),
                    value = "${wb.temperatureKelvin.roundToInt()} K"
                )
            }
            if (wb.tint != 0f) {
                EditRow(
                    label = stringResource(Res.string.editor_tint),
                    value = formatSignedCompact(wb.tint)
                )
            }
            val grade = finishing.grade
            if (grade.vibrance != 0f) {
                EditRow(
                    label = stringResource(Res.string.editor_vibrance),
                    value = formatSignedCompact(grade.vibrance * 100f)
                )
            }
            if (!grade.hslIsNeutral) {
                EditRow(
                    label = stringResource(Res.string.editor_colour_hsl),
                    value = "✓"
                )
            }
            if (!grade.wheelsAreNeutral) {
                EditRow(
                    label = stringResource(Res.string.editor_colour_wheels),
                    value = "✓"
                )
            }
            val look = finishing.look
            if (!look.isNeutral) {
                EditRow(
                    label = stringResource(Res.string.editor_tool_looks) +
                        " · " + stringResource(photoLookLabelRes(look.id)),
                    value = formatDecimal(look.amount * 100) + "%"
                )
            }
            val vignette = finishing.vignette
            if (vignette.amount != 0f) {
                EditRow(
                    label = stringResource(Res.string.editor_amount),
                    value = formatSignedCompact(vignette.amount * 100)
                )
            }
            if (vignette.midpoint != 0.5f) {
                EditRow(
                    label = stringResource(Res.string.editor_midpoint),
                    value = formatDecimal(vignette.midpoint * 100)
                )
            }
            if (vignette.feather != 0.5f) {
                EditRow(
                    label = stringResource(Res.string.editor_feather),
                    value = formatDecimal(vignette.feather * 100)
                )
            }
            if (vignette.roundness != 0f) {
                EditRow(
                    label = stringResource(Res.string.editor_roundness),
                    value = formatSignedCompact(vignette.roundness * 100)
                )
            }
            if (vignette.highlights != 0f) {
                EditRow(
                    label = stringResource(Res.string.editor_highlights),
                    value = formatDecimal(vignette.highlights * 100)
                )
            }
            val grain = finishing.grain
            if (grain.amount != 0f) {
                EditRow(
                    label = stringResource(Res.string.editor_amount),
                    value = formatDecimal(grain.amount * 100)
                )
            }
            if (grain.size != 0.24f) {
                EditRow(
                    label = stringResource(Res.string.editor_grain_size),
                    value = formatDecimal(grain.size * 100)
                )
            }
            if (grain.roughness != 0.35f) {
                EditRow(
                    label = stringResource(Res.string.editor_roughness),
                    value = formatDecimal(grain.roughness * 100)
                )
            }
            val details = doc.spatial.details
            if (details.texture != 0f) {
                EditRow(
                    label = stringResource(Res.string.editor_texture),
                    value = formatSignedCompact(details.texture * 100)
                )
            }
            if (details.clarity != 0f) {
                EditRow(
                    label = stringResource(Res.string.editor_clarity),
                    value = formatSignedCompact(details.clarity * 100)
                )
            }
            if (details.sharpening != 0f) {
                EditRow(
                    label = stringResource(Res.string.editor_sharpening),
                    value = formatDecimal(details.sharpening * 100)
                )
            }
            if (details.noiseReduction != 0f) {
                EditRow(
                    label = stringResource(
                        Res.string.editor_noise_reduction
                    ),
                    value = formatDecimal(details.noiseReduction * 100)
                )
            }
            if (state.info?.isRaw == true && !doc.develop.isNeutral) {
                EditRow(
                    label = stringResource(Res.string.editor_tool_develop),
                    value = "\u2713"
                )
            }
            val retouch = doc.retouch
            if (!retouch.tone.isNeutral) {
                EditRow(
                    label = stringResource(Res.string.editor_tool_colour_tone),
                    value = "\u2713"
                )
            }
            if (retouch.effects.posterize != 0f) {
                EditRow(
                    label = stringResource(Res.string.editor_tool_posterize),
                    value = formatDecimal(retouch.effects.posterize * 100)
                )
            }
            if (retouch.effects.edges != 0f) {
                EditRow(
                    label = stringResource(Res.string.editor_tool_edges),
                    value = formatDecimal(retouch.effects.edges * 100)
                )
            }
            if (retouch.effects.borderWidth != 0f) {
                EditRow(
                    label = stringResource(Res.string.editor_tool_borders),
                    value = formatDecimal(retouch.effects.borderWidth * 100)
                )
            }
            if (!retouch.markup.isEmpty) {
                EditRow(
                    label = stringResource(Res.string.editor_tool_markup),
                    value = (retouch.markup.strokes.size + retouch.markup.texts.size).toString()
                )
            }
            if (retouch.matte != null) {
                EditRow(
                    label = stringResource(Res.string.editor_tool_remove_background),
                    value = "\u2713"
                )
            }
            val lensBlur = doc.spatial.lensBlur
            if (!lensBlur.isNeutral) {
                EditRow(
                    label = stringResource(Res.string.editor_tool_lens_blur),
                    value = stringResource(
                        if (lensBlur.mode == LensBlurMode.Linear) {
                            Res.string.editor_blur_linear
                        } else {
                            Res.string.editor_blur_radial
                        }
                    )
                )
                EditRow(
                    label = stringResource(Res.string.editor_strength),
                    value = formatDecimal(lensBlur.strength * 100)
                )
                if (lensBlur.transition != 0.6f) {
                    EditRow(
                        label = stringResource(
                            Res.string.editor_transition
                        ),
                        value = formatDecimal(lensBlur.transition * 100)
                    )
                }
                if (lensBlur.highlightBokeh != 0.12f) {
                    EditRow(
                        label = stringResource(
                            Res.string.editor_highlight_bokeh
                        ),
                        value = formatDecimal(lensBlur.highlightBokeh * 100)
                    )
                }
                if (lensBlur.centerX != 0.5f || lensBlur.centerY != 0.5f) {
                    EditRow(
                        label = stringResource(
                            Res.string.editor_blur_centre
                        ),
                        value = formatDecimal(lensBlur.centerX * 100) +
                            " · " + formatDecimal(lensBlur.centerY * 100)
                    )
                }
                if (lensBlur.mode == LensBlurMode.Linear &&
                    lensBlur.angleDegrees != 0f
                ) {
                    EditRow(
                        label = stringResource(Res.string.editor_angle),
                        value = formatDegrees(lensBlur.angleDegrees)
                    )
                }
            }
            val geometry = doc.geometry
            if (geometry.crop != NormalizedCrop()) {
                // Report the true output frame, not a guessed aspect name.
                val output =
                    PhotoMapping(doc.source.size, 1, geometry).outputSize
                EditRow(
                    label = stringResource(Res.string.editor_tool_crop),
                    value = "${output.width} × ${output.height}"
                )
            }
            val turns = geometry.quarterTurns.mod(4)
            if (turns != 0) {
                EditRow(
                    label = stringResource(Res.string.editor_rotation),
                    value = "${turns * 90}°"
                )
            }
            if (geometry.flipHorizontal) {
                EditRow(
                    label = stringResource(Res.string.editor_flip_horizontal),
                    value = "✓"
                )
            }
            if (geometry.flipVertical) {
                EditRow(
                    label = stringResource(Res.string.editor_flip_vertical),
                    value = "✓"
                )
            }
            val warp = geometry.warp
            if (warp.rotationDegrees != 0f) {
                EditRow(
                    label = stringResource(Res.string.editor_straighten),
                    value = formatDegrees(warp.rotationDegrees)
                )
            }
            if (warp.verticalPerspective != 0f) {
                EditRow(
                    label = stringResource(
                        Res.string.editor_perspective_vertical
                    ),
                    value = formatSignedCompact(warp.verticalPerspective)
                )
            }
            if (warp.horizontalPerspective != 0f) {
                EditRow(
                    label = stringResource(
                        Res.string.editor_perspective_horizontal
                    ),
                    value = formatSignedCompact(warp.horizontalPerspective)
                )
            }
            if (warp.scale != 1f) {
                EditRow(
                    label = stringResource(
                        Res.string.editor_perspective_scale
                    ),
                    value = "${(warp.scale * 100f).roundToInt()}%"
                )
            }
            TextButton(
                onClick = actions.reset,
                enabled = !state.interactionActive,
                modifier = Modifier.align(Alignment.End)
            ) {
                Text(stringResource(Res.string.editor_reset_edits))
            }
        }
    }
}

@Composable
private fun EditRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}
