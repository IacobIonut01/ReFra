package com.dot.gallery.editor.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import com.dot.gallery.editor.core.model.PhotoEdits
import com.dot.gallery.editor.core.model.toEdits
import com.dot.gallery.editor.render.photo.PhotoEditorState
import com.dot.gallery.editor.ui.generated.resources.Res
import com.dot.gallery.editor.ui.generated.resources.editor_dock_auto
import com.dot.gallery.editor.ui.generated.resources.editor_dock_more
import com.dot.gallery.editor.ui.generated.resources.editor_layers
import com.dot.gallery.editor.ui.generated.resources.editor_show_all_tools
import com.dot.gallery.editor.ui.generated.resources.editor_tool_crop_short
import com.dot.gallery.editor.ui.icons.AutoAwesome
import com.dot.gallery.editor.ui.icons.EditorSymbols
import com.dot.gallery.editor.ui.icons.Layers
import com.dot.gallery.editor.ui.icons.MoreVert
import org.jetbrains.compose.resources.stringResource

internal const val TAG_CATALOGUE = "editor.catalogue"
internal const val TAG_LAYERS = "editor.layers"
internal const val TAG_FOCUS = "editor.focus"
internal const val TAG_DOCK_AUTO = "editor.dock.auto"

/** Ids the shared Simple dock items carry for its non-tool slots. */
internal const val DOCK_ID_AUTO = "dock.auto"
internal const val DOCK_ID_MORE = "dock.more"

private val RAW_DOCK = listOf(
    EditorToolIds.PhotoCrop.value,
    EditorToolIds.PhotoDevelop.value,
    EditorToolIds.PhotoLooks.value
)

/**
 * The Simple dock — [EditorDock] items of Auto, the pinned
 * destinations (or the RAW set Crop/Develop/Looks) and More. On narrow
 * windows the item width drops to 60 dp and pins that would not fit
 * stay reachable through the catalogue instead of clipping the pill.
 * The pill keeps the approved swipe-up and "Show all tools" custom
 * action.
 */
@Composable
internal fun EditorFloatingToolbar(
    workspace: EditorWorkspaceState,
    state: PhotoEditorState,
    entries: List<EditorToolEntry<PhotoEditorState, PhotoEditorActions>>,
    enabled: Boolean,
    onSelectTool: (EditorToolId) -> Unit,
    onShowTools: () -> Unit,
    auto: PhotoAutoDock,
    modifier: Modifier = Modifier
) {
    val showAllLabel = stringResource(Res.string.editor_show_all_tools)
    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val itemWidth = if (maxWidth < 600.dp) 60.dp else 72.dp
        // Auto and More always show; the pins between them take what
        // the remaining width affords (16 dp of side insets included).
        val fitCount = (
            (maxWidth - 16.dp) / itemWidth
            ).toInt() - 2
        val items = photoDockItems(
            workspace = workspace,
            state = state,
            entries = entries,
            auto = auto,
            enabled = enabled,
            maxPins = fitCount.coerceAtLeast(0)
        )
        EditorDock(
            items = items,
            orientation = Orientation.Horizontal,
            onSelect = { id ->
                photoDockSelect(
                    id = id,
                    workspace = workspace,
                    onSelectTool = onSelectTool,
                    onShowTools = onShowTools,
                    auto = auto
                )
            },
            itemWidth = itemWidth,
            modifier = Modifier
                .fillMaxWidth()
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
        )
    }
}

/**
 * The Simple dock's item list: Auto first, then the pinned
 * destinations with Colour fixed at index two — capped at [maxPins]
 * so narrow windows shed the tail into More — then the catalogue's
 * More slot. A RAW source swaps the pins for its own
 * Crop/Develop/Looks set and leaves Colour out.
 */
@Composable
internal fun photoDockItems(
    workspace: EditorWorkspaceState,
    state: PhotoEditorState,
    entries: List<EditorToolEntry<PhotoEditorState, PhotoEditorActions>>,
    auto: PhotoAutoDock,
    enabled: Boolean,
    maxPins: Int = Int.MAX_VALUE
): List<EditorDockItem> {
    val autoLabel = stringResource(Res.string.editor_dock_auto)
    val moreLabel = stringResource(Res.string.editor_dock_more)
    val layersLabel = stringResource(Res.string.editor_layers)
    val rawSource = state.info?.isRaw == true
    val pinned = if (rawSource) RAW_DOCK else workspace.pinnedPhotoDestinations
    // Colour is a fixed member of the Simple dock too — it follows the
    // second pin, tagged like any other destination.
    val destinations = (
        if (rawSource) pinned
        else pinned.take(2) + EditorToolIds.PhotoColour.value + pinned.drop(2)
        ).take(maxPins.coerceAtLeast(0))
    val items = mutableListOf<EditorDockItem>()
    items += EditorDockItem(
        id = DOCK_ID_AUTO,
        label = autoLabel,
        icon = EditorSymbols.AutoAwesome,
        selected = auto.selected,
        enabled = auto.enabled && enabled,
        tag = TAG_DOCK_AUTO
    )
    destinations.forEach { destination ->
        if (destination == PHOTO_PIN_LAYERS) {
            items += EditorDockItem(
                id = destination,
                label = layersLabel,
                icon = EditorSymbols.Layers,
                selected = workspace.panel ==
                    EditorWorkspacePanel.Layers &&
                    workspace.toolPanelExpanded,
                enabled = enabled,
                tag = photoPinTag(destination)
            )
        } else {
            entries.firstOrNull {
                it.descriptor.id.value == destination
            }?.let { entry ->
                items += EditorDockItem(
                    id = destination,
                    label = entry.pillLabel(),
                    icon = entry.descriptor.icon,
                    selected = workspace.panel ==
                        EditorWorkspacePanel.Edit &&
                        workspace.activeTool ==
                        entry.descriptor.id &&
                        workspace.toolPanelExpanded,
                    // The slot mirrors the catalogue gate — an
                    // unavailable contextual tool shows but refuses
                    // the tap.
                    enabled = enabled && entry.available(state),
                    tag = photoPinTag(destination),
                    edited = photoToolEditCount(
                        entry.descriptor.id,
                        state.document?.toEdits() ?: PhotoEdits()
                    ) > 0 || (entry.descriptor.id == EditorToolIds.PhotoLight &&
                        photoToolEditCount(
                            EditorToolIds.PhotoCurves,
                            state.document?.toEdits() ?: PhotoEdits()
                        ) > 0)
                )
            }
        }
    }
    items += EditorDockItem(
        id = DOCK_ID_MORE,
        label = moreLabel,
        icon = EditorSymbols.MoreVert,
        selected = workspace.catalogueOpen,
        enabled = enabled,
        tag = TAG_CATALOGUE
    )
    return items
}

/** Routes a Simple dock selection: Auto applies/undoes, More opens the
 * catalogue, the Layers pin toggles its pane, tools select normally. */
internal fun photoDockSelect(
    id: String,
    workspace: EditorWorkspaceState,
    onSelectTool: (EditorToolId) -> Unit,
    onShowTools: () -> Unit,
    auto: PhotoAutoDock
) {
    when (id) {
        DOCK_ID_AUTO -> auto.onSelect()
        DOCK_ID_MORE -> onShowTools()
        PHOTO_PIN_LAYERS -> workspace.togglePanel(EditorWorkspacePanel.Layers)
        else -> onSelectTool(EditorToolId(id))
    }
}

/**
 * The dock's short label. "Crop & straighten" cannot fit a six-slot
 * capsule, so Crop carries its pill-only alias; every other slot uses
 * the registry title.
 */
@Composable
internal fun EditorToolEntry<PhotoEditorState, PhotoEditorActions>.pillLabel(): String =
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
