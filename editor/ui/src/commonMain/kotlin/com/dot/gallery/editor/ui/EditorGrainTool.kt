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
import com.dot.gallery.editor.core.model.PhotoGrain
import com.dot.gallery.editor.core.model.toEdits
import com.dot.gallery.editor.render.photo.PhotoEditorState
import com.dot.gallery.editor.ui.generated.resources.Res
import com.dot.gallery.editor.ui.generated.resources.editor_amount
import com.dot.gallery.editor.ui.generated.resources.editor_grain_size
import com.dot.gallery.editor.ui.generated.resources.editor_roughness
import org.jetbrains.compose.resources.stringResource

internal const val TAG_CATEGORY_GRAIN = "editor.category.grain"

private val GrainParameters = listOf(
    FinishingParam(
        key = "grainAmount",
        labelRes = Res.string.editor_amount,
        displayRange = 0f..100f,
        step = 1f,
        neutral = 0f,
        signed = false,
        read = { it.finishing.grain.amount * 100f },
        write = { e, v -> e.grain { it.copy(amount = v / 100f) } }
    ),
    FinishingParam(
        key = "grainSize",
        labelRes = Res.string.editor_grain_size,
        displayRange = 1f..100f,
        step = 1f,
        neutral = 24f,
        signed = false,
        read = { it.finishing.grain.size * 100f },
        write = { e, v -> e.grain { it.copy(size = v / 100f) } }
    ),
    FinishingParam(
        key = "grainRoughness",
        labelRes = Res.string.editor_roughness,
        displayRange = 0f..100f,
        step = 1f,
        neutral = 35f,
        signed = false,
        read = { it.finishing.grain.roughness * 100f },
        write = { e, v -> e.grain { it.copy(roughness = v / 100f) } }
    )
)

private inline fun PhotoEdits.grain(
    update: (PhotoGrain) -> PhotoGrain
): PhotoEdits = copy(
    finishing = finishing.copy(grain = update(finishing.grain))
)

/**
 * The Grain tool per the approved `p-grain-inline` frame: the three
 * parameter tabs (Amount selected by default), the shared precision
 * ruler for the active tab and the settings rows that reflect every
 * field. There is no Preview row — the editor has no focused-zoom seam
 * for grain inspection, so the affordance is omitted rather than
 * painted dead.
 */
@Composable
internal fun GrainToolContent(
    state: PhotoEditorState,
    actions: PhotoEditorActions,
    modifier: Modifier = Modifier
) {
    val labels = GrainParameters.map { stringResource(it.labelRes) }
    val sourceRef = state.info?.reference
    var activeIndex by rememberSaveable(sourceRef) { mutableIntStateOf(0) }
    val index = activeIndex.coerceIn(GrainParameters.indices)
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
            GrainParameters.forEachIndexed { i, param ->
                FinishingChip(
                    label = labels[i],
                    selected = i == index,
                    enabled = enabled,
                    tag = "editor.grain.tab.${param.key}",
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
        val active = GrainParameters[index]
        FinishingParamControls(
            state = state,
            actions = actions,
            param = active,
            label = labels[index],
            enabled = enabled,
            identity = sourceRef to active.key
        )
    }, details = {
        GrainParameters.forEachIndexed { i, param ->
            FinishingSettingsRow(
                label = labels[i],
                value = param.displayOf(edits),
                selected = i == index,
                enabled = enabled,
                tag = "editor.grain.row.${param.key}",
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
