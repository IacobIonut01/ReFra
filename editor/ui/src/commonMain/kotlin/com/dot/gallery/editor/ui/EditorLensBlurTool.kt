package com.dot.gallery.editor.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.dot.gallery.editor.core.model.LensBlurMode
import com.dot.gallery.editor.core.model.PhotoEdits
import com.dot.gallery.editor.core.model.PhotoLensBlur
import com.dot.gallery.editor.core.model.toEdits
import com.dot.gallery.editor.render.photo.PhotoEditorState
import com.dot.gallery.editor.ui.generated.resources.Res
import com.dot.gallery.editor.ui.generated.resources.editor_angle
import com.dot.gallery.editor.ui.generated.resources.editor_blur_linear
import com.dot.gallery.editor.ui.generated.resources.editor_blur_radial
import com.dot.gallery.editor.ui.generated.resources.editor_highlight_bokeh
import com.dot.gallery.editor.ui.generated.resources.editor_strength
import com.dot.gallery.editor.ui.generated.resources.editor_transition
import org.jetbrains.compose.resources.stringResource

internal const val TAG_CATEGORY_LENS_BLUR = "editor.category.lensBlur"

private val LensBlurParameters = listOf(
    FinishingParam(
        key = "lensBlurStrength",
        labelRes = Res.string.editor_strength,
        displayRange = 0f..100f,
        step = 1f,
        neutral = 0f,
        signed = false,
        read = { it.spatial.lensBlur.strength * 100f },
        write = { e, v -> e.lensBlur { it.copy(strength = v / 100f) } }
    ),
    FinishingParam(
        key = "lensBlurTransition",
        labelRes = Res.string.editor_transition,
        displayRange = 5f..100f,
        step = 1f,
        neutral = 60f,
        signed = false,
        read = { it.spatial.lensBlur.transition * 100f },
        write = { e, v -> e.lensBlur { it.copy(transition = v / 100f) } }
    ),
    FinishingParam(
        key = "lensBlurHighlightBokeh",
        labelRes = Res.string.editor_highlight_bokeh,
        displayRange = 0f..100f,
        step = 1f,
        neutral = 12f,
        signed = false,
        read = { it.spatial.lensBlur.highlightBokeh * 100f },
        write = { e, v -> e.lensBlur { it.copy(highlightBokeh = v / 100f) } }
    ),
    FinishingParam(
        key = "lensBlurAngle",
        labelRes = Res.string.editor_angle,
        displayRange = -180f..180f,
        step = 1f,
        neutral = 0f,
        read = { it.spatial.lensBlur.angleDegrees },
        write = { e, v -> e.lensBlur { it.copy(angleDegrees = v) } }
    )
)

/** Strength, Transition and Highlight bokeh apply in both modes — the
 * Angle row joins them only while the band is Linear (the model
 * ignores it under Radial). */
private fun lensBlurParamsFor(mode: LensBlurMode): List<FinishingParam> =
    if (mode == LensBlurMode.Linear) LensBlurParameters
    else LensBlurParameters.dropLast(1)

private inline fun PhotoEdits.lensBlur(
    update: (PhotoLensBlur) -> PhotoLensBlur
): PhotoEdits = copy(
    spatial = spatial.copy(lensBlur = update(spatial.lensBlur))
)

/**
 * The Lens blur tool per the approved `p-blur-inline` frame: the
 * Radial/Linear mode chips, the shared precision ruler (Strength
 * selected by default) and the settings rows that reflect every field
 * — Angle appears only under Linear. The focus centre lives on the
 * canvas overlay, not in a settings row.
 */
@Composable
internal fun LensBlurToolContent(
    state: PhotoEditorState,
    actions: PhotoEditorActions,
    modifier: Modifier = Modifier
) {
    val sourceRef = state.info?.reference
    val edits = state.document?.toEdits() ?: PhotoEdits()
    val lensBlur = edits.spatial.lensBlur
    val mode = lensBlur.mode
    val params = lensBlurParamsFor(mode)
    val paramLabels = LensBlurParameters.map { stringResource(it.labelRes) }
    var activeIndex by rememberSaveable(sourceRef) { mutableIntStateOf(0) }
    val index = activeIndex.coerceIn(params.indices)
    val enabled = state.document != null && !state.opening
    val latestState by rememberUpdatedState(state)
    val latestActions by rememberUpdatedState(actions)
    val modes = listOf(
        Triple(LensBlurMode.Radial,
            stringResource(Res.string.editor_blur_radial), "radial"),
        Triple(LensBlurMode.Linear,
            stringResource(Res.string.editor_blur_linear), "linear")
    )

    PhotoParameterControls(modifier = modifier, tabs = {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            modes.forEach { (m, label, key) ->
                FinishingChip(
                    label = label,
                    selected = mode == m,
                    enabled = enabled && !state.interactionActive,
                    tag = "editor.lensBlur.mode.$key",
                    onClick = {
                        if (mode != m) {
                            val current = latestState.document?.toEdits()
                                ?: return@FinishingChip
                            latestActions.applyEdits(
                                label, current.lensBlur { it.copy(mode = m) }
                            )
                        }
                    }
                )
            }
        }
    }, controls = {
        val active = params[index]
        val activeLabel = paramLabels[LensBlurParameters.indexOf(active)]
        FinishingParamControls(
            state = state,
            actions = actions,
            param = active,
            label = activeLabel,
            enabled = enabled,
            identity = sourceRef to active.key
        )
    }, details = {
        params.forEachIndexed { i, param ->
            FinishingSettingsRow(
                label = paramLabels[LensBlurParameters.indexOf(param)],
                value = param.displayOf(edits),
                selected = i == index,
                enabled = enabled,
                tag = "editor.lensBlur.row.${param.key}",
                onClick = {
                    if (i != activeIndex) {
                        latestActions.cancelEdit()
                        activeIndex = i
                    }
                }
            )
        }
    })
}
