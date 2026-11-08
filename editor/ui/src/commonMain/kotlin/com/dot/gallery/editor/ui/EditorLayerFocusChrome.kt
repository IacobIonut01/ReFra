package com.dot.gallery.editor.ui

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.dot.gallery.editor.core.model.PhotoImageLayer
import com.dot.gallery.editor.render.photo.PhotoEditorState
import com.dot.gallery.editor.ui.generated.resources.Res
import com.dot.gallery.editor.ui.generated.resources.editor_apply
import com.dot.gallery.editor.ui.generated.resources.editor_exit_focus
import com.dot.gallery.editor.ui.generated.resources.editor_layer_align_centre
import com.dot.gallery.editor.ui.generated.resources.editor_layer_opacity
import com.dot.gallery.editor.ui.generated.resources.editor_layers
import com.dot.gallery.editor.ui.icons.Close
import com.dot.gallery.editor.ui.icons.EditorSymbols
import com.dot.gallery.editor.ui.icons.FitScreen
import com.dot.gallery.editor.ui.icons.Layers
import kotlin.math.roundToInt
import org.jetbrains.compose.resources.stringResource

internal const val TAG_LAYER_FOCUS_TOP = "editor.layerFocus.top"
internal const val TAG_LAYER_FOCUS_BOTTOM = "editor.layerFocus.bottom"
internal const val TAG_LAYER_FOCUS_EXIT = "editor.layerFocus.exit"
internal const val TAG_LAYER_FOCUS_ALIGN = "editor.layerFocus.align"
internal const val TAG_LAYER_FOCUS_LAYERS = "editor.layerFocus.layers"
internal const val TAG_LAYER_FOCUS_OPACITY = "editor.layerFocus.opacity"
internal const val TAG_LAYER_FOCUS_APPLY = "editor.layerFocus.apply"

/**
 * The composite focus chrome of `x-photo-composite`: while a real image
 * layer is selected on a layer-aware surface (Layers, Transform, Blend,
 * Double exposure, Mask), full-screen focus replaces the generic
 * navigator chrome with layer controls — the canvas keeps the tool's
 * interactive overlay.
 *
 * Top bar: the full-width 56 dp pill carrying Exit, the selected
 * layer's `name · blend` title and its opacity percent in primary.
 * Bottom bar: the full-width 72 dp pill carrying Align centre, the
 * Layers destination, the `Opacity 65%` readout and the filled Apply.
 * Apply commits an open tool transaction before leaving; exiting focus
 * on its own never touches the draft.
 */
@Composable
internal fun BoxScope.EditorLayerFocusChrome(
    layer: PhotoImageLayer,
    state: PhotoEditorState,
    actions: PhotoEditorActions,
    onExitFocus: () -> Unit,
    onOpenLayers: () -> Unit,
    onApply: () -> Unit
) {
    val alignLabel = stringResource(Res.string.editor_layer_align_centre)
    EditorFloatingPanel(
        shape = RoundedCornerShape(28.dp),
        modifier = Modifier
            .align(Alignment.TopCenter)
            .padding(start = 16.dp, top = 16.dp, end = 16.dp)
            .fillMaxWidth()
            .testTag(TAG_LAYER_FOCUS_TOP)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            WorkbenchIconButton(
                icon = EditorSymbols.Close,
                label = stringResource(Res.string.editor_exit_focus),
                onClick = onExitFocus,
                modifier = Modifier.testTag(TAG_LAYER_FOCUS_EXIT)
            )
            Text(
                text = "${layer.name} · ${blendModeTitle(layer.blendMode)}",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 8.dp)
            )
            Text(
                text = "${(layer.opacity * 100f).roundToInt()}%",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1,
                modifier = Modifier.padding(end = 12.dp)
            )
        }
    }
    EditorFloatingPanel(
        shape = RoundedCornerShape(28.dp),
        modifier = Modifier
            .align(Alignment.BottomCenter)
            .padding(start = 16.dp, end = 16.dp, bottom = 28.dp)
            .fillMaxWidth()
            .testTag(TAG_LAYER_FOCUS_BOTTOM)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(72.dp)
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            WorkbenchIconButton(
                icon = EditorSymbols.FitScreen,
                label = alignLabel,
                onClick = {
                    actions.applyLayerEdit(state, layer.id, alignLabel) {
                        it.copy(
                            transform = it.transform.copy(
                                translationX = 0f,
                                translationY = 0f
                            )
                        )
                    }
                },
                enabled = !layer.locked && state.document != null &&
                    !state.interactionActive &&
                    (layer.transform.translationX != 0f ||
                        layer.transform.translationY != 0f),
                modifier = Modifier.testTag(TAG_LAYER_FOCUS_ALIGN)
            )
            WorkbenchIconButton(
                icon = EditorSymbols.Layers,
                label = stringResource(Res.string.editor_layers),
                onClick = onOpenLayers,
                modifier = Modifier.testTag(TAG_LAYER_FOCUS_LAYERS)
            )
            Text(
                text = stringResource(Res.string.editor_layer_opacity) +
                    " ${(layer.opacity * 100f).roundToInt()}%",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                modifier = Modifier
                    .padding(start = 8.dp)
                    .testTag(TAG_LAYER_FOCUS_OPACITY)
            )
            Spacer(modifier = Modifier.weight(1f))
            Button(
                onClick = onApply,
                enabled = !state.interactionActive,
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .testTag(TAG_LAYER_FOCUS_APPLY)
            ) {
                Text(
                    text = stringResource(Res.string.editor_apply),
                    maxLines = 1,
                    style = MaterialTheme.typography.labelLarge
                )
            }
        }
    }
}
