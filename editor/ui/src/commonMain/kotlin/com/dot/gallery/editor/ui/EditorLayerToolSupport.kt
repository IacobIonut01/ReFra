package com.dot.gallery.editor.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dot.gallery.editor.core.model.ContinuousPoint
import com.dot.gallery.editor.core.model.PhotoImageLayer
import com.dot.gallery.editor.core.model.PhotoLayerMapping
import com.dot.gallery.editor.core.model.PhotoMapping
import com.dot.gallery.editor.core.model.PixelSize
import com.dot.gallery.editor.core.model.toEdits
import com.dot.gallery.editor.render.photo.PhotoEditorState

/**
 * Shared machinery for the contextual composition tools (Transform,
 * Blend & opacity, Double exposure, Mask): they all edit *the selected
 * image layer* through the same recipe write, so the resolution, the
 * layer-replacing edit and the canvas placement maths live here once.
 */

/**
 * The layer a contextual tool edits — the workspace selection while it
 * still exists in the recipe, else the topmost image layer (the
 * recipe's last entry). Null when the document has no image layers.
 */
internal fun resolveSelectedLayer(
    state: PhotoEditorState,
    selectedLayerId: String?
): PhotoImageLayer? {
    val layers = state.document?.layers.orEmpty()
    return layers.firstOrNull { it.id == selectedLayerId }
        ?: layers.lastOrNull()
}

/** The full-resolution base output the layer transform numbers mean. */
internal fun PhotoEditorState.baseOutputSize(): PixelSize? =
    document?.let {
        PhotoMapping(it.source.size, 1, it.geometry).outputSize
    }

/**
 * One committed recipe step replacing the layer [layerId] in place —
 * every field the update does not touch is preserved verbatim.
 */
internal fun PhotoEditorActions.applyLayerEdit(
    state: PhotoEditorState,
    layerId: String,
    label: String,
    update: (PhotoImageLayer) -> PhotoImageLayer
) {
    val document = state.document ?: return
    val index = document.layers.indexOfFirst { it.id == layerId }
    if (index < 0) return
    applyEdits(
        label,
        document.toEdits().copy(
            layers = document.layers.replacingAt(
                index, update(document.layers[index])
            )
        )
    )
}

/** The in-flight counterpart of [applyLayerEdit] — a live interaction. */
internal fun PhotoEditorActions.draftLayerEdit(
    state: PhotoEditorState,
    layerId: String,
    update: (PhotoImageLayer) -> PhotoImageLayer
) {
    val document = state.document ?: return
    val index = document.layers.indexOfFirst { it.id == layerId }
    if (index < 0) return
    updateEdits(
        document.toEdits().copy(
            layers = document.layers.replacingAt(
                index, update(document.layers[index])
            )
        )
    )
}

/**
 * The selected layer's placement shared by the overlays: the full base
 * output size, the forward/inverse mapping and the four upright corners
 * in output pixels (TL, TR, BR, BL). Null when there is no visible
 * image layer to frame.
 */
internal class LayerPlacement(
    val layer: PhotoImageLayer,
    val outputSize: PixelSize,
    val mapping: PhotoLayerMapping,
    val corners: List<ContinuousPoint>
)

internal fun layerPlacement(
    state: PhotoEditorState,
    selectedLayerId: String?
): LayerPlacement? {
    val layer = resolveSelectedLayer(state, selectedLayerId) ?: return null
    if (!layer.visible) return null
    val outputSize = state.baseOutputSize() ?: return null
    val mapping = PhotoLayerMapping(
        rawSize = layer.source.size,
        orientation = 1,
        outputSize = outputSize,
        transform = layer.transform
    )
    val w = mapping.uprightSize.width.toDouble()
    val h = mapping.uprightSize.height.toDouble()
    return LayerPlacement(
        layer = layer,
        outputSize = outputSize,
        mapping = mapping,
        corners = listOf(
            mapping.outputPoint(-0.5, -0.5),
            mapping.outputPoint(w - 0.5, -0.5),
            mapping.outputPoint(w - 0.5, h - 0.5),
            mapping.outputPoint(-0.5, h - 0.5)
        )
    )
}

/**
 * Publishes a contextual tool's scoped Reset into the compact header's
 * More menu — the same contract the Adjust tools use, scoped to the
 * selected layer's own fields.
 */
@Composable
internal fun PublishLayerToolReset(
    label: String,
    tag: String,
    enabled: Boolean,
    onReset: () -> Unit
) {
    val toolMenu = LocalPhotoToolMenu.current
    toolMenu?.apply {
        resetParameterLabel = label
        resetParameterTag = tag
        resetParameterFooterTag = "$tag.foot"
        resetParameterEnabled = enabled
        onResetParameter = onReset
    }
    DisposableEffect(toolMenu) {
        onDispose {
            toolMenu?.resetParameterLabel = null
            toolMenu?.onResetParameter = null
        }
    }
}

/**
 * Publishes a contextual tool's second More-menu action — the Blend
 * tool's View original compare toggle — under the same contract as
 * [PublishLayerToolReset].
 */
@Composable
internal fun PublishLayerToolCompare(
    label: String,
    tag: String,
    enabled: Boolean,
    onCompare: () -> Unit
) {
    val toolMenu = LocalPhotoToolMenu.current
    toolMenu?.apply {
        compareLabel = label
        compareTag = tag
        compareEnabled = enabled
        this.onCompare = onCompare
    }
    DisposableEffect(toolMenu) {
        onDispose {
            toolMenu?.compareLabel = null
            toolMenu?.onCompare = null
        }
    }
}

/**
 * The p-transform 48 dp pair row — a `surfaceContainerHigh` card holding
 * one visible label (`X · Y`, `Scale · Rotation`) and a [fieldWidth]×40
 * readout field showing both values (124 dp for `X · Y`, 101 dp for
 * `Scale · Rotation`). The row carries the first parameter's progress
 * semantics, the field the second's — every parameter keeps its own
 * SetProgress path — and tapping the field opens the pair dialog that
 * edits both atomically.
 */
@Composable
internal fun LayerToolPairRow(
    label: String,
    display: String,
    fieldWidth: Dp,
    primaryValue: Float,
    primaryRange: ClosedFloatingPointRange<Float>,
    secondaryValue: Float,
    secondaryRange: ClosedFloatingPointRange<Float>,
    enabled: Boolean,
    interactionActive: Boolean,
    rowTag: String,
    fieldTag: String,
    onPrimaryProgress: (Float) -> Unit,
    onSecondaryProgress: (Float) -> Unit,
    onFieldClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(48.dp)
            .semantics {
                contentDescription = "$label: $display"
                progressBarRangeInfo = ProgressBarRangeInfo(
                    current = primaryValue.coerceIn(
                        primaryRange.start, primaryRange.endInclusive
                    ),
                    range = primaryRange,
                    steps = 0
                )
                setProgress { target ->
                    if (!enabled || interactionActive || !target.isFinite()) {
                        return@setProgress false
                    }
                    onPrimaryProgress(
                        target.coerceIn(
                            primaryRange.start, primaryRange.endInclusive
                        )
                    )
                    true
                }
            }
            .testTag(rowTag)
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = if (enabled) {
                MaterialTheme.colorScheme.onSurfaceVariant
            } else {
                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
            },
            maxLines = 1,
            modifier = Modifier.weight(1f)
        )
        Box(
            modifier = Modifier
                .size(fieldWidth, 40.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                .then(
                    if (enabled) {
                        Modifier.border(
                            1.dp,
                            MaterialTheme.colorScheme.outlineVariant,
                            RoundedCornerShape(8.dp)
                        )
                    } else {
                        Modifier
                    }
                )
                .semantics {
                    progressBarRangeInfo = ProgressBarRangeInfo(
                        current = secondaryValue.coerceIn(
                            secondaryRange.start, secondaryRange.endInclusive
                        ),
                        range = secondaryRange,
                        steps = 0
                    )
                    setProgress { target ->
                        if (!enabled || interactionActive ||
                            !target.isFinite()
                        ) {
                            return@setProgress false
                        }
                        onSecondaryProgress(
                            target.coerceIn(
                                secondaryRange.start,
                                secondaryRange.endInclusive
                            )
                        )
                        true
                    }
                }
                .clickable(
                    enabled = enabled && !interactionActive,
                    role = Role.Button,
                    onClickLabel = label
                ) { onFieldClick() }
                .testTag(fieldTag),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = display,
                style = MaterialTheme.typography.bodyMedium,
                color = if (enabled) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                },
                fontSize = 14.sp,
                maxLines = 1
            )
        }
    }
}


