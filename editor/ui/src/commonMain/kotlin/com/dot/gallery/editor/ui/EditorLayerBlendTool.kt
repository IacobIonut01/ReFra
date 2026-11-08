package com.dot.gallery.editor.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dot.gallery.editor.core.model.PhotoBlendMode
import com.dot.gallery.editor.render.photo.PhotoEditorState
import com.dot.gallery.editor.ui.generated.resources.Res
import com.dot.gallery.editor.ui.generated.resources.editor_blend_multiply
import com.dot.gallery.editor.ui.generated.resources.editor_blend_normal
import com.dot.gallery.editor.ui.generated.resources.editor_blend_overlay
import com.dot.gallery.editor.ui.generated.resources.editor_blend_reset
import com.dot.gallery.editor.ui.generated.resources.editor_blend_screen
import com.dot.gallery.editor.ui.generated.resources.editor_blend_soft_light
import com.dot.gallery.editor.ui.generated.resources.editor_compare_after
import com.dot.gallery.editor.ui.generated.resources.editor_layer_clip_below
import com.dot.gallery.editor.ui.generated.resources.editor_layer_off
import com.dot.gallery.editor.ui.generated.resources.editor_layer_on
import com.dot.gallery.editor.ui.generated.resources.editor_layer_opacity
import com.dot.gallery.editor.ui.generated.resources.editor_layer_view_original
import kotlin.math.roundToInt
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

internal const val TAG_CATEGORY_BLEND = "editor.category.blend"
internal const val TAG_BLEND_MODE_PREFIX = "editor.layer.blend.mode."
internal const val TAG_LAYER_OPACITY = "editor.layer.opacity"
internal const val TAG_LAYER_OPACITY_VALUE = "editor.layer.opacity.value"
internal const val TAG_LAYER_CLIP = "editor.layer.clip"
internal const val TAG_LAYER_ORIGINAL = "editor.layer.original"
internal const val TAG_LAYER_BLEND_RESET = "editor.layer.blend.reset"

// The p-blend mode cells — exact Figma chip widths.
private data class BlendModeCell(
    val mode: PhotoBlendMode,
    val titleRes: StringResource,
    val width: Int
)

private val BLEND_MODE_CELLS = listOf(
    BlendModeCell(PhotoBlendMode.Normal, Res.string.editor_blend_normal, 96),
    BlendModeCell(
        PhotoBlendMode.Multiply, Res.string.editor_blend_multiply, 112
    ),
    BlendModeCell(PhotoBlendMode.Screen, Res.string.editor_blend_screen, 96),
    BlendModeCell(
        PhotoBlendMode.Overlay, Res.string.editor_blend_overlay, 96
    ),
    BlendModeCell(
        PhotoBlendMode.SoftLight, Res.string.editor_blend_soft_light, 120
    )
)

/**
 * The Blend & opacity inspector per the approved `p-blend` frame: the
 * 56 dp mode strip with a 48 dp cell per real blend mode, the opacity
 * block (label + 28 sp tappable value over the thin 0..100 slider) and
 * the Clip to layer below Off/On toggle. View original stays reachable
 * as the More-menu compare toggle — while it is on the preview shows
 * the layer unblended (Normal · 100%) through the same draft machinery
 * every interaction uses; toggled off the draft is cancelled so the
 * recipe never changes and no history step lands.
 *
 * Every control writes the selected layer in place; a locked layer
 * disables the whole surface.
 */
@Composable
internal fun LayerBlendToolContent(
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
    val sourceKey = state.info?.reference

    // The More-menu Reset restores Normal · 100% · unclipped.
    val resetLabel = stringResource(Res.string.editor_blend_reset)
    val resettable = layer != null &&
        (layer.blendMode != PhotoBlendMode.Normal ||
            layer.opacity != 1f || layer.clipToBelow)
    PublishLayerToolReset(
        label = resetLabel,
        tag = TAG_LAYER_BLEND_RESET,
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

    val owner = remember { NumericEntryOwner() }
    NumericEntryOwnerEffects(owner, state, actions)
    var dialogOpen by remember { mutableStateOf(false) }

    val opacityLabel = stringResource(Res.string.editor_layer_opacity)
    val clipLabel = stringResource(Res.string.editor_layer_clip_below)
    val originalLabel = stringResource(Res.string.editor_layer_view_original)

    // The compare toggle — a real interaction draft while On, always
    // cancelled when toggled off: the recipe never records it. It lives
    // in the header's More menu, so the inspector keeps the p-blend
    // three-block anatomy.
    var holding by remember { mutableStateOf(false) }
    fun toggleCompare() {
        val id = latestLayerId ?: return
        if (holding) {
            holding = false
            latestActions.cancelEdit()
            return
        }
        if (latestState.interactionActive) return
        // `interactionActive` is a published snapshot — the session's
        // nested-draft check is the live verdict; a rejection means the
        // draft is not ours to hold.
        try {
            latestActions.beginEdit("View original")
        } catch (rejected: IllegalStateException) {
            return
        }
        holding = true
        latestActions.draftLayerEdit(latestState, id) {
            it.copy(blendMode = PhotoBlendMode.Normal, opacity = 1f)
        }
    }
    LaunchedEffect(state.interactionActive) {
        if (!state.interactionActive) holding = false
    }
    DisposableEffect(layerId) {
        onDispose {
            if (holding) {
                holding = false
                latestActions.cancelEdit()
            }
        }
    }
    PublishLayerToolCompare(
        label = if (holding) {
            stringResource(Res.string.editor_compare_after)
        } else {
            originalLabel
        },
        tag = TAG_LAYER_ORIGINAL,
        enabled = holding || (enabled && !state.interactionActive),
        onCompare = ::toggleCompare
    )

    // The p-blend column: the inspector surface gives top10 and this
    // column top6 → the four direct 48 dp children land at 16/72/128/184.
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 16.dp, top = 6.dp, end = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // The 48 dp mode viewport — fixed-width pill cells per blend mode.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .horizontalScroll(rememberScrollState()),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            BLEND_MODE_CELLS.forEach { cell ->
                val selected = layer?.blendMode == cell.mode
                val label = stringResource(cell.titleRes)
                Box(
                    modifier = Modifier
                        .size(cell.width.dp, 48.dp)
                        .clip(RoundedCornerShape(24.dp))
                        .background(
                            if (selected) {
                                MaterialTheme.colorScheme.secondaryContainer
                            } else {
                                MaterialTheme.colorScheme.surfaceContainerHigh
                            }
                        )
                        .clickable(
                            enabled = canEdit && !selected,
                            role = Role.Button,
                            onClickLabel = label
                        ) {
                            val id = latestLayerId ?: return@clickable
                            latestActions.applyLayerEdit(
                                latestState, id, "Blend mode"
                            ) { it.copy(blendMode = cell.mode) }
                        }
                        .semantics { this.selected = selected }
                        .testTag(TAG_BLEND_MODE_PREFIX + cell.mode.name),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelLarge,
                        color = if (selected) {
                            MaterialTheme.colorScheme.onSecondaryContainer
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        maxLines = 1
                    )
                }
            }
        }

        // The opacity pair — a 48 dp label row (14 sp label beside the
        // 28 sp tappable value, the exact-entry affordance), then the
        // thin 0..100 slider as its own 48 dp child. A slider gesture is
        // one local interaction; the value tap opens the numeric dialog.
        val opacity = layer?.opacity ?: 1f
        var gestureOpen by remember { mutableStateOf(false) }
        val sliderInteraction = remember { MutableInteractionSource() }
        LaunchedEffect(state.interactionActive) {
            if (!state.interactionActive) gestureOpen = false
        }
        DisposableEffect(sourceKey) {
            onDispose {
                if (gestureOpen) {
                    gestureOpen = false
                    latestActions.cancelEdit()
                }
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth().height(48.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = opacityLabel,
                style = MaterialTheme.typography.labelLarge,
                color = if (enabled) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                },
                maxLines = 1,
                modifier = Modifier.padding(start = 8.dp)
            )
            Spacer(Modifier.weight(1f))
            Text(
                text = "${(opacity * 100f).roundToInt()}%",
                style = MaterialTheme.typography.labelLarge,
                fontSize = 28.sp,
                color = if (enabled) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                },
                maxLines = 1,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(
                        enabled = enabled && !state.interactionActive,
                        role = Role.Button,
                        onClickLabel = opacityLabel
                    ) {
                        try {
                            latestActions.beginEdit("Layer opacity")
                        } catch (rejected: IllegalStateException) {
                            return@clickable
                        }
                        owner.open(sourceKey)
                        dialogOpen = true
                    }
                    .testTag(TAG_LAYER_OPACITY_VALUE)
                    .padding(horizontal = 8.dp)
            )
        }
        WorkbenchSlider(
            value = opacity * 100f,
            onValueChange = { next ->
                val id = latestLayerId ?: return@WorkbenchSlider
                if (!gestureOpen) {
                    if (!enabled || latestState.interactionActive) {
                        return@WorkbenchSlider
                    }
                    // The snapshot check can lag a foreign draft —
                    // the nested-draft check is the live verdict; a
                    // rejection means this value is not ours.
                    try {
                        latestActions.beginEdit("Layer opacity")
                    } catch (rejected: IllegalStateException) {
                        return@WorkbenchSlider
                    }
                    gestureOpen = true
                }
                latestActions.draftLayerEdit(latestState, id) {
                    it.copy(opacity = (next / 100f).coerceIn(0f, 1f))
                }
            },
            onValueChangeFinished = {
                if (gestureOpen) {
                    gestureOpen = false
                    latestActions.commitEdit()
                }
            },
            interactionSource = sliderInteraction,
            // An active interaction locks the other controls, never
            // the one that owns it.
            enabled = enabled &&
                (!state.interactionActive || gestureOpen),
            valueRange = 0f..100f,
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .testTag(TAG_LAYER_OPACITY)
        )

        // Clip to layer below — the 96×48 rounded tonal Off/On toggle.
        val clip = layer?.clipToBelow == true
        Row(
            modifier = Modifier.fillMaxWidth().height(48.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = clipLabel,
                style = MaterialTheme.typography.bodyMedium,
                color = if (enabled) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                },
                maxLines = 1,
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 8.dp)
            )
            Spacer(Modifier.width(8.dp))
            Box(
                modifier = Modifier
                    .size(96.dp, 48.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(
                        if (clip) {
                            MaterialTheme.colorScheme.secondaryContainer
                        } else {
                            MaterialTheme.colorScheme.surfaceContainerHigh
                        }
                    )
                    .toggleable(
                        value = clip,
                        enabled = canEdit,
                        role = Role.Switch,
                        onValueChange = { checked ->
                            val id = latestLayerId ?: return@toggleable
                            latestActions.applyLayerEdit(
                                latestState, id, "Clip to below"
                            ) {
                                it.copy(clipToBelow = checked)
                            }
                        }
                    )
                    .testTag(TAG_LAYER_CLIP),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = stringResource(
                        if (clip) {
                            Res.string.editor_layer_on
                        } else {
                            Res.string.editor_layer_off
                        }
                    ),
                    style = MaterialTheme.typography.labelLarge,
                    color = if (clip) {
                        MaterialTheme.colorScheme.onSecondaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    maxLines = 1
                )
            }
        }
    }

    if (dialogOpen && owner.showDialog) {
        val opacity = layer?.opacity ?: 1f
        NumericEntryDialog(
            label = opacityLabel,
            initialValue = opacity * 100f,
            range = 0f..100f,
            onConfirm = { entered ->
                val id = latestLayerId
                // `ownsDraft` + the source ref are the ownership proof —
                // `interactionActive` is a published snapshot that can
                // lag the open draft and would orphan it.
                if (id != null && owner.ownsDraft &&
                    owner.sourceRef == sourceKey
                ) {
                    latestActions.draftLayerEdit(latestState, id) {
                        it.copy(opacity = entered / 100f)
                    }
                    latestActions.commitEdit()
                }
                owner.hide()
                dialogOpen = false
            },
            onDismiss = {
                dialogOpen = false
                owner.dismiss(sourceKey, latestActions.cancelEdit)
            }
        )
    }
}
