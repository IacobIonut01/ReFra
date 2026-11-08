package com.dot.gallery.editor.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dot.gallery.editor.render.photo.PhotoEditorState
import com.dot.gallery.editor.ui.generated.resources.Res
import com.dot.gallery.editor.ui.generated.resources.editor_layers
import com.dot.gallery.editor.ui.generated.resources.editor_show_all_tools
import com.dot.gallery.editor.ui.generated.resources.editor_tool_crop_short
import com.dot.gallery.editor.ui.generated.resources.editor_tools
import com.dot.gallery.editor.ui.icons.EditorSymbols
import com.dot.gallery.editor.ui.icons.Layers
import com.dot.gallery.editor.ui.icons.Tune
import org.jetbrains.compose.resources.stringResource

internal const val TAG_CATALOGUE = "editor.catalogue"
internal const val TAG_CATALOGUE_GRID = "editor.catalogue.grid"
internal const val TAG_PANEL_SOURCE = "editor.panel.source"
internal const val TAG_PANEL_EDITS = "editor.panel.edits"
internal const val TAG_LAYERS = "editor.layers"
internal const val TAG_FOCUS = "editor.focus"

private val RAW_DOCK = listOf(
    EditorToolIds.PhotoCrop.value,
    EditorToolIds.PhotoDevelop.value,
    EditorToolIds.PhotoLooks.value
)

/**
 * The approved phone dock — a 68 dp capsule with up to four saveable
 * favourites from [EditorWorkspaceState.pinnedPhotoDestinations], the
 * permanent Tools slot and the fixed Colour slot last. Exactly the six
 * slots of the Stitch navigation bar ship by default: Crop, Light,
 * Markup, Looks, Tools, Colour. Each slot is a full-height tap target
 * with the 48×32 dp selection pill behind its 22 dp icon and a 12 sp
 * label below. The [PHOTO_PIN_LAYERS] destination routes to the Layers
 * workspace; tool destinations select through [onSelectTool]. Tools is
 * never pinnable, and an upward swipe past 24 dp — or the "Show all
 * tools" custom action — opens the catalogue without a tap on the slot.
 */
@Composable
internal fun EditorFloatingToolbar(
    workspace: EditorWorkspaceState,
    state: PhotoEditorState,
    entries: List<EditorToolEntry<PhotoEditorState, PhotoEditorActions>>,
    enabled: Boolean,
    onSelectTool: (EditorToolId) -> Unit,
    onShowTools: () -> Unit,
    modifier: Modifier = Modifier
) {
    val showAllLabel = stringResource(Res.string.editor_show_all_tools)
    val layersLabel = stringResource(Res.string.editor_layers)
    val toolsLabel = stringResource(Res.string.editor_tools)
    EditorFloatingPanel(
        shape = RoundedCornerShape(EditorV2Layout.PhonePillHeight / 2),
        modifier = modifier
            .fillMaxWidth()
            .height(EditorV2Layout.PhonePillHeight)
            .testTag(TAG_EDITOR_PINS)
            .semantics {
                customActions = listOf(
                    CustomAccessibilityAction(label = showAllLabel) {
                        if (!enabled) {
                            false
                        } else {
                            onShowTools()
                            true
                        }
                    }
                )
            }
            .pointerInput(enabled) {
                var drag = 0f
                val threshold = 24.dp.toPx()
                detectVerticalDragGestures(
                    onVerticalDrag = { change, amount ->
                        if (enabled) {
                            drag += amount
                            change.consume()
                        }
                    },
                    onDragEnd = {
                        if (enabled && drag <= -threshold) onShowTools()
                        drag = 0f
                    },
                    onDragCancel = { drag = 0f }
                )
            }
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // A RAW dock is the design's own: Crop, Develop, Looks and Tools.
            val rawSource = state.info?.isRaw == true
            val destinations = if (rawSource) RAW_DOCK else workspace.pinnedPhotoDestinations
            destinations.forEach { destination ->
                if (destination == PHOTO_PIN_LAYERS) {
                    PillSlot(
                        icon = EditorSymbols.Layers,
                        label = layersLabel,
                        selected = workspace.panel ==
                            EditorWorkspacePanel.Layers &&
                            workspace.toolPanelExpanded,
                        enabled = enabled,
                        onClick = {
                            workspace.togglePanel(
                                EditorWorkspacePanel.Layers
                            )
                        },
                        modifier = Modifier
                            .weight(1f)
                            .testTag(photoPinTag(PHOTO_PIN_LAYERS))
                    )
                } else {
                    entries.firstOrNull {
                        it.descriptor.id.value == destination
                    }?.let { entry ->
                        PillSlot(
                            icon = entry.descriptor.icon,
                            label = entry.pillLabel(),
                            selected = workspace.activeTool ==
                                entry.descriptor.id &&
                                workspace.toolPanelExpanded,
                            // The slot mirrors the catalogue gate — an
                            // unavailable contextual tool shows but
                            // refuses the tap.
                            enabled = enabled && entry.available(state),
                            onClick = { onSelectTool(entry.descriptor.id) },
                            modifier = Modifier
                                .weight(1f)
                                .testTag(photoPinTag(destination))
                        )
                    }
                }
            }
            // The fixed Colour slot — a tool, so it never leaves, except on a
            // RAW, whose colour lives in Develop.
            entries.firstOrNull {
                it.descriptor.id == EditorToolIds.PhotoColour && !rawSource
            }?.let { entry ->
                PillSlot(
                    icon = entry.descriptor.icon,
                    label = entry.pillLabel(),
                    selected = workspace.activeTool == entry.descriptor.id &&
                        workspace.toolPanelExpanded,
                    enabled = enabled && entry.available(state),
                    onClick = { onSelectTool(entry.descriptor.id) },
                    modifier = Modifier
                        .weight(1f)
                        .testTag(photoPinTag(entry.descriptor.id.value))
                )
            }
            // Tools closes the dock: the catalogue is the last destination,
            // not a tool — the bar is a tool strip that ends at the drawer.
            PillSlot(
                icon = EditorSymbols.Tune,
                label = toolsLabel,
                selected = workspace.catalogueOpen,
                enabled = enabled,
                onClick = onShowTools,
                modifier = Modifier
                    .weight(1f)
                    .testTag(TAG_CATALOGUE)
            )
        }
    }
}

/**
 * The dock's short label. "Crop & straighten" cannot fit a six-slot
 * capsule, so Crop carries its pill-only alias; every other slot uses
 * the registry title.
 */
@Composable
private fun EditorToolEntry<PhotoEditorState, PhotoEditorActions>.pillLabel(): String =
    stringResource(
        if (descriptor.id == EditorToolIds.PhotoCrop) {
            Res.string.editor_tool_crop_short
        } else {
            descriptor.title
        }
    )

/**
 * One labelled dock destination — the 48×32 dp selection pill behind a
 * 22 dp icon with the 12 sp label below, centred in a full-height slot.
 * The hit region is the whole slot, never just the icon.
 */
@Composable
internal fun PillSlot(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    labelled: Boolean = true
) {
    // Six slots across a 68 dp capsule cannot hold six growing labels at
    // large text, so the dock falls back to icon-only slots there and
    // keeps the name on the merged semantics instead. Icon-only rails
    // ([labelled] false) keep the name there too.
    val showLabel = labelled && LocalDensity.current.fontScale < 1.5f
    Column(
        modifier = modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(16.dp))
            .semantics(mergeDescendants = true) {
                this.selected = selected
                role = Role.Button
                if (!showLabel) contentDescription = label
            }
            .clickable(
                enabled = enabled,
                onClickLabel = label,
                onClick = onClick
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        val tint by animateColorAsState(
            targetValue = when {
                !enabled -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                selected -> MaterialTheme.colorScheme.onSurface
                else -> MaterialTheme.colorScheme.onSurfaceVariant
            },
            animationSpec = tween(180),
            label = "dockTint"
        )
        // The active slot pops with a small bouncy scale instead of a
        // hard swap.
        val slotScale by animateFloatAsState(
            targetValue = if (selected) 1.10f else 1f,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessMediumLow
            ),
            label = "dockScale"
        )
        val iconBg by animateColorAsState(
            targetValue = if (selected) {
                MaterialTheme.colorScheme.surfaceContainerHighest
            } else {
                Color.Transparent
            },
            animationSpec = tween(180),
            label = "dockIconBg"
        )
        Box(
            modifier = Modifier
                .size(width = 48.dp, height = 32.dp)
                .graphicsLayer {
                    scaleX = slotScale
                    scaleY = slotScale
                }
                .clip(RoundedCornerShape(16.dp))
                .background(iconBg),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(22.dp)
            )
        }
        if (showLabel) {
            Spacer(Modifier.height(2.dp))
            Text(
                text = label,
                fontSize = 12.sp,
                lineHeight = 14.sp,
                color = tint,
                maxLines = 1
            )
        }
    }
}
