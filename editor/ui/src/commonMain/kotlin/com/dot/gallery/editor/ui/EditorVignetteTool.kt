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
import com.dot.gallery.editor.core.model.PhotoEdits
import com.dot.gallery.editor.core.model.PhotoVignette
import com.dot.gallery.editor.core.model.toEdits
import com.dot.gallery.editor.render.photo.PhotoEditorState
import com.dot.gallery.editor.ui.generated.resources.Res
import com.dot.gallery.editor.ui.generated.resources.editor_amount
import com.dot.gallery.editor.ui.generated.resources.editor_feather
import com.dot.gallery.editor.ui.generated.resources.editor_highlights
import com.dot.gallery.editor.ui.generated.resources.editor_midpoint
import com.dot.gallery.editor.ui.generated.resources.editor_roundness
import org.jetbrains.compose.resources.stringResource

internal const val TAG_CATEGORY_VIGNETTE = "editor.category.vignette"

private val VignetteParameters = listOf(
    FinishingParam(
        key = "vignetteAmount",
        labelRes = Res.string.editor_amount,
        displayRange = -100f..100f,
        step = 1f,
        neutral = 0f,
        read = { it.finishing.vignette.amount * 100f },
        write = { e, v -> e.vignette { it.copy(amount = v / 100f) } }
    ),
    FinishingParam(
        key = "vignetteMidpoint",
        labelRes = Res.string.editor_midpoint,
        displayRange = 0f..100f,
        step = 1f,
        neutral = 50f,
        signed = false,
        read = { it.finishing.vignette.midpoint * 100f },
        write = { e, v -> e.vignette { it.copy(midpoint = v / 100f) } }
    ),
    FinishingParam(
        key = "vignetteFeather",
        labelRes = Res.string.editor_feather,
        displayRange = 1f..100f,
        step = 1f,
        neutral = 50f,
        signed = false,
        read = { it.finishing.vignette.feather * 100f },
        write = { e, v -> e.vignette { it.copy(feather = v / 100f) } }
    ),
    FinishingParam(
        key = "vignetteRoundness",
        labelRes = Res.string.editor_roundness,
        displayRange = -100f..100f,
        step = 1f,
        neutral = 0f,
        read = { it.finishing.vignette.roundness * 100f },
        write = { e, v -> e.vignette { it.copy(roundness = v / 100f) } }
    ),
    FinishingParam(
        key = "vignetteHighlights",
        labelRes = Res.string.editor_highlights,
        displayRange = 0f..100f,
        step = 1f,
        neutral = 0f,
        signed = false,
        read = { it.finishing.vignette.highlights * 100f },
        write = { e, v -> e.vignette { it.copy(highlights = v / 100f) } }
    )
)

private inline fun PhotoEdits.vignette(
    update: (PhotoVignette) -> PhotoVignette
): PhotoEdits = copy(
    finishing = finishing.copy(vignette = update(finishing.vignette))
)

/**
 * The Vignette tool per the approved `p-vignette-inline` frame: the five
 * parameter tabs (Amount selected by default), the shared precision
 * ruler for the active tab and the settings rows that reflect every
 * field — tapping a row selects its tab.
 */
@Composable
internal fun VignetteToolContent(
    state: PhotoEditorState,
    actions: PhotoEditorActions,
    modifier: Modifier = Modifier
) {
    val labels = VignetteParameters.map { stringResource(it.labelRes) }
    val sourceRef = state.info?.reference
    var activeIndex by rememberSaveable(sourceRef) { mutableIntStateOf(0) }
    val index = activeIndex.coerceIn(VignetteParameters.indices)
    val edits = state.document?.toEdits() ?: PhotoEdits()
    val enabled = state.document != null && !state.opening
    val latestActions by rememberUpdatedState(actions)

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
            VignetteParameters.forEachIndexed { i, param ->
                FinishingChip(
                    label = labels[i],
                    selected = i == index,
                    enabled = enabled,
                    tag = "editor.vignette.tab.${param.key}",
                    onClick = {
                        if (i != activeIndex) {
                            latestActions.cancelEdit()
                            activeIndex = i
                        }
                    }
                )
            }
        }
    }, controls = {
        val active = VignetteParameters[index]
        FinishingParamControls(
            state = state,
            actions = actions,
            param = active,
            label = labels[index],
            enabled = enabled,
            identity = sourceRef to active.key
        )
    }, details = {
        VignetteParameters.forEachIndexed { i, param ->
            FinishingSettingsRow(
                label = labels[i],
                value = param.displayOf(edits),
                selected = i == index,
                enabled = enabled,
                tag = "editor.vignette.row.${param.key}",
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
