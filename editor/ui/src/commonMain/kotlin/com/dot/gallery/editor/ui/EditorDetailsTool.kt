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
import com.dot.gallery.editor.core.model.PhotoDetails
import com.dot.gallery.editor.core.model.PhotoEdits
import com.dot.gallery.editor.core.model.toEdits
import com.dot.gallery.editor.render.photo.PhotoEditorState
import com.dot.gallery.editor.ui.generated.resources.Res
import com.dot.gallery.editor.ui.generated.resources.editor_clarity
import com.dot.gallery.editor.ui.generated.resources.editor_noise_reduction
import com.dot.gallery.editor.ui.generated.resources.editor_sharpening
import com.dot.gallery.editor.ui.generated.resources.editor_texture
import org.jetbrains.compose.resources.stringResource

internal const val TAG_CATEGORY_DETAILS = "editor.category.details"

private val DetailsParameters = listOf(
    FinishingParam(
        key = "detailTexture",
        labelRes = Res.string.editor_texture,
        displayRange = -100f..100f,
        step = 1f,
        neutral = 0f,
        read = { it.spatial.details.texture * 100f },
        write = { e, v -> e.details { it.copy(texture = v / 100f) } }
    ),
    FinishingParam(
        key = "detailClarity",
        labelRes = Res.string.editor_clarity,
        displayRange = -100f..100f,
        step = 1f,
        neutral = 0f,
        read = { it.spatial.details.clarity * 100f },
        write = { e, v -> e.details { it.copy(clarity = v / 100f) } }
    ),
    FinishingParam(
        key = "detailSharpening",
        labelRes = Res.string.editor_sharpening,
        displayRange = 0f..100f,
        step = 1f,
        neutral = 0f,
        signed = false,
        read = { it.spatial.details.sharpening * 100f },
        write = { e, v -> e.details { it.copy(sharpening = v / 100f) } }
    ),
    FinishingParam(
        key = "detailNoiseReduction",
        labelRes = Res.string.editor_noise_reduction,
        displayRange = 0f..100f,
        step = 1f,
        neutral = 0f,
        signed = false,
        read = { it.spatial.details.noiseReduction * 100f },
        write = { e, v -> e.details { it.copy(noiseReduction = v / 100f) } }
    )
)

private inline fun PhotoEdits.details(
    update: (PhotoDetails) -> PhotoDetails
): PhotoEdits = copy(
    spatial = spatial.copy(details = update(spatial.details))
)

/**
 * The Details tool per the approved `p-details-inline` frame: the four
 * parameter tabs (Texture selected by default), the shared precision
 * ruler for the active tab and the settings rows that reflect every
 * field — tapping a row selects its tab.
 */
@Composable
internal fun DetailsToolContent(
    state: PhotoEditorState,
    actions: PhotoEditorActions,
    modifier: Modifier = Modifier
) {
    val labels = DetailsParameters.map { stringResource(it.labelRes) }
    val sourceRef = state.info?.reference
    var activeIndex by rememberSaveable(sourceRef) { mutableIntStateOf(0) }
    val index = activeIndex.coerceIn(DetailsParameters.indices)
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
            DetailsParameters.forEachIndexed { i, param ->
                FinishingChip(
                    label = labels[i],
                    selected = i == index,
                    enabled = enabled,
                    tag = "editor.details.tab.${param.key}",
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
        val active = DetailsParameters[index]
        FinishingParamControls(
            state = state,
            actions = actions,
            param = active,
            label = labels[index],
            enabled = enabled,
            identity = sourceRef to active.key
        )
    }, details = {
        DetailsParameters.forEachIndexed { i, param ->
            FinishingSettingsRow(
                label = labels[i],
                value = param.displayOf(edits),
                selected = i == index,
                enabled = enabled,
                tag = "editor.details.row.${param.key}",
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
