package com.dot.gallery.editor.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.dot.gallery.editor.ui.generated.resources.Res
import com.dot.gallery.editor.ui.generated.resources.editor_dock_edited
import org.jetbrains.compose.resources.stringResource

/**
 * One labelled destination of the [EditorDock] — a pinned tool, the Auto
 * action or the catalogue's More slot. [tag] is the node's test tag.
 */
internal data class EditorDockItem(
    val id: String,
    val label: String,
    val icon: ImageVector,
    val selected: Boolean,
    val enabled: Boolean = true,
    val tag: String,
    /** A tool with changes: the icon carries a dot and TalkBack says so. */
    val edited: Boolean = false
)

/**
 * The editor's labelled dock: a glass pill of icon-over-label
 * destinations. Horizontal it floats along the bottom edge (72 dp tall,
 * full pill ends); [Orientation.Vertical] makes it the left rail of the
 * large Simple workspace (80 dp wide). The selected item carries a
 * secondaryContainer pill behind its icon; every item is a Role.Tab with
 * `selected` semantics inside a 48 dp touch target.
 */
@Composable
internal fun EditorDock(
    items: List<EditorDockItem>,
    orientation: Orientation,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
    itemWidth: Dp = 72.dp,
    /** A fixed first item, such as Back, kept clear of the scrolling items. */
    leading: EditorDockItem? = null
) {
    val glass = editorGlassColors()
    val horizontal = orientation == Orientation.Horizontal
    val shape = RoundedCornerShape(if (horizontal) 36.dp else 40.dp)
    Box(
        modifier = modifier
            .then(
                if (horizontal) {
                    Modifier.fillMaxWidth().height(72.dp)
                } else {
                    Modifier.width(80.dp).fillMaxHeight()
                }
            )
            // The same panelGlass material the large tool card uses —
            // near-opaque over the blurred canvas, never a see-through
            // tint of the photo behind it.
            .chromeBlur(shape, glass.panelGlass)
            .padding(
                horizontal = if (horizontal) 8.dp else 4.dp,
                vertical = if (horizontal) 4.dp else 8.dp
            )
    ) {
        if (horizontal) {
            if (leading != null) {
                Row(
                    modifier = Modifier.fillMaxSize(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    EditorDockCell(
                        item = leading,
                        onSelect = onSelect,
                        itemWidth = itemWidth,
                        modifier = Modifier.width(itemWidth).height(64.dp)
                    )
                    DockDivider(horizontal = true)
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        items.forEach { item ->
                            EditorDockCell(
                                item = item,
                                onSelect = onSelect,
                                itemWidth = itemWidth,
                                modifier = Modifier.width(itemWidth).height(64.dp)
                            )
                        }
                    }
                }
                return@Box
            }
            Row(
                modifier = Modifier.fillMaxSize(),
                // Wide portrait windows leave the pill room to spare —
                // the items centre in it instead of packing to the start.
                horizontalArrangement = Arrangement.spacedBy(
                    2.dp, Alignment.CenterHorizontally
                ),
                verticalAlignment = Alignment.CenterVertically
            ) {
                items.forEach { item ->
                    EditorDockCell(
                        item = item,
                        onSelect = onSelect,
                        itemWidth = itemWidth,
                        modifier = Modifier
                            .width(itemWidth)
                            .height(64.dp)
                    )
                }
            }
        } else {
            if (leading != null) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    EditorDockCell(
                        item = leading,
                        onSelect = onSelect,
                        itemWidth = itemWidth,
                        modifier = Modifier.width(itemWidth).height(64.dp)
                    )
                    DockDivider(horizontal = false)
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        items.forEach { item ->
                            EditorDockCell(
                                item = item,
                                onSelect = onSelect,
                                itemWidth = itemWidth,
                                modifier = Modifier.width(itemWidth).height(64.dp)
                            )
                        }
                    }
                }
                return@Box
            }
            Column(
                // A short landscape window cannot fit every destination:
                // the rail scrolls rather than clipping its tail.
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(
                    2.dp,
                    Alignment.CenterVertically
                ),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                items.forEach { item ->
                    EditorDockCell(
                        item = item,
                        onSelect = onSelect,
                        itemWidth = itemWidth,
                        modifier = Modifier
                            .width(itemWidth)
                            .height(64.dp)
                    )
                }
            }
        }
    }
}

/** The hairline between a dock's fixed leading item and the rest. */
@Composable
private fun DockDivider(horizontal: Boolean) {
    Box(
        Modifier
            .padding(if (horizontal) 4.dp else 0.dp, if (horizontal) 0.dp else 4.dp)
            .then(if (horizontal) Modifier.size(1.dp, 32.dp) else Modifier.size(32.dp, 1.dp))
            .background(MaterialTheme.colorScheme.outlineVariant)
    )
}

/**
 * One dock destination: the 56×32 selection pill behind a 24 dp icon
 * with its labelMedium label below, centred in the item's box. The hit
 * region is the whole cell.
 */
@Composable
private fun EditorDockCell(
    item: EditorDockItem,
    onSelect: (String) -> Unit,
    itemWidth: Dp,
    modifier: Modifier = Modifier
) {
    val contentColor = when {
        !item.enabled -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
        item.selected -> MaterialTheme.colorScheme.onSecondaryContainer
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    val spokenLabel = if (item.edited) {
        stringResource(Res.string.editor_dock_edited, item.label)
    } else {
        item.label
    }
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .semantics(mergeDescendants = true) {
                role = Role.Tab
                selected = item.selected
                contentDescription = spokenLabel
            }
            .clickable(
                enabled = item.enabled,
                onClickLabel = item.label,
                onClick = { onSelect(item.id) }
            )
            .testTag(item.tag),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(width = 56.dp, height = 32.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(
                    if (item.selected) {
                        MaterialTheme.colorScheme.secondaryContainer
                    } else {
                        Color.Transparent
                    }
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = item.icon,
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(24.dp)
            )
            if (item.edited) {
                // The dot touches the icon's top-end corner (icon spans x 16..40, y 4..28).
                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .offset(x = 37.dp, y = 1.dp)
                        .size(6.dp)
                        .background(MaterialTheme.colorScheme.primary, CircleShape)
                )
            }
        }
        Text(
            text = item.label,
            style = MaterialTheme.typography.labelMedium,
            color = contentColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .width(itemWidth - 8.dp)
                .padding(top = 2.dp)
        )
    }
}
