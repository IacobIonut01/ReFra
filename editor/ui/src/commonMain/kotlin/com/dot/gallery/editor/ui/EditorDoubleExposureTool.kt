package com.dot.gallery.editor.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.dot.gallery.editor.core.model.PhotoBlendMode
import com.dot.gallery.editor.render.photo.PhotoEditorState
import com.dot.gallery.editor.ui.generated.resources.Res
import com.dot.gallery.editor.ui.generated.resources.editor_blend_multiply
import com.dot.gallery.editor.ui.generated.resources.editor_blend_normal
import com.dot.gallery.editor.ui.generated.resources.editor_blend_overlay
import com.dot.gallery.editor.ui.generated.resources.editor_blend_reset
import com.dot.gallery.editor.ui.generated.resources.editor_blend_screen
import com.dot.gallery.editor.ui.generated.resources.editor_blend_soft_light
import com.dot.gallery.editor.ui.generated.resources.editor_cancel
import com.dot.gallery.editor.ui.generated.resources.editor_dismiss
import com.dot.gallery.editor.ui.generated.resources.editor_double_base_overlay
import com.dot.gallery.editor.ui.generated.resources.editor_double_blend
import com.dot.gallery.editor.ui.generated.resources.editor_double_empty
import com.dot.gallery.editor.ui.generated.resources.editor_double_transform_overlay
import com.dot.gallery.editor.ui.generated.resources.editor_layer_add_image
import com.dot.gallery.editor.ui.generated.resources.editor_layer_edit_mask
import com.dot.gallery.editor.ui.generated.resources.editor_layer_import_error
import com.dot.gallery.editor.ui.generated.resources.editor_layer_importing
import com.dot.gallery.editor.ui.generated.resources.editor_layer_opacity
import com.dot.gallery.editor.ui.generated.resources.editor_retry
import com.dot.gallery.editor.ui.icons.AddPhotoAlternate
import com.dot.gallery.editor.ui.icons.EditorSymbols
import kotlin.math.roundToInt
import org.jetbrains.compose.resources.stringResource

internal const val TAG_CATEGORY_DOUBLE_EXPOSURE = "editor.category.double"
internal const val TAG_DOUBLE_ADD = "editor.double.add"
internal const val TAG_DOUBLE_BASE = "editor.double.baseOverlay"
internal const val TAG_DOUBLE_BLEND = "editor.double.blend"
internal const val TAG_DOUBLE_OPACITY = "editor.double.opacity"
internal const val TAG_DOUBLE_TRANSFORM = "editor.double.transform"
internal const val TAG_DOUBLE_MASK = "editor.double.mask"
internal const val TAG_DOUBLE_RESET = "editor.double.reset"

/**
 * The Double exposure inspector per the approved `p-double` frame.
 * With no image layer the surface is the honest empty state — the real
 * Add image action plus the import progress/error rows — so the tool
 * is always available and creates its own subject. With a selected
 * layer it reads Base · Overlay (the two sources' names), the current
 * Blend and Opacity values (tapping either routes to the Blend tool
 * that edits them), then the Transform overlay / Edit mask buttons
 * which switch contextual tool through the same selection path a
 * catalogue cell takes — the outgoing transaction settles first, so
 * drafts never nest.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun DoubleExposureToolContent(
    state: PhotoEditorState,
    actions: PhotoEditorActions,
    modifier: Modifier = Modifier
) {
    val context = LocalPhotoLayerToolContext.current
    val layer = resolveSelectedLayer(state, context?.selectedLayerId)
    val document = state.document
    val layerId = layer?.id
    val enabled = layerId != null && layer != null && !layer.locked &&
        document != null && !state.opening
    val canEdit = enabled && !state.interactionActive
    val latestState by rememberUpdatedState(state)
    val latestActions by rememberUpdatedState(actions)
    val latestLayerId by rememberUpdatedState(layerId)

    // The More-menu Reset restores Normal · 100% · unclipped — the same
    // fields the Blend tool owns on this layer.
    val resetLabel = stringResource(Res.string.editor_blend_reset)
    val resettable = layer != null &&
        (layer.blendMode != PhotoBlendMode.Normal ||
            layer.opacity != 1f || layer.clipToBelow)
    PublishLayerToolReset(
        label = resetLabel,
        tag = TAG_DOUBLE_RESET,
        enabled = canEdit && resettable,
        onReset = {
            val id = latestLayerId ?: return@PublishLayerToolReset
            latestActions.applyLayerEdit(latestState, id, resetLabel) {
                it.copy(
                    blendMode = PhotoBlendMode.Normal,
                    opacity = 1f,
                    clipToBelow = false
                )
            }
        }
    )

    Column(
        modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (layer == null || document == null) {
            DoubleExposureEmpty(state = state, actions = actions)
        } else {
            // Base · Overlay — the two sources this composite blends.
            DoubleExposureRow(
                label = stringResource(Res.string.editor_double_base_overlay),
                value = "${state.info?.displayName.orEmpty()} · ${layer.name}",
                enabled = false,
                onClick = null,
                rowTag = TAG_DOUBLE_BASE
            )
            // Blend and Opacity read the live values; a tap routes to the
            // Blend tool that owns the controls.
            DoubleExposureRow(
                label = stringResource(Res.string.editor_double_blend),
                value = blendModeTitle(layer.blendMode),
                enabled = true,
                onClick = { context?.openTool(EditorToolIds.PhotoBlend) },
                rowTag = TAG_DOUBLE_BLEND
            )
            DoubleExposureRow(
                label = stringResource(Res.string.editor_layer_opacity),
                value = "${(layer.opacity * 100f).roundToInt()}%",
                enabled = true,
                onClick = { context?.openTool(EditorToolIds.PhotoBlend) },
                rowTag = TAG_DOUBLE_OPACITY
            )
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // p-double paints these as plain surface buttons, not
                // tonal: surfaceContainerHigh #2B2930 + onSurface label.
                DoubleExposureRouteButton(
                    label = stringResource(
                        Res.string.editor_double_transform_overlay
                    ),
                    onClick = { context?.openTool(EditorToolIds.PhotoTransform) },
                    tag = TAG_DOUBLE_TRANSFORM
                )
                DoubleExposureRouteButton(
                    label = stringResource(Res.string.editor_layer_edit_mask),
                    onClick = { context?.openTool(EditorToolIds.PhotoLayerMask) },
                    tag = TAG_DOUBLE_MASK
                )
            }
        }
    }
}

/** The blend mode's catalogue label — the same names the strip uses. */
@Composable
internal fun blendModeTitle(mode: PhotoBlendMode): String = stringResource(
    when (mode) {
        PhotoBlendMode.Normal -> Res.string.editor_blend_normal
        PhotoBlendMode.Multiply -> Res.string.editor_blend_multiply
        PhotoBlendMode.Screen -> Res.string.editor_blend_screen
        PhotoBlendMode.Overlay -> Res.string.editor_blend_overlay
        PhotoBlendMode.SoftLight -> Res.string.editor_blend_soft_light
    }
)

/**
 * A p-double route button — a plain `surfaceContainerHigh` card with an
 * `onSurface` label, not the tonal treatment the catalogue buttons use.
 */
@Composable
private fun DoubleExposureRouteButton(
    label: String,
    onClick: () -> Unit,
    tag: String
) {
    Box(
        modifier = Modifier
            .height(48.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .clickable(role = Role.Button, onClickLabel = label, onClick = onClick)
            .testTag(tag)
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1
        )
    }
}

/**
 * One 48 dp summary row — label left, live value right. Rows with an
 * [onClick] route to the tool that owns the value; the rest are honest
 * read-only cells.
 */
@Composable
private fun DoubleExposureRow(
    label: String,
    value: String,
    enabled: Boolean,
    onClick: (() -> Unit)?,
    rowTag: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .then(
                if (onClick != null) {
                    Modifier.clickable(
                        enabled = enabled,
                        role = Role.Button,
                        onClickLabel = label,
                        onClick = onClick
                    )
                } else {
                    Modifier
                }
            )
            .testTag(rowTag)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = if (enabled) {
                MaterialTheme.colorScheme.onSurface
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            maxLines = 1
        )
    }
}

/**
 * The no-layer surface — a real Add image action plus the same import
 * progress, cancel and error/retry affordances the Layers workspace
 * shows, all fed by the session's own import flags.
 */
@Composable
private fun DoubleExposureEmpty(
    state: PhotoEditorState,
    actions: PhotoEditorActions
) {
    val canAdd = canAddLayerImage(state)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 120.dp)
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = stringResource(Res.string.editor_double_empty),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (state.importingLayer) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(Res.string.editor_layer_importing),
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1
                    )
                    LinearProgressIndicator(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 6.dp)
                    )
                }
                TextButton(
                    onClick = actions.cancelLayerImport,
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .padding(start = 4.dp)
                        .testTag(TAG_LAYER_IMPORT_CANCEL)
                ) {
                    Text(stringResource(Res.string.editor_cancel))
                }
            }
        } else if (state.layerImportError) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Outlined.ErrorOutline,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error
                )
                Text(
                    text = stringResource(Res.string.editor_layer_import_error),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 8.dp)
                )
                TextButton(
                    onClick = actions.requestAddImage,
                    enabled = canAdd,
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .testTag(TAG_LAYER_IMPORT_RETRY)
                ) {
                    Text(stringResource(Res.string.editor_retry))
                }
                TextButton(
                    onClick = actions.clearError,
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .testTag(TAG_LAYER_IMPORT_DISMISS)
                ) {
                    Text(stringResource(Res.string.editor_dismiss))
                }
            }
        } else {
            FilledTonalButton(
                onClick = actions.requestAddImage,
                enabled = canAdd,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .padding(top = 12.dp)
                    .height(48.dp)
                    .testTag(TAG_DOUBLE_ADD)
            ) {
                Icon(
                    imageVector = EditorSymbols.AddPhotoAlternate,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    text = stringResource(Res.string.editor_layer_add_image),
                    modifier = Modifier.padding(start = 6.dp)
                )
            }
        }
    }
}
