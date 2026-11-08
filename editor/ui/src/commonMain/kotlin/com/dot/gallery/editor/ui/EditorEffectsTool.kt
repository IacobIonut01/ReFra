package com.dot.gallery.editor.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.dot.gallery.editor.core.model.PhotoEdits
import com.dot.gallery.editor.core.model.PhotoEffects
import com.dot.gallery.editor.core.model.toEdits
import com.dot.gallery.editor.render.photo.PhotoEditorState
import com.dot.gallery.editor.ui.generated.resources.Res
import com.dot.gallery.editor.ui.generated.resources.editor_retouch_border_width
import com.dot.gallery.editor.ui.generated.resources.editor_tool_borders
import com.dot.gallery.editor.ui.generated.resources.editor_tool_edges
import com.dot.gallery.editor.ui.generated.resources.editor_tool_posterize
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

internal const val TAG_CATEGORY_POSTERIZE = "editor.category.posterize"
internal const val TAG_CATEGORY_EDGES = "editor.category.edges"
internal const val TAG_CATEGORY_BORDERS = "editor.category.borders"

/** The Effects tool's three tabs; each catalogue cell opens on its own. */
internal enum class EffectsMode(val labelRes: StringResource) {
    Posterize(Res.string.editor_tool_posterize),
    Edges(Res.string.editor_tool_edges),
    Borders(Res.string.editor_tool_borders)
}

private inline fun PhotoEdits.effects(update: (PhotoEffects) -> PhotoEffects): PhotoEdits =
    copy(retouch = retouch.copy(effects = update(retouch.effects)))

private fun effectParam(mode: EffectsMode) = when (mode) {
    EffectsMode.Posterize -> FinishingParam(
        key = "effectPosterize",
        labelRes = Res.string.editor_tool_posterize,
        displayRange = 0f..100f,
        step = 1f,
        neutral = 0f,
        signed = false,
        read = { it.retouch.effects.posterize * 100f },
        write = { e, v -> e.effects { it.copy(posterize = v / 100f) } }
    )
    EffectsMode.Edges -> FinishingParam(
        key = "effectEdges",
        labelRes = Res.string.editor_tool_edges,
        displayRange = 0f..100f,
        step = 1f,
        neutral = 0f,
        signed = false,
        read = { it.retouch.effects.edges * 100f },
        write = { e, v -> e.effects { it.copy(edges = v / 100f) } }
    )
    EffectsMode.Borders -> FinishingParam(
        key = "effectBorder",
        labelRes = Res.string.editor_retouch_border_width,
        displayRange = 0f..100f,
        step = 1f,
        neutral = 0f,
        signed = false,
        read = { it.retouch.effects.borderWidth * 100f },
        write = { e, v -> e.effects { it.copy(borderWidth = v / 100f) } }
    )
}

/**
 * Effects: the connected Posterize, Edges and Borders group over one ruler;
 * Borders adds the shared swatch row, which keeps its colour for the next ring.
 */
@Composable
internal fun EffectsToolContent(
    state: PhotoEditorState,
    actions: PhotoEditorActions,
    initialMode: EffectsMode,
    modifier: Modifier = Modifier
) {
    val sourceRef = state.info?.reference
    var mode by rememberSaveable(sourceRef, initialMode) { mutableStateOf(initialMode) }
    val enabled = state.document != null && !state.opening
    val latestState by rememberUpdatedState(state)
    val latestActions by rememberUpdatedState(actions)
    val modes = EffectsMode.entries
    val param = effectParam(mode)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(RetouchCardPadding)
            .testTag("editor.effects"),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        ConnectedButtonGroup(
            itemCount = modes.size,
            selectedIndex = modes.indexOf(mode),
            enabled = enabled,
            scrolling = false,
            label = { stringResource(modes[it].labelRes) },
            tag = { "editor.effects.tab.${modes[it].name}" },
            onSelect = {
                if (modes[it] != mode) {
                    latestActions.cancelEdit()
                    mode = modes[it]
                }
            }
        )
        FinishingParamControls(
            state = state,
            actions = actions,
            param = param,
            label = stringResource(param.labelRes),
            enabled = enabled,
            identity = sourceRef to param.key,
            showMetaRow = false,
            limitLabels = true,
            insetContent = false,
            modifier = Modifier.padding(top = 14.dp)
        )
        if (mode == EffectsMode.Borders) {
            RetouchColourRow(
                selected = state.document?.retouch?.effects?.borderColor
                    ?: PhotoEffects.DEFAULT_BORDER_COLOR,
                enabled = enabled && !state.interactionActive,
                tag = "editor.effects.colour",
                onSelect = { colour ->
                    val current = latestState.document?.toEdits() ?: return@RetouchColourRow
                    latestActions.applyEdits(
                        "Border colour",
                        current.effects { it.copy(borderColor = colour) }
                    )
                }
            )
        }
    }
}
